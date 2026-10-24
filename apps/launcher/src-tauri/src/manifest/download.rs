//! Diff manifest <-> filesystem local + téléchargement parallèle.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §8.1, étapes [3] et [4].
//!
//! Les fichiers du manifest Reborn (mods…) sont **toujours** re-hachés en
//! SHA-256 au calcul du plan : c'est la surface anti-triche du modpack, on
//! ne s'y contente pas d'un témoin taille/date comme pour les fichiers Mojang.
//! Le hachage est en revanche parallélisé et fait hors du runtime async.

use futures::stream::{self, StreamExt};
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tauri::{AppHandle, Emitter, Runtime};
use tokio::fs;
use tokio::io::AsyncWriteExt;

use super::{ManifestFile, SignedManifest};
use crate::launcher::mojang::{describe_http_error, describe_io_error, short_hash};

/// Concurrence maximale (cf §8.1 étape [4]).
const MAX_CONCURRENT_DOWNLOADS: usize = 4;
/// Fichiers hachés en parallèle au calcul du plan (CPU + disque).
const MAX_CONCURRENT_HASHES: usize = 4;
/// Taille des blocs lus pendant le hachage.
const HASH_CHUNK: usize = 256 * 1024;
/// Tampon d'écriture des téléchargements.
const WRITE_BUFFER: usize = 64 * 1024;
/// Tentatives par fichier (erreurs réseau transitoires et disque uniquement).
const DOWNLOAD_ATTEMPTS: u32 = 4;
/// Cadence des événements de progression intermédiaires (octets reçus).
const PROGRESS_TICK: Duration = Duration::from_millis(250);

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

#[derive(Debug, thiserror::Error)]
pub enum DownloadError {
    #[error(
        "Téléchargement de « {file} » impossible{} : {}",
        attempts_note(.attempts),
        describe_http_error(.source)
    )]
    Http {
        file: String,
        attempts: u32,
        source: reqwest::Error,
    },
    #[error("{}", describe_io_error(.path, .source))]
    Io {
        path: PathBuf,
        source: std::io::Error,
    },
    #[error(
        "Fichier « {path} » corrompu après téléchargement : SHA-256 attendu {}…, obtenu {}…. Un \
         antivirus ou un proxy altère peut-être les téléchargements — réessaie ; si ça persiste, \
         préviens le staff (le manifest publié est peut-être incohérent).",
        short_hash(.expected),
        short_hash(.actual)
    )]
    HashMismatch {
        path: String,
        expected: String,
        actual: String,
    },
    #[error("Tâche de téléchargement interrompue ({0}) — relance la mise à jour.")]
    Internal(String),
}

fn attempts_note(attempts: &u32) -> String {
    if *attempts > 1 {
        format!(" après {attempts} tentatives")
    } else {
        String::new()
    }
}

fn io_at(path: &Path) -> impl FnOnce(std::io::Error) -> DownloadError + '_ {
    move |source| DownloadError::Io {
        path: path.to_path_buf(),
        source,
    }
}

/// Calcule le diff entre le manifest et les fichiers locaux.
/// Retourne uniquement les entrées à (re-)télécharger, dans l'ordre du manifest.
///
/// Respecte le flag `required` + les préférences user :
///   - required:true  -> toujours dans le plan si missing/mismatch
///   - required:false :
///       - prefs[filename] = true  -> traité comme required
///       - sinon                   -> skip (mod optionnel non activé)
pub async fn compute_plan(
    manifest: &SignedManifest,
    game_dir: &Path,
    prefs: &crate::storage::mod_prefs::ModPrefs,
) -> Result<Vec<PlanItem>, DownloadError> {
    // Données possédées (pas de références dans le stream) : le futur de la
    // commande Tauri doit rester `Send` sans contraintes de durée de vie.
    let active: Vec<ManifestFile> = manifest
        .files
        .iter()
        .filter(|f| is_file_active(f, prefs))
        .cloned()
        .collect();
    let checks: Vec<Result<Option<PlanItem>, DownloadError>> = stream::iter(active)
        .map(|file| {
            let local = game_dir.join(&file.path);
            async move {
                let reason = check_one(local, &file.sha256).await?;
                Ok(reason.map(|reason| PlanItem { file, reason }))
            }
        })
        .buffered(MAX_CONCURRENT_HASHES)
        .collect()
        .await;

    let mut plan = Vec::new();
    for check in checks {
        if let Some(item) = check? {
            plan.push(item);
        }
    }
    Ok(plan)
}

