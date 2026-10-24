//! Installation du Fabric Loader.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §9 (loader = Fabric côté client).
//!
//! Fabric publie via https://meta.fabricmc.net/v2 :
//! - la liste des versions du loader disponibles pour une version MC ;
//! - un « profile json » qui hérite de la version vanilla et apporte sa propre
//!   `mainClass` + des libraries supplémentaires.
//!
//! Pour le launch on a besoin de :
//! - la mainClass Fabric (override de la mainClass vanilla) ;
//! - les libraries Fabric ajoutées au classpath APRÈS les libraries vanilla
//!   et le client.jar.
//!
//! On garde toujours le dernier loader **stable** publié, mais le profil est
//! mis en cache (`versions/<mc>/fabric-profile.json`) : si Fabric Meta est
//! injoignable, on relance avec le profil connu au lieu d'échouer.

use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use tokio::fs;

use super::mojang::{
    describe_http_error, ensure_file_set, MojangError, RemoteFile, METADATA_TIMEOUT,
};

const META_BASE: &str = "https://meta.fabricmc.net/v2";
const MAX_CONCURRENT_DOWNLOADS: usize = 4;

#[derive(Debug, thiserror::Error)]
pub enum FabricError {
    #[error(
        "Service Fabric (meta.fabricmc.net) injoignable et aucun profil en cache : {}",
        describe_http_error(.0)
    )]
    Http(#[from] reqwest::Error),
    #[error(transparent)]
    Files(#[from] MojangError),
    #[error(
        "Aucune version stable de Fabric Loader n'est publiée pour Minecraft {0} — vérifie la \
         version configurée ou réessaie plus tard."
    )]
    NoStableLoader(String),
    #[error("Profil Fabric illisible ({0}) — réessaie plus tard ou mets à jour le launcher.")]
    Schema(String),
    #[error("Nom Maven invalide dans le profil Fabric : « {0} ».")]
    InvalidMaven(String),
}

impl FabricError {
    /// Vrai si la cause est réseau (cf [`MojangError::is_network`]).
    pub(crate) fn is_network(&self) -> bool {
        match self {
            Self::Http(_) => true,
            Self::Files(e) => e.is_network(),
            _ => false,
        }
    }
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
    /// Optionnel : les profils récents incluent hash + taille, les vieux non.
    #[serde(default)]
    sha1: Option<String>,
    #[serde(default)]
    size: Option<u64>,
}

/// Profil Fabric tel qu'enregistré dans le cache local.
#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CachedProfile {
    loader_version: String,
    profile: serde_json::Value,
}

#[derive(Debug, Clone)]
pub struct FabricSetup {
    pub loader_version: String,
    pub main_class: String,
    /// Libraries Fabric à ajouter au classpath. Ordre conservé.
    pub library_jars: Vec<PathBuf>,
}

/// Récupère la dernière version *stable* du Fabric Loader pour la version
/// Minecraft donnée + vérifie ses libraries dans `<game_dir>/libraries/...`
/// (la même racine que les libs vanilla — chemins Maven partagés).
pub async fn ensure_fabric(
    http: &reqwest::Client,
    game_dir: &Path,
    minecraft_version: &str,
) -> Result<FabricSetup, FabricError> {
    let cache_path = game_dir
        .join("versions")
        .join(minecraft_version)
        .join("fabric-profile.json");

    let (loader_version, profile) = match fetch_latest_profile(http, minecraft_version).await {
        Ok((loader_version, raw, profile)) => {
            save_cached_profile(&cache_path, &loader_version, raw).await;
            (loader_version, profile)
        }
        // Panne réseau ou réponse illisible : le dernier profil connu suffit.
        Err(e @ (FabricError::Http(_) | FabricError::Schema(_))) => {
            match load_cached_profile(&cache_path).await {
                Some((loader_version, profile)) => {
                    let cause = match &e {
                        FabricError::Http(inner) => describe_http_error(inner),
                        other => other.to_string(),
                    };
                    tracing::warn!(
                        "Fabric Meta indisponible ({cause}) — profil en cache utilisé (loader {loader_version})"
                    );
                    (loader_version, profile)
                }
                None => return Err(e),
            }
        }
        Err(e) => return Err(e),
    };

    let libraries_root = game_dir.join("libraries");
    let mut files = Vec::with_capacity(profile.libraries.len());
    let mut classpath = Vec::with_capacity(profile.libraries.len());
    for lib in &profile.libraries {
        let maven_rel = maven_relative_path(&lib.name)?;
        let dest = libraries_root.join(&maven_rel);
        // Pas de hash publié (vieux profils) : téléchargé si absent, sans
        // vérification — documenté comme acceptable côté launcher
        // (https://fabricmc.net/wiki/install_with_launcher).
        files.push(RemoteFile {
            url: build_url(&lib.url, &maven_rel),
            sha1: lib.sha1.clone(),
            size: lib.size,
            dest: dest.clone(),
        });
        classpath.push(dest);
    }

    ensure_file_set(
        http,
        game_dir,
        &format!("fabric-{loader_version}-{minecraft_version}"),
        files,
        MAX_CONCURRENT_DOWNLOADS,
        |_, _| {},
    )
    .await?;

    Ok(FabricSetup {
        loader_version,
        main_class: profile.main_class,
        library_jars: classpath,
    })
}

