//! Zone de notification Windows (l'« icône cachée » près de l'horloge).
//!
//! - Icône de l'application, info-bulle « Reborn Launcher » (« Reborn
//!   Launcher · en jeu » pendant une partie).
//! - Clic gauche : ramène la fenêtre (affichée, dé-réduite, au premier plan).
//!   Clic droit : « Ouvrir Reborn », « Redémarrer le launcher », « Quitter ».
//!   Sous Windows 11, une nouvelle icône va d'abord dans les icônes cachées
//!   (flèche ^ près de l'horloge) : les textes d'aide le disent.
//! - Fermer la fenêtre (croix de `WindowControls`, qui appelle `close()`,
//!   Alt+F4, « Fermer la fenêtre » de la barre des tâches) la cache au lieu
//!   de quitter quand la préférence `closeToTray` est active (défaut). La
//!   première fois, une notification Windows explique où retrouver le
//!   launcher (préférence interne `trayHintShown`). Si l'icône n'est pas
//!   réellement affichée (cf [`tray_icon_present`]), la fermeture quitte.
//! - « Redémarrer le launcher » : avec la fermeture qui cache, « ferme et
//!   relance » ne redémarre plus rien (la relance ramène le même processus,
//!   instance unique) ; cette entrée repart d'un processus neuf.
//! - Sortie : `cleanup_before_exit` de Tauri retire l'icône, y compris quand
//!   l'installation d'une mise à jour termine le processus (le plugin updater
//!   l'appelle avant son `std::process::exit`).
//!
//! « Quitter » pendant une partie : le launcher quitte, le jeu continue.
//! Vérifié le 24/10/2026 : le processus Java est lancé sans `kill_on_drop` et
//! appartient à une tâche détachée (`await_game_end`), que la sortie du
//! processus abandonne sans la détruire ; Windows ne tue pas les processus
//! enfants à la mort du parent, et les stdout/stderr devenus orphelins
//! échouent en silence côté Java (PrintStream avale l'erreur). Essai : un
//! `javaw` qui écrit en continu dans ces tubes termine normalement après la
//! mort de son parent. On perd seulement le suivi de la partie (journal,
//! rapport de crash, statut Discord) : pas de confirmation, on quitte.

use std::sync::atomic::{AtomicBool, Ordering};

use tauri::menu::{Menu, MenuEvent, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIcon, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, CloseRequestApi, Manager, Runtime, Window};
use tauri_plugin_notification::NotificationExt;

use crate::launcher::GameState;
use crate::storage::prefs::{self, Preferences};

const TRAY_ID: &str = "reborn-tray";
const MAIN_WINDOW: &str = "main";
const MENU_OPEN: &str = "tray-open";
const MENU_RESTART: &str = "tray-restart";
const MENU_QUIT: &str = "tray-quit";
const HINT_TITLE: &str = "Reborn Launcher";
const HINT_BODY: &str = "Reborn tourne toujours : clique sur son icône, dans les icônes cachées (flèche ^ près de l'horloge), pour le rouvrir.";

/// Réglages lus par le gestionnaire de fermeture, qui est synchrone (pas de
/// lecture disque à chaque clic sur la croix). Chargés au démarrage, mis à
/// jour par `prefs_set`.
pub struct TrayState {
    close_to_tray: AtomicBool,
    hint_shown: AtomicBool,
    /// Partie en cours, pour l'info-bulle remise par [`tray_icon_present`].
    in_game: AtomicBool,
}

impl TrayState {
    pub fn new() -> Self {
        let defaults = Preferences::default();
        Self {
            close_to_tray: AtomicBool::new(defaults.close_to_tray),
            hint_shown: AtomicBool::new(defaults.tray_hint_shown),
            in_game: AtomicBool::new(false),
        }
    }

    pub fn apply_prefs(&self, prefs: &Preferences) {
        self.close_to_tray
            .store(prefs.close_to_tray, Ordering::SeqCst);
        // Jamais remis à faux : une fois montrée, la notification ne revient
        // pas dans la session, même si le disque n'a pas pu être mis à jour.
        if prefs.tray_hint_shown {
            self.hint_shown.store(true, Ordering::SeqCst);
        }
    }
}

impl Default for TrayState {
    fn default() -> Self {
        Self::new()
    }
}

