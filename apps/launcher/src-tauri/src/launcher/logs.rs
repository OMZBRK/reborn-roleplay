//! Journal du jeu : capture stdout/stderr de la JVM, tampon circulaire et
//! émission groupée vers le front.
//!
//! Avant : chaque ligne partait en event Tauri (`game:stdout` / `game:stderr`)
//! — des milliers d'IPC pendant le démarrage de Minecraft, et rien de
//! consultable après coup. Maintenant :
//!   - deux pumps lisent stdout/stderr en octets bruts (une ligne non UTF-8,
//!     typiquement un message de la JVM en cp1252, ne coupe plus la lecture —
//!     avant, `next_line()` échouait, la pump s'arrêtait et le tube plein
//!     finissait par bloquer le jeu) et les poussent dans un canal ;
//!   - une tâche unique (le « batcher ») masque les secrets, déduit le niveau
//!     (`LevelClassifier`, un seul état pour les deux flux : la pile d'appels
//!     d'une erreur sort souvent sur l'autre flux que son en-tête),
//!     nourrit l'analyseur de diagnostics, détecte l'ouverture de la fenêtre
//!     Minecraft, puis émet `game:log-batch` au plus toutes les 250 ms et range
//!     les lignes dans un tampon circulaire de 5000 lignes ;
//!   - le front relit l'historique via `launcher_logs_snapshot` et l'exporte
//!     dans un fichier via `launcher_logs_export`.
//!
//! `last-stderr.txt` reste écrit en temps réel (forensique d'un crash très
//! précoce, avant même que log4j soit initialisé).

use serde::Serialize;
use std::collections::VecDeque;
use std::path::PathBuf;
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use std::time::Duration;
use tauri::{AppHandle, Emitter, Runtime, State};
use tokio::io::{AsyncBufReadExt, AsyncRead, AsyncWriteExt, BufReader};
use tokio::process::Child;
use tokio::sync::mpsc;
use tokio::task::JoinHandle;
use tokio::time::MissedTickBehavior;

use super::clock;
use super::diagnostics::LogAnalyzer;
use super::game::{data_dir, GameError, GameState};

/// Nombre de lignes conservées (stdout + stderr confondus).
pub const LOG_CAPACITY: usize = 5000;
/// Cadence maximale des events `game:log-batch`.
const BATCH_INTERVAL: Duration = Duration::from_millis(250);
/// Longueur maximale d'une ligne conservée : borne la mémoire du tampon
/// (5000 × 8 Ko au pire) si un mod déverse une ligne géante.
const MAX_LINE_BYTES: usize = 8 * 1024;

#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum LogStream {
    Out,
    Err,
}

/// Ordonné du moins grave au plus grave (`Debug < Info < Warn < Error`).
#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq, PartialOrd, Ord)]
#[serde(rename_all = "lowercase")]
pub enum LogLevel {
    Debug,
    Info,
    Warn,
    Error,
}

impl LogLevel {
    fn label(self) -> &'static str {
        match self {
            LogLevel::Debug => "DEBUG",
            LogLevel::Info => "INFO",
            LogLevel::Warn => "WARN",
            LogLevel::Error => "ERROR",
        }
    }
}

/// Une ligne du journal, telle qu'envoyée au front (`game:log-batch`,
/// `launcher_logs_snapshot`). `t` = millisecondes Unix de la capture.
#[derive(Debug, Clone, Serialize)]
pub struct LogLine {
    pub t: u64,
    pub stream: LogStream,
    pub level: LogLevel,
    pub text: String,
}

#[derive(Serialize, Clone)]
struct LogBatch<'a> {
    lines: &'a [LogLine],
}

/// Tampon circulaire partagé (partie en cours, ou dernière partie jouée).
#[derive(Default)]
pub struct LogStore {
    lines: Mutex<VecDeque<LogLine>>,
}

impl LogStore {
    fn guard(&self) -> MutexGuard<'_, VecDeque<LogLine>> {
        self.lines.lock().unwrap_or_else(PoisonError::into_inner)
    }

    pub fn clear(&self) {
        self.guard().clear();
    }

    fn extend(&self, batch: impl IntoIterator<Item = LogLine>) {
        let mut lines = self.guard();
        for line in batch {
            if lines.len() >= LOG_CAPACITY {
                lines.pop_front();
            }
            lines.push_back(line);
        }
    }

    pub fn snapshot(&self) -> Vec<LogLine> {
        self.guard().iter().cloned().collect()
    }

    /// Les `n` dernières lignes en texte brut (modal de crash).
    pub fn tail_text(&self, n: usize) -> String {
        let lines = self.guard();
        let start = lines.len().saturating_sub(n);
        lines
            .iter()
            .skip(start)
            .map(|l| l.text.as_str())
            .collect::<Vec<_>>()
            .join("\n")
    }
}

