use serde::Serialize;
use thiserror::Error;

use crate::api::{self, ApiError};
use crate::storage::secrets::StorageError;

/// Erreurs du flow d'authentification — converties proprement en payload
/// IPC `{ kind, message, code }`. Chaque message est une phrase française
/// complète (cause réelle + quoi faire) et ne contient jamais de jeton.
#[derive(Error, Debug)]
pub enum AuthError {
    #[error("Launcher mal configuré : {0}")]
    Config(String),

    /// Échec réseau vers Microsoft / Xbox Live / Minecraft Services (le
    /// message nomme le service et la cause : délai, DNS, TLS…).
    #[error("{}", api::transport_message(&service_name(.0), .0))]
    Http(#[from] reqwest::Error),

    /// Erreur système sur la socket locale du retour OAuth (127.0.0.1:53682).
    #[error("Retour de connexion Microsoft interrompu : {0}")]
    Io(#[from] std::io::Error),

    /// Le port du retour OAuth est déjà pris (double clic, autre launcher…).
    #[error("Port 53682 indisponible pour le retour de connexion Microsoft (une autre connexion est déjà en cours ? ferme-la ou redémarre le launcher) : {0}")]
    CallbackPort(std::io::Error),

    /// Refus du endpoint OAuth Microsoft. `error_code` = champ `error` OAuth
    /// (`invalid_grant`, `interaction_required`…), exposé dans `code`.
    #[error("{message}")]
    MicrosoftResponse {
        status: Option<u16>,
        error_code: Option<String>,
        message: String,
    },

    /// Refus de api.minecraftservices.com (login_with_xbox, profil).
    #[error("{message}")]
    MinecraftServices { status: u16, message: String },

    /// `message` = détail Xbox : phrase complète avec sa propre consigne
    /// (code XErr traduit) ou message brut du service, voire vide.
    #[error("{}", xbox_message(.code, .message))]
    Xbox { code: String, message: String },

    #[error("Xbox Live a refusé l'accès : {reason}")]
    Xsts { code: String, reason: String },

    #[error("Aucune licence Minecraft Java Edition sur ce compte Microsoft (ou profil Java pas encore créé) : connecte-toi avec le compte qui possède le jeu, ou choisis ton pseudo une fois dans le launcher officiel.")]
    NoMinecraftLicense,

    #[error("Connexion annulée : l'autorisation a été refusée dans la fenêtre Microsoft.")]
    UserCanceled,

    #[error("Délai dépassé : {0}")]
    Timeout(String),

    #[error(transparent)]
    Storage(#[from] StorageError),

    /// Erreur de l'API Reborn pendant l'authentification (le message porte
    /// déjà le message NestJS + code HTTP, ou la cause réseau).
    #[error("{message}")]
    Api {
        status: Option<u16>,
        message: String,
    },

    #[error("Impossible d'ouvrir ton navigateur pour la connexion Microsoft : {0}")]
    Browser(String),

    #[error("Erreur interne du launcher : {0}")]
    Internal(String),

    // Erreurs spécifiques au "switch silencieux" (auth_login_with_saved_account).
    // Le frontend déclenche le fallback OAuth interactif uniquement sur ces deux
    // kinds — les autres (http, api, internal…) sont des erreurs réseau/serveur
    // qu'il faut afficher sans rouvrir la fenêtre MS.
    #[error("Aucun identifiant enregistré pour ce compte : reconnecte-toi avec Microsoft.")]
    NoStoredCredentials,

    #[error("Les identifiants enregistrés pour ce compte ont expiré ou ont été révoqués : reconnecte-toi avec Microsoft.")]
    StoredCredentialsExpired,
}

impl AuthError {
    /// Erreur de l'API Reborn (refresh, logout, dev-login…).
    pub(crate) fn reborn_api(e: ApiError) -> Self {
        Self::Api {
            status: e.status(),
            message: e.to_string(),
        }
    }

    /// Erreur de `/auth/login` : un 401 n'y signifie pas « session expirée »
    /// mais « l'API n'a pas pu valider le compte Minecraft ».
    pub(crate) fn reborn_login(e: ApiError) -> Self {
        if e.status() == Some(401) {
            return Self::Api {
                status: Some(401),
                message: format!(
                    "Le serveur Reborn n'a pas pu valider ton compte Minecraft{} (HTTP 401) — relance la connexion Microsoft.",
                    detail_suffix(&e.api_message().unwrap_or_default())
                ),
            };
        }
        Self::reborn_api(e)
    }

    /// Vrai si Microsoft a explicitement révoqué / expiré le refresh token
    /// (et pas une panne passagère) : seul cas où l'on peut effacer les
    /// jetons stockés d'un compte.
    pub(crate) fn is_revoked_grant(&self) -> bool {
        matches!(
            self,
            Self::MicrosoftResponse { error_code: Some(code), .. }
                if matches!(
                    code.as_str(),
                    "invalid_grant" | "interaction_required" | "consent_required" | "login_required"
                )
        )
    }

    pub(crate) fn kind(&self) -> &'static str {
        match self {
            Self::Config(_) => "config",
            Self::Http(_) => "http",
            Self::Io(_) | Self::CallbackPort(_) => "io",
            Self::MicrosoftResponse { .. } => "microsoft_response",
            Self::MinecraftServices { .. } => "minecraft_services",
            Self::Xbox { .. } => "xbox",
            Self::Xsts { .. } => "xsts",
            Self::NoMinecraftLicense => "no_minecraft_license",
            Self::UserCanceled => "user_canceled",
            Self::Timeout(_) => "timeout",
            Self::Storage(_) => "storage",
            Self::Api { .. } => "api",
            Self::Browser(_) => "browser",
            Self::Internal(_) => "internal",
            Self::NoStoredCredentials => "no_stored_credentials",
            Self::StoredCredentialsExpired => "stored_credentials_expired",
        }
    }

    /// Code technique complémentaire (code Xbox/XSTS, erreur OAuth, statut
    /// HTTP) — jamais un jeton.
    fn code(&self) -> Option<String> {
        match self {
            Self::Xbox { code, .. } | Self::Xsts { code, .. } => Some(code.clone()),
            Self::MicrosoftResponse {
                error_code, status, ..
            } => error_code.clone().or_else(|| status.map(|s| s.to_string())),
            Self::MinecraftServices { status, .. } => Some(status.to_string()),
            Self::Api { status, .. } => status.map(|s| s.to_string()),
            _ => None,
        }
    }
}

/// Nom lisible du service visé par une requête reqwest (hôte seul, jamais
/// le chemin ni la query).
fn service_name(e: &reqwest::Error) -> String {
    match e.url().and_then(|u| u.host_str()) {
        Some(h) if h.ends_with("microsoftonline.com") => {
            "Microsoft (login.microsoftonline.com)".into()
        }
        Some(h) if h.ends_with("xboxlive.com") => format!("Xbox Live ({h})"),
        Some(h) if h.ends_with("minecraftservices.com") => {
            "Minecraft Services (api.minecraftservices.com)".into()
        }
        Some(h) => h.to_string(),
        None => "le service de connexion".into(),
    }
}

/// Refus Xbox Live : la consigne générique n'est ajoutée que si le détail
/// n'en porte pas déjà une. Un code XErr traduit est une phrase terminée
/// par un point, avec sa propre consigne ; un message brut du service
/// (`extract_api_message`) n'a pas de point final.
fn xbox_message(code: &str, detail: &str) -> String {
    const GENERIC: &str = "réessaie dans quelques minutes ou vérifie ton compte sur xbox.com.";
    let head = format!("Xbox Live a refusé l'authentification (HTTP {code})");
    let d = detail.trim();
    if d.is_empty() {
        format!("{head} : {GENERIC}")
    } else if d.ends_with(['.', '!', '?', '…']) {
        format!("{head} : {d}")
    } else {
        format!("{head} : {d}. Réessaie dans quelques minutes ou vérifie ton compte sur xbox.com.")
    }
}

/// « : <détail> » si le détail est non vide, sinon rien.
pub(super) fn detail_suffix(detail: &str) -> String {
    let d = detail.trim();
    if d.is_empty() {
        String::new()
    } else {
        format!(" : {d}")
    }
}

/// Payload sérialisable côté frontend pour distinguer les erreurs métier
/// (XSTS country block, no MC license, etc.) de l'erreur technique brute.
#[derive(Debug, Serialize)]
pub struct AuthErrorPayload {
    pub kind: &'static str,
    pub message: String,
    pub code: Option<String>,
}

impl From<&AuthError> for AuthErrorPayload {
    fn from(err: &AuthError) -> Self {
        Self {
            kind: err.kind(),
            message: err.to_string(),
            code: err.code(),
        }
    }
}

impl serde::Serialize for AuthError {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        AuthErrorPayload::from(self).serialize(serializer)
    }
}

pub type AuthResult<T> = Result<T, AuthError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn serialise_kind_message_et_code() {
        let json = serde_json::to_value(AuthError::UserCanceled).unwrap();
        assert_eq!(json["kind"], "user_canceled");
        assert_eq!(
            json["message"],
            "Connexion annulée : l'autorisation a été refusée dans la fenêtre Microsoft."
        );
        assert!(json["code"].is_null());

        let json = serde_json::to_value(AuthError::NoStoredCredentials).unwrap();
        assert_eq!(json["kind"], "no_stored_credentials");

        let xsts = AuthError::Xsts {
            code: "2148916235".into(),
            reason: "Xbox Live n'est pas disponible dans ton pays.".into(),
        };
        let json = serde_json::to_value(&xsts).unwrap();
        assert_eq!(json["kind"], "xsts");
        assert_eq!(json["code"], "2148916235");
        assert_eq!(
            json["message"],
            "Xbox Live a refusé l'accès : Xbox Live n'est pas disponible dans ton pays."
        );
    }

