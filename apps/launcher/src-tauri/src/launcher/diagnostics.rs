//! Détection automatique des erreurs Minecraft / Fabric en temps réel.
//!
//! La JVM lancée par le launcher émet ses logs sur stdout/stderr. Cette
//! couche les analyse ligne par ligne (cf logs.rs, qui la nourrit) et émet
//! des `Diagnostic` structurés vers le frontend (event Tauri
//! `game:diagnostic`) pour qu'il puisse afficher un message clair plutôt que
//! d'obliger l'utilisateur à fouiller `latest.log`.
//!
//! Le contrat avec le frontend est minimal :
//!   - chaque diagnostic a un `code` stable (`MOD_MC_VERSION_MISMATCH`, …)
//!     que l'UI peut switcher pour proposer des actions spécifiques.
//!   - `severity` permet d'afficher avec la bonne couleur.
//!   - `message` est un texte FR prêt à afficher ; `details` est l'extrait
//!     de log brut pour les utilisateurs avancés.

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
    /// Code stable identifiant le pattern détecté. Le frontend peut
    /// brancher des actions spécifiques dessus.
    pub code: &'static str,
    pub severity: Severity,
    /// Message court, lisible par l'utilisateur final, en français.
    pub message: String,
    /// Indication d'action concrète à effectuer (ex: "Supprime le mod X").
    pub hint: Option<String>,
    /// Extrait du log brut pour les utilisateurs avancés.
    pub details: Option<String>,
}

/// Lecture en cours d'un rapport de crash Minecraft (imprimé sur stdout) :
///
/// ```text
/// ---- Minecraft Crash Report ----
/// // commentaire
/// Time: …
/// Description: Rendering overlay
///
/// java.lang.NullPointerException: Cannot invoke "…" because "…" is null
/// ```
#[derive(Default)]
enum CrashReport {
    #[default]
    Idle,
    /// En-tête vu, on attend la ligne `Description:`.
    Header { lines_seen: u8 },
    /// Description lue, la prochaine ligne non vide est l'exception.
    Description(String),
}

/// Analyseur stateful : le rapport de crash s'étale sur plusieurs lignes.
#[derive(Default)]
pub struct LogAnalyzer {
    crash: CrashReport,
}

impl LogAnalyzer {
    pub fn new() -> Self {
        Self::default()
    }

