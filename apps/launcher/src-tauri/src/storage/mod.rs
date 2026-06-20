//! Stockage securise des secrets utilisateur (refresh token MS, JWT API).
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §4.5.
//!
//! Implementation actuelle : `keyring` (Windows Credential Manager / macOS
//! Keychain / Secret Service Linux). Migration future possible vers
//! Tauri Stronghold pour un coffre chiffre par mot de passe local.

pub mod mod_prefs;
pub mod prefs;
pub mod secrets;

pub use secrets::SecretStore;
