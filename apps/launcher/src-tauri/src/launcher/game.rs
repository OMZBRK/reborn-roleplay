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
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::mpsc;
use tauri::{AppHandle, Emitter, Manager, Runtime, State};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::{Child, Command};
use tokio::sync::Mutex;

use crate::auth::AuthState;
use crate::discord_rpc::DiscordRpcState;
use crate::integrity::{spawn_watcher, TamperingEvent, WatcherHandle};
use crate::launcher::{
    assets, diagnostics, fabric, jvm, libraries, mods as mods_inspect, mojang, paths, runtime,
};
use crate::storage::{prefs, secrets::SecretKey};

/// Version Minecraft cible. Plus tard, viendra du manifest courant Reborn.
/// En attendant, lisible via REBORN_MC_VERSION pour faciliter l'alignement
/// avec le serveur de dev sans recompiler.
fn minecraft_version() -> String {
    std::env::var("REBORN_MC_VERSION").unwrap_or_else(|_| "1.21.1".into())
}
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
    #[error("whitelist requise pour rejoindre le serveur")]
    NotWhitelisted,
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
    #[error("play-token : {message}")]
    PlayToken { message: String },
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

    // Gate whitelist : seul un user dont la candidature a ete acceptee
    // (role promu a WHITELISTED ou superieur par staff.service) peut
    // rejoindre le serveur. Cote serveur, le plugin Reborn Guardian
    // appliquera la meme regle ; le check ici evite juste d'avoir un user
    // qui se fait kick par le serveur 3s apres le lancement.
    if user.role == "PLAYER" {
        return Err(GameError::NotWhitelisted);
    }

    // Token MC pour Mojang sessionserver. Sans ca le serveur rejecte avec
    // "Invalid session". `ensure_mc_token` renvoie None pour un dev-login
    // (pas de chaine MS) : on tombe sur le placeholder qui ne marche que
    // sur un serveur en offline-mode (utile pour tester localement).
    let mc_access_token = match auth.ensure_mc_token().await {
        Ok(Some(token)) => token,
        Ok(None) => {
            tracing::warn!(
                "aucun token MC disponible (dev-login ?), utilise le placeholder — la connexion serveur online-mode echouera"
            );
            DEV_PLACEHOLDER_TOKEN.to_string()
        }
        Err(e) => {
            tracing::warn!("rafraichissement MS->MC echoue : {e} — utilise le placeholder");
            DEV_PLACEHOLDER_TOKEN.to_string()
        }
    };

    let dir = paths::game_dir().map_err(|e| GameError::Io {
        message: e.to_string(),
    })?;

    // Play-token : signe HMAC par l'API, lu par le mod Reborn Integrity au
    // boot et pousse au serveur via custom payload `reborn:auth`. Sans ca,
    // le plugin Guardian kick le joueur a T+8s du JOIN. Echec ici =>
    // bloque le launch (on ne sait pas attester, autant ne pas lancer).
    let reborn_token = auth
        .store
        .get(SecretKey::RebornAccessToken)
        .map_err(|e| GameError::Io { message: e.to_string() })?
        .ok_or(GameError::NotAuthenticated)?;
    let play_session = auth
        .api
        .fetch_play_token(&reborn_token)
        .await
        .map_err(|e| GameError::PlayToken { message: e.to_string() })?;
    let play_token_path = dir.join(".reborn-play-token");
    tokio::fs::write(&play_token_path, play_session.play_token.as_bytes())
        .await
        .map_err(|e| GameError::Io { message: format!("write play-token : {e}") })?;
    tracing::info!(
        "play-token ecrit ({} bytes), expire a {}",
        play_session.play_token.len(),
        play_session.expires_at
    );

    let user_prefs = prefs::load().await.unwrap_or_default();
    let mc_version = minecraft_version();

    // Etape 0 : nettoie les mods qui ne ciblent pas la version MC active.
    // Quand on bouge entre versions (ex: 1.21.4 -> 1.21.1), des jars Sodium
    // / Iris / etc. d'une autre version restent et plantent Fabric Loader
    // au boot. Cf launcher/diagnostics.rs::parse_mod_version_mismatch.
    let mods_dir_pre = dir.join("mods");
    match mods_inspect::purge_incompatible_mods(&mods_dir_pre, &mc_version) {
        Ok(removed) if !removed.is_empty() => {
            tracing::info!("nettoyage mods : {} jar(s) incompatibles supprime(s)", removed.len());
            for path in &removed {
                tracing::info!("  - {path}");
            }
            let _ = app.emit(
                "mods:purged",
                serde_json::json!({
                    "removed": removed,
                    "targetMcVersion": mc_version,
                }),
            );
        }
        Ok(_) => {}
        Err(e) => tracing::warn!("inspection mods impossible : {e}"),
    }

    // Etape 1 : JRE.
    tracing::info!("launch [1/6] : runtime Java");
    let java_path = runtime::ensure_runtime(&auth.download_http, &dir)
        .await
        .map_err(|e| GameError::Runtime {
            message: e.to_string(),
        })?;

    // Etape 2 : version JSON Mojang.
    tracing::info!("launch [2/6] : metadata Minecraft {mc_version}");
    let version = mojang::fetch_version_json(&auth.download_http, &dir, &mc_version)
        .await
        .map_err(|e| GameError::Mojang {
            message: e.to_string(),
        })?;

    // Etape 3 : libraries + client jar + natives.
    tracing::info!("launch [3/6] : libraries vanilla + natives");
    let lib_setup = libraries::ensure_libraries(&auth.download_http, &dir, &version)
        .await
        .map_err(|e| GameError::Libraries {
            message: e.to_string(),
        })?;

    // Etape 4 : assets (long la premiere fois).
    tracing::info!("launch [4/6] : assets");
    let asset_setup = assets::ensure_assets(&auth.download_http, &dir, &version.asset_index)
        .await
        .map_err(|e| GameError::Assets {
            message: e.to_string(),
        })?;

    // Etape 5 : Fabric Loader.
    tracing::info!("launch [5/6] : Fabric Loader");
    let fabric_setup = fabric::ensure_fabric(&auth.download_http, &dir, &mc_version)
        .await
        .map_err(|e| GameError::Fabric {
            message: e.to_string(),
        })?;

    // Etape 6 : assemble + spawn.
    tracing::info!("launch [6/6] : spawn JVM (Fabric {})", fabric_setup.loader_version);

    // Classpath = libs vanilla + libs Fabric, deduplique sur group:artifact.
    // Fabric apporte parfois une version plus recente d'une lib transitive
    // (exemple : ASM 9.9 vs ASM 9.6 vanilla) ; Fabric Loader plante avec
    // "duplicate classes found on classpath" si on garde les deux. On
    // garde donc la *derniere* version vue (Fabric, ajoutee apres vanilla).
    let mut vanilla_libs = lib_setup.library_jars.clone();
    vanilla_libs.extend(fabric_setup.library_jars.clone());
    let all_libs = dedupe_classpath(vanilla_libs);

    let cfg = jvm::LaunchConfig {
        minecraft_version: mc_version.clone(),
        launcher_version: env!("CARGO_PKG_VERSION").to_string(),
        minecraft_username: user.minecraft_username.clone(),
        minecraft_uuid: user.minecraft_uuid.clone(),
        mc_access_token,
        natives_dir: lib_setup.natives_dir.display().to_string(),
        library_jars: all_libs.iter().map(|p| p.display().to_string()).collect(),
        client_jar: lib_setup.client_jar.display().to_string(),
        game_dir: dir.display().to_string(),
        assets_dir: asset_setup.assets_dir.display().to_string(),
        asset_index: asset_setup.asset_index.clone(),
        ram_mb: user_prefs.ram_mb,
        width: user_prefs.width,
        height: user_prefs.height,
        auto_connect: resolve_auto_connect(),
        play_token_path: Some(play_token_path.display().to_string()),
    };

    let mut argv = jvm::build_command(&cfg);

    // Override de la mainClass vanilla par celle de Fabric.
    if let Some(idx) = argv
        .iter()
        .position(|a| a == "net.minecraft.client.main.Main")
    {
        argv[idx] = fabric_setup.main_class.clone();
    }

    // DEBUG : dump l'argv complet dans un fichier pour pouvoir reproduire
    // la commande a la main, et redirige stderr aussi vers un fichier (les
    // logs tracing semblent rater quand le process meurt en <1s).
    let debug_argv_path = dir.join("logs").join("last-argv.txt");
    let debug_stderr_path = dir.join("logs").join("last-stderr.txt");
    let _ = tokio::fs::create_dir_all(dir.join("logs")).await;
    let _ = tokio::fs::write(
        &debug_argv_path,
        argv.iter()
            .map(|s| format!("{s:?}"))
            .collect::<Vec<_>>()
            .join("\n"),
    )
    .await;
    tracing::info!(
        "argv dumped to {} ({} args)",
        debug_argv_path.display(),
        argv.len()
    );

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

    pump_stdio(&mut child, app.clone(), debug_stderr_path.clone());

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
        minecraft_version: mc_version.clone(),
        fabric_version: fabric_setup.loader_version.clone(),
    };

    let _ = app.emit("game:started", result.clone());

    // Discord Rich Presence — best-effort, ne bloque pas le launch.
    if let Some(rpc) = app.try_state::<DiscordRpcState>() {
        rpc.start_in_game(&result.minecraft_version).await;
    }

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

