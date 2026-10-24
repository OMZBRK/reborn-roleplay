//! Orchestrateur du flow de mise à jour + lancement Minecraft.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §8.1.
//!
//! Ce module couvre les étapes [1]..[4] (manifeste signé : fetch,
//! vérification Ed25519 + fraîcheur, diff et téléchargement) et l'onglet
//! Paramètres › Jeu. Le lancement de la JVM vit dans `game.rs`, son journal
//! dans `logs.rs`.

pub mod assets;
pub mod clock;
pub mod diagnostics;
pub mod fabric;
pub mod game;
pub mod jvm;
pub mod libraries;
pub mod logs;
pub mod mods;
pub mod mojang;
pub mod paths;
pub mod runtime;

pub use game::GameState;

use serde::Serialize;
use tauri::{AppHandle, Emitter, Runtime, State};

use crate::auth::AuthState;
use crate::manifest::{
    self, compute_plan_export, download_plan, purge_orphan_mods, DownloadError,
    ManifestVerifyError, PlanItem, SignedManifest,
};
use crate::storage::secrets::SecretKey;

#[derive(Debug, Serialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum UpdateStatus {
    /// Le manifest est valide et tous les fichiers locaux matchent.
    UpToDate { version: String },
    /// Une fois le download terminé, le launcher est prêt.
    Updated {
        version: String,
        files_downloaded: usize,
    },
    /// Le launcher courant est trop vieux pour ce manifest.
    LauncherOutdated { current: String, required: String },
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdatePreview {
    pub version: String,
    pub minecraft_version: String,
    pub plan: Vec<PlanItem>,
    pub bytes_total: u64,
    pub min_launcher_version: String,
    pub launcher_version: String,
    pub launcher_outdated: bool,
}

/// Forme IPC commune des erreurs renvoyées au front : `{ kind, message }`
/// (même contrat que `auth/error.rs`). `message` est une phrase complète.
pub(crate) fn serialize_ipc_error<S: serde::Serializer>(
    serializer: S,
    kind: &str,
    message: &str,
) -> Result<S::Ok, S::Error> {
    use serde::ser::SerializeStruct;
    let mut s = serializer.serialize_struct("IpcError", 2)?;
    s.serialize_field("kind", kind)?;
    s.serialize_field("message", message)?;
    s.end()
}

/// Payload de l'event `mods:purged` (même forme pour toutes les purges).
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ModsPurgedPayload {
    pub removed: Vec<String>,
    pub target_mc_version: String,
}

pub(crate) fn emit_mods_purged<R: Runtime>(
    app: &AppHandle<R>,
    removed: Vec<String>,
    target_mc_version: &str,
) {
    let _ = app.emit(
        "mods:purged",
        ModsPurgedPayload {
            removed,
            target_mc_version: target_mc_version.to_string(),
        },
    );
}

#[derive(Debug, thiserror::Error)]
pub enum LauncherError {
    #[error("Tu n'es pas connecté : reconnecte-toi pour vérifier les mises à jour du jeu.")]
    NotAuthenticated,
    #[error("Impossible de récupérer le manifeste des mods — {message}")]
    Manifest { message: String },
    #[error("Manifeste des mods refusé : {message}.")]
    Signature { message: String },
    #[error("{message}")]
    ManifestExpired { message: String },
    #[error("{message}")]
    ManifestRollback { message: String },
    #[error("{message}")]
    Io { message: String },
    /// Message complet construit par [`download_failure`] selon la cause.
    #[error("{message}")]
    Download { message: String },
    #[error("Ferme le jeu avant de {action}.")]
    GameRunning { action: &'static str },
}

impl LauncherError {
    fn kind(&self) -> &'static str {
        match self {
            Self::NotAuthenticated => "not_authenticated",
            Self::Manifest { .. } => "manifest",
            Self::Signature { .. } => "signature",
            Self::ManifestExpired { .. } => "manifest_expired",
            Self::ManifestRollback { .. } => "manifest_rollback",
            Self::Io { .. } => "io",
            Self::Download { .. } => "download",
            Self::GameRunning { .. } => "game_running",
        }
    }
}

impl Serialize for LauncherError {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serialize_ipc_error(serializer, self.kind(), &self.to_string())
    }
}

