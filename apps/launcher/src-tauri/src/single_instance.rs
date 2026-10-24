//! Instance unique du launcher, sans crate dédiée.
//!
//! Au démarrage, le launcher réserve un port TCP local (127.0.0.1 seulement :
//! rien n'est exposé au réseau, pas d'alerte du pare-feu Windows). Si un
//! Reborn Launcher du même utilisateur le tient déjà, le nouveau lui demande
//! de ramener sa fenêtre (même action que le clic sur l'icône de la zone de
//! notification) puis se ferme aussitôt, sans créer de fenêtre.
//!
//! Protocole : une ligne ASCII dans chaque sens, rien d'autre n'est lu, et
//! rien de ce qui arrive n'est exécuté à part l'ordre « afficher ».
//!   nouveau → existant : `reborn-launcher/1 show <clé>\n`
//!   existant → nouveau : `reborn-launcher/1 ok\n`      (fenêtre ramenée)
//!                        `reborn-launcher/1 other\n`   (autre utilisateur ou autre build)
//!                        `reborn-launcher/1 closing\n` (en train de quitter)
//!
//! La clé (et le port qui en découle) identifie l'utilisateur Windows et le
//! type de build : un `tauri dev` n'est pas avalé par le launcher installé
//! qui tourne dans la zone de notification, et deux sessions Windows du même
//! PC ne se gênent pas.
//!
//! Robustesse : port occupé par un autre programme, réservé par Windows
//! (Hyper-V, WSL…) ou instance qui ne répond pas → on démarre normalement,
//! sans protection, et on le journalise. À la sortie, le port est rendu avant
//! le redémarrage d'une mise à jour (cf [`Instance::shutdown`]) ; le nouveau
//! processus réessaie quelques fois s'il tombe sur une instance qui se ferme.

use sha2::{Digest, Sha256};
use std::io::{ErrorKind, Read, Write};
use std::net::{Ipv4Addr, SocketAddr, TcpListener, TcpStream};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{mpsc, Arc, Mutex, OnceLock, PoisonError};
use std::time::Duration;

const PROTOCOL: &str = "reborn-launcher/1";
/// Plage de ports possibles (le port exact dépend de la clé utilisateur).
const PORT_BASE: u16 = 47_310;
const PORT_SPAN: u16 = 300;
/// Taille maximale d'un message : au-delà, ce n'est pas un Reborn Launcher.
const MAX_LINE: usize = 128;
const CONNECT_TIMEOUT: Duration = Duration::from_millis(500);
const IO_TIMEOUT: Duration = Duration::from_millis(1500);
/// Tentatives face à une instance en train de quitter (redémarrage).
const ATTEMPTS: u32 = 3;
const RETRY_DELAY: Duration = Duration::from_millis(300);
/// Attente maximale de la libération du port à la sortie.
const RELEASE_TIMEOUT: Duration = Duration::from_secs(1);

/// Résultat du démarrage.
pub enum Startup {
    /// Première instance : elle garde le port et écoute les suivantes.
    Primary(Instance),
    /// Un launcher tourne déjà et vient de ramener sa fenêtre : quitter.
    AlreadyRunning,
    /// Pas de protection possible (port indisponible) : démarrage normal.
    Unprotected,
}

/// Message reçu par l'instance principale.
#[derive(Debug, PartialEq, Eq)]
enum Request {
    /// Ramener la fenêtre (même utilisateur, même build).
    Show,
    /// Message valide d'un autre utilisateur ou d'un autre build.
    OtherUser,
    /// Tout le reste : ignoré, connexion fermée sans réponse.
    Invalid,
}

/// Réponse reçue par le nouveau lancement.
#[derive(Debug, PartialEq, Eq)]
enum Reply {
    Shown,
    OtherUser,
    Closing,
    /// Réponse qui ne suit pas le protocole : autre programme sur ce port.
    Foreign,
}

/// Une ligne ASCII complète (terminée par `\n`, `\r` toléré), sans le saut de
/// ligne. `None` si le message est tronqué, contient plusieurs lignes ou des
/// caractères de contrôle.
fn line_of(raw: &[u8]) -> Option<&str> {
    let line = raw.strip_suffix(b"\n")?;
    let line = line.strip_suffix(b"\r").unwrap_or(line);
    if !line.iter().all(|b| b.is_ascii_graphic() || *b == b' ') {
        return None;
    }
    std::str::from_utf8(line).ok()
}

