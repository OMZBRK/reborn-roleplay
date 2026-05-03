//! Detection automatique des erreurs Minecraft / Fabric en temps reel.
//!
//! Le JVM lance par le launcher emet ses logs sur stdout/stderr. Cette
//! couche les analyse ligne par ligne et emet des `Diagnostic` structures
//! vers le frontend (event Tauri `game:diagnostic`) pour qu'il puisse
//! afficher un message clair plutot que d'obliger l'utilisateur a fouiller
//! `latest.log`.
//!
//! Le contrat avec le frontend est minimal :
//!   - chaque diagnostic a un `code` stable (`SODIUM_MC_MISMATCH`, …) que
//!     le UI peut switcher pour proposer des actions specifiques.
//!   - `severity` permet d'afficher avec la bonne couleur.
//!   - `message` est un texte FR pret a afficher ; `details` est l'extrait
//!     de log brut pour les utilisateurs avances.

use serde::Serialize;

#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum Severity {
    Warning,
    Error,
    Fatal,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Diagnostic {
    /// Code stable identifiant le pattern detecte. Le frontend peut
    /// brancher des actions specifiques dessus.
    pub code: &'static str,
    pub severity: Severity,
    /// Message court, lisible par l'utilisateur final, en francais.
    pub message: String,
    /// Indication d'action concrete a effectuer (ex: "Supprime le mod X").
    pub hint: Option<String>,
    /// Extrait du log brut pour les utilisateurs avances.
    pub details: Option<String>,
}

/// Analyseur stateful : certains patterns s'etalent sur plusieurs lignes
/// (ex: une stack Java apres une ligne `ERROR`). On garde un petit buffer.
#[derive(Default)]
pub struct LogAnalyzer {
    /// Si on a vu une ligne "Mod resolution failed", la prochaine ligne
    /// "Reason:" / "Fix:" est capturee comme details.
    pending_resolution_failure: bool,
}

impl LogAnalyzer {
    pub fn new() -> Self {
        Self::default()
    }