/// (version du loader, profil brut pour le cache, profil décodé).
async fn fetch_latest_profile(
    http: &reqwest::Client,
    minecraft_version: &str,
) -> Result<(String, serde_json::Value, FabricProfile), FabricError> {
    let loader_version = latest_stable_loader(http, minecraft_version).await?;
    let url =
        format!("{META_BASE}/versions/loader/{minecraft_version}/{loader_version}/profile/json");
    let raw: serde_json::Value = http
        .get(&url)
        .timeout(METADATA_TIMEOUT)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    let profile = parse_profile(&raw)?;
    Ok((loader_version, raw, profile))
}

async fn latest_stable_loader(
    http: &reqwest::Client,
    minecraft_version: &str,
) -> Result<String, FabricError> {
    let url = format!("{META_BASE}/versions/loader/{minecraft_version}");
    let versions: Vec<LoaderVersion> = http
        .get(&url)
        .timeout(METADATA_TIMEOUT)
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

fn parse_profile(raw: &serde_json::Value) -> Result<FabricProfile, FabricError> {
    FabricProfile::deserialize(raw).map_err(|e| FabricError::Schema(e.to_string()))
}

async fn load_cached_profile(path: &Path) -> Option<(String, FabricProfile)> {
    let bytes = fs::read(path).await.ok()?;
    let cached: CachedProfile = serde_json::from_slice(&bytes).ok()?;
    let profile = parse_profile(&cached.profile).ok()?;
    Some((cached.loader_version, profile))
}

/// Best-effort : sans cache, on perd seulement le repli hors ligne.
async fn save_cached_profile(path: &Path, loader_version: &str, profile: serde_json::Value) {
    let cached = CachedProfile {
        loader_version: loader_version.to_string(),
        profile,
    };
    let result = async {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).await?;
        }
        let bytes = serde_json::to_vec(&cached).map_err(std::io::Error::other)?;
        fs::write(path, bytes).await
    }
    .await;
    if let Err(e) = result {
        tracing::warn!("profil Fabric non mis en cache ({}) : {e}", path.display());
    }
}

/// `group:artifact:version[:classifier]` → chemin Maven relatif.
/// Ex : `com.mojang:authlib:6.0.54` → `com/mojang/authlib/6.0.54/authlib-6.0.54.jar`.
fn maven_relative_path(name: &str) -> Result<PathBuf, FabricError> {
    let parts: Vec<&str> = name.split(':').collect();
    let (group, artifact, version, classifier) = match parts.as_slice() {
        [g, a, v] => (*g, *a, *v, None),
        [g, a, v, c] => (*g, *a, *v, Some(*c)),
        _ => return Err(FabricError::InvalidMaven(name.into())),
    };
    if [group, artifact, version].iter().any(|s| s.is_empty()) {
        return Err(FabricError::InvalidMaven(name.into()));
    }

    let mut path = PathBuf::new();
    for seg in group.split('.') {
        path.push(seg);
    }
    path.push(artifact);
    path.push(version);
    path.push(match classifier {
        Some(c) => format!("{artifact}-{version}-{c}.jar"),
        None => format!("{artifact}-{version}.jar"),
    });
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
    fn maven_path_translates_dotted_group() {
        let p = maven_relative_path("com.mojang:authlib:6.0.54").unwrap();
        let s = p.to_string_lossy().replace('\\', "/");
        assert_eq!(s, "com/mojang/authlib/6.0.54/authlib-6.0.54.jar");
    }

    #[test]
    fn maven_path_with_classifier() {
        let p = maven_relative_path("org.lwjgl:lwjgl:3.3.3:natives-windows").unwrap();
        let s = p.to_string_lossy().replace('\\', "/");
        assert_eq!(s, "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar");
    }

    #[test]
    fn maven_path_rejects_garbage() {
        assert!(maven_relative_path("pas-un-nom-maven").is_err());
        assert!(maven_relative_path("a::1").is_err());
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

    #[test]
    fn parses_profile_with_optional_hashes() {
        let raw = serde_json::json!({
            "id": "fabric-loader-0.19.3-26.2",
            "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "libraries": [
                {"name": "org.ow2.asm:asm:9.10.1", "url": "https://maven.fabricmc.net/",
                 "sha1": "ada2141c0cc52ee8f5c48cd5fa4ce0e794f22236", "size": 126151},
                {"name": "net.fabricmc:intermediary:26.2", "url": "https://maven.fabricmc.net/"}
            ]
        });
        let profile = parse_profile(&raw).unwrap();
        assert_eq!(profile.libraries.len(), 2);
        assert_eq!(profile.libraries[0].size, Some(126151));
        assert!(profile.libraries[1].sha1.is_none());
    }
}