/// Rend le journal complet en texte, avec un en-tête (fichier exporté ou
/// journal de crash).
pub fn render_log_file(lines: &[LogLine], title: &str) -> String {
    let now = clock::now_ms();
    let mut out =
        String::with_capacity(lines.iter().map(|l| l.text.len() + 16).sum::<usize>() + 256);
    out.push_str(&format!(
        "# Reborn Launcher {} — {title}\n# Généré le {} — {} ligne(s), stdout + stderr\n#\n",
        env!("CARGO_PKG_VERSION"),
        clock::format_human(now),
        lines.len()
    ));
    for line in lines {
        let stream = match line.stream {
            LogStream::Out => "out",
            LogStream::Err => "err",
        };
        out.push_str(&format!(
            "[{stream} {:<5}] {}\n",
            line.level.label(),
            line.text
        ));
    }
    out
}

/// Écrit le journal dans `<dossier de données>/logs/<file_name>` et renvoie
/// le chemin.
pub async fn write_log_file(
    lines: &[LogLine],
    file_name: &str,
    title: &str,
) -> Result<PathBuf, GameError> {
    let dir = data_dir()?.join("logs");
    tokio::fs::create_dir_all(&dir)
        .await
        .map_err(|e| GameError::Io {
            message: format!("Création du dossier {} impossible : {e}", dir.display()),
        })?;
    let path = dir.join(file_name);
    tokio::fs::write(&path, render_log_file(lines, title))
        .await
        .map_err(|e| GameError::Io {
            message: format!("Écriture du journal {} impossible : {e}", path.display()),
        })?;
    Ok(path)
}

/// Renvoie le contenu du tampon (partie en cours ou dernière partie).
#[tauri::command]
pub async fn launcher_logs_snapshot(game: State<'_, GameState>) -> Result<Vec<LogLine>, GameError> {
    Ok(game.logs.snapshot())
}

/// Exporte le tampon dans `logs/reborn-AAAA-MM-JJ_HH-MM-SS.log` (heure UTC)
/// et renvoie le chemin du fichier écrit.
#[tauri::command]
pub async fn launcher_logs_export(game: State<'_, GameState>) -> Result<String, GameError> {
    let lines = game.logs.snapshot();
    if lines.is_empty() {
        return Err(GameError::LogsEmpty);
    }
    let name = format!("reborn-{}.log", clock::format_file_stamp(clock::now_ms()));
    let path = write_log_file(&lines, &name, "journal du jeu exporté").await?;
    tracing::info!(
        "journal exporté : {} ({} lignes)",
        path.display(),
        lines.len()
    );
    Ok(path.display().to_string())
}

// ──────────────────────────────────────────────────────
//  Pipeline de capture
// ──────────────────────────────────────────────────────

struct RawLine {
    t: u64,
    stream: LogStream,
    text: String,
}

/// Branche la capture sur stdout/stderr du `Child`. Renvoie le handle du
/// batcher : il se termine quand les deux flux sont fermés (fin du jeu) et
/// que tout a été émis, rangé et écrit dans `stderr_dump`.
///
/// `secrets` : valeurs à masquer (« *** ») si elles apparaissent dans une
/// ligne (jeton Minecraft). `on_window_ready` est appelé une seule fois,
/// juste après l'event `game:window-ready`.
pub fn spawn_pipeline<R, F>(
    app: AppHandle<R>,
    child: &mut Child,
    store: Arc<LogStore>,
    stderr_dump: PathBuf,
    secrets: Vec<String>,
    on_window_ready: F,
) -> JoinHandle<()>
where
    R: Runtime,
    F: FnOnce() + Send + 'static,
{
    // Non borné : un batcher en retard ne doit jamais faire attendre les
    // pumps, sinon c'est la JVM qui bloque sur son tube stdout.
    let (tx, rx) = mpsc::unbounded_channel::<RawLine>();

    if let Some(stdout) = child.stdout.take() {
        tokio::spawn(pump(stdout, LogStream::Out, tx.clone(), None));
    }
    if let Some(stderr) = child.stderr.take() {
        tokio::spawn(pump(stderr, LogStream::Err, tx.clone(), Some(stderr_dump)));
    }
    drop(tx);

    // Les secrets trop courts (placeholder dev « 0 ») masqueraient n'importe quoi.
    let secrets: Vec<String> = secrets.into_iter().filter(|s| s.len() >= 8).collect();
    tokio::spawn(batcher(app, rx, store, secrets, on_window_ready))
}

