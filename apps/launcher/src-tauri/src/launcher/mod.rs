//! Orchestrateur du flow de mise a jour + lancement Minecraft.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §8.1.
//!
//! Aujourd'hui (semaine 3) on couvre les etapes [1]..[4] : fetch du
//! manifest signe, verification Ed25519, diff et telechargement.
//! Le spawn JVM (etapes [5]..[12]) est laisse en TODO pour la semaine 4.

pub mod assets;
pub mod diagnostics;
pub mod fabric;
pub mod game;
pub mod jvm;
pub mod libraries;
pub mod mods;
pub mod mojang;
pub mod paths;
pub mod runtime;

pub use game::{launcher_launch_game, launcher_stop_game, GameState};
pub use paths::game_dir;

use serde::Serialize;
use tauri::{AppHandle, Emitter, Runtime, State};

use crate::auth::AuthState;
use crate::manifest::{compute_plan_export, download_plan, purge_orphan_mods, verify_signature, PlanItem};
use crate::storage::secrets::SecretKey;

#[derive(Debug, Serialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum UpdateStatus {
    /// Le manifest est valide et tous les fichiers locaux matchent.
    UpToDate { version: String },
    /// Une fois le download termine, le launcher est pret.
    Updated {
        version: String,
        files_downloaded: usize,
    },
    /// Le launcher courant est trop vieux pour ce manifest.
    LauncherOutdated {
        current: String,
        required: String,
    },
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

#[derive(Debug, thiserror::Error, Serialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum LauncherError {
    #[error("non authentifie")]
    NotAuthenticated,
    #[error("manifest indisponible : {message}")]
    Manifest { message: String },
    #[error("signature manifest invalide : {message}")]
    Signature { message: String },
    #[error("io : {message}")]
    Io { message: String },
    #[error("telechargement : {message}")]
    Download { message: String },
}

const LAUNCHER_VERSION: &str = env!("CARGO_PKG_VERSION");

