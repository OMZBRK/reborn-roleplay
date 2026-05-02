//! Flow OAuth Authorization Code + PKCE contre login.microsoftonline.com.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §7.2, §7.3.
//!
//! Le launcher ouvre l'URL d'autorisation dans le navigateur du systeme,
//! ecoute sur 127.0.0.1:53682, recupere le code, l'echange contre un
//! couple (access_token, refresh_token).

use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use rand::distributions::{Alphanumeric, DistString};
use serde::Deserialize;
use sha2::{Digest, Sha256};
use std::time::Duration;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpListener;
use tokio::time::timeout;
use url::Url;

use super::error::{AuthError, AuthResult};

const AUTHORIZE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const REDIRECT_URI: &str = "http://localhost:53682/callback";
const SCOPES: &str = "XboxLive.signin offline_access";
const CALLBACK_TIMEOUT: Duration = Duration::from_secs(180);

/// Tokens Microsoft retournes apres l'echange du code.
#[derive(Debug, Clone, Deserialize)]
pub struct MicrosoftTokens {
    pub access_token: String,
    #[serde(default)]
    pub refresh_token: Option<String>,
    #[serde(default)]
    pub expires_in: Option<u64>,
}

/// Etape 1+2 : genere PKCE + URL d'autorisation. L'appelant ouvre l'URL
/// dans le navigateur (via tauri-plugin-opener) et appelle ensuite
/// [`wait_for_code`] pour recuperer le code apres redirection.
pub fn build_authorize_url(client_id: &str) -> AuthResult<AuthorizeRequest> {
    let verifier = Alphanumeric.sample_string(&mut rand::thread_rng(), 64);
    let challenge = pkce_challenge(&verifier);
    let state = Alphanumeric.sample_string(&mut rand::thread_rng(), 32);

    let mut url = Url::parse(AUTHORIZE_URL)
        .map_err(|e| AuthError::Internal(format!("URL invalide : {e}")))?;
    url.query_pairs_mut()
        .append_pair("client_id", client_id)
        .append_pair("response_type", "code")
        .append_pair("redirect_uri", REDIRECT_URI)
        .append_pair("scope", SCOPES)
        .append_pair("code_challenge", &challenge)
        .append_pair("code_challenge_method", "S256")
        .append_pair("state", &state)
        .append_pair("prompt", "select_account");

    Ok(AuthorizeRequest {
        url: url.to_string(),
        verifier,
        state,
    })
}

#[derive(Debug, Clone)]
pub struct AuthorizeRequest {
    pub url: String,
    pub verifier: String,
    pub state: String,
}

fn pkce_challenge(verifier: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(verifier.as_bytes());
    URL_SAFE_NO_PAD.encode(hasher.finalize())
}

/// Etape 2 : ouvre un listener TCP sur 53682, attend la redirection
/// Microsoft, repond avec une page HTML auto-fermante et renvoie le code.
pub async fn wait_for_code(expected_state: &str) -> AuthResult<String> {
    let listener = TcpListener::bind("127.0.0.1:53682").await?;
    tracing::info!("OAuth callback listener bind sur 127.0.0.1:53682");

    let accepted = timeout(CALLBACK_TIMEOUT, listener.accept())
        .await
        .map_err(|_| AuthError::Timeout("aucune redirection MS recue en 3 minutes".into()))?;
    let (mut socket, _addr) = accepted?;

    // Lecture de la 1re ligne HTTP : `GET /callback?code=...&state=... HTTP/1.1`.
    let mut reader = BufReader::new(&mut socket);
    let mut request_line = String::new();
    reader.read_line(&mut request_line).await?;

    let path = request_line
        .split_whitespace()
        .nth(1)
        .ok_or_else(|| AuthError::Internal("requete HTTP malformee".into()))?;

    let parsed = Url::parse(&format!("http://localhost{path}"))
        .map_err(|e| AuthError::Internal(format!("URL callback invalide : {e}")))?;

    let mut returned_code: Option<String> = None;
    let mut returned_state: Option<String> = None;
    let mut returned_error: Option<String> = None;
    let mut returned_error_desc: Option<String> = None;

    for (k, v) in parsed.query_pairs() {
        match k.as_ref() {
            "code" => returned_code = Some(v.into_owned()),
            "state" => returned_state = Some(v.into_owned()),
            "error" => returned_error = Some(v.into_owned()),
            "error_description" => returned_error_desc = Some(v.into_owned()),
            _ => {}
        }
    }

    // On repond toujours quelque chose au navigateur, meme en erreur,
    // pour eviter qu'il reste sur "page blanche".
    let body = include_str!("callback.html");
    let response = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        body.len(),
        body
    );
    let _ = socket.write_all(response.as_bytes()).await;
    let _ = socket.shutdown().await;

    if let Some(err) = returned_error {
        if err == "access_denied" {
            return Err(AuthError::UserCanceled);
        }
        return Err(AuthError::MicrosoftResponse(format!(
            "{err} : {}",
            returned_error_desc.unwrap_or_default()
        )));
    }

    let code = returned_code
        .ok_or_else(|| AuthError::Internal("le callback ne contient pas de code".into()))?;
    let state = returned_state
        .ok_or_else(|| AuthError::Internal("le callback ne contient pas de state".into()))?;

    if state != expected_state {
        return Err(AuthError::Internal(
            "state CSRF invalide (mismatch)".into(),
        ));
    }

    Ok(code)
}

/// Etape 3 : echange le code contre les tokens MS.
pub async fn exchange_code(
    http: &reqwest::Client,
    client_id: &str,
    code: &str,
    verifier: &str,
) -> AuthResult<MicrosoftTokens> {
    let params = [
        ("client_id", client_id),
        ("code", code),
        ("grant_type", "authorization_code"),
        ("redirect_uri", REDIRECT_URI),
        ("code_verifier", verifier),
    ];

    let resp = http.post(TOKEN_URL).form(&params).send().await?;
    if !resp.status().is_success() {
        let txt = resp.text().await.unwrap_or_default();
        return Err(AuthError::MicrosoftResponse(txt));
    }
    Ok(resp.json::<MicrosoftTokens>().await?)
}

/// Recupere de nouveaux tokens MS a partir d'un refresh_token deja stocke.
/// Microsoft fait tourner les refresh tokens : il faut sauver le nouveau.
pub async fn refresh_tokens(
    http: &reqwest::Client,
    client_id: &str,
    refresh_token: &str,
) -> AuthResult<MicrosoftTokens> {
    let params = [
        ("client_id", client_id),
        ("refresh_token", refresh_token),
        ("grant_type", "refresh_token"),
        ("scope", SCOPES),
    ];

    let resp = http.post(TOKEN_URL).form(&params).send().await?;
    if !resp.status().is_success() {
        let txt = resp.text().await.unwrap_or_default();
        return Err(AuthError::MicrosoftResponse(txt));
    }
    Ok(resp.json::<MicrosoftTokens>().await?)
}