/// Lit un flux ligne par ligne, en octets bruts, et l'envoie au batcher.
/// Pour stderr, écrit aussi chaque ligne dans `dump` (last-stderr.txt).
async fn pump<S>(
    stream: S,
    kind: LogStream,
    tx: mpsc::UnboundedSender<RawLine>,
    dump: Option<PathBuf>,
) where
    S: AsyncRead + Unpin,
{
    let mut file = match dump {
        Some(path) => match tokio::fs::File::create(&path).await {
            Ok(f) => Some(f),
            Err(e) => {
                tracing::warn!("impossible d'ouvrir {} : {e}", path.display());
                None
            }
        },
        None => None,
    };
    let mut reader = BufReader::new(stream);
    let mut buf = Vec::with_capacity(512);
    loop {
        buf.clear();
        match reader.read_until(b'\n', &mut buf).await {
            Ok(0) => break,
            Ok(_) => {}
            Err(e) => {
                tracing::warn!("lecture du flux {kind:?} du jeu interrompue : {e}");
                break;
            }
        }
        while matches!(buf.last(), Some(b'\n' | b'\r')) {
            buf.pop();
        }
        if let Some(f) = file.as_mut() {
            buf.push(b'\n');
            let _ = f.write_all(&buf).await;
            buf.pop();
        }
        let text = truncate_line(decode_line(&buf));
        let line = RawLine {
            t: clock::now_ms().max(0) as u64,
            stream: kind,
            text,
        };
        if tx.send(line).is_err() {
            break;
        }
    }
    if let Some(mut f) = file {
        let _ = f.flush().await;
    }
}

async fn batcher<R, F>(
    app: AppHandle<R>,
    mut rx: mpsc::UnboundedReceiver<RawLine>,
    store: Arc<LogStore>,
    secrets: Vec<String>,
    on_window_ready: F,
) where
    R: Runtime,
    F: FnOnce() + Send + 'static,
{
    let mut analyzer = LogAnalyzer::new();
    let mut window_ready = Some(on_window_ready);
    // Un seul état de classement pour stdout ET stderr. Il n'a pas besoin de
    // verrou : les deux pumps ne font qu'envoyer dans le même canal, et ce
    // batcher, seul consommateur, en est l'unique propriétaire — il voit les
    // lignes des deux flux dans leur ordre d'arrivée, une à une.
    let mut levels = LevelClassifier::default();
    let mut pending: Vec<LogLine> = Vec::with_capacity(256);
    let mut ticker = tokio::time::interval(BATCH_INTERVAL);
    ticker.set_missed_tick_behavior(MissedTickBehavior::Delay);

    loop {
        tokio::select! {
            raw = rx.recv() => {
                let Some(raw) = raw else { break };
                let mut text = raw.text;
                for secret in &secrets {
                    if text.contains(secret.as_str()) {
                        text = text.replace(secret.as_str(), "***");
                    }
                }
                let level = levels.classify(&text, raw.stream);
                tracing::debug!(target: "game", "{:?}: {text}", raw.stream);

                if let Some(diag) = analyzer.ingest(&text) {
                    tracing::warn!("diagnostic détecté : {} - {}", diag.code, diag.message);
                    let _ = app.emit("game:diagnostic", diag);
                }
                if window_ready.is_some() && is_window_ready_marker(&text) {
                    tracing::info!("fenêtre Minecraft prête ({text})");
                    let _ = app.emit("game:window-ready", serde_json::json!({}));
                    if let Some(cb) = window_ready.take() {
                        cb();
                    }
                }
                pending.push(LogLine { t: raw.t, stream: raw.stream, level, text });
            }
            _ = ticker.tick() => flush(&app, &store, &mut pending),
        }
    }
    flush(&app, &store, &mut pending);
}

fn flush<R: Runtime>(app: &AppHandle<R>, store: &LogStore, pending: &mut Vec<LogLine>) {
    if pending.is_empty() {
        return;
    }
    if pending.len() > LOG_CAPACITY {
        let excess = pending.len() - LOG_CAPACITY;
        pending.drain(..excess);
    }
    // `emit` sérialise immédiatement : on peut ensuite déplacer les lignes
    // dans le tampon sans les cloner.
    let _ = app.emit("game:log-batch", LogBatch { lines: pending });
    store.extend(pending.drain(..));
}