impl From<ManifestVerifyError> for LauncherError {
    fn from(e: ManifestVerifyError) -> Self {
        match e {
            ManifestVerifyError::Expired { .. } => Self::ManifestExpired {
                message: e.to_string(),
            },
            ManifestVerifyError::Rollback { .. } => Self::ManifestRollback {
                message: e.to_string(),
            },
            _ => Self::Signature {
                message: e.to_string(),
            },
        }
    }
}

/// Texte d'une cause, sans point final (on l'insère au milieu d'une phrase).
fn cause(e: impl std::fmt::Display) -> String {
    e.to_string().trim_end().trim_end_matches('.').to_string()
}

/// Échec du téléchargement du modpack, une seule consigne par cause :
/// - réseau : le message interne (fichier, tentatives, cause) + « relance
///   Jouer » ; « vérifie ta connexion » seulement s'il ne le dit pas déjà ;
/// - fichier corrompu : le message interne tel quel (il a sa consigne, rien
///   à voir avec la connexion) ;
/// - disque / tâche interrompue : le message interne, avec le contexte.
fn download_failure(e: DownloadError) -> LauncherError {
    const KEPT: &str = "les fichiers déjà reçus sont conservés";
    let text = cause(&e);
    let message = match e {
        DownloadError::Http { .. } if text.contains(" — ") => {
            format!("{text}. Relance « Jouer » : {KEPT}.")
        }
        DownloadError::Http { .. } => {
            format!("{text}. Vérifie ta connexion puis relance « Jouer » : {KEPT}.")
        }
        DownloadError::HashMismatch { .. } => format!("{text}."),
        DownloadError::Io { .. } | DownloadError::Internal(_) => format!(
            "Téléchargement des fichiers du jeu interrompu : {}.",
            mojang::lower_first(&text)
        ),
    };
    LauncherError::Download { message }
}

fn data_dir() -> Result<std::path::PathBuf, LauncherError> {
    game::data_dir().map_err(|e| LauncherError::Io {
        message: e.to_string(),
    })
}

async fn load_mod_prefs() -> Result<crate::storage::mod_prefs::ModPrefs, LauncherError> {
    crate::storage::mod_prefs::load()
        .await
        .map_err(|e| LauncherError::Io {
            message: format!(
                "Lecture de tes choix de mods optionnels impossible : {}.",
                cause(e)
            ),
        })
}

const LAUNCHER_VERSION: &str = env!("CARGO_PKG_VERSION");

/// Récupère le manifeste courant et ne le rend que s'il est digne de
/// confiance : signature Ed25519 valide, pas expiré, pas plus ancien que le
/// dernier accepté (anti-retour). Factorise le triplet jeton + fetch +
/// vérification autrefois recopié dans chaque commande.
pub(crate) async fn fetch_verified_manifest(
    state: &AuthState,
) -> Result<SignedManifest, LauncherError> {
    let token = state
        .store
        .get(SecretKey::RebornAccessToken)
        .map_err(|e| LauncherError::Io {
            message: format!(
                "Lecture de ta session dans le coffre de Windows impossible ({}) : déconnecte-toi puis reconnecte-toi.",
                cause(e)
            ),
        })?
        .ok_or(LauncherError::NotAuthenticated)?;

    let manifest = state
        .api
        .fetch_manifest(&token)
        .await
        .map_err(|e| LauncherError::Manifest {
            message: e.to_string(),
        })?;

    manifest::verify_signature(&manifest)?;

    // Fraîcheur : la signature prouve l'origine, pas l'actualité. Sans ces
    // deux contrôles, un vieux manifeste signé (rejoué par un proxy ou un
    // miroir) réinstallerait d'anciennes versions des mods.
    let api = state.api.base_url.as_str();
    let last_accepted = manifest::last_accepted_issued_at(api).await;
    manifest::check_freshness(&manifest, clock::now_ms(), last_accepted.as_deref())?;
    if let Err(e) = manifest::remember_issued_at(api, &manifest.issued_at).await {
        tracing::warn!("mémorisation de la date du manifeste impossible : {e}");
    }
    Ok(manifest)
}

