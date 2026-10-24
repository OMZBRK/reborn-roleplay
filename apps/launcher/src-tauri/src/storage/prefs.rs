//! Préférences locales utilisateur (RAM, résolution, etc.).
//!
//! Persistées en JSON dans `<game_dir>/prefs.json`. Tauri-plugin-store
//! ferait l'affaire, mais on garde la dep arbre pour l'instant — ces
//! prefs ne sont ni secrètes ni partagées.
//!
//! Deux écrivains : le front (`prefs_set`, réglages affichés) et le launcher
//! lui-même (champs internes comme `trayHintShown`). Toute écriture passe par
//! [`update`], qui sérialise les lectures-écritures du fichier.

use serde::{Deserialize, Deserializer, Serialize};
use std::path::PathBuf;
use tokio::fs;

use crate::launcher::paths;

/// Comportement de la fenêtre du launcher quand le jeu démarre.
#[derive(Debug, Clone, Copy, Default, Serialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum OnGameStart {
    /// Le launcher se réduit dès que la fenêtre Minecraft est ouverte, et
    /// revient au premier plan quand le jeu se ferme.
    #[default]
    Minimize,
    /// Le launcher reste affiché (pratique pour suivre les logs).
    Stay,
}

/// Valeur inconnue ou mal typée → défaut, sans invalider tout le fichier
/// (sinon un `prefs.json` écrit par une version future remettrait TOUTES les
/// préférences à zéro).
fn lenient_on_game_start<'de, D: Deserializer<'de>>(d: D) -> Result<OnGameStart, D::Error> {
    let raw = serde_json::Value::deserialize(d)?;
    Ok(match raw.as_str() {
        Some("stay") => OnGameStart::Stay,
        _ => OnGameStart::Minimize,
    })
}

/// Booléen tolérant, vrai par défaut : une valeur mal typée ne remet pas
/// tout le fichier à zéro (même logique que [`lenient_on_game_start`]).
fn lenient_true<'de, D: Deserializer<'de>>(d: D) -> Result<bool, D::Error> {
    Ok(serde_json::Value::deserialize(d)?.as_bool().unwrap_or(true))
}

