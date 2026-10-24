//! Étapes Xbox Live + XSTS du flow Microsoft.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §7.3, §7.5.

use serde::Deserialize;
use serde_json::json;

use super::error::{detail_suffix, AuthError, AuthResult};
use crate::api::{decode_json, extract_api_message};

const XBL_AUTH_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";

/// Pas de `Debug` : la structure porte un jeton XSTS.
#[derive(Clone)]
pub struct XstsToken {
    pub token: String,
    pub user_hash: String,
}

#[derive(Deserialize)]
struct XblResponse {
    #[serde(rename = "Token")]
    token: String,
}

#[derive(Deserialize)]
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

#[derive(Debug, Default, Deserialize)]
struct XstsErrorResponse {
    #[serde(rename = "XErr")]
    x_err: Option<i64>,
    #[serde(rename = "Message")]
    message: Option<String>,
}

/// Authentifie contre Xbox Live et retourne le token XBL.
pub async fn authenticate_xbl(http: &reqwest::Client, ms_access_token: &str) -> AuthResult<String> {
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
        // Xbox renvoie souvent un corps vide ; sinon `{ XErr, Message }`.
        let detail = match serde_json::from_str::<XstsErrorResponse>(&txt) {
            Ok(XstsErrorResponse {
                x_err: Some(code), ..
            }) => explain_xsts_error(Some(code)),
            _ => extract_api_message(&txt).unwrap_or_default(),
        };
        return Err(AuthError::Xbox {
            code: status.as_u16().to_string(),
            message: detail,
        });
    }

    let parsed: XblResponse = decode_json(resp).await?;
    Ok(parsed.token)
}

/// Échange le token XBL contre un token XSTS + userhash.
/// Les codes d'erreur XSTS connus sont mappés en messages utilisateur.
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
        let err: XstsErrorResponse = resp.json().await.unwrap_or_default();
        let code = err
            .x_err
            .map(|c| c.to_string())
            .unwrap_or_else(|| "401".to_string());
        // Sans XErr, le `Message` brut de Xbox (s'il y en a un) vaut mieux
        // que la phrase générique.
        let reason = match (err.x_err, err.message.filter(|m| !m.trim().is_empty())) {
            (None, Some(m)) => format!(
                "{} — réessaie dans quelques minutes.",
                m.trim().trim_end_matches('.')
            ),
            (x_err, _) => explain_xsts_error(x_err),
        };
        return Err(AuthError::Xsts { code, reason });
    }

    if !resp.status().is_success() {
        let status = resp.status().as_u16();
        let txt = resp.text().await.unwrap_or_default();
        return Err(AuthError::Xsts {
            code: status.to_string(),
            reason: format!(
                "réponse inattendue (HTTP {status}){} — réessaie dans quelques minutes.",
                detail_suffix(&extract_api_message(&txt).unwrap_or_default())
            ),
        });
    }

    let parsed: XstsResponse = decode_json(resp).await?;
    let user_hash = parsed
        .display_claims
        .xui
        .into_iter()
        .next()
        .ok_or_else(|| {
            AuthError::Internal("réponse Xbox (XSTS) sans identifiant de joueur (userhash)".into())
        })?
        .uhs;

    Ok(XstsToken {
        token: parsed.token,
        user_hash,
    })
}

fn explain_xsts_error(code: Option<i64>) -> String {
    match code {
        Some(2148916233) => {
            "ce compte Microsoft n'a pas encore de profil Xbox. Crée-en un sur xbox.com puis réessaie.".into()
        }
        Some(2148916235) => "Xbox Live n'est pas disponible dans ton pays.".into(),
        Some(2148916236) | Some(2148916237) => {
            "vérification d'âge requise sur ton compte Microsoft (account.microsoft.com).".into()
        }
        Some(2148916238) => {
            "compte enfant : il doit être ajouté à une famille Microsoft par un compte parent (family.microsoft.com).".into()
        }
        Some(2148916227) => "ce compte est banni de Xbox Live.".into(),
        Some(c) => format!("erreur Xbox XErr {c} — réessaie, puis contacte le staff avec ce code si ça persiste."),
        None => "aucune raison donnée — réessaie dans quelques minutes.".into(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn codes_xsts_traduits() {
        assert!(explain_xsts_error(Some(2148916235)).contains("pays"));
        assert!(explain_xsts_error(Some(42)).contains("XErr 42"));
    }
}
