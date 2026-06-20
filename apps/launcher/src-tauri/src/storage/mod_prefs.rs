//! Preferences utilisateur pour les mods optionnels du manifest.
//!
//! Map `filename -> enabled` persistee dans `<game_dir>/mod-prefs.json`.
//! Default pour clef absente = `false` (opt-in explicite).
//!
//! La purge & le diff respectent ce map :
//!   - file.required: true  -> toujours DL + jamais purge orphan
//!   - file.required: false :
//!       - prefs[filename] = true  -> traite comme requis (DL + preserve)
//!       - sinon (default)         -> skip DL + purge si present localement

use std::collections::HashMap;
use std::path::PathBuf;

use serde::{Deserialize, Serialize};
use tokio::fs;

use crate::launcher::paths;

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(transparent)]
pub struct ModPrefs {
    pub enabled: HashMap<String, bool>,
}

impl ModPrefs {
    /// True si le mod optionnel est explicitement enable par le user.
    /// False par defaut (les optionnels sont opt-in, donc clef absente = off).
    pub fn is_enabled(&self, filename: &str) -> bool {
        *self.enabled.get(filename).unwrap_or(&false)
    }

    pub fn set(&mut self, filename: String, enabled: bool) {
        self.enabled.insert(filename, enabled);
    }
}

#[derive(Debug, thiserror::Error)]
pub enum ModPrefsError {
    #[error("io : {0}")]
    Io(#[from] std::io::Error),
    #[error("paths : {0}")]
    Paths(#[from] paths::PathError),
    #[error("json : {0}")]
    Json(#[from] serde_json::Error),
}

fn prefs_path() -> Result<PathBuf, ModPrefsError> {
    Ok(paths::game_dir()?.join("mod-prefs.json"))
}

pub async fn load() -> Result<ModPrefs, ModPrefsError> {
    let path = prefs_path()?;
    if !fs::try_exists(&path).await? {
        return Ok(ModPrefs::default());
    }
    let bytes = fs::read(&path).await?;
    Ok(serde_json::from_slice(&bytes).unwrap_or_default())
}

pub async fn save(prefs: &ModPrefs) -> Result<(), ModPrefsError> {
    let path = prefs_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).await?;
    }
    let pretty = serde_json::to_vec_pretty(prefs)?;
    fs::write(&path, pretty).await?;
    Ok(())
}
