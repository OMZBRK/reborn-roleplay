//! Tauri commands fines pour les endpoints "contenu" côté API :
//! patchnotes, règlement, whitelist, etc. Tous lisent le JWT depuis le
//! keyring et passent par api::ApiClient — le frontend ne fait jamais
//! d'appel HTTP direct (cf PLAN §3.1).

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use serde_json::Value as Json;
use tauri::{AppHandle, Runtime, State};
use tauri_plugin_opener::OpenerExt;

use crate::api::{ApiError, ApiUser, ShotView};
use crate::auth::AuthState;
use crate::launcher::paths::game_dir;
use crate::storage::describe_io_error;
use crate::storage::prefs::{self, Preferences};
use crate::storage::secrets::{SecretKey, StorageError};
use crate::tray::TrayState;

/// Compteur Discord de l'accueil (endpoint public `/menu/panel`).
pub mod communaute;

/// Candidature v2, brouillon, voix, séances de douane et accès au jeu.
pub mod candidature;

/// « Mes personnages » : personnages du compte sur le serveur de prod.
pub mod personnages;

/// Erreur des commandes "contenu". Traverse l'IPC sous la forme
/// `{ kind, message }` (cf `Serialize` plus bas) : `message` est une phrase
/// française complète, `kind` une catégorie stable pour le front.
#[derive(Debug, thiserror::Error)]
pub enum ContentError {
    #[error("Tu n'es pas connecté : connecte-toi avec Microsoft.")]
    NotAuthenticated,
    /// Erreur API Reborn : le message porte déjà la cause (message NestJS +
    /// code HTTP, ou cause réseau).
    #[error(transparent)]
    Api(#[from] ApiError),
    #[error(transparent)]
    Storage(#[from] StorageError),
    /// Erreur disque / système, message complet (chemin + cause OS).
    #[error("{message}")]
    Io { message: String },
    /// Paramètre refusé avant tout appel (nom de fichier, encodage…).
    #[error("{message}")]
    InvalidInput { message: String },
}

impl ContentError {
    /// Kinds émis : `not_authenticated`, `storage`, `io`, `invalid_input`,
    /// et pour l'API ceux de [`ApiError::kind`] (`session_expired`,
    /// `forbidden`, `not_found`, `conflict`, `rate_limited`, `server_error`,
    /// `network`, `unexpected_response`, `api`, `internal`).
    pub fn kind(&self) -> &'static str {
        match self {
            Self::NotAuthenticated => "not_authenticated",
            Self::Api(e) => e.kind(),
            Self::Storage(_) => "storage",
            Self::Io { .. } => "io",
            Self::InvalidInput { .. } => "invalid_input",
        }
    }

    fn io(action: &str, path: &Path, err: &std::io::Error) -> Self {
        Self::Io {
            message: describe_io_error(action, path, err),
        }
    }

    fn invalid_file_name(name: &str) -> Self {
        Self::InvalidInput {
            message: format!("Nom de fichier invalide : « {name} »."),
        }
    }
}

impl Serialize for ContentError {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        use serde::ser::SerializeStruct;
        let mut s = serializer.serialize_struct("ContentError", 2)?;
        s.serialize_field("kind", self.kind())?;
        s.serialize_field("message", &self.to_string())?;
        s.end()
    }
}

async fn jwt(state: &AuthState) -> Result<String, ContentError> {
    state
        .store
        .get(SecretKey::RebornAccessToken)?
        .ok_or(ContentError::NotAuthenticated)
}

/// Dossier du jeu, avec un message clair si l'OS ne le fournit pas.
fn game_dir_or_err() -> Result<PathBuf, ContentError> {
    game_dir().map_err(|e| ContentError::Io {
        message: format!("Dossier du jeu introuvable : {e}"),
    })
}

/// Refuse tout nom qui tenterait de sortir du dossier des captures.
fn is_safe_file_name(name: &str) -> bool {
    !name.is_empty() && !name.contains('/') && !name.contains('\\') && !name.contains("..")
}

