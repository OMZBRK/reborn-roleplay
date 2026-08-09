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
    std::env::var("REBORN_MC_VERSION").unwrap_or_else(|_| "26.1.2".into())
}

/// Mode Builder (staff) : version MC + manifest statique séparés (26.2 + Axiom /
/// opti / shaders, sans mods RP). Le manifest est signé avec la MÊME clé Ed25519
/// que le manifest principal → vérifiable côté launcher sans changement API.
const BUILDER_MC_VERSION: &str = "26.2";
const BUILDER_MANIFEST_URL: &str =
    "https://github.com/OMZBRK/reborn-roleplay/releases/download/builder-v1/builder-manifest-signed.json";
/// Token MC factice utilise quand l'auth dev (sans Microsoft) est en cours.
/// Le client Minecraft accepte n'importe quelle string non-vide ici, mais
/// refusera de valider auprès des serveurs Mojang (mode online). Suffisant
/// pour voir le menu principal et tester le launch chain.
const DEV_PLACEHOLDER_TOKEN: &str = "0";

#[derive(Default)]
pub struct GameState {
    pub running: Mutex<Option<RunningGame>>,
    /// Vrai quand l'arret a ete demande par l'utilisateur (`launcher_stop_game`).
    /// Sert a distinguer un arret volontaire (pas de modal de crash) d'une
    /// sortie non-zero subie (crash → on remonte `game:crashed`).
    pub stop_requested: std::sync::atomic::AtomicBool,
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
    #[error("réservé au staff (grade HELPER ou supérieur)")]
    NotStaff,
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

/// Progression du flux de lancement (etapes [1]..[6]) emise sur l'event
/// `launch:progress`. Sans ca, l'UI affiche "En jeu" pendant toute la
/// preparation (notamment le DL des ~3900 assets au premier lancement) et
/// donne l'impression d'un freeze. `current`/`total` ne sont remplis que
/// pour l'etape assets (la seule a sous-progression utile).
/// Payload de l'event `game:crashed`, emis quand la JVM se termine avec un
/// status non-zero (et que l'arret n'a pas ete demande par l'utilisateur).
/// `stderr_tail` porte les ~100 dernieres lignes de last-stderr.txt pour un
/// affichage immediat dans le modal ; le log complet est recupere a la
/// demande via la commande `read_crash_log`.
#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct GameCrashPayload {
    pub exit_code: Option<i32>,
    pub stderr_path: String,
    pub stderr_tail: String,
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct LaunchProgress {
    pub step: u8,
    pub total_steps: u8,
    pub label: String,
    pub current: Option<usize>,
    pub total: Option<usize>,
}

const LAUNCH_TOTAL_STEPS: u8 = 6;

fn emit_launch<R: Runtime>(
    app: &AppHandle<R>,
    step: u8,
    label: &str,
    current: Option<usize>,
    total: Option<usize>,
) {
    let _ = app.emit(
        "launch:progress",
        LaunchProgress {
            step,
            total_steps: LAUNCH_TOTAL_STEPS,
            label: label.to_string(),
            current,
            total,
        },
    );
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

    // Reset du flag d'arret volontaire : un lancement frais ne doit pas
    // heriter d'un `stop_requested` laisse par une session precedente.
    game.stop_requested
        .store(false, std::sync::atomic::Ordering::SeqCst);

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
    emit_launch(&app, 1, "Runtime Java", None, None);
    let java_path = runtime::ensure_runtime(&auth.download_http, &dir)
        .await
        .map_err(|e| GameError::Runtime {
            message: e.to_string(),
        })?;

    // Etape 2 : version JSON Mojang.
    tracing::info!("launch [2/6] : metadata Minecraft {mc_version}");
    emit_launch(&app, 2, "Métadonnées Minecraft", None, None);
    let version = mojang::fetch_version_json(&auth.download_http, &dir, &mc_version)
        .await
        .map_err(|e| GameError::Mojang {
            message: e.to_string(),
        })?;

    // Etape 3 : libraries + client jar + natives.
    tracing::info!("launch [3/6] : libraries vanilla + natives");
    emit_launch(&app, 3, "Bibliothèques & natives", None, None);
    let lib_setup = libraries::ensure_libraries(&auth.download_http, &dir, &version)
        .await
        .map_err(|e| GameError::Libraries {
            message: e.to_string(),
        })?;

    // Etape 4 : assets (long la premiere fois -> sous-progression emise).
    tracing::info!("launch [4/6] : assets");
    emit_launch(&app, 4, "Téléchargement des ressources", Some(0), None);
    let asset_setup =
        assets::ensure_assets(&auth.download_http, &dir, &version.asset_index, |done, total| {
            emit_launch(
                &app,
                4,
                "Téléchargement des ressources",
                Some(done),
                Some(total),
            );
        })
        .await
        .map_err(|e| GameError::Assets {
            message: e.to_string(),
        })?;

    // Etape 5 : Fabric Loader.
    tracing::info!("launch [5/6] : Fabric Loader");
    emit_launch(&app, 5, "Fabric Loader", None, None);
    let fabric_setup = fabric::ensure_fabric(&auth.download_http, &dir, &mc_version)
        .await
        .map_err(|e| GameError::Fabric {
            message: e.to_string(),
        })?;

    // Etape 6 : assemble + spawn.
    tracing::info!("launch [6/6] : spawn JVM (Fabric {})", fabric_setup.loader_version);
    emit_launch(&app, 6, "Démarrage du jeu", None, None);

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
        dev_server: resolve_dev_server(),
        is_staff: is_staff_role(&user.role),
        play_token_path: Some(play_token_path.display().to_string()),
        api_url: Some(auth.api.base_url.clone()),
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
    let crash_stderr_path = debug_stderr_path.clone();
    tokio::spawn(async move {
        await_game_end(game_state_handle, crash_stderr_path).await;
    });

    Ok(result)
}

/// Lance le **mode Builder (staff-only)** : MC 26.2 + Axiom/opti/shaders (sans
/// mods RP), dans un dossier de jeu SÉPARÉ (`builder/`) pour ne pas mélanger avec
/// le modpack RP. Récupère le manifest builder STATIQUE (signé Ed25519, même
/// clé), télécharge/synchronise ses mods, et se connecte au serveur build (dev).
/// PAS de play-token/attestation (le serveur build n'a pas Guardian).
#[tauri::command]
pub async fn launcher_launch_builder<R: Runtime>(
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
    game.stop_requested
        .store(false, std::sync::atomic::Ordering::SeqCst);

    let user = auth
        .current_user()
        .await
        .ok_or(GameError::NotAuthenticated)?;
    // Gate staff : le mode builder est réservé au staff (HELPER+).
    if !is_staff_role(&user.role) {
        return Err(GameError::NotWhitelisted);
    }

    let mc_access_token = match auth.ensure_mc_token().await {
        Ok(Some(token)) => token,
        _ => DEV_PLACEHOLDER_TOKEN.to_string(),
    };

    // Dossier de jeu builder séparé (mods 26.2 isolés du modpack RP 26.1).
    let dir = paths::game_dir()
        .map_err(|e| GameError::Io { message: e.to_string() })?
        .join("builder");
    tokio::fs::create_dir_all(&dir)
        .await
        .map_err(|e| GameError::Io { message: format!("create builder dir : {e}") })?;

    let mc_version = BUILDER_MC_VERSION.to_string();

    // Manifest builder statique (signé Ed25519, vérifié comme le principal).
    emit_launch(&app, 1, "Manifest builder", None, None);
    let manifest: crate::manifest::SignedManifest = auth
        .download_http
        .get(BUILDER_MANIFEST_URL)
        .send()
        .await
        .and_then(|r| r.error_for_status())
        .map_err(|e| GameError::Mojang { message: format!("fetch builder manifest : {e}") })?
        .json()
        .await
        .map_err(|e| GameError::Mojang { message: format!("parse builder manifest : {e}") })?;
    crate::manifest::verify_signature(&manifest)
        .map_err(|e| GameError::Mojang { message: format!("signature builder invalide : {e}") })?;

    // Sync des mods builder (tous required) + purge des orphelins.
    let prefs_empty = crate::storage::mod_prefs::ModPrefs::default();
    let plan = crate::manifest::compute_plan_export(&manifest, &dir, &prefs_empty)
        .await
        .map_err(|e| GameError::Io { message: format!("plan builder : {e}") })?;
    if !plan.is_empty() {
        emit_launch(&app, 1, "Téléchargement des mods builder", Some(0), Some(plan.len()));
        crate::manifest::download_plan(&app, &auth.download_http, plan, &dir)
            .await
            .map_err(|e| GameError::Io { message: format!("download builder : {e}") })?;
    }
    let _ = crate::manifest::purge_orphan_mods(&manifest, &dir, &prefs_empty).await;

    // Étapes MC (mêmes helpers, version 26.2, dossier builder).
    let user_prefs = prefs::load().await.unwrap_or_default();
    emit_launch(&app, 2, "Runtime Java", None, None);
    let java_path = runtime::ensure_runtime(&auth.download_http, &dir)
        .await
        .map_err(|e| GameError::Runtime { message: e.to_string() })?;
    emit_launch(&app, 3, "Métadonnées Minecraft", None, None);
    let version = mojang::fetch_version_json(&auth.download_http, &dir, &mc_version)
        .await
        .map_err(|e| GameError::Mojang { message: e.to_string() })?;
    emit_launch(&app, 4, "Bibliothèques & natives", None, None);
    let lib_setup = libraries::ensure_libraries(&auth.download_http, &dir, &version)
        .await
        .map_err(|e| GameError::Libraries { message: e.to_string() })?;
    emit_launch(&app, 5, "Ressources", Some(0), None);
    let asset_setup = assets::ensure_assets(&auth.download_http, &dir, &version.asset_index, |done, total| {
        emit_launch(&app, 5, "Ressources", Some(done), Some(total));
    })
    .await
    .map_err(|e| GameError::Assets { message: e.to_string() })?;
    emit_launch(&app, 6, "Fabric Loader", None, None);
    let fabric_setup = fabric::ensure_fabric(&auth.download_http, &dir, &mc_version)
        .await
        .map_err(|e| GameError::Fabric { message: e.to_string() })?;

    emit_launch(&app, 7, "Démarrage (Builder)", None, None);
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
        auto_connect: resolve_dev_server(), // serveur build (dev, 26.2)
        dev_server: resolve_dev_server(),
        is_staff: true,
        play_token_path: None, // pas d'attestation sur le serveur build
        api_url: Some(auth.api.base_url.clone()),
    };