/// Booléen tolérant, faux par défaut.
fn lenient_false<'de, D: Deserializer<'de>>(d: D) -> Result<bool, D::Error> {
    Ok(serde_json::Value::deserialize(d)?
        .as_bool()
        .unwrap_or(false))
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct Preferences {
    pub ram_mb: u32,
    pub width: u32,
    pub height: u32,
    pub auto_connect: bool,
    pub discord_rich_presence: bool,
    pub language: String,
    #[serde(deserialize_with = "lenient_on_game_start")]
    pub on_game_start: OnGameStart,
    /// Fermer la fenêtre (croix, Alt+F4) la cache dans la zone de
    /// notification au lieu de quitter (cf tray.rs). Défaut : oui.
    #[serde(deserialize_with = "lenient_true")]
    pub close_to_tray: bool,
    /// Interne, jamais modifié par le front : la notification « Reborn tourne
    /// toujours » a déjà été montrée une fois.
    #[serde(deserialize_with = "lenient_false")]
    pub tray_hint_shown: bool,
}

impl Preferences {
    /// Valeur reçue du front : les champs internes restent ceux du disque
    /// (le front ne les affiche pas, ou en garde une copie périmée).
    pub fn with_internal_fields_of(mut self, disk: &Preferences) -> Self {
        self.tray_hint_shown = disk.tray_hint_shown;
        self
    }
}

impl Default for Preferences {
    fn default() -> Self {
        Self {
            ram_mb: 4096,
            width: 1280,
            height: 720,
            auto_connect: true,
            discord_rich_presence: true,
            language: "fr".to_string(),
            on_game_start: OnGameStart::Minimize,
            close_to_tray: true,
            tray_hint_shown: false,
        }
    }
}

#[derive(Debug, thiserror::Error)]
pub enum PrefsError {
    #[error("dossier de données introuvable ({0})")]
    Paths(#[from] paths::PathError),
    #[error("lecture de {} impossible ({source})", .path.display())]
    Read {
        path: PathBuf,
        source: std::io::Error,
    },
    #[error("écriture de {} impossible ({source}) : vérifie que le dossier n'est pas en lecture seule ou bloqué par un antivirus", .path.display())]
    Write {
        path: PathBuf,
        source: std::io::Error,
    },
    #[error("préférences impossibles à enregistrer ({0})")]
    Json(#[from] serde_json::Error),
}

fn prefs_path() -> Result<PathBuf, PrefsError> {
    Ok(paths::game_dir()?.join("prefs.json"))
}

pub async fn load() -> Result<Preferences, PrefsError> {
    let path = prefs_path()?;
    let bytes = match fs::read(&path).await {
        Ok(bytes) => bytes,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(Preferences::default()),
        Err(source) => return Err(PrefsError::Read { path, source }),
    };
    // Fichier corrompu (édition à la main, écriture interrompue) : on repart
    // des valeurs par défaut plutôt que de bloquer le lancement.
    Ok(
        serde_json::from_slice::<Preferences>(&bytes).unwrap_or_else(|e| {
            tracing::warn!(
                "{} illisible ({e}) : préférences par défaut",
                path.display()
            );
            Preferences::default()
        }),
    )
}

/// Sérialise les lectures-modifications-écritures de `prefs.json` (et le
/// fichier temporaire de [`save`], commun à toutes les écritures).
static WRITE_LOCK: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());

/// Lit, modifie et réécrit les préférences d'un bloc, sans écrivain
/// concurrent. Un JSON corrompu est déjà ramené aux valeurs par défaut par
/// [`load`] ; une erreur de lecture (accès refusé, fichier verrouillé) est
/// propagée sans rien écrire : repartir des valeurs par défaut écraserait
/// la RAM, la résolution, etc. du joueur (l'écrivain interne de
/// `trayHintShown` ne veut changer qu'un champ).
pub async fn update(change: impl FnOnce(&mut Preferences)) -> Result<Preferences, PrefsError> {
    let _guard = WRITE_LOCK.lock().await;
    let mut prefs = load().await?;
    change(&mut prefs);
    save(&prefs).await?;
    Ok(prefs)
}

/// Écriture atomique (fichier temporaire + rename) : un crash pendant
/// l'enregistrement ne laisse jamais un `prefs.json` à moitié écrit.
/// Passer par [`update`] (seul appelant) pour ne pas croiser deux écritures.
async fn save(prefs: &Preferences) -> Result<(), PrefsError> {
    let path = prefs_path()?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .await
            .map_err(|source| PrefsError::Write {
                path: parent.to_path_buf(),
                source,
            })?;
    }
    let pretty = serde_json::to_vec_pretty(prefs)?;
    let tmp = path.with_extension("json.tmp");
    fs::write(&tmp, pretty)
        .await
        .map_err(|source| PrefsError::Write {
            path: tmp.clone(),
            source,
        })?;
    fs::rename(&tmp, &path)
        .await
        .map_err(|source| PrefsError::Write { path, source })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn on_game_start_defaults_and_round_trip() {
        let p: Preferences = serde_json::from_str(r#"{"ramMb": 6144}"#).unwrap();
        assert_eq!(p.ram_mb, 6144);
        assert_eq!(p.on_game_start, OnGameStart::Minimize);

        let p: Preferences = serde_json::from_str(r#"{"onGameStart": "stay"}"#).unwrap();
        assert_eq!(p.on_game_start, OnGameStart::Stay);
        let json = serde_json::to_value(&p).unwrap();
        assert_eq!(json["onGameStart"], "stay");
    }

    #[test]
    fn close_to_tray_defaults_to_true_and_tolerates_bad_values() {
        let p: Preferences = serde_json::from_str("{}").unwrap();
        assert!(p.close_to_tray);
        assert!(!p.tray_hint_shown);

        let p: Preferences = serde_json::from_str(r#"{"closeToTray": false}"#).unwrap();
        assert!(!p.close_to_tray);
        let json = serde_json::to_value(&p).unwrap();
        assert_eq!(json["closeToTray"], false);
        assert_eq!(json["trayHintShown"], false);

        // Valeurs mal typées : défaut pour le champ, le reste est conservé.
        let p: Preferences =
            serde_json::from_str(r#"{"ramMb": 6144, "closeToTray": "non", "trayHintShown": 1}"#)
                .unwrap();
        assert_eq!(p.ram_mb, 6144);
        assert!(p.close_to_tray);
        assert!(!p.tray_hint_shown);
    }

    #[test]
    fn front_value_keeps_internal_fields_from_disk() {
        let disk = Preferences {
            tray_hint_shown: true,
            ..Preferences::default()
        };
        // Le front ne connaît pas trayHintShown : il arrive absent (= faux).
        let from_front: Preferences =
            serde_json::from_str(r#"{"ramMb": 8192, "closeToTray": false}"#).unwrap();
        let merged = from_front.with_internal_fields_of(&disk);
        assert_eq!(merged.ram_mb, 8192);
        assert!(!merged.close_to_tray);
        assert!(merged.tray_hint_shown);

        // Et une copie périmée envoyée par le front ne l'écrase pas non plus.
        let stale = Preferences {
            tray_hint_shown: true,
            ..Preferences::default()
        };
        let merged = stale.with_internal_fields_of(&Preferences::default());
        assert!(!merged.tray_hint_shown);
    }

    #[test]
    fn unknown_on_game_start_keeps_other_prefs() {
        let p: Preferences = serde_json::from_str(r#"{"ramMb": 8192, "onGameStart": 42}"#).unwrap();
        assert_eq!(p.ram_mb, 8192);
        assert_eq!(p.on_game_start, OnGameStart::Minimize);
    }
}
