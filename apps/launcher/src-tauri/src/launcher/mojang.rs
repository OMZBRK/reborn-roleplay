//! Acces aux metadonnees Mojang (version_manifest_v2.json + version json).
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §8.1 etape [4]/[5].
//!
//! On met en cache la "version json" telechargee sous
//! `versions/<id>/<id>.json` pour eviter de re-DL a chaque lancement.

use serde::Deserialize;
use sha1::{Digest, Sha1};
use std::path::{Path, PathBuf};
use tokio::fs;
use tokio::io::AsyncWriteExt;

const VERSION_MANIFEST_V2: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Debug, thiserror::Error)]
pub enum MojangError {
    #[error("reseau : {0}")]
    Http(#[from] reqwest::Error),
    #[error("io : {0}")]
    Io(#[from] std::io::Error),
    #[error("version Minecraft inconnue : {0}")]
    UnknownVersion(String),
    #[error("hash sha1 invalide pour {what} : attendu {expected}, calcule {actual}")]
    HashMismatch {
        what: String,
        expected: String,
        actual: String,
    },
    #[error("schema piston-meta inattendu : {0}")]
    Schema(String),
}

#[derive(Debug, Deserialize)]
struct VersionManifestV2 {
    versions: Vec<ManifestEntry>,
}

#[derive(Debug, Deserialize)]
struct ManifestEntry {
    id: String,
    url: String,
    sha1: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct VersionJson {
    pub id: String,
    #[serde(rename = "mainClass")]
    pub main_class: String,
    pub libraries: Vec<Library>,
    pub downloads: VersionDownloads,
    #[serde(rename = "assetIndex")]
    pub asset_index: AssetIndexRef,
    pub assets: String,
    #[serde(rename = "javaVersion", default)]
    pub java_version: Option<JavaVersion>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct VersionDownloads {
    pub client: VersionDownload,
}

#[derive(Debug, Deserialize, Clone)]
pub struct VersionDownload {
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct AssetIndexRef {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    #[serde(rename = "totalSize")]
    pub total_size: u64,
    pub url: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct JavaVersion {
    pub component: String,
    #[serde(rename = "majorVersion")]
    pub major_version: u32,
}

#[derive(Debug, Deserialize, Clone)]
pub struct Library {
    pub name: String,
    #[serde(default)]
    pub downloads: Option<LibraryDownloads>,
    #[serde(default)]
    pub rules: Vec<LibraryRule>,
    #[serde(default)]
    pub natives: std::collections::HashMap<String, String>,
    #[serde(default)]
    pub extract: Option<LibraryExtract>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct LibraryDownloads {
    pub artifact: Option<LibraryArtifact>,
    #[serde(default)]
    pub classifiers: std::collections::HashMap<String, LibraryArtifact>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct LibraryArtifact {
    pub path: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct LibraryRule {
    pub action: String, // "allow" / "disallow"
    #[serde(default)]
    pub os: Option<RuleOs>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct RuleOs {
    pub name: Option<String>,
    pub version: Option<String>,
    pub arch: Option<String>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct LibraryExtract {
    #[serde(default)]
    pub exclude: Vec<String>,
}

/// Recupere (ou lit en cache) le JSON d'une version Minecraft donnee.
///
/// Cache : `<game_dir>/versions/<id>/<id>.json`. On verifie le SHA-1 contre
/// le manifest racine pour s'assurer qu'on n'a pas un cache obsolete.
pub async fn fetch_version_json(
    http: &reqwest::Client,
    game_dir: &Path,
    version_id: &str,
) -> Result<VersionJson, MojangError> {
    let cache_path = game_dir
        .join("versions")
        .join(version_id)
        .join(format!("{version_id}.json"));

    // 1. Recupere le manifest racine pour avoir l'URL + sha1 attendus.
    let entry = fetch_manifest_entry(http, version_id).await?;

    // 2. Si on a un cache et que son sha1 matche, on le lit.
    if let Ok(bytes) = fs::read(&cache_path).await {
        let actual = hex_sha1(&bytes);
        if actual.eq_ignore_ascii_case(&entry.sha1) {
            return parse_version_json(&bytes);
        }
    }

    // 3. Sinon DL + verif + cache.
    if let Some(parent) = cache_path.parent() {
        fs::create_dir_all(parent).await?;
    }

    let bytes = http.get(&entry.url).send().await?.error_for_status()?.bytes().await?;
    let actual = hex_sha1(&bytes);
    if !actual.eq_ignore_ascii_case(&entry.sha1) {
        return Err(MojangError::HashMismatch {
            what: format!("{version_id}.json"),
            expected: entry.sha1.clone(),
            actual,
        });
    }

    let mut writer = fs::File::create(&cache_path).await?;
    writer.write_all(&bytes).await?;
    writer.flush().await?;

    parse_version_json(&bytes)
}

async fn fetch_manifest_entry(
    http: &reqwest::Client,
    version_id: &str,
) -> Result<ManifestEntry, MojangError> {
    let manifest: VersionManifestV2 = http
        .get(VERSION_MANIFEST_V2)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    manifest
        .versions
        .into_iter()
        .find(|e| e.id == version_id)
        .ok_or_else(|| MojangError::UnknownVersion(version_id.to_string()))
}

fn parse_version_json(bytes: &[u8]) -> Result<VersionJson, MojangError> {
    serde_json::from_slice::<VersionJson>(bytes).map_err(|e| MojangError::Schema(e.to_string()))
}

fn hex_sha1(bytes: &[u8]) -> String {
    hex::encode(Sha1::digest(bytes))
}

/// Telecharge un fichier en streaming SHA-1, cf le pattern reutilise dans
/// le launcher. Retourne `Ok(())` si le hash matche.
pub async fn download_with_sha1(
    http: &reqwest::Client,
    url: &str,
    expected_sha1: &str,
    dest: &Path,
) -> Result<(), MojangError> {
    if let Some(parent) = dest.parent() {
        fs::create_dir_all(parent).await?;
    }
    if fs::try_exists(dest).await? {
        let bytes = fs::read(dest).await?;
        if hex_sha1(&bytes).eq_ignore_ascii_case(expected_sha1) {
            return Ok(());
        }
    }

    let mut response = http.get(url).send().await?.error_for_status()?;
    let mut hasher = Sha1::new();
    let tmp = dest.with_extension(format!(
        "{}.part",
        dest.extension().and_then(|s| s.to_str()).unwrap_or("dl")
    ));
    let mut writer = fs::File::create(&tmp).await?;

    while let Some(chunk) = response.chunk().await? {
        hasher.update(&chunk);
        writer.write_all(&chunk).await?;
    }
    writer.flush().await?;
    drop(writer);

    let actual = hex::encode(hasher.finalize());
    if !actual.eq_ignore_ascii_case(expected_sha1) {
        let _ = fs::remove_file(&tmp).await;
        return Err(MojangError::HashMismatch {
            what: dest.display().to_string(),
            expected: expected_sha1.to_string(),
            actual,
        });
    }

    fs::rename(&tmp, dest).await?;
    Ok(())
}

/// Path de cache pour un fichier de la lib Maven `name` (ex `com.mojang:authlib:6.0.54`).
/// Format Mojang : `libraries/com/mojang/authlib/6.0.54/authlib-6.0.54.jar`.
#[allow(dead_code)]
pub fn maven_path(library_name: &str) -> Option<PathBuf> {
    let mut parts = library_name.splitn(3, ':');
    let group = parts.next()?;
    let artifact = parts.next()?;
    let version = parts.next()?;
    let mut p = PathBuf::new();
    for seg in group.split('.') {
        p.push(seg);
    }
    p.push(artifact);
    p.push(version);
    p.push(format!("{artifact}-{version}.jar"));
    Some(p)
}

impl Library {
    /// Decide si la library s'applique a la plateforme courante (rules
    /// allow/disallow Mojang).
    pub fn applies_to_current_os(&self) -> bool {
        if self.rules.is_empty() {
            return true;
        }
        let mut allowed = false;
        for rule in &self.rules {
            let matches = rule
                .os
                .as_ref()
                .map(matches_current_os)
                .unwrap_or(true);
            match (rule.action.as_str(), matches) {
                ("allow", true) => allowed = true,
                ("allow", false) => {}
                ("disallow", true) => return false,
                ("disallow", false) => {}
                _ => {}
            }
            if rule.action == "allow" && rule.os.is_none() {
                allowed = true;
            }
        }
        allowed
    }

    /// Si la library a une entree natives pour cet OS, retourne le
    /// classifier correspondant (ex "natives-windows").
    pub fn current_native_classifier(&self) -> Option<String> {
        let os = current_os_key();
        self.natives.get(os).cloned().map(|c| {
            // Mojang utilise parfois ${arch} -> 64
            c.replace("${arch}", "64")
        })
    }
}

fn matches_current_os(os: &RuleOs) -> bool {
    if let Some(ref name) = os.name {
        if name != current_os_key() {
            return false;
        }
    }
    if let Some(ref arch) = os.arch {
        let cur = std::env::consts::ARCH;
        let normalized = match cur {
            "x86_64" => "x86",
            "aarch64" => "arm64",
            _ => cur,
        };
        if arch != cur && arch != normalized {
            return false;
        }
    }
    true
}

fn current_os_key() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maven_path_translates_dotted_group() {
        let p = maven_path("com.mojang:authlib:6.0.54").unwrap();
        let s = p.to_string_lossy().replace('\\', "/");
        assert_eq!(s, "com/mojang/authlib/6.0.54/authlib-6.0.54.jar");
    }
}