fn pump_stdio<R: Runtime>(child: &mut Child, app: AppHandle<R>, stderr_dump: PathBuf) {
    use std::sync::Arc;
    use tokio::sync::Mutex as AsyncMutex;

    // L'analyzer est partage entre les deux pumps : certaines erreurs
    // arrivent sur stdout (Fabric Loader logge ses warnings ici), d'autres
    // sur stderr (les vraies stack traces JVM). Un seul detecteur pour
    // eviter les doublons.
    let analyzer = Arc::new(AsyncMutex::new(diagnostics::LogAnalyzer::new()));

    if let Some(stdout) = child.stdout.take() {
        let app2 = app.clone();
        let analyzer2 = analyzer.clone();
        tokio::spawn(async move {
            let mut reader = BufReader::new(stdout).lines();
            while let Ok(Some(line)) = reader.next_line().await {
                tracing::info!(target: "game", "stdout: {line}");
                if let Some(diag) = analyzer2.lock().await.ingest(&line) {
                    tracing::warn!("diagnostic detecte : {} - {}", diag.code, diag.message);
                    let _ = app2.emit("game:diagnostic", diag);
                }
                let _ = app2.emit("game:stdout", line);
            }
        });
    }

    if let Some(stderr) = child.stderr.take() {
        let app3 = app.clone();
        let analyzer3 = analyzer.clone();
        let dump_path = stderr_dump.clone();
        tokio::spawn(async move {
            // Tee : on dump dans last-stderr.txt ET on analyse en stream.
            let mut writer: Option<tokio::fs::File> = match tokio::fs::OpenOptions::new()
                .create(true)
                .write(true)
                .truncate(true)
                .open(&dump_path)
                .await
            {
                Ok(f) => Some(f),
                Err(e) => {
                    tracing::warn!("impossible d'ouvrir {} : {e}", dump_path.display());
                    None
                }
            };
            let mut reader = BufReader::new(stderr).lines();
            while let Ok(Some(line)) = reader.next_line().await {
                tracing::warn!(target: "game", "stderr: {line}");
                if let Some(file) = writer.as_mut() {
                    use tokio::io::AsyncWriteExt;
                    let _ = file.write_all(line.as_bytes()).await;
                    let _ = file.write_all(b"\n").await;
                }
                if let Some(diag) = analyzer3.lock().await.ingest(&line) {
                    tracing::warn!("diagnostic detecte : {} - {}", diag.code, diag.message);
                    let _ = app3.emit("game:diagnostic", diag);
                }
                let _ = app3.emit("game:stderr", line);
            }
        });
    }
}

