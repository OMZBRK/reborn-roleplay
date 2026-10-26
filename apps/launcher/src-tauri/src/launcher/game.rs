//! Orchestrateur du flow "click Jouer" → JVM en cours d'exécution.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §8.1 étapes [5]..[13].
//!
//! Étapes implémentées :
//! 1. JRE Mojang via piston-meta (cf runtime.rs)
//! 2. Version json Mojang pour la version Minecraft cible
//! 3. Client jar + libraries vanilla + extraction natives
//! 4. Asset index + assets
//! 5. Fabric Loader (override main class + libs supplémentaires)
//! 6. Play-token + build du LaunchConfig + spawn java avec argv complet
//! 7. FS watcher + journal (cf logs.rs) + rafraîchissement des jetons de jeu
//!    (cf play_tokens.rs) + lifecycle events Tauri
//!
//! Cycle de vie : le `Child` appartient à la tâche [`await_game_end`], qui
//! l'attend hors de tout verrou. `launcher_stop_game` lui envoie une demande
//! d'arrêt par canal et attend l'accusé « processus terminé ». (Avant, le
//! `wait()` se faisait verrou tenu : « Arrêter le jeu » restait bloqué
//! jusqu'à ce que le joueur ferme Minecraft lui-même.)

use serde::Serialize;
use std::fmt::Display;
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{mpsc as std_mpsc, Arc, Mutex, MutexGuard, PoisonError};
use std::time::Duration;
use tauri::{AppHandle, Emitter, Manager, Runtime, State};
use tokio::process::{Child, Command};
use tokio::sync::{mpsc, oneshot};
use tokio::task::JoinHandle;

use crate::api::PlaySession;
use crate::auth::{AuthError, AuthState};
use crate::content::candidature;
use crate::discord_rpc::DiscordRpcState;
use crate::integrity::{spawn_watcher, TamperingEvent, WatcherHandle};
use crate::launcher::play_tokens::{self, JwtSource, TokenRefresher, TokenTarget};
use crate::launcher::{
    assets, emit_mods_purged, fabric, jvm, libraries, logs, mods as mods_inspect, mojang, paths,
    runtime, serialize_ipc_error,
};
use crate::storage::prefs::{self, OnGameStart, Preferences};
use crate::storage::secrets::SecretKey;

/// Version Minecraft cible. Plus tard, viendra du manifest courant Reborn.
/// En attendant, lisible via REBORN_MC_VERSION pour faciliter l'alignement
/// avec le serveur de dev sans recompiler.
pub(crate) fn minecraft_version() -> String {
    std::env::var("REBORN_MC_VERSION").unwrap_or_else(|_| "26.2".into())
}

/// Token MC factice utilisé quand l'auth dev (sans Microsoft) est en cours.
/// Le client Minecraft accepte n'importe quelle string non-vide ici, mais
/// refusera de valider auprès des serveurs Mojang (mode online). Suffisant
/// pour voir le menu principal et tester le launch chain. Debug uniquement :
/// en release, l'absence de jeton est une erreur (cf [`resolve_mc_token`]).
const DEV_PLACEHOLDER_TOKEN: &str = "0";

/// Délai max d'attente de la fermeture du jeu après `launcher_stop_game`.
const STOP_TIMEOUT: Duration = Duration::from_secs(10);
/// Délai laissé aux pumps pour vider stdout/stderr une fois la JVM sortie.
const PIPELINE_DRAIN_TIMEOUT: Duration = Duration::from_secs(3);
/// Lignes de journal jointes à l'event `game:crashed`.
const CRASH_TAIL_LINES: usize = 100;

/// Demande d'arrêt envoyée à la tâche qui possède le `Child` ; elle répond
/// quand le processus est réellement terminé (ou si le kill a échoué).
type KillAck = oneshot::Sender<std::io::Result<()>>;

#[derive(Default)]
pub struct GameState {
    /// Posé dès l'entrée de `launcher_launch_game`, relâché à la fin (succès
    /// ou échec) par [`LaunchGuard`] : un double clic « Jouer » ne lance plus
    /// deux préparations (et deux JVM) en parallèle.
    launching: AtomicBool,
    running: Mutex<Option<RunningGame>>,
    /// Vrai quand l'arrêt a été demandé par l'utilisateur (`launcher_stop_game`).
    /// Sert à distinguer un arrêt volontaire (pas de modal de crash) d'une
    /// sortie non-zéro subie (crash → on remonte `game:crashed`).
    stop_requested: AtomicBool,
    /// Journal de la partie en cours, ou de la dernière jouée.
    pub logs: Arc<logs::LogStore>,
}

pub struct RunningGame {
    pid: u32,
    kill_tx: mpsc::Sender<KillAck>,
    _watcher: WatcherHandle,
    /// Rafraîchissement des jetons de jeu, arrêté avec la partie.
    _play_tokens: TokenRefresher,
}

impl GameState {
    pub fn new() -> Self {
        Self::default()
    }

    fn running_lock(&self) -> MutexGuard<'_, Option<RunningGame>> {
        self.running.lock().unwrap_or_else(PoisonError::into_inner)
    }

    /// Vrai si une partie tourne (ou se prépare).
    pub fn is_busy(&self) -> bool {
        self.launching.load(Ordering::SeqCst) || self.running_lock().is_some()
    }

    /// Sortie du launcher : coupe le watcher et oublie la partie en cours.
    /// Le processus du jeu n'est PAS tué : lancé sans `kill_on_drop`, il
    /// continue seul (ses stdout/stderr orphelins échouent en silence côté
    /// Java). Renvoie son pid pour le journal.
    pub fn detach_for_exit(&self) -> Option<u32> {
        self.running_lock().take().map(|game| game.pid)
    }
}

/// Verrou RAII du lancement : relâche `launching` quoi qu'il arrive (retour
/// anticipé par `?` compris).
struct LaunchGuard<'a>(&'a AtomicBool);

impl<'a> LaunchGuard<'a> {
    fn acquire(state: &'a GameState) -> Result<Self, GameError> {
        if state.running_lock().is_some() || state.launching.swap(true, Ordering::SeqCst) {
            return Err(GameError::AlreadyRunning);
        }
        Ok(Self(&state.launching))
    }
}

impl Drop for LaunchGuard<'_> {
    fn drop(&mut self) {
        self.0.store(false, Ordering::SeqCst);
    }
}

/// Erreurs des commandes de jeu. Sérialisées pour le front en
/// `{ kind, message }` (message = phrase complète, cause réelle comprise).
#[derive(Debug, thiserror::Error)]
pub enum GameError {
    #[error("Tu n'es pas connecté : connecte-toi avant de lancer le jeu.")]
    NotAuthenticated,
    #[error("Ta candidature whitelist n'a pas encore été acceptée : tu pourras rejoindre le serveur dès qu'elle le sera.")]
    NotWhitelisted,
    /// Lancement refusé d'après la phase de la candidature (cf
    /// `content::candidature::decider_acces`). `kind` : `candidature_required`,
    /// `douane_hors_jeu`, `douane_not_open`, `douane_closed`, `not_whitelisted`
    /// face à une API d'avant la candidature v2, ou `session_expired`.
    #[error("{message}")]
    Acces { kind: &'static str, message: String },
    /// Phase de la candidature illisible (réseau, session expirée…).
    #[error("Vérification de ta candidature impossible : {message}.")]
    AccesInverifiable { message: String },
    #[error("Réservé au staff (grade Helper ou supérieur).")]
    NotStaff,
    #[error("Le jeu est déjà lancé ou en cours de lancement : attends qu'il s'ouvre, ou ferme-le avant de relancer.")]
    AlreadyRunning,
    #[error("Aucune partie en cours à arrêter.")]
    NotRunning,
    #[error("Ferme le jeu avant de {action}.")]
    GameRunning { action: &'static str },
    #[error("{message}")]
    McSessionExpired { message: String },
    #[error("{message}")]
    McSessionUnreachable { message: String },
    #[error("Lecture de ta session dans le coffre de Windows impossible ({message}) : déconnecte-toi puis reconnecte-toi.")]
    Storage { message: String },
    #[error("{message}")]
    Io { message: String },
    // Étapes d'installation : `message` vient de `install_cause` (cause
    // réelle avec sa propre consigne ; « vérifie ta connexion » seulement
    // pour une cause réseau qui n'en porte pas déjà une).
    #[error("Préparation de Java impossible : {message}.")]
    Runtime { message: String },
    #[error("Téléchargement des métadonnées Minecraft impossible : {message}.")]
    Mojang { message: String },
    #[error("Installation des bibliothèques Minecraft impossible : {message}.")]
    Libraries { message: String },
    #[error("Téléchargement des ressources du jeu impossible : {message}.")]
    Assets { message: String },
    #[error("Installation de Fabric Loader impossible : {message}.")]
    Fabric { message: String },
    #[error("Surveillance des dossiers mods/config impossible : {message}.")]
    Watcher { message: String },
    #[error(
        "Impossible d'obtenir l'autorisation de connexion au serveur (play-token) : {message}."
    )]
    PlayToken { message: String },
    #[error("{message}")]
    SameAccount { message: String },
    #[error("Préparation du compte secondaire impossible : {message}.")]
    AltAccount { message: String },
    #[error("Impossible de démarrer Minecraft : {message}")]
    Spawn { message: String },
    #[error("Impossible d'arrêter le jeu : {message}")]
    Kill { message: String },
    #[error("Aucun journal à exporter : lance le jeu au moins une fois.")]
    LogsEmpty,
}

