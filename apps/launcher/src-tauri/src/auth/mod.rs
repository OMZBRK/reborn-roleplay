//! Orchestrateur du flow Microsoft -> Reborn.
//!
//! Depuis le frontend on n'invoque que 3 commandes :
//! - `auth_login_microsoft` : flow OAuth interactif complet
//! - `auth_resume_session`  : tente de reauthentifier au boot via les
//!   tokens stockes (refresh MS + refresh API)
//! - `auth_logout`          : revoque la session API + supprime les
//!   tokens locaux

mod error;
mod microsoft;
mod minecraft;
mod xbox;

pub use error::{AuthError, AuthErrorPayload, AuthResult};

use serde::Serialize;
use tauri::{AppHandle, Runtime, State};
use tauri_plugin_opener::OpenerExt;

use crate::api::{ApiClient, ApiUser};
use crate::storage::{secrets::SecretKey, SecretStore};

const DEFAULT_HTTP_TIMEOUT: std::time::Duration = std::time::Duration::from_secs(20);

#[derive(Debug, Clone, Serialize)]
pub struct AuthSession {
    pub user: ApiUser,
    /// L'access token API n'est PAS renvoye au frontend : il reste cote
    /// Rust et le frontend le re-recupere implicitement via les commands.
    /// Inclus ici pour le payload IPC initial uniquement (le frontend
    /// l'oublie apres lecture).
    #[serde(rename = "accessToken")]
    pub access_token: String,
}

/// State injecte dans Tauri — porte l'HTTP client + le store de secrets.
pub struct AuthState {
    pub http: reqwest::Client,
    pub api: ApiClient,
    pub store: SecretStore,
}

impl AuthState {
    pub fn new() -> Self {
        let http = reqwest::Client::builder()
            .user_agent(format!("RebornLauncher/{}", env!("CARGO_PKG_VERSION")))
            .timeout(DEFAULT_HTTP_TIMEOUT)
            .build()
            .expect("reqwest client doit pouvoir s'initialiser");
        Self {
            http,
            api: ApiClient::new(),
            store: SecretStore::new(),
        }
    }
}

impl Default for AuthState {
    fn default() -> Self {
        Self::new()
    }
}

fn ms_client_id() -> AuthResult<String> {
    std::env::var("MS_CLIENT_ID").map_err(|_| {
        AuthError::Config(
            "MS_CLIENT_ID n'est pas configuree. Enregistre une app Azure (cf annexe A du plan) puis copie le Client ID dans .env.".into(),
        )
    })
}

/// Phase 1 (PKCE + URL) -> ouvre dans le navigateur -> attend callback ->
/// echange code -> chaine XBL/XSTS/MC -> profil MC -> /auth/login API ->
/// stocke les refresh tokens.
async fn run_full_login(state: &AuthState, app: &AppHandle<impl Runtime>) -> AuthResult<AuthSession> {
    let client_id = ms_client_id()?;
    let req = microsoft::build_authorize_url(&client_id)?;

    // Ouvre l'URL d'autorisation dans le navigateur systeme.
    app.opener()
        .open_url(&req.url, None::<String>)
        .map_err(|e| AuthError::Internal(format!("impossible d'ouvrir le navigateur : {e}")))?;
    tracing::info!("MS authorize URL ouverte, attente du callback…");

    let code = microsoft::wait_for_code(&req.state).await?;
    let ms_tokens = microsoft::exchange_code(&state.http, &client_id, &code, &req.verifier).await?;

    finalize_login(state, ms_tokens).await
}

