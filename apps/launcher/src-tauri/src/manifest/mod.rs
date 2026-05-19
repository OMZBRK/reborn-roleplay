//! Manifest signe = source de verite des fichiers de jeu.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §4.3, §8.1 (Triple validation).
//!
//! Le launcher ne fait JAMAIS confiance a un manifest non signe : la cle
//! publique Ed25519 attendue est embarquee dans le binaire (cf [`PUBLIC_KEY_HEX`]),
//! et chaque appel a [`fetch_current`] re-verifie la signature avant de
//! retourner les donnees a l'appelant.

mod download;
mod verify;

pub use download::{compute_plan as compute_plan_export, download_plan, PlanItem};
pub use verify::{verify_signature, ManifestVerifyError};

use serde::{Deserialize, Serialize};

/// Cle publique Ed25519 du manifest, en hex (32 octets).
///
/// Priorite : env compile-time `MANIFEST_PUBLIC_KEY_HEX_BUILD` (positionne
/// avant `pnpm launcher:build` pour cibler prod) > fallback dev en dur.
///
/// En dev (`debug_assertions`), `MANIFEST_PUBLIC_KEY_HEX` env runtime peut
/// surcharger pour pointer sur la cle locale.
pub const PUBLIC_KEY_HEX: &str = match option_env!("MANIFEST_PUBLIC_KEY_HEX_BUILD") {
    Some(s) => s,
    None => "0000000000000000000000000000000000000000000000000000000000000000",
};

#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
pub struct ManifestFile {
    pub path: String,
    pub sha256: String,
    pub size: u64,
    pub url: String,
    pub required: bool,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SignedManifest {
    pub version: String,
    pub minecraft_version: String,
    pub issued_at: String,
    pub expires_at: String,
    pub min_launcher_version: String,
    pub files: Vec<ManifestFile>,
    pub signature: String,
}
