//! Téléchargement du JRE Mojang via piston-meta.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §8.1 étape [5] ; détails dans
//! docs/technique/LAUNCHER_JVM.md §3.
//!
//! Mojang publie un catalogue « all.json » qui, pour chaque plateforme et
//! chaque variante de runtime (jre-legacy, java-runtime-gamma, -delta…),
//! pointe vers un manifeste décrivant chaque fichier (chemin, sha1, url,
//! taille, exécutable). On vérifie tout, on pose dans `runtime/<component>/`.
//!
//! Après une vérification complète, le témoin `.reborn-verify/runtime-<component>.json`
//! garde la liste (chemin, taille) des fichiers : les lancements suivants se
//! contentent de `stat` et ne touchent plus au réseau.

use serde::Deserialize;
use sha1::{Digest, Sha1};
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use tokio::fs;

use super::mojang::{
    describe_http_error, download_all, files_present, io_at, read_stamp, write_stamp, MojangError,
    RemoteFile, Stamp, METADATA_TIMEOUT,
};

// Mojang fait tourner le hash de cette URL de temps en temps. Quand un
// 404 est observé ici, récupérer la nouvelle valeur depuis
// https://gist.github.com/skyrising/95a8e6a7287634e097ecafa2f21c240f
// (seuls les nouvelles installations et « Réparer » interrogent ce catalogue).
const ALL_JSON_URL: &str =
    "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

/// Composant JRE requis par la version cible. MC 26.x exige **Java 25**
/// (`java-runtime-epsilon`, cf `version.json` -> `javaVersion.component`).
/// MC 1.21.x utilisait `java-runtime-delta` (Java 21). À resynchroniser
/// (avec [`RUNTIME_MAJOR`]) si on rebumpe la version MC.
pub const RUNTIME_COMPONENT: &str = "java-runtime-epsilon";

/// Version majeure de Java fournie par [`RUNTIME_COMPONENT`]. Une version MC
/// qui exige plus est refusée avec un message clair (cf `mojang::check_java`).
pub const RUNTIME_MAJOR: u32 = 25;

const MAX_CONCURRENT_FILE_DOWNLOADS: usize = 6;

#[derive(Debug, thiserror::Error)]
pub enum RuntimeError {
    #[error(
        "Plateforme non prise en charge pour Java ({0}) : Reborn fournit Java pour Windows, \
         macOS et Linux."
    )]
    Platform(String),
    #[error(
        "Java « {component} » est absent du catalogue Mojang pour {platform} — mets à jour le \
         launcher."
    )]
    Component { component: String, platform: String },
    #[error("Catalogue Java de Mojang inaccessible : {}{}", describe_http_error(.0), catalog_hint(.0))]
    Catalog(#[from] reqwest::Error),
    #[error(transparent)]
    Files(#[from] MojangError),
    #[error("Catalogue Java de Mojang illisible : {0} — mets à jour le launcher.")]
    Schema(String),
    #[error(
        "Java est installé mais l'exécutable « {0} » est introuvable — ton antivirus l'a \
         peut-être mis en quarantaine ; lance « Réparer »."
    )]
    MissingExecutable(String),
}

/// Un 404 sur all.json signifie que Mojang a fait tourner l'URL du catalogue.
impl RuntimeError {
    /// Vrai si la cause est réseau (cf [`MojangError::is_network`]).
    pub(crate) fn is_network(&self) -> bool {
        match self {
            Self::Catalog(_) => true,
            Self::Files(e) => e.is_network(),
            _ => false,
        }
    }
}

fn catalog_hint(e: &reqwest::Error) -> &'static str {
    match e.status().map(|s| s.as_u16()) {
        Some(404) => " (l'adresse du catalogue a changé : mets à jour le launcher)",
        _ => "",
    }
}

#[derive(Debug, Deserialize)]
struct ComponentEntry {
    manifest: ManifestRef,
    version: ComponentVersion,
}

#[derive(Debug, Deserialize)]
struct ManifestRef {
    sha1: String,
    url: String,
}

#[derive(Debug, Deserialize)]
struct ComponentVersion {
    name: String,
}

#[derive(Debug, Deserialize)]
struct RuntimeManifest {
    files: HashMap<String, RuntimeEntry>,
}

#[derive(Debug, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
enum RuntimeEntry {
    File(RuntimeFile),
    Directory,
    /// Lien symbolique (`target` ignoré) : non reproduit, cf plus bas.
    Link,
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
    // les fichiers JRE sont déjà relativement compressés.
}