/// Etapes [1]..[3] : recupere le manifest, verifie la signature, calcule
/// le diff, retourne un preview que l'UI peut afficher avant de declencher
/// le download.
#[tauri::command]
pub async fn launcher_check_update<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AuthState>,
) -> Result<UpdatePreview, LauncherError> {
    let token = state
        .store
        .get(SecretKey::RebornAccessToken)
        .map_err(|e| LauncherError::Io { message: e.to_string() })?
        .ok_or(LauncherError::NotAuthenticated)?;

    let manifest = state
        .api
        .fetch_manifest(&token)
        .await
        .map_err(|e| LauncherError::Manifest { message: e.to_string() })?;

    verify_signature(&manifest).map_err(|e| LauncherError::Signature { message: e.to_string() })?;

    let outdated = launcher_is_outdated(LAUNCHER_VERSION, &manifest.min_launcher_version);

    let dir = paths::game_dir().map_err(|e| LauncherError::Io { message: e.to_string() })?;
    let mod_prefs = crate::storage::mod_prefs::load()
        .await
        .map_err(|e| LauncherError::Io { message: e.to_string() })?;
    let plan = compute_plan_export(&manifest, &dir, &mod_prefs)
        .await
        .map_err(|e| LauncherError::Io { message: e.to_string() })?;

    // Purge les orphans des le check : si l'utilisateur est deja a jour
    // (plan vide), apply_update ne sera pas appele et la purge ne s'executerait
    // pas. On la fait ici a la place pour garantir que les jars d'une
    // version anterieure du manifest sont retires des le prochain "Jouer".
    match purge_orphan_mods(&manifest, &dir, &mod_prefs).await {
        Ok(removed) if !removed.is_empty() => {
            tracing::info!(?removed, "check_update: orphan mods supprimes");
            let _ = app.emit("mods:purged", &removed);
        }
        Ok(_) => {}
        Err(e) => {
            tracing::warn!(error = ?e, "check_update: purge orphans failed");
        }
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

/// Etape [4] : execute le plan calcule precedemment. Emet
/// `manifest:progress` au fil de l'eau.
#[tauri::command]
pub async fn launcher_apply_update<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AuthState>,
) -> Result<UpdateStatus, LauncherError> {
    let token = state
        .store
        .get(SecretKey::RebornAccessToken)
        .map_err(|e| LauncherError::Io { message: e.to_string() })?
        .ok_or(LauncherError::NotAuthenticated)?;

    let manifest = state
        .api
        .fetch_manifest(&token)
        .await
        .map_err(|e| LauncherError::Manifest { message: e.to_string() })?;

    verify_signature(&manifest).map_err(|e| LauncherError::Signature { message: e.to_string() })?;

    if launcher_is_outdated(LAUNCHER_VERSION, &manifest.min_launcher_version) {
        return Ok(UpdateStatus::LauncherOutdated {
            current: LAUNCHER_VERSION.to_string(),
            required: manifest.min_launcher_version,
        });
    }

    let dir = paths::game_dir().map_err(|e| LauncherError::Io { message: e.to_string() })?;
    let mod_prefs = crate::storage::mod_prefs::load()
        .await
        .map_err(|e| LauncherError::Io { message: e.to_string() })?;
    let plan = compute_plan_export(&manifest, &dir, &mod_prefs)
        .await
        .map_err(|e| LauncherError::Io { message: e.to_string() })?;

    // Purge les .jar dans mods/ qui ne sont plus dans le manifest courant
    // (typique : ancienne version d'un mod laissee orpheline apres bump de
    // version manifest, sinon Fabric charge les 2 et c'est la collision).
    // Idempotent : retourne [] si rien a purger. Erreur non bloquante : on
    // log seulement, le launch peut continuer meme si la purge a foire.
    match purge_orphan_mods(&manifest, &dir, &mod_prefs).await {
        Ok(removed) if !removed.is_empty() => {
            tracing::info!(?removed, "manifest sync: orphan mods supprimes");
            let _ = app.emit("mods:purged", &removed);
        }
        Ok(_) => {}
        Err(e) => {
            tracing::warn!(error = ?e, "manifest sync: purge orphans failed");
        }
    }

    // Nettoyage auto des résidus obsolètes (caches MCEF, vieilles versions MC,
    // crash dumps) — cf clean_obsolete_files. Best-effort, ne bloque pas.
    let clean = clean_obsolete_files();
    if !clean.removed.is_empty() {
        tracing::info!(
            removed = ?clean.removed,
            freed_mb = clean.freed_mb,
            "cleanup: résidus obsolètes supprimés"
        );
    }

    if plan.is_empty() {
        return Ok(UpdateStatus::UpToDate {
            version: manifest.version,
        });
    }

    let count = plan.len();
    download_plan(&app, &state.download_http, plan, &dir)
        .await
        .map_err(|e| LauncherError::Download { message: e.to_string() })?;

    Ok(UpdateStatus::Updated {
        version: manifest.version,
        files_downloaded: count,
    })
}

/// Retourne la map enabled actuelle des mods optionnels du user.
/// Cle = filename (ex "iris-fabric-1.8.8+mc1.21.1.jar"). Valeur = bool.
/// Cles absentes = false (opt-in).
#[tauri::command]
pub async fn launcher_get_mod_prefs() -> Result<std::collections::HashMap<String, bool>, LauncherError> {
    let prefs = crate::storage::mod_prefs::load()
        .await
        .map_err(|e| LauncherError::Io { message: e.to_string() })?;
    Ok(prefs.enabled)
}

/// Description d'un mod optionnel exposee a l'UI pour la liste cocher.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OptionalMod {
    pub filename: String,
    pub size_bytes: u64,
    pub enabled: bool,
    pub installed: bool,
}

