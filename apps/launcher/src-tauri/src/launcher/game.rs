//! Orchestrateur du flow "click Jouer" → JVM en cours d'execution.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §8.1 etapes [5]..[13].
//!
//! Ce module n'a pas encore tout ce qu'il faut pour lancer Minecraft pour
//! de vrai (il manque le download du client jar + libraries vanilla + le
//! Fabric loader). Il pose la machinerie reutilisable : telecharger le JRE,
//! demarrer le FS watcher, spawn le binaire, capturer stdout/stderr, gerer
//! l'arret. Les morceaux manquants seront ajoutes Semaines 5-6.

use serde::Serialize;
use std::path::PathBuf;
use std::process::Stdio;
use std::sync::mpsc;
use tauri::{AppHandle, Emitter, Manager, Runtime, State};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::{Child, Command};
use tokio::sync::Mutex;

use crate::auth::AuthState;
use crate::integrity::{spawn_watcher, TamperingEvent, WatcherHandle};
use crate::launcher::{paths, runtime};

/// State partage par les commandes Tauri qui touchent a la JVM.
/// Aujourd'hui : un slot pour le PID courant. Plus tard : RAM, win position…
#[derive(Default)]
pub struct GameState {
    pub running: Mutex<Option<RunningGame>>,
}

pub struct RunningGame {
    pub child: Child,
    pub _watcher: WatcherHandle,
}

impl GameState {
    pub fn new() -> Self {
        Self::default()
    }
}

#[derive(Debug, thiserror::Error, Serialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum GameError {
    #[error("non authentifie : connecte-toi avant de lancer le jeu")]
    NotAuthenticated,
    #[error("le jeu tourne deja")]
    AlreadyRunning,
    #[error("aucune partie en cours a arreter")]
    NotRunning,
    #[error("io : {message}")]
    Io { message: String },
    #[error("runtime Java : {message}")]
    Runtime { message: String },
    #[error("watcher : {message}")]
    Watcher { message: String },
    #[error("non implemente : {message}")]
    NotImplemented { message: String },
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct LaunchResult {
    pub pid: u32,
    pub java_path: String,
}

/// Etape [5] uniquement (pour l'instant) : on prepare le JRE.
/// Le spawn JVM avec un argv complet attend le download du client jar +
/// libraries vanilla, qui arrive en S5-S6.
#[tauri::command]
pub async fn launcher_launch_game<R: Runtime>(
    app: AppHandle<R>,
    auth: State<'_, AuthState>,
    game: State<'_, GameState>,
) -> Result<LaunchResult, GameError> {
    {
        let lock = game.running.lock().await;
        if lock.is_some() {
            return Err(GameError::AlreadyRunning);
        }
    }

    // 1. JRE present localement, ou DL via piston-meta.
    let dir = paths::game_dir().map_err(|e| GameError::Io {
        message: e.to_string(),
    })?;
    let java_path = runtime::ensure_runtime(&auth.http, &dir)
        .await
        .map_err(|e| GameError::Runtime {
            message: e.to_string(),
        })?;

    // 2. Spawn d'un process Java tres minimal, juste pour valider la chaine
    //    end-to-end. On lance "java -version" et on capture le retour.
    //    Le vrai argv MC viendra quand on aura le download du client jar.
    let mut command = Command::new(&java_path);
    command
        .arg("-version")
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .stdin(Stdio::null());

    let mut child = command.spawn().map_err(|e| GameError::Io {
        message: format!("spawn java -version : {e}"),
    })?;

    let pid = child.id().unwrap_or(0);
    let java_display = java_path.display().to_string();

    pump_stdio(&mut child, app.clone());

    // 3. FS watcher.
    let (tamper_tx, _tamper_rx) = mpsc::channel::<TamperingEvent>();
    let watch_dirs: Vec<PathBuf> = vec![dir.join("mods"), dir.join("config")];
    let watcher = spawn_watcher(app.clone(), watch_dirs, tamper_tx).map_err(|e| {
        GameError::Watcher {
            message: e.to_string(),
        }
    })?;

    {
        let mut lock = game.running.lock().await;
        *lock = Some(RunningGame {
            child,
            _watcher: watcher,
        });
    }

    let _ = app.emit(
        "game:started",
        LaunchResult {
            pid,
            java_path: java_display.clone(),
        },
    );

    // Spawn une task qui attend la fin du process et nettoie l'etat.
    let game_state_handle = app.clone();
    tokio::spawn(async move {
        await_game_end(game_state_handle).await;
    });

    Ok(LaunchResult {
        pid,
        java_path: java_display,
    })
}

#[tauri::command]
pub async fn launcher_stop_game(game: State<'_, GameState>) -> Result<(), GameError> {
    let mut lock = game.running.lock().await;
    let Some(running) = lock.as_mut() else {
        return Err(GameError::NotRunning);
    };
    running.child.start_kill().map_err(|e| GameError::Io {
        message: e.to_string(),
    })?;
    Ok(())
}

fn pump_stdio<R: Runtime>(child: &mut Child, app: AppHandle<R>) {
    if let Some(stdout) = child.stdout.take() {
        let app2 = app.clone();
        tokio::spawn(async move {
            let mut reader = BufReader::new(stdout).lines();
            while let Ok(Some(line)) = reader.next_line().await {
                tracing::debug!(target: "game", "stdout: {line}");
                let _ = app2.emit("game:stdout", line);
            }
        });
    }
    if let Some(stderr) = child.stderr.take() {
        tokio::spawn(async move {
            let mut reader = BufReader::new(stderr).lines();
            while let Ok(Some(line)) = reader.next_line().await {
                tracing::debug!(target: "game", "stderr: {line}");
                let _ = app.emit("game:stderr", line);
            }
        });
    }
}

async fn await_game_end<R: Runtime>(app: AppHandle<R>) {
    // Re-acquerir le state via l'AppHandle. La sortie du process stdio + drop
    // du watcher s'occupent du nettoyage logique, ici on attend juste la fin.
    let Some(state) = app.try_state::<GameState>() else {
        return;
    };
    let exit_status = {
        let mut lock = state.running.lock().await;
        if let Some(running) = lock.as_mut() {
            running.child.wait().await.ok()
        } else {
            None
        }
    };
    {
        let mut lock = state.running.lock().await;
        *lock = None;
    }
    let code = exit_status.and_then(|s| s.code()).unwrap_or(-1);
    let _ = app.emit("game:exited", code);
    tracing::info!("game process exited with code {code}");
}