// ──────────────────────────────────────────────────────
// Patch notes
// ──────────────────────────────────────────────────────

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct PatchNoteSummary {
    pub id: String,
    pub version: String,
    pub title: String,
    pub thumbnail: Option<String>,
    pub pinned: bool,
    pub published_at: String,
    pub excerpt: String,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct PatchNoteListResponse {
    pub items: Vec<PatchNoteSummary>,
    pub total: u64,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct PatchNoteDetail {
    pub id: String,
    pub version: String,
    pub title: String,
    pub thumbnail: Option<String>,
    pub pinned: bool,
    pub published_at: String,
    pub excerpt: String,
    pub content: String,
}

#[tauri::command]
pub async fn patchnotes_list(
    state: State<'_, AuthState>,
    page: Option<u32>,
    size: Option<u32>,
) -> Result<PatchNoteListResponse, ContentError> {
    let token = jwt(state.inner()).await?;
    let page = page.unwrap_or(1);
    let size = size.unwrap_or(10);
    state
        .api
        .get_json(&token, &format!("/patchnotes?page={page}&size={size}"))
        .await
        .map_err(ContentError::from)
}

#[tauri::command]
pub async fn patchnotes_detail(
    state: State<'_, AuthState>,
    id: String,
) -> Result<PatchNoteDetail, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .get_json(&token, &format!("/patchnotes/{id}"))
        .await
        .map_err(ContentError::from)
}

// ──────────────────────────────────────────────────────
// Reglement
// ──────────────────────────────────────────────────────

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct RulesDocument {
    pub version: String,
    pub content: String,
    pub published_at: String,
}

#[tauri::command]
pub async fn rules_current(state: State<'_, AuthState>) -> Result<RulesDocument, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .get_json(&token, "/rules/current")
        .await
        .map_err(ContentError::from)
}

// ──────────────────────────────────────────────────────
// Lore
// ──────────────────────────────────────────────────────

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct LoreDocument {
    pub version: String,
    pub content: String,
    pub published_at: String,
}

#[tauri::command]
pub async fn lore_current(state: State<'_, AuthState>) -> Result<LoreDocument, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .get_json(&token, "/lore/current")
        .await
        .map_err(ContentError::from)
}

// ──────────────────────────────────────────────────────
// Whitelist
// ──────────────────────────────────────────────────────

// Lecture de la forme d'avant la v2 (`whitelist_me`). L'écran de la
// candidature v2 passe par `content::candidature` (`candidature_me`…) ;
// l'ancien envoi (`whitelist_submit`, sans `formVersion`) et les créneaux
// d'oral ont été retirés (complément du 25/10 : plus de voie « ancien
// format »). Les champs de l'ancien formulaire valent "" en v2.
#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct WhitelistApplicationDto {
    pub id: String,
    pub status: String, // PENDING / APPROVED / REJECTED / NEEDS_REVISION
    // L5 — statuts HRP/RP indépendants (prévalidation orale + RP).
    pub hrp_status: String,
    pub rp_status: String,
    // Étape 1
    pub dob: String, // ISO YYYY-MM-DD
    pub motivation: String,
    pub experience: String,
    pub availability: String,
    // Étape 2
    pub first_name: String,
    pub last_name: String,
    pub village: String,
    pub support: Option<String>,
    pub history: String,
    pub appearance: String,
    pub objectives: String,
    // Méta
    pub submitted_at: String,
    pub reviewed_at: Option<String>,
    pub review_notes: Option<String>,
    // Assignation staff (cf C5 — flow DM Discord). assignee est null
    // tant que personne n'a pris en charge ; assigned_at sert au
    // launcher pour decider si le bouton "Demander une reprise" est
    // dispo (>= 4h).
    pub assignee: Option<AssigneeDto>,
    pub assigned_at: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct AssigneeDto {
    pub username: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct WhitelistStatus {
    pub application: Option<WhitelistApplicationDto>,
}

#[tauri::command]
pub async fn whitelist_me(state: State<'_, AuthState>) -> Result<WhitelistStatus, ContentError> {
    let token = jwt(state.inner()).await?;
    // L'API retourne `{ application: … | null }`. Un champ `application`
    // absent donne `None` (Option) : pas besoin de passer par un Json
    // intermédiaire, et une réponse inattendue remonte avec sa cause serde.
    Ok(state.api.get_json(&token, "/whitelist/me").await?)
}

#[tauri::command]
pub async fn whitelist_withdraw(state: State<'_, AuthState>) -> Result<(), ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .delete_no_content(&token, "/whitelist/me")
        .await
        .map_err(ContentError::from)
}

/// Demande au backend de liberer le staff actuellement assigne sur la
/// candidature, si la prise en charge dure depuis > 4h. L'API refuse
/// (HTTP 400) si le delai n'est pas atteint ou si rien n'est assigne.
#[tauri::command]
pub async fn whitelist_reclaim(state: State<'_, AuthState>) -> Result<Json, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .post_json::<_, Json>(&token, "/whitelist/me/reclaim", &serde_json::json!({}))
        .await
        .map_err(ContentError::from)
}

// ── Messages whitelist (chat staff↔candidat via thread Discord) ──────

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct WhitelistAttachment {
    pub url: String,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct WhitelistMessageDto {
    pub id: String,
    pub application_id: String,
    pub author_type: String, // USER / STAFF / SYSTEM
    pub author_id: Option<String>,
    pub author_name: Option<String>,
    pub content: String,
    pub attachments: Vec<WhitelistAttachment>,
    pub created_at: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct PostWhitelistMessageRequest {
    pub content: String,
    pub attachment_urls: Option<Vec<String>>,
}

#[tauri::command]
pub async fn whitelist_messages_list(
    state: State<'_, AuthState>,
) -> Result<Vec<WhitelistMessageDto>, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .get_json(&token, "/whitelist/me/messages")
        .await
        .map_err(ContentError::from)
}

#[tauri::command]
pub async fn whitelist_messages_post(
    state: State<'_, AuthState>,
    content: String,
    attachment_urls: Option<Vec<String>>,
) -> Result<WhitelistMessageDto, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .post_json(
            &token,
            "/whitelist/me/messages",
            &PostWhitelistMessageRequest {
                content,
                attachment_urls,
            },
        )
        .await
        .map_err(ContentError::from)
}

// ──────────────────────────────────────────────────────
// Tickets
// ──────────────────────────────────────────────────────

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct TicketSummary {
    pub id: String,
    pub category: String,
    pub subject: String,
    pub status: String,
    pub created_at: String,
    pub updated_at: String,
    pub last_message_preview: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct TicketAttachment {
    pub url: String,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct TicketMessageDto {
    pub id: String,
    pub author_id: Option<String>,
    pub author_name: Option<String>,
    pub is_staff: bool,
    pub content: String,
    pub attachments: Vec<TicketAttachment>,
    pub created_at: String,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct TicketDetail {
    pub id: String,
    pub category: String,
    pub subject: String,
    pub status: String,
    pub created_at: String,
    pub updated_at: String,
    pub messages: Vec<TicketMessageDto>,
    // Cf C5 — meme principe que WhitelistApplicationDto.
    pub assignee: Option<AssigneeDto>,
    pub assigned_at: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct CreateTicketRequest {
    category: String,
    subject: String,
    message: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct PostMessageRequest {
    content: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    attachment_urls: Option<Vec<String>>,
}

#[tauri::command]
pub async fn tickets_list(state: State<'_, AuthState>) -> Result<Vec<TicketSummary>, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .get_json(&token, "/tickets")
        .await
        .map_err(ContentError::from)
}

#[tauri::command]
pub async fn tickets_detail(
    state: State<'_, AuthState>,
    id: String,
) -> Result<TicketDetail, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .get_json(&token, &format!("/tickets/{id}"))
        .await
        .map_err(ContentError::from)
}

#[tauri::command]
pub async fn tickets_create(
    state: State<'_, AuthState>,
    category: String,
    subject: String,
    message: String,
) -> Result<TicketDetail, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .post_json(
            &token,
            "/tickets",
            &CreateTicketRequest {
                category,
                subject,
                message,
            },
        )
        .await
        .map_err(ContentError::from)
}

#[tauri::command]
pub async fn tickets_post_message(
    state: State<'_, AuthState>,
    id: String,
    content: String,
    attachment_urls: Option<Vec<String>>,
) -> Result<TicketMessageDto, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .post_json(
            &token,
            &format!("/tickets/{id}/messages"),
            &PostMessageRequest {
                content,
                attachment_urls,
            },
        )
        .await
        .map_err(ContentError::from)
}

#[tauri::command]
pub async fn tickets_delete(state: State<'_, AuthState>, id: String) -> Result<(), ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .delete_no_content(&token, &format!("/tickets/{id}"))
        .await
        .map_err(ContentError::from)
}

/// Idem whitelist_reclaim mais pour un ticket precis. L'API verifie
/// que le ticket appartient au user et que le delai 4h est ecoule.
#[tauri::command]
pub async fn tickets_reclaim(
    state: State<'_, AuthState>,
    id: String,
) -> Result<Json, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .post_json::<_, Json>(
            &token,
            &format!("/tickets/{id}/reclaim"),
            &serde_json::json!({}),
        )
        .await
        .map_err(ContentError::from)
}

// ──────────────────────────────────────────────────────
// Discord (liaison OAuth)
// ──────────────────────────────────────────────────────

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct DiscordStartResponse {
    pub url: String,
    pub state: String,
}

/// Demande a l'API une URL d'autorisation Discord, l'ouvre dans le
/// navigateur systeme. La suite (callback) est geree cote API ; le
/// frontend polle ensuite `auth_me` pour detecter la liaison.
#[tauri::command]
pub async fn discord_link_start<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AuthState>,
) -> Result<DiscordStartResponse, ContentError> {
    let token = jwt(state.inner()).await?;
    let resp: DiscordStartResponse = state
        .api
        .get_json(&token, "/auth/discord/start")
        .await
        .map_err(ContentError::from)?;
    app.opener()
        .open_url(&resp.url, None::<String>)
        .map_err(|e| ContentError::Io {
            message: format!("Impossible d'ouvrir ton navigateur pour lier Discord : {e}."),
        })?;
    Ok(resp)
}

#[tauri::command]
pub async fn discord_unlink(state: State<'_, AuthState>) -> Result<(), ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .delete_no_content(&token, "/auth/discord/unlink")
        .await
        .map_err(ContentError::from)
}

// ──────────────────────────────────────────────────────
// Server status (ping Minecraft via API)
// ──────────────────────────────────────────────────────

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct ServerPlayers {
    pub online: i32,
    pub max: i32,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct ServerStatus {
    pub online: bool,
    pub players: ServerPlayers,
    pub version: Option<String>,
    pub motd: Option<String>,
    pub latency_ms: Option<i64>,
    pub fetched_at: String,
}

/// Statut serveur via SLP. L'endpoint API est PUBLIC (pas de JWT) — on
/// l'appelle donc sans bearer pour qu'il marche aussi avant login si besoin.
#[tauri::command]
pub async fn server_status(state: State<'_, AuthState>) -> Result<ServerStatus, ContentError> {
    state
        .api
        .get_json_public("/server/status")
        .await
        .map_err(ContentError::from)
}

// ──────────────────────────────────────────────────────
// Upload de pièces jointes (screenshots whitelist / tickets)
// ──────────────────────────────────────────────────────

/// Upload d'une pièce jointe. Le frontend lit le fichier en base64 (le
/// WebView ne peut pas faire d'HTTP direct, cf §3.1) et passe les bytes ici ;
/// le backend POST en multipart vers /v1/upload et renvoie l'URL publique à
/// joindre au message. base64 standard (avec padding).
#[tauri::command]
pub async fn upload_attachment(
    state: State<'_, AuthState>,
    file_name: String,
    mime_type: String,
    data_base64: String,
) -> Result<String, ContentError> {
    use base64::Engine as _;
    let token = jwt(state.inner()).await?;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(data_base64.as_bytes())
        .map_err(|e| ContentError::InvalidInput {
            message: format!(
                "Pièce jointe « {file_name} » illisible (encodage base64 invalide : {e})."
            ),
        })?;
    let resp = state
        .api
        .upload_file(&token, &file_name, &mime_type, bytes)
        .await?;
    Ok(resp.url)
}

// ──────────────────────────────────────────────────────
// Badges (compteurs non-lus + monnaie) — cloche / sidebar
// ──────────────────────────────────────────────────────

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct Badges {
    pub unread_tickets: u32,
    pub unread_patchnotes: u32,
    pub coins: i64,
}

#[tauri::command]
pub async fn me_badges(state: State<'_, AuthState>) -> Result<Badges, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .get_json(&token, "/me/badges")
        .await
        .map_err(ContentError::from)
}

#[derive(Debug, Serialize)]
struct MarkReadRequest<'a> {
    scope: &'a str,
}

/// Marque "tickets" ou "patchnotes" comme lu et renvoie les badges à jour.
#[tauri::command]
pub async fn me_mark_read(
    state: State<'_, AuthState>,
    scope: String,
) -> Result<Badges, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .post_json(
            &token,
            "/me/badges/read",
            &MarkReadRequest { scope: &scope },
        )
        .await
        .map_err(ContentError::from)
}

/// Refresh l'objet User côté frontend (utile après une liaison Discord
/// complétée dans le navigateur). Hit /v1/auth/me + met à jour le cache
/// in-memory du backend.
#[tauri::command]
pub async fn auth_me(state: State<'_, AuthState>) -> Result<ApiUser, ContentError> {
    let token = jwt(state.inner()).await?;
    let user: ApiUser = state.api.get_json(&token, "/auth/me").await?;
    state.set_user(Some(user.clone())).await;
    Ok(user)
}

// ──────────────────────────────────────────────────────
// Préférences locales
// ──────────────────────────────────────────────────────

#[tauri::command]
pub async fn prefs_get() -> Result<Preferences, ContentError> {
    prefs::load().await.map_err(|e| ContentError::Io {
        message: format!("Impossible de lire tes préférences : {e}"),
    })
}

#[tauri::command]
pub async fn prefs_set(
    value: Preferences,
    tray: State<'_, TrayState>,
) -> Result<Preferences, ContentError> {
    // Les champs internes (trayHintShown) restent ceux du disque : le front
    // ne les gère pas.
    let saved = prefs::update(|current| *current = value.with_internal_fields_of(current))
        .await
        .map_err(|e| ContentError::Io {
            message: format!("Impossible d'enregistrer tes préférences : {e}"),
        })?;
    // « Fermer = cacher » suit le réglage tout de suite, sans relire le disque.
    tray.apply_prefs(&saved);
    Ok(saved)
}

// ──────────────────────────────────────────────────────
// Screenshots — galerie locale + partage social
//
// Le launcher et le mod-hud partagent le même dossier de jeu
// (`game_dir()` == gameDir de Fabric), donc :
//   • les captures vivent dans `<game_dir>/screenshots/*.png`
//   • les favoris dans `<game_dir>/reborn/screenshots-fav.json` (tableau
//     JSON de noms de fichier) — le MÊME fichier que la galerie in-game,
//     donc les favoris se synchronisent entre le jeu et le launcher.
// Le rendu des vignettes se fait via `convertFileSrc(path)` côté front
// (protocole asset activé dans tauri.conf.json).
// ──────────────────────────────────────────────────────

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct ScreenshotItem {
    /// Nom de fichier (sert d'id + de clé favori). Ex: `2026-07-01_23.42.15.png`.
    pub file_name: String,
    /// Chemin absolu — à passer à `convertFileSrc` pour l'affichage.
    pub path: String,
    /// Date de modification en millisecondes epoch (tri + affichage).
    pub modified_ms: u64,
    pub size_bytes: u64,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub favorite: bool,
}

fn screenshots_dir() -> Result<PathBuf, ContentError> {
    Ok(game_dir_or_err()?.join("screenshots"))
}

fn favorites_file() -> Result<PathBuf, ContentError> {
    Ok(game_dir_or_err()?
        .join("reborn")
        .join("screenshots-fav.json"))
}

/// Favoris partagés avec la galerie in-game. Absent ou corrompu = aucun
/// favori (le fichier est réécrit proprement au prochain basculement).
fn read_favorites() -> Vec<String> {
    let Ok(path) = favorites_file() else {
        return Vec::new();
    };
    let Ok(raw) = std::fs::read_to_string(&path) else {
        return Vec::new();
    };
    serde_json::from_str::<Vec<String>>(&raw).unwrap_or_default()
}

/// Réécrit le fichier de favoris (crée `reborn/` au besoin).
fn write_favorites(favorites: &[String]) -> Result<(), ContentError> {
    let path = favorites_file()?;
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)
            .map_err(|e| ContentError::io("créer le dossier", parent, &e))?;
    }
    let json = serde_json::to_string(favorites).map_err(|e| ContentError::Io {
        message: format!("Impossible d'encoder la liste des favoris : {e}"),
    })?;
    std::fs::write(&path, json).map_err(|e| ContentError::io("écrire", &path, &e))
}

