//! Preferences locales utilisateur (RAM, resolution, etc.).
//!
//! Persistees en JSON dans `<game_dir>/prefs.json`. Tauri-plugin-store
//! ferait l'affaire, mais on garde la dep arbre pour l'instant — ces
//! prefs ne sont ni secretes ni partagees.

use serde::{Deserialize, Serialize};
use tokio::fs;

use crate::launcher::paths;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct Preferences {
    pub ram_mb: u32,
    pub width: u32,
    pub height: u32,
    pub auto_connect: bool,
    pub discord_rich_presence: bool,
    pub language: String,
}

impl Default for Preferences {
    fn default() -> Self {
        Self {
            ram_mb: 4096,
            width: 1280,
            height: 720,
            auto_connect: true,
            discord_rich_presence: true,
            language: "fr".to_string(),
        }
    }
}

#[derive(Debug, thiserror::Error)]
pub enum PrefsError {
    #[error("io : {0}")]
    Io(#[from] std::io::Error),
    #[error("paths : {0}")]
    Paths(#[from] paths::PathError),
    #[error("json : {0}")]
    Json(#[from] serde_json::Error),
}

fn prefs_path() -> Result<std::path::PathBuf, PrefsError> {
    Ok(paths::game_dir()?.join("prefs.json"))
}

pub async fn load() -> Result<Preferences, PrefsError> {
    let path = prefs_path()?;
    if !fs::try_exists(&path).await? {
        return Ok(Preferences::default());
    }
    let bytes = fs::read(&path).await?;
    let prefs = serde_json::from_slice::<Preferences>(&bytes).unwrap_or_default();
    Ok(prefs)
}

pub async fn save(prefs: &Preferences) -> Result<(), PrefsError> {
    let path = prefs_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).await?;
    }
    let pretty = serde_json::to_vec_pretty(prefs)?;
    fs::write(&path, pretty).await?;
    Ok(())
}
