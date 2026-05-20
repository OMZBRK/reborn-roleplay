//! Wrapper autour de `keyring` avec un namespace unique par cle.

use keyring::Entry;

const SERVICE: &str = "fr.reborn-rp.launcher";

/// Cles connues — typees pour eviter les fautes de frappe.
#[derive(Debug, Clone, Copy)]
pub enum SecretKey {
    /// Refresh token Microsoft (long-lived, rotation a chaque usage).
    MicrosoftRefresh,
    /// JWT emis par l'API Reborn (court terme, mais persiste pour le
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
}

#[derive(Debug, thiserror::Error)]
pub enum StorageError {
    #[error(transparent)]
    Keyring(#[from] keyring::Error),
}

/// Facade fine. Construit avec `SecretStore::new()`, partagee via Tauri State.
#[derive(Default, Clone, Copy)]
pub struct SecretStore;

impl SecretStore {
    pub fn new() -> Self {
        Self
    }

    fn entry(&self, key: SecretKey) -> Result<Entry, StorageError> {
        Entry::new(SERVICE, key.name()).map_err(StorageError::from)
    }

    pub fn set(&self, key: SecretKey, value: &str) -> Result<(), StorageError> {
        self.entry(key)?.set_password(value).map_err(StorageError::from)
    }

    /// Retourne `Ok(None)` si la cle n'existe pas (premier lancement).
    pub fn get(&self, key: SecretKey) -> Result<Option<String>, StorageError> {
        match self.entry(key)?.get_password() {
            Ok(v) => Ok(Some(v)),
            Err(keyring::Error::NoEntry) => Ok(None),
            Err(e) => Err(StorageError::from(e)),
        }
    }

    pub fn delete(&self, key: SecretKey) -> Result<(), StorageError> {
        match self.entry(key)?.delete_credential() {
            Ok(()) => Ok(()),
            Err(keyring::Error::NoEntry) => Ok(()),
            Err(e) => Err(StorageError::from(e)),
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Slots per-compte — utilises par le carousel "switch silencieux".
    //
    // Cohabitent avec les slots legacy (MicrosoftRefresh, RebornRefreshToken,
    // RebornAccessToken) qui restent ecrits a chaque login pour preserver
    // la session des staffs deja en beta. Ces nouvelles fonctions ajoutent
    // juste des entrees keyring kxyees par minecraftUuid pour pouvoir
    // retrouver les tokens d'un compte specifique quand l'utilisateur clique
    // sur sa carte dans le carousel.
    //
    // Format des cles : "{nom_legacy}:{uuid}", ex
    // "ms_refresh_token:550e8400-e29b-41d4-a716-446655440000".
    // L'UUID Mojang etant public et stable, pas de hashing necessaire.
    // ──────────────────────────────────────────────────────────────────

    fn account_entry(&self, prefix: &str, uuid: &str) -> Result<Entry, StorageError> {
        let name = format!("{prefix}:{uuid}");
        Entry::new(SERVICE, &name).map_err(StorageError::from)
    }

    fn set_account(&self, prefix: &str, uuid: &str, value: &str) -> Result<(), StorageError> {
        self.account_entry(prefix, uuid)?
            .set_password(value)
            .map_err(StorageError::from)
    }

    fn get_account(&self, prefix: &str, uuid: &str) -> Result<Option<String>, StorageError> {
        match self.account_entry(prefix, uuid)?.get_password() {
            Ok(v) => Ok(Some(v)),
            Err(keyring::Error::NoEntry) => Ok(None),
            Err(e) => Err(StorageError::from(e)),
        }
    }

    fn delete_account_slot(&self, prefix: &str, uuid: &str) -> Result<(), StorageError> {
        match self.account_entry(prefix, uuid)?.delete_credential() {
            Ok(()) => Ok(()),
            Err(keyring::Error::NoEntry) => Ok(()),
            Err(e) => Err(StorageError::from(e)),
        }
    }

    pub fn set_ms_refresh_token_for(&self, uuid: &str, token: &str) -> Result<(), StorageError> {
        self.set_account("ms_refresh_token", uuid, token)
    }

    pub fn get_ms_refresh_token_for(&self, uuid: &str) -> Result<Option<String>, StorageError> {
        self.get_account("ms_refresh_token", uuid)
    }

    pub fn set_reborn_refresh_token_for(&self, uuid: &str, token: &str) -> Result<(), StorageError> {
        self.set_account("reborn_refresh_token", uuid, token)
    }

    pub fn get_reborn_refresh_token_for(&self, uuid: &str) -> Result<Option<String>, StorageError> {
        self.get_account("reborn_refresh_token", uuid)
    }

    /// Supprime les deux slots per-uuid (MS + Reborn refresh) d'un compte.
    /// Utilise apres echec du silent switch : le user devra refaire un
    /// OAuth interactif pour re-peupler ces slots.
    pub fn delete_account_tokens(&self, uuid: &str) -> Result<(), StorageError> {
        self.delete_account_slot("ms_refresh_token", uuid)?;
        self.delete_account_slot("reborn_refresh_token", uuid)?;
        Ok(())
    }
}
