//! Téléchargement de l'asset index + des asset objects.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §8.1 étape [4].
//!
//! Les « assets » Minecraft (textures, sons, langues) sont stockés par
//! Mojang dans un CAS adressé par hash :
//!
//! ```text
//! assets/indexes/<asset_index_id>.json
//! assets/objects/<aa>/<aabbccdd...>            ← fichier nommé par son sha1
//! ```
//!
//! L'index liste tous les hashs + tailles. Après une première vérification
//! complète, un témoin (`.reborn-verify/assets-<id>.json`) permet de sauter
//! le hachage des ~5000 objets tant qu'ils sont tous présents à la bonne
//! taille (cf `mojang::ensure_file_set`).

use serde::Deserialize;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use tokio::fs;

use super::mojang::{
    download_file, ensure_file_set, io_at, AssetIndexRef, MojangError, RemoteFile,
};

// Plus on monte, plus le risque de saturation TCP/DNS Windows + de blocage par
// un AV (real-time scanning) augmente. 8 reste rapide tout en évitant les
// avalanches d'erreurs réseau qu'on voit sur des réseaux résidentiels.
const MAX_CONCURRENT: usize = 8;
const RESOURCES_BASE: &str = "https://resources.download.minecraft.net";

#[derive(Debug, thiserror::Error)]
pub enum AssetsError {
    #[error(transparent)]
    Mojang(#[from] MojangError),
    #[error(
        "Index des ressources « {path} » illisible : {detail} — lance « Réparer » pour le \
         retélécharger."
    )]
    Index { path: String, detail: String },
}

impl AssetsError {
    /// Vrai si la cause est réseau (cf [`MojangError::is_network`]).
    pub(crate) fn is_network(&self) -> bool {
        matches!(self, Self::Mojang(e) if e.is_network())
    }
}

#[derive(Debug, Deserialize)]
struct AssetIndexFile {
    objects: HashMap<String, AssetObject>,
}

#[derive(Debug, Deserialize)]
struct AssetObject {
    hash: String,
    size: u64,
}

/// Résultat : chemin du dossier `assets/` (à passer en `--assetsDir` au
/// launch) + l'id de l'asset index (`--assetIndex`).
#[derive(Debug, Clone)]
pub struct AssetSetup {
    pub assets_dir: PathBuf,
    pub asset_index: String,
}

/// `on_progress(completed, total)` est appelé au fil de l'eau (~1 % près)
/// pendant la vérification des objets, pour que l'UI affiche une vraie barre
/// de progression (étape [4], la plus longue au premier lancement). Sur le
/// chemin rapide (témoin à jour), un seul appel `(total, total)`.
pub async fn ensure_assets(
    http: &reqwest::Client,
    game_dir: &Path,
    asset_ref: &AssetIndexRef,
    on_progress: impl Fn(usize, usize),
) -> Result<AssetSetup, AssetsError> {
    let assets_dir = game_dir.join("assets");
    let objects_dir = assets_dir.join("objects");

    // 1. Asset index JSON : petit (~500 Ko), re-haché à chaque lancement —
    //    c'est la racine de confiance de tous les objets.
    let index_path = assets_dir
        .join("indexes")
        .join(format!("{}.json", asset_ref.id));
    download_file(http, &asset_ref.url, Some(&asset_ref.sha1), &index_path).await?;
    let bytes = fs::read(&index_path).await.map_err(io_at(&index_path))?;
    let index_error = |detail: String| AssetsError::Index {
        path: index_path.display().to_string(),
        detail,
    };
    let index: AssetIndexFile =
        serde_json::from_slice(&bytes).map_err(|e| index_error(e.to_string()))?;

    // 2. Objets (dédupliqués par chemin dans ensure_file_set : plusieurs noms
    //    d'assets pointent parfois vers le même hash).
    let mut files = Vec::with_capacity(index.objects.len());
    for (name, object) in index.objects {
        if object.hash.len() != 40 || !object.hash.bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err(index_error(format!(
                "hash invalide « {} » pour l'asset « {name} »",
                object.hash
            )));
        }
        let prefix = &object.hash[..2];
        files.push(RemoteFile {
            url: format!("{RESOURCES_BASE}/{prefix}/{}", object.hash),
            dest: objects_dir.join(prefix).join(&object.hash),
            sha1: Some(object.hash),
            size: Some(object.size),
        });
    }

    ensure_file_set(
        http,
        game_dir,
        &format!("assets-{}", asset_ref.id),
        files,
        MAX_CONCURRENT,
        on_progress,
    )
    .await?;

    Ok(AssetSetup {
        assets_dir,
        asset_index: asset_ref.id.clone(),
    })
}