    let mut argv = jvm::build_command(&cfg);
    if let Some(idx) = argv.iter().position(|a| a == "net.minecraft.client.main.Main") {
        argv[idx] = fabric_setup.main_class.clone();
    }

    let debug_stderr_path = dir.join("logs").join("last-stderr.txt");
    let _ = tokio::fs::create_dir_all(dir.join("logs")).await;

    let mut command = Command::new(&java_path);
    command
        .args(&argv)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .stdin(Stdio::null())
        .current_dir(&dir);
    let mut child = command
        .spawn()
        .map_err(|e| GameError::Io { message: format!("spawn java builder : {e}") })?;
    let pid = child.id().unwrap_or(0);
    let java_display = java_path.display().to_string();

    pump_stdio(&mut child, app.clone(), debug_stderr_path.clone());

    let (tamper_tx, _tamper_rx) = mpsc::channel::<TamperingEvent>();
    let watcher = spawn_watcher(app.clone(), vec![dir.join("mods")], tamper_tx)
        .map_err(|e| GameError::Watcher { message: e.to_string() })?;
    {
        let mut lock = game.running.lock().await;
        *lock = Some(RunningGame { child, _watcher: watcher });
    }

    let result = LaunchResult {
        pid,
        java_path: java_display,
        minecraft_version: mc_version.clone(),
        fabric_version: fabric_setup.loader_version.clone(),
    };
    let _ = app.emit("game:started", result.clone());

