//! Orchestrateur du flow Microsoft -> Reborn.
//!
//! Commandes principales côté frontend :
//! - `auth_login_microsoft` : flow OAuth interactif complet
//! - `auth_resume_session`  : tente de réauthentifier au boot via les
//!   tokens stockés (refresh MS + refresh API)
//! - `auth_logout`          : révoque la session API + supprime les
//!   tokens locaux
//!
//! Aucun jeton (MS, Reborn, Minecraft) ne traverse l'IPC ni n'apparaît dans
//! les logs : ils vivent dans le keyring et en mémoire Rust uniquement.

mod error;
mod microsoft;
mod minecraft;
mod xbox;

pub use error::{AuthError, AuthResult};

use serde::Serialize;
use std::time::{Duration, Instant};
use tauri::{AppHandle, Runtime, State};
use tauri_plugin_opener::OpenerExt;

use crate::api::{ApiClient, ApiUser};
use crate::storage::secrets::{SecretKey, StorageError};
use crate::storage::SecretStore;

/// Écriture keyring non bloquante (slots du carousel, rotation de refresh) :
/// un échec ne doit pas casser la connexion en cours, mais on le trace.
fn warn_on_store_error(result: Result<(), StorageError>) {
    if let Err(e) = result {
        tracing::warn!("{e}");
    }
}

// 60s : timeout TOTAL de requete pour les calls API/OAuth (payloads petits,
// reponse rapide). NE PAS reutiliser ce client pour telecharger des
// fichiers : `.timeout()` borne la requete entiere *corps compris*, donc un
// gros jar (mcef, ReplayMod...) qui depasse 60s avorte systematiquement.
const DEFAULT_HTTP_TIMEOUT: std::time::Duration = std::time::Duration::from_secs(60);

// Client de telechargement : PAS de timeout total (un fichier peut
// legitimement prendre plusieurs minutes sur une connexion lente). On borne
// uniquement :
//   - la connexion (`connect_timeout`) : detecte un hote injoignable,
//   - chaque lecture (`read_timeout`) : detecte une socket morte / stall
//     (aucun octet recu pendant ce delai) sans plafonner la duree totale.
// Resultat : les gros fichiers passent, les vraies connexions mortes sont
// coupees rapidement.
const DOWNLOAD_CONNECT_TIMEOUT: std::time::Duration = std::time::Duration::from_secs(30);
const DOWNLOAD_READ_TIMEOUT: std::time::Duration = std::time::Duration::from_secs(60);

/// Session renvoyée au frontend. L'access token API n'y figure PAS (cf
/// PLAN §3.1) : il reste dans le keyring et chaque commande le relit.
#[derive(Debug, Clone, Serialize)]
pub struct AuthSession {
    pub user: ApiUser,
}

/// State injecte dans Tauri — porte l'HTTP client + le store de secrets.
pub struct AuthState {
    pub http: reqwest::Client,
    /// Client dedie au telechargement de fichiers (manifest mods + assets/
    /// libraries/JRE du jeu). Sans timeout total, avec connect+read timeouts
    /// — cf [`DOWNLOAD_CONNECT_TIMEOUT`] / [`DOWNLOAD_READ_TIMEOUT`].
    pub download_http: reqwest::Client,
    pub api: ApiClient,
    pub store: SecretStore,
    /// Cache du dernier user authentifie. Necessaire pour construire le
    /// LaunchConfig (uuid + pseudo) sans recoupler launcher::game a la
    /// chaine API.
    pub user: tokio::sync::Mutex<Option<ApiUser>>,
    /// Cache memoire du Minecraft access token (Mojang sessionserver).
    /// Pas persiste : duree de vie courte (~24h) et `ensure_mc_token` sait
    /// le regenerer a la volee via le refresh token MS du keyring.
    mc_access_token: tokio::sync::Mutex<Option<CachedMcToken>>,
}

/// Durée de vie annoncée par Mojang quand `expires_in` manque (24 h).
const MC_TOKEN_DEFAULT_TTL: Duration = Duration::from_secs(24 * 3600);
/// Marge avant l'expiration : un jeton ne doit pas expirer en pleine partie
/// (ni pendant la préparation du lancement).
const MC_TOKEN_MARGIN: Duration = Duration::from_secs(3600);

/// Jeton Minecraft en cache, daté et rattaché au compte qui l'a obtenu.
struct CachedMcToken {
    token: String,
    /// UUID Minecraft du propriétaire (sans tirets, minuscules) : un jeton
    /// d'un autre compte (changement de compte) n'est jamais réutilisé.
    owner: String,
    /// Au-delà, le jeton est considéré expiré et regénéré.
    valid_until: Instant,
}

