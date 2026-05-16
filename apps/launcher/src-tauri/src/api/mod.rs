//! Client HTTP fin vers l'API Reborn (cf §3.1 : pas d'appels HTTP cote
//! frontend, tout passe par le backend Rust).
//!
//! En dev, l'URL de l'API est lue depuis `REBORN_API_URL` ou tombe sur
//! `http://localhost:3000/v1` par defaut.

use serde::{Deserialize, Serialize};

const DEFAULT_API_URL: &str = "http://localhost:3000/v1";

#[derive(Debug, Clone)]
pub struct ApiClient {
    pub base_url: String,
    pub http: reqwest::Client,
}

impl ApiClient {
    pub fn new() -> Self {
        let base_url = std::env::var("REBORN_API_URL").unwrap_or_else(|_| DEFAULT_API_URL.into());
        let http = reqwest::Client::builder()
            .user_agent(format!("RebornLauncher/{}", env!("CARGO_PKG_VERSION")))
            .timeout(std::time::Duration::from_secs(15))
            .build()
            .expect("reqwest client doit pouvoir s'initialiser");
        Self { base_url, http }
    }
}

impl Default for ApiClient {
    fn default() -> Self {
        Self::new()
    }
}

/// Payload envoye a `/auth/login` cote API : on transmet juste l'access
/// token Mojang, l'API valide elle-meme contre `/minecraft/profile`.
#[derive(Debug, Serialize)]
pub struct LoginRequest {
    #[serde(rename = "mcAccessToken")]
    pub mc_access_token: String,
}

#[derive(Debug, Serialize)]
pub struct DevLoginRequest {
    pub username: String,
}

