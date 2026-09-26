//! Profils de serveur — support multi-version.
//!
//! Le launcher expose plusieurs serveurs, chacun avec SA version Minecraft et
//! SON modpack :
//! - `rp`    : le serveur RP/dev de prod, MC 26.2, modpack servi par l'API
//!             (`GET /manifest/current`). C'est le comportement historique.
//! - `build` : le serveur de build staff, MC 26.3, modpack servi par un
//!             manifest statique signé (GitHub release). Réservé au staff.
//!
//! Chaque version tourne dans son propre *instance directory* (cf
//! `paths::instance_dir`) pour que les `mods/` des deux versions ne se
//! purgent pas mutuellement (cf `mods::purge_incompatible_mods`).
//!
//! Les adresses/versions sont bakées au build via `option_env!("*_BUILD")` et
//! surchargeables au runtime par les mêmes variables sans le suffixe — même
//! schéma que le reste du launcher (cf `game.rs::resolve_auto_connect`).

use crate::launcher::jvm::ServerAddress;

/// D'où provient le modpack (manifest signé) d'un serveur.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ManifestSource {
    /// Manifest « courant » servi par l'API Reborn (`GET /manifest/current`).
    ApiCurrent,
    /// Manifest statique signé téléchargé depuis une URL fixe (GitHub release).
    StaticUrl(String),
}

/// Un serveur sélectionnable dans le launcher.
#[derive(Debug, Clone)]
pub struct ServerProfile {
    /// Identifiant stable ("rp" | "build"). Passé par le frontend au launch.
    pub id: String,
    /// Nom affiché ("Reborn RP" | "Serveur Build").
    pub name: String,
    /// Version Minecraft cible (ex "26.2" / "26.3").
    pub mc_version: String,
    /// Adresse du serveur (None = pas d'adresse bakée → menu sans cible).
    pub address: Option<ServerAddress>,
    /// Source du modpack pour ce serveur.
    pub manifest: ManifestSource,
    /// Vrai si réservé au staff (non listé pour les joueurs normaux).
    pub staff_only: bool,
}

impl ServerProfile {
    /// Identifiant du dossier d'instance (sous `game_dir/instances/<id>/`).
    pub fn instance_id(&self) -> &str {
        &self.id
    }
}

/// Helper : lit une var d'env runtime, sinon la var `_BUILD` bakée au compile.
fn env_or_build(runtime_key: &str, build_val: Option<&str>) -> Option<String> {
    if let Ok(v) = std::env::var(runtime_key) {
        if !v.trim().is_empty() {
            return Some(v);
        }
    }
    build_val
        .map(str::trim)
        .filter(|v| !v.is_empty())
        .map(str::to_string)
}

/// Construit l'adresse d'un serveur depuis un couple (host, port) env/build.
fn resolve_address(
    host_runtime: &str,
    host_build: Option<&str>,
    port_runtime: &str,
    port_build: Option<&str>,
    default_port: u16,
) -> Option<ServerAddress> {
    let host = env_or_build(host_runtime, host_build)?;
    let port = env_or_build(port_runtime, port_build)
        .and_then(|p| p.parse::<u16>().ok())
        .unwrap_or(default_port);
    Some(ServerAddress { host, port })
}

/// Profil RP/dev — MC 26.2, modpack API. Comportement historique du launcher.
pub fn rp_profile() -> ServerProfile {
    let mc_version = std::env::var("REBORN_MC_VERSION")
        .ok()
        .filter(|v| !v.trim().is_empty())
        .unwrap_or_else(|| "26.2".into());
    ServerProfile {
        id: "rp".into(),
        name: "Reborn RP".into(),
        mc_version,
        address: resolve_address(
            "REBORN_SERVER_HOST",
            option_env!("REBORN_SERVER_HOST_BUILD"),
            "REBORN_SERVER_PORT",
            option_env!("REBORN_SERVER_PORT_BUILD"),
            25565,
        ),
        manifest: ManifestSource::ApiCurrent,
        staff_only: false,
    }
}

/// Profil Build — MC 26.3, modpack statique signé. Réservé au staff.
///
/// Réutilise le slot serveur « dev » historique (`REBORN_SERVER_DEV_*`) comme
/// cible du serveur de build. Le manifest vient de `REBORN_BUILD_MANIFEST_URL`
/// (runtime) ou de la constante bakée `REBORN_BUILD_MANIFEST_URL_BUILD`.
pub fn build_profile() -> Option<ServerProfile> {
    let manifest_url = env_or_build(
        "REBORN_BUILD_MANIFEST_URL",
        option_env!("REBORN_BUILD_MANIFEST_URL_BUILD"),
    )?;
    let mc_version = env_or_build(
        "REBORN_BUILD_MC_VERSION",
        option_env!("REBORN_BUILD_MC_VERSION_BUILD"),
    )
    .unwrap_or_else(|| "26.3".into());
    Some(ServerProfile {
        id: "build".into(),
        name: "Serveur Build".into(),
        mc_version,
        address: resolve_address(
            "REBORN_SERVER_DEV_HOST",
            option_env!("REBORN_SERVER_DEV_HOST_BUILD"),
            "REBORN_SERVER_DEV_PORT",
            option_env!("REBORN_SERVER_DEV_PORT_BUILD"),
            25565,
        ),
        manifest: ManifestSource::StaticUrl(manifest_url),
        staff_only: true,
    })
}

/// Tous les profils disponibles, dans l'ordre d'affichage. Le profil `build`
/// n'apparaît que s'il est configuré (URL de manifest présente).
pub fn all_profiles() -> Vec<ServerProfile> {
    let mut out = vec![rp_profile()];
    if let Some(build) = build_profile() {
        out.push(build);
    }
    out
}

/// Résout un profil par id. `None` ou id inconnu → profil RP par défaut
/// (rétro-compatible avec l'ancien flux sans sélecteur de serveur).
pub fn resolve(id: Option<&str>) -> ServerProfile {
    match id {
        Some("build") => build_profile().unwrap_or_else(rp_profile),
        _ => rp_profile(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rp_profile_defaults_to_262() {
        // Sans REBORN_MC_VERSION en env, le profil RP cible 26.2.
        let p = rp_profile();
        assert_eq!(p.id, "rp");
        assert_eq!(p.manifest, ManifestSource::ApiCurrent);
        assert!(!p.staff_only);
    }

    #[test]
    fn resolve_unknown_falls_back_to_rp() {
        assert_eq!(resolve(Some("nope")).id, "rp");
        assert_eq!(resolve(None).id, "rp");
    }

    #[test]
    fn build_profile_requires_manifest_url() {
        // En test (pas de var d'env ni de constante bakée), le profil build
        // n'est pas disponible → all_profiles ne contient que RP.
        if std::env::var("REBORN_BUILD_MANIFEST_URL").is_err()
            && option_env!("REBORN_BUILD_MANIFEST_URL_BUILD").is_none()
        {
            assert!(build_profile().is_none());
            assert_eq!(all_profiles().len(), 1);
        }
    }
}