impl CachedMcToken {
    fn new(token: String, owner: &str, expires_in: Option<u64>) -> Self {
        let ttl = expires_in
            .filter(|s| *s > 0)
            .map(Duration::from_secs)
            .unwrap_or(MC_TOKEN_DEFAULT_TTL);
        // Marge d'1 h, sans jamais descendre sous la moitié de la durée
        // annoncée (jeton exceptionnellement court).
        let usable = ttl.saturating_sub(MC_TOKEN_MARGIN).max(ttl / 2);
        Self {
            token,
            owner: normalize_uuid(owner),
            valid_until: Instant::now() + usable,
        }
    }

    fn usable_for(&self, owner: &str) -> bool {
        self.owner == normalize_uuid(owner) && Instant::now() < self.valid_until
    }
}

/// UUID comparable : sans tirets, minuscules (l'API et Mojang n'utilisent
/// pas la même forme).
fn normalize_uuid(uuid: &str) -> String {
    uuid.chars()
        .filter(|c| *c != '-')
        .map(|c| c.to_ascii_lowercase())
        .collect()
}

impl AuthState {
    pub fn new() -> Self {
        let http = reqwest::Client::builder()
            .user_agent(format!("RebornLauncher/{}", env!("CARGO_PKG_VERSION")))
            .timeout(DEFAULT_HTTP_TIMEOUT)
            .build()
            .expect("reqwest client doit pouvoir s'initialiser");
        let download_http = reqwest::Client::builder()
            .user_agent(format!("RebornLauncher/{}", env!("CARGO_PKG_VERSION")))
            .connect_timeout(DOWNLOAD_CONNECT_TIMEOUT)
            .read_timeout(DOWNLOAD_READ_TIMEOUT)
            .build()
            .expect("reqwest download client doit pouvoir s'initialiser");
        Self {
            http,
            download_http,
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

    /// Met en cache le jeton Minecraft de `owner` (UUID Minecraft), daté
    /// d'après `expires_in` (cf [`CachedMcToken`]).
    async fn cache_mc_token(&self, auth: &minecraft::MinecraftAuth, owner: &str) {
        *self.mc_access_token.lock().await = Some(CachedMcToken::new(
            auth.access_token.clone(),
            owner,
            auth.expires_in,
        ));
    }

    async fn clear_mc_token(&self) {
        *self.mc_access_token.lock().await = None;
    }

    /// Retourne un access token Minecraft valide pour le compte connecté, ou
    /// `None` si ce compte n'a pas de refresh MS stocké (typiquement :
    /// dev-login).
    ///
    /// Stratégie : le cache mémoire s'il appartient au compte connecté et
    /// n'approche pas de son expiration (~24 h chez Mojang, le launcher peut
    /// rester ouvert plus longtemps), sinon un refresh complet de la chaîne
    /// MS → XBL → XSTS → login_with_xbox via le refresh token persisté (celui
    /// du compte, puis le slot global). C'est aussi ce qui couvre le « boot
    /// via Reborn JWT refresh » et le changement de compte du carrousel, où
    /// `finalize_login` n'a pas tourné.
    pub async fn ensure_mc_token(&self) -> AuthResult<Option<String>> {
        let owner = self
            .current_user()
            .await
            .map(|u| u.minecraft_uuid)
            .unwrap_or_default();
        if let Some(cached) = self.mc_access_token.lock().await.as_ref() {
            if cached.usable_for(&owner) {
                return Ok(Some(cached.token.clone()));
            }
            tracing::info!("jeton Minecraft en cache expiré ou d'un autre compte : regénération");
        }

        let per_account = if owner.is_empty() {
            None
        } else {
            self.store.get_ms_refresh_token_for(&owner)?
        };
        let ms_refresh = match per_account {
            Some(token) => token,
            None => match self.store.get(SecretKey::MicrosoftRefresh)? {
                Some(token) => token,
                None => return Ok(None),
            },
        };
        let client_id = ms_client_id()?;
        let ms_tokens = microsoft::refresh_tokens(&self.http, &client_id, &ms_refresh).await?;
        let xbl_token = xbox::authenticate_xbl(&self.http, &ms_tokens.access_token).await?;
        let xsts = xbox::authorize_xsts(&self.http, &xbl_token).await?;
        let mc_auth = minecraft::login_with_xbox(&self.http, &xsts.user_hash, &xsts.token).await?;

        // Microsoft fait tourner le refresh token : persiste le nouveau
        // (slot du compte + slot global de la session courante).
        if let Some(new_refresh) = ms_tokens.refresh_token.as_deref() {
            warn_on_store_error(self.store.set(SecretKey::MicrosoftRefresh, new_refresh));
            if !owner.is_empty() {
                warn_on_store_error(self.store.set_ms_refresh_token_for(&owner, new_refresh));
            }
        }

        self.cache_mc_token(&mc_auth, &owner).await;
        Ok(Some(mc_auth.access_token))
    }

    /// Prépare un compte ALT (2e instance dev, staff-only) SANS toucher la
    /// session courante : ne modifie ni `self.user`, ni `self.mc_access_token`,
    /// ni les slots keyring legacy — uniquement les slots per-uuid (refresh
    /// roté). Renvoie de quoi lancer une 2e instance : profil MC + JWT Reborn
    /// (pour le play-token) + access token Minecraft (pour Mojang session).
    ///
    /// Le compte doit avoir des tokens sauvegardés (carousel) : refresh Reborn
    /// per-uuid ET refresh MS per-uuid. Sinon `NoStoredCredentials`.
    pub async fn prepare_alt_account(&self, uuid: &str) -> AuthResult<AltAccountLaunch> {
        // 1) Access token Minecraft via la chaîne MS per-uuid (non-mutating :
        //    on n'appelle PAS cache_mc_token, qui écraserait le token courant).
        let ms_refresh = self
            .store
            .get_ms_refresh_token_for(uuid)?
            .ok_or(AuthError::NoStoredCredentials)?;
        let client_id = ms_client_id()?;
        let ms_tokens = microsoft::refresh_tokens(&self.http, &client_id, &ms_refresh).await?;
        let xbl_token = xbox::authenticate_xbl(&self.http, &ms_tokens.access_token).await?;
        let xsts = xbox::authorize_xsts(&self.http, &xbl_token).await?;
        let mc_auth = minecraft::login_with_xbox(&self.http, &xsts.user_hash, &xsts.token).await?;

        // 2) Échange le MC token contre un JWT Reborn FRAIS via /auth/login
        //    (comme finalize_login) plutôt que via le refresh Reborn per-uuid,
        //    qui peut être périmé/déjà consommé (→ 401). Toujours non-mutating :
        //    on ne touche NI self.user, NI self.mc_access_token, NI les slots
        //    legacy — seulement les slots keyring per-uuid (refresh rotés).
        let api_resp = self
            .api
            .login(&mc_auth.access_token)
            .await
            .map_err(AuthError::reborn_login)?;

        let acct_uuid = api_resp.user.minecraft_uuid.clone();
        if let Some(new_refresh) = ms_tokens.refresh_token.as_deref() {
            warn_on_store_error(self.store.set_ms_refresh_token_for(&acct_uuid, new_refresh));
        }
        warn_on_store_error(
            self.store
                .set_reborn_refresh_token_for(&acct_uuid, &api_resp.refresh_token),
        );

        Ok(AltAccountLaunch {
            user: api_resp.user,
            reborn_jwt: api_resp.access_token,
            mc_access_token: mc_auth.access_token,
        })
    }
}

/// Données prêtes-à-lancer d'un compte ALT (cf `prepare_alt_account`).
pub struct AltAccountLaunch {
    pub user: ApiUser,
    pub reborn_jwt: String,
    pub mc_access_token: String,
}

impl Default for AuthState {
    fn default() -> Self {
        Self::new()
    }
}

/// Priorité : env runtime > compile-time `MS_CLIENT_ID_BUILD` (baké
/// par le maintainer pour les builds release distribués). Sans aucune
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
        "identifiant d'application Microsoft absent (MS_CLIENT_ID) : ce launcher a été compilé sans MS_CLIENT_ID_BUILD. Contacte le staff.".into(),
    ))
}