/// Purge les .jar de mods/ absents du manifeste courant. Non bloquant : on
/// log seulement, le launch peut continuer même si la purge a échoué.
async fn purge_orphans<R: Runtime>(
    app: &AppHandle<R>,
    manifest: &SignedManifest,
    dir: &std::path::Path,
    mod_prefs: &crate::storage::mod_prefs::ModPrefs,
    context: &str,
) {
    match purge_orphan_mods(manifest, dir, mod_prefs).await {
        Ok(removed) if !removed.is_empty() => {
            tracing::info!(?removed, "{context} : mods orphelins supprimés");
            emit_mods_purged(app, removed, &manifest.minecraft_version);
        }
        Ok(_) => {}
        Err(e) => tracing::warn!("{context} : purge des mods orphelins impossible : {e}"),
    }
}

async fn compute_plan(
    manifest: &SignedManifest,
    dir: &std::path::Path,
    mod_prefs: &crate::storage::mod_prefs::ModPrefs,
) -> Result<Vec<PlanItem>, LauncherError> {
    compute_plan_export(manifest, dir, mod_prefs)
        .await
        .map_err(|e| LauncherError::Io {
            message: format!(
                "Analyse des fichiers du jeu dans {} impossible : {}.",
                dir.display(),
                cause(e)
            ),
        })
}

/// Étapes [1]..[3] : récupère le manifeste, le vérifie, calcule le diff,
/// retourne un preview que l'UI peut afficher avant de déclencher le
/// download.
#[tauri::command]
pub async fn launcher_check_update<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AuthState>,
    game: State<'_, GameState>,
) -> Result<UpdatePreview, LauncherError> {
    let manifest = fetch_verified_manifest(&state).await?;
    let outdated = launcher_is_outdated(LAUNCHER_VERSION, &manifest.min_launcher_version);

    let dir = data_dir()?;
    let mod_prefs = load_mod_prefs().await?;
    let plan = compute_plan(&manifest, &dir, &mod_prefs).await?;

    // Purge les orphelins dès le check : si l'utilisateur est déjà à jour
    // (plan vide), apply_update ne sera pas appelé et la purge ne
    // s'exécuterait pas. On la fait ici pour garantir que les jars d'une
    // version antérieure du manifeste sont retirés dès le prochain "Jouer".
    // Pas pendant une partie : les jars sont chargés par la JVM.
    if !game.is_busy() {
        purge_orphans(&app, &manifest, &dir, &mod_prefs, "check_update").await;
    }

    let bytes_total = plan.iter().map(|p| p.file.size).sum();

    Ok(UpdatePreview {
        version: manifest.version,
        minecraft_version: manifest.minecraft_version,
        plan,
        bytes_total,
        min_launcher_version: manifest.min_launcher_version,
        launcher_version: LAUNCHER_VERSION.to_string(),
        launcher_outdated: outdated,
    })
}

/// Étape [4] : exécute le plan. Émet `manifest:progress` au fil de l'eau.
#[tauri::command]
pub async fn launcher_apply_update<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AuthState>,
    game: State<'_, GameState>,
) -> Result<UpdateStatus, LauncherError> {
    // Jeu lancé = jars verrouillés par Windows : la mise à jour échouerait
    // au milieu (« accès refusé ») et laisserait un mods/ incohérent.
    if game.is_busy() {
        return Err(LauncherError::GameRunning {
            action: "mettre à jour les fichiers du jeu",
        });
    }

    let manifest = fetch_verified_manifest(&state).await?;
    if launcher_is_outdated(LAUNCHER_VERSION, &manifest.min_launcher_version) {
        return Ok(UpdateStatus::LauncherOutdated {
            current: LAUNCHER_VERSION.to_string(),
            required: manifest.min_launcher_version,
        });
    }

    let dir = data_dir()?;
    let mod_prefs = load_mod_prefs().await?;
    let plan = compute_plan(&manifest, &dir, &mod_prefs).await?;

    // Purge les .jar de mods/ qui ne sont plus dans le manifeste courant
    // (typique : ancienne version d'un mod laissée orpheline après bump du
    // manifeste, sinon Fabric charge les 2 et c'est la collision).
    purge_orphans(&app, &manifest, &dir, &mod_prefs, "manifest sync").await;

    // Nettoyage auto des résidus obsolètes (caches MCEF, vieilles versions MC,
    // crash dumps) — cf clean_obsolete_files. Best-effort, hors du runtime
    // async (parcours disque bloquant).
    if let Ok(clean) = tokio::task::spawn_blocking(clean_obsolete_files).await {
        if !clean.removed.is_empty() {
            tracing::info!(
                removed = ?clean.removed,
                freed_mb = clean.freed_mb,
                "cleanup: résidus obsolètes supprimés"
            );
        }
    }

    if plan.is_empty() {
        return Ok(UpdateStatus::UpToDate {
            version: manifest.version,
        });
    }

    let count = plan.len();
    download_plan(&app, &state.download_http, plan, &dir)
        .await
        .map_err(download_failure)?;

    Ok(UpdateStatus::Updated {
        version: manifest.version,
        files_downloaded: count,
    })
}