impl GameError {
    fn kind(&self) -> &'static str {
        match self {
            Self::NotAuthenticated => "not_authenticated",
            Self::NotWhitelisted => "not_whitelisted",
            Self::Acces { kind, .. } => kind,
            Self::AccesInverifiable { .. } => "access_check",
            Self::NotStaff => "not_staff",
            Self::AlreadyRunning => "already_running",
            Self::NotRunning => "not_running",
            Self::GameRunning { .. } => "game_running",
            Self::McSessionExpired { .. } => "mc_session_expired",
            Self::McSessionUnreachable { .. } => "mc_session_unreachable",
            Self::Storage { .. } => "storage",
            Self::Io { .. } => "io",
            Self::Runtime { .. } => "runtime",
            Self::Mojang { .. } => "mojang",
            Self::Libraries { .. } => "libraries",
            Self::Assets { .. } => "assets",
            Self::Fabric { .. } => "fabric",
            Self::Watcher { .. } => "watcher",
            Self::PlayToken { .. } => "play_token",
            Self::SameAccount { .. } => "same_account",
            Self::AltAccount { .. } => "alt_account",
            Self::Spawn { .. } => "spawn",
            Self::Kill { .. } => "kill",
            Self::LogsEmpty => "logs_empty",
        }
    }
}

impl Serialize for GameError {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serialize_ipc_error(serializer, self.kind(), &self.to_string())
    }
}

/// Texte d'une cause, sans point final (on l'insère au milieu d'une phrase).
fn cause(e: impl Display) -> String {
    e.to_string().trim_end().trim_end_matches('.').to_string()
}

/// Cause d'une étape d'installation, insérée après « … impossible : » :
/// première lettre en minuscule, et consigne réseau ajoutée seulement si la
/// cause est réseau et ne dit pas déjà quoi faire (« — … »). Une cause disque,
/// de hash ou de quarantaine garde sa propre consigne, sans « vérifie ta
/// connexion » contradictoire.
fn install_cause(e: impl Display, network: bool) -> String {
    let text = mojang::lower_first(&cause(e));
    if network && !text.contains(" — ") {
        format!("{text}. Vérifie ta connexion puis relance")
    } else {
        text
    }
}

/// Dossier de données du launcher (`%APPDATA%\RebornRoleplay`).
pub(crate) fn data_dir() -> Result<PathBuf, GameError> {
    paths::game_dir().map_err(|e| GameError::Io {
        message: format!(
            "Dossier de données du launcher introuvable ({}) : la variable d'environnement APPDATA est absente de ta session.",
            cause(e)
        ),
    })
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct LaunchResult {
    pub pid: u32,
    pub java_path: String,
    pub minecraft_version: String,
    pub fabric_version: String,
    /// Portée du jeton de jeu du serveur principal, `play` ou `douane`, telle
    /// que l'API l'a émise (`None` : API d'avant le 25/10).
    pub scope: Option<String>,
    /// Serveur visé par ce jeton (claim `aud`), `None` si l'API n'en émet pas.
    pub server_id: Option<String>,
}

/// Payload de l'event `game:crashed`, émis quand la JVM se termine avec un
/// status non-zéro (et que l'arrêt n'a pas été demandé par l'utilisateur).
/// `stderr_tail` porte les ~100 dernières lignes du journal (stdout ET
/// stderr : le rapport de crash Minecraft sort sur stdout) ; `stderr_path`
/// pointe sur `logs/last-crash.log` (journal complet de la partie), ou sur
/// `last-stderr.txt` si le journal est vide. Le front affiche ce fichier
/// dans l'Explorateur et ouvre le panneau Journal (`launcher_logs_snapshot`).
#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct GameCrashPayload {
    pub exit_code: Option<i32>,
    pub stderr_path: String,
    pub stderr_tail: String,
}

/// Progression du flux de lancement (étapes [1]..[6]) émise sur l'event
/// `launch:progress`. Sans ça, l'UI affiche "En jeu" pendant toute la
/// préparation (notamment le DL des ~3900 assets au premier lancement) et
/// donne l'impression d'un freeze. `current`/`total` ne sont remplis que
/// pour l'étape assets (la seule à sous-progression utile).
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
/// « Préparer le jeu » : les étapes 1 à 5 seulement (ni jeton ni démarrage).
const PREPARE_TOTAL_STEPS: u8 = 5;

fn emit_steps<R: Runtime>(
    app: &AppHandle<R>,
    total_steps: u8,
    step: u8,
    label: &str,
    current: Option<usize>,
    total: Option<usize>,
) {
    let _ = app.emit(
        "launch:progress",
        LaunchProgress {
            step,
            total_steps,
            label: label.to_string(),
            current,
            total,
        },
    );
}

fn emit_launch<R: Runtime>(
    app: &AppHandle<R>,
    step: u8,
    label: &str,
    current: Option<usize>,
    total: Option<usize>,
) {
    emit_steps(app, LAUNCH_TOTAL_STEPS, step, label, current, total);
}

/// Étape 0 du lancement et de la préparation : retire les mods qui ne ciblent
/// pas la version MC active. Quand on bouge entre versions (ex: 1.21.4 ->
/// 1.21.1), des jars Sodium / Iris / etc. d'une autre version restent et
/// plantent Fabric Loader au boot. Cf launcher/diagnostics.rs::parse_mod_version_mismatch.
fn purge_before_install<R: Runtime>(app: &AppHandle<R>, dir: &Path, mc_version: &str) {
    match mods_inspect::purge_incompatible_mods(&dir.join("mods"), mc_version) {
        Ok(removed) if !removed.is_empty() => {
            tracing::info!(
                "nettoyage mods : {} jar(s) incompatible(s) supprimé(s) : {removed:?}",
                removed.len()
            );
            emit_mods_purged(app, removed, mc_version);
        }
        Ok(_) => {}
        Err(e) => tracing::warn!("inspection mods impossible : {e}"),
    }
}

/// Retour de `launcher_prepare_game`.
#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct PreparedGame {
    pub minecraft_version: String,
    pub fabric_version: String,
}

/// « Préparer le jeu » : installe tout ce que demande un lancement (Java,
/// bibliothèques, ~3 900 ressources, Fabric), SANS jeton de jeu ni démarrage.
///
/// Pour un candidat avant sa séance de douane : le premier téléchargement se
/// fait hors de la fenêtre d'entrée (H − 15 min à H + 30 min), qui ne garde
/// que le dernier pas (jeton et démarrage, quelques secondes). Aucune
/// barrière d'accès ici : rien n'est lancé, et le lancement relit l'accès.
/// Même progression que le lancement (`launch:progress`), sur 5 étapes.
#[tauri::command]
pub async fn launcher_prepare_game<R: Runtime>(
    app: AppHandle<R>,
    auth: State<'_, AuthState>,
    game: State<'_, GameState>,
) -> Result<PreparedGame, GameError> {
    let _launching = LaunchGuard::acquire(&game)?;
    auth.current_user()
        .await
        .ok_or(GameError::NotAuthenticated)?;
    let dir = data_dir()?;
    let mc_version = minecraft_version();

    purge_before_install(&app, &dir, &mc_version);
    let setup = prepare_install(&auth, &dir, &mc_version, |step, label, current, total| {
        emit_steps(&app, PREPARE_TOTAL_STEPS, step, label, current, total)
    })
    .await?;
    tracing::info!(
        "jeu préparé : Minecraft {mc_version}, Fabric {}",
        setup.fabric.loader_version
    );
    Ok(PreparedGame {
        minecraft_version: mc_version,
        fabric_version: setup.fabric.loader_version,
    })
}