    #[test]
    fn erreur_api_au_login_reformulee() {
        let e = ApiError::Status {
            status: 401,
            body: r#"{"statusCode":401,"message":"Token Minecraft invalide"}"#.into(),
        };
        let err = AuthError::reborn_login(e);
        assert_eq!(err.kind(), "api");
        assert_eq!(
            err.to_string(),
            "Le serveur Reborn n'a pas pu valider ton compte Minecraft : Token Minecraft invalide (HTTP 401) — relance la connexion Microsoft."
        );
        let json = serde_json::to_value(&err).unwrap();
        assert_eq!(json["code"], "401");
    }

    #[test]
    fn refus_xbox_sans_consigne_doublee() {
        let traduit = AuthError::Xbox {
            code: "400".into(),
            message: "ce compte Microsoft n'a pas encore de profil Xbox. Crée-en un sur xbox.com puis réessaie.".into(),
        };
        assert_eq!(
            traduit.to_string(),
            "Xbox Live a refusé l'authentification (HTTP 400) : ce compte Microsoft n'a pas encore de profil Xbox. Crée-en un sur xbox.com puis réessaie."
        );
        let vide = AuthError::Xbox {
            code: "503".into(),
            message: String::new(),
        };
        assert_eq!(
            vide.to_string(),
            "Xbox Live a refusé l'authentification (HTTP 503) : réessaie dans quelques minutes ou vérifie ton compte sur xbox.com."
        );
        let brut = AuthError::Xbox {
            code: "400".into(),
            message: "Bad token".into(),
        };
        assert_eq!(
            brut.to_string(),
            "Xbox Live a refusé l'authentification (HTTP 400) : Bad token. Réessaie dans quelques minutes ou vérifie ton compte sur xbox.com."
        );
    }

    #[test]
    fn seuls_les_refus_explicites_revoquent() {
        let revoked = AuthError::MicrosoftResponse {
            status: Some(400),
            error_code: Some("invalid_grant".into()),
            message: String::new(),
        };
        assert!(revoked.is_revoked_grant());
        let outage = AuthError::MicrosoftResponse {
            status: Some(503),
            error_code: Some("temporarily_unavailable".into()),
            message: String::new(),
        };
        assert!(!outage.is_revoked_grant());
    }
}