/// MIME d'une capture d'après son extension (PNG par défaut côté jeu).
fn image_mime(file_name: &str) -> &'static str {
    if file_name.to_ascii_lowercase().ends_with(".png") {
        "image/png"
    } else {
        "image/jpeg"
    }
}

/// Dimensions d'un PNG via son en-tête IHDR (largeur @16, hauteur @20,
/// big-endian). `None` si ce n'est pas un PNG (jpg/webp non gérés — le front
/// affiche alors l'image sans résolution).
fn png_dimensions(bytes: &[u8]) -> Option<(u32, u32)> {
    if bytes.len() >= 24 && bytes[0] == 0x89 && bytes[1] == 0x50 {
        let w = u32::from_be_bytes([bytes[16], bytes[17], bytes[18], bytes[19]]);
        let h = u32::from_be_bytes([bytes[20], bytes[21], bytes[22], bytes[23]]);
        return Some((w, h));
    }
    None
}

/// Liste les captures locales, plus récentes d'abord.
#[tauri::command]
pub async fn screenshots_list() -> Result<Vec<ScreenshotItem>, ContentError> {
    let dir = screenshots_dir()?;
    let favorites = read_favorites();
    let mut items: Vec<ScreenshotItem> = Vec::new();

    let entries = match std::fs::read_dir(&dir) {
        Ok(e) => e,
        // Dossier absent = pas encore de capture : liste vide, pas une erreur.
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(items),
        Err(e) => return Err(ContentError::io("lire le dossier", &dir, &e)),
    };

    for entry in entries.flatten() {
        let path = entry.path();
        let is_image = path
            .extension()
            .and_then(|e| e.to_str())
            .map(|e| {
                let e = e.to_ascii_lowercase();
                e == "png" || e == "jpg" || e == "jpeg"
            })
            .unwrap_or(false);
        if !is_image {
            continue;
        }
        let Ok(meta) = entry.metadata() else { continue };
        if !meta.is_file() {
            continue;
        }
        let file_name = entry.file_name().to_string_lossy().to_string();
        let modified_ms = meta
            .modified()
            .ok()
            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0);

        // Lit uniquement les 24 premiers octets pour les dimensions PNG.
        let dims = {
            use std::io::Read as _;
            std::fs::File::open(&path)
                .ok()
                .and_then(|mut f| {
                    let mut header = [0u8; 24];
                    f.read_exact(&mut header).ok().map(|_| header)
                })
                .and_then(|h| png_dimensions(&h))
        };

        items.push(ScreenshotItem {
            favorite: favorites.contains(&file_name),
            path: path.to_string_lossy().to_string(),
            file_name,
            modified_ms,
            size_bytes: meta.len(),
            width: dims.map(|(w, _)| w),
            height: dims.map(|(_, h)| h),
        });
    }

    items.sort_by_key(|i| std::cmp::Reverse(i.modified_ms));
    Ok(items)
}