/// Info-bulle de l'icône selon l'état du jeu.
fn tooltip(in_game: bool) -> &'static str {
    if in_game {
        "Reborn Launcher · en jeu"
    } else {
        "Reborn Launcher"
    }
}

/// Crée l'icône et son menu. En cas d'échec, l'appelant journalise et la
/// fermeture de la fenêtre quitte le launcher comme avant (cf
/// [`on_close_requested`]).
pub fn create<R: Runtime>(app: &AppHandle<R>) -> tauri::Result<()> {
    let open = MenuItem::with_id(app, MENU_OPEN, "Ouvrir Reborn", true, None::<&str>)?;
    let separator = PredefinedMenuItem::separator(app)?;
    let restart = MenuItem::with_id(
        app,
        MENU_RESTART,
        "Redémarrer le launcher",
        true,
        None::<&str>,
    )?;
    let quit = MenuItem::with_id(app, MENU_QUIT, "Quitter", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&open, &separator, &restart, &quit])?;

    let mut builder = TrayIconBuilder::with_id(TRAY_ID)
        .tooltip(tooltip(false))
        .menu(&menu)
        // Clic gauche = ouvrir ; le menu reste sur le clic droit.
        .show_menu_on_left_click(false)
        .on_menu_event(on_menu_event)
        .on_tray_icon_event(on_tray_icon_event);
    if let Some(icon) = app.default_window_icon() {
        builder = builder.icon(icon.clone());
    }
    builder.build(app)?;
    Ok(())
}

fn on_tray_icon_event<R: Runtime>(tray: &TrayIcon<R>, event: TrayIconEvent) {
    if let TrayIconEvent::Click {
        button: MouseButton::Left,
        button_state: MouseButtonState::Up,
        ..
    } = event
    {
        show_main_window(tray.app_handle());
    }
}

fn on_menu_event<R: Runtime>(app: &AppHandle<R>, event: MenuEvent) {
    match event.id().0.as_str() {
        MENU_OPEN => show_main_window(app),
        MENU_RESTART => restart(app),
        MENU_QUIT => quit(app),
        _ => {}
    }
}

/// Journalise qu'une partie en cours ne sera plus suivie (« Quitter »,
/// « Redémarrer le launcher ») : le jeu n'est pas tué (cf en-tête du module).
fn log_detached_game<R: Runtime>(app: &AppHandle<R>, action: &str) {
    if app
        .try_state::<GameState>()
        .is_some_and(|game| game.is_busy())
    {
        tracing::info!(
            "« {action} » pendant une partie : le jeu reste ouvert, il n'est simplement plus suivi"
        );
    }
}

/// « Quitter » : quitte vraiment. Le ménage (port d'instance unique, statut
/// Discord, watcher) se fait sur `RunEvent::Exit` (lib.rs). Une partie en
/// cours n'est pas tuée (cf en-tête du module).
fn quit<R: Runtime>(app: &AppHandle<R>) {
    log_detached_game(app, "Quitter");
    app.exit(0);
}

/// « Redémarrer le launcher » : même sortie que « Quitter » (`RunEvent::Exit`
/// rend d'abord le port d'instance unique, cf lib.rs), puis Tauri relance
/// l'exécutable. Remet à zéro un état bloqué côté Rust (connexion,
/// téléchargement, partie détachée), ce que le rechargement de la page
/// (RouteBoundary) ne fait pas.
fn restart<R: Runtime>(app: &AppHandle<R>) {
    log_detached_game(app, "Redémarrer le launcher");
    tracing::info!("redémarrage du launcher demandé depuis la zone de notification");
    app.request_restart();
}

/// Affiche la fenêtre principale, la dé-réduit et la met au premier plan :
/// clic sur l'icône, « Ouvrir Reborn », relance du launcher (instance
/// unique), fin de partie.
pub fn show_main_window<R: Runtime>(app: &AppHandle<R>) {
    let Some(win) = app.get_webview_window(MAIN_WINDOW) else {
        return;
    };
    // Dans cet ordre : `set_focus` n'agit que sur une fenêtre visible et non
    // réduite.
    if let Err(e) = win.show() {
        tracing::warn!("affichage de la fenêtre principale impossible : {e}");
    }
    let _ = win.unminimize();
    let _ = win.set_focus();
}