#[tauri::command]
pub async fn launcher_launch_game<R: Runtime>(
    app: AppHandle<R>,
    auth: State<'_, AuthState>,
    game: State<'_, GameState>,
) -> Result<LaunchResult, GameError> {
    let _launching = LaunchGuard::acquire(&game)?;

    // Reset du flag d'arrêt volontaire : un lancement frais ne doit pas
    // hériter d'un `stop_requested` laissé par une session précédente.
    game.stop_requested.store(false, Ordering::SeqCst);

    let user = auth
        .current_user()
        .await
        .ok_or(GameError::NotAuthenticated)?;

    // Barrière d'accès, lue AVANT l'installation (qui peut durer plusieurs
    // minutes) : par la PHASE de la candidature, plus seulement le rôle
    // (CANDIDATURE_ET_DOUANE.md §3.3). Whitelisté ou staff : jeu normal.
    // Candidat dont l'écrit est accepté, dans la fenêtre d'entrée de sa
    // séance (H − 15 min à H + 30 min) : lancement en portée « douane ».
    // Sinon : message selon la phase. L'API reste l'autorité (jeton refusé
    // en 403 sinon), Guardian et ShinobiCore aussi côté serveur.
    let access_jwt = reborn_jwt(&auth)?;
    let acces = candidature::controler_acces(&auth, &access_jwt, &user.role)
        .await
        .map_err(|e| match e.status() {
            // Session expirée : même `kind` que les autres commandes, pour
            // que le front renouvelle la session ou renvoie à la connexion.
            Some(401) => GameError::Acces {
                kind: e.kind(),
                message: e.to_string(),
            },
            _ => GameError::AccesInverifiable {
                message: mojang::lower_first(&cause(e)),
            },
        })?;
    if let Some(refus) = acces.refus {
        return Err(GameError::Acces {
            kind: refus.kind,
            message: refus.message,
        });
    }
    // Rôle relu par le contrôle (promotion après l'oral) : sert au drapeau
    // staff du mod et au jeton du serveur de dev.
    let user = auth.current_user().await.unwrap_or(user);
    tracing::info!(
        "accès au jeu : phase {}, portée attendue {:?}",
        acces.phase.as_deref().unwrap_or("?"),
        acces.portee
    );

    let mc_access_token = resolve_mc_token(&auth).await?;
    let dir = data_dir()?;
    let user_prefs = prefs::load().await.unwrap_or_default();
    let mc_version = minecraft_version();

    // Étape 0 : nettoie les mods qui ne ciblent pas la version MC active.
    purge_before_install(&app, &dir, &mc_version);

    // Étapes 1..5 : runtime, metadata, libraries, assets, Fabric.
    let setup = prepare_install(&auth, &dir, &mc_version, |step, label, current, total| {
        emit_launch(&app, step, label, current, total)
    })
    .await?;

    // Étape 6 : play-token + assemble + spawn.
    tracing::info!(
        "launch [6/6] : spawn JVM (Fabric {})",
        setup.fabric.loader_version
    );
    emit_launch(&app, 6, "Démarrage du jeu", None, None);

    // Play-token : signé HMAC par l'API, relu par le mod Reborn Integrity à
    // chaque connexion à un serveur et poussé via custom payload
    // `reborn:auth`. Sans ça, le plugin Guardian kick le joueur à T+8s du
    // JOIN. Demandé ICI, juste avant le spawn, et non en tête : il ne vit
    // que 5 min, et un premier lancement (JRE + ~3900 assets) dépasse souvent
    // ce délai ; ensuite, `play_tokens` le réécrit pendant toute la partie.
    // Jeton relu dans le coffre : le front a pu renouveler la session
    // entre-temps.
    let reborn_token = reborn_jwt(&auth)?;
    let is_staff = is_staff_role(&user.role);
    let mut tokens =
        write_play_tokens(&auth, &reborn_token, &dir, ".reborn-play-token", is_staff).await?;
    if acces.portee == Some(candidature::Portee::Douane)
        && tokens.scope.as_deref() != Some("douane")
    {
        tracing::warn!(
            "portée attendue « douane », jeton émis en {:?} (l'API fait foi)",
            tokens.scope
        );
    }

    let identity = Identity {
        username: &user.minecraft_username,
        uuid: &user.minecraft_uuid,
        is_staff,
    };
    let argv = build_argv(
        &setup,
        &mc_version,
        &dir,
        &user_prefs,
        &identity,
        mc_access_token.clone(),
        &tokens,
        &auth.api.base_url,
    );

    // Copie de l'argv (jeton masqué) pour pouvoir reproduire la commande à
    // la main, et stderr teeté en temps réel dans last-stderr.txt (les logs
    // tracing ratent quand le process meurt en <1s).
    let logs_dir = dir.join("logs");
    let _ = tokio::fs::create_dir_all(&logs_dir).await;
    let argv_path = logs_dir.join("last-argv.txt");
    let stderr_path = logs_dir.join("last-stderr.txt");
    let dump = redact_argv(&argv)
        .iter()
        .map(|s| format!("{s:?}"))
        .collect::<Vec<_>>()
        .join("\n");
    if let Err(e) = tokio::fs::write(&argv_path, dump).await {
        tracing::warn!("écriture de {} impossible : {e}", argv_path.display());
    }

    // FS watcher armé AVANT le spawn : s'il échoue, on n'a pas encore de
    // JVM orpheline sur les bras (avant, elle tournait sans être suivie).
    let (tamper_tx, _tamper_rx) = std_mpsc::channel::<TamperingEvent>();
    let watcher = spawn_watcher(
        app.clone(),
        vec![dir.join("mods"), dir.join("config")],
        tamper_tx,
    )
    .map_err(|e| GameError::Watcher { message: cause(e) })?;

    let mut child = Command::new(&setup.java_path)
        .args(&argv)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .stdin(Stdio::null())
        .current_dir(&dir)
        .spawn()
        .map_err(|e| spawn_error(&setup.java_path, &e))?;
    let pid = child.id().unwrap_or(0);

    // Journal : nouvelle partie = tampon vidé.
    game.logs.clear();
    let minimize = user_prefs.on_game_start == OnGameStart::Minimize;
    let ready_app = app.clone();
    let pipeline = logs::spawn_pipeline(
        app.clone(),
        &mut child,
        game.logs.clone(),
        stderr_path.clone(),
        vec![mc_access_token],
        move || {
            // Effacement : le launcher se réduit quand la fenêtre du jeu est
            // vraiment là (pas au spawn, sinon on regarde un bureau vide).
            if minimize {
                if let Some(win) = ready_app.get_webview_window("main") {
                    let _ = win.minimize();
                }
            }
        },
    );

    // Jetons tenus à jour tant que la partie tourne (5 min de validité, lus
    // par le mod à la connexion, qui peut venir bien plus tard).
    let refresher = play_tokens::spawn_refresher(
        app.clone(),
        JwtSource::Session {
            mc_uuid: user.minecraft_uuid.clone(),
        },
        std::mem::take(&mut tokens.targets),
    );

    let (kill_tx, kill_rx) = mpsc::channel::<KillAck>(4);
    *game.running_lock() = Some(RunningGame {
        pid,
        kill_tx,
        _watcher: watcher,
        _play_tokens: refresher,
    });

    let result = LaunchResult {
        pid,
        java_path: setup.java_path.display().to_string(),
        minecraft_version: mc_version,
        fabric_version: setup.fabric.loader_version.clone(),
        scope: tokens.scope.clone(),
        server_id: tokens.server_id.clone(),
    };
    tracing::info!("Minecraft lancé (pid {pid})");
    let _ = app.emit("game:started", result.clone());
    crate::tray::set_in_game(&app, true);

    // Discord Rich Presence — best-effort, ne bloque pas le launch, et
    // respecte le réglage du joueur.
    if user_prefs.discord_rich_presence {
        if let Some(rpc) = app.try_state::<DiscordRpcState>() {
            rpc.start_in_game(&result.minecraft_version).await;
        }
    }

    tokio::spawn(await_game_end(
        app.clone(),
        child,
        kill_rx,
        pipeline,
        stderr_path,
    ));

    Ok(result)
}