/// Ouvre le dossier des captures dans l'explorateur système.
#[tauri::command]
pub async fn screenshots_open_folder<R: Runtime>(app: AppHandle<R>) -> Result<(), ContentError> {
    let dir = screenshots_dir()?;
    std::fs::create_dir_all(&dir).map_err(|e| ContentError::io("créer le dossier", &dir, &e))?;
    app.opener()
        .open_path(dir.to_string_lossy().to_string(), None::<String>)
        .map_err(|e| ContentError::Io {
            message: format!(
                "Impossible d'ouvrir « {} » dans l'explorateur : {e}.",
                dir.display()
            ),
        })
}

/// Bascule l'état favori d'une capture (partagé avec la galerie in-game).
/// Retourne la liste à jour des noms favoris.
#[tauri::command]
pub async fn screenshots_toggle_favorite(file_name: String) -> Result<Vec<String>, ContentError> {
    let mut favorites = read_favorites();
    if let Some(pos) = favorites.iter().position(|f| f == &file_name) {
        favorites.remove(pos);
    } else {
        favorites.push(file_name);
    }
    write_favorites(&favorites)?;
    Ok(favorites)
}

/// Supprime une capture du disque (+ la retire des favoris si besoin).
/// Idempotent : une capture déjà absente n'est pas une erreur.
#[tauri::command]
pub async fn screenshots_delete(file_name: String) -> Result<(), ContentError> {
    if !is_safe_file_name(&file_name) {
        return Err(ContentError::invalid_file_name(&file_name));
    }
    let path = screenshots_dir()?.join(&file_name);
    match std::fs::remove_file(&path) {
        Ok(()) => {}
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
        Err(e) => return Err(ContentError::io("supprimer", &path, &e)),
    }
    // Nettoie l'entrée favori éventuelle (best-effort : la capture est déjà
    // supprimée, un échec ici ne doit pas faire croire le contraire).
    let mut favorites = read_favorites();
    if let Some(pos) = favorites.iter().position(|f| f == &file_name) {
        favorites.remove(pos);
        if let Err(e) = write_favorites(&favorites) {
            tracing::warn!("favoris non mis à jour après suppression : {e}");
        }
    }
    Ok(())
}

