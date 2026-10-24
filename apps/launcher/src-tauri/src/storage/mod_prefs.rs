//! Préférences utilisateur pour les mods optionnels du manifest.
//!
//! Map `filename -> enabled` persistée dans `<game_dir>/mod-prefs.json`.
//! Default pour clé absente = `false` (opt-in explicite).
//!
//! La purge & le diff respectent ce map :
//!   - file.required: true  -> toujours DL + jamais purge orphan
//!   - file.required: false :
//!       - prefs[filename] = true  -> traité comme requis (DL + préservé)
//!       - sinon (default)         -> skip DL + purge si présent localement

use std::collections::HashMap;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use tokio::fs;

use super::describe_io_error;
use crate::launcher::paths;

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(transparent)]
pub struct ModPrefs {
    pub enabled: HashMap<String, bool>,
}

impl ModPrefs {
    /// True si le mod optionnel est explicitement activé par le user.
    /// False par défaut (les optionnels sont opt-in, donc clé absente = off).
    pub fn is_enabled(&self, filename: &str) -> bool {
        self.enabled.get(filename).copied().unwrap_or(false)
    }

    pub fn set(&mut self, filename: String, enabled: bool) {
        self.enabled.insert(filename, enabled);
    }
}

#[derive(Debug, thiserror::Error)]
pub enum ModPrefsError {
    #[error("{}", describe_io_error(.action, .path, .source))]
    Io {
        action: &'static str,
        path: PathBuf,
        source: std::io::Error,
    },
    #[error("Dossier du jeu introuvable : {0}")]
    Paths(#[from] paths::PathError),
    #[error("Impossible d'enregistrer tes choix de mods optionnels : {0}")]
    Json(#[from] serde_json::Error),
}

fn io_err(action: &'static str, path: &Path) -> impl FnOnce(std::io::Error) -> ModPrefsError {
    let path = path.to_path_buf();
    move |source| ModPrefsError::Io {
        action,
        path,
        source,
    }
}

fn prefs_path() -> Result<PathBuf, ModPrefsError> {
    Ok(paths::game_dir()?.join("mod-prefs.json"))
}

pub async fn load() -> Result<ModPrefs, ModPrefsError> {
    let path = prefs_path()?;
    let bytes = match fs::read(&path).await {
        Ok(b) => b,
        // Premier lancement : pas encore de choix enregistrés.
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(ModPrefs::default()),
        Err(e) => return Err(io_err("lire", &path)(e)),
    };
    // Fichier corrompu (édition à la main, écriture interrompue) : on repart
    // des valeurs par défaut plutôt que de bloquer le lancement.
    Ok(serde_json::from_slice(&bytes).unwrap_or_else(|e| {
        tracing::warn!(
            "{} illisible ({e}) : mods optionnels remis par défaut",
            path.display()
        );
        ModPrefs::default()
    }))
}

pub async fn save(prefs: &ModPrefs) -> Result<(), ModPrefsError> {
    let path = prefs_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .await
            .map_err(io_err("créer le dossier", parent))?;
    }
    let pretty = serde_json::to_vec_pretty(prefs)?;
    fs::write(&path, pretty)
        .await
        .map_err(io_err("écrire", &path))?;
    Ok(())
}