/// Phase 1 (PKCE + URL) -> ouvre dans le navigateur -> attend callback ->
/// echange code -> chaine XBL/XSTS/MC -> profil MC -> /auth/login API ->
/// stocke les refresh tokens.
async fn run_full_login(
    state: &AuthState,
    app: &AppHandle<impl Runtime>,
) -> AuthResult<AuthSession> {
    let client_id = ms_client_id()?;
    tracing::info!(
        "lancement OAuth avec client_id prefix={}",
        client_id.chars().take(8).collect::<String>()
    );
    let req = microsoft::build_authorize_url(&client_id)?;

    // Ouvre l'URL d'autorisation dans le navigateur systeme.
    app.opener()
        .open_url(&req.url, None::<String>)
        .map_err(|e| AuthError::Browser(e.to_string()))?;
    tracing::info!("MS authorize URL ouverte, attente du callback…");

    let code = match microsoft::wait_for_code(&req.state).await {
        Ok(c) => {
            tracing::info!("callback reçu, code de longueur {}", c.len());
            c
        }
        Err(e) => {
            tracing::warn!("callback échoué : {e}");
            return Err(e);
        }
    };

    let ms_tokens =
        match microsoft::exchange_code(&state.http, &client_id, &code, &req.verifier).await {
            Ok(t) => {
                tracing::info!(
                    "MS tokens obtenus (refresh_token={})",
                    t.refresh_token.is_some()
                );
                t
            }
            Err(e) => {
                tracing::warn!("exchange_code échoué : {e}");
                return Err(e);
            }
        };

    finalize_login(state, ms_tokens).await
}

