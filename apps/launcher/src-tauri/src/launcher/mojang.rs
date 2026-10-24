//! Accès aux métadonnées Mojang + socle de téléchargement partagé.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §8.1 étapes [4]/[5] ; détails
//! dans docs/technique/LAUNCHER_JVM.md §3.
//!
//! Ce module porte aussi les briques communes aux étapes du lancement
//! (assets, libraries, JRE, Fabric) :
//! - [`download_file`] : téléchargement en streaming vérifié SHA-1, écrit en
//!   `.part` puis renommé, avec retries sur les erreurs réseau transitoires ;
//! - les **témoins de vérification** (`<game_dir>/.reborn-verify/<ensemble>.json`)
//!   qui évitent de relire et re-hacher ~5000 fichiers à chaque « Jouer » ;
//! - la mise en forme française des erreurs réseau et disque
//!   ([`describe_http_error`], [`describe_io_error`]).
//!
//! La « version json » est mise en cache sous `versions/<id>/<id>.json`.

use futures::stream::{self, StreamExt};
use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};
use std::collections::HashMap;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::Duration;
use tokio::fs;
use tokio::io::AsyncWriteExt;

use super::runtime;

const VERSION_MANIFEST_V2: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

/// Délai maximal des requêtes de métadonnées (petits catalogues JSON). Au-delà,
/// on se rabat sur le cache local quand il existe plutôt que de figer l'UI.
pub(crate) const METADATA_TIMEOUT: Duration = Duration::from_secs(15);

/// Tentatives par fichier (erreurs réseau transitoires et disque uniquement).
const DOWNLOAD_ATTEMPTS: u32 = 4;

/// Dossier des témoins de vérification, relatif au game dir.
const STAMP_DIR: &str = ".reborn-verify";

/// Taille des blocs de lecture pour hacher les fichiers déjà présents.
const HASH_CHUNK: usize = 256 * 1024;

/// Tampon d'écriture des téléchargements (les chunks reqwest font ~8-16 Ko).
const WRITE_BUFFER: usize = 64 * 1024;

#[derive(Debug, thiserror::Error)]
pub enum MojangError {
    /// Requête de métadonnées (catalogue de versions, version json…).
    #[error("{}", describe_http_error(.0))]
    Http(#[from] reqwest::Error),
    /// Téléchargement d'un fichier précis, une fois les retries épuisés.
    #[error(
        "Téléchargement de « {file} » impossible{} : {}",
        attempts_note(.attempts),
        describe_http_error(.source)
    )]
    Download {
        file: String,
        attempts: u32,
        source: reqwest::Error,
    },
    #[error("{}", describe_io_error(.path, .source))]
    Io {
        path: PathBuf,
        source: std::io::Error,
    },
    #[error(
        "La version Minecraft « {0} » est absente du catalogue Mojang — vérifie la version \
         configurée (REBORN_MC_VERSION) ou mets à jour le launcher."
    )]
    UnknownVersion(String),
    #[error(
        "Minecraft {minecraft} exige Java {required} (composant « {component} ») mais le \
         launcher fournit Java {provided} — mets à jour le launcher."
    )]
    JavaTooOld {
        minecraft: String,
        component: String,
        required: u32,
        provided: u32,
    },
    #[error(
        "Fichier « {what} » corrompu : SHA-1 attendu {}…, obtenu {}…. Un antivirus ou un proxy \
         altère peut-être les téléchargements — réessaie, puis lance « Réparer » si ça persiste.",
        short_hash(.expected),
        short_hash(.actual)
    )]
    HashMismatch {
        what: String,
        expected: String,
        actual: String,
    },
    #[error("Réponse inattendue de {origin} : {detail} — réessaie plus tard ou mets à jour le launcher.")]
    Schema { origin: String, detail: String },
    #[error("Tâche de téléchargement interrompue ({0}) — relance le jeu.")]
    Internal(String),
}

impl MojangError {
    /// Vrai si la cause est réseau (requête ou téléchargement) : c'est le seul
    /// cas où « vérifie ta connexion » est une consigne utile.
    pub(crate) fn is_network(&self) -> bool {
        matches!(self, Self::Http(_) | Self::Download { .. })
    }
}

// ──────────────────────────────────────────────────────
// Messages d'erreur (réseau / disque) en français
// ──────────────────────────────────────────────────────

