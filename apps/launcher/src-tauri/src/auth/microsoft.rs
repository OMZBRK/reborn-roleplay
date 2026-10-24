//! Flow OAuth Authorization Code + PKCE contre login.microsoftonline.com.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §7.2, §7.3.
//!
//! Le launcher ouvre l'URL d'autorisation dans le navigateur du système,
//! écoute sur 127.0.0.1:53682, récupère le code, l'échange contre un
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

use super::error::{detail_suffix, AuthError, AuthResult};
use crate::api::{decode_json, truncate_chars};

const AUTHORIZE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const REDIRECT_URI: &str = "http://localhost:53682/callback";
const SCOPES: &str = "XboxLive.signin offline_access";
const CALLBACK_TIMEOUT: Duration = Duration::from_secs(180);

/// Tokens Microsoft retournés après l'échange du code.
#[derive(Clone, Deserialize)]
pub struct MicrosoftTokens {
    pub access_token: String,
    #[serde(default)]
    pub refresh_token: Option<String>,
}

/// `Debug` masqué : un `{:?}` dans un log ne doit jamais sortir de jeton.
impl std::fmt::Debug for MicrosoftTokens {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("MicrosoftTokens")
            .field("access_token", &"<masqué>")
            .field(
                "refresh_token",
                &self.refresh_token.as_ref().map(|_| "<masqué>"),
            )
            .finish()
    }
}

/// Etape 1+2 : genere PKCE + URL d'autorisation. L'appelant ouvre l'URL
/// dans le navigateur (via tauri-plugin-opener) et appelle ensuite
/// [`wait_for_code`] pour recuperer le code apres redirection.
pub fn build_authorize_url(client_id: &str) -> AuthResult<AuthorizeRequest> {
    let verifier = Alphanumeric.sample_string(&mut rand::thread_rng(), 64);
    let challenge = pkce_challenge(&verifier);
    let state = Alphanumeric.sample_string(&mut rand::thread_rng(), 32);

    let mut url = Url::parse(AUTHORIZE_URL)
        .map_err(|e| AuthError::Internal(format!("URL d'autorisation Microsoft invalide : {e}")))?;
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

/// Étape 2 : ouvre un listener TCP sur 53682, attend la redirection
/// Microsoft, répond avec une page HTML auto-fermante et renvoie le code.
pub async fn wait_for_code(expected_state: &str) -> AuthResult<String> {
    let listener = TcpListener::bind("127.0.0.1:53682")
        .await
        .map_err(AuthError::CallbackPort)?;
    tracing::info!("OAuth callback listener bind sur 127.0.0.1:53682");

    let accepted = timeout(CALLBACK_TIMEOUT, listener.accept()).await.map_err(|_| {
        AuthError::Timeout(
            "aucune réponse de Microsoft en 3 minutes. Relance la connexion et valide-la dans ton navigateur."
                .into(),
        )
    })?;
    let (mut socket, _addr) = accepted?;

    // Lecture de la 1re ligne HTTP : `GET /callback?code=...&state=... HTTP/1.1`.
    let mut reader = BufReader::new(&mut socket);
    let mut request_line = String::new();
    reader.read_line(&mut request_line).await?;

    let path = request_line.split_whitespace().nth(1).ok_or_else(|| {
        AuthError::Internal("retour de connexion Microsoft malformé, relance la connexion".into())
    })?;

    let parsed = Url::parse(&format!("http://localhost{path}")).map_err(|e| {
        AuthError::Internal(format!(
            "retour de connexion Microsoft illisible ({e}), relance la connexion"
        ))
    })?;

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
        let desc = returned_error_desc.unwrap_or_default();
        return Err(AuthError::MicrosoftResponse {
            status: None,
            message: format!(
                "Microsoft a refusé la connexion ({err}){} — relance la connexion.",
                detail_suffix(&first_line(&desc))
            ),
            error_code: Some(err),
        });
    }

    let code = returned_code.ok_or_else(|| {
        AuthError::Internal(
            "le retour de connexion Microsoft ne contient pas de code, relance la connexion".into(),
        )
    })?;
    let state = returned_state.ok_or_else(|| {
        AuthError::Internal(
            "le retour de connexion Microsoft ne contient pas de state, relance la connexion"
                .into(),
        )
    })?;

    if state != expected_state {
        return Err(AuthError::Internal(
            "retour de connexion Microsoft inattendu (state CSRF différent) : relance la connexion depuis le launcher"
                .into(),
        ));
    }

    Ok(code)
}

