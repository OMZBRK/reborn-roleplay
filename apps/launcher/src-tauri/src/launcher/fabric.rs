//! Installation du Fabric Loader.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §9 (loader = Fabric cote client).
//!
//! Fabric publie via https://meta.fabricmc.net/v2 :
//! - liste des loader versions disponibles pour une MC version
//! - "profile json" qui herite de la version vanilla et apporte sa propre
//!   `mainClass` + libraries supplementaires
//!
//! Pour le launch on a besoin de :
//! - la mainClass Fabric (override de la mainClass vanilla)
//! - les libraries Fabric ajoutees au classpath APRES les libraries vanilla
//!   et le client.jar

use serde::Deserialize;
use std::path::{Path, PathBuf};

use super::mojang::download_with_sha1;

const META_BASE: &str = "https://meta.fabricmc.net/v2";

#[derive(Debug, thiserror::Error)]
pub enum FabricError {
    #[error("reseau : {0}")]
    Http(#[from] reqwest::Error),
    #[error("io : {0}")]
    Io(#[from] std::io::Error),
    #[error("aucun loader stable pour {0}")]
    NoStableLoader(String),
    #[error("schema fabric inattendu : {0}")]
    Schema(String),
    #[error("nom maven invalide : {0}")]
    InvalidMaven(String),
    #[error("download : {0}")]
    Download(String),
}

#[derive(Debug, Deserialize)]
struct LoaderVersion {
    loader: LoaderInfo,
}

#[derive(Debug, Deserialize)]
struct LoaderInfo {
    version: String,
    stable: bool,
}

#[derive(Debug, Deserialize)]
struct FabricProfile {
    #[serde(rename = "mainClass")]
    main_class: String,
    libraries: Vec<FabricLibrary>,
}

#[derive(Debug, Deserialize)]
struct FabricLibrary {
    name: String,
    url: String,
    /// Optionnel : recents profils Fabric incluent les hashes ; les vieux non.
    #[serde(default)]
    sha1: Option<String>,
}

#[derive(Debug, Clone)]
pub struct FabricSetup {
    pub loader_version: String,
    pub main_class: String,
    /// Libraries Fabric a ajouter au classpath. Ordre conserve.
    pub library_jars: Vec<PathBuf>,
}

/// Recupere la derniere version *stable* du Fabric Loader pour la version
/// Minecraft donnee + telecharge ses libraries dans
/// `<game_dir>/libraries/...` (la meme racine que les libs vanilla — Maven
/// path partage).
pub async fn ensure_fabric(
    http: &reqwest::Client,
    game_dir: &Path,
    minecraft_version: &str,
) -> Result<FabricSetup, FabricError> {
    let loader_version = latest_stable_loader(http, minecraft_version).await?;
    let profile = fetch_profile(http, minecraft_version, &loader_version).await?;

    let libraries_root = game_dir.join("libraries");
    let mut classpath: Vec<PathBuf> = Vec::with_capacity(profile.libraries.len());

    for lib in &profile.libraries {
        let maven_rel = maven_relative_path(&lib.name)?;
        let dest = libraries_root.join(&maven_rel);
        let url = build_url(&lib.url, &maven_rel);

        if let Some(sha1) = &lib.sha1 {
            download_with_sha1(http, &url, sha1, &dest)
                .await
                .map_err(|e| FabricError::Download(e.to_string()))?;
        } else {
            // Pas de hash fourni : on telecharge si absent, sans verifier.
            // Mojang verifie tous les ses assets ; Fabric ne fournit pas
            // toujours le sha1, c'est documente comme acceptable cote
            // launcher (cf https://fabricmc.net/wiki/install_with_launcher).
            if !tokio::fs::try_exists(&dest).await? {
                if let Some(parent) = dest.parent() {
                    tokio::fs::create_dir_all(parent).await?;
                }
                let bytes = http.get(&url).send().await?.error_for_status()?.bytes().await?;
                tokio::fs::write(&dest, &bytes).await?;
            }
        }

        classpath.push(dest);
    }

    Ok(FabricSetup {
        loader_version,
        main_class: profile.main_class,
        library_jars: classpath,
    })
}

async fn latest_stable_loader(
    http: &reqwest::Client,
    minecraft_version: &str,
) -> Result<String, FabricError> {
    let url = format!("{META_BASE}/versions/loader/{minecraft_version}");
    let versions: Vec<LoaderVersion> = http
        .get(&url)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;

    versions
        .into_iter()
        .find(|v| v.loader.stable)
        .map(|v| v.loader.version)
        .ok_or_else(|| FabricError::NoStableLoader(minecraft_version.to_string()))
}

async fn fetch_profile(
    http: &reqwest::Client,
    minecraft_version: &str,
    loader_version: &str,
) -> Result<FabricProfile, FabricError> {
    let url =
        format!("{META_BASE}/versions/loader/{minecraft_version}/{loader_version}/profile/json");
    let profile: FabricProfile = http
        .get(&url)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await
        .map_err(|e| FabricError::Schema(e.to_string()))?;
    Ok(profile)
}

/// "com.mojang:authlib:6.0.54" -> "com/mojang/authlib/6.0.54/authlib-6.0.54.jar"
fn maven_relative_path(name: &str) -> Result<PathBuf, FabricError> {
    let mut parts = name.splitn(3, ':');
    let group = parts
        .next()
        .ok_or_else(|| FabricError::InvalidMaven(name.into()))?;
    let artifact = parts
        .next()
        .ok_or_else(|| FabricError::InvalidMaven(name.into()))?;
    let version = parts
        .next()
        .ok_or_else(|| FabricError::InvalidMaven(name.into()))?;

    let mut path = PathBuf::new();
    for seg in group.split('.') {
        path.push(seg);
    }
    path.push(artifact);
    path.push(version);
    path.push(format!("{artifact}-{version}.jar"));
    Ok(path)
}

fn build_url(repo_base: &str, maven_path: &Path) -> String {
    let trimmed = repo_base.trim_end_matches('/');
    let rel = maven_path.to_string_lossy().replace('\\', "/");
    format!("{trimmed}/{rel}")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maven_path_basic() {
        let p = maven_relative_path("net.fabricmc:fabric-loader:0.16.10").unwrap();
        let s = p.to_string_lossy().replace('\\', "/");
        assert_eq!(
            s,
            "net/fabricmc/fabric-loader/0.16.10/fabric-loader-0.16.10.jar"
        );
    }

    #[test]
    fn build_url_normalises_trailing_slash() {
        let p = std::path::PathBuf::from("a/b/c.jar");
        assert_eq!(
            build_url("https://maven.fabricmc.net/", &p),
            "https://maven.fabricmc.net/a/b/c.jar"
        );
        assert_eq!(
            build_url("https://maven.fabricmc.net", &p),
            "https://maven.fabricmc.net/a/b/c.jar"
        );
    }
}
