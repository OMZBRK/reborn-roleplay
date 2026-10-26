//! Carte « Discord officiel » de l'accueil (CANDIDATURE_ET_DOUANE §5.3) :
//! compteur membres / en ligne du Discord des joueurs, lu sur l'endpoint
//! PUBLIC `/menu/panel` de l'API (le même que le menu Échap du mod). Sans
//! JWT : la carte s'affiche aussi pendant un renouvellement de session.
//! L'API lit l'invitation publique et met le compteur en cache ; le launcher
//! ne parle jamais à Discord directement.

use serde::{Deserialize, Serialize};
use tauri::State;

use super::ContentError;
use crate::auth::AuthState;

/// Seuls liens que la carte accepte d'ouvrir depuis la réponse de l'API :
/// une invitation Discord, rien d'autre.
const INVITE_PREFIXES: [&str; 2] = ["https://discord.gg/", "https://discord.com/invite/"];

/// Compteur du Discord des joueurs, tel que le front l'affiche.
#[derive(Debug, Deserialize, Serialize, Clone, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct DiscordCommunity {
    pub members: u64,
    pub online: u64,
    /// Invitation renvoyée par l'API. `None` si absente ou refusée : le front
    /// ouvre alors son lien de repli constant.
    #[serde(default)]
    pub invite_url: Option<String>,
    /// Nom du serveur tel que Discord le renvoie.
    #[serde(default)]
    pub name: Option<String>,
}

/// Seul champ du panneau utile au launcher (patch notes et streams servent
/// au menu Échap du mod) ; les autres sont ignorés.
#[derive(Debug, Deserialize)]
struct MenuPanel {
    #[serde(default)]
    discord: Option<DiscordCommunity>,
}

fn is_discord_invite(url: &str) -> bool {
    INVITE_PREFIXES.iter().any(|prefix| {
        url.strip_prefix(prefix).is_some_and(|code| {
            !code.is_empty()
                && code.len() <= 32
                && code.chars().all(|c| c.is_ascii_alphanumeric() || c == '-')
        })
    })
}

fn sanitize(mut discord: DiscordCommunity) -> DiscordCommunity {
    discord.invite_url = discord.invite_url.filter(|u| is_discord_invite(u));
    discord
}

/// Compteur Discord de l'accueil. `Ok(None)` = compteur indisponible côté API
/// (invitation absente ou expirée, Discord muet) : la carte l'affiche et son
/// bouton garde le lien de repli. `Err` = API injoignable.
#[tauri::command]
pub async fn community_discord(
    state: State<'_, AuthState>,
) -> Result<Option<DiscordCommunity>, ContentError> {
    let panel: MenuPanel = state.api.get_json_public("/menu/panel").await?;
    Ok(panel.discord.map(sanitize))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn panel(json: &str) -> Option<DiscordCommunity> {
        serde_json::from_str::<MenuPanel>(json)
            .unwrap()
            .discord
            .map(sanitize)
    }

    #[test]
    fn lit_le_compteur_et_ignore_le_reste_du_panneau() {
        let d = panel(
            r#"{"discord":{"members":1238,"online":214,"inviteUrl":"https://discord.gg/Nw7jpjJ6dg","name":"Reborn Roleplay"},
                "patchNotes":[{"id":"1"}],"streams":[]}"#,
        )
        .unwrap();
        assert_eq!(d.members, 1238);
        assert_eq!(d.online, 214);
        assert_eq!(
            d.invite_url.as_deref(),
            Some("https://discord.gg/Nw7jpjJ6dg")
        );
        assert_eq!(d.name.as_deref(), Some("Reborn Roleplay"));

        // Réponse camelCase vers le front.
        let json = serde_json::to_value(&d).unwrap();
        assert_eq!(json["inviteUrl"], "https://discord.gg/Nw7jpjJ6dg");
    }

    #[test]
    fn compteur_indisponible_ou_ancienne_api() {
        assert_eq!(
            panel(r#"{"discord":null,"patchNotes":[],"streams":[]}"#),
            None
        );
        assert_eq!(panel(r#"{"patchNotes":[]}"#), None);
        // Ancienne API : pas de lien ni de nom, le front prend son repli.
        let d = panel(r#"{"discord":{"members":3,"online":1}}"#).unwrap();
        assert_eq!(d.invite_url, None);
        assert_eq!(d.name, None);
    }

    #[test]
    fn n_ouvre_qu_une_invitation_discord() {
        assert!(is_discord_invite("https://discord.gg/Nw7jpjJ6dg"));
        assert!(is_discord_invite("https://discord.com/invite/Nw7jpjJ6dg"));
        assert!(!is_discord_invite("http://discord.gg/Nw7jpjJ6dg"));
        assert!(!is_discord_invite("https://discord.gg/"));
        assert!(!is_discord_invite("https://discord.gg/a/../b"));
        assert!(!is_discord_invite("https://discord.gg.exemple.test/x"));
        assert!(!is_discord_invite("https://exemple.test/discord.gg/x"));

        let d =
            panel(r#"{"discord":{"members":1,"online":0,"inviteUrl":"https://exemple.test/x"}}"#)
                .unwrap();
        assert_eq!(d.invite_url, None);
    }
}
