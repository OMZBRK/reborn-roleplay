//! Resolution du game directory isolé (cf §4.2).

use std::path::PathBuf;

const APP_FOLDER: &str = "RebornRoleplay";

#[derive(Debug, thiserror::Error)]
pub enum PathError {
    #[error("repertoire utilisateur introuvable")]
    NoHome,
}

/// Retourne le path absolu du game directory selon la plateforme.
///
/// - Windows : `%APPDATA%\RebornRoleplay\`
/// - macOS   : `~/Library/Application Support/RebornRoleplay/`
/// - Linux   : `~/.local/share/RebornRoleplay/`
pub fn game_dir() -> Result<PathBuf, PathError> {
    #[cfg(target_os = "windows")]
    {
        let base = std::env::var_os("APPDATA").ok_or(PathError::NoHome)?;
        Ok(PathBuf::from(base).join(APP_FOLDER))
    }

    #[cfg(target_os = "macos")]
    {
        let home = std::env::var_os("HOME").ok_or(PathError::NoHome)?;
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
            .ok_or(PathError::NoHome)?;
        Ok(xdg.join(APP_FOLDER))
    }
}

/// Dossier d'instance d'un serveur (multi-version).
///
/// Chaque serveur/version tourne dans `game_dir/instances/<id>/` : c'est le
/// `--gameDir` de la JVM, il porte `mods/`, `config/`, `saves/`, `options.txt`
/// et le play-token. Isole les `mods/` des versions pour qu'elles ne se
/// purgent pas entre elles (cf `mods::purge_incompatible_mods`).
///
/// Les caches lourds partagés (`versions/`, `assets/`, `libraries/`,
/// `runtime/`) restent à la racine `game_dir` — ils sont clés par version id
/// et coexistent sans conflit, donc on ne les duplique pas par instance.
///
/// Pour l'instance `rp` on garde la **racine `game_dir`** (rétro-compatibilité
/// avec l'ancien flux mono-serveur : ne casse pas les installs existantes).
pub fn instance_dir(server_id: &str) -> Result<PathBuf, PathError> {
    let root = game_dir()?;
    if server_id == "rp" {
        Ok(root)
    } else {
        Ok(root.join("instances").join(server_id))
    }
}

/// Dossier `mods/` d'une instance de serveur.
pub fn mods_dir(server_id: &str) -> Result<PathBuf, PathError> {
    Ok(instance_dir(server_id)?.join("mods"))
}