/// Partage une capture locale sur le feed social (`POST /v1/shots`). Le
/// launcher détient l'auth : il lit le fichier et l'upload en multipart.
#[tauri::command]
pub async fn screenshots_share(
    state: State<'_, AuthState>,
    file_name: String,
    caption: Option<String>,
) -> Result<ShotView, ContentError> {
    if !is_safe_file_name(&file_name) {
        return Err(ContentError::invalid_file_name(&file_name));
    }
    let token = jwt(state.inner()).await?;
    let path = screenshots_dir()?.join(&file_name);
    let bytes = std::fs::read(&path).map_err(|e| ContentError::io("lire", &path, &e))?;
    Ok(state
        .api
        .upload_shot(&token, &file_name, image_mime(&file_name), bytes, caption)
        .await?)
}

/// Une demande de partage en attente, écrite par le mod-hud in-game dans
/// `<game_dir>/reborn/pending-shares.json`.
#[derive(Debug, Deserialize)]
struct PendingShare {
    file: String,
    #[serde(default)]
    caption: String,
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct PendingSharesResult {
    pub shared: u32,
    pub failed: u32,
}

fn pending_shares_file() -> Result<PathBuf, ContentError> {
    Ok(game_dir_or_err()?
        .join("reborn")
        .join("pending-shares.json"))
}

/// Traite la file de partages déposée par le mod in-game : upload chaque
/// capture vers `/v1/shots`, puis réécrit la file avec seulement les entrées
/// encore en échec (les captures disparues du disque sont abandonnées). Appelé
/// par le launcher à l'ouverture du feed. No-op si la file est vide/absente.
#[tauri::command]
pub async fn screenshots_process_pending_shares(
    state: State<'_, AuthState>,
) -> Result<PendingSharesResult, ContentError> {
    let path = pending_shares_file()?;
    let raw = match std::fs::read_to_string(&path) {
        Ok(r) => r,
        Err(_) => {
            return Ok(PendingSharesResult {
                shared: 0,
                failed: 0,
            })
        }
    };
    let queue: Vec<PendingShare> = serde_json::from_str(&raw).unwrap_or_default();
    if queue.is_empty() {
        return Ok(PendingSharesResult {
            shared: 0,
            failed: 0,
        });
    }

    let token = jwt(state.inner()).await?;
    let dir = screenshots_dir()?;
    let mut shared = 0u32;
    let mut remaining: Vec<serde_json::Value> = Vec::new();

    for item in queue {
        // Garde-fou path traversal.
        if !is_safe_file_name(&item.file) {
            continue;
        }
        let file_path = dir.join(&item.file);
        let bytes = match std::fs::read(&file_path) {
            Ok(b) => b,
            // Fichier disparu : on abandonne l'entrée (pas de retry infini).
            Err(_) => continue,
        };
        let caption = Some(item.caption.clone()).filter(|c| !c.trim().is_empty());
        match state
            .api
            .upload_shot(&token, &item.file, image_mime(&item.file), bytes, caption)
            .await
        {
            Ok(_) => shared += 1,
            // Échec réseau/API : on garde l'entrée pour un prochain passage.
            Err(e) => {
                tracing::warn!("partage en attente « {} » non envoyé : {e}", item.file);
                remaining.push(serde_json::json!({
                    "file": item.file,
                    "caption": item.caption,
                }));
            }
        }
    }

    // Réécrit (ou supprime) la file selon ce qui reste. Best-effort : un
    // échec ici renverrait juste les mêmes partages au prochain passage.
    let rewrite = if remaining.is_empty() {
        std::fs::remove_file(&path)
    } else {
        serde_json::to_string(&remaining)
            .map_err(std::io::Error::other)
            .and_then(|json| std::fs::write(&path, json))
    };
    if let Err(e) = rewrite {
        tracing::warn!("{}", describe_io_error("mettre à jour", &path, &e));
    }

    Ok(PendingSharesResult {
        shared,
        failed: remaining.len() as u32,
    })
}

/// Feed social (screenshots partagés par la communauté).
#[tauri::command]
pub async fn shots_feed(
    state: State<'_, AuthState>,
    cursor: Option<String>,
) -> Result<Json, ContentError> {
    let token = jwt(state.inner()).await?;
    // Le curseur vient de l'API mais on l'encode quand même : un `&` ou un
    // `#` casserait la query string.
    let path = match cursor {
        Some(c) => format!(
            "/shots/feed?cursor={}",
            url::form_urlencoded::byte_serialize(c.as_bytes()).collect::<String>()
        ),
        None => "/shots/feed".to_string(),
    };
    state
        .api
        .get_json(&token, &path)
        .await
        .map_err(ContentError::from)
}

/// Toggle like sur un screenshot du feed.
#[tauri::command]
pub async fn shots_toggle_like(
    state: State<'_, AuthState>,
    shot_id: String,
) -> Result<Json, ContentError> {
    let token = jwt(state.inner()).await?;
    // Body `{}` et non `Json::Null` : l'API prod utilise express.json({strict:true})
    // qui rejette les primitives JSON (null) → 400. L'endpoint ignore le body.
    state
        .api
        .post_json(
            &token,
            &format!("/shots/{shot_id}/like"),
            &serde_json::json!({}),
        )
        .await
        .map_err(ContentError::from)
}

// ──────────────────────────────────────────────────────
// Profil — modification du nom d'affichage RP (displayName)
// ──────────────────────────────────────────────────────

#[derive(serde::Serialize)]
struct UpdateProfileBody<'a> {
    #[serde(rename = "displayName")]
    display_name: &'a str,
}

