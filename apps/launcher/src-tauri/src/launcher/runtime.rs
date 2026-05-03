//! Telechargement du JRE Mojang via piston-meta.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §8.1 etape [5].
//!
//! Mojang publie un manifest "all.json" qui pour chaque plateforme + chaque
//! variante de runtime (jre-legacy, java-runtime-gamma, java-runtime-delta…)
//! pointe vers un manifest secondaire decrivant chaque fichier (path, sha1,
//! url, taille, executable). On telecharge tout, on verifie chaque sha1, on
//! pose dans `runtime/<component>/`.

use futures::stream::{FuturesUnordered, StreamExt};
use serde::Deserialize;
use sha1::{Digest, Sha1};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use tokio::fs;
use tokio::io::AsyncWriteExt;
use tokio::sync::Semaphore;

const ALL_JSON_URL: &str =
    "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0b0db5d4cd96e34c7baea8bc1aa5dccc6bbe5/all.json";

/// Composant JRE recommande pour Minecraft 1.21+ (Java 21).
pub const RUNTIME_COMPONENT: &str = "java-runtime-delta";

const MAX_CONCURRENT_FILE_DOWNLOADS: usize = 6;

#[derive(Debug, thiserror::Error)]
pub enum RuntimeError {
    #[error("plateforme non supportee : {0}")]
    Platform(&'static str),
    #[error("composant introuvable dans piston-meta : {0}")]
    Component(String),
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
    #[error("manifest piston-meta inattendu : {0}")]
    Schema(String),
}

#[derive(Debug, Deserialize)]
struct AllJsonResponse(std::collections::HashMap<String, serde_json::Value>);

#[derive(Debug, Deserialize)]
struct ComponentEntry {
    manifest: ManifestRef,
    #[allow(dead_code)]
    version: ComponentVersion,
}

#[derive(Debug, Deserialize)]
struct ManifestRef {
    sha1: String,
    url: String,
    #[allow(dead_code)]
    size: u64,
}

#[derive(Debug, Deserialize)]
struct ComponentVersion {
    #[allow(dead_code)]
    name: String,
}

#[derive(Debug, Deserialize)]
struct RuntimeManifest {
    files: std::collections::HashMap<String, RuntimeEntry>,
}

#[derive(Debug, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
enum RuntimeEntry {
    File(RuntimeFile),
    Directory,
    Link {
        #[allow(dead_code)]
        target: String,
    },
}

#[derive(Debug, Deserialize)]
struct RuntimeFile {
    downloads: RuntimeDownloads,
    #[serde(default)]
    executable: bool,
}

#[derive(Debug, Deserialize)]
struct RuntimeDownloads {
    raw: RuntimeRawDownload,
    // Le champ "lzma" existe aussi mais on prend la version raw : plus simple,
    // les fichiers JRE sont deja relativement compresses.
}

#[derive(Debug, Deserialize)]
struct RuntimeRawDownload {
    sha1: String,
    url: String,
    #[allow(dead_code)]
    size: u64,
}

/// Resout la cle plateforme attendue par le manifest piston-meta.
pub fn current_platform_key() -> Result<&'static str, RuntimeError> {
    #[cfg(all(target_os = "windows", target_arch = "x86_64"))]
    {
        Ok("windows-x64")
    }
    #[cfg(all(target_os = "windows", target_arch = "aarch64"))]
    {
        Ok("windows-arm64")
    }
    #[cfg(all(target_os = "macos", target_arch = "aarch64"))]
    {
        Ok("mac-os-arm64")
    }
    #[cfg(all(target_os = "macos", target_arch = "x86_64"))]
    {
        Ok("mac-os")
    }
    #[cfg(all(target_os = "linux", target_arch = "x86_64"))]
    {
        Ok("linux")
    }
    #[cfg(all(target_os = "linux", target_arch = "x86"))]
    {
        Ok("linux-i386")
    }
    #[cfg(not(any(
        all(target_os = "windows", any(target_arch = "x86_64", target_arch = "aarch64")),
        all(target_os = "macos", any(target_arch = "aarch64", target_arch = "x86_64")),
        all(target_os = "linux", any(target_arch = "x86_64", target_arch = "x86")),
    )))]
    {
        Err(RuntimeError::Platform(std::env::consts::OS))
    }
}

/// Verifie la presence du JRE et le telecharge sinon. Retourne le chemin
/// vers l'executable `java`.
pub async fn ensure_runtime(
    http: &reqwest::Client,
    game_dir: &Path,
) -> Result<PathBuf, RuntimeError> {
    let runtime_root = game_dir.join("runtime").join(RUNTIME_COMPONENT);
    let java_path = runtime_executable(&runtime_root);

    if fs::try_exists(&java_path).await? {
        return Ok(java_path);
    }

    tracing::info!("JRE absent — telechargement de {RUNTIME_COMPONENT}");
    fs::create_dir_all(&runtime_root).await?;

    let manifest = fetch_runtime_manifest(http).await?;
    download_runtime_files(http, manifest, &runtime_root).await?;

    if !fs::try_exists(&java_path).await? {
        return Err(RuntimeError::Schema(format!(
            "java introuvable apres install : {}",
            java_path.display()
        )));
    }
    Ok(java_path)
}

