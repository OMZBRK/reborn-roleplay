//! Discord Rich Presence côté launcher.
//!
//! Pendant qu'une partie tourne, on publie un statut Discord ("Joue à
//! Reborn Roleplay — En jeu depuis 12 min") visible par les amis du joueur.
//! L'IPC Discord est un socket Unix (Linux/macOS) ou named pipe (Windows)
//! exposé par le client Discord local — pas d'API réseau, donc 0 latence et
//! pas besoin de clés OAuth.
//!
//! Configuration : `DISCORD_RICH_PRESENCE_CLIENT_ID` dans `.env` (ID de
//! l'application Discord créée sur le Developer Portal — c'est lui qui pose
//! le nom "Reborn Roleplay" et l'icône large/petite). Si absent, le module
//! ne fait rien (silent no-op) pour permettre dev sans setup.
//!
//! Le module opère en best-effort : aucune erreur ne remonte au caller, on
//! log juste en warn. La présence Discord est cosmetic — elle ne doit pas
//! bloquer le lancement du jeu si le client Discord n'est pas ouvert.

use discord_rich_presence::{
    activity::{Activity, Assets, Timestamps},
    DiscordIpc, DiscordIpcClient,
};
use std::sync::Arc;
use tokio::sync::Mutex;

// État partagé du client RPC (un seul client par instance launcher).
// Wrappé dans Arc<Mutex<>> pour pouvoir le passer dans des tâches spawn et
// le clear depuis le exit handler.
pub struct DiscordRpcState {
    inner: Arc<Mutex<Option<DiscordIpcClient>>>,
}

impl DiscordRpcState {
    pub fn new() -> Self {
        Self {
            inner: Arc::new(Mutex::new(None)),
        }
    }

    /// Tente de connecter au client Discord local et publie l'activité
    /// "En jeu sur Reborn". Best-effort : si le client Discord n'est pas
    /// ouvert ou si le client_id env n'est pas configuré, log et continue.
    pub async fn start_in_game(&self, mc_version: &str) {
        let Some(client_id) = std::env::var("DISCORD_RICH_PRESENCE_CLIENT_ID")
            .ok()
            .filter(|s| !s.trim().is_empty())
        else {
            tracing::debug!(
                "DISCORD_RICH_PRESENCE_CLIENT_ID non configure — Rich Presence desactivee"
            );
            return;
        };

        let inner = self.inner.clone();
        let mc_version = mc_version.to_string();
        // L'IPC Discord est bloquant — on pousse sur spawn_blocking pour ne
        // pas saturer le runtime tokio principal.
        tokio::task::spawn_blocking(move || {
            let mut client = match DiscordIpcClient::new(&client_id) {
                Ok(c) => c,
                Err(e) => {
                    tracing::warn!("Discord RPC client init echec : {e}");
                    return;
                }
            };

            if let Err(e) = client.connect() {
                tracing::warn!("Discord RPC connect echec : {e}");
                return;
            }

            // Timestamp now() = "depuis quand le user joue", Discord affichera
            // "elapsed 12 min" automatiquement.
            let started = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_secs() as i64)
                .unwrap_or(0);

            // Les Strings doivent vivre tout le temps de l'activity (les
            // builders prennent &str et ne clone pas). On les garde en
            // bindings nommes pour que leur duree de vie depasse le set.
            let small_text = format!("Minecraft {mc_version}");

            let timestamps = Timestamps::new().start(started);
            let assets = Assets::new()
                // Les keys "logo" / "play" doivent matcher des images
                // uploadees dans le Discord Developer Portal sous l'onglet
                // Rich Presence > Art Assets. Si absentes, Discord affiche
                // juste l'icone par defaut de l'app (pas de crash).
                .large_image("logo")
                .large_text("Reborn Roleplay")
                .small_image("play")
                .small_text(&small_text);

            let activity = Activity::new()
                .state("En jeu")
                .details("Reborn Roleplay — RP shinobi")
                .timestamps(timestamps)
                .assets(assets);

            if let Err(e) = client.set_activity(activity) {
                tracing::warn!("Discord RPC set_activity echec : {e}");
                let _ = client.close();
                return;
            }

            // On stocke le client connecte ; il sera close par stop().
            // On utilise blocking_lock car on est deja dans un thread
            // bloquant (spawn_blocking) — pas de runtime tokio dispo ici.
            let mut guard = inner.blocking_lock();
            *guard = Some(client);
            tracing::info!("Discord RPC actif : Reborn Roleplay (MC {mc_version})");
        });
    }

    /// Ferme la connexion Discord IPC et libère le client. Appelé quand
    /// le jeu se termine (game:exited). No-op si aucun client n'est actif.
    pub async fn stop(&self) {
        let inner = self.inner.clone();
        tokio::task::spawn_blocking(move || {
            let mut guard = inner.blocking_lock();
            if let Some(mut client) = guard.take() {
                let _ = client.clear_activity();
                let _ = client.close();
                tracing::info!("Discord RPC ferme");
            }
        });
    }
}

impl Default for DiscordRpcState {
    fn default() -> Self {
        Self::new()
    }
}