/// Reprend la chaine apres un refresh MS deja effectue (boot) ou apres
/// l'echange du code (login interactif).
async fn finalize_login(
    state: &AuthState,
    ms: microsoft::MicrosoftTokens,
) -> AuthResult<AuthSession> {
    tracing::info!("etape 1/5 : XBL authenticate");
    let xbl_token = xbox::authenticate_xbl(&state.http, &ms.access_token)
        .await
        .inspect_err(|e| tracing::warn!("XBL authenticate échoué : {e}"))?;

    tracing::info!("etape 2/5 : XSTS authorize");
    let xsts = xbox::authorize_xsts(&state.http, &xbl_token)
        .await
        .inspect_err(|e| tracing::warn!("XSTS échoué : {e}"))?;

    tracing::info!("etape 3/5 : login_with_xbox");
    let mc_auth = minecraft::login_with_xbox(&state.http, &xsts.user_hash, &xsts.token)
        .await
        .inspect_err(|e| tracing::warn!("login_with_xbox échoué : {e}"))?;

    tracing::info!("etape 4/5 : fetch profile");
    let profile = minecraft::fetch_profile(&state.http, &mc_auth.access_token)
        .await
        .inspect_err(|e| tracing::warn!("fetch profile échoué : {e}"))?;

    // Cache memoire du MC access token — consomme par launcher::game pour le
    // --accessToken passe a la JVM. Sans ca, le serveur reject avec
    // "Invalid session" car le client se presente avec un token bidon.
    state.cache_mc_token(&mc_auth, &profile.id).await;
    // Pseudo + UUID Mojang : publics, utiles pour lire un log de support.
    tracing::info!(
        "profil Minecraft {} ({})",
        profile.name,
        minecraft::dashed_uuid(&profile.id)
    );

    tracing::info!("etape 5/5 : POST /v1/auth/login");

    // Echange contre un JWT Reborn — l'API revalide elle-meme le token
    // contre /minecraft/profile et cree/maj l'utilisateur.
    let api_resp = state
        .api
        .login(&mc_auth.access_token)
        .await
        .map_err(AuthError::reborn_login)
        .inspect_err(|e| tracing::warn!("POST /auth/login échoué : {e}"))?;

    // Persiste les refresh tokens dans le keyring.
    //
    // Double-ecriture : slots legacy (clef unique globale, preserve la
    // session des staffs deja en beta + utilise par try_resume) ET slots
    // per-uuid (necessaires au switch silencieux du carousel — sans eux on
    // ne saurait pas distinguer plusieurs comptes).
    let uuid = api_resp.user.minecraft_uuid.as_str();
    if let Some(refresh) = ms.refresh_token.as_deref() {
        state.store.set(SecretKey::MicrosoftRefresh, refresh)?;
        state.store.set_ms_refresh_token_for(uuid, refresh)?;
    }
    state
        .store
        .set(SecretKey::RebornRefreshToken, &api_resp.refresh_token)?;
    state
        .store
        .set_reborn_refresh_token_for(uuid, &api_resp.refresh_token)?;
    state
        .store
        .set(SecretKey::RebornAccessToken, &api_resp.access_token)?;

    state.set_user(Some(api_resp.user.clone())).await;

    Ok(AuthSession {
        user: api_resp.user,
    })
}