/// True si le fichier doit être présent côté launcher : required ou
/// (optionnel + prefs enabled). Centralisé pour partage entre compute_plan
/// et purge_orphan_mods.
fn is_file_active(file: &ManifestFile, prefs: &crate::storage::mod_prefs::ModPrefs) -> bool {
    if file.required {
        return true;
    }
    let name = Path::new(&file.path)
        .file_name()
        .and_then(|s| s.to_str())
        .unwrap_or("");
    prefs.is_enabled(name)
}

async fn check_one(
    path: PathBuf,
    expected_sha256: &str,
) -> Result<Option<PlanReason>, DownloadError> {
    let reason = match sha256_file(&path).await? {
        None => Some(PlanReason::Missing),
        Some(actual) if actual.eq_ignore_ascii_case(expected_sha256) => None,
        Some(_) => Some(PlanReason::HashMismatch),
    };
    Ok(reason)
}

/// SHA-256 d'un fichier, lu par blocs sur le pool bloquant (un seul
/// aller-retour, pas un par bloc ; jamais 200 Mo en RAM). `None` si absent.
async fn sha256_file(path: &Path) -> Result<Option<String>, DownloadError> {
    let owned = path.to_path_buf();
    tokio::task::spawn_blocking(move || -> std::io::Result<Option<String>> {
        let mut file = match std::fs::File::open(&owned) {
            Ok(f) => f,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            Err(e) => return Err(e),
        };
        let mut hasher = Sha256::new();
        let mut buf = vec![0u8; HASH_CHUNK];
        loop {
            let n = file.read(&mut buf)?;
            if n == 0 {
                break;
            }
            hasher.update(&buf[..n]);
        }
        Ok(Some(hex::encode(hasher.finalize())))
    })
    .await
    .map_err(|e| DownloadError::Internal(e.to_string()))?
    .map_err(io_at(path))
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

/// Télécharge tous les items du plan en parallèle (max 4 simultanés).
/// Vérifie le hash après chaque fichier et abandonne en cas de mismatch.
///
/// Émet `manifest:progress` à chaque fichier terminé ET toutes les 250 ms
/// pendant les transferts (octets reçus, fichiers en cours compris) : sur un
/// mod de 50 Mo, la barre avance au lieu de rester figée.
pub async fn download_plan<R: Runtime>(
    app: &AppHandle<R>,
    http: &reqwest::Client,
    plan: Vec<PlanItem>,
    game_dir: &Path,
) -> Result<(), DownloadError> {
    let total = plan.len();
    let bytes_total: u64 = plan.iter().map(|p| p.file.size).sum();
    let received = Arc::new(AtomicU64::new(0));

    let mut results = stream::iter(plan)
        .map(|item| {
            let http = http.clone();
            let game_dir = game_dir.to_path_buf();
            let received = received.clone();
            tokio::spawn(async move { download_one(&http, &item, &game_dir, &received).await })
        })
        .buffer_unordered(MAX_CONCURRENT_DOWNLOADS);

    let mut completed = 0usize;
    let mut current_file: Option<String> = None;
    let mut last_emitted = u64::MAX;
    let mut ticker = tokio::time::interval(PROGRESS_TICK);
    ticker.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);

    loop {
        tokio::select! {
            next = results.next() => match next {
                None => break,
                Some(Ok(Ok(path))) => {
                    completed += 1;
                    current_file = Some(path);
                }
                Some(Ok(Err(e))) => return Err(e),
                Some(Err(join_err)) => return Err(DownloadError::Internal(join_err.to_string())),
            },
            _ = ticker.tick() => {
                // Pas d'événement si rien n'a bougé depuis le dernier tick.
                if received.load(Ordering::Relaxed) == last_emitted {
                    continue;
                }
            }
        }
        last_emitted = received.load(Ordering::Relaxed);
        emit_progress(
            app,
            DownloadProgress {
                completed,
                total,
                bytes_downloaded: if completed == total {
                    bytes_total
                } else {
                    last_emitted.min(bytes_total)
                },
                bytes_total,
                current_file: current_file.clone(),
            },
        );
    }

    Ok(())
}