/// Deduplique le classpath sur (group:artifact). Quand un meme artifact
/// est present plusieurs fois (typiquement vanilla + Fabric), on garde la
/// *derniere* occurrence — c'est generalement la version la plus recente
/// puisque les libs Fabric sont concatenees apres celles de vanilla.
///
/// Conserve l'ordre relatif entre artifacts distincts pour la
/// reproductibilite des launches.
fn dedupe_classpath(jars: Vec<PathBuf>) -> Vec<PathBuf> {
    use std::collections::HashMap;
    let mut last_index: HashMap<String, usize> = HashMap::new();
    for (i, path) in jars.iter().enumerate() {
        if let Some(key) = maven_dedupe_key(path) {
            last_index.insert(key, i);
        }
    }
    let mut out = Vec::with_capacity(jars.len());
    for (i, path) in jars.into_iter().enumerate() {
        match maven_dedupe_key(&path) {
            Some(key) if last_index.get(&key) == Some(&i) => out.push(path),
            None => out.push(path), // pas un layout maven → on garde
            _ => {}                  // doublon, on saute
        }
    }
    out
}

/// Pour un chemin de la forme `<...>/libraries/<group>/<artifact>/<version>/<filename>`,
/// retourne `<group dot-separated>:<artifact>:<classifier>` (classifier vide pour
/// le jar principal). Les variantes `natives-*` du meme artifact partagent le
/// group:artifact mais doivent rester distinctes (sinon on drop le jar contenant
/// les classes Java au profit d'un jar de .dll). None si le path ne suit pas
/// la convention Maven du launcher Mojang.
fn maven_dedupe_key(path: &Path) -> Option<String> {
    let comps: Vec<&str> = path
        .components()
        .filter_map(|c| c.as_os_str().to_str())
        .collect();
    let lib_idx = comps.iter().rposition(|s| *s == "libraries")?;
    let tail = &comps[lib_idx + 1..];
    if tail.len() < 4 {
        return None;
    }
    let filename = tail[tail.len() - 1];
    let version = tail[tail.len() - 2];
    let artifact = tail[tail.len() - 3];
    let group = tail[..tail.len() - 3].join(".");

    // <artifact>-<version>[-<classifier>].jar → on isole le classifier.
    let stem = filename.strip_suffix(".jar").unwrap_or(filename);
    let prefix = format!("{artifact}-{version}");
    let classifier = stem
        .strip_prefix(&prefix)
        .map(|c| c.trim_start_matches('-'))
        .unwrap_or("");

    Some(format!("{group}:{artifact}:{classifier}"))
}