/// Lance une **2e instance de jeu (dev, staff-only)** avec un autre compte
/// Microsoft — pour tester à deux (double compte) sans changer les paramètres
/// serveur. Volontairement « fire-and-forget » : on ne l'intègre PAS au cycle
/// de vie de l'instance principale (pas de `game.running`, pas d'events
/// `game:started/exited/crashed`, pas de Rich Presence, pas de bouton stop, pas
/// de watcher). Le staff ferme la fenêtre à la main. Ça garantit ZÉRO impact
/// sur le launch normal des joueurs. Seule une tâche de fond lui reste
/// attachée : le rafraîchissement de ses jetons de jeu, jusqu'à sa fermeture.
///
/// `alt_uuid` = UUID Minecraft du compte alternatif (déjà sauvegardé dans le
/// carousel : il faut ses refresh tokens MS + Reborn per-uuid). Le compte doit
/// être whitelisté (role != PLAYER) pour obtenir un play-token et passer
/// Guardian. L'appelant (compte courant) doit être staff.
#[tauri::command]
pub async fn launcher_launch_second_instance<R: Runtime>(
    app: AppHandle<R>,
    auth: State<'_, AuthState>,
    alt_uuid: String,
) -> Result<LaunchResult, GameError> {
    // Gate staff : la fonctionnalité n'est ouverte qu'au staff (compte courant).
    let current = auth
        .current_user()
        .await
        .ok_or(GameError::NotAuthenticated)?;
    if !is_staff_role(&current.role) {
        return Err(GameError::NotStaff);
    }

    // Prépare le compte alt SANS toucher la session courante.
    let alt = auth
        .prepare_alt_account(&alt_uuid)
        .await
        .map_err(|e| GameError::AltAccount { message: cause(e) })?;
    if alt.user.role == "PLAYER" {
        return Err(GameError::NotWhitelisted);
    }
    // Garde-fou anti-collision de session : si le compte « alt » se résout au
    // MÊME compte Minecraft que la session principale (souvent une entrée
    // dupliquée du carousel, ou un UUID avec/sans tirets qui trompe le filtre),
    // lancer deux fois le même compte fait invalider la session par Mojang →
    // « Invalid session » en jeu. On refuse tôt avec un message clair.
    if uuid_eq(&alt.user.minecraft_uuid, &current.minecraft_uuid) {
        return Err(GameError::SameAccount {
            message: format!(
                "« {} » est le MÊME compte Minecraft que ta session principale — \
                 Minecraft renverrait « Invalid session ». Choisis un AUTRE compte \
                 dans le carousel de connexion.",
                alt.user.minecraft_username
            ),
        });
    }
    tracing::info!(
        "2e instance : compte alt {} (rôle {})",
        alt.user.minecraft_username,
        alt.user.role
    );

    let dir = data_dir()?;
    let user_prefs = prefs::load().await.unwrap_or_default();
    let mc_version = minecraft_version();

    // Setup partagé (idempotent, déjà en cache : l'instance principale a tout
    // téléchargé) → quasi instantané. Aucune progression émise.
    let setup = prepare_install(&auth, &dir, &mc_version, |_, _, _, _| {}).await?;

    // Play-tokens du compte alt, dans des fichiers propres à ce compte : ni
    // ceux de l'instance principale, ni ceux d'une autre 2e instance lancée
    // avec un autre compte ne sont écrasés.
    let alt_is_staff = is_staff_role(&alt.user.role);
    let stem = alt_token_stem(&alt.user.minecraft_uuid);
    let mut tokens = write_play_tokens(&auth, &alt.reborn_jwt, &dir, &stem, alt_is_staff).await?;

    let identity = Identity {
        username: &alt.user.minecraft_username,
        uuid: &alt.user.minecraft_uuid,
        is_staff: alt_is_staff,
    };
    let argv = build_argv(
        &setup,
        &mc_version,
        &dir,
        &user_prefs,
        &identity,
        alt.mc_access_token.clone(),
        &tokens,
        &auth.api.base_url,
    );

    // stdout/stderr → un log dédié (pas d'analyzer ni de pipeline de crash).
    let logs_dir = dir.join("logs");
    let _ = tokio::fs::create_dir_all(&logs_dir).await;
    let alt_log = logs_dir.join("alt-instance.log");
    let log_out = std::fs::File::create(&alt_log).map_err(|e| GameError::Io {
        message: format!(
            "Création du journal {} impossible : {e}.",
            alt_log.display()
        ),
    })?;
    let log_err = log_out.try_clone().map_err(|e| GameError::Io {
        message: format!(
            "Ouverture du journal {} impossible : {e}.",
            alt_log.display()
        ),
    })?;

    // Détaché : le Child n'est gardé que par la tâche qui rafraîchit les
    // jetons de l'instance, le temps qu'elle tourne (tokio n'a pas
    // kill_on_drop par défaut → le process survit au drop, sortie du
    // launcher comprise). Le staff ferme la fenêtre à la main.
    let mut child = Command::new(&setup.java_path)
        .args(&argv)
        .stdout(Stdio::from(log_out))
        .stderr(Stdio::from(log_err))
        .stdin(Stdio::null())
        .current_dir(&dir)
        .spawn()
        .map_err(|e| spawn_error(&setup.java_path, &e))?;
    let pid = child.id().unwrap_or(0);
    let refresher = play_tokens::spawn_refresher(
        app,
        JwtSource::Alt {
            mc_uuid: alt.user.minecraft_uuid.clone(),
            jwt: alt.reborn_jwt.clone(),
        },
        std::mem::take(&mut tokens.targets),
    );
    tokio::spawn(async move {
        // Rafraîchissement arrêté (drop) à la fermeture de la 2e instance.
        let _refresher = refresher;
        match child.wait().await {
            Ok(status) => tracing::info!("2e instance fermée (pid {pid}, {status})"),
            Err(e) => tracing::warn!("attente de la fin de la 2e instance impossible : {e}"),
        }
    });

    tracing::info!(
        "2e instance (dev) lancée : {} (pid {}), logs → {}",
        alt.user.minecraft_username,
        pid,
        alt_log.display()
    );

    Ok(LaunchResult {
        pid,
        java_path: setup.java_path.display().to_string(),
        minecraft_version: mc_version,
        fabric_version: setup.fabric.loader_version,
        scope: tokens.scope,
        server_id: tokens.server_id,
    })
}

/// Arrête la partie en cours : demande le kill à la tâche qui possède le
/// processus, puis attend (au plus [`STOP_TIMEOUT`]) qu'il soit réellement
/// terminé. Ne bloque jamais au-delà.
#[tauri::command]
pub async fn launcher_stop_game(game: State<'_, GameState>) -> Result<(), GameError> {
    let (kill_tx, pid) = {
        let lock = game.running_lock();
        let Some(running) = lock.as_ref() else {
            return Err(GameError::NotRunning);
        };
        (running.kill_tx.clone(), running.pid)
    };
    // Marque l'arrêt comme volontaire AVANT le kill : `await_game_end` lira
    // ce flag pour ne pas remonter un faux `game:crashed` (un kill produit un
    // exit-code non-zéro qui ressemble à un crash).
    game.stop_requested.store(true, Ordering::SeqCst);

    let (ack_tx, ack_rx) = oneshot::channel();
    if kill_tx.send(ack_tx).await.is_err() {
        // La tâche de fin vient de se terminer : le jeu est déjà fermé.
        return Ok(());
    }
    match tokio::time::timeout(STOP_TIMEOUT, ack_rx).await {
        // Accusé « terminé », ou tâche finie entre-temps : le jeu est fermé.
        Ok(Ok(Ok(()))) | Ok(Err(_)) => {
            tracing::info!("jeu arrêté à la demande du joueur (pid {pid})");
            Ok(())
        }
        Ok(Ok(Err(e))) => {
            game.stop_requested.store(false, Ordering::SeqCst);
            Err(GameError::Kill {
                message: format!(
                    "le processus Java (pid {pid}) refuse de se fermer ({e}). Ferme la fenêtre du jeu, ou termine « javaw.exe » dans le Gestionnaire des tâches."
                ),
            })
        }
        Err(_) => Err(GameError::Kill {
            message: format!(
                "le processus Java (pid {pid}) ne s'est pas fermé après {} s. Termine « javaw.exe » dans le Gestionnaire des tâches.",
                STOP_TIMEOUT.as_secs()
            ),
        }),
    }
}

/// État de la partie, pour resynchroniser le front si la WebView est
/// rechargée en pleine partie (les events `game:*` déjà émis sont perdus).
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GameStatus {
    pub running: bool,
    pub pid: Option<u32>,
}

#[tauri::command]
pub fn launcher_game_status(game: State<'_, GameState>) -> GameStatus {
    let lock = game.running_lock();
    GameStatus {
        running: lock.is_some(),
        pid: lock.as_ref().map(|g| g.pid),
    }
}