fn emit_progress<R: Runtime>(app: &AppHandle<R>, p: DownloadProgress) {
    let _ = app.emit("manifest:progress", p);
}

/// Retry transparent (4 tentatives, backoff 0,5 s / 1 s / 2 s) sur les
/// erreurs réseau transitoires — même pattern que `mojang::download_file`.
/// Indispensable quand l'AV du user (typiquement McAfee qui scanne les .jar
/// comme des archives Java) coupe un stream au milieu : sans retry, tout le
/// batch s'arrête sur un seul chunk interrompu.
///
/// Hash mismatch -> pas un blip réseau, on abandonne tout de suite (et on
/// remonte bien CE mismatch, avec attendu/obtenu).
///
/// Retourne le chemin relatif du fichier terminé.
async fn download_one(
    http: &reqwest::Client,
    item: &PlanItem,
    game_dir: &Path,
    received: &AtomicU64,
) -> Result<String, DownloadError> {
    let mut attempt = 1u32;
    loop {
        let mut counted = 0u64;
        match try_download_once(http, item, game_dir, received, &mut counted).await {
            Ok(()) => return Ok(item.file.path.clone()),
            Err(e) => {
                // Les octets de la tentative ratée ne comptent plus.
                received.fetch_sub(counted, Ordering::Relaxed);
                if attempt < DOWNLOAD_ATTEMPTS && is_retryable(&e) {
                    tracing::warn!(
                        path = %item.file.path,
                        "téléchargement : tentative {attempt}/{DOWNLOAD_ATTEMPTS} échouée ({e}), nouvel essai"
                    );
                    tokio::time::sleep(Duration::from_millis(500 * 2u64.pow(attempt - 1))).await;
                    attempt += 1;
                    continue;
                }
                return Err(match e {
                    DownloadError::Http { file, source, .. } => DownloadError::Http {
                        file,
                        attempts: attempt,
                        source,
                    },
                    other => other,
                });
            }
        }
    }
}

async fn try_download_once(
    http: &reqwest::Client,
    item: &PlanItem,
    game_dir: &Path,
    received: &AtomicU64,
    counted: &mut u64,
) -> Result<(), DownloadError> {
    let dest: PathBuf = game_dir.join(&item.file.path);
    if let Some(parent) = dest.parent() {
        fs::create_dir_all(parent).await.map_err(io_at(parent))?;
    }
    let http_err = |source: reqwest::Error| DownloadError::Http {
        file: item.file.path.clone(),
        attempts: 1,
        source,
    };

    // On stream la réponse en hachant à la volée, vers un fichier temporaire,
    // puis on renomme atomiquement si le hash correspond.
    let tmp = dest.with_extension(format!(
        "{}.part",
        dest.extension().and_then(|s| s.to_str()).unwrap_or("dl")
    ));
    let mut response = http
        .get(&item.file.url)
        .send()
        .await
        .and_then(|r| r.error_for_status())
        .map_err(http_err)?;

    let result = async {
        let file = fs::File::create(&tmp).await.map_err(io_at(&tmp))?;
        let mut writer = tokio::io::BufWriter::with_capacity(WRITE_BUFFER, file);
        let mut hasher = Sha256::new();
        while let Some(chunk) = response.chunk().await.map_err(http_err)? {
            hasher.update(&chunk);
            writer.write_all(&chunk).await.map_err(io_at(&tmp))?;
            let len = chunk.len() as u64;
            *counted += len;
            received.fetch_add(len, Ordering::Relaxed);
        }
        writer.flush().await.map_err(io_at(&tmp))?;
        drop(writer);

        let actual = hex::encode(hasher.finalize());
        if !actual.eq_ignore_ascii_case(&item.file.sha256) {
            return Err(DownloadError::HashMismatch {
                path: item.file.path.clone(),
                expected: item.file.sha256.clone(),
                actual,
            });
        }
        fs::rename(&tmp, &dest).await.map_err(io_at(&dest))
    }
    .await;
    if result.is_err() {
        let _ = fs::remove_file(&tmp).await;
    }
    result
}

