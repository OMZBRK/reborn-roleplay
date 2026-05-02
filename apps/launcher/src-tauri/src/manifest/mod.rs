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
/// En dev (`debug_assertions`), on peut surcharger via `MANIFEST_PUBLIC_KEY_HEX`
/// pour pointer sur la cle generee par `secrets/manifest_ed25519_public.hex`.
/// En prod, la valeur en dur ci-dessous est seule autoritative.
///
/// **TODO release v1.0** : remplacer par la cle de prod et retirer
/// le fallback dev (cf §14.7 — rotation des secrets).
pub const PUBLIC_KEY_HEX: &str = "0000000000000000000000000000000000000000000000000000000000000000";

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
