//! Wrapper autour de `keyring` avec un namespace unique par clé.
//!
//! Les messages d'erreur nomment le jeton concerné (« jeton de session
//! Reborn »…) et la cause système, jamais sa valeur.

use keyring::Entry;

const SERVICE: &str = "fr.reborn-rp.launcher";

/// Nom du coffre d'identifiants de l'OS, pour les messages.
const VAULT: &str = if cfg!(windows) {
    "Gestionnaire d'identification Windows"
} else if cfg!(target_os = "macos") {
    "Trousseau macOS"
} else {
    "trousseau du système (Secret Service)"
};

/// Clés connues — typées pour éviter les fautes de frappe.
#[derive(Debug, Clone, Copy)]
pub enum SecretKey {
    /// Refresh token Microsoft (long-lived, rotation à chaque usage).
    MicrosoftRefresh,
    /// JWT émis par l'API Reborn (court terme, mais persisté pour le
    /// "remember me").
    RebornAccessToken,
    /// Refresh token de l'API Reborn.
    RebornRefreshToken,
}

impl SecretKey {
    fn name(self) -> &'static str {
        match self {
            Self::MicrosoftRefresh => "ms_refresh_token",
            Self::RebornAccessToken => "reborn_access_token",
            Self::RebornRefreshToken => "reborn_refresh_token",
        }
    }

    fn label(self) -> &'static str {
        match self {
            Self::MicrosoftRefresh => "le jeton Microsoft",
            Self::RebornAccessToken => "le jeton d'accès Reborn",
            Self::RebornRefreshToken => "le jeton de session Reborn",
        }
    }
}

/// Slots per-compte du carousel (cf plus bas).
#[derive(Debug, Clone, Copy)]
enum AccountSlot {
    MicrosoftRefresh,
    RebornRefresh,
}

impl AccountSlot {
    fn prefix(self) -> &'static str {
        match self {
            Self::MicrosoftRefresh => "ms_refresh_token",
            Self::RebornRefresh => "reborn_refresh_token",
        }
    }

    fn label(self) -> &'static str {
        match self {
            Self::MicrosoftRefresh => "le jeton Microsoft de ce compte",
            Self::RebornRefresh => "le jeton de session Reborn de ce compte",
        }
    }
}

#[derive(Debug, thiserror::Error)]
pub enum StorageError {
    #[error("Impossible {action} {label} dans le {VAULT} : {}", describe_keyring(.source))]
    Keyring {
        /// « de lire », « d'enregistrer », « de supprimer », « d'accéder à ».
        action: &'static str,
        label: &'static str,
        #[source]
        source: keyring::Error,
    },
}

impl StorageError {
    fn keyring(action: &'static str, label: &'static str, source: keyring::Error) -> Self {
        Self::Keyring {
            action,
            label,
            source,
        }
    }
}

/// Cause keyring en français + quoi faire. Ne formate jamais le contenu
/// d'un identifiant (`BadEncoding` porte les octets du secret, `Ambiguous`
/// les entrées trouvées).
fn describe_keyring(err: &keyring::Error) -> String {
    match err {
        keyring::Error::PlatformFailure(e) => {
            format!("le coffre a échoué ({e}) — redémarre le launcher, puis ton PC si ça persiste")
        }
        keyring::Error::NoStorageAccess(e) => {
            format!("accès au coffre refusé ({e}) — vérifie que ta session est déverrouillée")
        }
        keyring::Error::NoEntry => "aucune entrée enregistrée".to_string(),
        keyring::Error::BadEncoding(_) => {
            "donnée enregistrée illisible — déconnecte-toi puis reconnecte-toi".to_string()
        }
        keyring::Error::TooLong(attr, max) => {
            format!("valeur trop longue pour le coffre ({attr} > {max} caractères)")
        }
        keyring::Error::Invalid(attr, reason) => format!("attribut {attr} invalide : {reason}"),
        keyring::Error::Ambiguous(items) => {
            format!(
                "{} entrées correspondent dans le coffre — déconnecte-toi puis reconnecte-toi",
                items.len()
            )
        }
        other => other.to_string(),
    }
}

/// Façade fine. Construit avec `SecretStore::new()`, partagée via Tauri State.
#[derive(Default, Clone, Copy)]
pub struct SecretStore;

impl SecretStore {
    pub fn new() -> Self {
        Self
    }

    pub fn set(&self, key: SecretKey, value: &str) -> Result<(), StorageError> {
        write_slot(key.name(), key.label(), value)
    }

