//! Stockage sécurisé des secrets utilisateur (refresh token MS, JWT API).
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §4.5.
//!
//! Implémentation actuelle : `keyring` (Windows Credential Manager / macOS
//! Keychain / Secret Service Linux). Migration future possible vers
//! Tauri Stronghold pour un coffre chiffré par mot de passe local.

use std::path::Path;

pub mod mod_prefs;
pub mod prefs;
pub mod secrets;

pub use secrets::SecretStore;

/// Phrase complète pour une erreur disque : action + chemin + erreur OS
/// (localisée par Windows) + piste de résolution quand on en connaît une.
///
/// `action` est un infinitif : « lire », « écrire », « supprimer »…
/// Ex. : « Impossible d'écrire « C:\…\mod-prefs.json » : Accès refusé.
/// (os error 5) — vérifie que le dossier n'est pas en lecture seule… »
pub fn describe_io_error(action: &str, path: &Path, err: &std::io::Error) -> String {
    let de = if action.starts_with(['a', 'e', 'é', 'i', 'o', 'u', 'h']) {
        "d'"
    } else {
        "de "
    };
    format!(
        "Impossible {de}{action} « {} » : {err}{}",
        path.display(),
        io_hint(err)
    )
}

/// Conseil actionnable pour les erreurs disque fréquentes chez les joueurs.
fn io_hint(err: &std::io::Error) -> &'static str {
    // Codes Win32 : 32 = fichier utilisé par un autre processus, 33 = verrou,
    // 39/112 = disque plein. Unix : 28 = ENOSPC.
    match err.raw_os_error() {
        Some(32 | 33) if cfg!(windows) => {
            " — le fichier est utilisé par un autre programme (jeu ouvert, antivirus, OneDrive ?) : ferme-le puis réessaie."
        }
        Some(39 | 112) if cfg!(windows) => " — le disque est plein : libère de la place puis réessaie.",
        Some(28) if cfg!(unix) => " — le disque est plein : libère de la place puis réessaie.",
        _ => match err.kind() {
            std::io::ErrorKind::PermissionDenied => {
                " — accès refusé : vérifie que le dossier n'est pas en lecture seule ni bloqué par l'antivirus."
            }
            _ => "",
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn message_disque_avec_chemin_et_conseil() {
        let err = std::io::Error::from(std::io::ErrorKind::PermissionDenied);
        let msg = describe_io_error("écrire", Path::new("prefs.json"), &err);
        assert!(msg.starts_with("Impossible d'écrire « prefs.json » : "));
        assert!(msg.contains("lecture seule"));

        let err = std::io::Error::other("boom");
        let msg = describe_io_error("lire", Path::new("a.txt"), &err);
        assert_eq!(msg, "Impossible de lire « a.txt » : boom");
    }
}