    /// Analyse une ligne (stdout ou stderr) et retourne 0..1 diagnostic.
    /// Volontairement conservateur : ne signale qu'avec certitude pour
    /// eviter le bruit.
    pub fn ingest(&mut self, line: &str) -> Option<Diagnostic> {
        // Sodium : version MC mismatch (le pattern le plus frequent en dev).
        // Exemple :
        //   "Mod 'Sodium' (sodium) 0.6.13+mc1.21.4 requires version 1.21.4
        //    of 'Minecraft' (minecraft), but only the wrong version is
        //    present: 1.21.1!"
        if let Some(diag) = parse_mod_version_mismatch(line) {
            return Some(diag);
        }

        // Resolution failure generique (mods incompatibles entre eux).
        if line.contains("Mod resolution failed")
            || line.contains("Incompatible mods found")
        {
            self.pending_resolution_failure = true;
            return Some(Diagnostic {
                code: "FABRIC_MOD_RESOLUTION_FAILED",
                severity: Severity::Fatal,
                message:
                    "Fabric refuse de demarrer : un ou plusieurs mods sont incompatibles."
                        .into(),
                hint: Some(
                    "Lance le nettoyage automatique du dossier mods, ou supprime les mods listes ci-dessous."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // Out of memory : la JVM s'est etranglee.
        if line.contains("OutOfMemoryError")
            || line.contains("java.lang.OutOfMemoryError")
        {
            return Some(Diagnostic {
                code: "JVM_OUT_OF_MEMORY",
                severity: Severity::Fatal,
                message: "La JVM Minecraft a manque de memoire.".into(),
                hint: Some(
                    "Augmente la RAM allouee dans Parametres > Jeu (recommande : 4 a 6 Go)."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // Classe principale introuvable : classpath casse.
        if line.contains("Could not find or load main class") {
            return Some(Diagnostic {
                code: "JVM_MAIN_CLASS_NOT_FOUND",
                severity: Severity::Fatal,
                message:
                    "Le client Minecraft n'a pas trouve sa classe principale."
                        .into(),
                hint: Some(
                    "Probleme de classpath ou de Fabric corrompu. Re-lance pour re-telecharger."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // Connexion refusee au serveur (auto-connect ou menu multi).
        if line.contains("Connection refused")
            || line.contains("ConnectException: Connection refused")
        {
            return Some(Diagnostic {
                code: "SERVER_UNREACHABLE",
                severity: Severity::Error,
                message: "Le serveur Reborn est injoignable.".into(),
                hint: Some(
                    "Verifie qu'il est en ligne (status sur le Discord), ou attends quelques minutes."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // UUID malforme passe a MC (probleme courant avec dev-login si on
        // genere un UUID non-hex). Pattern : "Description: Argument parsing"
        // suivi de "NumberFormatException" sur "fromStringLenient" (UUID).
        if line.contains("NumberFormatException")
            && (line.contains("UUID") || line.contains("UndashedUuid"))
        {
            return Some(Diagnostic {
                code: "MC_INVALID_UUID",
                severity: Severity::Fatal,
                message: "Le client Minecraft a recu un UUID invalide.".into(),
                hint: Some(
                    "C'est un bug du dev-login (UUID non-hex). Bumpe l'API et reconnecte-toi via [DEV] Connexion sans Microsoft."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // Authentification refusee par Mojang/Yggdrasil (token invalide).
        if line.contains("Failed to verify username")
            || line.contains("Bad login")
            || line.contains("Invalid session")
        {
            return Some(Diagnostic {
                code: "MC_AUTH_INVALID",
                severity: Severity::Fatal,
                message:
                    "Mojang refuse ton token Minecraft (probablement expire)."
                        .into(),
                hint: Some(
                    "Deconnecte-toi puis reconnecte-toi avec Microsoft. En attendant l'app MS approuvee, le mode dev-login produit des tokens factices que les serveurs en mode online refusent."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // OpenGL / drivers GPU : LWJGL n'arrive pas a creer un contexte.
        if line.contains("Failed to find OpenGL function")
            || line.contains("Pixel format not accelerated")
            || line.contains("could not create GLFW window")
        {
            return Some(Diagnostic {
                code: "GPU_DRIVER_ISSUE",
                severity: Severity::Fatal,
                message: "Probleme avec le driver graphique.".into(),
                hint: Some(
                    "Mets a jour les drivers de ta carte graphique (NVIDIA / AMD / Intel)."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        None
    }
}

/// Parse les messages de Fabric Loader signalant qu'un mod requiert une
/// version differente de Minecraft. Format type :
///   "Mod 'Sodium' (sodium) 0.6.13+mc1.21.4 requires version 1.21.4 of
///    'Minecraft' (minecraft), but only the wrong version is present: 1.21.1!"
fn parse_mod_version_mismatch(line: &str) -> Option<Diagnostic> {
    if !line.contains("requires version") || !line.contains("but only the wrong version") {
        return None;
    }
    // Extraction grossiere : on essaie de sortir le nom du mod et la version
    // attendue / presente, sinon on tombe sur un message generique.
    let mod_name = line
        .split_once("Mod '")
        .and_then(|(_, rest)| rest.split_once('\''))
        .map(|(name, _)| name.to_string());
    let required = line
        .split_once("requires version ")
        .and_then(|(_, rest)| rest.split_once(' '))
        .map(|(v, _)| v.to_string());
    let present = line
        .rsplit_once(": ")
        .map(|(_, rest)| rest.trim_end_matches('!').to_string());

    let message = match (mod_name.as_deref(), required.as_deref(), present.as_deref()) {
        (Some(name), Some(req), Some(have)) => format!(
            "Le mod '{name}' est compile pour Minecraft {req} mais le launcher est en {have}.",
        ),
        _ => "Un mod du dossier 'mods' n'est pas compatible avec la version Minecraft du launcher.".into(),
    };

    Some(Diagnostic {
        code: "MOD_MC_VERSION_MISMATCH",
        severity: Severity::Fatal,
        message,
        hint: Some(
            "Lance le nettoyage automatique des mods, ou supprime manuellement le jar incompatible dans le dossier 'mods'."
                .into(),
        ),
        details: Some(line.to_string()),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn detects_sodium_mc_mismatch() {
        let line = "Mod 'Sodium' (sodium) 0.6.13+mc1.21.4 requires version 1.21.4 of 'Minecraft' (minecraft), but only the wrong version is present: 1.21.1!";
        let mut a = LogAnalyzer::new();
        let d = a.ingest(line).expect("diagnostic attendu");
        assert_eq!(d.code, "MOD_MC_VERSION_MISMATCH");
        assert!(d.message.contains("Sodium"));
        assert!(d.message.contains("1.21.4"));
        assert!(d.message.contains("1.21.1"));
    }

    #[test]
    fn detects_oom() {
        let line = "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space";
        let d = LogAnalyzer::new().ingest(line).unwrap();
        assert_eq!(d.code, "JVM_OUT_OF_MEMORY");
    }

    #[test]
    fn detects_resolution_failure() {
        let line = "[main/WARN]: Mod resolution failed";
        let d = LogAnalyzer::new().ingest(line).unwrap();
        assert_eq!(d.code, "FABRIC_MOD_RESOLUTION_FAILED");
    }

    #[test]
    fn ignores_normal_logs() {
        let line = "[main/INFO]: Loading Minecraft 1.21.1 with Fabric Loader 0.19.2";
        assert!(LogAnalyzer::new().ingest(line).is_none());
    }
}
