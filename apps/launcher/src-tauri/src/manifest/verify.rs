//! Vérification cryptographique et temporelle du manifeste.
//!
//! On reproduit le format canonique du CLI `@reborn/manifest-signer` :
//! JSON avec clés dans l'ordre `version, minecraftVersion, issuedAt,
//! expiresAt, minLauncherVersion, files[] (triés par path)`, sans signature,
//! sans espaces.
//!
//! La signature prouve l'origine du manifeste, pas son actualité : on
//! contrôle en plus qu'il n'est pas expiré et qu'il n'est pas plus ancien que
//! le dernier accepté (cf [`check_freshness`]).

use ed25519_dalek::{Signature, Verifier, VerifyingKey};
use serde_json::json;
use thiserror::Error;

use super::{SignedManifest, PUBLIC_KEY_HEX};
use crate::launcher::clock;

/// Tolérance d'horloge pour l'expiration : un PC en avance de quelques
/// minutes ne doit pas bloquer le joueur.
pub const CLOCK_SKEW_MS: i64 = 5 * 60 * 1000;

#[derive(Debug, Error)]
pub enum ManifestVerifyError {
    #[error("la clé publique de vérification intégrée au launcher est invalide ({0}) : réinstalle la dernière version du launcher")]
    PublicKey(String),
    #[error("sa signature ne correspond pas : le fichier a été modifié ou signé avec une autre clé. Ne lance pas le jeu et préviens le staff")]
    InvalidSignature,
    #[error("sa signature est illisible ({0}) : préviens le staff")]
    SignatureFormat(String),
    #[error("sa date « {field} » est illisible (« {value} ») : préviens le staff")]
    BadDate { field: &'static str, value: String },
    #[error("Le manifeste des mods a expiré le {expires_at} : relance le launcher plus tard ou préviens le staff. Vérifie aussi la date et l'heure de ton PC.")]
    Expired { expires_at: String },
    #[error("Le serveur a renvoyé un manifeste des mods plus ancien (émis le {issued_at}) que celui déjà installé (émis le {last_accepted}) : retour en arrière refusé par sécurité. Réessaie plus tard ou préviens le staff.")]
    Rollback {
        issued_at: String,
        last_accepted: String,
    },
}

/// Charge la clé publique Ed25519 depuis le binaire ou depuis l'env en dev.
fn load_public_key() -> Result<VerifyingKey, ManifestVerifyError> {
    let hex_value = if cfg!(debug_assertions) {
        std::env::var("MANIFEST_PUBLIC_KEY_HEX").unwrap_or_else(|_| PUBLIC_KEY_HEX.to_string())
    } else {
        PUBLIC_KEY_HEX.to_string()
    };

    let bytes = hex::decode(hex_value.trim())
        .map_err(|e| ManifestVerifyError::PublicKey(format!("hexadécimal invalide : {e}")))?;
    let array: [u8; 32] = bytes
        .try_into()
        .map_err(|_| ManifestVerifyError::PublicKey("elle doit faire 32 octets".into()))?;

    VerifyingKey::from_bytes(&array)
        .map_err(|e| ManifestVerifyError::PublicKey(format!("décodage : {e}")))
}

/// Recompose le message canonique signé (cf packages/manifest-signer/src/manifest.ts).
fn canonical_bytes(manifest: &SignedManifest) -> Vec<u8> {
    let mut files: Vec<_> = manifest.files.iter().collect();
    files.sort_by(|a, b| a.path.cmp(&b.path));

    // serde_json::to_vec préserve l'ordre d'insertion des objets construits via json!.
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

    serde_json::to_vec(&canonical).expect("serde_json::Value est toujours sérialisable")
}

/// Vérifie la signature Ed25519 d'un manifeste. Retourne `Ok(())` si OK,
/// une erreur typée sinon.
pub fn verify_signature(manifest: &SignedManifest) -> Result<(), ManifestVerifyError> {
    let pubkey = load_public_key()?;

    let (prefix, sig_hex) = manifest
        .signature
        .split_once(':')
        .ok_or(ManifestVerifyError::InvalidSignature)?;
    if prefix != "ed25519" {
        return Err(ManifestVerifyError::InvalidSignature);
    }

    let sig_bytes =
        hex::decode(sig_hex).map_err(|e| ManifestVerifyError::SignatureFormat(e.to_string()))?;
    let sig_array: [u8; 64] = sig_bytes
        .try_into()
        .map_err(|_| ManifestVerifyError::SignatureFormat("elle doit faire 64 octets".into()))?;
    let signature = Signature::from_bytes(&sig_array);

    let message = canonical_bytes(manifest);
    pubkey
        .verify(&message, &signature)
        .map_err(|_| ManifestVerifyError::InvalidSignature)
}

/// Contrôles de fraîcheur, à faire APRÈS [`verify_signature`] (les dates ne
/// valent que si elles sont signées) :
/// - expiré : `expires_at + CLOCK_SKEW_MS < now` → refus ;
/// - anti-retour : `issued_at` strictement plus ancien que `last_accepted`
///   (dernier `issued_at` accepté par ce PC) → refus. Égal = même manifeste.
///
/// Une date mémorisée illisible est ignorée (on ne bloque pas le joueur sur
/// un fichier local corrompu).
pub fn check_freshness(
    manifest: &SignedManifest,
    now_ms: i64,
    last_accepted: Option<&str>,
) -> Result<(), ManifestVerifyError> {
    let issued = clock::parse_rfc3339_ms(&manifest.issued_at).ok_or_else(|| {
        ManifestVerifyError::BadDate {
            field: "issuedAt",
            value: manifest.issued_at.clone(),
        }
    })?;
    let expires = clock::parse_rfc3339_ms(&manifest.expires_at).ok_or_else(|| {
        ManifestVerifyError::BadDate {
            field: "expiresAt",
            value: manifest.expires_at.clone(),
        }
    })?;

    if expires + CLOCK_SKEW_MS < now_ms {
        return Err(ManifestVerifyError::Expired {
            expires_at: clock::format_human(expires),
        });
    }

    if let Some(last) = last_accepted.and_then(clock::parse_rfc3339_ms) {
        if issued < last {
            return Err(ManifestVerifyError::Rollback {
                issued_at: clock::format_human(issued),
                last_accepted: clock::format_human(last),
            });
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::manifest::ManifestFile;

    fn sample_manifest() -> SignedManifest {
        SignedManifest {
            version: "1.0.0".into(),
            minecraft_version: "26.1.2".into(),
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

    fn at(s: &str) -> i64 {
        clock::parse_rfc3339_ms(s).unwrap()
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

    #[test]
    fn fresh_manifest_accepted() {
        let m = sample_manifest();
        assert!(check_freshness(&m, at("2026-05-05T00:00:00Z"), None).is_ok());
        // Même manifeste que le dernier accepté : OK.
        assert!(check_freshness(
            &m,
            at("2026-05-05T00:00:00Z"),
            Some("2026-05-03T08:00:00.000Z")
        )
        .is_ok());
        // Plus récent que le dernier accepté : OK.
        assert!(
            check_freshness(&m, at("2026-05-05T00:00:00Z"), Some("2026-05-01T00:00:00Z")).is_ok()
        );
    }

    #[test]
    fn expired_manifest_rejected_with_clock_tolerance() {
        let m = sample_manifest();
        // 4 min après l'expiration : toléré (horloge en avance).
        assert!(check_freshness(&m, at("2026-05-10T08:04:00Z"), None).is_ok());
        // 6 min après : refusé, avec la date lisible dans le message.
        let err = check_freshness(&m, at("2026-05-10T08:06:00Z"), None).unwrap_err();
        assert!(matches!(err, ManifestVerifyError::Expired { .. }));
        assert!(err.to_string().contains("10/05/2026 à 08:00 UTC"));
    }

    #[test]
    fn older_manifest_rejected() {
        let m = sample_manifest();
        let err = check_freshness(&m, at("2026-05-05T00:00:00Z"), Some("2026-05-04T00:00:00Z"))
            .unwrap_err();
        assert!(matches!(err, ManifestVerifyError::Rollback { .. }));
        // Date mémorisée corrompue : ignorée.
        assert!(check_freshness(&m, at("2026-05-05T00:00:00Z"), Some("pas une date")).is_ok());
    }

    #[test]
    fn unreadable_dates_rejected() {
        let mut m = sample_manifest();
        m.expires_at = "bientôt".into();
        let err = check_freshness(&m, at("2026-05-05T00:00:00Z"), None).unwrap_err();
        assert!(matches!(
            err,
            ManifestVerifyError::BadDate {
                field: "expiresAt",
                ..
            }
        ));
    }
}
