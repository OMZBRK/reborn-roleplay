//! Orchestrateur du flow "click Jouer" → JVM en cours d'execution.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §8.1 etapes [5]..[13].
//!
//! Etapes implementees :
//! 1. JRE Mojang via piston-meta (cf runtime.rs)
//! 2. Version json Mojang pour la version Minecraft cible
//! 3. Client jar + libraries vanilla + extraction natives
//! 4. Asset index + assets
//! 5. Fabric Loader (override main class + libs supplementaires)
//! 6. Build du LaunchConfig + spawn java avec argv complet
//! 7. FS watcher + capture stdout/stderr + lifecycle events Tauri

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
use crate::launcher::{
    assets, fabric, jvm, libraries, mojang, paths, runtime,
};

/// Version Minecraft cible. Plus tard, viendra du manifest courant Reborn.
const MINECRAFT_VERSION: &str = "1.21.4";
const DEFAULT_RAM_MB: u32 = 4096;
const DEFAULT_WIDTH: u32 = 1280;
const DEFAULT_HEIGHT: u32 = 720;
/// Token MC factice utilise quand l'auth dev (sans Microsoft) est en cours.
/// Le client Minecraft accepte n'importe quelle string non-vide ici, mais
/// refusera de valider auprès des serveurs Mojang (mode online). Suffisant
/// pour voir le menu principal et tester le launch chain.
const DEV_PLACEHOLDER_TOKEN: &str = "0";

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
    #[error("metadata Minecraft : {message}")]
    Mojang { message: String },
    #[error("libraries : {message}")]
    Libraries { message: String },
    #[error("assets : {message}")]
    Assets { message: String },
    #[error("Fabric Loader : {message}")]
    Fabric { message: String },
    #[error("watcher : {message}")]
    Watcher { message: String },
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct LaunchResult {
    pub pid: u32,
    pub java_path: String,
    pub minecraft_version: String,
    pub fabric_version: String,
}

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

    let user = auth
        .current_user()
        .await
        .ok_or(GameError::NotAuthenticated)?;

    let dir = paths::game_dir().map_err(|e| GameError::Io {
        message: e.to_string(),
    })?;

    // Etape 1 : JRE.
    tracing::info!("launch [1/6] : runtime Java");
    let java_path = runtime::ensure_runtime(&auth.http, &dir)
        .await
        .map_err(|e| GameError::Runtime {
            message: e.to_string(),
        })?;

    // Etape 2 : version JSON Mojang.
    tracing::info!("launch [2/6] : metadata Minecraft {MINECRAFT_VERSION}");
    let version = mojang::fetch_version_json(&auth.http, &dir, MINECRAFT_VERSION)
        .await
        .map_err(|e| GameError::Mojang {
            message: e.to_string(),
        })?;

    // Etape 3 : libraries + client jar + natives.
    tracing::info!("launch [3/6] : libraries vanilla + natives");
    let lib_setup = libraries::ensure_libraries(&auth.http, &dir, &version)
        .await
        .map_err(|e| GameError::Libraries {
            message: e.to_string(),
        })?;

    // Etape 4 : assets (long la premiere fois).
    tracing::info!("launch [4/6] : assets");
    let asset_setup = assets::ensure_assets(&auth.http, &dir, &version.asset_index)
        .await
        .map_err(|e| GameError::Assets {
            message: e.to_string(),
        })?;

    // Etape 5 : Fabric Loader.
    tracing::info!("launch [5/6] : Fabric Loader");
    let fabric_setup = fabric::ensure_fabric(&auth.http, &dir, MINECRAFT_VERSION)
        .await
        .map_err(|e| GameError::Fabric {
            message: e.to_string(),
        })?;

    // Etape 6 : assemble + spawn.
    tracing::info!("launch [6/6] : spawn JVM (Fabric {})", fabric_setup.loader_version);

    // Classpath = libs vanilla + libs Fabric. Le client.jar est ajoute par
    // build_command lui-meme.
    let mut all_libs: Vec<PathBuf> = lib_setup.library_jars.clone();
    all_libs.extend(fabric_setup.library_jars.clone());

    let cfg = jvm::LaunchConfig {
        minecraft_version: MINECRAFT_VERSION.to_string(),
        launcher_version: env!("CARGO_PKG_VERSION").to_string(),
        minecraft_username: user.minecraft_username.clone(),
        minecraft_uuid: user.minecraft_uuid.clone(),
        // Pour l'instant, dev placeholder. Quand l'app MS sera approuvee on
        // stockera le mc_access_token dans le keyring et on le passera ici.
        mc_access_token: DEV_PLACEHOLDER_TOKEN.to_string(),
        natives_dir: lib_setup.natives_dir.display().to_string(),
        library_jars: all_libs.iter().map(|p| p.display().to_string()).collect(),
        client_jar: lib_setup.client_jar.display().to_string(),
        game_dir: dir.display().to_string(),
        assets_dir: asset_setup.assets_dir.display().to_string(),
        asset_index: asset_setup.asset_index.clone(),
        ram_mb: DEFAULT_RAM_MB,
        width: DEFAULT_WIDTH,
        height: DEFAULT_HEIGHT,
        auto_connect: None,
    };

    let mut argv = jvm::build_command(&cfg);

    // Override de la mainClass vanilla par celle de Fabric.
    if let Some(idx) = argv
        .iter()
        .position(|a| a == "net.minecraft.client.main.Main")
    {
        argv[idx] = fabric_setup.main_class.clone();
    }

    let mut command = Command::new(&java_path);
    command
        .args(&argv)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .stdin(Stdio::null())
        .current_dir(&dir);

    let mut child = command.spawn().map_err(|e| GameError::Io {
        message: format!("spawn java : {e}"),
    })?;

    let pid = child.id().unwrap_or(0);
    let java_display = java_path.display().to_string();

    pump_stdio(&mut child, app.clone());

    // FS watcher
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

    let result = LaunchResult {
        pid,
        java_path: java_display,
        minecraft_version: MINECRAFT_VERSION.to_string(),
        fabric_version: fabric_setup.loader_version.clone(),
    };

    let _ = app.emit("game:started", result.clone());

    let game_state_handle = app.clone();
    tokio::spawn(async move {
        await_game_end(game_state_handle).await;
    });

    Ok(result)
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