    let game_state_handle = app.clone();
    let crash_stderr_path = debug_stderr_path.clone();
    tokio::spawn(async move {
        await_game_end(game_state_handle, crash_stderr_path).await;
    });

    Ok(result)
}

/// Lance une **2e instance de jeu (dev, staff-only)** avec un autre compte
/// Microsoft — pour tester à deux (double compte) sans changer les paramètres
/// serveur. Volontairement « fire-and-forget » : on ne l'intègre PAS au cycle
/// de vie de l'instance principale (pas de `game.running`, pas d'events
/// `game:started/exited/crashed`, pas de Rich Presence, pas de bouton stop, pas
/// de watcher). Le staff ferme la fenêtre à la main. Ça garantit ZÉRO impact
/// sur le launch normal des joueurs.
///
/// `alt_uuid` = UUID Minecraft du compte alternatif (déjà sauvegardé dans le
/// carousel : il faut ses refresh tokens MS + Reborn per-uuid). Le compte doit
/// être whitelisté (role != PLAYER) pour obtenir un play-token et passer
/// Guardian. L'appelant (compte courant) doit être staff.
#[tauri::command]
pub async fn launcher_launch_second_instance<R: Runtime>(
    _app: AppHandle<R>,
    auth: State<'_, AuthState>,
    alt_uuid: String,
) -> Result<LaunchResult, GameError> {
    // Gate staff : la fonctionnalité n'est ouverte qu'au staff (compte courant).
    let current = auth.current_user().await.ok_or(GameError::NotAuthenticated)?;
    if !is_staff_role(&current.role) {
        return Err(GameError::NotStaff);
    }

    // Prépare le compte alt SANS toucher la session courante.
    let alt = auth
        .prepare_alt_account(&alt_uuid)
        .await
        .map_err(|e| GameError::Io {
            message: format!("compte alt : {e}"),
        })?;
    if alt.user.role == "PLAYER" {
        return Err(GameError::NotWhitelisted);
    }
    tracing::info!(
        "2e instance : compte alt {} (role {})",
        alt.user.minecraft_username,
        alt.user.role
    );

    let dir = paths::game_dir().map_err(|e| GameError::Io {
        message: e.to_string(),
    })?;

    // Play-token du compte alt (fichier séparé pour ne pas écraser celui de
    // l'instance principale).
    let play_session = auth
        .api
        .fetch_play_token(&alt.reborn_jwt)
        .await
        .map_err(|e| GameError::PlayToken {
            message: e.to_string(),
        })?;
    let play_token_path = dir.join(".reborn-play-token-alt");
    tokio::fs::write(&play_token_path, play_session.play_token.as_bytes())
        .await
        .map_err(|e| GameError::Io {
            message: format!("write play-token alt : {e}"),
        })?;

    let user_prefs = prefs::load().await.unwrap_or_default();
    let mc_version = minecraft_version();

    // Setup partagé (runtime / version / libs / assets / fabric) — ces
    // fonctions sont idempotentes et déjà en cache (l'instance principale les a
    // téléchargées), donc quasi instantané. On les rappelle ici plutôt que de
    // refactorer `launcher_launch_game`, pour ne prendre AUCUN risque sur le
    // chemin de lancement principal.
    let java_path = runtime::ensure_runtime(&auth.download_http, &dir)
        .await
        .map_err(|e| GameError::Runtime {
            message: e.to_string(),
        })?;
    let version = mojang::fetch_version_json(&auth.download_http, &dir, &mc_version)
        .await
        .map_err(|e| GameError::Mojang {
            message: e.to_string(),
        })?;
    let lib_setup = libraries::ensure_libraries(&auth.download_http, &dir, &version)
        .await
        .map_err(|e| GameError::Libraries {
            message: e.to_string(),
        })?;
    let asset_setup = assets::ensure_assets(&auth.download_http, &dir, &version.asset_index, |_, _| {})
        .await
        .map_err(|e| GameError::Assets {
            message: e.to_string(),
        })?;
    let fabric_setup = fabric::ensure_fabric(&auth.download_http, &dir, &mc_version)
        .await
        .map_err(|e| GameError::Fabric {
            message: e.to_string(),
        })?;

    let mut vanilla_libs = lib_setup.library_jars.clone();
    vanilla_libs.extend(fabric_setup.library_jars.clone());
    let all_libs = dedupe_classpath(vanilla_libs);

    let cfg = jvm::LaunchConfig {
        minecraft_version: mc_version.clone(),
        launcher_version: env!("CARGO_PKG_VERSION").to_string(),
        minecraft_username: alt.user.minecraft_username.clone(),
        minecraft_uuid: alt.user.minecraft_uuid.clone(),
        mc_access_token: alt.mc_access_token.clone(),
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
        dev_server: resolve_dev_server(),
        is_staff: is_staff_role(&alt.user.role),
        play_token_path: Some(play_token_path.display().to_string()),
        api_url: Some(auth.api.base_url.clone()),
    };

    let mut argv = jvm::build_command(&cfg);
    if let Some(idx) = argv
        .iter()
        .position(|a| a == "net.minecraft.client.main.Main")
    {
        argv[idx] = fabric_setup.main_class.clone();
    }

    // stdout/stderr → un log dédié (pas d'analyzer ni de pipeline de crash).
    let _ = tokio::fs::create_dir_all(dir.join("logs")).await;
    let alt_log = dir.join("logs").join("alt-instance.log");
    let log_out = std::fs::File::create(&alt_log).map_err(|e| GameError::Io {
        message: format!("log alt : {e}"),
    })?;
    let log_err = log_out.try_clone().map_err(|e| GameError::Io {
        message: e.to_string(),
    })?;

    let mut command = Command::new(&java_path);
    command
        .args(&argv)
        .stdout(Stdio::from(log_out))
        .stderr(Stdio::from(log_err))
        .stdin(Stdio::null())
        .current_dir(&dir);

    // Détaché : on ne conserve pas le Child (tokio n'a pas kill_on_drop par
    // défaut → le process survit au drop). Le staff ferme la fenêtre à la main.
    let child = command.spawn().map_err(|e| GameError::Io {
        message: format!("spawn 2e instance : {e}"),
    })?;
    let pid = child.id().unwrap_or(0);
    drop(child);

    tracing::info!(
        "2e instance (dev) lancée : {} (pid {}), logs → {}",
        alt.user.minecraft_username,
        pid,
        alt_log.display()
    );

    Ok(LaunchResult {
        pid,
        java_path: java_path.display().to_string(),
        minecraft_version: mc_version,
        fabric_version: fabric_setup.loader_version,
    })
}