fn is_retryable(err: &DownloadError) -> bool {
    match err {
        DownloadError::Http { source: e, .. } => {
            e.is_timeout()
                || e.is_connect()
                || e.is_body()
                || e.is_decode()
                || e.is_request()
                || e.status().map(|s| s.is_server_error()).unwrap_or(true)
        }
        DownloadError::Io { .. } => true,
        _ => false,
    }
}

/// Supprime **tout** .jar de `mods/` qui n'appartient pas à l'ensemble actif
/// du manifest courant. Le modpack Reborn est entièrement piloté par le
/// manifest (PLAN §9.1 : « Aucun mod ajouté manuellement n'est toléré ») : on
/// veut donc `mods/` == exactement l'ensemble actif du manifest, ni plus ni
/// moins.
///
/// Couvre en un seul mécanisme : anciennes versions d'un mod (bump), mods
/// retirés du manifest entre deux versions, et optionnels désactivés.
///
/// Ne touche QUE les `.jar` de `mods/` : configs, resourcepacks, shaderpacks
/// et sous-dossiers sont préservés. Retourne les noms de fichiers supprimés.
pub async fn purge_orphan_mods(
    manifest: &SignedManifest,
    game_dir: &Path,
    prefs: &crate::storage::mod_prefs::ModPrefs,
) -> Result<Vec<String>, DownloadError> {
    let mods_dir = game_dir.join("mods");

    // Ensemble des filenames « actifs » du manifest dont le parent est `mods/`.
    // Un optionnel non activé n'est PAS dans `expected` -> purgé si présent.
    let expected: std::collections::HashSet<String> = manifest
        .files
        .iter()
        .filter(|f| is_file_active(f, prefs))
        .filter_map(|f| {
            let p = Path::new(&f.path);
            (p.parent().map(|x| x.to_string_lossy().replace('\\', "/")) == Some("mods".into()))
                .then(|| p.file_name()?.to_str().map(|s| s.to_string()))
                .flatten()
        })
        .collect();

    let mut entries = match fs::read_dir(&mods_dir).await {
        Ok(entries) => entries,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(Vec::new()),
        Err(e) => return Err(io_at(&mods_dir)(e)),
    };
    let mut removed = Vec::new();
    while let Some(entry) = entries.next_entry().await.map_err(io_at(&mods_dir))? {
        let path = entry.path();
        let Some(name) = path.file_name().and_then(|n| n.to_str()) else {
            continue;
        };
        if !name.ends_with(".jar") || expected.contains(name) {
            continue;
        }
        // Strict : tout .jar hors de l'ensemble actif du manifest est retiré.
        match fs::remove_file(&path).await {
            Ok(()) => removed.push(name.to_string()),
            Err(e) => tracing::warn!(
                "mod orphelin non supprimé : {}",
                describe_io_error(&path, &e)
            ),
        }
    }
    Ok(removed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hash_mismatch_message_names_file_and_hashes() {
        let err = DownloadError::HashMismatch {
            path: "mods/reborn-hud.jar".into(),
            expected: "aa".into(),
            actual: "bb".into(),
        };
        let msg = err.to_string();
        assert!(msg.contains("mods/reborn-hud.jar") && msg.contains("aa") && msg.contains("bb"));
    }

    #[test]
    fn io_message_carries_path() {
        let err = io_at(Path::new("C:/jeu/mods/x.jar"))(std::io::Error::from(
            std::io::ErrorKind::NotFound,
        ));
        assert!(err.to_string().contains("C:/jeu/mods/x.jar"));
    }

    #[tokio::test]
    async fn sha256_file_streams_and_reports_missing() {
        let dir = std::env::temp_dir().join(format!("reborn-dl-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let f = dir.join("a.bin");
        std::fs::write(&f, b"abc").unwrap();
        assert_eq!(
            sha256_file(&f).await.unwrap().as_deref(),
            Some("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        );
        assert!(sha256_file(&dir.join("absent")).await.unwrap().is_none());
        let _ = std::fs::remove_dir_all(&dir);
    }
}