/// Lit les `max_lines` dernières lignes d'un fichier, best-effort (chaîne
/// vide si le fichier n'existe pas / illisible).
async fn read_stderr_tail(path: &Path, max_lines: usize) -> String {
    match tokio::fs::read(path).await {
        Ok(bytes) => {
            let content = String::from_utf8_lossy(&bytes);
            let lines: Vec<&str> = content.lines().collect();
            let start = lines.len().saturating_sub(max_lines);
            lines[start..].join("\n")
        }
        Err(_) => String::new(),
    }
}

// ──────────────────────────────────────────────────────
//  Préparation commune (instance principale + 2e instance)
// ──────────────────────────────────────────────────────

/// Résultat des étapes 1..5 : tout ce qu'il faut pour assembler l'argv.
struct InstallSetup {
    java_path: PathBuf,
    libraries: libraries::LibrarySetup,
    assets: assets::AssetSetup,
    fabric: fabric::FabricSetup,
}

/// Étapes 1..5 (idempotentes, en cache après le premier lancement).
/// `progress(step, label, current, total)` alimente `launch:progress`.
async fn prepare_install<F>(
    auth: &AuthState,
    dir: &Path,
    mc_version: &str,
    progress: F,
) -> Result<InstallSetup, GameError>
where
    F: Fn(u8, &str, Option<usize>, Option<usize>) + Send + Sync,
{
    let http = &auth.download_http;

    tracing::info!("launch [1/6] : runtime Java");
    progress(1, "Runtime Java", None, None);
    let java_path = runtime::ensure_runtime(http, dir)
        .await
        .map_err(|e| GameError::Runtime {
            message: install_cause(&e, e.is_network()),
        })?;

    tracing::info!("launch [2/6] : metadata Minecraft {mc_version}");
    progress(2, "Métadonnées Minecraft", None, None);
    let version = mojang::fetch_version_json(http, dir, mc_version)
        .await
        .map_err(|e| GameError::Mojang {
            message: install_cause(&e, e.is_network()),
        })?;

    tracing::info!("launch [3/6] : libraries vanilla + natives");
    progress(3, "Bibliothèques & natives", None, None);
    let libraries = libraries::ensure_libraries(http, dir, &version)
        .await
        .map_err(|e| GameError::Libraries {
            message: install_cause(&e, e.is_network()),
        })?;

    // Long la première fois → sous-progression émise.
    tracing::info!("launch [4/6] : assets");
    progress(4, "Téléchargement des ressources", Some(0), None);
    let assets = assets::ensure_assets(http, dir, &version.asset_index, |done, total| {
        progress(4, "Téléchargement des ressources", Some(done), Some(total))
    })
    .await
    .map_err(|e| GameError::Assets {
        message: install_cause(&e, e.is_network()),
    })?;

    tracing::info!("launch [5/6] : Fabric Loader");
    progress(5, "Fabric Loader", None, None);
    let fabric = fabric::ensure_fabric(http, dir, mc_version)
        .await
        .map_err(|e| GameError::Fabric {
            message: install_cause(&e, e.is_network()),
        })?;

    Ok(InstallSetup {
        java_path,
        libraries,
        assets,
        fabric,
    })
}

/// Compte Minecraft qui joue (principal ou alt).
struct Identity<'a> {
    username: &'a str,
    uuid: &'a str,
    is_staff: bool,
}

/// Assemble l'argv JVM complet (main class Fabric comprise).
#[allow(clippy::too_many_arguments)]
fn build_argv(
    setup: &InstallSetup,
    mc_version: &str,
    dir: &Path,
    user_prefs: &Preferences,
    identity: &Identity<'_>,
    mc_access_token: String,
    tokens: &PlayTokens,
    api_url: &str,
) -> Vec<String> {
    // Classpath = libs vanilla + libs Fabric, dédupliqué sur group:artifact.
    // Fabric apporte parfois une version plus récente d'une lib transitive
    // (exemple : ASM 9.9 vs ASM 9.6 vanilla) ; Fabric Loader plante avec
    // "duplicate classes found on classpath" si on garde les deux. On
    // garde donc la *dernière* version vue (Fabric, ajoutée après vanilla).
    let mut jars = setup.libraries.library_jars.clone();
    jars.extend(setup.fabric.library_jars.iter().cloned());
    let all_libs = dedupe_classpath(jars);

    let cfg = jvm::LaunchConfig {
        minecraft_version: mc_version.to_string(),
        launcher_version: env!("CARGO_PKG_VERSION").to_string(),
        minecraft_username: identity.username.to_string(),
        minecraft_uuid: identity.uuid.to_string(),
        mc_access_token,
        natives_dir: setup.libraries.natives_dir.display().to_string(),
        library_jars: all_libs.iter().map(|p| p.display().to_string()).collect(),
        client_jar: setup.libraries.client_jar.display().to_string(),
        game_dir: dir.display().to_string(),
        assets_dir: setup.assets.assets_dir.display().to_string(),
        asset_index: setup.assets.asset_index.clone(),
        ram_mb: user_prefs.ram_mb,
        width: user_prefs.width,
        height: user_prefs.height,
        auto_connect: choisir_auto_connect(
            runtime_server_override(),
            tokens.adresse.clone(),
            resolve_auto_connect(),
        ),
        dev_server: resolve_dev_server(),
        is_staff: identity.is_staff,
        play_token_path: Some(tokens.main.display().to_string()),
        play_token_dev_path: tokens.dev.as_ref().map(|p| p.display().to_string()),
        api_url: Some(api_url.to_string()),
    };

    let mut argv = jvm::build_command(&cfg);
    // Override de la mainClass vanilla par celle de Fabric.
    if let Some(slot) = argv.iter_mut().find(|a| *a == jvm::VANILLA_MAIN_CLASS) {
        *slot = setup.fabric.main_class.clone();
    }
    argv
}

/// Jeton Minecraft (Mojang sessionserver) pour `--accessToken`. Sans lui le
/// serveur rejette avec "Invalid session".
///
/// - Release : pas de jeton = erreur claire (avant, on lançait avec le
///   placeholder « 0 » et le joueur découvrait « Invalid session » en jeu).
/// - Debug : le dev-login n'a pas de chaîne Microsoft → placeholder,
///   accepté seulement par un serveur en offline-mode.
async fn resolve_mc_token(auth: &AuthState) -> Result<String, GameError> {
    let outcome = auth.ensure_mc_token().await;
    if cfg!(debug_assertions) {
        return Ok(match outcome {
            Ok(Some(token)) => token,
            Ok(None) => {
                tracing::warn!(
                    "aucun token MC disponible (dev-login ?), utilise le placeholder — la connexion serveur online-mode échouera"
                );
                DEV_PLACEHOLDER_TOKEN.to_string()
            }
            Err(e) => {
                tracing::warn!("rafraîchissement MS->MC échoué : {e} — utilise le placeholder");
                DEV_PLACEHOLDER_TOKEN.to_string()
            }
        });
    }
    match outcome {
        Ok(Some(token)) => Ok(token),
        Ok(None) => Err(GameError::McSessionExpired {
            message: "Ta session Minecraft a expiré : reconnecte-toi avec Microsoft.".into(),
        }),
        Err(e @ (AuthError::Http(_) | AuthError::Timeout(_) | AuthError::Io(_))) => {
            Err(GameError::McSessionUnreachable {
                message: format!(
                    "Impossible de rafraîchir ta session Minecraft auprès de Microsoft ({}) : vérifie ta connexion puis relance.",
                    cause(e)
                ),
            })
        }
        Err(e) => Err(GameError::McSessionExpired {
            message: format!(
                "Ta session Minecraft a expiré ({}) : reconnecte-toi avec Microsoft.",
                cause(e)
            ),
        }),
    }
}

/// JWT Reborn lu dans le coffre de Windows.
fn reborn_jwt(auth: &AuthState) -> Result<String, GameError> {
    auth.store
        .get(SecretKey::RebornAccessToken)
        .map_err(|e| GameError::Storage { message: cause(e) })?
        .ok_or(GameError::NotAuthenticated)
}

/// Identifiant du serveur de dev (sélecteur Build/Dev du mod, staff), envoyé
/// à `POST /play/session` (claim `aud`, comparée à la clé `server-id` de
/// Guardian).
///
/// Le serveur PRINCIPAL, lui, n'est pas nommé : le jeton principal part sans
/// `serveur`, et l'API applique `REBORN_SERVER_ID`, le serveur des launchers
/// qui ne précisent rien. « Build » n'est que le libellé du sélecteur du mod,
/// pas l'identifiant d'un serveur (il en existe un vrai, celui des builders).
const SERVEUR_DEV: &str = "dev";

