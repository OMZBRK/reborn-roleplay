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

// 60s : assez generaux pour absorber les transferts lents lors des batchs
// d'assets (~5000 fichiers en parallele) sur des connexions residentielles.
// Les calls OAuth/API restent rapides (TLS keep-alive sur 5s typiquement).
const DEFAULT_HTTP_TIMEOUT: std::time::Duration = std::time::Duration::from_secs(60);

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
    /// Cache du dernier user authentifie. Necessaire pour construire le
    /// LaunchConfig (uuid + pseudo) sans recoupler launcher::game a la
    /// chaine API.
    pub user: tokio::sync::Mutex<Option<ApiUser>>,
    /// Cache memoire du Minecraft access token (Mojang sessionserver).
    /// Pas persiste : duree de vie courte (~24h) et `ensure_mc_token` sait
    /// le regenerer a la volee via le refresh token MS du keyring.
    mc_access_token: tokio::sync::Mutex<Option<String>>,
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
            user: tokio::sync::Mutex::new(None),
            mc_access_token: tokio::sync::Mutex::new(None),
        }
    }

    pub async fn set_user(&self, user: Option<ApiUser>) {
        let mut lock = self.user.lock().await;
        *lock = user;
    }

    pub async fn current_user(&self) -> Option<ApiUser> {
        self.user.lock().await.clone()
    }

    pub async fn set_mc_token(&self, token: Option<String>) {
        let mut lock = self.mc_access_token.lock().await;
        *lock = token;
    }

    /// Retourne un access token Minecraft frais, ou `None` si l'utilisateur
    /// n'a pas de refresh MS stocke (typiquement : dev-login).
    ///
    /// Strategie : utilise le cache memoire en priorite (rempli apres un
    /// `finalize_login`), puis tombe sur un refresh complet de la chaine
    /// MS → XBL → XSTS → login_with_xbox via le refresh token persiste.
    /// C'est ce qui couvre le cas "boot via Reborn JWT refresh" ou le
    /// finalize_login n'a pas tourne mais le user est quand meme connecte.
    pub async fn ensure_mc_token(&self) -> AuthResult<Option<String>> {
        if let Some(token) = self.mc_access_token.lock().await.clone() {
            return Ok(Some(token));
        }
        let Some(ms_refresh) = self
            .store
            .get(SecretKey::MicrosoftRefresh)
            .map_err(|e| AuthError::Storage(e.to_string()))?
        else {
            return Ok(None);
        };
        let client_id = ms_client_id()?;
        let ms_tokens = microsoft::refresh_tokens(&self.http, &client_id, &ms_refresh).await?;
        let xbl_token = xbox::authenticate_xbl(&self.http, &ms_tokens.access_token).await?;
        let xsts = xbox::authorize_xsts(&self.http, &xbl_token).await?;
        let mc_auth = minecraft::login_with_xbox(&self.http, &xsts.user_hash, &xsts.token).await?;

        // Microsoft fait tourner le refresh token : persiste le nouveau.
        if let Some(new_refresh) = ms_tokens.refresh_token.as_deref() {
            let _ = self.store.set(SecretKey::MicrosoftRefresh, new_refresh);
        }

        self.set_mc_token(Some(mc_auth.access_token.clone())).await;
        Ok(Some(mc_auth.access_token))
    }
}

impl Default for AuthState {
    fn default() -> Self {
        Self::new()
    }
}

/// Priorite : env runtime > compile-time `MS_CLIENT_ID_BUILD` (baker
/// par le maintainer pour les builds release distribues). Sans aucune
/// des deux : erreur user-friendly.
fn ms_client_id() -> AuthResult<String> {
    if let Ok(v) = std::env::var("MS_CLIENT_ID") {
        return Ok(v);
    }
    if let Some(v) = option_env!("MS_CLIENT_ID_BUILD") {
        if !v.is_empty() {
            return Ok(v.to_string());
        }
    }
    Err(AuthError::Config(
        "MS_CLIENT_ID manquant. Le launcher n'a pas ete builde avec MS_CLIENT_ID_BUILD positionne. Contacte le maintainer.".into(),
    ))
}