/// Retourne la liste des mods optionnels du manifest courant (flag
/// required:false), avec leur etat enabled (depuis ModPrefs) + installed
/// (fichier present dans mods/). UI : Mods.tsx affiche un toggle par
/// entree, le clic appelle launcher_set_mod_pref puis re-fetch.
#[tauri::command]
pub async fn launcher_list_optional_mods(
    state: State<'_, AuthState>,
) -> Result<Vec<OptionalMod>, LauncherError> {
    let token = state
        .store
        .get(SecretKey::RebornAccessToken)
        .map_err(|e| LauncherError::Io { message: e.to_string() })?
        .ok_or(LauncherError::NotAuthenticated)?;

    let manifest = state
        .api
        .fetch_manifest(&token)
        .await
        .map_err(|e| LauncherError::Manifest { message: e.to_string() })?;
    verify_signature(&manifest).map_err(|e| LauncherError::Signature { message: e.to_string() })?;

    let prefs = crate::storage::mod_prefs::load()
        .await
        .map_err(|e| LauncherError::Io { message: e.to_string() })?;
    let dir = paths::game_dir().map_err(|e| LauncherError::Io { message: e.to_string() })?;

    let mut out = Vec::new();
    for f in &manifest.files {
        if f.required {
            continue;
        }
        let filename = std::path::Path::new(&f.path)
            .file_name()
            .and_then(|n| n.to_str())
            .map(|s| s.to_string())
            .unwrap_or_default();
        if filename.is_empty() {
            continue;
        }
        let installed = tokio::fs::try_exists(dir.join(&f.path))
            .await
            .unwrap_or(false);
        out.push(OptionalMod {
            filename: filename.clone(),
            size_bytes: f.size,
            enabled: prefs.is_enabled(&filename),
            installed,
        });
    }
    out.sort_by(|a, b| a.filename.cmp(&b.filename));
    Ok(out)
}

/// Toggle (ou set explicite) un mod optionnel a enabled=true|false.
/// L'effet (DL ou purge du jar) s'applique au prochain `launcher_check_update`
/// — typiquement quand l'utilisateur arrive sur Home ou clique Jouer.
#[tauri::command]
pub async fn launcher_set_mod_pref(
    filename: String,
    enabled: bool,
) -> Result<(), LauncherError> {
    let mut prefs = crate::storage::mod_prefs::load()
        .await
        .map_err(|e| LauncherError::Io { message: e.to_string() })?;
    prefs.set(filename, enabled);
    crate::storage::mod_prefs::save(&prefs)
        .await
        .map_err(|e| LauncherError::Io { message: e.to_string() })?;
    Ok(())
}

/// Comparaison naive de versions semver "x.y.z". Retourne `true` si `current < min`.
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
    let dir = paths::game_dir().map_err(|e| LauncherError::Io { message: e.to_string() })?;
    let mc_version = std::env::var("REBORN_MC_VERSION").unwrap_or_else(|_| "26.2".into());

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
        mc_version,
        disk_free_gb: free as f64 / 1_000_000_000.0,
        disk_total_gb: total as f64 / 1_000_000_000.0,
    })
}

/// Ouvre le dossier d'installation dans l'explorateur du système.
#[tauri::command]
pub async fn launcher_open_install_dir<R: Runtime>(app: AppHandle<R>) -> Result<(), LauncherError> {
    use tauri_plugin_opener::OpenerExt;
    let dir = paths::game_dir().map_err(|e| LauncherError::Io { message: e.to_string() })?;
    let _ = std::fs::create_dir_all(&dir);
    app.opener()
        .open_path(dir.to_string_lossy().to_string(), None::<&str>)
        .map_err(|e| LauncherError::Io { message: e.to_string() })?;
    Ok(())
}

/// Réinstallation complète : supprime les dossiers téléchargeables (mods,
/// libraries, versions, assets) — préserve saves/config/options/reborn. Le
/// prochain check-update + lancement re-télécharge tout.
#[tauri::command]
pub async fn launcher_reinstall_all() -> Result<(), LauncherError> {
    let dir = paths::game_dir().map_err(|e| LauncherError::Io { message: e.to_string() })?;
    for sub in ["mods", "libraries", "versions", "assets"] {
        let p = dir.join(sub);
        if p.exists() {
            std::fs::remove_dir_all(&p)
                .map_err(|e| LauncherError::Io { message: format!("{sub}: {e}") })?;
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
pub fn clean_obsolete_files() -> CleanResult {
    let mut removed = Vec::new();
    let mut freed = 0u64;
    let dir = match paths::game_dir() {
        Ok(d) => d,
        Err(_) => return CleanResult { removed, freed_mb: 0.0 },
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
    let current = std::env::var("REBORN_MC_VERSION").unwrap_or_else(|_| "26.2".into());
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
pub async fn launcher_clean_obsolete() -> Result<CleanResult, LauncherError> {
    Ok(clean_obsolete_files())
}
