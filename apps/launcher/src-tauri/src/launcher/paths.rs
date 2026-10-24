//! Résolution du game directory isolé (cf §4.2).

use std::path::PathBuf;

const APP_FOLDER: &str = "RebornRoleplay";

#[derive(Debug, thiserror::Error)]
pub enum PathError {
    #[error(
        "Dossier de données introuvable : la variable d'environnement {0} n'est pas définie, \
         impossible de savoir où installer le jeu."
    )]
    NoHome(&'static str),
}

/// Retourne le path absolu du game directory selon la plateforme.
///
/// - Windows : `%APPDATA%\RebornRoleplay\`
/// - macOS   : `~/Library/Application Support/RebornRoleplay/`
/// - Linux   : `$XDG_DATA_HOME/RebornRoleplay/` (défaut `~/.local/share/`)
pub fn game_dir() -> Result<PathBuf, PathError> {
    #[cfg(target_os = "windows")]
    {
        let base = std::env::var_os("APPDATA").ok_or(PathError::NoHome("%APPDATA%"))?;
        Ok(PathBuf::from(base).join(APP_FOLDER))
    }

    #[cfg(target_os = "macos")]
    {
        let home = std::env::var_os("HOME").ok_or(PathError::NoHome("$HOME"))?;
        Ok(PathBuf::from(home)
            .join("Library")
            .join("Application Support")
            .join(APP_FOLDER))
    }

    #[cfg(all(unix, not(target_os = "macos")))]
    {
        let xdg = std::env::var_os("XDG_DATA_HOME")
            .map(PathBuf::from)
            .or_else(|| {
                std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".local").join("share"))
            })
            .ok_or(PathError::NoHome("$HOME"))?;
        Ok(xdg.join(APP_FOLDER))
    }
}
