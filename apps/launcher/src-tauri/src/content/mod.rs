//! Tauri commands fines pour les endpoints "contenu" cote API :
//! patchnotes, reglement, whitelist, etc. Tous lisent le JWT depuis le
//! keyring et passent par api::ApiClient — le frontend ne fait jamais
//! d'appel HTTP direct (cf PLAN §3.1).

use serde::{Deserialize, Serialize};
use serde_json::Value as Json;
use tauri::State;

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
// Whitelist
// ──────────────────────────────────────────────────────

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct WhitelistApplicationDto {
    pub id: String,
    pub status: String, // PENDING / APPROVED / REJECTED / NEEDS_REVISION
    pub character_name: String,
    pub character_age: i32,
    pub background: String,
    pub motivation: String,
    pub submitted_at: String,
    pub reviewed_at: Option<String>,
    pub review_notes: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct WhitelistStatus {
    pub application: Option<WhitelistApplicationDto>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SubmitWhitelistRequest {
    pub character_name: String,
    pub character_age: i32,
    pub background: String,
    pub motivation: String,
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
pub async fn whitelist_submit(
    state: State<'_, AuthState>,
    character_name: String,
    character_age: i32,
    background: String,
    motivation: String,
) -> Result<WhitelistApplicationDto, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .post_json(
            &token,
            "/whitelist",
            &SubmitWhitelistRequest {
                character_name,
                character_age,
                background,
                motivation,
            },
        )
        .await
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
}

#[tauri::command]
pub async fn whitelist_resubmit(
    state: State<'_, AuthState>,
    character_name: String,
    character_age: i32,
    background: String,
    motivation: String,
) -> Result<WhitelistApplicationDto, ContentError> {
    let token = jwt(state.inner()).await?;
    state
        .api
        .patch_json(
            &token,
            "/whitelist/me",
            &SubmitWhitelistRequest {
                character_name,
                character_age,
                background,
                motivation,
            },
        )
        .await
        .map_err(|e| ContentError::Api {
            message: e.to_string(),
        })
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