#[derive(serde::Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileResult {
    pub display_name: String,
}

/// PATCH /v1/me/profile — met à jour le nom d'affichage RP du joueur.
#[tauri::command]
pub async fn update_display_name(
    state: State<'_, AuthState>,
    display_name: String,
) -> Result<ProfileResult, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .patch_json(
            &token,
            "/me/profile",
            &UpdateProfileBody {
                display_name: display_name.trim(),
            },
        )
        .await
        .map_err(ContentError::from)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn serialise_kind_et_message_pour_chaque_variante() {
        let json = serde_json::to_value(ContentError::NotAuthenticated).unwrap();
        assert_eq!(json["kind"], "not_authenticated");
        assert_eq!(
            json["message"],
            "Tu n'es pas connecté : connecte-toi avec Microsoft."
        );

        let api = ContentError::from(ApiError::Status {
            status: 403,
            body: r#"{"statusCode":403,"message":"Candidature non acceptée","error":"Forbidden"}"#
                .into(),
        });
        let json = serde_json::to_value(&api).unwrap();
        assert_eq!(json["kind"], "forbidden");
        assert_eq!(
            json["message"],
            "Accès refusé : Candidature non acceptée (HTTP 403)."
        );

        let json = serde_json::to_value(ContentError::invalid_file_name("../x.png")).unwrap();
        assert_eq!(json["kind"], "invalid_input");
        assert_eq!(json["message"], "Nom de fichier invalide : « ../x.png ».");

        // Exactement deux champs : le front ne doit pas dépendre d'autre chose.
        assert_eq!(json.as_object().unwrap().len(), 2);
    }

    #[test]
    fn noms_de_fichier_dangereux_refuses() {
        assert!(is_safe_file_name("2026-07-01_23.42.15.png"));
        assert!(!is_safe_file_name(""));
        assert!(!is_safe_file_name("../secret.png"));
        assert!(!is_safe_file_name("a/b.png"));
        assert!(!is_safe_file_name("a\\b.png"));
    }
}