    /// Analyse une ligne (stdout ou stderr) et retourne 0..1 diagnostic.
    /// Volontairement conservateur : ne signale qu'avec certitude pour
    /// éviter le bruit.
    pub fn ingest(&mut self, line: &str) -> Option<Diagnostic> {
        if let Some(diag) = self.crash_report(line) {
            return Some(diag);
        }

        // Mod compilé pour une autre version de Minecraft (le pattern le plus
        // fréquent après un changement de version). Exemple :
        //   "Mod 'Sodium' (sodium) 0.6.13+mc1.21.4 requires version 1.21.4
        //    of 'Minecraft' (minecraft), but only the wrong version is
        //    present: 1.21.1!"
        if let Some(diag) = parse_mod_version_mismatch(line) {
            return Some(diag);
        }

        // Résolution échouée générique (mods incompatibles entre eux).
        if line.contains("Mod resolution failed") || line.contains("Incompatible mods found") {
            return Some(Diagnostic {
                code: "FABRIC_MOD_RESOLUTION_FAILED",
                severity: Severity::Fatal,
                message: "Fabric refuse de démarrer : un ou plusieurs mods sont incompatibles.".into(),
                hint: Some(
                    "Ouvre l'onglet Mods et clique sur « Purger les mods incompatibles », ou supprime les mods cités dans le journal du dossier « mods »."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // Out of memory : la JVM s'est étranglée.
        if line.contains("OutOfMemoryError") {
            return Some(Diagnostic {
                code: "JVM_OUT_OF_MEMORY",
                severity: Severity::Fatal,
                message: "Minecraft a manqué de mémoire.".into(),
                hint: Some(
                    "Augmente la RAM allouée dans Paramètres › Jeu (recommandé : 4 à 6 Go), et ferme les applications gourmandes."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // Classe principale introuvable : classpath cassé.
        if line.contains("Could not find or load main class")
            || line.contains("impossible de trouver ou de charger la classe principale")
        {
            return Some(Diagnostic {
                code: "JVM_MAIN_CLASS_NOT_FOUND",
                severity: Severity::Fatal,
                message:
                    "Minecraft ne trouve pas sa classe principale : l'installation est incomplète."
                        .into(),
                hint: Some(
                    "Utilise « Réinstaller tout » dans Paramètres › Jeu, puis relance.".into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // Connexion refusée au serveur (menu multi ou bouton Espace).
        if line.contains("Connection refused") {
            return Some(Diagnostic {
                code: "SERVER_UNREACHABLE",
                severity: Severity::Error,
                message: "Le serveur Reborn est injoignable.".into(),
                hint: Some(
                    "Vérifie s'il est en ligne (statut sur le Discord), ou réessaie dans quelques minutes."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // UUID malformé passé à MC (dev-login avec un UUID non hexadécimal).
        if line.contains("NumberFormatException")
            && (line.contains("UUID") || line.contains("UndashedUuid"))
        {
            return Some(Diagnostic {
                code: "MC_INVALID_UUID",
                severity: Severity::Fatal,
                message: "Minecraft a reçu un identifiant de joueur (UUID) invalide.".into(),
                hint: Some(
                    "Déconnecte-toi puis reconnecte-toi. Si tu utilises la connexion de développement, mets l'API à jour."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // Authentification refusée par Mojang (jeton invalide ou expiré).
        if line.contains("Failed to verify username")
            || line.contains("Bad login")
            || line.contains("Invalid session")
        {
            return Some(Diagnostic {
                code: "MC_AUTH_INVALID",
                severity: Severity::Fatal,
                message: "Mojang refuse ta session Minecraft (jeton expiré ou utilisé ailleurs).".into(),
                hint: Some(
                    "Ferme le jeu, déconnecte-toi du launcher puis reconnecte-toi avec Microsoft. Évite de lancer le même compte sur deux PC."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // Son coupé : aucun périphérique audio n'a pu être ouvert. Le jeu
        // continue, d'où le simple avertissement.
        if line.contains("Error starting SoundSystem") {
            return Some(Diagnostic {
                code: "AUDIO_UNAVAILABLE",
                severity: Severity::Warning,
                message: "Minecraft n'a pas pu ouvrir ta sortie audio : le jeu tourne sans son.".into(),
                hint: Some(
                    "Vérifie qu'un casque ou des haut-parleurs sont branchés et activés dans Windows, puis relance le jeu."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        // OpenGL / drivers GPU : LWJGL n'arrive pas à créer un contexte.
        if line.contains("Failed to find OpenGL function")
            || line.contains("Pixel format not accelerated")
            || line.contains("could not create GLFW window")
            || line.contains("GLFW error 65542")
        {
            return Some(Diagnostic {
                code: "GPU_DRIVER_ISSUE",
                severity: Severity::Fatal,
                message: "Ta carte graphique ou son pilote ne permet pas de lancer Minecraft.".into(),
                hint: Some(
                    "Mets à jour le pilote de ta carte graphique (NVIDIA / AMD / Intel) depuis le site du fabricant."
                        .into(),
                ),
                details: Some(line.to_string()),
            });
        }

        None
    }

    /// Suit un rapport de crash et émet UN diagnostic portant la vraie cause
    /// (description + exception) dès que l'exception est lue.
    fn crash_report(&mut self, line: &str) -> Option<Diagnostic> {
        if line.contains("---- Minecraft Crash Report ----") {
            self.crash = CrashReport::Header { lines_seen: 0 };
            return None;
        }
        match &mut self.crash {
            CrashReport::Idle => None,
            CrashReport::Header { lines_seen } => {
                if let Some(desc) = line.trim().strip_prefix("Description:") {
                    self.crash = CrashReport::Description(desc.trim().to_string());
                } else {
                    *lines_seen += 1;
                    // La description arrive en 4e ligne ; au-delà, ce n'est
                    // pas un rapport au format attendu.
                    if *lines_seen > 12 {
                        self.crash = CrashReport::Idle;
                    }
                }
                None
            }
            CrashReport::Description(desc) => {
                let exception = line.trim();
                if exception.is_empty() {
                    return None;
                }
                if exception.contains("OutOfMemoryError") {
                    // Le diagnostic mémoire (avec son conseil RAM) est plus utile.
                    self.crash = CrashReport::Idle;
                    return None;
                }
                let desc = std::mem::take(desc);
                self.crash = CrashReport::Idle;
                // Le titre (« Minecraft a planté ») dit déjà ce qui s'est
                // passé : le message ne garde que l'étape en cours, et
                // l'exception Java (en anglais) part dans `details`, que le
                // front affiche à part en police fixe.
                let message = if desc.is_empty() {
                    "Erreur inattendue.".to_string()
                } else {
                    format!("Erreur pendant « {desc} ».")
                };
                Some(Diagnostic {
                    code: "MC_CRASH_REPORT",
                    severity: Severity::Fatal,
                    message,
                    hint: Some(
                        "Le rapport complet est dans le journal du jeu et dans le dossier crash-reports : joins-le à un ticket si le problème revient."
                            .into(),
                    ),
                    details: Some(exception.to_string()),
                })
            }
        }
    }
}

/// Parse les messages de Fabric Loader signalant qu'un mod requiert une
/// version différente de Minecraft. Format type :
///   "Mod 'Sodium' (sodium) 0.6.13+mc1.21.4 requires version 1.21.4 of
///    'Minecraft' (minecraft), but only the wrong version is present: 1.21.1!"
fn parse_mod_version_mismatch(line: &str) -> Option<Diagnostic> {
    if !line.contains("requires version") || !line.contains("but only the wrong version") {
        return None;
    }
    // Extraction grossière : on essaie de sortir le nom du mod et la version
    // attendue / présente, sinon on tombe sur un message générique.
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
            "Le mod « {name} » est prévu pour Minecraft {req}, mais le jeu est en {have}.",
        ),
        _ => "Un mod du dossier « mods » n'est pas compatible avec la version de Minecraft du launcher.".into(),
    };

    Some(Diagnostic {
        code: "MOD_MC_VERSION_MISMATCH",
        severity: Severity::Fatal,
        message,
        hint: Some(
            "Ouvre l'onglet Mods et clique sur « Purger les mods incompatibles », ou supprime le jar cité du dossier « mods »."
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
    fn detects_french_jvm_main_class_error() {
        let line = "Erreur : impossible de trouver ou de charger la classe principale net.fabricmc.loader.impl.launch.knot.KnotClient";
        let d = LogAnalyzer::new().ingest(line).unwrap();
        assert_eq!(d.code, "JVM_MAIN_CLASS_NOT_FOUND");
    }

    #[test]
    fn detects_missing_audio_as_warning() {
        let line = "[Render thread/ERROR]: Error starting SoundSystem. Turning off sounds & music";
        let d = LogAnalyzer::new().ingest(line).unwrap();
        assert_eq!(d.code, "AUDIO_UNAVAILABLE");
        assert_eq!(d.severity, Severity::Warning);
    }

    #[test]
    fn ignores_normal_logs() {
        let line = "[main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3";
        assert!(LogAnalyzer::new().ingest(line).is_none());
    }

    #[test]
    fn crash_report_yields_description_and_exception() {
        let mut a = LogAnalyzer::new();
        let report = [
            "---- Minecraft Crash Report ----",
            "// Why did you do that?",
            "",
            "Time: 2026-10-24 21:03:11",
            "Description: Rendering overlay",
            "",
            "java.lang.NullPointerException: Cannot invoke \"Foo.bar()\" because \"x\" is null",
            "\tat com.reborn.hud.Overlay.render(Overlay.java:42)",
        ];
        let diags: Vec<_> = report.iter().filter_map(|l| a.ingest(l)).collect();
        assert_eq!(diags.len(), 1);
        let d = &diags[0];
        assert_eq!(d.code, "MC_CRASH_REPORT");
        assert_eq!(d.message, "Erreur pendant « Rendering overlay ».");
        // L'exception reste dans les détails, pas dans le message.
        assert!(!d.message.contains("NullPointerException"));
        assert!(d
            .details
            .as_deref()
            .is_some_and(|x| x.starts_with("java.lang.NullPointerException")));
        // L'analyseur revient à l'état normal.
        assert!(a.ingest("[main/INFO]: rien à signaler").is_none());
    }
}
