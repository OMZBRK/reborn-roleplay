//! Orchestrateur du flow de mise a jour + lancement Minecraft.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §8.1.
//!
//! Aujourd'hui (semaine 3) on couvre les etapes [1]..[4] : fetch du
//! manifest signe, verification Ed25519, diff et telechargement.
//! Le spawn JVM (etapes [5]..[12]) est laisse en TODO pour la semaine 4.

pub mod game;
pub mod jvm;
pub mod paths;
pub mod runtime;

pub use game::{launcher_launch_game, launcher_stop_game, GameState};
pub use paths::game_dir;

use serde::Serialize;
use tauri::{AppHandle, Runtime, State};

use crate::auth::AuthState;
use crate::manifest::{compute_plan_export, download_plan, verify_signature, PlanItem};
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
pub async fn launcher_check_update(
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
    let plan = compute_plan_export(&manifest, &dir)
        .await
        .map_err(|e| LauncherError::Io { message: e.to_string() })?;

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
    let plan = compute_plan_export(&manifest, &dir)
        .await
        .map_err(|e| LauncherError::Io { message: e.to_string() })?;

    if plan.is_empty() {
        return Ok(UpdateStatus::UpToDate {
            version: manifest.version,
        });
    }

    let count = plan.len();
    download_plan(&app, &state.http, plan, &dir)
        .await
        .map_err(|e| LauncherError::Download { message: e.to_string() })?;

    Ok(UpdateStatus::Updated {
        version: manifest.version,
        files_downloaded: count,
    })
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