/// Retourne la map enabled actuelle des mods optionnels du user.
/// Clé = filename (ex "iris-fabric-1.8.8+mc1.21.1.jar"). Valeur = bool.
/// Clés absentes = false (opt-in).
#[tauri::command]
pub async fn launcher_get_mod_prefs(
) -> Result<std::collections::HashMap<String, bool>, LauncherError> {
    Ok(load_mod_prefs().await?.enabled)
}

/// Description d'un mod optionnel exposée à l'UI pour la liste à cocher.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OptionalMod {
    pub filename: String,
    pub size_bytes: u64,
    pub enabled: bool,
    pub installed: bool,
}

/// Retourne la liste des mods optionnels du manifeste courant (flag
/// required:false), avec leur état enabled (depuis ModPrefs) + installed
/// (fichier présent dans mods/). UI : Mods.tsx affiche un toggle par
/// entrée, le clic appelle launcher_set_mod_pref puis re-fetch.
#[tauri::command]
pub async fn launcher_list_optional_mods(
    state: State<'_, AuthState>,
) -> Result<Vec<OptionalMod>, LauncherError> {
    let manifest = fetch_verified_manifest(&state).await?;
    let prefs = load_mod_prefs().await?;
    let dir = data_dir()?;

    let mut out = Vec::new();
    for f in manifest.files.iter().filter(|f| !f.required) {
        let Some(filename) = std::path::Path::new(&f.path)
            .file_name()
            .and_then(|n| n.to_str())
            .filter(|n| !n.is_empty())
        else {
            continue;
        };
        let installed = tokio::fs::try_exists(dir.join(&f.path))
            .await
            .unwrap_or(false);
        out.push(OptionalMod {
            filename: filename.to_string(),
            size_bytes: f.size,
            enabled: prefs.is_enabled(filename),
            installed,
        });
    }
    out.sort_by(|a, b| a.filename.cmp(&b.filename));
    Ok(out)
}

/// Toggle (ou set explicite) un mod optionnel à enabled=true|false.
/// L'effet (DL ou purge du jar) s'applique au prochain `launcher_check_update`
/// — typiquement quand l'utilisateur arrive sur Home ou clique Jouer.
#[tauri::command]
pub async fn launcher_set_mod_pref(filename: String, enabled: bool) -> Result<(), LauncherError> {
    let mut prefs = load_mod_prefs().await?;
    prefs.set(filename, enabled);
    crate::storage::mod_prefs::save(&prefs)
        .await
        .map_err(|e| LauncherError::Io {
            message: format!(
                "Enregistrement de tes choix de mods optionnels impossible : {}.",
                cause(e)
            ),
        })
}

/// Comparaison naïve de versions semver "x.y.z". Retourne `true` si `current < min`.
fn launcher_is_outdated(current: &str, min: &str) -> bool {
    let to_tuple = |s: &str| -> (u32, u32, u32) {
        let mut parts = s.split('.').map(|p| p.parse::<u32>().unwrap_or(0));
        (
            parts.next().unwrap_or(0),
            parts.next().unwrap_or(0),
            parts.next().unwrap_or(0),
        )
    };
    to_tuple(current) < to_tuple(min)
}

// ─────────────────────────────────────────────────────────────
//  Onglet Paramètres › Jeu — infos d'installation + actions.
// ─────────────────────────────────────────────────────────────

/// Infos affichées dans l'onglet « Jeu » des paramètres.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GameInfo {
    pub install_dir: String,
    pub mc_version: String,
    pub disk_free_gb: f64,
    pub disk_total_gb: f64,
}