// ──────────────────────────────────────────────────────
//  Analyse d'une ligne
// ──────────────────────────────────────────────────────

/// Marqueurs « la fenêtre Minecraft est ouverte », relevés sur un vrai
/// démarrage 26.2 + Fabric Loader 0.19.3 (latest.log du 13/06) :
///
/// ```text
/// T+0 s  Setting user: …                    (avant la fenêtre)
/// T+2 s  Backend library: LWJGL version …   (fenêtre créée, encore noire)
/// T+4 s  Reloading ResourceManager: …       (écran de chargement affiché)
/// T+7 s  Sound engine started               (fin du chargement audio)
/// ```
///
/// On retient `Reloading ResourceManager:` : c'est le marqueur le plus
/// tardif qui apparaît à TOUS les démarrages (le moteur son, lui, peut ne
/// jamais démarrer sans périphérique audio) et, quand il tombe, la fenêtre
/// affiche déjà l'écran de chargement — réduire le launcher à cet instant ne
/// laisse jamais l'utilisateur devant un écran vide. `Sound engine started`
/// sert de filet si Mojang renomme le premier.
pub fn is_window_ready_marker(line: &str) -> bool {
    line.contains("Reloading ResourceManager:") || line.contains("Sound engine started")
}

/// Première ligne du rapport de crash Minecraft (`Minecraft.crash` l'écrit
/// d'un bloc sur stdout, sans en-tête log4j).
const CRASH_REPORT_MARKER: &str = "---- Minecraft Crash Report ----";

/// Déduit le niveau des lignes du journal, stdout et stderr confondus.
///
/// Seules les lignes qui ouvrent une entrée portent un niveau : en-tête
/// log4j/Fabric (`[main/WARN]`, `[Render thread/ERROR]`, `[INFO]`) ou préfixe
/// de la JVM (`WARNING:`, `Exception in thread`). Les lignes qui suivent
/// (message sur plusieurs lignes, pile d'appels) n'en ont pas, et sortent
/// souvent sur l'autre flux que leur en-tête — d'où un état unique pour les
/// deux flux, qui doit voir les lignes dans leur ordre d'arrivée (il vit dans
/// le batcher, seul consommateur du canal des deux pumps). Règles :
///
/// - ligne avec en-tête : son niveau, retenu pour la suite ;
/// - ligne sans en-tête : le niveau du dernier en-tête vu, quel que soit son
///   flux (avant tout en-tête : stdout → info, stderr → warn) ;
/// - ligne qui ressemble à une exception (`java.lang.Foo: …`, `Caused by:`,
///   `Suppressed:`, `\tat …`, `... N more`) : au moins WARN — elle garde
///   donc le niveau d'un en-tête WARN/ERROR, et une pile isolée, sans
///   contexte, ne se fond plus dans les infos ;
/// - rapport de crash Minecraft, de `---- Minecraft Crash Report ----` à la
///   fin du bloc : ERROR, ligne par ligne, détails système compris.
#[derive(Debug, Default)]
pub struct LevelClassifier {
    /// Niveau du dernier en-tête vu, tous flux confondus.
    header: Option<LogLevel>,
    /// Flux sur lequel un rapport de crash est en cours d'écriture. Le bloc
    /// se ferme sur ce flux seulement (ligne `#@!@# Game crashed!` ou
    /// nouvel en-tête) : une ligne de l'autre flux glissée au milieu ne
    /// l'interrompt pas.
    crash_report: Option<LogStream>,
}

impl LevelClassifier {
    pub fn classify(&mut self, line: &str, stream: LogStream) -> LogLevel {
        let trimmed = line.trim_start();
        if line.contains(CRASH_REPORT_MARKER) {
            self.crash_report = Some(stream);
            self.header = Some(LogLevel::Error);
            return LogLevel::Error;
        }

        let header = header_level(line);
        if self.crash_report == Some(stream) {
            if header.is_none() {
                if is_crash_report_end(trimmed) {
                    self.crash_report = None;
                }
                return LogLevel::Error;
            }
            // Nouvelle entrée log4j sur le flux du rapport : le bloc est fini.
            self.crash_report = None;
        }

        if let Some(level) = header.or_else(|| jvm_prefix_level(trimmed)) {
            self.header = Some(level);
            return level;
        }

        let inherited = self.header.unwrap_or(match stream {
            LogStream::Out => LogLevel::Info,
            LogStream::Err => LogLevel::Warn,
        });
        if is_exception_line(line) {
            inherited.max(LogLevel::Warn)
        } else {
            inherited
        }
    }
}