#[tauri::command]
pub async fn launcher_stop_game(game: State<'_, GameState>) -> Result<(), GameError> {
    let mut lock = game.running.lock().await;
    let Some(running) = lock.as_mut() else {
        return Err(GameError::NotRunning);
    };
    // Marque l'arret comme volontaire AVANT le kill : `await_game_end` lira
    // ce flag pour ne pas remonter un faux `game:crashed` (un kill produit un
    // exit-code non-zero qui ressemble a un crash).
    game.stop_requested
        .store(true, std::sync::atomic::Ordering::SeqCst);
    running.child.start_kill().map_err(|e| GameError::Io {
        message: e.to_string(),
    })?;
    Ok(())
}

/// Taille max lue par `read_crash_log` : on plafonne a 200 Ko (derniers
/// octets) pour eviter de charger un last-stderr.txt de plusieurs Mo dans
/// la WebView.
const CRASH_LOG_MAX_BYTES: u64 = 200 * 1024;

/// Lit le contenu d'un fichier log pour affichage dans le modal de crash.
/// Plafonne a `CRASH_LOG_MAX_BYTES` (on garde la fin, la plus pertinente).
/// Restreint la lecture au dossier de jeu pour ne pas exposer un read
/// arbitraire de fichiers via l'IPC.
#[tauri::command]
pub async fn read_crash_log(path: String) -> Result<String, GameError> {
    use tokio::io::{AsyncReadExt, AsyncSeekExt};

    let requested = PathBuf::from(&path);
    let canon = tokio::fs::canonicalize(&requested)
        .await
        .map_err(|e| GameError::Io {
            message: format!("chemin log introuvable : {e}"),
        })?;

    let dir = paths::game_dir().map_err(|e| GameError::Io {
        message: e.to_string(),
    })?;
    let dir_canon = tokio::fs::canonicalize(&dir).await.unwrap_or(dir);
    if !canon.starts_with(&dir_canon) {
        return Err(GameError::Io {
            message: "chemin log hors du dossier de jeu".into(),
        });
    }

    let meta = tokio::fs::metadata(&canon)
        .await
        .map_err(|e| GameError::Io {
            message: e.to_string(),
        })?;
    let len = meta.len();

    if len <= CRASH_LOG_MAX_BYTES {
        return tokio::fs::read_to_string(&canon)
            .await
            .map_err(|e| GameError::Io {
                message: e.to_string(),
            });
    }

    let mut file = tokio::fs::File::open(&canon)
        .await
        .map_err(|e| GameError::Io {
            message: e.to_string(),
        })?;
    file.seek(std::io::SeekFrom::Start(len - CRASH_LOG_MAX_BYTES))
        .await
        .map_err(|e| GameError::Io {
            message: e.to_string(),
        })?;
    let mut buf = Vec::with_capacity(CRASH_LOG_MAX_BYTES as usize);
    file.read_to_end(&mut buf)
        .await
        .map_err(|e| GameError::Io {
            message: e.to_string(),
        })?;
    let text = String::from_utf8_lossy(&buf).into_owned();
    // Le seek peut tomber au milieu d'une ligne (et d'un caractere UTF-8) :
    // on jette le premier fragment partiel et on signale la troncature.
    let body = match text.find('\n') {
        Some(idx) => &text[idx + 1..],
        None => &text,
    };
    Ok(format!("[… log tronqué — derniers {} Ko …]\n{body}", CRASH_LOG_MAX_BYTES / 1024))
}