/// Phase 1 (PKCE + URL) -> ouvre dans le navigateur -> attend callback ->
/// echange code -> chaine XBL/XSTS/MC -> profil MC -> /auth/login API ->
/// stocke les refresh tokens.
async fn run_full_login(state: &AuthState, app: &AppHandle<impl Runtime>) -> AuthResult<AuthSession> {
    let client_id = ms_client_id()?;
    tracing::info!(
        "lancement OAuth avec client_id prefix={}",
        client_id.chars().take(8).collect::<String>()
    );
    let req = microsoft::build_authorize_url(&client_id)?;

    // Ouvre l'URL d'autorisation dans le navigateur systeme.
    app.opener()
        .open_url(&req.url, None::<String>)
        .map_err(|e| AuthError::Internal(format!("impossible d'ouvrir le navigateur : {e}")))?;
    tracing::info!("MS authorize URL ouverte, attente du callback…");

    let code = match microsoft::wait_for_code(&req.state).await {
        Ok(c) => {
            tracing::info!("callback recu, code de longueur {}", c.len());
            c
        }
        Err(e) => {
            tracing::warn!("callback echoue : {e}");
            return Err(e);
        }
    };

    let ms_tokens = match microsoft::exchange_code(&state.http, &client_id, &code, &req.verifier).await {
        Ok(t) => {
            tracing::info!("MS tokens obtenus (refresh_token={})", t.refresh_token.is_some());
            t
        }
        Err(e) => {
            tracing::warn!("exchange_code echoue : {e}");
            return Err(e);
        }
    };

    finalize_login(state, ms_tokens).await
}