/// Préfixes de la JVM (et des mods qui écrivent en direct) : ils ouvrent une
/// entrée comme un en-tête log4j.
fn jvm_prefix_level(trimmed: &str) -> Option<LogLevel> {
    const ERROR: [&str; 4] = ["Exception in thread", "ERROR:", "Error:", "FATAL"];
    const WARN: [&str; 2] = ["WARNING:", "Warning:"];
    if ERROR.iter().any(|p| trimmed.starts_with(p)) {
        Some(LogLevel::Error)
    } else if WARN.iter().any(|p| trimmed.starts_with(p)) {
        Some(LogLevel::Warn)
    } else {
        None
    }
}

/// Dernière ligne du rapport de crash (`#@!@# Game crashed! Crash report
/// saved to: …`, ou `#@?@# Game crashed! Crash report could not be saved.`).
fn is_crash_report_end(trimmed: &str) -> bool {
    trimmed.starts_with("#@") && trimmed.contains("Game crashed")
}

/// Ligne d'une exception Java sans en-tête : son nom (`java.lang.Foo: …`),
/// `Caused by:`, `Suppressed:`, une ligne de pile indentée (`\tat …`) ou
/// `... N more`.
fn is_exception_line(line: &str) -> bool {
    let trimmed = line.trim_start();
    let indented = trimmed.len() != line.len();
    (indented && trimmed.starts_with("at "))
        || trimmed.starts_with("Caused by:")
        || trimmed.starts_with("Suppressed:")
        || is_elided_frames(trimmed)
        || is_exception_name(trimmed)
}

/// `^[\w.$]+(Exception|Error|Throwable)(: |$)`, sans dépendre d'un moteur
/// d'expressions régulières : le nom (lettres, chiffres, `_`, `.`, `$`)
/// s'arrête au premier autre caractère, qui doit ouvrir `: ` ou finir la
/// ligne.
fn is_exception_name(text: &str) -> bool {
    let end = text
        .find(|c: char| !(c.is_alphanumeric() || matches!(c, '_' | '.' | '$')))
        .unwrap_or(text.len());
    let (name, rest) = text.split_at(end);
    (rest.is_empty() || rest.starts_with(": "))
        && ["Exception", "Error", "Throwable"]
            .iter()
            .any(|suffix| name.len() > suffix.len() && name.ends_with(suffix))
}

/// `... 7 more` (pile Java) ou `... 7 common frames omitted` (logback).
fn is_elided_frames(trimmed: &str) -> bool {
    let Some(rest) = trimmed.strip_prefix("... ") else {
        return false;
    };
    let digits = rest.len() - rest.trim_start_matches(|c: char| c.is_ascii_digit()).len();
    let tail = &rest[digits..];
    digits > 0 && (tail == " more" || tail == " common frames omitted")
}

/// Cherche un marqueur de niveau dans l'en-tête de la ligne seulement (une
/// ligne INFO qui cite « ERROR » plus loin reste INFO). Prend le marqueur le
/// plus à gauche.
fn header_level(line: &str) -> Option<LogLevel> {
    const TAGS: [(&str, LogLevel); 14] = [
        ("/FATAL]", LogLevel::Error),
        ("/ERROR]", LogLevel::Error),
        ("/WARN]", LogLevel::Warn),
        ("/INFO]", LogLevel::Info),
        ("/DEBUG]", LogLevel::Debug),
        ("/TRACE]", LogLevel::Debug),
        ("[FATAL]", LogLevel::Error),
        ("[ERROR]", LogLevel::Error),
        ("[WARN]", LogLevel::Warn),
        ("[WARNING]", LogLevel::Warn),
        ("[INFO]", LogLevel::Info),
        ("[DEBUG]", LogLevel::Debug),
        ("[TRACE]", LogLevel::Debug),
        ("[SEVERE]", LogLevel::Error),
    ];
    let mut end = line.len().min(160);
    while !line.is_char_boundary(end) {
        end -= 1;
    }
    let head = &line[..end];
    TAGS.iter()
        .filter_map(|(tag, level)| head.find(tag).map(|pos| (pos, *level)))
        .min_by_key(|(pos, _)| *pos)
        .map(|(_, level)| level)
}

/// Décode une ligne : UTF-8 si valide (log4j écrit en UTF-8), sinon
/// Windows-1252 (messages de la JVM Windows en français, ex. « Erreur :
/// impossible de trouver ou de charger la classe principale »).
fn decode_line(bytes: &[u8]) -> String {
    match std::str::from_utf8(bytes) {
        Ok(s) => s.to_owned(),
        Err(_) => bytes.iter().map(|&b| cp1252_char(b)).collect(),
    }
}

