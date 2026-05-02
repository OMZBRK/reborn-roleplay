//! Verification cryptographique du manifest.
//!
//! On reproduit le format canonique du CLI `@reborn/manifest-signer` :
//! JSON avec cles dans l'ordre `version, minecraftVersion, issuedAt,
//! expiresAt, minLauncherVersion, files[] (tries par path)`, sans signature,
//! sans espaces.

use ed25519_dalek::{Signature, Verifier, VerifyingKey};
use serde_json::json;
use thiserror::Error;

use super::{ManifestFile, SignedManifest, PUBLIC_KEY_HEX};

#[derive(Debug, Error)]
pub enum ManifestVerifyError {
    #[error("cle publique Ed25519 mal configuree : {0}")]
    PublicKey(String),
    #[error("signature du manifest invalide ou prefix manquant")]
    InvalidSignature,
    #[error("signature : {0}")]
    SignatureFormat(String),
}

/// Charge la cle publique Ed25519 depuis le binaire ou depuis l'env en dev.
fn load_public_key() -> Result<VerifyingKey, ManifestVerifyError> {
    let hex_value = if cfg!(debug_assertions) {
        std::env::var("MANIFEST_PUBLIC_KEY_HEX").unwrap_or_else(|_| PUBLIC_KEY_HEX.to_string())
    } else {
        PUBLIC_KEY_HEX.to_string()
    };

    let bytes = hex::decode(hex_value.trim())
        .map_err(|e| ManifestVerifyError::PublicKey(format!("hex invalide : {e}")))?;
    let array: [u8; 32] = bytes
        .try_into()
        .map_err(|_| ManifestVerifyError::PublicKey("doit faire 32 octets".into()))?;

    VerifyingKey::from_bytes(&array)
        .map_err(|e| ManifestVerifyError::PublicKey(format!("decodage : {e}")))
}

/// Recompose le message canonique signe (cf packages/manifest-signer/src/manifest.ts).
fn canonical_bytes(manifest: &SignedManifest) -> Vec<u8> {
    let mut files = manifest.files.clone();
    files.sort_by(|a, b| a.path.cmp(&b.path));

    // serde_json::to_vec preserve l'ordre d'insertion des objets construits via json!.
    let canonical = json!({
        "version": manifest.version,
        "minecraftVersion": manifest.minecraft_version,
        "issuedAt": manifest.issued_at,
        "expiresAt": manifest.expires_at,
        "minLauncherVersion": manifest.min_launcher_version,
        "files": files.iter().map(|f| json!({
            "path": f.path,
            "sha256": f.sha256.to_lowercase(),
            "size": f.size,
            "url": f.url,
            "required": f.required,
        })).collect::<Vec<_>>(),
    });

    serde_json::to_vec(&canonical).expect("serde_json::Value est toujours serialisable")
}

/// Verifie la signature Ed25519 d'un manifest. Retourne `Ok(())` si OK,
/// une erreur typee sinon.
pub fn verify_signature(manifest: &SignedManifest) -> Result<(), ManifestVerifyError> {
    let pubkey = load_public_key()?;

    let (prefix, sig_hex) = manifest
        .signature
        .split_once(':')
        .ok_or(ManifestVerifyError::InvalidSignature)?;
    if prefix != "ed25519" {
        return Err(ManifestVerifyError::InvalidSignature);
    }

    let sig_bytes = hex::decode(sig_hex)
        .map_err(|e| ManifestVerifyError::SignatureFormat(e.to_string()))?;
    let sig_array: [u8; 64] = sig_bytes
        .try_into()
        .map_err(|_| ManifestVerifyError::SignatureFormat("doit faire 64 octets".into()))?;
    let signature = Signature::from_bytes(&sig_array);

    let message = canonical_bytes(manifest);
    pubkey
        .verify(&message, &signature)
        .map_err(|_| ManifestVerifyError::InvalidSignature)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_manifest() -> SignedManifest {
        SignedManifest {
            version: "1.0.0".into(),
            minecraft_version: "1.21.4".into(),
            issued_at: "2026-05-03T08:00:00Z".into(),
            expires_at: "2026-05-10T08:00:00Z".into(),
            min_launcher_version: "0.1.0".into(),
            files: vec![ManifestFile {
                path: "mods/sodium.jar".into(),
                sha256: "deadbeef".into(),
                size: 100,
                url: "https://cdn/sodium.jar".into(),
                required: true,
            }],
            signature: "ed25519:00".into(),
        }
    }

    #[test]
    fn canonical_form_is_stable() {
        let m = sample_manifest();
        let a = canonical_bytes(&m);
        let b = canonical_bytes(&m);
        assert_eq!(a, b);
    }

    #[test]
    fn invalid_prefix_rejected() {
        let mut m = sample_manifest();
        m.signature = "rsa:dead".into();
        let res = verify_signature(&m);
        assert!(matches!(res, Err(ManifestVerifyError::InvalidSignature)));
    }
}