/// Décrit une erreur reqwest pour l'utilisateur : hôte (jamais l'URL complète,
/// qui peut porter des paramètres signés), code HTTP, cause racine, et quoi faire.
pub(crate) fn describe_http_error(e: &reqwest::Error) -> String {
    let host = e
        .url()
        .and_then(|u| u.host_str())
        .unwrap_or("le serveur")
        .to_string();
    if let Some(status) = e.status() {
        let code = status.as_u16();
        let reason = status.canonical_reason().unwrap_or("");
        return match code {
            401 | 403 => format!("{host} refuse l'accès (HTTP {code} {reason})"),
            404 | 410 => format!(
                "{host} répond HTTP {code} (fichier introuvable) — la ressource a peut-être été \
                 déplacée ; mets à jour le launcher si ça persiste"
            ),
            429 => format!(
                "{host} limite les requêtes (HTTP 429) — patiente quelques minutes puis relance"
            ),
            500..=599 => format!(
                "{host} est en panne (HTTP {code} {reason}) — réessaie dans quelques minutes"
            ),
            _ => format!("{host} répond HTTP {code} {reason}"),
        };
    }
    let cause = root_cause(e);
    if e.is_timeout() {
        format!("délai dépassé en contactant {host}{cause} — vérifie ta connexion internet")
    } else if e.is_connect() {
        format!(
            "connexion impossible à {host}{cause} — vérifie ta connexion internet, ton pare-feu \
             ou ton antivirus"
        )
    } else if e.is_body() || e.is_decode() {
        format!("réponse de {host} interrompue ou illisible{cause} — vérifie ta connexion internet")
    } else {
        format!("erreur réseau avec {host}{cause}")
    }
}

/// Cause racine d'une erreur reqwest (« (connexion réinitialisée…) »), sans
/// l'URL que le `Display` de premier niveau inclut. Vide si inconnue.
fn root_cause(e: &reqwest::Error) -> String {
    let mut current: &dyn std::error::Error = e;
    let mut deepest: Option<&dyn std::error::Error> = None;
    while let Some(source) = current.source() {
        deepest = Some(source);
        current = source;
    }
    deepest
        .map(|s| s.to_string())
        .filter(|s| !s.trim().is_empty())
        .map(|s| format!(" ({s})"))
        .unwrap_or_default()
}

/// Décrit une erreur disque : chemin complet, raison OS (localisée par Windows)
/// et piste de résolution pour les cas fréquents.
pub(crate) fn describe_io_error(path: &Path, e: &std::io::Error) -> String {
    use std::io::ErrorKind;
    let sharing_violation = cfg!(windows) && matches!(e.raw_os_error(), Some(32) | Some(33));
    let hint = if sharing_violation {
        " — le fichier est utilisé par un autre programme (le jeu est peut-être encore ouvert)"
    } else {
        match e.kind() {
            ErrorKind::PermissionDenied => {
                " — un antivirus, un dossier en lecture seule ou un programme qui garde le fichier \
                 ouvert bloque l'accès"
            }
            ErrorKind::StorageFull => " — le disque est plein : libère de l'espace puis relance",
            _ => "",
        }
    };
    format!("Impossible d'accéder à « {} » : {e}{hint}", path.display())
}

/// Début d'une empreinte (8 caractères) : assez pour comparer à l'œil, sans
/// noyer le message sous deux hash complets.
pub(crate) fn short_hash(hash: &str) -> &str {
    hash.get(..8).unwrap_or(hash)
}

/// Première lettre en minuscule, pour insérer un message complet au milieu
/// d'une phrase (« … impossible : fichier corrompu… »). Les noms propres
/// (Java, Minecraft…), les sigles et les chemins (« C:\… ») sont laissés.
pub(crate) fn lower_first(text: &str) -> String {
    const PROPER: [&str; 9] = [
        "Java",
        "Minecraft",
        "Mojang",
        "Fabric",
        "Windows",
        "Microsoft",
        "Xbox",
        "Reborn",
        "OneDrive",
    ];
    let first_word = text
        .split(|c: char| !c.is_alphanumeric())
        .next()
        .unwrap_or("");
    if PROPER.contains(&first_word) {
        return text.to_string();
    }
    let mut chars = text.chars();
    match (chars.next(), chars.next()) {
        (Some(first), Some(second)) if first.is_uppercase() && second.is_lowercase() => {
            first.to_lowercase().chain(text.chars().skip(1)).collect()
        }
        _ => text.to_string(),
    }
}

fn attempts_note(attempts: &u32) -> String {
    if *attempts > 1 {
        format!(" après {attempts} tentatives")
    } else {
        String::new()
    }
}

/// Adaptateur `map_err` : rattache le chemin concerné à une erreur disque.
pub(crate) fn io_at(path: &Path) -> impl FnOnce(std::io::Error) -> MojangError + '_ {
    move |source| MojangError::Io {
        path: path.to_path_buf(),
        source,
    }
}

