//! Étapes Minecraft Services : login_with_xbox + récupération du profil.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §7.3.

use serde::Deserialize;
use serde_json::json;

use super::error::{detail_suffix, AuthError, AuthResult};
use crate::api::{decode_json, extract_api_message};

const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

/// Réponse de l'API Mojang : c'est le token utilisé par le client MC.
#[derive(Deserialize)]
pub struct MinecraftAuth {
    pub access_token: String,
    /// Durée de vie du jeton en secondes (86 400 chez Mojang).
    #[serde(default)]
    pub expires_in: Option<u64>,
}

/// `Debug` masqué : un `{:?}` dans un log ne doit jamais sortir de jeton.
impl std::fmt::Debug for MinecraftAuth {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("MinecraftAuth")
            .field("access_token", &"<masqué>")
            .field("expires_in", &self.expires_in)
            .finish()
    }
}

/// Profil Minecraft : UUID (sans tirets) + pseudo actuel.
#[derive(Debug, Deserialize, Clone)]
pub struct MinecraftProfile {
    pub id: String,   // UUID en hex sans tirets
    pub name: String, // pseudo MC actuel
}

/// Échange (userhash, xsts_token) contre l'access_token Mojang.
pub async fn login_with_xbox(
    http: &reqwest::Client,
    user_hash: &str,
    xsts_token: &str,
) -> AuthResult<MinecraftAuth> {
    let body = json!({
        "identityToken": format!("XBL3.0 x={};{}", user_hash, xsts_token),
    });

    let resp = http
        .post(MC_LOGIN_URL)
        .header("Accept", "application/json")
        .json(&body)
        .send()
        .await?;

    if !resp.status().is_success() {
        let status = resp.status().as_u16();
        let txt = resp.text().await.unwrap_or_default();
        return Err(services_error("la connexion à Minecraft", status, &txt));
    }

    Ok(decode_json::<MinecraftAuth>(resp).await?)
}

/// Récupère le profil. 404 = pas de licence Java sur ce compte.
pub async fn fetch_profile(
    http: &reqwest::Client,
    mc_access_token: &str,
) -> AuthResult<MinecraftProfile> {
    let resp = http
        .get(MC_PROFILE_URL)
        .bearer_auth(mc_access_token)
        .send()
        .await?;

    if resp.status() == reqwest::StatusCode::NOT_FOUND {
        return Err(AuthError::NoMinecraftLicense);
    }
    if !resp.status().is_success() {
        let status = resp.status().as_u16();
        let txt = resp.text().await.unwrap_or_default();
        return Err(services_error(
            "la lecture de ton profil Minecraft",
            status,
            &txt,
        ));
    }

    Ok(decode_json::<MinecraftProfile>(resp).await?)
}

/// Réponse non-2xx de api.minecraftservices.com (corps Mojang :
/// `{ errorMessage, error, path }`). `step` décrit l'étape en cours.
fn services_error(step: &str, status: u16, body: &str) -> AuthError {
    let detail = extract_api_message(body).unwrap_or_default();
    let message = match status {
        429 => format!(
            "Trop de tentatives pendant {step} : réessaie dans quelques minutes (HTTP 429)."
        ),
        500..=599 => format!(
            "Les serveurs Minecraft (Mojang) ont un problème pendant {step} (HTTP {status}) — réessaie plus tard."
        ),
        _ => format!(
            "Minecraft Services a refusé {step}{} (HTTP {status}) — relance la connexion Microsoft.",
            detail_suffix(&detail)
        ),
    };
    AuthError::MinecraftServices { status, message }
}

/// Convertit un UUID brut (32 hex chars) en forme avec tirets 8-4-4-4-12.
pub fn dashed_uuid(id: &str) -> String {
    if id.len() != 32 || !id.chars().all(|c| c.is_ascii_hexdigit()) {
        return id.to_string();
    }
    format!(
        "{}-{}-{}-{}-{}",
        &id[0..8],
        &id[8..12],
        &id[12..16],
        &id[16..20],
        &id[20..32]
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dashed_uuid_inserts_hyphens() {
        let raw = "069a79f444e94726a5befca90e38aaf5";
        let dashed = dashed_uuid(raw);
        assert_eq!(dashed, "069a79f4-44e9-4726-a5be-fca90e38aaf5");
    }

    #[test]
    fn dashed_uuid_passthrough_when_invalid() {
        assert_eq!(dashed_uuid("not-a-uuid"), "not-a-uuid");
    }

    #[test]
    fn erreur_minecraft_services_avec_message_mojang() {
        let err = services_error(
            "la connexion à Minecraft",
            403,
            r#"{"path":"/authentication/login_with_xbox","errorMessage":"Invalid app registration"}"#,
        );
        assert_eq!(
            err.to_string(),
            "Minecraft Services a refusé la connexion à Minecraft : Invalid app registration (HTTP 403) — relance la connexion Microsoft."
        );
    }
}
