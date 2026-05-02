//! Etapes Minecraft Services : login_with_xbox + recuperation du profil.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §7.3.

use serde::Deserialize;
use serde_json::json;

use super::error::{AuthError, AuthResult};

const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

/// Reponse de l'API Mojang : c'est le token utilise par le client MC.
#[derive(Debug, Deserialize)]
pub struct MinecraftAuth {
    pub access_token: String,
    #[serde(default)]
    pub expires_in: Option<u64>,
}

/// Profil Minecraft : UUID (sans tirets) + pseudo actuel.
#[derive(Debug, Deserialize, Clone)]
pub struct MinecraftProfile {
    pub id: String,   // UUID en hex sans tirets
    pub name: String, // pseudo MC actuel
}

/// Echange (userhash, xsts_token) contre l'access_token Mojang.
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
        let status = resp.status();
        let txt = resp.text().await.unwrap_or_default();
        return Err(AuthError::MicrosoftResponse(format!(
            "minecraftservices login {status} : {txt}"
        )));
    }

    Ok(resp.json::<MinecraftAuth>().await?)
}

/// Recupere le profil. 404 = pas de licence Java sur ce compte.
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
        let status = resp.status();
        let txt = resp.text().await.unwrap_or_default();
        return Err(AuthError::MicrosoftResponse(format!(
            "minecraftservices profile {status} : {txt}"
        )));
    }

    Ok(resp.json::<MinecraftProfile>().await?)
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
}