/// Chemin d'installation + version MC + espace disque du volume hôte.
#[tauri::command]
pub async fn launcher_game_info() -> Result<GameInfo, LauncherError> {
    let dir = data_dir()?;

    // Volume contenant le game dir : mount point le plus long qui préfixe le chemin.
    let disks = sysinfo::Disks::new_with_refreshed_list();
    let (mut free, mut total, mut best) = (0u64, 0u64, 0usize);
    for d in disks.list() {
        let mp = d.mount_point();
        if dir.starts_with(mp) && mp.as_os_str().len() >= best {
            best = mp.as_os_str().len();
            free = d.available_space();
            total = d.total_space();
        }
    }
    Ok(GameInfo {
        install_dir: dir.display().to_string(),
        mc_version: game::minecraft_version(),
        disk_free_gb: free as f64 / 1_000_000_000.0,
        disk_total_gb: total as f64 / 1_000_000_000.0,
    })
}

/// Ouvre le dossier d'installation dans l'explorateur du système.
#[tauri::command]
pub async fn launcher_open_install_dir<R: Runtime>(app: AppHandle<R>) -> Result<(), LauncherError> {
    use tauri_plugin_opener::OpenerExt;
    let dir = data_dir()?;
    let _ = tokio::fs::create_dir_all(&dir).await;
    app.opener()
        .open_path(dir.to_string_lossy().to_string(), None::<&str>)
        .map_err(|e| LauncherError::Io {
            message: format!(
                "Ouverture du dossier {} impossible : {}.",
                dir.display(),
                cause(e)
            ),
        })
}

/// Réinstallation complète : supprime les dossiers téléchargeables (mods,
/// libraries, versions, assets, Java) et les témoins de vérification —
/// préserve saves/config/options/reborn. Le prochain check-update +
/// lancement re-télécharge tout.
#[tauri::command]
pub async fn launcher_reinstall_all(game: State<'_, GameState>) -> Result<(), LauncherError> {
    if game.is_busy() {
        return Err(LauncherError::GameRunning {
            action: "tout réinstaller",
        });
    }
    let dir = data_dir()?;
    // `runtime` (Java) et `.reborn-verify` (témoins) aussi : sans eux, un
    // JRE abîmé à taille égale repasserait par le chemin rapide du témoin.
    for sub in [
        "mods",
        "libraries",
        "versions",
        "assets",
        "runtime",
        ".reborn-verify",
    ] {
        let p = dir.join(sub);
        if tokio::fs::try_exists(&p).await.unwrap_or(false) {
            tokio::fs::remove_dir_all(&p).await.map_err(|e| LauncherError::Io {
                message: format!(
                    "Suppression de {} impossible ({e}) : un fichier est peut-être ouvert par un autre programme (antivirus, explorateur). Réessaie dans un instant.",
                    p.display()
                ),
            })?;
        }
    }
    Ok(())
}

// ─────────────────────────────────────────────────────────────
//  Nettoyage des fichiers obsolètes (cruft migration 1.21 → 26.1,
//  MCEF abandonné, crash dumps). Appelé auto au apply_update + bouton
//  manuel dans Paramètres › Jeu.
// ─────────────────────────────────────────────────────────────

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CleanResult {
    pub removed: Vec<String>,
    pub freed_mb: f64,
}

fn dir_size(p: &std::path::Path) -> u64 {
    if p.is_file() {
        return std::fs::metadata(p).map(|m| m.len()).unwrap_or(0);
    }
    let mut total = 0u64;
    if let Ok(entries) = std::fs::read_dir(p) {
        for e in entries.flatten() {
            total += dir_size(&e.path());
        }
    }
    total
}