/// Identifiant de serveur : minuscules, chiffres et tirets, 32 caractères au
/// plus (même règle que l'API et Guardian).
fn is_server_id(id: &str) -> bool {
    let mut chars = id.chars();
    id.len() <= 32
        && chars
            .next()
            .is_some_and(|c| c.is_ascii_lowercase() || c.is_ascii_digit())
        && chars.all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-')
}

/// Identifiant de serveur lu dans une variable d'environnement (tests : un
/// launcher de dev dont `REBORN_SERVER_HOST` pointe sur le serveur de dev
/// pose `REBORN_PLAY_SERVER_ID=dev`). Absente ou vide : `None`. Invalide :
/// ignorée, avec un avertissement.
fn server_id_override(variable: &str) -> Option<String> {
    let brut = std::env::var(variable).ok()?;
    let v = brut.trim();
    if v.is_empty() {
        None
    } else if is_server_id(v) {
        Some(v.to_string())
    } else {
        tracing::warn!("{variable} « {v} » invalide : ignorée");
        None
    }
}

/// Identifiant de serveur, avec un remplacement possible par variable
/// d'environnement (cf [`server_id_override`]).
fn server_id_from_env(variable: &str, defaut: &'static str) -> String {
    server_id_override(variable).unwrap_or_else(|| defaut.to_string())
}

/// Jetons de jeu écrits pour une partie (fichiers lus par Reborn Integrity).
struct PlayTokens {
    /// Serveur principal (`-Dreborn.playTokenPath`).
    main: PathBuf,
    /// Serveur de dev, pour un staff avec un serveur dev configuré
    /// (`-Dreborn.playTokenPath.dev`). Le fichier peut manquer (jeton refusé
    /// au lancement) : le rafraîchissement le redemande pendant la partie.
    dev: Option<PathBuf>,
    /// Portée et serveur du jeton principal, tels que l'API les a émis.
    scope: Option<String>,
    server_id: Option<String>,
    /// Adresse du serveur visé donnée par l'API avec le jeton principal.
    adresse: Option<jvm::ServerAddress>,
    /// Fichiers à tenir à jour pendant la partie (cf `play_tokens`), avec le
    /// serveur demandé pour chacun.
    targets: Vec<TokenTarget>,
}

/// Écrit les jetons de jeu d'une partie dans `<dir>/<stem>` (serveur
/// principal) et, pour un staff avec un serveur de dev configuré,
/// `<dir>/<stem>-dev` : le sélecteur Build/Dev du mod se choisit après le
/// lancement, chaque cible doit donc avoir son jeton (claim `aud`).
///
/// Jeton principal : obligatoire, demandé SANS serveur (l'API applique
/// `REBORN_SERVER_ID`). Seul `REBORN_PLAY_SERVER_ID` (lancement de dev) le
/// nomme ; si l'API ne connaît pas cet identifiant (400), il est redemandé
/// sans serveur plutôt que de bloquer le lancement, avec un avertissement au
/// journal. Jeton dev : facultatif, un échec n'empêche pas de jouer sur le
/// serveur principal (le mod y envoie alors le jeton principal) ; son chemin
/// est fourni quand même, le rafraîchissement le réessaie.
async fn write_play_tokens(
    auth: &AuthState,
    reborn_jwt: &str,
    dir: &Path,
    stem: &str,
    is_staff: bool,
) -> Result<PlayTokens, GameError> {
    let main_path = dir.join(stem);
    // Serveur réellement demandé : le rafraîchissement redemande le même.
    let (session, main_serveur) = match server_id_override("REBORN_PLAY_SERVER_ID") {
        None => (auth.api.fetch_play_token(reborn_jwt, None).await, None),
        Some(main_id) => match auth.api.fetch_play_token(reborn_jwt, Some(&main_id)).await {
            Err(e) if e.status() == Some(400) => {
                tracing::warn!(
                    "serveur « {main_id} » refusé par l'API ({e}) : jeton demandé pour le serveur par défaut"
                );
                (auth.api.fetch_play_token(reborn_jwt, None).await, None)
            }
            other => (other, Some(main_id)),
        },
    };
    let session = session.map_err(|e| GameError::PlayToken { message: cause(e) })?;
    write_token_file(&main_path, &session).await?;
    let mut targets = vec![TokenTarget::new(main_path.clone(), main_serveur, true)];

    let dev_path = dir.join(format!("{stem}-dev"));
    let mut dev = None;
    if is_staff && resolve_dev_server().is_some() {
        let dev_id = server_id_from_env("REBORN_PLAY_SERVER_DEV_ID", SERVEUR_DEV);
        let written = match auth.api.fetch_play_token(reborn_jwt, Some(&dev_id)).await {
            Ok(dev_session) => match write_token_file(&dev_path, &dev_session).await {
                Ok(()) => true,
                Err(e) => {
                    tracing::warn!("jeton du serveur de dev non écrit : {e}");
                    false
                }
            },
            Err(e) => {
                tracing::warn!("jeton du serveur « {dev_id} » refusé par l'API : {e}");
                false
            }
        };
        if !written {
            // Jeton dev d'une partie précédente : expiré ou d'un autre compte.
            let _ = tokio::fs::remove_file(&dev_path).await;
        }
        targets.push(TokenTarget::new(dev_path.clone(), Some(dev_id), written));
        dev = Some(dev_path);
    } else {
        // Plus de serveur dev pour ce compte : ancien jeton retiré.
        let _ = tokio::fs::remove_file(&dev_path).await;
    }

    Ok(PlayTokens {
        main: main_path,
        dev,
        scope: session.scope,
        server_id: session.aud,
        adresse: session.adresse.map(|a| jvm::ServerAddress {
            host: a.host,
            port: a.port,
        }),
        targets,
    })
}

/// Écrit un jeton de jeu dans `path` (atomique, cf `play_tokens`).
async fn write_token_file(path: &Path, session: &PlaySession) -> Result<(), GameError> {
    play_tokens::write_token_atomic(path, &session.play_token)
        .await
        .map_err(|e| GameError::Io {
            message: format!(
                "Écriture du jeton de jeu dans {} impossible ({e}) : vérifie que ton antivirus ne bloque pas le dossier du launcher.",
                path.display()
            ),
        })?;
    tracing::info!(
        "play-token écrit ({} octets, portée {}, serveur {}), expire à {}",
        session.play_token.len(),
        session.scope.as_deref().unwrap_or("non précisée"),
        session.aud.as_deref().unwrap_or("non précisé"),
        session.expires_at
    );
    Ok(())
}

/// Erreur de spawn de la JVM, avec la cause probable selon le code OS.
fn spawn_error(java: &Path, e: &std::io::Error) -> GameError {
    let hint = match e.kind() {
        std::io::ErrorKind::NotFound => {
            "Java est introuvable : lance « Réparer les fichiers du jeu » dans Paramètres › Jeu, puis relance."
        }
        std::io::ErrorKind::PermissionDenied => {
            "accès refusé : ton antivirus bloque probablement Java, ajoute le dossier du launcher à ses exceptions."
        }
        _ => "relance le launcher ; si l'erreur persiste, lance « Réparer les fichiers du jeu » dans Paramètres › Jeu.",
    };
    GameError::Spawn {
        message: format!("{} n'a pas pu démarrer ({e}) — {hint}", java.display()),
    }
}

/// Copie de l'argv sans secrets, pour `last-argv.txt` : la valeur qui suit
/// `--accessToken` (jeton Mojang, valable ~24 h) devient « *** ». Le
/// play-token, lui, n'est jamais dans l'argv (on passe le chemin du fichier).
fn redact_argv(argv: &[String]) -> Vec<String> {
    const SECRET_FLAGS: [&str; 1] = ["--accessToken"];
    let mut out = Vec::with_capacity(argv.len());
    let mut hide_next = false;
    for arg in argv {
        if hide_next {
            out.push("***".to_string());
            hide_next = false;
            continue;
        }
        if let Some((flag, _)) = arg.split_once('=') {
            if SECRET_FLAGS.contains(&flag) {
                out.push(format!("{flag}=***"));
                continue;
            }
        }
        hide_next = SECRET_FLAGS.contains(&arg.as_str());
        out.push(arg.clone());
    }
    out
}