/// Met l'info-bulle à jour au début et à la fin d'une partie.
pub fn set_in_game<R: Runtime>(app: &AppHandle<R>, in_game: bool) {
    if let Some(state) = app.try_state::<TrayState>() {
        state.in_game.store(in_game, Ordering::SeqCst);
    }
    if let Some(tray) = app.tray_by_id(TRAY_ID) {
        if let Err(e) = tray.set_tooltip(Some(tooltip(in_game))) {
            tracing::debug!("info-bulle de l'icône non mise à jour : {e}");
        }
    }
}

/// Vrai si l'icône est réellement enregistrée dans la zone de notification
/// (icônes cachées comprises).
///
/// `tray_by_id` ne suffit pas : sous Windows, tray-icon 0.23 renvoie Ok même
/// quand l'ajout de l'icône (`Shell_NotifyIconW(NIM_ADD)`) échoue (Explorateur
/// pas encore prêt ou occupé au démarrage, session RDP/RemoteApp) et attend
/// le message `TaskbarCreated` pour réessayer ; Tauri garde alors l'icône.
/// Remettre l'info-bulle (`NIM_MODIFY`) échoue tant qu'elle n'est pas
/// enregistrée, et réussit quand elle est rangée dans les icônes cachées.
fn tray_icon_present<R: Runtime>(app: &AppHandle<R>, in_game: bool) -> bool {
    let Some(tray) = app.tray_by_id(TRAY_ID) else {
        return false;
    };
    match tray.set_tooltip(Some(tooltip(in_game))) {
        Ok(()) => true,
        Err(e) => {
            tracing::warn!(
                "icône absente de la zone de notification ({e}) : fermer la fenêtre quitte le launcher"
            );
            false
        }
    }
}

/// Fermeture de la fenêtre principale (croix, Alt+F4, barre des tâches) :
/// la cache dans la zone de notification si `closeToTray` est actif et que
/// l'icône est bien affichée. Sinon, on laisse faire : la dernière fenêtre
/// fermée, le launcher quitte.
pub fn on_close_requested<R: Runtime>(window: &Window<R>, api: &CloseRequestApi) {
    if window.label() != MAIN_WINDOW {
        return;
    }
    let app = window.app_handle();
    let Some(state) = app.try_state::<TrayState>() else {
        return;
    };
    if !state.close_to_tray.load(Ordering::SeqCst) {
        return;
    }
    // Sans icône visible (création ratée, ou ajout refusé par l'Explorateur),
    // une fenêtre cachée serait introuvable et le launcher impossible à
    // quitter.
    if !tray_icon_present(app, state.in_game.load(Ordering::SeqCst)) {
        return;
    }
    api.prevent_close();
    if let Err(e) = window.hide() {
        tracing::warn!("fenêtre impossible à cacher dans la zone de notification : {e}");
        return;
    }
    if !state.hint_shown.swap(true, Ordering::SeqCst) {
        show_hint(app);
    }
}

/// Première fermeture : notification Windows « Reborn tourne toujours »,
/// puis mémorisation dans les préférences pour ne plus la montrer.
fn show_hint<R: Runtime>(app: &AppHandle<R>) {
    if let Err(e) = app
        .notification()
        .builder()
        .title(HINT_TITLE)
        .body(HINT_BODY)
        .show()
    {
        // Pas mémorisé : elle sera retentée au prochain démarrage.
        tracing::warn!("notification « Reborn tourne toujours » impossible : {e}");
        return;
    }
    tauri::async_runtime::spawn(async {
        if let Err(e) = prefs::update(|p| p.tray_hint_shown = true).await {
            tracing::warn!("préférence trayHintShown non enregistrée : {e}");
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tooltip_follows_game_state() {
        assert_eq!(tooltip(false), "Reborn Launcher");
        assert_eq!(tooltip(true), "Reborn Launcher · en jeu");
    }

    #[test]
    fn tray_state_follows_prefs_but_never_forgets_the_hint() {
        let state = TrayState::new();
        assert!(state.close_to_tray.load(Ordering::SeqCst));
        assert!(!state.hint_shown.load(Ordering::SeqCst));

        state.apply_prefs(&Preferences {
            close_to_tray: false,
            tray_hint_shown: true,
            ..Preferences::default()
        });
        assert!(!state.close_to_tray.load(Ordering::SeqCst));
        assert!(state.hint_shown.load(Ordering::SeqCst));

        state.apply_prefs(&Preferences::default());
        assert!(state.close_to_tray.load(Ordering::SeqCst));
        assert!(state.hint_shown.load(Ordering::SeqCst));
    }
}