/// Supprime les résidus obsolètes du game dir : caches MCEF (abandonné),
/// anciennes versions MC, crash dumps JVM. Préserve saves/config utiles/mods.
/// Bloquant (parcours disque) : à appeler via `spawn_blocking`.
pub fn clean_obsolete_files() -> CleanResult {
    let mut removed = Vec::new();
    let mut freed = 0u64;
    let Ok(dir) = paths::game_dir() else {
        return CleanResult {
            removed,
            freed_mb: 0.0,
        };
    };

    // 1. Dossiers obsolètes fixes (MCEF + vieux caches Reborn).
    for rel in [
        "config/mcef",
        "config/mcef-modern",
        "mods/mcef-cache",
        "mods/mcef-libraries",
        "reborn-hud-cache",
    ] {
        let p = dir.join(rel);
        if p.exists() {
            freed += dir_size(&p);
            if std::fs::remove_dir_all(&p).is_ok() {
                removed.push(rel.to_string());
            }
        }
    }

    // 2. Anciennes versions MC (on garde seulement la courante).
    let current = game::minecraft_version();
    if let Ok(entries) = std::fs::read_dir(dir.join("versions")) {
        for e in entries.flatten() {
            let name = e.file_name().to_string_lossy().to_string();
            if name != current && e.path().is_dir() {
                freed += dir_size(&e.path());
                if std::fs::remove_dir_all(e.path()).is_ok() {
                    removed.push(format!("versions/{name}"));
                }
            }
        }
    }

    // 3. Crash dumps JVM (hs_err_pid*.log) à la racine.
    if let Ok(entries) = std::fs::read_dir(&dir) {
        for e in entries.flatten() {
            let name = e.file_name().to_string_lossy().to_string();
            if name.starts_with("hs_err_pid") && name.ends_with(".log") {
                freed += std::fs::metadata(e.path()).map(|m| m.len()).unwrap_or(0);
                if std::fs::remove_file(e.path()).is_ok() {
                    removed.push(name);
                }
            }
        }
    }

    CleanResult {
        removed,
        freed_mb: freed as f64 / 1_000_000.0,
    }
}

/// Commande exposée : nettoyage manuel (bouton Paramètres › Jeu).
#[tauri::command]
pub async fn launcher_clean_obsolete(
    game: State<'_, GameState>,
) -> Result<CleanResult, LauncherError> {
    if game.is_busy() {
        return Err(LauncherError::GameRunning {
            action: "nettoyer les fichiers obsolètes",
        });
    }
    tokio::task::spawn_blocking(clean_obsolete_files)
        .await
        .map_err(|e| LauncherError::Io {
            message: format!("Le nettoyage s'est interrompu : {}.", cause(e)),
        })
}

/// Bouton « Réparer » (Paramètres › Jeu) : efface les témoins de
/// vérification (`<game_dir>/.reborn-verify/`) pour que le « Jouer » suivant
/// revérifie par hash Java, les bibliothèques, les ressources et Fabric, et
/// retélécharge ce qui est abîmé. Le modpack, lui, est resynchronisé tout de
/// suite par `launcher_apply_update` (appelé juste après par le front).
#[tauri::command]
pub async fn launcher_repair(game: State<'_, GameState>) -> Result<(), LauncherError> {
    if game.is_busy() {
        return Err(LauncherError::GameRunning {
            action: "réparer les fichiers du jeu",
        });
    }
    let dir = data_dir()?;
    mojang::force_full_verify(&dir)
        .await
        .map_err(|e| LauncherError::Io {
            message: format!(
                "Réinitialisation de la vérification des fichiers impossible : {}.",
                cause(e)
            ),
        })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_compare() {
        assert!(launcher_is_outdated("0.1.0", "1.0.0"));
        assert!(launcher_is_outdated("1.0.0", "1.0.1"));
        assert!(!launcher_is_outdated("1.0.0", "1.0.0"));
        assert!(!launcher_is_outdated("2.0.0", "1.99.99"));
    }

    #[test]
    fn mods_purged_payload_shape() {
        let v = serde_json::to_value(ModsPurgedPayload {
            removed: vec!["mods/old.jar".into()],
            target_mc_version: "26.2".into(),
        })
        .unwrap();
        assert_eq!(v["removed"][0], "mods/old.jar");
        assert_eq!(v["targetMcVersion"], "26.2");
    }

    #[test]
    fn launcher_errors_serialize_as_kind_and_message() {
        let v = serde_json::to_value(LauncherError::GameRunning {
            action: "tout réinstaller",
        })
        .unwrap();
        assert_eq!(v["kind"], "game_running");
        assert_eq!(v["message"], "Ferme le jeu avant de tout réinstaller.");
        let expired: LauncherError = ManifestVerifyError::Expired {
            expires_at: "01/01/2026 à 00:00 UTC".into(),
        }
        .into();
        assert_eq!(
            serde_json::to_value(&expired).unwrap()["kind"],
            "manifest_expired"
        );
    }
}
