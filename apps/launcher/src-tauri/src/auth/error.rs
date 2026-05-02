use serde::Serialize;
use thiserror::Error;

/// Erreurs du flow d'authentification — converties proprement en payload IPC.
#[derive(Error, Debug)]
pub enum AuthError {
    #[error("configuration manquante : {0}")]
    Config(String),

    #[error("requete HTTP echouee : {0}")]
    Http(#[from] reqwest::Error),

    #[error("erreur reseau : {0}")]
    Io(#[from] std::io::Error),

    #[error("reponse Microsoft inattendue : {0}")]
    MicrosoftResponse(String),

    #[error("Xbox Live a refuse l'authentification (code {code})")]
    Xbox { code: String, message: String },

    #[error("XSTS a refuse l'authentification : {reason}")]
    Xsts { code: String, reason: String },

    #[error("aucune licence Minecraft Java sur ce compte")]
    NoMinecraftLicense,

    #[error("flow OAuth annule par l'utilisateur")]
    UserCanceled,

    #[error("delai depasse : {0}")]
    Timeout(String),

    #[error("erreur de stockage du refresh token : {0}")]
    Storage(String),

    #[error("erreur interne : {0}")]
    Internal(String),
}

/// Payload serialisable cote frontend pour distinguer les erreurs metier
/// (XSTS country block, no MC license, etc.) de l'erreur technique brute.
#[derive(Debug, Serialize)]
pub struct AuthErrorPayload {
    pub kind: &'static str,
    pub message: String,
    pub code: Option<String>,
}

impl From<&AuthError> for AuthErrorPayload {
    fn from(err: &AuthError) -> Self {
        let (kind, code) = match err {
            AuthError::Config(_) => ("config", None),
            AuthError::Http(_) => ("http", None),
            AuthError::Io(_) => ("io", None),
            AuthError::MicrosoftResponse(_) => ("microsoft_response", None),
            AuthError::Xbox { code, .. } => ("xbox", Some(code.clone())),
            AuthError::Xsts { code, .. } => ("xsts", Some(code.clone())),
            AuthError::NoMinecraftLicense => ("no_minecraft_license", None),
            AuthError::UserCanceled => ("user_canceled", None),
            AuthError::Timeout(_) => ("timeout", None),
            AuthError::Storage(_) => ("storage", None),
            AuthError::Internal(_) => ("internal", None),
        };
        Self {
            kind,
            message: err.to_string(),
            code,
        }
    }
}

impl serde::Serialize for AuthError {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        AuthErrorPayload::from(self).serialize(serializer)
    }
}

pub type AuthResult<T> = Result<T, AuthError>;