/// Nom court d'un fichier pour les messages (`authlib-6.0.54.jar`).
fn display_name(path: &Path) -> String {
    path.file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_else(|| path.display().to_string())
}

// ──────────────────────────────────────────────────────
// Version json Mojang
// ──────────────────────────────────────────────────────

#[derive(Debug, Deserialize)]
struct VersionManifestV2 {
    versions: Vec<ManifestEntry>,
}

#[derive(Debug, Deserialize)]
struct ManifestEntry {
    id: String,
    url: String,
    sha1: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct VersionJson {
    pub id: String,
    pub libraries: Vec<Library>,
    pub downloads: VersionDownloads,
    #[serde(rename = "assetIndex")]
    pub asset_index: AssetIndexRef,
    #[serde(rename = "javaVersion", default)]
    pub java_version: Option<JavaVersion>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct VersionDownloads {
    pub client: VersionDownload,
}

#[derive(Debug, Deserialize, Clone)]
pub struct VersionDownload {
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct AssetIndexRef {
    pub id: String,
    pub sha1: String,
    pub url: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct JavaVersion {
    pub component: String,
    #[serde(rename = "majorVersion")]
    pub major_version: u32,
}

#[derive(Debug, Deserialize, Clone)]
pub struct Library {
    pub name: String,
    #[serde(default)]
    pub downloads: Option<LibraryDownloads>,
    #[serde(default)]
    pub rules: Vec<LibraryRule>,
    #[serde(default)]
    pub natives: HashMap<String, String>,
    #[serde(default)]
    pub extract: Option<LibraryExtract>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct LibraryDownloads {
    pub artifact: Option<LibraryArtifact>,
    #[serde(default)]
    pub classifiers: HashMap<String, LibraryArtifact>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct LibraryArtifact {
    pub path: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct LibraryRule {
    pub action: String, // "allow" / "disallow"
    #[serde(default)]
    pub os: Option<RuleOs>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct RuleOs {
    pub name: Option<String>,
    pub arch: Option<String>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct LibraryExtract {
    #[serde(default)]
    pub exclude: Vec<String>,
}

/// Récupère (ou lit en cache) le JSON d'une version Minecraft donnée.
///
/// Cache : `<game_dir>/versions/<id>/<id>.json`, validé par le SHA-1 du
/// catalogue racine. Si piston-meta est injoignable, on se rabat sur le cache
/// (vérifié lors de son écriture) au lieu de bloquer le lancement.
pub async fn fetch_version_json(
    http: &reqwest::Client,
    game_dir: &Path,
    version_id: &str,
) -> Result<VersionJson, MojangError> {
    let cache_path = game_dir
        .join("versions")
        .join(version_id)
        .join(format!("{version_id}.json"));

    // 1. Catalogue racine : URL + SHA-1 attendus.
    let entry = match fetch_manifest_entry(http, version_id).await {
        Ok(entry) => entry,
        Err(MojangError::Http(e)) => {
            if let Some(cached) = read_cached_version(&cache_path, version_id).await {
                tracing::warn!(
                    "catalogue Mojang injoignable ({}) — version {version_id} lue depuis le cache",
                    describe_http_error(&e)
                );
                return check_java(cached);
            }
            return Err(MojangError::Http(e));
        }
        Err(e) => return Err(e),
    };

    // 2. Cache à jour ?
    if let Ok(bytes) = fs::read(&cache_path).await {
        if hex_sha1(&bytes).eq_ignore_ascii_case(&entry.sha1) {
            return check_java(parse_version_json(&bytes, version_id)?);
        }
    }

    // 3. Sinon téléchargement + vérification + cache.
    if let Some(parent) = cache_path.parent() {
        fs::create_dir_all(parent).await.map_err(io_at(parent))?;
    }
    let bytes = http
        .get(&entry.url)
        .timeout(METADATA_TIMEOUT)
        .send()
        .await?
        .error_for_status()?
        .bytes()
        .await?;
    let actual = hex_sha1(&bytes);
    if !actual.eq_ignore_ascii_case(&entry.sha1) {
        return Err(MojangError::HashMismatch {
            what: format!("{version_id}.json"),
            expected: entry.sha1,
            actual,
        });
    }
    let version = parse_version_json(&bytes, version_id)?;
    fs::write(&cache_path, &bytes)
        .await
        .map_err(io_at(&cache_path))?;
    check_java(version)
}

async fn fetch_manifest_entry(
    http: &reqwest::Client,
    version_id: &str,
) -> Result<ManifestEntry, MojangError> {
    let manifest: VersionManifestV2 = http
        .get(VERSION_MANIFEST_V2)
        .timeout(METADATA_TIMEOUT)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    manifest
        .versions
        .into_iter()
        .find(|e| e.id == version_id)
        .ok_or_else(|| MojangError::UnknownVersion(version_id.to_string()))
}

async fn read_cached_version(cache_path: &Path, version_id: &str) -> Option<VersionJson> {
    let bytes = fs::read(cache_path).await.ok()?;
    parse_version_json(&bytes, version_id).ok()
}

fn parse_version_json(bytes: &[u8], version_id: &str) -> Result<VersionJson, MojangError> {
    serde_json::from_slice::<VersionJson>(bytes).map_err(|e| MojangError::Schema {
        origin: format!("piston-meta ({version_id}.json)"),
        detail: e.to_string(),
    })
}

/// Refuse tôt une version qui exige un Java plus récent que le JRE fourni :
/// sinon la JVM crashe au boot avec un `UnsupportedClassVersionError` obscur.
fn check_java(version: VersionJson) -> Result<VersionJson, MojangError> {
    if let Some(java) = &version.java_version {
        if java.major_version > runtime::RUNTIME_MAJOR {
            return Err(MojangError::JavaTooOld {
                minecraft: version.id.clone(),
                component: java.component.clone(),
                required: java.major_version,
                provided: runtime::RUNTIME_MAJOR,
            });
        }
    }
    Ok(version)
}

fn hex_sha1(bytes: &[u8]) -> String {
    hex::encode(Sha1::digest(bytes))
}

// ──────────────────────────────────────────────────────
// Téléchargement vérifié
// ──────────────────────────────────────────────────────

/// Garantit que `dest` existe avec le SHA-1 attendu, en le (re)téléchargeant
/// au besoin. `expected_sha1 = None` (vieilles libs Fabric sans hash publié) :
/// on télécharge seulement si le fichier est absent, sans vérification.
///
/// Retry transparent (4 tentatives, backoff 0,5 s / 1 s / 2 s) sur les erreurs
/// réseau transitoires et disque : le CDN Mojang coupe souvent quelques
/// connexions sur ~5000 assets. Un hash faux n'est PAS retenté — c'est une
/// vraie corruption, le serveur renverrait le même contenu.
pub(crate) async fn download_file(
    http: &reqwest::Client,
    url: &str,
    expected_sha1: Option<&str>,
    dest: &Path,
) -> Result<(), MojangError> {
    if let Some(parent) = dest.parent() {
        fs::create_dir_all(parent).await.map_err(io_at(parent))?;
    }
    match expected_sha1 {
        Some(expected) => {
            if let Some(actual) = sha1_file(dest).await? {
                if actual.eq_ignore_ascii_case(expected) {
                    return Ok(());
                }
            }
        }
        None => {
            if fs::try_exists(dest).await.map_err(io_at(dest))? {
                return Ok(());
            }
        }
    }

    let mut attempt = 1u32;
    loop {
        match try_download_once(http, url, expected_sha1, dest).await {
            Ok(()) => return Ok(()),
            Err(e) if attempt < DOWNLOAD_ATTEMPTS && is_retryable(&e) => {
                tracing::warn!(
                    "téléchargement de {} : tentative {attempt}/{DOWNLOAD_ATTEMPTS} échouée ({e}), nouvel essai",
                    display_name(dest)
                );
                tokio::time::sleep(Duration::from_millis(500 * 2u64.pow(attempt - 1))).await;
                attempt += 1;
            }
            Err(MojangError::Http(source)) => {
                return Err(MojangError::Download {
                    file: display_name(dest),
                    attempts: attempt,
                    source,
                })
            }
            Err(e) => return Err(e),
        }
    }
}

async fn try_download_once(
    http: &reqwest::Client,
    url: &str,
    expected_sha1: Option<&str>,
    dest: &Path,
) -> Result<(), MojangError> {
    let mut response = http.get(url).send().await?.error_for_status()?;
    let tmp = part_path(dest);
    let result = async {
        let file = fs::File::create(&tmp).await.map_err(io_at(&tmp))?;
        let mut writer = tokio::io::BufWriter::with_capacity(WRITE_BUFFER, file);
        let mut hasher = Sha1::new();
        while let Some(chunk) = response.chunk().await? {
            hasher.update(&chunk);
            writer.write_all(&chunk).await.map_err(io_at(&tmp))?;
        }
        writer.flush().await.map_err(io_at(&tmp))?;
        drop(writer);
        if let Some(expected) = expected_sha1 {
            let actual = hex::encode(hasher.finalize());
            if !actual.eq_ignore_ascii_case(expected) {
                return Err(MojangError::HashMismatch {
                    what: display_name(dest),
                    expected: expected.to_string(),
                    actual,
                });
            }
        }
        fs::rename(&tmp, dest).await.map_err(io_at(dest))
    }
    .await;
    if result.is_err() {
        let _ = fs::remove_file(&tmp).await;
    }
    result
}

/// `foo.jar` → `foo.jar.part` ; `3f8a…` (asset sans extension) → `3f8a….dl.part`.
fn part_path(dest: &Path) -> PathBuf {
    dest.with_extension(format!(
        "{}.part",
        dest.extension().and_then(|s| s.to_str()).unwrap_or("dl")
    ))
}

fn is_retryable(err: &MojangError) -> bool {
    match err {
        MojangError::Http(e) => {
            // Connexion / timeout / body interrompu / 5xx : on retente.
            // 4xx : on n'y reviendra pas.
            e.is_timeout()
                || e.is_connect()
                || e.is_body()
                || e.is_decode()
                || e.is_request()
                || e.status().map(|s| s.is_server_error()).unwrap_or(true)
        }
        MojangError::Io { .. } => true,
        _ => false,
    }
}

/// SHA-1 d'un fichier existant, lu par blocs sur le pool bloquant (un seul
/// aller-retour, pas un par bloc). `Ok(None)` si le fichier n'existe pas.
async fn sha1_file(path: &Path) -> Result<Option<String>, MojangError> {
    let owned = path.to_path_buf();
    tokio::task::spawn_blocking(move || -> std::io::Result<Option<String>> {
        let mut file = match std::fs::File::open(&owned) {
            Ok(f) => f,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            Err(e) => return Err(e),
        };
        let mut hasher = Sha1::new();
        let mut buf = vec![0u8; HASH_CHUNK];
        loop {
            let n = file.read(&mut buf)?;
            if n == 0 {
                break;
            }
            hasher.update(&buf[..n]);
        }
        Ok(Some(hex::encode(hasher.finalize())))
    })
    .await
    .map_err(|e| MojangError::Internal(e.to_string()))?
    .map_err(io_at(path))
}

// ──────────────────────────────────────────────────────
// Ensembles de fichiers + témoins de vérification
// ──────────────────────────────────────────────────────

/// Fichier distant attendu localement (asset, library, fichier du JRE…).
#[derive(Debug, Clone)]
pub(crate) struct RemoteFile {
    pub url: String,
    /// `None` : pas de hash publié (vieilles libs Fabric) → présence seule.
    pub sha1: Option<String>,
    /// `None` : taille inconnue → présence seule pour le chemin rapide.
    pub size: Option<u64>,
    pub dest: PathBuf,
}

/// Témoin écrit après une vérification complète réussie d'un ensemble.
#[derive(Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Stamp {
    /// Empreinte de l'ensemble attendu (cf [`fingerprint`]) ; pour le JRE, le
    /// SHA-1 de son manifeste piston-meta.
    pub fingerprint: String,
    pub count: usize,
    #[serde(default)]
    pub verified_at: u64,
    /// Libellé lisible (ex. version du JRE « 25.0.1 »).
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub label: String,
    /// (chemin relatif, taille) — seulement quand l'ensemble attendu n'est pas
    /// connu hors ligne (JRE) : le chemin rapide se vérifie alors sans réseau.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub files: Vec<(String, u64)>,
}

fn stamp_path(game_dir: &Path, name: &str) -> PathBuf {
    game_dir.join(STAMP_DIR).join(format!("{name}.json"))
}

pub(crate) async fn read_stamp(game_dir: &Path, name: &str) -> Option<Stamp> {
    let bytes = fs::read(stamp_path(game_dir, name)).await.ok()?;
    serde_json::from_slice(&bytes).ok()
}

/// Écrit un témoin (best-effort : un échec coûte seulement une vérification
/// complète au prochain lancement).
pub(crate) async fn write_stamp(game_dir: &Path, name: &str, mut stamp: Stamp) {
    stamp.verified_at = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let path = stamp_path(game_dir, name);
    let tmp = path.with_extension("json.tmp");
    let result = async {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).await?;
        }
        let bytes = serde_json::to_vec(&stamp).map_err(std::io::Error::other)?;
        fs::write(&tmp, bytes).await?;
        fs::rename(&tmp, &path).await
    }
    .await;
    if let Err(e) = result {
        tracing::warn!(
            "témoin {name} non écrit ({}) — vérification complète au prochain lancement",
            describe_io_error(&path, &e)
        );
    }
}

/// Oublie tous les témoins : le prochain lancement re-hache intégralement
/// assets, libraries, Fabric et JRE (et retélécharge ce qui diffère). C'est
/// le cœur du bouton « Réparer ». Supprime aussi les natives extraites
/// (`versions/*/natives/`) : l'extraction saute un fichier déjà présent à la
/// même taille, une DLL abîmée sans changer de taille ne serait jamais
/// réécrite. Idempotent.
pub async fn force_full_verify(game_dir: &Path) -> Result<(), MojangError> {
    let dir = game_dir.join(STAMP_DIR);
    match fs::remove_dir_all(&dir).await {
        Ok(()) => {
            tracing::info!(
                "témoins de vérification supprimés : vérification complète au prochain lancement"
            );
        }
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
        Err(source) => return Err(MojangError::Io { path: dir, source }),
    }
    remove_extracted_natives(game_dir).await;
    Ok(())
}

/// Supprime `versions/*/natives/` (réextraites au lancement suivant). Au
/// mieux : un dossier verrouillé est seulement signalé dans les logs.
async fn remove_extracted_natives(game_dir: &Path) {
    let Ok(mut entries) = fs::read_dir(game_dir.join("versions")).await else {
        return;
    };
    while let Ok(Some(entry)) = entries.next_entry().await {
        let natives = entry.path().join("natives");
        match fs::remove_dir_all(&natives).await {
            Ok(()) => tracing::info!("natives supprimées : {}", natives.display()),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
            Err(e) => tracing::warn!(
                "natives non supprimées ({}) : elles seront gardées telles quelles",
                describe_io_error(&natives, &e)
            ),
        }
    }
}

/// Vrai si chaque fichier existe avec la taille attendue (`None` = taille
/// inconnue, seule la présence compte). Un seul aller-retour sur le pool
/// bloquant : ~5000 `stat` prennent quelques dizaines de millisecondes.
pub(crate) async fn files_present(files: Vec<(PathBuf, Option<u64>)>) -> bool {
    tokio::task::spawn_blocking(move || {
        files
            .iter()
            .all(|(path, size)| match std::fs::metadata(path) {
                Ok(meta) => meta.is_file() && size.is_none_or(|s| meta.len() == s),
                Err(_) => false,
            })
    })
    .await
    .unwrap_or(false)
}

/// Empreinte d'un ensemble : SHA-1 des lignes « chemin|sha1|taille » triées
/// (l'index d'assets est une HashMap : son ordre d'itération n'est pas stable).
fn fingerprint(game_dir: &Path, files: &[RemoteFile]) -> String {
    let mut lines: Vec<String> = files
        .iter()
        .map(|f| {
            let rel = f
                .dest
                .strip_prefix(game_dir)
                .unwrap_or(&f.dest)
                .to_string_lossy()
                .replace('\\', "/");
            let sha1 = f.sha1.as_deref().unwrap_or("-").to_ascii_lowercase();
            let size = f.size.map_or_else(|| "-".to_string(), |s| s.to_string());
            format!("{rel}|{sha1}|{size}")
        })
        .collect();
    lines.sort_unstable();
    let mut hasher = Sha1::new();
    for line in &lines {
        hasher.update(line.as_bytes());
        hasher.update(b"\n");
    }
    hex::encode(hasher.finalize())
}

/// Garantit qu'un ensemble de fichiers distants est présent et intègre.
///
/// Chemin rapide : si le témoin `set_name` porte la même empreinte et que
/// chaque fichier existe avec la bonne taille, on ne relit rien. Sinon,
/// vérification complète (SHA-1 de chaque fichier présent, téléchargement de
/// ceux qui manquent ou diffèrent), puis réécriture du témoin.
pub(crate) async fn ensure_file_set(
    http: &reqwest::Client,
    game_dir: &Path,
    set_name: &str,
    mut files: Vec<RemoteFile>,
    concurrency: usize,
    on_progress: impl Fn(usize, usize),
) -> Result<(), MojangError> {
    // Deux entrées vers le même fichier (objets d'assets identiques sous deux
    // noms) se marcheraient dessus sur le même `.part` : on déduplique.
    files.sort_by(|a, b| a.dest.cmp(&b.dest));
    files.dedup_by(|a, b| a.dest == b.dest);

    let total = files.len();
    let fingerprint = fingerprint(game_dir, &files);
    if let Some(stamp) = read_stamp(game_dir, set_name).await {
        if stamp.fingerprint == fingerprint
            && files_present(files.iter().map(|f| (f.dest.clone(), f.size)).collect()).await
        {
            tracing::info!(
                "{set_name} : {total} fichier(s) déjà vérifiés (témoin à jour), hachage sauté"
            );
            on_progress(total, total);
            return Ok(());
        }
    }

    tracing::info!("{set_name} : vérification complète de {total} fichier(s)");
    download_all(http, files, concurrency, &on_progress).await?;
    write_stamp(
        game_dir,
        set_name,
        Stamp {
            fingerprint,
            count: total,
            ..Stamp::default()
        },
    )
    .await;
    Ok(())
}

/// Télécharge / vérifie `files` avec au plus `concurrency` transferts
/// simultanés. `on_progress(fait, total)` suit les fins de fichier, limité à
/// ~100 appels pour ne pas inonder l'IPC.
pub(crate) async fn download_all(
    http: &reqwest::Client,
    files: Vec<RemoteFile>,
    concurrency: usize,
    on_progress: &impl Fn(usize, usize),
) -> Result<(), MojangError> {
    let total = files.len();
    let emit_every = (total / 100).max(1);
    on_progress(0, total);

    // `buffer_unordered` ne crée la tâche suivante qu'à la libération d'une
    // place : la progression avance au fil de l'eau (et pas d'un bloc à la fin).
    let mut results = stream::iter(files)
        .map(|f| {
            let http = http.clone();
            tokio::spawn(
                async move { download_file(&http, &f.url, f.sha1.as_deref(), &f.dest).await },
            )
        })
        .buffer_unordered(concurrency.max(1));

    let mut done = 0usize;
    while let Some(joined) = results.next().await {
        joined.map_err(|e| MojangError::Internal(e.to_string()))??;
        done += 1;
        if done.is_multiple_of(emit_every) || done == total {
            on_progress(done, total);
        }
        if done.is_multiple_of(500) {
            tracing::info!("vérification : {done}/{total}");
        }
    }
    Ok(())
}

// ──────────────────────────────────────────────────────
// Règles OS / natives des libraries
// ──────────────────────────────────────────────────────

impl Library {
    /// Décide si la library s'applique à la plateforme courante (règles
    /// allow/disallow Mojang).
    pub fn applies_to_current_os(&self) -> bool {
        if self.rules.is_empty() {
            return true;
        }
        let mut allowed = false;
        for rule in &self.rules {
            let matches = rule.os.as_ref().map(matches_current_os).unwrap_or(true);
            match (rule.action.as_str(), matches) {
                ("allow", true) => allowed = true,
                ("disallow", true) => return false,
                _ => {}
            }
        }
        allowed
    }

    /// Si la library a une entrée natives pour cet OS (ancien format),
    /// retourne le classifier correspondant (ex « natives-windows »).
    pub fn current_native_classifier(&self) -> Option<String> {
        self.natives
            .get(current_os_key())
            // Mojang utilise parfois ${arch} -> 64
            .map(|c| c.replace("${arch}", "64"))
    }

    /// Pour les libs au format moderne (1.19+) où le classifier est dans le
    /// `name` lui-même (4e composant Maven `:`-séparé), retourne ce
    /// classifier. Ex : `org.lwjgl:lwjgl-glfw:3.3.3:natives-windows-x86`
    /// → `Some("natives-windows-x86")`.
    pub fn classifier_from_name(&self) -> Option<String> {
        self.name.split(':').nth(3).map(str::to_string)
    }
}

/// Filtre supplémentaire pour les natives au format moderne : Mojang
/// publie 3 variantes Windows (natives-windows, natives-windows-arm64,
/// natives-windows-x86) toutes annotées `os.name=windows` mais sans
/// préciser l'arch dans les `rules`. Le launcher doit donc choisir.
pub fn native_classifier_matches_current_arch(classifier: &str) -> bool {
    let wants_arm64 = classifier.ends_with("-arm64");
    let wants_x86 = classifier.ends_with("-x86");
    match std::env::consts::ARCH {
        "x86_64" => !wants_arm64 && !wants_x86,
        "aarch64" => wants_arm64,
        "x86" => wants_x86,
        _ => false,
    }
}

fn matches_current_os(os: &RuleOs) -> bool {
    if let Some(ref name) = os.name {
        if name != current_os_key() {
            return false;
        }
    }
    if let Some(ref arch) = os.arch {
        let cur = std::env::consts::ARCH;
        let normalized = match cur {
            "x86_64" => "x86",
            "aarch64" => "arm64",
            _ => cur,
        };
        if arch != cur && arch != normalized {
            return false;
        }
    }
    true
}

fn current_os_key() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn remote(dest: &str, sha1: &str, size: u64) -> RemoteFile {
        RemoteFile {
            url: format!("https://example.invalid/{sha1}"),
            sha1: Some(sha1.into()),
            size: Some(size),
            dest: PathBuf::from("/game").join(dest),
        }
    }

    fn scratch_dir(tag: &str) -> PathBuf {
        let dir =
            std::env::temp_dir().join(format!("reborn-mojang-test-{tag}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn fingerprint_ignores_order_and_tracks_content() {
        let game = Path::new("/game");
        let a = vec![remote("a.jar", "AA", 1), remote("b.jar", "bb", 2)];
        let b = vec![remote("b.jar", "bb", 2), remote("a.jar", "aa", 1)];
        assert_eq!(fingerprint(game, &a), fingerprint(game, &b));
        let c = vec![remote("a.jar", "aa", 1), remote("b.jar", "cc", 2)];
        assert_ne!(fingerprint(game, &a), fingerprint(game, &c));
    }

    #[test]
    fn io_error_message_carries_path_and_hint() {
        let err = std::io::Error::from(std::io::ErrorKind::PermissionDenied);
        let msg = describe_io_error(Path::new("C:/jeu/mods/x.jar"), &err);
        assert!(msg.contains("C:/jeu/mods/x.jar"), "{msg}");
        assert!(msg.contains("antivirus"), "{msg}");
    }

    #[test]
    fn attempts_note_only_when_retried() {
        assert_eq!(attempts_note(&1), "");
        assert_eq!(attempts_note(&4), " après 4 tentatives");
    }

    #[test]
    fn rejects_version_needing_newer_java() {
        let json = format!(
            r#"{{"id":"27.1","libraries":[],"downloads":{{"client":{{"sha1":"x","size":1,"url":"u"}}}},
               "assetIndex":{{"id":"30","sha1":"y","url":"u"}},
               "javaVersion":{{"component":"java-runtime-zeta","majorVersion":{}}}}}"#,
            runtime::RUNTIME_MAJOR + 1
        );
        let v = parse_version_json(json.as_bytes(), "27.1").unwrap();
        let err = check_java(v).unwrap_err();
        assert!(matches!(err, MojangError::JavaTooOld { .. }));
        assert!(err.to_string().contains("java-runtime-zeta"));
    }

    #[test]
    fn accepts_version_with_same_or_older_java() {
        let json = r#"{"id":"26.2","libraries":[],"downloads":{"client":{"sha1":"x","size":1,"url":"u"}},
                       "assetIndex":{"id":"30","sha1":"y","url":"u"},
                       "javaVersion":{"component":"java-runtime-epsilon","majorVersion":25}}"#;
        let v = parse_version_json(json.as_bytes(), "26.2").unwrap();
        assert!(check_java(v).is_ok());
    }

    #[test]
    fn classifier_from_name_reads_fourth_component() {
        let lib = Library {
            name: "org.lwjgl:lwjgl-glfw:3.3.3:natives-windows-x86".into(),
            downloads: None,
            rules: vec![],
            natives: HashMap::new(),
            extract: None,
        };
        assert_eq!(
            lib.classifier_from_name().as_deref(),
            Some("natives-windows-x86")
        );
    }

    #[tokio::test]
    async fn stamp_round_trip_and_force_full_verify() {
        let dir = scratch_dir("stamp");
        assert!(read_stamp(&dir, "assets-30").await.is_none());
        write_stamp(
            &dir,
            "assets-30",
            Stamp {
                fingerprint: "abc".into(),
                count: 2,
                ..Stamp::default()
            },
        )
        .await;
        let back = read_stamp(&dir, "assets-30").await.expect("témoin relu");
        assert_eq!(back.fingerprint, "abc");
        assert!(back.verified_at > 0);
        force_full_verify(&dir).await.unwrap();
        assert!(read_stamp(&dir, "assets-30").await.is_none());
        // Idempotent : dossier déjà absent.
        force_full_verify(&dir).await.unwrap();
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[tokio::test]
    async fn files_present_checks_existence_and_size() {
        let dir = scratch_dir("present");
        let f = dir.join("a.bin");
        std::fs::write(&f, b"hello").unwrap();
        assert!(files_present(vec![(f.clone(), Some(5))]).await);
        assert!(files_present(vec![(f.clone(), None)]).await);
        assert!(!files_present(vec![(f.clone(), Some(6))]).await);
        assert!(!files_present(vec![(dir.join("absent"), None)]).await);
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[tokio::test]
    async fn sha1_file_streams_and_reports_missing() {
        let dir = scratch_dir("sha1");
        let f = dir.join("a.bin");
        std::fs::write(&f, b"abc").unwrap();
        assert_eq!(
            sha1_file(&f).await.unwrap().as_deref(),
            Some("a9993e364706816aba3e25717850c26c9cd0d89d")
        );
        assert!(sha1_file(&dir.join("absent")).await.unwrap().is_none());
        let _ = std::fs::remove_dir_all(&dir);
    }
}