/// Reprend la chaine apres un refresh MS deja effectue (boot) ou apres
/// l'echange du code (login interactif).
async fn finalize_login(state: &AuthState, ms: microsoft::MicrosoftTokens) -> AuthResult<AuthSession> {
    tracing::info!("etape 1/5 : XBL authenticate");
    let xbl_token = xbox::authenticate_xbl(&state.http, &ms.access_token)
        .await
        .inspect_err(|e| tracing::warn!("XBL authenticate echoue : {e}"))?;

    tracing::info!("etape 2/5 : XSTS authorize");
    let xsts = xbox::authorize_xsts(&state.http, &xbl_token)
        .await
        .inspect_err(|e| tracing::warn!("XSTS echoue : {e}"))?;

    tracing::info!("etape 3/5 : login_with_xbox");
    let mc_auth = minecraft::login_with_xbox(&state.http, &xsts.user_hash, &xsts.token)
        .await
        .inspect_err(|e| tracing::warn!("login_with_xbox echoue : {e}"))?;

    // Cache memoire du MC access token — consomme par launcher::game pour le
    // --accessToken passe a la JVM. Sans ca, le serveur reject avec
    // "Invalid session" car le client se presente avec un token bidon.
    state.set_mc_token(Some(mc_auth.access_token.clone())).await;

    tracing::info!("etape 4/5 : fetch profile");
    let _profile = minecraft::fetch_profile(&state.http, &mc_auth.access_token)
        .await
        .inspect_err(|e| tracing::warn!("fetch profile echoue : {e}"))?;

    tracing::info!("etape 5/5 : POST /v1/auth/login");

    // Echange contre un JWT Reborn — l'API revalide elle-meme le token
    // contre /minecraft/profile et cree/maj l'utilisateur.
    let api_resp = state
        .api
        .login(&mc_auth.access_token)
        .await
        .map_err(|e| AuthError::Internal(format!("API Reborn : {e}")))?;

    // Persiste les refresh tokens dans le keyring.
    //
    // Double-ecriture : slots legacy (clef unique globale, preserve la
    // session des staffs deja en beta + utilise par try_resume) ET slots
    // per-uuid (necessaires au switch silencieux du carousel — sans eux on
    // ne saurait pas distinguer plusieurs comptes).
    let uuid = api_resp.user.minecraft_uuid.as_str();
    if let Some(refresh) = ms.refresh_token.as_deref() {
        state
            .store
            .set(SecretKey::MicrosoftRefresh, refresh)
            .map_err(|e| AuthError::Storage(e.to_string()))?;
        state
            .store
            .set_ms_refresh_token_for(uuid, refresh)
            .map_err(|e| AuthError::Storage(e.to_string()))?;
    }
    state
        .store
        .set(SecretKey::RebornRefreshToken, &api_resp.refresh_token)
        .map_err(|e| AuthError::Storage(e.to_string()))?;
    state
        .store
        .set_reborn_refresh_token_for(uuid, &api_resp.refresh_token)
        .map_err(|e| AuthError::Storage(e.to_string()))?;
    state
        .store
        .set(SecretKey::RebornAccessToken, &api_resp.access_token)
        .map_err(|e| AuthError::Storage(e.to_string()))?;

    state.set_user(Some(api_resp.user.clone())).await;

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
                state.set_user(Some(resp.user.clone())).await;
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

/// Dev-only : login factice tant que MS_CLIENT_ID n'est pas approuve.
/// En release build, retourne `Err(Config)` immediatement (le binaire de
/// distribution n'expose donc pas ce chemin meme s'il est cable cote IPC).
#[tauri::command]
pub async fn auth_dev_login(
    state: State<'_, AuthState>,
    username: String,
) -> AuthResult<AuthSession> {
    if !cfg!(debug_assertions) {
        return Err(AuthError::Config("dev-login disabled in release build".into()));
    }

    let api_resp = state
        .api
        .dev_login(&username)
        .await
        .map_err(|e| AuthError::Internal(format!("dev-login : {e}")))?;

    state
        .store
        .set(SecretKey::RebornRefreshToken, &api_resp.refresh_token)
        .map_err(|e| AuthError::Storage(e.to_string()))?;
    state
        .store
        .set(SecretKey::RebornAccessToken, &api_resp.access_token)
        .map_err(|e| AuthError::Storage(e.to_string()))?;

    state.set_user(Some(api_resp.user.clone())).await;

    Ok(AuthSession {
        user: api_resp.user,
        access_token: api_resp.access_token,
    })
}

/// "Switch silencieux" depuis le carousel : essaie de reconnecter le compte
/// identifie par son `minecraft_uuid` sans rouvrir la fenetre OAuth Microsoft.
///
/// Strategie (defensive, suit le contrat documente dans PLAN §7.2 etendu) :
///   1. Refresh JWT API per-uuid → si OK, persiste les nouveaux tokens et
///      retourne la session (chemin rapide, ~200ms).
///   2. Si l'API rejette (401) ou si on n'a pas de refresh API : essaie le
///      refresh MS per-uuid → chaine XBL/XSTS/MC → /v1/auth/login.
///   3. Sinon : retourne une erreur typee pour que le frontend declenche
///      l'OAuth interactif.
///
/// Erreurs typees consommees par le frontend :
///   - `NoStoredCredentials`     : aucun token trouve pour cet UUID
///   - `StoredCredentialsExpired`: tokens revoques cote MS (slots nettoyes)
/// Tout autre kind (`http`, `internal`, ...) = erreur reseau/serveur : le
/// frontend doit afficher le message, PAS basculer en OAuth automatiquement.
#[tauri::command]
pub async fn auth_login_with_saved_account(
    state: State<'_, AuthState>,
    uuid: String,
) -> AuthResult<AuthSession> {
    let inner = state.inner();

    // ── 1) Tentative refresh JWT API
    let api_refresh = inner
        .store
        .get_reborn_refresh_token_for(&uuid)
        .map_err(|e| AuthError::Storage(e.to_string()))?;

    if let Some(token) = api_refresh {
        match inner.api.refresh(&token).await {
            Ok(resp) => {
                // Persiste tokens (legacy + per-uuid) puis retourne la session.
                let new_uuid = resp.user.minecraft_uuid.as_str();
                inner
                    .store
                    .set(SecretKey::RebornAccessToken, &resp.access_token)
                    .map_err(|e| AuthError::Storage(e.to_string()))?;
                inner
                    .store
                    .set(SecretKey::RebornRefreshToken, &resp.refresh_token)
                    .map_err(|e| AuthError::Storage(e.to_string()))?;
                inner
                    .store
                    .set_reborn_refresh_token_for(new_uuid, &resp.refresh_token)
                    .map_err(|e| AuthError::Storage(e.to_string()))?;
                inner.set_user(Some(resp.user.clone())).await;
                return Ok(AuthSession {
                    user: resp.user,
                    access_token: resp.access_token,
                });
            }
            Err(crate::api::ApiError::Status { status: 401, .. }) => {
                tracing::info!("refresh API per-uuid expire pour {uuid}, fallback MS");
                // Fall through au refresh MS.
            }
            Err(crate::api::ApiError::Http(e)) => {
                return Err(AuthError::Http(e));
            }
            Err(crate::api::ApiError::Status { status, body }) => {
                return Err(AuthError::Internal(format!(
                    "API a repondu {status} : {body}"
                )));
            }
        }
    }

    // ── 2) Tentative refresh MS chain
    let ms_refresh = inner
        .store
        .get_ms_refresh_token_for(&uuid)
        .map_err(|e| AuthError::Storage(e.to_string()))?;

    let Some(ms_refresh) = ms_refresh else {
        return Err(AuthError::NoStoredCredentials);
    };

    let client_id = ms_client_id()?;
    match microsoft::refresh_tokens(&inner.http, &client_id, &ms_refresh).await {
        Ok(ms_tokens) => {
            // finalize_login s'occupe de la double-ecriture (legacy + per-uuid)
            // a partir de l'UUID renvoye par l'API Reborn.
            finalize_login(inner, ms_tokens).await
        }
        Err(AuthError::MicrosoftResponse(msg)) => {
            // MS a explicitement rejete le refresh token (invalid_grant, etc.)
            // → tokens revoques, on nettoie les slots per-uuid de ce compte
            // pour eviter que le carousel re-essaie en boucle. La carte du
            // plugin-store reste (UX : l'utilisateur saura quel compte
            // re-authentifier), elle pointera juste vers des slots vides → le
            // prochain clic ira directement en OAuth interactif via le
            // fallback frontend.
            tracing::warn!("refresh MS revoque pour {uuid} : {msg}");
            let _ = inner.store.delete_account_tokens(&uuid);
            Err(AuthError::StoredCredentialsExpired)
        }
        Err(other) => Err(other),
    }
}

/// "Oublier ce compte" depuis le carousel : supprime les slots keyring
/// per-uuid (refresh MS + refresh API) pour qu'un futur clic sur la carte
/// re-bascule en OAuth interactif (chemin `NoStoredCredentials` cote
/// frontend). Les slots legacy ne sont PAS touches : si le compte oublie
/// se trouve aussi etre le user actuellement connecte via try_resume, sa
/// session courante reste intacte — l'oubli est purement "scoped au
/// carousel". Le retrait de l'entree saved-accounts.json est fait cote
/// frontend via le store Zustand (removeSavedAccount).
#[tauri::command]
pub async fn auth_forget_account(
    state: State<'_, AuthState>,
    uuid: String,
) -> AuthResult<()> {
    state
        .inner()
        .store
        .delete_account_tokens(&uuid)
        .map_err(|e| AuthError::Storage(e.to_string()))?;
    Ok(())
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
    state.set_user(None).await;
    state.set_mc_token(None).await;
    Ok(())
}