fn cp1252_char(b: u8) -> char {
    const HIGH: [char; 32] = [
        '€', '\u{81}', '‚', 'ƒ', '„', '…', '†', '‡', 'ˆ', '‰', 'Š', '‹', 'Œ', '\u{8d}', 'Ž',
        '\u{8f}', '\u{90}', '‘', '’', '“', '”', '•', '–', '—', '˜', '™', 'š', '›', 'œ', '\u{9d}',
        'ž', 'Ÿ',
    ];
    match b {
        0x80..=0x9f => HIGH[usize::from(b - 0x80)],
        _ => char::from(b),
    }
}

fn truncate_line(mut text: String) -> String {
    if text.len() > MAX_LINE_BYTES {
        let mut end = MAX_LINE_BYTES;
        while !text.is_char_boundary(end) {
            end -= 1;
        }
        text.truncate(end);
        text.push_str(" […ligne tronquée]");
    }
    text
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn window_ready_marker_on_real_26_2_log() {
        // Extrait réel (26.2 + Fabric 0.19.3), dans l'ordre d'apparition.
        let log = [
            "[16:38:36] [main/INFO] (FabricLoader/GameProvider) Loading Minecraft 26.2 with Fabric Loader 0.19.3",
            "[16:38:50] [Render thread/INFO] (Minecraft) Setting user: Player902",
            "[16:38:52] [Render thread/INFO] (Minecraft) Backend library: LWJGL version 3.4.1-snapshot",
            "[16:38:54] [Render thread/INFO] (Minecraft) Reloading ResourceManager: vanilla, fabric-api, reborn-hud",
            "[16:38:57] [Render thread/INFO] (Minecraft) Sound engine started",
        ];
        let first = log.iter().position(|l| is_window_ready_marker(l));
        assert_eq!(
            first,
            Some(3),
            "doit attendre l'écran de chargement, pas la création du contexte"
        );
        // Format console vanilla (sans le nom du logger).
        assert!(is_window_ready_marker(
            "[19:02:11] [Render thread/INFO]: Reloading ResourceManager: vanilla"
        ));
        assert!(!is_window_ready_marker(
            "[19:02:10] [Render thread/INFO]: Setting user: Steve"
        ));
    }

    const OUT: LogStream = LogStream::Out;
    const ERR: LogStream = LogStream::Err;

    /// Niveau d'une ligne seule, sans contexte.
    fn level_of(line: &str, stream: LogStream) -> LogLevel {
        LevelClassifier::default().classify(line, stream)
    }

    /// Classe une suite de lignes avec un seul état, comme le batcher.
    fn classify_all(lines: &[(LogStream, &str)]) -> Vec<LogLevel> {
        let mut levels = LevelClassifier::default();
        lines
            .iter()
            .map(|(stream, line)| levels.classify(line, *stream))
            .collect()
    }

    #[test]
    fn levels_from_headers() {
        assert_eq!(
            level_of("[16:38:36] [main/INFO] (FabricLoader) Loading", OUT),
            LogLevel::Info
        );
        assert_eq!(
            level_of("[12:00:00] [Render thread/WARN]: Missing sound", OUT),
            LogLevel::Warn
        );
        assert_eq!(
            level_of("[12:00:00] [Server thread/ERROR]: boom", OUT),
            LogLevel::Error
        );
        assert_eq!(
            level_of("[12:00:00] [main/DEBUG]: détail", OUT),
            LogLevel::Debug
        );
        // Le mot ERROR dans le corps d'une ligne INFO ne change pas son niveau.
        assert_eq!(
            level_of("[12:00:00] [main/INFO]: compteur [ERROR] = 0", OUT),
            LogLevel::Info
        );
    }

    #[test]
    fn levels_without_header() {
        assert_eq!(
            level_of("Exception in thread \"main\" java.lang.Foo", ERR),
            LogLevel::Error
        );
        assert_eq!(
            level_of("WARNING: A restricted method has been called", ERR),
            LogLevel::Warn
        );
        // Aucun en-tête encore vu : défaut par flux.
        assert_eq!(level_of("texte quelconque", ERR), LogLevel::Warn);
        assert_eq!(level_of("texte quelconque", OUT), LogLevel::Info);
    }

    #[test]
    fn error_header_on_stderr_then_exception_on_stdout() {
        let levels = classify_all(&[
            (ERR, "[12:00:00] [Render thread/ERROR] (Minecraft) Reported exception thrown!"),
            (OUT, "java.lang.NullPointerException: Cannot read field \"pivotX\" because \"part\" is null"),
            (OUT, "\tat net.minecraft.client.model.Model.renderToBuffer(Model.java:62)"),
            (ERR, "\tat net.minecraft.client.Minecraft.run(Minecraft.java:912)"),
        ]);
        assert_eq!(levels, vec![LogLevel::Error; 4]);
    }

    #[test]
    fn full_stack_trace_keeps_its_header_level() {
        let lines = [
            (OUT, "[12:00:00] [Render thread/ERROR] (Minecraft) Reported exception thrown!"),
            (OUT, "net.minecraft.ReportedException: Rendering entity in world"),
            (OUT, "\tat net.minecraft.client.renderer.LevelRenderer.renderEntity(LevelRenderer.java:1544)"),
            (OUT, "\tat net.minecraft.client.main.Main.main(Main.java:266) ~[client.jar:?]"),
            (OUT, "Caused by: java.lang.NullPointerException: part is null"),
            (OUT, "\tat traben.entity_model_features.models.parts.EMFModelPartRoot.render(EMFModelPartRoot.java:214)"),
            (OUT, "\t... 7 more"),
            (OUT, "\tSuppressed: java.lang.IllegalStateException: closed"),
            (OUT, "\t\tat net.minecraft.util.Closer.close(Closer.java:12)"),
            (OUT, "\t\t... 3 more"),
        ];
        assert_eq!(classify_all(&lines), vec![LogLevel::Error; lines.len()]);

        // Après un WARN, la pile reste WARN ; le prochain en-tête reprend la main.
        let levels = classify_all(&[
            (OUT, "[12:00:01] [main/WARN] (Fabric) Mixin apply failed"),
            (ERR, "java.lang.ClassNotFoundException: com.example.Missing"),
            (
                ERR,
                "\tat java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:526)",
            ),
            (
                OUT,
                "[12:00:02] [main/INFO] (Minecraft) Setting user: Steve",
            ),
            (ERR, "texte quelconque"),
        ]);
        assert_eq!(
            levels,
            vec![
                LogLevel::Warn,
                LogLevel::Warn,
                LogLevel::Warn,
                LogLevel::Info,
                LogLevel::Info,
            ]
        );
    }

    #[test]
    fn line_without_header_inherits_last_header_across_streams() {
        let levels = classify_all(&[
            (
                OUT,
                "[12:00:00] [main/ERROR] (Fabric) Incompatible mods found!",
            ),
            (ERR, "\t - reborn-hud requires fabric-api >= 0.156.0"),
            (OUT, "[12:00:01] [main/INFO] (Minecraft) Stopping!"),
            (ERR, "suite du message"),
        ]);
        assert_eq!(
            levels,
            vec![
                LogLevel::Error,
                LogLevel::Error,
                LogLevel::Info,
                LogLevel::Info
            ]
        );
    }

    #[test]
    fn isolated_exception_is_at_least_warn() {
        // Sans aucun contexte, sur l'un ou l'autre flux.
        assert_eq!(
            level_of("java.lang.IllegalStateException: Not initialized", OUT),
            LogLevel::Warn
        );
        assert_eq!(level_of("java.lang.OutOfMemoryError", OUT), LogLevel::Warn);
        assert_eq!(level_of("\tat a.b.C.d(C.java:1)", OUT), LogLevel::Warn);
        assert_eq!(
            level_of("Caused by: java.io.IOException", OUT),
            LogLevel::Warn
        );
        // Après un en-tête INFO aussi : une pile ne passe jamais pour une info.
        let levels = classify_all(&[
            (OUT, "[12:00:00] [main/INFO] (Mod) chargement"),
            (OUT, "java.io.FileNotFoundException: config/mod.json"),
            (OUT, "\tat a.b.C.d(C.java:1)"),
            (OUT, "ligne ordinaire"),
        ]);
        assert_eq!(
            levels,
            vec![
                LogLevel::Info,
                LogLevel::Warn,
                LogLevel::Warn,
                LogLevel::Info
            ]
        );
    }

    #[test]
    fn exception_name_pattern() {
        for yes in [
            "java.lang.NullPointerException: Cannot invoke",
            "net.minecraft.ReportedException: Rendering overlay",
            "java.lang.OutOfMemoryError",
            "java.lang.Throwable",
            "com.example.Outer$InnerException: boom",
            "  java.lang.StackOverflowError",
        ] {
            assert!(is_exception_line(yes), "devrait être une exception : {yes}");
        }
        for no in [
            "Error: Could not find or load main class",
            "Exception",
            "java.lang.NullPointerException:sans espace",
            "ExceptionHandler started",
            "Loading 42 mods: java.lang.Exception",
            "at a.b.C.d(C.java:1)",
            "... more to come",
            "\t... 7 more frames here",
        ] {
            assert!(
                !is_exception_line(no),
                "ne devrait pas être une exception : {no}"
            );
        }
        assert!(is_exception_line("\t... 12 common frames omitted"));
    }

    #[test]
    fn minecraft_crash_report_is_error() {
        let report = [
            "---- Minecraft Crash Report ----",
            "// Oops.",
            "",
            "Time: 2026-10-24 18:02:11",
            "Description: Rendering entity in world",
            "",
            "java.lang.NullPointerException: part is null",
            "\tat net.minecraft.client.model.Model.renderToBuffer(Model.java:62)",
            "",
            "A detailed walkthrough of the error, its code path and all known details is as follows:",
            "---------------------------------------------------------------------------------------",
            "",
            "-- Head --",
            "Thread: Render thread",
            "Stacktrace:",
            "\tat net.minecraft.client.model.Model.renderToBuffer(Model.java:62)",
            "",
            "-- System Details --",
            "Details:",
            "\tMinecraft Version: 26.2",
            "\tJava Version: 25.0.1, Microsoft",
            "\tIs Modded: Definitely; Client brand changed to 'fabric'",
            "",
            "#@!@# Game crashed! Crash report saved to: #@!@# C:\\Reborn\\crash-reports\\crash-client.txt",
        ];
        let mut levels = LevelClassifier::default();
        // Contexte INFO avant le rapport : il ne doit pas déteindre dessus.
        assert_eq!(
            levels.classify(
                "[18:02:10] [Render thread/INFO] (Minecraft) Connecting",
                OUT
            ),
            LogLevel::Info
        );
        for (i, line) in report.iter().enumerate() {
            assert_eq!(
                levels.classify(line, OUT),
                LogLevel::Error,
                "ligne {i} : {line:?}"
            );
            // Une ligne de l'autre flux au milieu du rapport ne le coupe pas.
            if i == 10 {
                assert_eq!(
                    levels.classify("[18:02:11] [Sound Library Loader/INFO]: stopping", ERR),
                    LogLevel::Info
                );
            }
        }
        // Le bloc est clos : le prochain en-tête reprend la main.
        assert_eq!(
            levels.classify("[18:02:12] [Render thread/INFO] (Minecraft) Stopping!", OUT),
            LogLevel::Info
        );
        assert_eq!(levels.classify("ligne ordinaire", OUT), LogLevel::Info);

        // Un nouvel en-tête sur le flux du rapport le clôt aussi.
        let mut levels = LevelClassifier::default();
        levels.classify(report[0], OUT);
        assert_eq!(
            levels.classify("Time: 2026-10-24 18:02:11", OUT),
            LogLevel::Error
        );
        assert_eq!(
            levels.classify("[18:02:12] [main/INFO]: Stopping!", OUT),
            LogLevel::Info
        );
        assert_eq!(levels.classify("Details:", OUT), LogLevel::Info);
    }

    #[test]
    fn decodes_cp1252_fallback() {
        // « Erreur : impossible de trouver » avec un « é » en cp1252 (0xE9) et un « € » (0x80).
        let bytes = b"classe introuv\xe9e \x80";
        assert_eq!(decode_line(bytes), "classe introuvée €");
        assert_eq!(decode_line("déjà UTF-8".as_bytes()), "déjà UTF-8");
    }

    #[test]
    fn ring_buffer_keeps_last_lines() {
        let store = LogStore::default();
        let line = |i: usize| LogLine {
            t: i as u64,
            stream: LogStream::Out,
            level: LogLevel::Info,
            text: format!("ligne {i}"),
        };
        store.extend((0..LOG_CAPACITY + 10).map(line));
        let snap = store.snapshot();
        assert_eq!(snap.len(), LOG_CAPACITY);
        assert_eq!(snap[0].text, "ligne 10");
        assert_eq!(
            store.tail_text(2),
            format!("ligne {}\nligne {}", LOG_CAPACITY + 8, LOG_CAPACITY + 9)
        );
    }

    #[test]
    fn long_lines_are_truncated_on_char_boundary() {
        let long = "é".repeat(MAX_LINE_BYTES);
        let out = truncate_line(long);
        assert!(out.ends_with("[…ligne tronquée]"));
        assert!(out.len() <= MAX_LINE_BYTES + 32);
    }
}
