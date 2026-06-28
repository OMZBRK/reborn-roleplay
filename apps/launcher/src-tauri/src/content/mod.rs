//! Tauri commands fines pour les endpoints "contenu" cote API :
//! patchnotes, reglement, whitelist, etc. Tous lisent le JWT depuis le
//! keyring et passent par api::ApiClient — le frontend ne fait jamais
//! d'appel HTTP direct (cf PLAN §3.1).

use serde::{Deserialize, Serialize};
use serde_json::Value as Json;
use tauri::{AppHandle, Runtime, State};
use tauri_plugin_opener::OpenerExt;

use crate::api::ApiUser;
use crate::auth::AuthState;
use crate::storage::prefs::{self, Preferences};
use crate::storage::secrets::SecretKey;

#[derive(Debug, thiserror::Error, Serialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum ContentError {
    #[error("non authentifie")]
    NotAuthenticated,
    #[error("api : {message}")]
    Api { message: String },
    #[error("io : {message}")]
    Io { message: String },
}

async fn jwt(state: &AuthState) -> Result<String, ContentError> {
    state
        .store
        .get(SecretKey::RebornAccessToken)
        .map_err(|e| ContentError::Io {
            message: e.to_string(),
        })?
        .ok_or(ContentError::NotAuthenticated)
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
}

// ──────────────────────────────────────────────────────
// Whitelist
// ──────────────────────────────────────────────────────

// Schéma riche v2 : aligné sur le wizard 2-étapes du launcher
// (cf src/stores/whitelist-store.ts WhitelistDraft) et le DTO API
// (cf apps/api/src/whitelist/whitelist.service.ts WhitelistApplicationDto).
#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct WhitelistApplicationDto {
    pub id: String,
    pub status: String, // PENDING / APPROVED / REJECTED / NEEDS_REVISION
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

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SubmitWhitelistRequest {
    pub dob: String,
    pub motivation: String,
    pub experience: String,
    pub availability: String,
    pub first_name: String,
    pub last_name: String,
    pub village: String,
    pub support: Option<String>,
    pub history: String,
    pub appearance: String,
    pub objectives: String,
}

#[tauri::command]
pub async fn whitelist_me(state: State<'_, AuthState>) -> Result<WhitelistStatus, ContentError> {
    let token = jwt(state.inner()).await?;
    let resp: Json = state
        .api
        .get_json(&token, "/whitelist/me")
        .await
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })?;
    // L'API retourne soit l'application, soit { application: null }.
    Ok(serde_json::from_value(resp).map_err(|e| ContentError::Api {
        message: format!("parse whitelist : {e}"),
    })?)
}

#[tauri::command]
#[allow(clippy::too_many_arguments)]
pub async fn whitelist_submit(
    state: State<'_, AuthState>,
    dob: String,
    motivation: String,
    experience: String,
    availability: String,
    first_name: String,
    last_name: String,
    village: String,
    support: Option<String>,
    history: String,
    appearance: String,
    objectives: String,
) -> Result<WhitelistApplicationDto, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .post_json(
            &token,
            "/whitelist",
            &SubmitWhitelistRequest {
                dob,
                motivation,
                experience,
                availability,
                first_name,
                last_name,
                village,
                support,
                history,
                appearance,
                objectives,
            },
        )
        .await
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
}

#[tauri::command]
#[allow(clippy::too_many_arguments)]
pub async fn whitelist_resubmit(
    state: State<'_, AuthState>,
    dob: String,
    motivation: String,
    experience: String,
    availability: String,
    first_name: String,
    last_name: String,
    village: String,
    support: Option<String>,
    history: String,
    appearance: String,
    objectives: String,
) -> Result<WhitelistApplicationDto, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .patch_json(
            &token,
            "/whitelist/me",
            &SubmitWhitelistRequest {
                dob,
                motivation,
                experience,
                availability,
                first_name,
                last_name,
                village,
                support,
                history,
                appearance,
                objectives,
            },
        )
        .await
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
}

#[tauri::command]
pub async fn whitelist_withdraw(state: State<'_, AuthState>) -> Result<(), ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .delete_no_content(&token, "/whitelist/me")
        .await
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
}

#[tauri::command]
pub async fn tickets_list(state: State<'_, AuthState>) -> Result<Vec<TicketSummary>, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .get_json(&token, "/tickets")
        .await
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
}

#[tauri::command]
pub async fn tickets_post_message(
    state: State<'_, AuthState>,
    id: String,
    content: String,
) -> Result<TicketMessageDto, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .post_json(
            &token,
            &format!("/tickets/{id}/messages"),
            &PostMessageRequest { content },
        )
        .await
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
}

#[tauri::command]
pub async fn tickets_delete(
    state: State<'_, AuthState>,
    id: String,
) -> Result<(), ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .delete_no_content(&token, &format!("/tickets/{id}"))
        .await
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })?;
    app.opener()
        .open_url(&resp.url, None::<String>)
        .map_err(|e| ContentError::Io {
            message: format!("impossible d'ouvrir le navigateur : {e}"),
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
}

/// Refresh l'objet User cote frontend (utile apres une liaison Discord
/// completee dans le navigateur). Hit /v1/auth/me + met a jour le cache
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
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
}

/// in-memory du backend.
#[tauri::command]
pub async fn auth_me(state: State<'_, AuthState>) -> Result<ApiUser, ContentError> {
    let token = jwt(state.inner()).await?;
    let user: ApiUser = state
        .api
        .get_json(&token, "/auth/me")
        .await
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })?;
    state.set_user(Some(user.clone())).await;
    Ok(user)
}

// ──────────────────────────────────────────────────────
// Preferences locales
// ──────────────────────────────────────────────────────

#[tauri::command]
pub async fn prefs_get() -> Result<Preferences, ContentError> {
    prefs::load().await.map_err(|e| ContentError::Io {
        message: e.to_string(),
    })
}

#[tauri::command]
pub async fn prefs_set(value: Preferences) -> Result<Preferences, ContentError> {
    prefs::save(&value).await.map_err(|e| ContentError::Io {
        message: e.to_string(),
    })?;
    Ok(value)
}
