//! FS watcher anti-tampering pendant que la JVM tourne.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §4.4.
//!
//! Pendant qu'une instance Minecraft tourne, on surveille `mods/` et
//! `config/` pour detecter toute modification. Si quelque chose change,
//! on tue la JVM, on emet un event "integrity:tampering" et on remonte
//! l'incident a l'API (`/v1/incidents`) pour audit.

pub mod watcher;

pub use watcher::{spawn_watcher, TamperingEvent, WatcherHandle};