/// Microsoft ajoute « Trace ID / Correlation ID / Timestamp » sur les lignes
/// suivantes de `error_description` : seule la première ligne est utile.
fn first_line(s: &str) -> String {
    truncate_chars(s.lines().next().unwrap_or("").trim(), 300)
}

#[derive(Debug, Deserialize)]
struct TokenErrorBody {
    error: Option<String>,
    error_description: Option<String>,
}

/// Traduit une réponse non-2xx du endpoint token Microsoft. Le corps OAuth
/// ne contient jamais de jeton (seulement `error` + description AADSTS).
fn token_error(status: u16, body: &str) -> AuthError {
    let parsed: Option<TokenErrorBody> = serde_json::from_str(body).ok();
    let error_code = parsed.as_ref().and_then(|p| p.error.clone());
    let detail = parsed
        .and_then(|p| p.error_description)
        .map(|d| first_line(&d))
        .unwrap_or_default();
    let hint = match error_code.as_deref() {
        Some("invalid_grant") => "ta connexion Microsoft a expiré ou a été révoquée : reconnecte-toi",
        Some("interaction_required" | "consent_required" | "login_required") => {
            "Microsoft demande une nouvelle validation : reconnecte-toi"
        }
        Some("invalid_client" | "unauthorized_client") => {
            "l'application Microsoft du launcher est refusée (Client ID invalide) : contacte le staff"
        }
        Some("temporarily_unavailable" | "server_error") => {
            "service Microsoft momentanément indisponible : réessaie dans quelques minutes"
        }
        _ if status == 429 => "trop de tentatives : réessaie dans quelques minutes",
        _ if status >= 500 => {
            "service Microsoft momentanément indisponible : réessaie dans quelques minutes"
        }
        _ => "relance la connexion",
    };
    let code_part = error_code
        .as_deref()
        .map(|c| format!(", {c}"))
        .unwrap_or_default();
    let detail_part = if detail.is_empty() {
        String::new()
    } else {
        format!(". Détail : {detail}")
    };
    AuthError::MicrosoftResponse {
        status: Some(status),
        message: format!(
            "Microsoft a refusé la connexion : {hint} (HTTP {status}{code_part}){detail_part}."
        ),
        error_code,
    }
}

/// Étape 3 : échange le code contre les tokens MS.
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
        let status = resp.status().as_u16();
        let txt = resp.text().await.unwrap_or_default();
        return Err(token_error(status, &txt));
    }
    Ok(decode_json::<MicrosoftTokens>(resp).await?)
}

/// Récupère de nouveaux tokens MS à partir d'un refresh_token déjà stocké.
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
        let status = resp.status().as_u16();
        let txt = resp.text().await.unwrap_or_default();
        return Err(token_error(status, &txt));
    }
    Ok(decode_json::<MicrosoftTokens>(resp).await?)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn erreur_token_microsoft_lisible_et_sans_trace() {
        let body = r#"{"error":"invalid_grant","error_description":"AADSTS70000: The provided value is not valid.\r\nTrace ID: abc\r\nCorrelation ID: def"}"#;
        let err = token_error(400, body);
        assert!(err.is_revoked_grant());
        let msg = err.to_string();
        assert!(
            msg.starts_with("Microsoft a refusé la connexion : ta connexion Microsoft a expiré")
        );
        assert!(msg.contains("(HTTP 400, invalid_grant)"));
        assert!(msg.contains("AADSTS70000"));
        assert!(!msg.contains("Trace ID"));
    }

    #[test]
    fn panne_microsoft_ne_revoque_pas() {
        let err = token_error(503, "");
        assert!(!err.is_revoked_grant());
        assert!(err.to_string().contains("momentanément indisponible"));
    }
}