    /// Retourne `Ok(None)` si la clé n'existe pas (premier lancement).
    pub fn get(&self, key: SecretKey) -> Result<Option<String>, StorageError> {
        read_slot(key.name(), key.label())
    }

    pub fn delete(&self, key: SecretKey) -> Result<(), StorageError> {
        delete_slot(key.name(), key.label())
    }

    // ──────────────────────────────────────────────────────────────────
    // Slots per-compte — utilisés par le carousel "switch silencieux".
    //
    // Cohabitent avec les slots legacy (MicrosoftRefresh, RebornRefreshToken,
    // RebornAccessToken) qui restent écrits à chaque login pour préserver
    // la session des staffs déjà en beta. Ces fonctions ajoutent juste des
    // entrées keyring keyées par minecraftUuid pour pouvoir retrouver les
    // tokens d'un compte spécifique quand l'utilisateur clique sur sa carte
    // dans le carousel.
    //
    // Format des clés : "{nom_legacy}:{uuid}", ex
    // "ms_refresh_token:550e8400-e29b-41d4-a716-446655440000".
    // L'UUID Mojang étant public et stable, pas de hashing nécessaire.
    // ──────────────────────────────────────────────────────────────────

    pub fn set_ms_refresh_token_for(&self, uuid: &str, token: &str) -> Result<(), StorageError> {
        set_account(AccountSlot::MicrosoftRefresh, uuid, token)
    }

    pub fn get_ms_refresh_token_for(&self, uuid: &str) -> Result<Option<String>, StorageError> {
        get_account(AccountSlot::MicrosoftRefresh, uuid)
    }

    pub fn set_reborn_refresh_token_for(
        &self,
        uuid: &str,
        token: &str,
    ) -> Result<(), StorageError> {
        set_account(AccountSlot::RebornRefresh, uuid, token)
    }

    pub fn get_reborn_refresh_token_for(&self, uuid: &str) -> Result<Option<String>, StorageError> {
        get_account(AccountSlot::RebornRefresh, uuid)
    }

    /// Supprime les deux slots per-uuid (MS + Reborn refresh) d'un compte.
    /// Utilisé après échec du silent switch : le user devra refaire un
    /// OAuth interactif pour re-peupler ces slots.
    pub fn delete_account_tokens(&self, uuid: &str) -> Result<(), StorageError> {
        for slot in [AccountSlot::MicrosoftRefresh, AccountSlot::RebornRefresh] {
            delete_slot(&format!("{}:{uuid}", slot.prefix()), slot.label())?;
        }
        Ok(())
    }
}

fn set_account(slot: AccountSlot, uuid: &str, value: &str) -> Result<(), StorageError> {
    write_slot(&format!("{}:{uuid}", slot.prefix()), slot.label(), value)
}

fn get_account(slot: AccountSlot, uuid: &str) -> Result<Option<String>, StorageError> {
    read_slot(&format!("{}:{uuid}", slot.prefix()), slot.label())
}

fn entry(name: &str, label: &'static str) -> Result<Entry, StorageError> {
    Entry::new(SERVICE, name).map_err(|e| StorageError::keyring("d'accéder à", label, e))
}

fn write_slot(name: &str, label: &'static str, value: &str) -> Result<(), StorageError> {
    entry(name, label)?
        .set_password(value)
        .map_err(|e| StorageError::keyring("d'enregistrer", label, e))
}

fn read_slot(name: &str, label: &'static str) -> Result<Option<String>, StorageError> {
    match entry(name, label)?.get_password() {
        Ok(v) => Ok(Some(v)),
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(e) => Err(StorageError::keyring("de lire", label, e)),
    }
}

/// Idempotent : supprimer une entrée absente n'est pas une erreur.
fn delete_slot(name: &str, label: &'static str) -> Result<(), StorageError> {
    match entry(name, label)?.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(StorageError::keyring("de supprimer", label, e)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn message_coffre_nomme_le_jeton_et_la_cause_sans_secret() {
        let err = StorageError::keyring(
            "de lire",
            SecretKey::RebornRefreshToken.label(),
            keyring::Error::BadEncoding(b"secret-ultra-sensible".to_vec()),
        );
        let msg = err.to_string();
        assert!(msg.starts_with("Impossible de lire le jeton de session Reborn dans le "));
        assert!(msg.contains("illisible"));
        assert!(!msg.contains("secret-ultra-sensible"));
    }
}
