//! Telechargement du client jar + libraries vanilla + extraction des natives.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §8.1 etape [4].
//!
//! La structure cible reproduit le layout du launcher officiel :
//!
//! ```text
//! versions/<id>/<id>.jar          ← client jar
//! versions/<id>/natives/          ← .dll / .so / .dylib extraits
//! libraries/<group>/<artifact>/<version>/<artifact>-<version>.jar
//! ```

use futures::stream::{FuturesUnordered, StreamExt};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use tokio::fs;
use tokio::sync::Semaphore;

use super::mojang::{download_with_sha1, Library, MojangError, VersionJson};

const MAX_CONCURRENT_DOWNLOADS: usize = 8;

#[derive(Debug, thiserror::Error)]
pub enum LibrariesError {
    #[error("mojang : {0}")]
    Mojang(#[from] MojangError),
    #[error("io : {0}")]
    Io(#[from] std::io::Error),
    #[error("zip : {0}")]
    Zip(String),
    #[error("interne : {0}")]
    Internal(String),
}

/// Result du download : chemins absolus du client jar, des libs, et du
/// dossier natives extrait. Pret a etre passe a `LaunchConfig`.
#[derive(Debug, Clone)]
pub struct LibrarySetup {
    pub client_jar: PathBuf,
    pub library_jars: Vec<PathBuf>,
    pub natives_dir: PathBuf,
}

/// Etape principale : pour la version donnee, s'assure que le client jar +
/// toutes les libraries applicables a l'OS courant + natives sont locales,
/// retourne les chemins necessaires au build_command.
pub async fn ensure_libraries(
    http: &reqwest::Client,
    game_dir: &Path,
    version: &VersionJson,
) -> Result<LibrarySetup, LibrariesError> {
    // 1. Client jar
    let client_jar = game_dir
        .join("versions")
        .join(&version.id)
        .join(format!("{}.jar", &version.id));
    download_with_sha1(
        http,
        &version.downloads.client.url,
        &version.downloads.client.sha1,
        &client_jar,
    )
    .await?;

    // 2. Libraries — on filtre, on prepare la liste des taches DL.
    let libraries_root = game_dir.join("libraries");
    let natives_dir = game_dir
        .join("versions")
        .join(&version.id)
        .join("natives");
    fs::create_dir_all(&natives_dir).await?;

    let semaphore = Arc::new(Semaphore::new(MAX_CONCURRENT_DOWNLOADS));
    let mut tasks = FuturesUnordered::new();
    let mut classpath_jars: Vec<PathBuf> = Vec::new();
    let mut natives_jars: Vec<(PathBuf, Vec<String>)> = Vec::new();

    for library in &version.libraries {
        if !library.applies_to_current_os() {
            continue;
        }

        let downloads = match &library.downloads {
            Some(d) => d,
            None => continue,
        };

        // 2a. Artifact principal (pour le classpath).
        if let Some(artifact) = &downloads.artifact {
            let dest = libraries_root.join(&artifact.path);
            classpath_jars.push(dest.clone());
            let permit = semaphore.clone().acquire_owned().await.unwrap();
            let http_clone = http.clone();
            let url = artifact.url.clone();
            let sha1 = artifact.sha1.clone();
            tasks.push(tokio::spawn(async move {
                let _permit = permit;
                download_with_sha1(&http_clone, &url, &sha1, &dest).await
            }));
        }

        // 2b. Natives (s'il y en a pour cet OS).
        if let Some(classifier) = library.current_native_classifier() {
            if let Some(native_artifact) = downloads.classifiers.get(&classifier) {
                let dest = libraries_root.join(&native_artifact.path);
                let exclude = library
                    .extract
                    .as_ref()
                    .map(|e| e.exclude.clone())
                    .unwrap_or_default();
                natives_jars.push((dest.clone(), exclude));

                let permit = semaphore.clone().acquire_owned().await.unwrap();
                let http_clone = http.clone();
                let url = native_artifact.url.clone();
                let sha1 = native_artifact.sha1.clone();
                tasks.push(tokio::spawn(async move {
                    let _permit = permit;
                    download_with_sha1(&http_clone, &url, &sha1, &dest).await
                }));
            }
        }
    }

    // 3. Attendre les DL.
    while let Some(joined) = tasks.next().await {
        joined
            .map_err(|e| LibrariesError::Internal(format!("task : {e}")))?
            .map_err(|e| LibrariesError::Mojang(e))?;
    }

    // 4. Extraire les natives.
    for (jar_path, exclude) in &natives_jars {
        extract_natives(jar_path, &natives_dir, exclude).await?;
    }

    Ok(LibrarySetup {
        client_jar,
        library_jars: classpath_jars,
        natives_dir,
    })
}

/// Extrait les fichiers natives d'un jar dans `natives_dir`. Skip les
/// entrees qui matchent un prefix dans `exclude` (en general "META-INF/").
async fn extract_natives(
    jar_path: &Path,
    natives_dir: &Path,
    exclude: &[String],
) -> Result<(), LibrariesError> {
    let jar = jar_path.to_path_buf();
    let dest = natives_dir.to_path_buf();
    let exclude = exclude.to_vec();

    // L'extraction zip est synchrone — on l'isole sur un thread blocking.
    tokio::task::spawn_blocking(move || -> Result<(), LibrariesError> {
        let file = std::fs::File::open(&jar)
            .map_err(|e| LibrariesError::Io(e))?;
        let mut archive = zip::ZipArchive::new(file)
            .map_err(|e| LibrariesError::Zip(e.to_string()))?;

        for i in 0..archive.len() {
            let mut entry = archive
                .by_index(i)
                .map_err(|e| LibrariesError::Zip(e.to_string()))?;
            if entry.is_dir() {
                continue;
            }
            let name = entry.name().to_string();
            if exclude.iter().any(|p| name.starts_with(p)) {
                continue;
            }
            // Mojang met les natives a la racine du jar — on garde juste le
            // basename pour eviter les sous-dossiers.
            let basename = std::path::Path::new(&name)
                .file_name()
                .and_then(|s| s.to_str())
                .unwrap_or(&name);
            let out_path = dest.join(basename);
            if let Some(parent) = out_path.parent() {
                std::fs::create_dir_all(parent).map_err(LibrariesError::Io)?;
            }
            let mut out = std::fs::File::create(&out_path).map_err(LibrariesError::Io)?;
            std::io::copy(&mut entry, &mut out).map_err(LibrariesError::Io)?;
        }
        Ok(())
    })
    .await
    .map_err(|e| LibrariesError::Internal(format!("blocking task : {e}")))??;

    Ok(())
}

/// Helper utilise par les tests + le code de spawn pour formater le
/// classpath (`;` Windows, `:` Unix).
#[allow(dead_code)]
pub fn format_classpath(jars: &[PathBuf]) -> String {
    let sep = if cfg!(target_os = "windows") { ";" } else { ":" };
    jars.iter()
        .map(|p| p.display().to_string())
        .collect::<Vec<_>>()
        .join(sep)
}