fn runtime_executable(runtime_root: &Path) -> PathBuf {
    if cfg!(target_os = "windows") {
        runtime_root.join("bin").join("javaw.exe")
    } else if cfg!(target_os = "macos") {
        runtime_root
            .join("jre.bundle")
            .join("Contents")
            .join("Home")
            .join("bin")
            .join("java")
    } else {
        runtime_root.join("bin").join("java")
    }
}

async fn fetch_runtime_manifest(http: &reqwest::Client) -> Result<RuntimeManifest, RuntimeError> {
    let all_json: AllJsonResponse = http.get(ALL_JSON_URL).send().await?.error_for_status()?.json().await?;
    let platform = current_platform_key()?;

    let platform_value = all_json
        .0
        .get(platform)
        .ok_or_else(|| RuntimeError::Component(format!("plateforme {platform} absente")))?;

    let component_value = platform_value
        .get(RUNTIME_COMPONENT)
        .ok_or_else(|| RuntimeError::Component(RUNTIME_COMPONENT.into()))?;

    // C'est un tableau (Mojang met plusieurs candidats parfois) — on prend le premier.
    let entries: Vec<ComponentEntry> = serde_json::from_value(component_value.clone())
        .map_err(|e| RuntimeError::Schema(e.to_string()))?;

    let entry = entries
        .into_iter()
        .next()
        .ok_or_else(|| RuntimeError::Component(format!("aucune entree pour {RUNTIME_COMPONENT}")))?;

    tracing::info!(
        "manifest piston-meta : {} ({})",
        entry.manifest.url,
        &entry.manifest.sha1[..8]
    );

    let manifest: RuntimeManifest = http
        .get(&entry.manifest.url)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    Ok(manifest)
}

async fn download_runtime_files(
    http: &reqwest::Client,
    manifest: RuntimeManifest,
    runtime_root: &Path,
) -> Result<(), RuntimeError> {
    let semaphore = Arc::new(Semaphore::new(MAX_CONCURRENT_FILE_DOWNLOADS));
    let mut tasks = FuturesUnordered::new();

    for (rel_path, entry) in manifest.files {
        let dest = runtime_root.join(&rel_path);

        match entry {
            RuntimeEntry::Directory => {
                fs::create_dir_all(&dest).await?;
            }
            RuntimeEntry::Link { .. } => {
                // Ignore : on ne reproduit pas les symlinks Unix sur Windows.
                // Les symlinks rencontres sont typiquement des "java -> bin/java"
                // qui sont redondants pour notre usage launcher.
                continue;
            }
            RuntimeEntry::File(file) => {
                if let Some(parent) = dest.parent() {
                    fs::create_dir_all(parent).await?;
                }
                let permit = semaphore.clone().acquire_owned().await.unwrap();
                let http = http.clone();
                tasks.push(tokio::spawn(async move {
                    let _permit = permit;
                    download_runtime_file(&http, &rel_path, file, &dest).await
                }));
            }
        }
    }

    while let Some(joined) = tasks.next().await {
        joined
            .map_err(|e| RuntimeError::Schema(format!("task : {e}")))?
            .map_err(|e| e)?;
    }
    Ok(())
}

async fn download_runtime_file(
    http: &reqwest::Client,
    rel_path: &str,
    file: RuntimeFile,
    dest: &Path,
) -> Result<(), RuntimeError> {
    let mut response = http.get(&file.downloads.raw.url).send().await?.error_for_status()?;
    let mut hasher = Sha1::new();
    let mut writer = fs::File::create(dest).await?;

    while let Some(chunk) = response.chunk().await? {
        hasher.update(&chunk);
        writer.write_all(&chunk).await?;
    }
    writer.flush().await?;
    drop(writer);

    let actual = hex::encode(hasher.finalize());
    if !actual.eq_ignore_ascii_case(&file.downloads.raw.sha1) {
        let _ = fs::remove_file(dest).await;
        return Err(RuntimeError::HashMismatch {
            path: rel_path.to_string(),
            expected: file.downloads.raw.sha1,
            actual,
        });
    }

    #[cfg(unix)]
    if file.executable {
        use std::os::unix::fs::PermissionsExt;
        let mut perms = fs::metadata(dest).await?.permissions();
        perms.set_mode(perms.mode() | 0o111);
        fs::set_permissions(dest, perms).await?;
    }
    #[cfg(not(unix))]
    let _ = file.executable; // Sur Windows, le bit executable n'a pas d'effet.

    Ok(())
}