/// Déduplique le classpath sur (group:artifact). Quand un même artifact
/// est présent plusieurs fois (typiquement vanilla + Fabric), on garde la
/// *dernière* occurrence — c'est généralement la version la plus récente
/// puisque les libs Fabric sont concaténées après celles de vanilla.
///
/// Conserve l'ordre relatif entre artifacts distincts pour la
/// reproductibilité des launches.
fn dedupe_classpath(jars: Vec<PathBuf>) -> Vec<PathBuf> {
    use std::collections::HashMap;
    let keys: Vec<Option<String>> = jars.iter().map(|p| maven_dedupe_key(p)).collect();
    let mut last_index: HashMap<&str, usize> = HashMap::new();
    for (i, key) in keys.iter().enumerate() {
        if let Some(key) = key {
            last_index.insert(key.as_str(), i);
        }
    }
    jars.into_iter()
        .enumerate()
        .filter(|(i, _)| match &keys[*i] {
            Some(key) => last_index.get(key.as_str()) == Some(i),
            None => true, // pas un layout maven → on garde
        })
        .map(|(_, path)| path)
        .collect()
}

/// Pour un chemin de la forme `<...>/libraries/<group>/<artifact>/<version>/<filename>`,
/// retourne `<group dot-separated>:<artifact>:<classifier>` (classifier vide pour
/// le jar principal). Les variantes `natives-*` du même artifact partagent le
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

/// Liste les mods présents dans le game directory avec leur statut
/// (compatible / incompatible avec la version MC active).
#[tauri::command]
pub async fn launcher_mods_list() -> Result<Vec<mods_inspect::ModEntry>, GameError> {
    let mods_dir = data_dir()?.join("mods");
    // Cause seule (elle nomme déjà le dossier) : l'onglet Mods ajoute le
    // contexte « Impossible de lire le dossier des mods ».
    mods_inspect::inspect_mods_folder(&mods_dir, &minecraft_version()).map_err(|e| GameError::Io {
        message: e.to_string(),
    })
}

/// Supprime tous les mods marqués incompatibles avec la version MC
/// active. Retourne la liste des fichiers supprimés.
#[tauri::command]
pub async fn launcher_mods_purge(game: State<'_, GameState>) -> Result<Vec<String>, GameError> {
    if game.is_busy() {
        return Err(GameError::GameRunning {
            action: "nettoyer le dossier mods",
        });
    }
    let mods_dir = data_dir()?.join("mods");
    // Seule la lecture du dossier peut échouer (un jar verrouillé est
    // seulement journalisé) : cause seule, l'onglet Mods ajoute le contexte.
    mods_inspect::purge_incompatible_mods(&mods_dir, &minecraft_version()).map_err(|e| {
        GameError::Io {
            message: e.to_string(),
        }
    })
}

/// Résout l'adresse du serveur Reborn auquel auto-connect le client.
/// Priorité : env runtime `REBORN_SERVER_HOST` (dev/override) > compile-time
/// `REBORN_SERVER_HOST_BUILD` (baker au build release). Idem pour port.
/// Quand le manifest signé portera l'adresse du serveur, on la prendra de
/// là et on virera ces fallbacks.
/// Adresse forcée sur ce PC (`REBORN_SERVER_HOST` à l'exécution), sans repli.
fn runtime_server_override() -> Option<jvm::ServerAddress> {
    let host = std::env::var("REBORN_SERVER_HOST").ok()?;
    let host = host.trim();
    if host.is_empty() {
        return None;
    }
    let port = std::env::var("REBORN_SERVER_PORT")
        .ok()
        .and_then(|s| s.trim().parse::<u16>().ok())
        .unwrap_or(25565);
    Some(jvm::ServerAddress {
        host: host.to_string(),
        port,
    })
}

/// Serveur de connexion automatique : l'adresse forcée sur ce PC, puis celle
/// que l'API joint au jeton (aiguillage depuis le VPS : dev pré-prod, bascule
/// en prod), puis celle inscrite à la publication du launcher.
fn choisir_auto_connect(
    forcee: Option<jvm::ServerAddress>,
    api: Option<jvm::ServerAddress>,
    build: Option<jvm::ServerAddress>,
) -> Option<jvm::ServerAddress> {
    let choix = forcee.or(api).or(build);
    if let Some(a) = &choix {
        tracing::info!("serveur de connexion : port {}", a.port);
    }
    choix
}

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

/// Résout l'adresse du serveur de DEV (build/dev pendant le développement).
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
        "HELPER"
            | "MODELISATEUR"
            | "DEVELOPPEUR"
            | "MODERATOR"
            | "WHITELIST_REVIEWER"
            | "ADMIN"
            | "OWNER"
    )
}

/// Compare deux UUID Minecraft en ignorant les tirets et la casse (les entrées
/// du carousel peuvent stocker l'un avec tirets, l'autre sans → sinon un même
/// compte passe pour un compte « alt » distinct).
pub(super) fn uuid_eq(a: &str, b: &str) -> bool {
    let norm = |s: &str| {
        s.chars()
            .filter(|c| *c != '-')
            .flat_map(char::to_lowercase)
            .collect::<String>()
    };
    norm(a) == norm(b)
}

/// Nom des fichiers de jeton d'une 2e instance (`<nom>`, `<nom>-dev` et
/// leurs `.tmp`), propre à son compte. Deux 2es instances lancées avec deux
/// comptes différents ont chacune leur tâche de rafraîchissement : avec un
/// nom fixe, elles réécrivaient tour à tour les mêmes fichiers, et comme le
/// mod relit le fichier à chaque connexion, l'une envoyait le jeton de
/// l'autre (refusé par Guardian). L'UUID vient de l'API : seuls ses
/// caractères alphanumériques entrent dans le nom de fichier.
fn alt_token_stem(mc_uuid: &str) -> String {
    let id: String = mc_uuid
        .chars()
        .filter(char::is_ascii_alphanumeric)
        .map(|c| c.to_ascii_lowercase())
        .collect();
    format!(".reborn-play-token-alt-{id}")
}

/// Tâche propriétaire du processus : attend sa fin (ou une demande d'arrêt),
/// vide le journal, libère l'état, émet `game:exited` (+ `game:crashed` si
/// la sortie n'était ni volontaire ni propre) et ramène le launcher.
async fn await_game_end<R: Runtime>(
    app: AppHandle<R>,
    mut child: Child,
    mut kill_rx: mpsc::Receiver<KillAck>,
    pipeline: JoinHandle<()>,
    stderr_path: PathBuf,
) {
    // Accusés d'arrêt en attente : envoyés quand le processus est vraiment sorti.
    let mut acks: Vec<KillAck> = Vec::new();
    let status = loop {
        tokio::select! {
            status = child.wait() => break status,
            Some(ack) = kill_rx.recv() => match child.start_kill() {
                Ok(()) => acks.push(ack),
                Err(e) => {
                    let _ = ack.send(Err(e));
                }
            },
        }
    };
    for ack in acks {
        let _ = ack.send(Ok(()));
    }
    if let Err(e) = &status {
        tracing::warn!("attente de la fin du jeu impossible : {e}");
    }
    // `code()` est None quand le process a été tué par un signal (unix) ; on
    // traite ce cas comme un échec sauf si l'arrêt était volontaire.
    let code = status.ok().and_then(|s| s.code());

    // Laisse les pumps vider stdout/stderr (last-stderr.txt compris) et le
    // batcher émettre le dernier lot avant de conclure.
    if tokio::time::timeout(PIPELINE_DRAIN_TIMEOUT, pipeline)
        .await
        .is_err()
    {
        tracing::warn!(
            "journal du jeu : flux encore ouverts {} s après la sortie de la JVM",
            PIPELINE_DRAIN_TIMEOUT.as_secs()
        );
    }

    let Some(state) = app.try_state::<GameState>() else {
        return;
    };
    // Libère la partie (et le watcher) AVANT d'annoncer la fin : un
    // relancement immédiat ne doit pas être refusé.
    state.running_lock().take();
    let stop_requested = state.stop_requested.swap(false, Ordering::SeqCst);

    // On continue d'émettre `game:exited` pour le chemin normal (PlayButton
    // remet la phase à "ready" dessus, crash ou pas).
    let _ = app.emit("game:exited", code.unwrap_or(-1));
    // Ramène le launcher au premier plan (fin de partie, crash), qu'il soit
    // réduit (onGameStart = minimize) ou caché dans la zone de notification.
    crate::tray::set_in_game(&app, false);
    crate::tray::show_main_window(&app);

    if !stop_requested && code != Some(0) {
        let payload = crash_payload(&state, code, &stderr_path).await;
        tracing::warn!("crash du jeu détecté (exit={code:?}) — emit game:crashed");
        let _ = app.emit("game:crashed", payload);
    } else {
        tracing::info!("le jeu s'est fermé (code {code:?})");
    }

    // Clear Discord Rich Presence — le user n'est plus en jeu.
    if let Some(rpc) = app.try_state::<DiscordRpcState>() {
        rpc.stop().await;
    }
}

