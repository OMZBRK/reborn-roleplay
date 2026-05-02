//! Etapes Xbox Live + XSTS du flow Microsoft.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §7.3, §7.5.

use serde::Deserialize;
use serde_json::json;

use super::error::{AuthError, AuthResult};

const XBL_AUTH_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";

#[derive(Debug, Clone)]
pub struct XstsToken {
    pub token: String,
    pub user_hash: String,
}

#[derive(Debug, Deserialize)]
struct XblResponse {
    #[serde(rename = "Token")]
    token: String,
}

#[derive(Debug, Deserialize)]
struct XstsResponse {
    #[serde(rename = "Token")]
    token: String,
    #[serde(rename = "DisplayClaims")]
    display_claims: XstsDisplayClaims,
}

#[derive(Debug, Deserialize)]
struct XstsDisplayClaims {
    xui: Vec<XstsXui>,
}

#[derive(Debug, Deserialize)]
struct XstsXui {
    uhs: String,
}

#[derive(Debug, Deserialize)]
struct XstsErrorResponse {
    #[serde(rename = "XErr")]
    x_err: Option<i64>,
    #[serde(rename = "Message")]
    message: Option<String>,
}

/// Authentifie contre Xbox Live et retourne le token XBL.
pub async fn authenticate_xbl(
    http: &reqwest::Client,
    ms_access_token: &str,
) -> AuthResult<String> {
    let body = json!({
        "Properties": {
            "AuthMethod": "RPS",
            "SiteName": "user.auth.xboxlive.com",
            "RpsTicket": format!("d={}", ms_access_token),
        },
        "RelyingParty": "http://auth.xboxlive.com",
        "TokenType": "JWT"
    });

    let resp = http
        .post(XBL_AUTH_URL)
        .header("Accept", "application/json")
        .json(&body)
        .send()
        .await?;

    if !resp.status().is_success() {
        let status = resp.status();
        let txt = resp.text().await.unwrap_or_default();
        return Err(AuthError::Xbox {
            code: status.as_u16().to_string(),
            message: txt,
        });
    }

    let parsed: XblResponse = resp.json().await?;
    Ok(parsed.token)
}

/// Echange le token XBL contre un token XSTS + userhash.
/// Les codes d'erreur XSTS connus sont mappes en messages utilisateur.
pub async fn authorize_xsts(http: &reqwest::Client, xbl_token: &str) -> AuthResult<XstsToken> {
    let body = json!({
        "Properties": {
            "SandboxId": "RETAIL",
            "UserTokens": [xbl_token],
        },
        "RelyingParty": "rp://api.minecraftservices.com/",
        "TokenType": "JWT"
    });

    let resp = http
        .post(XSTS_AUTH_URL)
        .header("Accept", "application/json")
        .json(&body)
        .send()
        .await?;

    if resp.status() == reqwest::StatusCode::UNAUTHORIZED {
        let err: XstsErrorResponse = resp.json().await.unwrap_or(XstsErrorResponse {
            x_err: None,
            message: None,
        });
        let code = err
            .x_err
            .map(|c| c.to_string())
            .unwrap_or_else(|| "unknown".to_string());
        let reason = explain_xsts_error(err.x_err);
        return Err(AuthError::Xsts { code, reason });
    }

    if !resp.status().is_success() {
        let status = resp.status();
        let txt = resp.text().await.unwrap_or_default();
        return Err(AuthError::Xsts {
            code: status.as_u16().to_string(),
            reason: txt,
        });
    }

    let parsed: XstsResponse = resp.json().await?;
    let user_hash = parsed
        .display_claims
        .xui
        .into_iter()
        .next()
        .ok_or_else(|| AuthError::Internal("XSTS : aucun userhash dans display_claims".into()))?
        .uhs;

    Ok(XstsToken {
        token: parsed.token,
        user_hash,
    })
}

fn explain_xsts_error(code: Option<i64>) -> String {
    match code {
        Some(2148916233) => "Ce compte Microsoft n'a pas encore de profil Xbox. Cree-en un sur xbox.com puis reessaie.".into(),
        Some(2148916235) => "Xbox Live n'est pas disponible dans ton pays.".into(),
        Some(2148916236) | Some(2148916237) => "Verification d'age requise sur ton compte Microsoft.".into(),
        Some(2148916238) => "Compte enfant : il doit etre rattache a un compte parent dans Family Settings.".into(),
        Some(c) => format!("Erreur Xbox XErr={c} (voir documentation Microsoft)."),
        None => "Erreur Xbox inconnue.".into(),
    }
}