/// Tente de reprendre une session existante (boot du launcher).
/// Strategie :
/// 1. Si on a un refresh API stocke -> /auth/refresh (chemin rapide).
/// 2. Sinon, si on a un refresh MS -> rejoue le flow MS sans UI.
/// 3. Sinon -> retourne `None` (le frontend affiche `/login`).
async fn try_resume(state: &AuthState) -> AuthResult<Option<AuthSession>> {
    if let Some(reborn_refresh) = state.store.get(SecretKey::RebornRefreshToken)? {
        match state.api.refresh(&reborn_refresh).await {
            Ok(resp) => {
                state
                    .store
                    .set(SecretKey::RebornAccessToken, &resp.access_token)?;
                state
                    .store
                    .set(SecretKey::RebornRefreshToken, &resp.refresh_token)?;
                state.set_user(Some(resp.user.clone())).await;
                return Ok(Some(AuthSession { user: resp.user }));
            }
            Err(e) => {
                tracing::warn!("refresh JWT API échoué ({e}), on tente via MS");
            }
        }
    }

    if let Some(ms_refresh) = state.store.get(SecretKey::MicrosoftRefresh)? {
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
pub async fn auth_resume_session(state: State<'_, AuthState>) -> AuthResult<Option<AuthSession>> {
    try_resume(state.inner()).await
}

/// Serialise les refresh silencieux : le refresh token API tourne a chaque
/// appel, deux refresh concurrents avec le meme token feraient echouer le
/// second (token deja consomme).
static REFRESH_LOCK: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());

/// Keepalive de session : l'access token API vit 15 min (`JWT_ACCESS_TTL`)
/// et les commandes content/launcher le lisent tel quel dans le keyring.
/// Sans ce refresh periodique, tout appel API echoue en 401 apres 15 min
/// d'ouverture (mods optionnels qui disparaissent, patch notes vides...).
/// Le frontend l'appelle toutes les 10 min et au retour de focus.
///
/// Pas de fallback MS ici : si le refresh API est rejete, on remonte
/// l'erreur et le frontend laisse l'utilisateur en place (le prochain
/// boot repassera par `try_resume`).
///
/// Renvoie le user à jour : son grade peut avoir changé depuis la
/// connexion (whitelist acceptée pendant que le launcher est ouvert).
#[tauri::command]
pub async fn auth_refresh_access(state: State<'_, AuthState>) -> AuthResult<ApiUser> {
    let _guard = REFRESH_LOCK.lock().await;
    let inner = state.inner();
    let Some(token) = inner.store.get(SecretKey::RebornRefreshToken)? else {
        return Err(AuthError::NoStoredCredentials);
    };

    let resp = inner
        .api
        .refresh(&token)
        .await
        .map_err(AuthError::reborn_api)?;

    inner
        .store
        .set(SecretKey::RebornAccessToken, &resp.access_token)?;
    inner
        .store
        .set(SecretKey::RebornRefreshToken, &resp.refresh_token)?;
    // Garde le slot du carousel aligne : l'ancien refresh vient d'etre
    // consomme, le laisser en place casserait le switch de compte.
    warn_on_store_error(
        inner
            .store
            .set_reborn_refresh_token_for(&resp.user.minecraft_uuid, &resp.refresh_token),
    );
    inner.set_user(Some(resp.user.clone())).await;
    Ok(resp.user)
}

/// Dev-only : login factice tant que MS_CLIENT_ID n'est pas approuvé.
/// En release build, retourne `Err(Config)` immédiatement (le binaire de
/// distribution n'expose donc pas ce chemin même s'il est câblé côté IPC).
#[tauri::command]
pub async fn auth_dev_login(
    state: State<'_, AuthState>,
    username: String,
) -> AuthResult<AuthSession> {
    if !cfg!(debug_assertions) {
        return Err(AuthError::Config(
            "la connexion de développement est désactivée dans cette version du launcher".into(),
        ));
    }

    let api_resp = state
        .api
        .dev_login(&username)
        .await
        .map_err(AuthError::reborn_api)?;

    state
        .store
        .set(SecretKey::RebornRefreshToken, &api_resp.refresh_token)?;
    state
        .store
        .set(SecretKey::RebornAccessToken, &api_resp.access_token)?;

    state.set_user(Some(api_resp.user.clone())).await;

    Ok(AuthSession {
        user: api_resp.user,
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
/// Erreurs typées consommées par le frontend :
///   - `NoStoredCredentials`     : aucun token trouvé pour cet UUID
///   - `StoredCredentialsExpired`: tokens révoqués côté MS (slots nettoyés)
///
/// Tout autre kind (`http`, `api`, `microsoft_response`, `internal`…) =
/// erreur réseau/serveur : le frontend doit afficher le message, PAS
/// basculer en OAuth automatiquement.
#[tauri::command]
pub async fn auth_login_with_saved_account(
    state: State<'_, AuthState>,
    uuid: String,
) -> AuthResult<AuthSession> {
    let inner = state.inner();

    // ── 1) Tentative refresh JWT API
    let api_refresh = inner.store.get_reborn_refresh_token_for(&uuid)?;

    if let Some(token) = api_refresh {
        match inner.api.refresh(&token).await {
            Ok(resp) => {
                // Persiste tokens (legacy + per-uuid) puis retourne la session.
                let new_uuid = resp.user.minecraft_uuid.as_str();
                inner
                    .store
                    .set(SecretKey::RebornAccessToken, &resp.access_token)?;
                inner
                    .store
                    .set(SecretKey::RebornRefreshToken, &resp.refresh_token)?;
                inner
                    .store
                    .set_reborn_refresh_token_for(new_uuid, &resp.refresh_token)?;
                inner.set_user(Some(resp.user.clone())).await;
                return Ok(AuthSession { user: resp.user });
            }
            Err(e) if e.status() == Some(401) => {
                tracing::info!("refresh API per-uuid expiré pour {uuid}, fallback MS");
                // Fall through au refresh MS.
            }
            // Réseau coupé, 5xx, 429… : on affiche l'erreur sans toucher
            // aux jetons ni rouvrir la fenêtre Microsoft.
            Err(e) => return Err(AuthError::reborn_api(e)),
        }
    }

    // ── 2) Tentative refresh MS chain
    let ms_refresh = inner.store.get_ms_refresh_token_for(&uuid)?;

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
        Err(e) if e.is_revoked_grant() => {
            // MS a explicitement rejeté le refresh token (invalid_grant, etc.)
            // → tokens révoqués, on nettoie les slots per-uuid de ce compte
            // pour éviter que le carousel ré-essaie en boucle. La carte du
            // plugin-store reste (UX : l'utilisateur saura quel compte
            // ré-authentifier), elle pointera juste vers des slots vides → le
            // prochain clic ira directement en OAuth interactif via le
            // fallback frontend. Une panne Microsoft (5xx, 429) ne passe PAS
            // ici : on déconnecterait sinon des comptes pour rien.
            tracing::warn!("refresh MS révoqué pour {uuid} : {e}");
            warn_on_store_error(inner.store.delete_account_tokens(&uuid));
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
pub async fn auth_forget_account(state: State<'_, AuthState>, uuid: String) -> AuthResult<()> {
    state.inner().store.delete_account_tokens(&uuid)?;
    Ok(())
}

#[tauri::command]
pub async fn auth_logout(state: State<'_, AuthState>) -> AuthResult<()> {
    let store = state.store;
    if let Some(token) = store.get(SecretKey::RebornAccessToken)? {
        // Best-effort : la déconnexion locale doit aboutir même hors ligne.
        if let Err(e) = state.api.logout(&token).await {
            tracing::warn!("révocation de session API ignorée : {e}");
        }
    }
    store.delete(SecretKey::MicrosoftRefresh)?;
    store.delete(SecretKey::RebornAccessToken)?;
    store.delete(SecretKey::RebornRefreshToken)?;
    state.set_user(None).await;
    state.clear_mc_token().await;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn jeton_mc_rattache_au_compte_et_date() {
        let t = CachedMcToken::new(
            "tok".into(),
            "069a79f4-44e9-4726-A5BE-fca90e38aaf5",
            Some(86_400),
        );
        assert!(t.usable_for("069a79f444e94726a5befca90e38aaf5"));
        assert!(!t.usable_for("00000000000000000000000000000000"));
        assert!(!t.usable_for(""));
        // 24 h annoncées → utilisable ~23 h (marge d'1 h).
        let left = t.valid_until.saturating_duration_since(Instant::now());
        assert!(left > Duration::from_secs(22 * 3600) && left <= Duration::from_secs(23 * 3600));

        // Jeton très court : jamais moins de la moitié de sa durée.
        let short = CachedMcToken::new("tok".into(), "a", Some(600));
        let left = short.valid_until.saturating_duration_since(Instant::now());
        assert!(left > Duration::from_secs(290) && left <= Duration::from_secs(300));

        // Durée expirée : plus utilisable.
        let mut old = CachedMcToken::new("tok".into(), "a", None);
        old.valid_until = Instant::now() - Duration::from_secs(1);
        assert!(!old.usable_for("a"));
    }
}
