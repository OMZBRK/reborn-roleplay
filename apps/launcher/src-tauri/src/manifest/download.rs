//! Diff manifest <-> filesystem local + telechargement parallele.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §8.1, etapes [3] et [4].

use futures::stream::{FuturesUnordered, StreamExt};
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use tauri::{AppHandle, Emitter, Runtime};
use tokio::fs;
use tokio::io::AsyncWriteExt;
use tokio::sync::Semaphore;

use super::{ManifestFile, SignedManifest};

/// Concurrence maximale (cf §8.1 etape [4]).
const MAX_CONCURRENT_DOWNLOADS: usize = 4;
/// Buffer pour la lecture/ecriture pendant le streaming.
const STREAM_CHUNK: usize = 64 * 1024;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlanItem {
    pub file: ManifestFile,
    pub reason: PlanReason,
}

#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum PlanReason {
    Missing,
    HashMismatch,
}

/// Calcule le diff entre le manifest et les fichiers locaux.
/// Retourne uniquement les entrees a (re-)telecharger.
///
/// Respecte le flag `required` + les preferences user :
///   - required:true  -> toujours dans le plan si missing/mismatch
///   - required:false :
///       - prefs[filename] = true  -> traite comme required
///       - sinon                   -> skip (mod optionnel non active)
pub async fn compute_plan(
    manifest: &SignedManifest,
    game_dir: &Path,
    prefs: &crate::storage::mod_prefs::ModPrefs,
) -> std::io::Result<Vec<PlanItem>> {
    let mut plan = Vec::new();
    for file in &manifest.files {
        if !is_file_active(file, prefs) {
            continue;
        }
        let local = game_dir.join(&file.path);
        let reason = check_one(&local, &file.sha256).await?;
        if let Some(reason) = reason {
            plan.push(PlanItem {
                file: file.clone(),
                reason,
            });
        }
    }
    Ok(plan)
}

/// True si le fichier doit etre present cote launcher : required ou
/// (optionnel + prefs enabled). Centralise pour partage entre compute_plan
/// et purge_orphan_mods.
fn is_file_active(file: &super::ManifestFile, prefs: &crate::storage::mod_prefs::ModPrefs) -> bool {
    if file.required {
        return true;
    }
    let name = std::path::Path::new(&file.path)
        .file_name()
        .and_then(|s| s.to_str())
        .unwrap_or("");
    prefs.is_enabled(name)
}

async fn check_one(path: &Path, expected_sha256: &str) -> std::io::Result<Option<PlanReason>> {
    if !fs::try_exists(path).await? {
        return Ok(Some(PlanReason::Missing));
    }
    let computed = sha256_file(path).await?;
    if computed.eq_ignore_ascii_case(expected_sha256) {
        Ok(None)
    } else {
        Ok(Some(PlanReason::HashMismatch))
    }
}

