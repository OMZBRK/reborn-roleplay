//! Manifest signé = source de vérité des fichiers de jeu.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §4.3, §8.1 (Triple validation).
//!
//! Le launcher ne fait JAMAIS confiance à un manifest non signé : la clé
//! publique Ed25519 attendue est embarquée dans le binaire (cf [`PUBLIC_KEY_HEX`]),
//! et `launcher::fetch_verified_manifest` re-vérifie signature + fraîcheur
//! (expiration, anti-retour) avant de rendre les données à l'appelant.

mod download;
mod verify;

pub use download::{
    compute_plan as compute_plan_export, download_plan, purge_orphan_mods, DownloadError, PlanItem,
};
pub use verify::{check_freshness, verify_signature, ManifestVerifyError};

use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::path::PathBuf;

use crate::launcher::{clock, paths};

/// Clé publique Ed25519 du manifest, en hex (32 octets).
///
/// Priorité : env compile-time `MANIFEST_PUBLIC_KEY_HEX_BUILD` (positionné
/// avant `pnpm launcher:build` pour cibler prod) > fallback dev en dur.
///
/// En dev (`debug_assertions`), `MANIFEST_PUBLIC_KEY_HEX` env runtime peut
/// surcharger pour pointer sur la clé locale.
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

// ──────────────────────────────────────────────────────
//  Anti-retour : dernier `issued_at` accepté
// ──────────────────────────────────────────────────────

/// `<dossier de données>/manifest-state.json`. Clé = URL de l'API, pour
/// qu'alterner entre l'API locale (dev) et la prod ne bloque personne : chaque
/// source a sa propre chronologie de manifestes.
///
/// ```json
/// { "lastIssuedAt": { "https://api.reborn-rp.com/v1": "2026-10-24T09:00:00.000Z" } }
/// ```
#[derive(Debug, Default, Deserialize, Serialize)]
#[serde(default, rename_all = "camelCase")]
struct ManifestState {
    last_issued_at: BTreeMap<String, String>,
}

fn state_path() -> Option<PathBuf> {
    paths::game_dir()
        .ok()
        .map(|d| d.join("manifest-state.json"))
}

async fn load_state() -> ManifestState {
    let Some(path) = state_path() else {
        return ManifestState::default();
    };
    match tokio::fs::read(&path).await {
        Ok(bytes) => serde_json::from_slice(&bytes).unwrap_or_else(|e| {
            tracing::warn!("{} illisible ({e}) : ignoré", path.display());
            ManifestState::default()
        }),
        Err(_) => ManifestState::default(),
    }
}

/// Dernier `issued_at` accepté pour cette API, s'il y en a un.
pub async fn last_accepted_issued_at(api_base: &str) -> Option<String> {
    load_state().await.last_issued_at.remove(api_base)
}

/// Vrai si `issued_ms` dépasse l'heure locale de plus que la tolérance
/// d'horloge admise pour l'expiration (`verify::CLOCK_SKEW_MS`).
fn issued_in_future(issued_ms: i64, now_ms: i64) -> bool {
    issued_ms > now_ms + verify::CLOCK_SKEW_MS
}

/// Mémorise `issued_at` comme dernier accepté pour cette API (seulement s'il
/// est plus récent que la valeur connue). Écriture atomique (tmp + rename).
///
/// Une date dans le futur (horloge du PC qui a signé en avance) n'est pas
/// mémorisée : sinon, chaque manifeste correctement daté publié ensuite
/// serait refusé pour « retour en arrière » jusqu'à ce que cette date soit
/// passée. Le manifeste reste accepté, il ne devient juste pas la référence.
pub async fn remember_issued_at(api_base: &str, issued_at: &str) -> std::io::Result<()> {
    let Some(new_ms) = clock::parse_rfc3339_ms(issued_at) else {
        return Ok(());
    };
    if issued_in_future(new_ms, clock::now_ms()) {
        tracing::warn!(
            "manifeste daté dans le futur ({issued_at}) : non mémorisé pour l'anti-retour"
        );
        return Ok(());
    }
    let mut state = load_state().await;
    let known = state
        .last_issued_at
        .get(api_base)
        .and_then(|s| clock::parse_rfc3339_ms(s));
    if known.is_some_and(|k| k >= new_ms) {
        return Ok(());
    }
    state
        .last_issued_at
        .insert(api_base.to_string(), issued_at.to_string());

    let Some(path) = state_path() else {
        return Ok(());
    };
    if let Some(parent) = path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let tmp = path.with_extension("json.tmp");
    let body = serde_json::to_vec_pretty(&state).map_err(std::io::Error::other)?;
    tokio::fs::write(&tmp, body).await?;
    tokio::fs::rename(&tmp, &path).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn future_issued_at_is_not_a_rollback_reference() {
        let now = 1_000_000_000_000;
        assert!(!issued_in_future(now, now));
        assert!(!issued_in_future(now + verify::CLOCK_SKEW_MS, now));
        assert!(issued_in_future(now + verify::CLOCK_SKEW_MS + 1, now));
    }
}