/// Construit le payload de crash à partir du journal de la partie (écrit
/// dans `logs/last-crash.log`), ou de last-stderr.txt si le journal est vide.
async fn crash_payload(
    state: &GameState,
    code: Option<i32>,
    stderr_path: &Path,
) -> GameCrashPayload {
    let lines = state.logs.snapshot();
    if lines.is_empty() {
        return GameCrashPayload {
            exit_code: code,
            stderr_path: stderr_path.display().to_string(),
            stderr_tail: read_stderr_tail(stderr_path, CRASH_TAIL_LINES).await,
        };
    }
    let path =
        match logs::write_log_file(&lines, "last-crash.log", "journal du dernier crash").await {
            Ok(path) => path,
            Err(e) => {
                tracing::warn!("{e}");
                stderr_path.to_path_buf()
            }
        };
    GameCrashPayload {
        exit_code: code,
        stderr_path: path.display().to_string(),
        stderr_tail: state.logs.tail_text(CRASH_TAIL_LINES),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn s(v: &[&str]) -> Vec<String> {
        v.iter().map(|x| x.to_string()).collect()
    }

    #[test]
    fn access_token_is_masked_in_argv_dump() {
        let argv = s(&[
            "-Xmx4096M",
            "--username",
            "Steve",
            "--uuid",
            "0123456789abcdef0123456789abcdef",
            "--accessToken",
            "eyJhbGciOiJIUzI1NiJ9.secret.signature",
            "--userType",
            "msa",
            "--accessToken=autre-secret",
        ]);
        let out = redact_argv(&argv);
        assert_eq!(out.len(), argv.len());
        assert_eq!(out[5], "--accessToken");
        assert_eq!(out[6], "***");
        assert_eq!(out[9], "--accessToken=***");
        // Le reste est intact (l'UUID n'est pas un secret).
        assert_eq!(out[4], argv[4]);
        assert_eq!(out[7], "--userType");
        assert!(!out.join(" ").contains("secret"));
    }

    #[test]
    fn redaction_handles_trailing_flag() {
        let out = redact_argv(&s(&["--accessToken"]));
        assert_eq!(out, s(&["--accessToken"]));
    }

    #[test]
    fn uuid_comparison_ignores_dashes_and_case() {
        assert!(uuid_eq(
            "0123ABCD-0000-0000-0000-000000000000",
            "0123abcd000000000000000000000000"
        ));
        assert!(!uuid_eq(
            "0123abcd000000000000000000000000",
            "0123abcd000000000000000000000001"
        ));
    }

    #[test]
    fn alt_token_files_are_named_after_the_account() {
        // Même compte, quelle que soit la forme de l'UUID : mêmes fichiers.
        assert_eq!(
            alt_token_stem("0123ABCD-0000-0000-0000-00000000000F"),
            ".reborn-play-token-alt-0123abcd00000000000000000000000f"
        );
        assert_eq!(
            alt_token_stem("0123abcd00000000000000000000000f"),
            alt_token_stem("0123ABCD-0000-0000-0000-00000000000F")
        );
        // Deux comptes : deux noms.
        assert_ne!(
            alt_token_stem("0123abcd000000000000000000000000"),
            alt_token_stem("0123abcd000000000000000000000001")
        );
        // Jamais de séparateur de chemin dans le nom.
        let nom = alt_token_stem("../..\\x");
        assert!(!nom.contains('/') && !nom.contains('\\') && !nom.contains(".."));
    }

    #[test]
    fn classpath_keeps_last_version_and_natives() {
        let jars = vec![
            PathBuf::from("/g/libraries/org/ow2/asm/asm/9.6/asm-9.6.jar"),
            PathBuf::from("/g/libraries/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"),
            PathBuf::from("/g/libraries/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar"),
            PathBuf::from("/g/libraries/org/ow2/asm/asm/9.9/asm-9.9.jar"),
            PathBuf::from("/ailleurs/custom.jar"),
        ];
        let out = dedupe_classpath(jars);
        assert_eq!(out.len(), 4);
        assert!(out.iter().any(|p| p.ends_with("asm-9.9.jar")));
        assert!(!out.iter().any(|p| p.ends_with("asm-9.6.jar")));
        assert!(out
            .iter()
            .any(|p| p.ends_with("lwjgl-3.3.3-natives-windows.jar")));
        assert!(out.iter().any(|p| p.ends_with("custom.jar")));
    }

    #[test]
    fn errors_serialize_as_kind_and_message() {
        let v = serde_json::to_value(GameError::AlreadyRunning).unwrap();
        assert_eq!(v["kind"], "already_running");
        assert!(v["message"].as_str().unwrap().contains("déjà lancé"));
        let v = serde_json::to_value(GameError::Runtime {
            message: install_cause("Erreur réseau avec piston-meta.mojang.com.", true),
        })
        .unwrap();
        assert_eq!(v["kind"], "runtime");
        assert_eq!(
            v["message"].as_str().unwrap(),
            "Préparation de Java impossible : erreur réseau avec piston-meta.mojang.com. \
             Vérifie ta connexion puis relance."
        );
        assert_eq!(v.as_object().unwrap().len(), 2);
    }

    #[test]
    fn access_refusals_keep_their_kind_and_message() {
        let v = serde_json::to_value(GameError::Acces {
            kind: candidature::REFUS_PAS_OUVERTE,
            message: "L'entrée de ta séance de douane ouvre dans 12 min.".into(),
        })
        .unwrap();
        assert_eq!(v["kind"], "douane_not_open");
        assert_eq!(
            v["message"],
            "L'entrée de ta séance de douane ouvre dans 12 min."
        );
        let v = serde_json::to_value(GameError::AccesInverifiable {
            message: "impossible de joindre le serveur Reborn (délai dépassé)".into(),
        })
        .unwrap();
        assert_eq!(v["kind"], "access_check");
        assert_eq!(
            v["message"],
            "Vérification de ta candidature impossible : impossible de joindre le serveur Reborn (délai dépassé)."
        );
    }

    #[test]
    fn server_ids_follow_guardian_rule() {
        for ok in ["build", "dev", "prod", "build-2", "0dev"] {
            assert!(is_server_id(ok), "{ok}");
        }
        for ko in ["", "Build", "-dev", "dev server", "dév", &"a".repeat(33)] {
            assert!(!is_server_id(ko), "{ko}");
        }
        assert_eq!(SERVEUR_DEV, "dev");
        // Sans variable : aucun remplacement (le jeton principal part sans serveur).
        assert_eq!(
            server_id_override("REBORN_PLAY_SERVER_ID_TEST_ABSENTE"),
            None
        );
    }

    #[test]
    fn auto_connect_forcee_puis_api_puis_build() {
        let a = |h: &str, p: u16| {
            Some(jvm::ServerAddress {
                host: h.into(),
                port: p,
            })
        };
        let c = |x: Option<jvm::ServerAddress>| x.map(|a| (a.host, a.port));
        assert_eq!(
            c(choisir_auto_connect(a("pc", 1), a("api", 2), a("build", 3))),
            Some(("pc".into(), 1))
        );
        assert_eq!(
            c(choisir_auto_connect(None, a("api", 2), a("build", 3))),
            Some(("api".into(), 2))
        );
        assert_eq!(
            c(choisir_auto_connect(None, None, a("build", 3))),
            Some(("build".into(), 3))
        );
        assert_eq!(c(choisir_auto_connect(None, None, None)), None);
    }

    #[test]
    fn install_cause_keeps_inner_advice_without_network_hint() {
        // Cause disque : sa consigne seule, pas de « vérifie ta connexion ».
        let disk = install_cause(
            "Impossible d'accéder à « C:\\jeu\\runtime » : accès refusé — un antivirus bloque l'accès.",
            false,
        );
        assert_eq!(
            disk,
            "impossible d'accéder à « C:\\jeu\\runtime » : accès refusé — un antivirus bloque l'accès"
        );
        // Cause réseau qui porte déjà sa consigne : rien n'est ajouté.
        let net = install_cause(
            "délai dépassé en contactant x — vérifie ta connexion internet",
            true,
        );
        assert!(!net.contains("Vérifie ta connexion puis relance"));
        // Nom propre en tête : la majuscule reste.
        assert!(install_cause("Java est installé mais…", false).starts_with("Java"));
    }
}