#[derive(Debug, Deserialize)]
struct RuntimeRawDownload {
    sha1: String,
    url: String,
    size: u64,
}

/// Clé plateforme attendue par le catalogue piston-meta.
fn current_platform_key() -> Option<&'static str> {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("windows", "x86_64") => Some("windows-x64"),
        ("windows", "aarch64") => Some("windows-arm64"),
        ("macos", "aarch64") => Some("mac-os-arm64"),
        ("macos", "x86_64") => Some("mac-os"),
        ("linux", "x86_64") => Some("linux"),
        ("linux", "x86") => Some("linux-i386"),
        _ => None,
    }
}

/// Vérifie la présence du JRE et le télécharge / répare sinon. Retourne le
/// chemin vers l'exécutable `java` (`javaw.exe` sous Windows : pas de console).
pub async fn ensure_runtime(
    http: &reqwest::Client,
    game_dir: &Path,
) -> Result<PathBuf, RuntimeError> {
    let runtime_root = game_dir.join("runtime").join(RUNTIME_COMPONENT);
    let java_path = runtime_executable(&runtime_root);
    let stamp_name = format!("runtime-{RUNTIME_COMPONENT}");

    // Chemin rapide : JRE vérifié intégralement lors d'un lancement précédent,
    // tous ses fichiers toujours là à la bonne taille → zéro réseau, zéro hash.
    if let Some(stamp) = read_stamp(game_dir, &stamp_name).await {
        if !stamp.files.is_empty() {
            let mut expected: Vec<(PathBuf, Option<u64>)> = stamp
                .files
                .iter()
                .map(|(rel, size)| (runtime_root.join(rel), Some(*size)))
                .collect();
            expected.push((java_path.clone(), None));
            if files_present(expected).await {
                tracing::info!(
                    "JRE {} ({RUNTIME_COMPONENT}) déjà vérifié (témoin à jour)",
                    stamp.label
                );
                return Ok(java_path);
            }
        }
    }

    tracing::info!("JRE : vérification complète de {RUNTIME_COMPONENT}");
    let platform = current_platform_key().ok_or_else(|| {
        RuntimeError::Platform(format!(
            "{} {}",
            std::env::consts::OS,
            std::env::consts::ARCH
        ))
    })?;
    let (version_name, manifest_sha1, manifest) = match fetch_runtime_manifest(http, platform).await
    {
        Ok(found) => found,
        // JRE déjà installé (install antérieure aux témoins, ou fichier
        // modifié) mais catalogue injoignable : on garde le JRE existant,
        // comme avant, plutôt que de bloquer le lancement. La vérification
        // complète sera retentée au prochain lancement.
        Err(e) if fs::try_exists(&java_path).await.unwrap_or(false) => {
            tracing::warn!("{e} — JRE existant utilisé sans vérification");
            return Ok(java_path);
        }
        Err(e) => return Err(e),
    };

    let mut files = Vec::new();
    let mut stamp_files = Vec::new();
    let mut executables = Vec::new();
    for (rel_path, entry) in manifest.files {
        let dest = runtime_root.join(&rel_path);
        match entry {
            RuntimeEntry::Directory => {
                fs::create_dir_all(&dest).await.map_err(io_at(&dest))?;
            }
            // Ignoré : on ne reproduit pas les symlinks Unix. Ceux rencontrés
            // sont typiquement des « java -> bin/java », redondants ici.
            RuntimeEntry::Link => {}
            RuntimeEntry::File(file) => {
                if file.executable {
                    executables.push(dest.clone());
                }
                stamp_files.push((rel_path, file.downloads.raw.size));
                files.push(RemoteFile {
                    url: file.downloads.raw.url,
                    sha1: Some(file.downloads.raw.sha1),
                    size: Some(file.downloads.raw.size),
                    dest,
                });
            }
        }
    }

    download_all(http, files, MAX_CONCURRENT_FILE_DOWNLOADS, &|_, _| {}).await?;
    mark_executables(&executables).await?;

    if !fs::try_exists(&java_path).await.unwrap_or(false) {
        return Err(RuntimeError::MissingExecutable(
            java_path.display().to_string(),
        ));
    }

    tracing::info!(
        "JRE {version_name} ({RUNTIME_COMPONENT}) vérifié : {} fichier(s)",
        stamp_files.len()
    );
    write_stamp(
        game_dir,
        &stamp_name,
        Stamp {
            fingerprint: manifest_sha1,
            count: stamp_files.len(),
            label: version_name,
            files: stamp_files,
            ..Stamp::default()
        },
    )
    .await;
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

/// Récupère le manifeste du composant pour la plateforme : (nom de version,
/// SHA-1 du manifeste, manifeste). Le manifeste est vérifié contre le SHA-1
/// annoncé par le catalogue.
async fn fetch_runtime_manifest(
    http: &reqwest::Client,
    platform: &str,
) -> Result<(String, String, RuntimeManifest), RuntimeError> {
    let all_json: HashMap<String, serde_json::Value> = http
        .get(ALL_JSON_URL)
        .timeout(METADATA_TIMEOUT)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;

    let missing = || RuntimeError::Component {
        component: RUNTIME_COMPONENT.to_string(),
        platform: platform.to_string(),
    };
    let component_value = all_json
        .get(platform)
        .and_then(|p| p.get(RUNTIME_COMPONENT))
        .ok_or_else(missing)?;

    // C'est un tableau (Mojang met parfois plusieurs candidats) — on prend le premier.
    let entries: Vec<ComponentEntry> = serde_json::from_value(component_value.clone())
        .map_err(|e| RuntimeError::Schema(e.to_string()))?;
    let entry = entries.into_iter().next().ok_or_else(missing)?;

    tracing::info!(
        "JRE {} : manifeste piston-meta {}",
        entry.version.name,
        entry.manifest.sha1.get(..8).unwrap_or(&entry.manifest.sha1)
    );

    let bytes = http
        .get(&entry.manifest.url)
        .timeout(METADATA_TIMEOUT)
        .send()
        .await?
        .error_for_status()?
        .bytes()
        .await?;
    let actual = hex::encode(Sha1::digest(&bytes));
    if !actual.eq_ignore_ascii_case(&entry.manifest.sha1) {
        return Err(MojangError::HashMismatch {
            what: format!("manifeste du JRE {}", entry.version.name),
            expected: entry.manifest.sha1,
            actual,
        }
        .into());
    }
    let manifest: RuntimeManifest =
        serde_json::from_slice(&bytes).map_err(|e| RuntimeError::Schema(e.to_string()))?;
    Ok((entry.version.name, entry.manifest.sha1, manifest))
}

/// Pose le bit exécutable (Unix) ; sans effet sous Windows.
#[cfg(unix)]
async fn mark_executables(paths: &[PathBuf]) -> Result<(), RuntimeError> {
    use std::os::unix::fs::PermissionsExt;
    for path in paths {
        let mut perms = fs::metadata(path).await.map_err(io_at(path))?.permissions();
        perms.set_mode(perms.mode() | 0o111);
        fs::set_permissions(path, perms)
            .await
            .map_err(io_at(path))?;
    }
    Ok(())
}

#[cfg(not(unix))]
async fn mark_executables(_paths: &[PathBuf]) -> Result<(), RuntimeError> {
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_runtime_manifest_with_links_and_dirs() {
        let json = r#"{"files":{
            "bin":{"type":"directory"},
            "bin/javaw.exe":{"type":"file","executable":true,"downloads":{
                "raw":{"sha1":"aa","size":12,"url":"https://piston-data.mojang.com/x"},
                "lzma":{"sha1":"bb","size":8,"url":"https://piston-data.mojang.com/y"}}},
            "legal/java.base/LICENSE":{"type":"link","target":"../java.desktop/LICENSE"}
        }}"#;
        let manifest: RuntimeManifest = serde_json::from_str(json).unwrap();
        assert_eq!(manifest.files.len(), 3);
        assert!(matches!(manifest.files["bin"], RuntimeEntry::Directory));
        assert!(matches!(
            manifest.files["legal/java.base/LICENSE"],
            RuntimeEntry::Link
        ));
        match &manifest.files["bin/javaw.exe"] {
            RuntimeEntry::File(f) => {
                assert!(f.executable);
                assert_eq!(f.downloads.raw.size, 12);
            }
            other => panic!("attendu un fichier, obtenu {other:?}"),
        }
    }

    #[test]
    fn platform_key_known_on_supported_hosts() {
        if cfg!(all(target_os = "windows", target_arch = "x86_64")) {
            assert_eq!(current_platform_key(), Some("windows-x64"));
        }
    }
}