/// Lit les `max_lines` dernieres lignes d'un fichier, best-effort (chaine
/// vide si le fichier n'existe pas / illisible).
async fn read_stderr_tail(path: &Path, max_lines: usize) -> String {
    match tokio::fs::read_to_string(path).await {
        Ok(content) => {
            let lines: Vec<&str> = content.lines().collect();
            let start = lines.len().saturating_sub(max_lines);
            lines[start..].join("\n")
        }
        Err(_) => String::new(),
    }
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

/// Resout l'adresse du serveur de DEV (build/dev pendant le développement).
/// Même priorité que `resolve_auto_connect` : env runtime
/// `REBORN_SERVER_DEV_HOST` > compile-time `REBORN_SERVER_DEV_HOST_BUILD`.
/// None = pas de serveur dev configuré (le mod n'affiche alors que le serveur
/// principal, aucun toggle). Le port par défaut suit le serveur principal.
fn resolve_dev_server() -> Option<jvm::ServerAddress> {
    let host = std::env::var("REBORN_SERVER_DEV_HOST")
        .ok()
        .filter(|s| !s.trim().is_empty())
        .or_else(|| option_env!("REBORN_SERVER_DEV_HOST_BUILD").map(|s| s.to_string()))
        .filter(|s| !s.trim().is_empty())?;
    let host = host.trim().to_string();
    let port = std::env::var("REBORN_SERVER_DEV_PORT")
        .ok()
        .and_then(|s| s.trim().parse::<u16>().ok())
        .or_else(|| {
            option_env!("REBORN_SERVER_DEV_PORT_BUILD").and_then(|s| s.trim().parse::<u16>().ok())
        })
        .unwrap_or(25565);
    tracing::info!("dev server target : {host}:{port}");
    Some(jvm::ServerAddress { host, port })
}

/// Un compte est « staff » dès le grade HELPER (au-dessus de WHITELISTED).
/// Débloque les features dev côté mod (sélecteur Build/Dev, 2e instance).
/// Rôles API : PLAYER < WHITELISTED < HELPER < MODERATOR < WHITELIST_REVIEWER
/// < ADMIN < OWNER (cf schema.prisma). On liste explicitement le staff pour
/// éviter qu'un futur rôle non-staff passe par erreur.
fn is_staff_role(role: &str) -> bool {
    matches!(
        role,
        "HELPER" | "MODERATOR" | "WHITELIST_REVIEWER" | "ADMIN" | "OWNER"
    )
}

async fn await_game_end<R: Runtime>(app: AppHandle<R>, stderr_path: PathBuf) {
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

    // `code()` est None quand le process a ete tue par un signal (unix) ou
    // dans certains cas de kill ; on traite ce cas comme un echec sauf si
    // l'arret etait volontaire.
    let code = exit_status.and_then(|s| s.code());
    let stop_requested = state
        .stop_requested
        .swap(false, std::sync::atomic::Ordering::SeqCst);

    // On continue d'emettre `game:exited` pour le chemin normal (PlayButton
    // remet la phase a "ready" dessus, crash ou pas).
    let _ = app.emit("game:exited", code.unwrap_or(-1));

    let is_crash = !stop_requested && code != Some(0);
    if is_crash {
        let stderr_tail = read_stderr_tail(&stderr_path, 100).await;
        let payload = GameCrashPayload {
            exit_code: code,
            stderr_path: stderr_path.display().to_string(),
            stderr_tail,
        };
        tracing::warn!("game crash detecte (exit={:?}) — emit game:crashed", code);
        let _ = app.emit("game:crashed", payload);
    } else {
        tracing::info!("game process exited with code {:?}", code);
    }

    // Clear Discord Rich Presence — le user n'est plus en jeu.
    if let Some(rpc) = app.try_state::<DiscordRpcState>() {
        rpc.stop().await;
    }
}