fn parse_request(raw: &[u8], own_key: &str) -> Request {
    let Some(line) = line_of(raw) else {
        return Request::Invalid;
    };
    let mut parts = line.split(' ');
    match (parts.next(), parts.next(), parts.next(), parts.next()) {
        (Some(PROTOCOL), Some("show"), Some(key), None) if !key.is_empty() => {
            if key == own_key {
                Request::Show
            } else {
                Request::OtherUser
            }
        }
        _ => Request::Invalid,
    }
}

fn parse_reply(raw: &[u8]) -> Reply {
    let answer = line_of(raw)
        .and_then(|line| line.strip_prefix(PROTOCOL))
        .and_then(|rest| rest.strip_prefix(' '));
    match answer {
        Some("ok") => Reply::Shown,
        Some("other") => Reply::OtherUser,
        Some("closing") => Reply::Closing,
        _ => Reply::Foreign,
    }
}

/// Clé de l'utilisateur et du type de build (empreinte, rien de lisible).
fn key_for(flavor: &str, data_dir: &str) -> String {
    let digest = Sha256::digest(format!("fr.reborn-rp.launcher|{flavor}|{data_dir}").as_bytes());
    hex::encode(&digest[..8])
}

fn user_key() -> String {
    let flavor = if cfg!(debug_assertions) {
        "dev"
    } else {
        "release"
    };
    // Dossier de données = dossier de l'utilisateur Windows (%APPDATA%).
    let data_dir = crate::launcher::paths::game_dir()
        .map(|p| p.display().to_string())
        .unwrap_or_default();
    key_for(flavor, &data_dir)
}

fn port_for(key: &str) -> u16 {
    let hash = key.bytes().fold(0u32, |acc, b| {
        acc.wrapping_mul(31).wrapping_add(u32::from(b))
    });
    // `hash % PORT_SPAN` < PORT_SPAN : la conversion ne tronque jamais.
    PORT_BASE + (hash % u32::from(PORT_SPAN)) as u16
}

/// Lit une ligne (au plus [`MAX_LINE`] octets, s'arrête au premier `\n`).
fn read_line(stream: &mut TcpStream) -> std::io::Result<Vec<u8>> {
    let mut buf = Vec::with_capacity(64);
    let mut chunk = [0u8; 64];
    while buf.len() < MAX_LINE && !buf.contains(&b'\n') {
        let n = stream.read(&mut chunk)?;
        if n == 0 {
            break;
        }
        buf.extend_from_slice(&chunk[..n]);
    }
    Ok(buf)
}

/// Réserve le port, ou demande à l'instance existante de se montrer.
pub fn acquire() -> Startup {
    let key = user_key();
    let port = port_for(&key);
    let addr = SocketAddr::from((Ipv4Addr::LOCALHOST, port));
    for attempt in 1..=ATTEMPTS {
        match TcpListener::bind(addr) {
            Ok(listener) => return Startup::Primary(Instance::spawn(listener, key, port)),
            Err(e) if e.kind() == ErrorKind::AddrInUse => match ask_to_show(addr, &key) {
                Some(Reply::Shown) => return Startup::AlreadyRunning,
                Some(Reply::OtherUser) => {
                    tracing::warn!(
                        "instance unique : le port local {port} sert un autre Reborn Launcher (autre session Windows ou autre build) : démarrage sans protection"
                    );
                    return Startup::Unprotected;
                }
                Some(Reply::Foreign) => {
                    tracing::warn!(
                        "instance unique : le port local {port} est pris par un autre programme : démarrage sans protection"
                    );
                    return Startup::Unprotected;
                }
                // Instance en train de quitter (redémarrage après mise à jour)
                // ou muette : on retente un peu plus tard.
                Some(Reply::Closing) | None => {
                    if attempt < ATTEMPTS {
                        std::thread::sleep(RETRY_DELAY);
                    }
                }
            },
            Err(e) => {
                tracing::warn!(
                    "instance unique : port local {port} indisponible ({e}) : démarrage sans protection"
                );
                return Startup::Unprotected;
            }
        }
    }
    tracing::warn!(
        "instance unique : le port local {port} reste occupé sans réponse exploitable : démarrage sans protection"
    );
    Startup::Unprotected
}