#[derive(Debug, Serialize)]
pub struct RefreshRequest {
    #[serde(rename = "refreshToken")]
    pub refresh_token: String,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct AuthResponse {
    #[serde(rename = "accessToken")]
    pub access_token: String,
    #[serde(rename = "refreshToken")]
    pub refresh_token: String,
    pub user: ApiUser,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct ApiUser {
    pub id: String,
    #[serde(rename = "minecraftUuid")]
    pub minecraft_uuid: String,
    #[serde(rename = "minecraftUsername")]
    pub minecraft_username: String,
    #[serde(rename = "displayName")]
    pub display_name: Option<String>,
    #[serde(rename = "avatarUrl")]
    pub avatar_url: Option<String>,
    pub role: String,
    pub discord: Option<DiscordLinkage>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct DiscordLinkage {
    pub user_id: String,
    pub username: String,
    pub linked_at: String,
}

#[derive(Debug, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct PlaySession {
    pub play_token: String,
    pub expires_at: String,
}

#[derive(Debug, thiserror::Error)]
pub enum ApiError {
    #[error("requete HTTP echouee : {0}")]
    Http(#[from] reqwest::Error),
    #[error("API a repondu {status} : {body}")]
    Status { status: u16, body: String },
}

impl ApiClient {
    pub async fn login(&self, mc_access_token: &str) -> Result<AuthResponse, ApiError> {
        let url = format!("{}/auth/login", self.base_url);
        let resp = self
            .http
            .post(url)
            .json(&LoginRequest {
                mc_access_token: mc_access_token.to_string(),
            })
            .send()
            .await?;

        if !resp.status().is_success() {
            let status = resp.status().as_u16();
            let body = resp.text().await.unwrap_or_default();
            return Err(ApiError::Status { status, body });
        }
        Ok(resp.json::<AuthResponse>().await?)
    }

    /// Dev-only : appelle /v1/auth/dev-login avec un pseudo factice.
    pub async fn dev_login(&self, username: &str) -> Result<AuthResponse, ApiError> {
        let url = format!("{}/auth/dev-login", self.base_url);
        let resp = self
            .http
            .post(url)
            .json(&DevLoginRequest {
                username: username.to_string(),
            })
            .send()
            .await?;
        if !resp.status().is_success() {
            let status = resp.status().as_u16();
            let body = resp.text().await.unwrap_or_default();
            return Err(ApiError::Status { status, body });
        }
        Ok(resp.json::<AuthResponse>().await?)
    }

    pub async fn refresh(&self, refresh_token: &str) -> Result<AuthResponse, ApiError> {
        let url = format!("{}/auth/refresh", self.base_url);
        let resp = self
            .http
            .post(url)
            .json(&RefreshRequest {
                refresh_token: refresh_token.to_string(),
            })
            .send()
            .await?;

        if !resp.status().is_success() {
            let status = resp.status().as_u16();
            let body = resp.text().await.unwrap_or_default();
            return Err(ApiError::Status { status, body });
        }
        Ok(resp.json::<AuthResponse>().await?)
    }

    pub async fn logout(&self, access_token: &str) -> Result<(), ApiError> {
        let url = format!("{}/auth/logout", self.base_url);
        let resp = self
            .http
            .post(url)
            .bearer_auth(access_token)
            .send()
            .await?;

        if !resp.status().is_success() {
            let status = resp.status().as_u16();
            let body = resp.text().await.unwrap_or_default();
            return Err(ApiError::Status { status, body });
        }
        Ok(())
    }

    /// Demande un play-token court (~5min) signe HMAC par l'API. Sera
    /// passe a la JVM via sysprop, puis pousse au serveur par le mod
    /// Reborn Integrity au JOIN. L'API gate sur role != PLAYER, donc cet
    /// appel echoue (403) si le user n'est pas whitelist.
    pub async fn fetch_play_token(&self, access_token: &str) -> Result<PlaySession, ApiError> {
        let url = format!("{}/play/session", self.base_url);
        let resp = self.http.post(url).bearer_auth(access_token).send().await?;
        if !resp.status().is_success() {
            let status = resp.status().as_u16();
            let body = resp.text().await.unwrap_or_default();
            return Err(ApiError::Status { status, body });
        }
        Ok(resp.json::<PlaySession>().await?)
    }

    /// Recupere le manifest courant. Le payload est strictement le meme
    /// que la sortie du CLI manifest-signer (cf §4.3).
    pub async fn fetch_manifest(
        &self,
        access_token: &str,
    ) -> Result<crate::manifest::SignedManifest, ApiError> {
        let url = format!("{}/manifest/current", self.base_url);
        let resp = self.http.get(url).bearer_auth(access_token).send().await?;

        if !resp.status().is_success() {
            let status = resp.status().as_u16();
            let body = resp.text().await.unwrap_or_default();
            return Err(ApiError::Status { status, body });
        }
        Ok(resp.json::<crate::manifest::SignedManifest>().await?)
    }

    /// GET generique authentifie. Le caller deserialize. Centralise la
    /// gestion du JWT pour ne pas avoir a la dupliquer par endpoint.
    pub async fn get_json<T: for<'de> Deserialize<'de>>(
        &self,
        access_token: &str,
        path: &str,
    ) -> Result<T, ApiError> {
        let url = format!("{}{}", self.base_url, path);
        let resp = self.http.get(url).bearer_auth(access_token).send().await?;
        if !resp.status().is_success() {
            let status = resp.status().as_u16();
            let body = resp.text().await.unwrap_or_default();
            return Err(ApiError::Status { status, body });
        }
        Ok(resp.json::<T>().await?)
    }

    /// POST generique authentifie avec body JSON.
    pub async fn post_json<B: Serialize, T: for<'de> Deserialize<'de>>(
        &self,
        access_token: &str,
        path: &str,
        body: &B,
    ) -> Result<T, ApiError> {
        let url = format!("{}{}", self.base_url, path);
        let resp = self
            .http
            .post(url)
            .bearer_auth(access_token)
            .json(body)
            .send()
            .await?;
        if !resp.status().is_success() {
            let status = resp.status().as_u16();
            let body = resp.text().await.unwrap_or_default();
            return Err(ApiError::Status { status, body });
        }
        Ok(resp.json::<T>().await?)
    }

    /// DELETE generique authentifie qui ignore le body de reponse (204).
    pub async fn delete_no_content(
        &self,
        access_token: &str,
        path: &str,
    ) -> Result<(), ApiError> {
        let url = format!("{}{}", self.base_url, path);
        let resp = self
            .http
            .delete(url)
            .bearer_auth(access_token)
            .send()
            .await?;
        if !resp.status().is_success() {
            let status = resp.status().as_u16();
            let body = resp.text().await.unwrap_or_default();
            return Err(ApiError::Status { status, body });
        }
        Ok(())
    }

    /// PATCH generique authentifie avec body JSON.
    pub async fn patch_json<B: Serialize, T: for<'de> Deserialize<'de>>(
        &self,
        access_token: &str,
        path: &str,
        body: &B,
    ) -> Result<T, ApiError> {
        let url = format!("{}{}", self.base_url, path);
        let resp = self
            .http
            .patch(url)
            .bearer_auth(access_token)
            .json(body)
            .send()
            .await?;
        if !resp.status().is_success() {
            let status = resp.status().as_u16();
            let body = resp.text().await.unwrap_or_default();
            return Err(ApiError::Status { status, body });
        }
        Ok(resp.json::<T>().await?)
    }
}
