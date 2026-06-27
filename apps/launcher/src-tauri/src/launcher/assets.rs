//! Telechargement de l'asset index + des asset objects.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §8.1 etape [4].
//!
//! Les "assets" Minecraft (textures, sons, langues) sont stockes par
//! Mojang dans un CAS hash-adresse :
//!
//! ```text
//! assets/indexes/<asset_index_id>.json
//! assets/objects/<aa>/<aabbccdd...>            ← fichier nomme par son sha1
//! ```
//!
//! L'asset index liste tous les hashs + tailles ; on telecharge en
//! parallele avec verification.

use futures::stream::{FuturesUnordered, StreamExt};
use serde::Deserialize;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use tokio::fs;
use tokio::sync::Semaphore;

use super::mojang::{download_with_sha1, AssetIndexRef, MojangError};

// Plus on monte, plus le risque de saturation TCP/DNS Windows + de blocage par
// un AV (real-time scanning) augmente. 8 reste rapide tout en evitant les
// avalanches d'erreurs reseau qu'on voit sur des reseaux residentiels.
const MAX_CONCURRENT: usize = 8;
const RESOURCES_BASE: &str = "https://resources.download.minecraft.net";

#[derive(Debug, thiserror::Error)]
pub enum AssetsError {
    #[error("mojang : {0}")]
    Mojang(#[from] MojangError),
    #[error("io : {0}")]
    Io(#[from] std::io::Error),
    #[error("schema : {0}")]
    Schema(String),
    #[error("interne : {0}")]
    Internal(String),
}

#[derive(Debug, Deserialize)]
struct AssetIndexFile {
    objects: std::collections::HashMap<String, AssetObject>,
}

#[derive(Debug, Deserialize)]
struct AssetObject {
    hash: String,
    size: u64,
}

/// Result du download : chemin du dossier `assets/` (a passer en
/// `--assetsDir` au launch) + l'id de l'asset index (`--assetIndex`).
#[derive(Debug, Clone)]
pub struct AssetSetup {
    pub assets_dir: PathBuf,
    pub asset_index: String,
}

/// `on_progress(completed, total)` est appele au fil de l'eau (throttle a
/// ~1% pres) pendant le download des objects, pour que l'UI puisse afficher
/// une vraie barre de progression au lieu d'un "En jeu" fige (cf etape [4]
/// du launch, la plus longue au premier lancement : ~3900 petits fichiers).
pub async fn ensure_assets(
    http: &reqwest::Client,
    game_dir: &Path,
    asset_ref: &AssetIndexRef,
    on_progress: impl Fn(usize, usize),
) -> Result<AssetSetup, AssetsError> {
    let assets_dir = game_dir.join("assets");
    let indexes_dir = assets_dir.join("indexes");
    let objects_dir = assets_dir.join("objects");
    fs::create_dir_all(&indexes_dir).await?;
    fs::create_dir_all(&objects_dir).await?;

    // 1. Asset index JSON.
    let index_path = indexes_dir.join(format!("{}.json", asset_ref.id));
    download_with_sha1(http, &asset_ref.url, &asset_ref.sha1, &index_path).await?;

    let bytes = fs::read(&index_path).await?;
    let index: AssetIndexFile =
        serde_json::from_slice(&bytes).map_err(|e| AssetsError::Schema(e.to_string()))?;

    // 2. Objects en parallele.
    let semaphore = Arc::new(Semaphore::new(MAX_CONCURRENT));
    let mut tasks = FuturesUnordered::new();

    for object in index.objects.values() {
        let prefix = &object.hash[..2];
        let url = format!("{RESOURCES_BASE}/{prefix}/{}", object.hash);
        let dest = objects_dir.join(prefix).join(&object.hash);

        let permit = semaphore.clone().acquire_owned().await.unwrap();
        let http = http.clone();
        let sha1 = object.hash.clone();
        tasks.push(tokio::spawn(async move {
            let _permit = permit;
            download_with_sha1(&http, &url, &sha1, &dest).await
        }));
    }

    let mut completed = 0usize;
    let total = tasks.len();
    // Throttle des events : on en emet ~100 max sur tout le batch (1% de pas),
    // pour ne pas inonder l'IPC Tauri avec 3900 events.
    let emit_every = (total / 100).max(1);
    on_progress(0, total);
    while let Some(joined) = tasks.next().await {
        joined
            .map_err(|e| AssetsError::Internal(format!("task : {e}")))?
            .map_err(AssetsError::Mojang)?;
        completed += 1;
        if completed % emit_every == 0 || completed == total {
            on_progress(completed, total);
        }
        if completed % 200 == 0 {
            tracing::info!("assets : {completed}/{total}");
        }
    }
    tracing::info!("assets : {completed}/{total} (termine)");

    Ok(AssetSetup {
        assets_dir,
        asset_index: asset_ref.id.clone(),
    })
}
