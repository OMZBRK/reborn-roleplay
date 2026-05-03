//! FS watcher autour du crate `notify`.
//!
//! Architecture :
//! - Thread dedie qui ecoute les events `notify`
//! - Filtre : on ignore Modify::Metadata (mtime mis a jour par le DL hash check par exemple),
//!   on ne reagit que sur Create / Modify::Data / Remove
//! - Sur event suspect : envoi sur `tx` channel + emit Tauri event sur l'AppHandle
//! - L'appelant (launcher::game) est responsable de tuer la JVM en reaction

use notify::{
    event::{ModifyKind, RemoveKind},
    EventKind, RecommendedWatcher, RecursiveMode, Watcher,
};
use serde::Serialize;
use std::path::{Path, PathBuf};
use std::sync::mpsc;
use std::thread::JoinHandle;
use tauri::{AppHandle, Emitter, Runtime};

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TamperingEvent {
    pub kind: TamperingKind,
    pub paths: Vec<String>,
}

#[derive(Debug, Clone, Copy, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum TamperingKind {
    /// Nouveau fichier detecte dans le repertoire surveille.
    FileAdded,
    /// Fichier existant modifie (donnees, pas juste mtime).
    FileModified,
    /// Fichier supprime.
    FileRemoved,
}

#[derive(Debug)]
pub struct WatcherHandle {
    /// On garde le watcher vivant tant que ce handle existe : son drop
    /// stoppe le thread `notify`.
    _watcher: RecommendedWatcher,
    /// Thread de pump qui transforme `notify` events en `TamperingEvent` Tauri.
    _pump: Option<JoinHandle<()>>,
}

/// Lance un watcher recursif sur les chemins fournis. Retourne un handle a
/// drop quand la JVM se termine.
///
/// Chaque tampering detecte declenche :
/// 1. l'event Tauri `integrity:tampering` (UI peut afficher une alerte)
/// 2. l'envoi d'un message sur `notify_tx` (le code de spawn JVM peut killer)
pub fn spawn_watcher<R: Runtime>(
    app: AppHandle<R>,
    paths: Vec<PathBuf>,
    notify_tx: mpsc::Sender<TamperingEvent>,
) -> notify::Result<WatcherHandle> {
    let (tx, rx) = mpsc::channel::<notify::Result<notify::Event>>();
    let mut watcher = notify::recommended_watcher(move |res| {
        let _ = tx.send(res);
    })?;

    for path in &paths {
        if path.exists() {
            watcher.watch(path, RecursiveMode::Recursive)?;
            tracing::info!("FS watcher armed on {}", path.display());
        }
    }

    let pump = std::thread::spawn(move || {
        while let Ok(event_res) = rx.recv() {
            let Ok(event) = event_res else { continue };
            let Some(kind) = classify(&event.kind) else {
                continue;
            };
            if event.paths.is_empty() {
                continue;
            }
            // Filtre : on ignore les events sur les fichiers `.part` (artefacts
            // de notre propre downloader manifest).
            if event
                .paths
                .iter()
                .all(|p| p.extension().and_then(|s| s.to_str()) == Some("part"))
            {
                continue;
            }
            let payload = TamperingEvent {
                kind,
                paths: event
                    .paths
                    .iter()
                    .map(|p| p.display().to_string())
                    .collect(),
            };
            tracing::warn!(
                "tampering detecte ({:?}) sur {} fichier(s)",
                payload.kind,
                payload.paths.len()
            );
            let _ = app.emit("integrity:tampering", payload.clone());
            let _ = notify_tx.send(payload);
        }
    });

    Ok(WatcherHandle {
        _watcher: watcher,
        _pump: Some(pump),
    })
}

fn classify(kind: &EventKind) -> Option<TamperingKind> {
    match kind {
        EventKind::Create(_) => Some(TamperingKind::FileAdded),
        EventKind::Modify(ModifyKind::Data(_)) => Some(TamperingKind::FileModified),
        EventKind::Modify(ModifyKind::Name(_)) => Some(TamperingKind::FileModified),
        EventKind::Remove(RemoveKind::File) | EventKind::Remove(RemoveKind::Folder) => {
            Some(TamperingKind::FileRemoved)
        }
        // ModifyKind::Metadata, Access(*) → on ignore
        _ => None,
    }
}

#[allow(dead_code)]
fn paths_under(root: &Path) -> bool {
    root.is_dir()
}