/// Reprend la chaine apres un refresh MS deja effectue (boot) ou apres
/// l'echange du code (login interactif).
async fn finalize_login(state: &AuthState, ms: microsoft::MicrosoftTokens) -> AuthResult<AuthSession> {
    let xbl_token = xbox::authenticate_xbl(&state.http, &ms.access_token).await?;
    let xsts = xbox::authorize_xsts(&state.http, &xbl_token).await?;
    let mc_auth = minecraft::login_with_xbox(&state.http, &xsts.user_hash, &xsts.token).await?;
    let _profile = minecraft::fetch_profile(&state.http, &mc_auth.access_token).await?;

    // Echange contre un JWT Reborn — l'API revalide elle-meme le token
    // contre /minecraft/profile et cree/maj l'utilisateur.
    let api_resp = state
        .api
        .login(&mc_auth.access_token)
        .await
        .map_err(|e| AuthError::Internal(format!("API Reborn : {e}")))?;

    // Persiste les refresh tokens dans le keyring.
    if let Some(refresh) = ms.refresh_token.as_deref() {
        state
            .store
            .set(SecretKey::MicrosoftRefresh, refresh)
            .map_err(|e| AuthError::Storage(e.to_string()))?;
    }
    state
        .store
        .set(SecretKey::RebornRefreshToken, &api_resp.refresh_token)
        .map_err(|e| AuthError::Storage(e.to_string()))?;
    state
        .store
        .set(SecretKey::RebornAccessToken, &api_resp.access_token)
        .map_err(|e| AuthError::Storage(e.to_string()))?;

    Ok(AuthSession {
        user: api_resp.user,
        access_token: api_resp.access_token,
    })
}

/// Tente de reprendre une session existante (boot du launcher).
/// Strategie :
/// 1. Si on a un refresh API stocke -> /auth/refresh (chemin rapide).
/// 2. Sinon, si on a un refresh MS -> rejoue le flow MS sans UI.
/// 3. Sinon -> retourne `None` (le frontend affiche `/login`).
async fn try_resume(state: &AuthState) -> AuthResult<Option<AuthSession>> {
    if let Some(reborn_refresh) = state
        .store
        .get(SecretKey::RebornRefreshToken)
        .map_err(|e| AuthError::Storage(e.to_string()))?
    {
        match state.api.refresh(&reborn_refresh).await {
            Ok(resp) => {
                state
                    .store
                    .set(SecretKey::RebornAccessToken, &resp.access_token)
                    .map_err(|e| AuthError::Storage(e.to_string()))?;
                state
                    .store
                    .set(SecretKey::RebornRefreshToken, &resp.refresh_token)
                    .map_err(|e| AuthError::Storage(e.to_string()))?;
                return Ok(Some(AuthSession {
                    user: resp.user,
                    access_token: resp.access_token,
                }));
            }
            Err(e) => {
                tracing::warn!("refresh JWT API echoue ({e}), on tente via MS");
            }
        }
    }

    if let Some(ms_refresh) = state
        .store
        .get(SecretKey::MicrosoftRefresh)
        .map_err(|e| AuthError::Storage(e.to_string()))?
    {
        let client_id = ms_client_id()?;
        let ms_tokens = microsoft::refresh_tokens(&state.http, &client_id, &ms_refresh).await?;
        return finalize_login(state, ms_tokens).await.map(Some);
    }

    Ok(None)
}

// ──────────────────────────────────────────────────────
//  Tauri commands
// ──────────────────────────────────────────────────────

#[tauri::command]
pub async fn auth_login_microsoft<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AuthState>,
) -> AuthResult<AuthSession> {
    run_full_login(state.inner(), &app).await
}

#[tauri::command]
pub async fn auth_resume_session(
    state: State<'_, AuthState>,
) -> AuthResult<Option<AuthSession>> {
    try_resume(state.inner()).await
}

#[tauri::command]
pub async fn auth_logout(state: State<'_, AuthState>) -> AuthResult<()> {
    let store = state.store;
    if let Some(token) = store
        .get(SecretKey::RebornAccessToken)
        .map_err(|e| AuthError::Storage(e.to_string()))?
    {
        let _ = state.api.logout(&token).await; // best-effort
    }
    store
        .delete(SecretKey::MicrosoftRefresh)
        .map_err(|e| AuthError::Storage(e.to_string()))?;
    store
        .delete(SecretKey::RebornAccessToken)
        .map_err(|e| AuthError::Storage(e.to_string()))?;
    store
        .delete(SecretKey::RebornRefreshToken)
        .map_err(|e| AuthError::Storage(e.to_string()))?;
    Ok(())
}
