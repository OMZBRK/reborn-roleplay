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
}