/// Côté nouveau lancement : envoie l'ordre « afficher » et lit la réponse.
/// `None` = pas de réponse (connexion refusée, délai dépassé, fermée à vide).
fn ask_to_show(addr: SocketAddr, key: &str) -> Option<Reply> {
    let mut stream = TcpStream::connect_timeout(&addr, CONNECT_TIMEOUT).ok()?;
    stream.set_read_timeout(Some(IO_TIMEOUT)).ok()?;
    stream.set_write_timeout(Some(IO_TIMEOUT)).ok()?;
    stream
        .write_all(format!("{PROTOCOL} show {key}\n").as_bytes())
        .ok()?;
    let raw = read_line(&mut stream).ok()?;
    // On ferme en premier (fin de `stream`) : l'attente TIME_WAIT reste de
    // notre côté, jamais sur le port de l'instance principale.
    (!raw.is_empty()).then(|| parse_reply(&raw))
}

/// Instance principale : tient le port et répond aux nouveaux lancements.
pub struct Instance {
    port: u16,
    closing: Arc<AtomicBool>,
    app: Arc<OnceLock<tauri::AppHandle>>,
    /// Prévenu quand le fil d'écoute a rendu le port.
    released: Mutex<Option<mpsc::Receiver<()>>>,
}

impl Instance {
    fn spawn(listener: TcpListener, key: String, port: u16) -> Self {
        let closing = Arc::new(AtomicBool::new(false));
        let app: Arc<OnceLock<tauri::AppHandle>> = Arc::default();
        let (released_tx, released_rx) = mpsc::channel();
        let (thread_closing, thread_app) = (closing.clone(), app.clone());
        let spawned = std::thread::Builder::new()
            .name("reborn-instance".into())
            .spawn(move || {
                for conn in listener.incoming() {
                    if thread_closing.load(Ordering::SeqCst) {
                        break;
                    }
                    match conn {
                        Ok(stream) => serve(stream, &key, &thread_closing, &thread_app),
                        Err(e) => {
                            tracing::debug!("instance unique : connexion entrante refusée ({e})");
                            std::thread::sleep(Duration::from_millis(50));
                        }
                    }
                }
                drop(listener);
                let _ = released_tx.send(());
            });
        match spawned {
            Ok(_) => tracing::info!("instance unique : port local {port} réservé"),
            // La fermeture (et le port) partent avec le fil manqué.
            Err(e) => tracing::warn!("instance unique : écoute impossible ({e}) : sans protection"),
        }
        Self {
            port,
            closing,
            app,
            released: Mutex::new(Some(released_rx)),
        }
    }

    /// Branche l'application : les ordres « afficher » reçus avant sont
    /// ignorés (la fenêtre s'affiche de toute façon au démarrage).
    pub fn attach(&self, app: tauri::AppHandle) {
        let _ = self.app.set(app);
    }

    /// Rend le port (sortie du launcher) : le launcher relancé juste après
    /// une mise à jour doit pouvoir le reprendre. Idempotent, borné à
    /// [`RELEASE_TIMEOUT`].
    pub fn shutdown(&self) {
        if self.closing.swap(true, Ordering::SeqCst) {
            return;
        }
        // Réveille le fil bloqué dans accept() pour qu'il voie le drapeau.
        let _ = TcpStream::connect_timeout(
            &SocketAddr::from((Ipv4Addr::LOCALHOST, self.port)),
            CONNECT_TIMEOUT,
        );
        let released = self
            .released
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .take();
        if let Some(rx) = released {
            if rx.recv_timeout(RELEASE_TIMEOUT).is_ok() {
                tracing::info!("instance unique : port local {} rendu", self.port);
            }
        }
    }
}