// ──────────────────────────────────────────────────────
// Commandes Tauri "mods folder"
// ──────────────────────────────────────────────────────

/// Liste les mods presents dans le game directory avec leur statut
/// (compatible / incompatible avec la version MC active).
#[tauri::command]
pub async fn launcher_mods_list() -> Result<Vec<mods_inspect::ModEntry>, GameError> {
    let dir = paths::game_dir().map_err(|e| GameError::Io {
        message: e.to_string(),
    })?;
    let mc_version = minecraft_version();
    let mods_dir = dir.join("mods");
    mods_inspect::inspect_mods_folder(&mods_dir, &mc_version).map_err(|e| GameError::Io {
        message: e.to_string(),
    })
}

/// Supprime tous les mods marques incompatibles avec la version MC
/// active. Retourne la liste des fichiers supprimes.
#[tauri::command]
pub async fn launcher_mods_purge() -> Result<Vec<String>, GameError> {
    let dir = paths::game_dir().map_err(|e| GameError::Io {
        message: e.to_string(),
    })?;
    let mc_version = minecraft_version();
    let mods_dir = dir.join("mods");
    mods_inspect::purge_incompatible_mods(&mods_dir, &mc_version).map_err(|e| GameError::Io {
        message: e.to_string(),
    })
}

/// Resout l'adresse du serveur Reborn auquel auto-connect le client.
/// Priorite : env runtime `REBORN_SERVER_HOST` (dev/override) > compile-time
/// `REBORN_SERVER_HOST_BUILD` (baker au build release). Idem pour port.
/// Quand le manifest signe portera l'adresse du serveur, on la prendra de
/// la et on virera ces fallbacks.
fn resolve_auto_connect() -> Option<jvm::ServerAddress> {
    let host = std::env::var("REBORN_SERVER_HOST")
        .ok()
        .filter(|s| !s.trim().is_empty())
        .or_else(|| option_env!("REBORN_SERVER_HOST_BUILD").map(|s| s.to_string()))
        .filter(|s| !s.trim().is_empty())?;
    let host = host.trim().to_string();
    let port = std::env::var("REBORN_SERVER_PORT")
        .ok()
        .and_then(|s| s.trim().parse::<u16>().ok())
        .or_else(|| {
            option_env!("REBORN_SERVER_PORT_BUILD").and_then(|s| s.trim().parse::<u16>().ok())
        })
        .unwrap_or(25565);
    tracing::info!("auto-connect target : {host}:{port}");
    Some(jvm::ServerAddress { host, port })
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

    // Clear Discord Rich Presence — le user n'est plus en jeu.
    if let Some(rpc) = app.try_state::<DiscordRpcState>() {
        rpc.stop().await;
    }
}