/// SHA-256 streaming d'un fichier (pour ne pas charger 200 Mo en RAM).
async fn sha256_file(path: &Path) -> std::io::Result<String> {
    let mut file = fs::File::open(path).await?;
    let mut hasher = Sha256::new();
    let mut buf = vec![0u8; STREAM_CHUNK];
    loop {
        let n = tokio::io::AsyncReadExt::read(&mut file, &mut buf).await?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DownloadProgress {
    pub completed: usize,
    pub total: usize,
    pub bytes_downloaded: u64,
    pub bytes_total: u64,
    pub current_file: Option<String>,
}

/// Telecharge tous les items du plan en parallele (max 4 simultanes).
/// Verifie le hash apres chaque DL et abandonne en cas de mismatch.
/// Emet un event Tauri "manifest:progress" apres chaque fichier termine.
pub async fn download_plan<R: Runtime>(
    app: &AppHandle<R>,
    http: &reqwest::Client,
    plan: Vec<PlanItem>,
    game_dir: &Path,
) -> Result<(), DownloadError> {
    let total = plan.len();
    let bytes_total: u64 = plan.iter().map(|p| p.file.size).sum();
    let semaphore = Arc::new(Semaphore::new(MAX_CONCURRENT_DOWNLOADS));

    let mut completed = 0usize;
    let mut bytes_downloaded = 0u64;

    let mut tasks = FuturesUnordered::new();
    for item in plan {
        let permit = semaphore.clone().acquire_owned().await.unwrap();
        let http = http.clone();
        let game_dir = game_dir.to_path_buf();
        tasks.push(tokio::spawn(async move {
            let _permit = permit; // libere a la fin du Future
            download_one(&http, &item, &game_dir).await
        }));
    }

    while let Some(joined) = tasks.next().await {
        match joined {
            Ok(Ok(done)) => {
                completed += 1;
                bytes_downloaded += done.size;
                emit_progress(
                    app,
                    DownloadProgress {
                        completed,
                        total,
                        bytes_downloaded,
                        bytes_total,
                        current_file: Some(done.path),
                    },
                );
            }
            Ok(Err(e)) => return Err(e),
            Err(join_err) => {
                return Err(DownloadError::Internal(format!(
                    "task join : {join_err}"
                )))
            }
        }
    }

    Ok(())
}

fn emit_progress<R: Runtime>(app: &AppHandle<R>, p: DownloadProgress) {
    let _ = app.emit("manifest:progress", p);
}

#[derive(Debug, thiserror::Error)]
pub enum DownloadError {
    #[error("reseau : {0}")]
    Http(#[from] reqwest::Error),
    #[error("io : {0}")]
    Io(#[from] std::io::Error),
    #[error("hash mismatch pour {path} : attendu {expected}, calcule {actual}")]
    HashMismatch {
        path: String,
        expected: String,
        actual: String,
    },
    #[error("interne : {0}")]
    Internal(String),
}

struct DownloadDone {
    path: String,
    size: u64,
}

/// Retry transparent jusqu'a 4 tentatives avec backoff exponentiel
/// (500ms / 1s / 2s / 4s) sur les erreurs reseau transientes — meme pattern
/// que `mojang::download_with_sha1`. Indispensable quand l'AV du user
/// (typiquement McAfee qui scanne les .jar comme des archives Java) coupe
/// un stream au milieu : sans retry, tout le batch de 158 Mo s'arrete sur
/// un seul chunk corrompu.
///
/// Hash mismatch -> pas un blip reseau, on abandonne tout de suite.
async fn download_one(
    http: &reqwest::Client,
    item: &PlanItem,
    game_dir: &Path,
) -> Result<DownloadDone, DownloadError> {
    let mut last_err: Option<DownloadError> = None;
    for attempt in 0..4u32 {
        if attempt > 0 {
            let delay_ms = 500u64 * 2u64.pow(attempt - 1);
            tokio::time::sleep(std::time::Duration::from_millis(delay_ms)).await;
            if let Some(e) = &last_err {
                tracing::warn!(
                    path = %item.file.path,
                    "retry download (tentative {}/4) apres : {e}",
                    attempt + 1,
                );
            }
        }
        match try_download_once(http, item, game_dir).await {
            Ok(done) => return Ok(done),
            Err(DownloadError::HashMismatch { .. }) => {
                // Corruption reelle -> ne pas retry, le serveur servirait le
                // meme contenu.
                return Err(last_err.unwrap_or_else(|| {
                    DownloadError::Internal("hash mismatch".into())
                }));
            }
            Err(e) if is_retryable(&e) => {
                last_err = Some(e);
            }
            Err(e) => return Err(e),
        }
    }
    Err(last_err.unwrap_or_else(|| DownloadError::Internal("retry epuise".into())))
}

async fn try_download_once(
    http: &reqwest::Client,
    item: &PlanItem,
    game_dir: &Path,
) -> Result<DownloadDone, DownloadError> {
    let dest: PathBuf = game_dir.join(&item.file.path);
    if let Some(parent) = dest.parent() {
        fs::create_dir_all(parent).await?;
    }

    // On stream la reponse en hashant a la volee, vers un fichier temporaire,
    // puis on rename atomiquement si le hash matche.
    let tmp = dest.with_extension(format!(
        "{}.part",
        dest.extension().and_then(|s| s.to_str()).unwrap_or("dl")
    ));
    let mut response = http.get(&item.file.url).send().await?.error_for_status()?;
    let mut writer = fs::File::create(&tmp).await?;
    let mut hasher = Sha256::new();
    let mut total_size = 0u64;

    while let Some(chunk) = response.chunk().await? {
        hasher.update(&chunk);
        writer.write_all(&chunk).await?;
        total_size += chunk.len() as u64;
    }
    writer.flush().await?;
    drop(writer);

    let actual = hex::encode(hasher.finalize());
    if !actual.eq_ignore_ascii_case(&item.file.sha256) {
        let _ = fs::remove_file(&tmp).await;
        return Err(DownloadError::HashMismatch {
            path: item.file.path.clone(),
            expected: item.file.sha256.clone(),
            actual,
        });
    }

    fs::rename(&tmp, &dest).await?;
    Ok(DownloadDone {
        path: item.file.path.clone(),
        size: total_size,
    })
}

fn is_retryable(err: &DownloadError) -> bool {
    match err {
        DownloadError::Http(e) => {
            e.is_timeout()
                || e.is_connect()
                || e.is_body()
                || e.is_decode()
                || e.is_request()
                || e.status().map(|s| s.is_server_error()).unwrap_or(true)
        }
        DownloadError::Io(_) => true,
        _ => false,
    }
}

/// Prefixes des jars geres par notre manifest. Tout .jar dans `mods/`
/// dont le nom commence par un de ces prefixes ET qui n'est plus liste
/// dans le manifest courant sera supprime par `purge_orphan_mods`.
///
/// On EVITE volontairement de purger les autres jars (sodium, lithium,
/// iris, etc.) meme s'ils ne sont pas dans le manifest : ce sont des
/// mods optionnels que l'utilisateur peut avoir installes a la main et
/// les effacer sans warning = data loss + jeu casse. La PLAN §9.1 dit
/// "Aucun mod ajoute manuellement n'est tolere" mais l'enforcement doit
/// se faire cote serveur (manifest_hash check au JOIN, plus tard).
const MANAGED_PREFIXES: &[&str] = &["reborn-", "mcef-", "mcef_", "fabric-api-"];

/// Supprime les .jar **geres** dans `mods/` (cf [`MANAGED_PREFIXES`])
/// qui ne sont PAS listes dans le manifest courant. Necessaire pour le
/// passage d'une version de manifest a la suivante : sinon les anciennes
/// versions cohabitent avec les nouvelles (typiquement
/// reborn-integrity-0.1.0-dev.jar ET reborn-integrity-0.2.0-dev.jar
/// chargees simultanement par Fabric -> mod ID collision).
///
/// **Garde-fou anti data-loss** : on ne touche qu'aux jars dont le nom
/// matche un prefix Reborn-managed. Les mods optionnels installes par
/// l'utilisateur (sodium, lithium, iris, ...) sont preserves. Cf PR
/// "v0.3.5 too aggressive purge".
pub async fn purge_orphan_mods(
    manifest: &SignedManifest,
    game_dir: &Path,
    prefs: &crate::storage::mod_prefs::ModPrefs,
) -> std::io::Result<Vec<String>> {
    let mods_dir = game_dir.join("mods");
    if !fs::try_exists(&mods_dir).await? {
        return Ok(Vec::new());
    }

    // Collecte les chemins manifest "actifs" dont le parent est `mods/`.
    // Un optionnel non-enabled n'est PAS dans "expected" -> sera purge
    // s'il est present (effet du toggle off : retire le jar de mods/).
    let expected: std::collections::HashSet<String> = manifest
        .files
        .iter()
        .filter(|f| is_file_active(f, prefs))
        .filter_map(|f| {
            let p = std::path::Path::new(&f.path);
            (p.parent().map(|x| x.to_string_lossy().replace('\\', "/")) == Some("mods".into()))
                .then(|| p.file_name()?.to_str().map(|s| s.to_string()))
                .flatten()
        })
        .collect();

    // Set des filenames du manifest (toutes versions confondues, sans tenir
    // compte du flag active) -> permet de detecter les jars "manages" qui
    // ont change de version ou ont ete toggle off. Les jars hors-manifest
    // qui ne matchent pas un MANAGED_PREFIXES static restent preserves.
    let in_manifest_any: std::collections::HashSet<String> = manifest
        .files
        .iter()
        .filter_map(|f| {
            std::path::Path::new(&f.path)
                .file_name()
                .and_then(|n| n.to_str())
                .map(|s| s.to_string())
        })
        .collect();

    let mut removed = Vec::new();
    let mut entries = fs::read_dir(&mods_dir).await?;
    while let Some(entry) = entries.next_entry().await? {
        let path = entry.path();
        let Some(name) = path.file_name().and_then(|n| n.to_str()) else {
            continue;
        };
        if !name.ends_with(".jar") {
            continue;
        }
        // Garde-fou anti data-loss : un jar est candidat a la purge si
        //   (a) il est dans le manifest courant (toutes versions, meme
        //       les optionnels desactives -> purge sur toggle off), OU
        //   (b) son nom commence par un MANAGED_PREFIXES static (catch
        //       les anciennes versions retirees du manifest entre 2 bumps,
        //       p.ex. reborn-integrity-0.1.0-dev.jar laisse apres bump
        //       0.2.0).
        // Tout autre jar (mods user manuels : sodium-extra, etc.) est
        // preserve sans question.
        let is_managed = in_manifest_any.contains(name)
            || MANAGED_PREFIXES.iter().any(|p| name.starts_with(p));
        if !is_managed {
            continue;
        }
        if expected.contains(name) {
            continue;
        }
        match fs::remove_file(&path).await {
            Ok(()) => removed.push(name.to_string()),
            Err(e) => {
                tracing::warn!(?path, error = ?e, "purge_orphan_mods: remove failed");
            }
        }
    }
    Ok(removed)
}
