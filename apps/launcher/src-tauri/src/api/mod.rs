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
}