/// Côté instance principale : une connexion = une ligne lue, une ligne
/// répondue. Les délais bornent tout client lent ou muet.
fn serve(mut stream: TcpStream, key: &str, closing: &AtomicBool, app: &OnceLock<tauri::AppHandle>) {
    let _ = stream.set_read_timeout(Some(IO_TIMEOUT));
    let _ = stream.set_write_timeout(Some(IO_TIMEOUT));
    let Ok(raw) = read_line(&mut stream) else {
        return;
    };
    let (answer, show) = match parse_request(&raw, key) {
        Request::Invalid => return,
        _ if closing.load(Ordering::SeqCst) => ("closing", false),
        Request::OtherUser => ("other", false),
        Request::Show => ("ok", true),
    };
    let _ = stream.write_all(format!("{PROTOCOL} {answer}\n").as_bytes());
    let _ = stream.flush();
    if show {
        if let Some(app) = app.get() {
            tracing::info!("nouveau lancement du launcher : fenêtre existante ramenée");
            crate::tray::show_main_window(app);
        }
    }
    // Laisse le nouveau lancement fermer en premier (cf `ask_to_show`).
    let _ = stream.read(&mut [0u8; 1]);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_the_exact_show_order_is_accepted() {
        let key = "0123456789abcdef";
        assert_eq!(
            parse_request(b"reborn-launcher/1 show 0123456789abcdef\n", key),
            Request::Show
        );
        assert_eq!(
            parse_request(b"reborn-launcher/1 show 0123456789abcdef\r\n", key),
            Request::Show
        );
        assert_eq!(
            parse_request(b"reborn-launcher/1 show fedcba9876543210\n", key),
            Request::OtherUser
        );
        for raw in [
            &b"reborn-launcher/1 show 0123456789abcdef"[..], // tronqué
            b"reborn-launcher/1 show\n",
            b"reborn-launcher/1 show \n",
            b"reborn-launcher/1 run 0123456789abcdef\n",
            b"reborn-launcher/2 show 0123456789abcdef\n",
            b"reborn-launcher/1 show 0123456789abcdef calc.exe\n",
            b"reborn-launcher/1 show 0123456789abcdef\nreborn-launcher/1 show 0123456789abcdef\n",
            b"reborn-launcher/1 show 0123\x00456789abcdef\n",
            b"GET / HTTP/1.1\r\n",
            b"\xff\xfe\n",
            b"",
        ] {
            assert_eq!(parse_request(raw, key), Request::Invalid, "{raw:?}");
        }
    }

    #[test]
    fn replies_are_recognised_or_foreign() {
        assert_eq!(parse_reply(b"reborn-launcher/1 ok\n"), Reply::Shown);
        assert_eq!(parse_reply(b"reborn-launcher/1 other\n"), Reply::OtherUser);
        assert_eq!(parse_reply(b"reborn-launcher/1 closing\n"), Reply::Closing);
        assert_eq!(parse_reply(b"reborn-launcher/1 ok"), Reply::Foreign);
        assert_eq!(parse_reply(b"reborn-launcher/1 okay\n"), Reply::Foreign);
        assert_eq!(parse_reply(b"HTTP/1.1 400 Bad Request\r\n"), Reply::Foreign);
        assert_eq!(parse_reply(b"SSH-2.0-OpenSSH\r\n"), Reply::Foreign);
    }

    #[test]
    fn key_and_port_depend_on_user_and_build() {
        let release = key_for("release", r"C:\Users\a\AppData\Roaming\RebornRoleplay");
        let dev = key_for("dev", r"C:\Users\a\AppData\Roaming\RebornRoleplay");
        let other = key_for("release", r"C:\Users\b\AppData\Roaming\RebornRoleplay");
        assert_eq!(release.len(), 16);
        assert!(release.bytes().all(|b| b.is_ascii_hexdigit()));
        assert_eq!(
            release,
            key_for("release", r"C:\Users\a\AppData\Roaming\RebornRoleplay")
        );
        assert_ne!(release, dev);
        assert_ne!(release, other);
        for key in [&release, &dev, &other] {
            let port = port_for(key);
            assert!((PORT_BASE..PORT_BASE + PORT_SPAN).contains(&port));
        }
        // La clé passe telle quelle dans le message « afficher ».
        let raw = format!("{PROTOCOL} show {release}\n");
        assert_eq!(parse_request(raw.as_bytes(), &release), Request::Show);
    }

    #[test]
    fn round_trip_over_loopback() {
        // Bout en bout sur un port éphémère : un ordre valide reçoit « ok »,
        // une clé étrangère « other ».
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let addr = listener.local_addr().unwrap();
        let key = key_for("test", "round-trip");
        let server_key = key.clone();
        let server = std::thread::spawn(move || {
            let closing = AtomicBool::new(false);
            let app = OnceLock::new();
            for _ in 0..2 {
                let (stream, _) = listener.accept().unwrap();
                serve(stream, &server_key, &closing, &app);
            }
        });
        assert_eq!(ask_to_show(addr, &key), Some(Reply::Shown));
        assert_eq!(
            ask_to_show(addr, "ffffffffffffffff"),
            Some(Reply::OtherUser)
        );
        server.join().unwrap();
    }
}
