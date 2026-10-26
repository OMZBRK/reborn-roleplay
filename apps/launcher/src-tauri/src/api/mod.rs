//! Client HTTP fin vers l'API Reborn (cf §3.1 : pas d'appels HTTP côté
//! frontend, tout passe par le backend Rust).
//!
//! Priorité des sources pour l'URL API :
//!   1. Env runtime `REBORN_API_URL` (debug local override)
//!   2. Env compile-time `REBORN_API_URL_BUILD` (positionné par le
//!      maintainer avant `pnpm launcher:build` pour cibler prod)
//!   3. Fallback `http://localhost:3000/v1` (dev sans config)
//!
//! Erreurs : toute réponse non-2xx devient [`ApiError::Status`] et toute
//! erreur de transport [`ApiError::Http`]. Leur `Display` est une phrase
//! française complète prête à afficher : message renvoyé par NestJS
//! (`{ statusCode, message: string | string[], error }`) + code HTTP, ou
//! cause réseau réelle (délai, connexion refusée, DNS, TLS…). Le corps des
//! REQUÊTES n'y figure jamais (il porte les jetons).

use std::error::Error as StdError;

use serde::{Deserialize, Serialize};
use serde_json::Value as Json;

const FALLBACK_API_URL: &str = "http://localhost:3000/v1";

/// Longueur max d'un message d'erreur repris d'un corps de réponse : au-delà
/// c'est presque toujours une trace ou une page complète, illisible en toast.
const MAX_DETAIL_CHARS: usize = 300;

fn default_api_url() -> String {
    if let Ok(runtime) = std::env::var("REBORN_API_URL") {
        return runtime;
    }
    option_env!("REBORN_API_URL_BUILD")
        .unwrap_or(FALLBACK_API_URL)
        .to_string()
}

#[derive(Debug, Clone)]
pub struct ApiClient {
    pub base_url: String,
    pub http: reqwest::Client,
}

impl ApiClient {
    pub fn new() -> Self {
        let base_url = default_api_url();
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

// Les structures qui portent un jeton n'ont pas de `Debug` dérivé : soit
// aucun, soit un `Debug` masqué. Un `{:?}` dans un log ne doit jamais
// sortir de secret.

/// Payload envoyé à `/auth/login` côté API : on transmet juste l'access
/// token Mojang, l'API valide elle-même contre `/minecraft/profile`.
#[derive(Serialize)]
pub struct LoginRequest {
    #[serde(rename = "mcAccessToken")]
    pub mc_access_token: String,
}

#[derive(Debug, Serialize)]
pub struct DevLoginRequest {
    pub username: String,
}

#[derive(Serialize)]
pub struct RefreshRequest {
    #[serde(rename = "refreshToken")]
    pub refresh_token: String,
}

#[derive(Deserialize, Serialize, Clone)]
pub struct AuthResponse {
    #[serde(rename = "accessToken")]
    pub access_token: String,
    #[serde(rename = "refreshToken")]
    pub refresh_token: String,
    pub user: ApiUser,
}

impl std::fmt::Debug for AuthResponse {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("AuthResponse")
            .field("access_token", &"<masqué>")
            .field("refresh_token", &"<masqué>")
            .field("user", &self.user)
            .finish()
    }
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
    pub discord: Option<DiscordLinkage>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct DiscordLinkage {
    pub user_id: String,
    pub username: String,
    pub linked_at: String,
}

#[derive(Debug, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct UploadResponse {
    pub url: String,
}

/// Miroir Rust de `ShotView` côté API (`apps/api/src/shots`). Représente un
/// screenshot partagé sur le feed social.
#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct ShotView {
    pub id: String,
    pub url: String,
    pub caption: Option<String>,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub like_count: u32,
    pub liked_by_me: bool,
    pub created_at: String,
    pub author: ShotAuthor,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct ShotAuthor {
    pub id: String,
    pub name: String,
    pub avatar_url: Option<String>,
}

#[derive(Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct PlaySession {
    pub play_token: String,
    pub expires_at: String,
    /// Portée du jeton, `play` ou `douane` (CANDIDATURE_ET_DOUANE.md §3.3).
    /// Absente des réponses d'une API d'avant le 25/10. Le jeton fait foi
    /// côté serveur : ce champ ne sert qu'à l'affichage et au journal.
    #[serde(default)]
    pub scope: Option<String>,
    /// Serveur visé (claim `aud`), ou `None` si l'API n'en émet pas.
    #[serde(default)]
    pub aud: Option<String>,
}

impl std::fmt::Debug for PlaySession {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("PlaySession")
            .field("play_token", &"<masqué>")
            .field("expires_at", &self.expires_at)
            .field("scope", &self.scope)
            .field("aud", &self.aud)
            .finish()
    }
}

/// Corps de `POST /play/session` : le serveur que le launcher va rejoindre
/// (même valeur que la clé `server-id` de Guardian). Sans `serveur`, aucun
/// corps n'est envoyé : l'API vise son serveur par défaut.
#[derive(Debug, Serialize)]
struct PlaySessionRequest<'a> {
    serveur: &'a str,
}

// ──────────────────────────────────────────────────────
//  Erreurs
// ──────────────────────────────────────────────────────

#[derive(Debug, thiserror::Error)]
pub enum ApiError {
    /// Échec de transport (délai, connexion refusée, DNS, TLS) ou réponse
    /// 2xx illisible (JSON qui ne correspond pas au DTO attendu).
    #[error("{}", describe_reborn_transport(.0))]
    Http(#[from] reqwest::Error),
    /// Réponse non-2xx. `body` = corps brut ; il n'est jamais affiché tel
    /// quel, le message en extrait le champ `message` de NestJS.
    #[error("{}", describe_status(*.status, .body))]
    Status { status: u16, body: String },
}

impl ApiError {
    /// Code HTTP pour une réponse non-2xx, `None` pour une erreur réseau.
    pub fn status(&self) -> Option<u16> {
        match self {
            Self::Status { status, .. } => Some(*status),
            Self::Http(e) => e.status().map(|s| s.as_u16()),
        }
    }

    /// Message brut renvoyé par l'API (champ `message` NestJS), s'il est
    /// exploitable. Utile aux appelants qui veulent reformuler (ex. login).
    pub fn api_message(&self) -> Option<String> {
        match self {
            Self::Status { body, .. } => {
                extract_api_message(body).filter(|m| !is_generic_reason(m))
            }
            Self::Http(_) => None,
        }
    }

    /// Catégorie stable (snake_case) exposée au front dans `kind`.
    pub fn kind(&self) -> &'static str {
        match self {
            Self::Http(e) if e.is_decode() => "unexpected_response",
            Self::Http(e) if e.is_builder() => "internal",
            Self::Http(_) => "network",
            Self::Status { status, .. } => match status {
                401 => "session_expired",
                403 => "forbidden",
                404 => "not_found",
                409 => "conflict",
                413 => "payload_too_large",
                429 => "rate_limited",
                500..=599 => "server_error",
                _ => "api",
            },
        }
    }
}

impl Serialize for ApiError {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        use serde::ser::SerializeStruct;
        let mut s = serializer.serialize_struct("ApiError", 2)?;
        s.serialize_field("kind", self.kind())?;
        s.serialize_field("message", &self.to_string())?;
        s.end()
    }
}

/// Message pour une réponse non-2xx de l'API Reborn.
fn describe_status(status: u16, body: &str) -> String {
    let api = extract_api_message(body).filter(|m| !is_generic_reason(m));
    match status {
        401 => "Session expirée : reconnecte-toi (HTTP 401).".to_string(),
        403 => format!(
            "Accès refusé : {} (HTTP 403).",
            api.unwrap_or_else(|| "tu n'as pas les droits nécessaires pour cette action".into())
        ),
        // NestJS répond « Cannot GET /v1/… » pour une route inconnue :
        // c'est presque toujours un launcher plus récent que l'API (ou
        // l'inverse), pas un élément manquant.
        404 => match api {
            Some(m) if m.starts_with("Cannot ") => format!(
                "Fonction indisponible sur le serveur Reborn ({m}) : launcher et serveur ne sont pas à la même version (HTTP 404)."
            ),
            Some(m) => format!("{m} (HTTP 404)."),
            None => "Élément introuvable sur le serveur Reborn (HTTP 404).".to_string(),
        },
        409 => format!(
            "{} (HTTP 409).",
            api.unwrap_or_else(|| "Conflit : cette action a déjà été faite ou n'est plus possible".into())
        ),
        // L'API dit elle-même quoi faire quand elle le peut (enregistrement
        // vocal trop lourd, brouillon trop gros) ; sinon, cas d'une image.
        413 => match api {
            Some(m) => format!("{m} (HTTP 413)."),
            None => "Fichier trop volumineux pour le serveur Reborn (HTTP 413) : choisis une image plus légère ou réduis-la avant de l'envoyer.".to_string(),
        },
        429 => "Trop de requêtes, réessaie dans un instant (HTTP 429).".to_string(),
        500..=599 => match api {
            Some(m) => format!(
                "Le serveur Reborn a un problème (HTTP {status} : {m}) — réessaie plus tard."
            ),
            None => format!("Le serveur Reborn a un problème (HTTP {status}) — réessaie plus tard."),
        },
        _ => format!(
            "{} (HTTP {status}).",
            api.unwrap_or_else(|| "Le serveur Reborn a refusé la requête".into())
        ),
    }
}

/// Message pour une erreur de transport vers l'API Reborn.
fn describe_reborn_transport(e: &reqwest::Error) -> String {
    transport_message("le serveur Reborn", e)
}

/// Phrase complète pour une erreur reqwest vers `target` (« le serveur
/// Reborn », « Microsoft », « Xbox Live »…). Réutilisable par les autres
/// modules qui parlent HTTP (téléchargements, auth Microsoft).
///
/// `target` sert de sujet ou de complément sans préposition contractée
/// (« le serveur Reborn a renvoyé… », jamais « de le serveur »). Le détail
/// serde d'une réponse illisible (anglais, technique) part dans les logs,
/// pas dans le message.
pub fn transport_message(target: &str, e: &reqwest::Error) -> String {
    if e.is_decode() {
        let endpoint = endpoint_suffix(e);
        tracing::warn!(
            "réponse illisible de {target}{endpoint} : {}",
            root_cause(e)
        );
        return format!(
            "{} a renvoyé une réponse illisible{endpoint} : mets le launcher à jour si le problème persiste.",
            capitalize_first(target)
        );
    }
    if e.is_builder() {
        return format!("Requête invalide vers {target} : {}.", root_cause(e));
    }
    if let Some(status) = e.status() {
        return format!(
            "{} a répondu une erreur (HTTP {}).",
            capitalize_first(target),
            status.as_u16()
        );
    }
    format!(
        "Impossible de joindre {target} ({}) — vérifie ta connexion.",
        network_cause(e)
    )
}

/// Cause réseau réelle, en français, à partir de la chaîne d'erreurs
/// reqwest → hyper → io. Ne contient jamais l'URL (ni donc de paramètres).
pub fn network_cause(e: &reqwest::Error) -> String {
    let chain = error_chain_lower(e);
    if e.is_timeout() || chain.contains("timed out") {
        return "délai dépassé".to_string();
    }
    if chain.contains("dns error") || chain.contains("failed to lookup") {
        return "adresse introuvable (DNS)".to_string();
    }
    if let Some(io) = find_io_error(e) {
        use std::io::ErrorKind;
        match io.kind() {
            ErrorKind::ConnectionRefused => {
                return "connexion refusée : le serveur est arrêté ou injoignable".to_string()
            }
            ErrorKind::ConnectionReset | ErrorKind::ConnectionAborted => {
                return "connexion coupée par le serveur ou un pare-feu".to_string()
            }
            ErrorKind::TimedOut => return "délai dépassé".to_string(),
            _ => {}
        }
    }
    if chain.contains("certificate") || chain.contains("tls") || chain.contains("handshake") {
        return format!("erreur de sécurité TLS : {}", root_cause(e));
    }
    if e.is_body() {
        return format!("réception interrompue : {}", root_cause(e));
    }
    root_cause(e)
}

/// Message de l'erreur la plus profonde (souvent l'erreur OS, localisée par
/// Windows), sans le « error sending request for url (…) » de reqwest.
fn root_cause(e: &(dyn StdError + 'static)) -> String {
    let mut current = e;
    while let Some(next) = current.source() {
        current = next;
    }
    truncate_chars(&current.to_string(), MAX_DETAIL_CHARS)
}

fn error_chain_lower(e: &(dyn StdError + 'static)) -> String {
    let mut out = String::new();
    let mut current = e.source();
    while let Some(err) = current {
        out.push_str(&err.to_string().to_lowercase());
        out.push(' ');
        current = err.source();
    }
    out
}

fn find_io_error<'a>(e: &'a (dyn StdError + 'static)) -> Option<&'a std::io::Error> {
    let mut current = e.source();
    while let Some(err) = current {
        if let Some(io) = err.downcast_ref::<std::io::Error>() {
            return Some(io);
        }
        current = err.source();
    }
    None
}

/// « (GET /v1/patchnotes) » — chemin seul, sans query string.
/// Première lettre en majuscule (début de phrase).
fn capitalize_first(text: &str) -> String {
    let mut chars = text.chars();
    match chars.next() {
        Some(first) => first.to_uppercase().chain(chars).collect(),
        None => String::new(),
    }
}

fn endpoint_suffix(e: &reqwest::Error) -> String {
    e.url()
        .map(|u| format!(" ({})", u.path()))
        .unwrap_or_default()
}

/// Extrait le message lisible d'un corps d'erreur. Gère le format NestJS
/// (`message: string | string[]`), Mojang (`errorMessage`), OAuth
/// (`error_description`), Xbox (`Message`) et le texte brut court. Renvoie
/// `None` pour un corps vide ou une page HTML (proxy, Caddy…).
pub fn extract_api_message(body: &str) -> Option<String> {
    let trimmed = body.trim();
    if trimmed.is_empty() {
        return None;
    }
    let raw = match serde_json::from_str::<Json>(trimmed) {
        Ok(Json::Object(map)) => [
            "message",
            "errorMessage",
            "error_description",
            "Message",
            "error",
        ]
        .iter()
        .find_map(|key| map.get(*key).and_then(message_from_value)),
        Ok(other) => message_from_value(&other),
        Err(_) if trimmed.starts_with('<') => None,
        Err(_) => trimmed.lines().next().map(str::to_string),
    }?;
    let clean = raw.replace(['\r', '\n'], " ");
    let clean = clean.trim().trim_end_matches('.').trim_end();
    if clean.is_empty() {
        None
    } else {
        Some(truncate_chars(clean, MAX_DETAIL_CHARS))
    }
}

/// Tous les messages d'un corps d'erreur NestJS (`message` en chaîne ou en
/// tableau), sans troncature : pour les refus de validation champ par champ
/// (« history: Encore 38 caractères… »), que l'écran range sous chaque champ.
/// Vide si le corps n'a pas ce format.
pub fn api_messages(body: &str) -> Vec<String> {
    let Ok(Json::Object(map)) = serde_json::from_str::<Json>(body.trim()) else {
        return Vec::new();
    };
    match map.get("message") {
        Some(Json::String(s)) if !s.trim().is_empty() => vec![s.trim().to_string()],
        Some(Json::Array(items)) => items
            .iter()
            .filter_map(Json::as_str)
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .map(str::to_string)
            .collect(),
        _ => Vec::new(),
    }
}

/// `message` NestJS : chaîne, ou tableau de chaînes (erreurs de validation
/// class-validator) qu'on joint par « ; ».
fn message_from_value(v: &Json) -> Option<String> {
    match v {
        Json::String(s) if !s.trim().is_empty() => Some(s.trim().to_string()),
        Json::Array(items) => {
            let parts: Vec<&str> = items
                .iter()
                .filter_map(Json::as_str)
                .map(str::trim)
                .filter(|s| !s.is_empty())
                .collect();
            (!parts.is_empty()).then(|| parts.join(" ; "))
        }
        _ => None,
    }
}

/// Phrases par défaut de NestJS / HTTP qui n'apprennent rien à l'utilisateur
/// (« Forbidden resource », « Internal server error »…) : on les remplace
/// par le texte français du code HTTP.
fn is_generic_reason(msg: &str) -> bool {
    const GENERIC: &[&str] = &[
        "unauthorized",
        "forbidden",
        "forbidden resource",
        "not found",
        "bad request",
        "conflict",
        "too many requests",
        "throttlerexception: too many requests",
        "payload too large",
        "unprocessable entity",
        "request timeout",
        "internal server error",
        "bad gateway",
        "service unavailable",
        "gateway timeout",
    ];
    let lower = msg.trim().trim_end_matches('.').to_lowercase();
    GENERIC.contains(&lower.as_str())
}

/// Coupe proprement sur une frontière de caractère (jamais au milieu d'un
/// caractère accentué) et ajoute « … ».
pub fn truncate_chars(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        return s.to_string();
    }
    let mut out: String = s.chars().take(max).collect();
    out.push('…');
    out
}

// ──────────────────────────────────────────────────────
//  Appels
// ──────────────────────────────────────────────────────

/// Transforme une réponse non-2xx en [`ApiError::Status`] (corps conservé
/// pour en extraire le message), laisse passer les 2xx.
async fn ensure_success(resp: reqwest::Response) -> Result<reqwest::Response, ApiError> {
    let status = resp.status();
    if status.is_success() {
        return Ok(resp);
    }
    let body = resp.text().await.unwrap_or_default();
    tracing::debug!(
        "API {} : {}",
        status.as_u16(),
        truncate_chars(&body, MAX_DETAIL_CHARS)
    );
    Err(ApiError::Status {
        status: status.as_u16(),
        body,
    })
}

/// Décode le corps JSON d'une réponse 2xx (Microsoft, Xbox Live, Minecraft
/// Services…). En cas d'échec, l'erreur reqwest (`is_decode()`) devient
/// « … a renvoyé une réponse illisible » via [`transport_message`], qui
/// garde le détail serde (anglais, technique) pour les logs.
pub async fn decode_json<T: for<'de> Deserialize<'de>>(
    resp: reqwest::Response,
) -> Result<T, reqwest::Error> {
    resp.json::<T>().await
}

async fn read_json<T: for<'de> Deserialize<'de>>(resp: reqwest::Response) -> Result<T, ApiError> {
    Ok(ensure_success(resp).await?.json::<T>().await?)
}

impl ApiClient {
    fn url(&self, path: &str) -> String {
        format!("{}{}", self.base_url, path)
    }

    pub async fn login(&self, mc_access_token: &str) -> Result<AuthResponse, ApiError> {
        let resp = self
            .http
            .post(self.url("/auth/login"))
            .json(&LoginRequest {
                mc_access_token: mc_access_token.to_string(),
            })
            .send()
            .await?;
        read_json(resp).await
    }

    /// Dev-only : appelle /v1/auth/dev-login avec un pseudo factice.
    pub async fn dev_login(&self, username: &str) -> Result<AuthResponse, ApiError> {
        let resp = self
            .http
            .post(self.url("/auth/dev-login"))
            .json(&DevLoginRequest {
                username: username.to_string(),
            })
            .send()
            .await?;
        read_json(resp).await
    }

    pub async fn refresh(&self, refresh_token: &str) -> Result<AuthResponse, ApiError> {
        let resp = self
            .http
            .post(self.url("/auth/refresh"))
            .json(&RefreshRequest {
                refresh_token: refresh_token.to_string(),
            })
            .send()
            .await?;
        read_json(resp).await
    }

    pub async fn logout(&self, access_token: &str) -> Result<(), ApiError> {
        let resp = self
            .http
            .post(self.url("/auth/logout"))
            .bearer_auth(access_token)
            .send()
            .await?;
        ensure_success(resp).await?;
        Ok(())
    }

    /// Demande un play-token court (~5 min) signé HMAC par l'API. Sera
    /// passé à la JVM via sysprop, puis poussé au serveur par le mod
    /// Reborn Integrity au JOIN.
    ///
    /// `serveur` : identifiant du serveur visé (`build`, `dev`…), recopié par
    /// l'API dans la claim `aud` ; `None` = serveur par défaut de l'API.
    /// L'API décide de la portée : `play` pour un compte whitelisté ou staff,
    /// `douane` pour un candidat dans la fenêtre d'entrée de sa séance, sinon
    /// 403 avec un message selon la phase (400 si `serveur` est inconnu).
    pub async fn fetch_play_token(
        &self,
        access_token: &str,
        serveur: Option<&str>,
    ) -> Result<PlaySession, ApiError> {
        let mut req = self
            .http
            .post(self.url("/play/session"))
            .bearer_auth(access_token);
        if let Some(serveur) = serveur {
            req = req.json(&PlaySessionRequest { serveur });
        }
        let resp = req.send().await?;
        read_json(resp).await
    }

    /// Récupère le manifest courant. Le payload est strictement le même
    /// que la sortie du CLI manifest-signer (cf §4.3).
    pub async fn fetch_manifest(
        &self,
        access_token: &str,
    ) -> Result<crate::manifest::SignedManifest, ApiError> {
        self.get_json(access_token, "/manifest/current").await
    }

    /// « Mes personnages » (`GET /me/characters`, CANDIDATURE_ET_DOUANE §4.6) :
    /// personnages du compte sur le serveur de prod, avec leur N° (vue du
    /// propriétaire seulement). Cartes brutes, relues une à une par
    /// `content::personnages`.
    pub async fn fetch_my_characters(
        &self,
        access_token: &str,
    ) -> Result<crate::content::personnages::MesPersonnagesApi, ApiError> {
        self.get_json(access_token, "/me/characters").await
    }

    /// GET générique authentifié. Le caller désérialise. Centralise la
    /// gestion du JWT pour ne pas avoir à la dupliquer par endpoint.
    pub async fn get_json<T: for<'de> Deserialize<'de>>(
        &self,
        access_token: &str,
        path: &str,
    ) -> Result<T, ApiError> {
        let resp = self
            .http
            .get(self.url(path))
            .bearer_auth(access_token)
            .send()
            .await?;
        read_json(resp).await
    }

    /// GET générique PUBLIC (sans JWT) — pour les endpoints non
    /// authentifiés comme `/server/status`.
    pub async fn get_json_public<T: for<'de> Deserialize<'de>>(
        &self,
        path: &str,
    ) -> Result<T, ApiError> {
        let resp = self.http.get(self.url(path)).send().await?;
        read_json(resp).await
    }

    /// Upload multipart d'un fichier vers `/upload` (authentifié). Le champ
    /// du form s'appelle `file`. Retourne l'URL publique du fichier stocké.
    pub async fn upload_file(
        &self,
        access_token: &str,
        file_name: &str,
        mime: &str,
        bytes: Vec<u8>,
    ) -> Result<UploadResponse, ApiError> {
        let part = reqwest::multipart::Part::bytes(bytes)
            .file_name(file_name.to_string())
            .mime_str(mime)?;
        let form = reqwest::multipart::Form::new().part("file", part);
        let resp = self
            .http
            .post(self.url("/upload"))
            .bearer_auth(access_token)
            .multipart(form)
            .send()
            .await?;
        read_json(resp).await
    }

    /// Partage un screenshot sur le feed social (`POST /shots`, multipart
    /// `file` + `caption` optionnel). Retourne le `ShotView` créé.
    pub async fn upload_shot(
        &self,
        access_token: &str,
        file_name: &str,
        mime: &str,
        bytes: Vec<u8>,
        caption: Option<String>,
    ) -> Result<ShotView, ApiError> {
        let part = reqwest::multipart::Part::bytes(bytes)
            .file_name(file_name.to_string())
            .mime_str(mime)?;
        let mut form = reqwest::multipart::Form::new().part("file", part);
        if let Some(caption) = caption.filter(|c| !c.trim().is_empty()) {
            form = form.text("caption", caption);
        }
        let resp = self
            .http
            .post(self.url("/shots"))
            .bearer_auth(access_token)
            .multipart(form)
            .send()
            .await?;
        read_json(resp).await
    }

    /// POST générique authentifié avec body JSON.
    pub async fn post_json<B: Serialize, T: for<'de> Deserialize<'de>>(
        &self,
        access_token: &str,
        path: &str,
        body: &B,
    ) -> Result<T, ApiError> {
        let resp = self
            .http
            .post(self.url(path))
            .bearer_auth(access_token)
            .json(body)
            .send()
            .await?;
        read_json(resp).await
    }

    /// DELETE générique authentifié qui ignore le body de réponse (204).
    pub async fn delete_no_content(&self, access_token: &str, path: &str) -> Result<(), ApiError> {
        let resp = self
            .http
            .delete(self.url(path))
            .bearer_auth(access_token)
            .send()
            .await?;
        ensure_success(resp).await?;
        Ok(())
    }

    /// PATCH générique authentifié avec body JSON.
    pub async fn patch_json<B: Serialize, T: for<'de> Deserialize<'de>>(
        &self,
        access_token: &str,
        path: &str,
        body: &B,
    ) -> Result<T, ApiError> {
        let resp = self
            .http
            .patch(self.url(path))
            .bearer_auth(access_token)
            .json(body)
            .send()
            .await?;
        read_json(resp).await
    }

    /// PUT générique authentifié avec body JSON (brouillon de candidature…).
    pub async fn put_json<B: Serialize, T: for<'de> Deserialize<'de>>(
        &self,
        access_token: &str,
        path: &str,
        body: &B,
    ) -> Result<T, ApiError> {
        let resp = self
            .http
            .put(self.url(path))
            .bearer_auth(access_token)
            .json(body)
            .send()
            .await?;
        read_json(resp).await
    }

    /// DELETE générique authentifié dont la réponse porte un corps JSON
    /// (`{ ok: true }`…), à la différence de [`Self::delete_no_content`].
    pub async fn delete_json<T: for<'de> Deserialize<'de>>(
        &self,
        access_token: &str,
        path: &str,
    ) -> Result<T, ApiError> {
        let resp = self
            .http
            .delete(self.url(path))
            .bearer_auth(access_token)
            .send()
            .await?;
        read_json(resp).await
    }

    /// POST authentifié d'octets bruts (corps de la requête tel quel, avec
    /// son `Content-Type`), réponse JSON. `timeout` remplace le délai global
    /// du client (15 s) : un envoi de plus d'un Mo sur une connexion
    /// résidentielle lente peut le dépasser.
    pub async fn post_raw<T: for<'de> Deserialize<'de>>(
        &self,
        access_token: &str,
        path: &str,
        content_type: &str,
        bytes: Vec<u8>,
        timeout: std::time::Duration,
    ) -> Result<T, ApiError> {
        let resp = self
            .http
            .post(self.url(path))
            .bearer_auth(access_token)
            .header(reqwest::header::CONTENT_TYPE, content_type)
            .timeout(timeout)
            .body(bytes)
            .send()
            .await?;
        read_json(resp).await
    }

    /// GET authentifié d'un fichier privé (enregistrement vocal…) : octets
    /// bruts. Le type est connu de l'appelant par ailleurs (`mimeType`).
    pub async fn get_bytes(
        &self,
        access_token: &str,
        path: &str,
        timeout: std::time::Duration,
    ) -> Result<Vec<u8>, ApiError> {
        let resp = self
            .http
            .get(self.url(path))
            .bearer_auth(access_token)
            .timeout(timeout)
            .send()
            .await?;
        Ok(ensure_success(resp).await?.bytes().await?.to_vec())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn status(status: u16, body: &str) -> ApiError {
        ApiError::Status {
            status,
            body: body.to_string(),
        }
    }

    #[test]
    fn extrait_le_message_nest_en_chaine() {
        let body = r#"{"statusCode":400,"message":"Le délai de 4 h n'est pas encore écoulé.","error":"Bad Request"}"#;
        assert_eq!(
            extract_api_message(body).as_deref(),
            Some("Le délai de 4 h n'est pas encore écoulé")
        );
        assert_eq!(
            status(400, body).to_string(),
            "Le délai de 4 h n'est pas encore écoulé (HTTP 400)."
        );
    }

    #[test]
    fn extrait_le_message_nest_en_tableau() {
        let body = r#"{"statusCode":400,"message":["displayName must be shorter than or equal to 32 characters","displayName should not be empty"],"error":"Bad Request"}"#;
        assert_eq!(
            extract_api_message(body).as_deref(),
            Some("displayName must be shorter than or equal to 32 characters ; displayName should not be empty")
        );
    }

    #[test]
    fn tous_les_messages_de_validation_sans_troncature() {
        let long = "é".repeat(400);
        let body = format!(
            r#"{{"statusCode":400,"message":["history: {long}"," firstName: Lettres. "],"error":"Bad Request"}}"#
        );
        let messages = api_messages(&body);
        assert_eq!(messages.len(), 2);
        assert_eq!(messages[0], format!("history: {long}"));
        assert_eq!(messages[1], "firstName: Lettres.");
        assert_eq!(
            api_messages(r#"{"message":"Ta candidature est déjà acceptée."}"#),
            vec!["Ta candidature est déjà acceptée.".to_string()]
        );
        assert!(api_messages("<html>502</html>").is_empty());
        assert!(api_messages(r#"{"message":42}"#).is_empty());
    }

    #[test]
    fn extrait_errormessage_mojang_et_texte_brut() {
        let mojang = r#"{"path":"/authentication/login_with_xbox","errorMessage":"Invalid app registration"}"#;
        assert_eq!(
            extract_api_message(mojang).as_deref(),
            Some("Invalid app registration")
        );
        assert_eq!(
            extract_api_message("upstream down\nstack…").as_deref(),
            Some("upstream down")
        );
        assert_eq!(extract_api_message("   "), None);
        assert_eq!(extract_api_message("<html><body>502</body></html>"), None);
        assert_eq!(extract_api_message(r#"{"message":42}"#), None);
    }

    #[test]
    fn tronque_les_messages_trop_longs_sans_couper_un_accent() {
        let long = format!(r#"{{"message":"{}"}}"#, "é".repeat(400));
        let msg = extract_api_message(&long).unwrap();
        assert_eq!(msg.chars().count(), MAX_DETAIL_CHARS + 1);
        assert!(msg.ends_with('…'));
    }

    #[test]
    fn messages_par_code_http() {
        assert_eq!(
            status(401, r#"{"statusCode":401,"message":"Unauthorized"}"#).to_string(),
            "Session expirée : reconnecte-toi (HTTP 401)."
        );
        assert_eq!(
            status(
                403,
                r#"{"statusCode":403,"message":"Forbidden resource","error":"Forbidden"}"#
            )
            .to_string(),
            "Accès refusé : tu n'as pas les droits nécessaires pour cette action (HTTP 403)."
        );
        assert_eq!(
            status(403, r#"{"message":"Candidature non acceptée"}"#).to_string(),
            "Accès refusé : Candidature non acceptée (HTTP 403)."
        );
        assert!(status(404, r#"{"message":"Cannot GET /v1/me/badges"}"#)
            .to_string()
            .contains("Cannot GET /v1/me/badges"));
        assert_eq!(
            status(
                429,
                r#"{"message":"ThrottlerException: Too Many Requests"}"#
            )
            .to_string(),
            "Trop de requêtes, réessaie dans un instant (HTTP 429)."
        );
        assert_eq!(
            status(502, "<html>Bad Gateway</html>").to_string(),
            "Le serveur Reborn a un problème (HTTP 502) — réessaie plus tard."
        );
        assert_eq!(
            status(
                500,
                r#"{"statusCode":500,"message":"Internal server error"}"#
            )
            .to_string(),
            "Le serveur Reborn a un problème (HTTP 500) — réessaie plus tard."
        );
        assert_eq!(
            status(418, "").to_string(),
            "Le serveur Reborn a refusé la requête (HTTP 418)."
        );
    }

    #[test]
    fn serialise_kind_et_message() {
        let json = serde_json::to_value(status(401, "")).unwrap();
        assert_eq!(json["kind"], "session_expired");
        assert_eq!(
            json["message"],
            "Session expirée : reconnecte-toi (HTTP 401)."
        );
        let json = serde_json::to_value(status(503, "")).unwrap();
        assert_eq!(json["kind"], "server_error");
    }

    #[test]
    fn erreur_de_construction_de_requete() {
        let err = reqwest::Client::new()
            .get("pas une url")
            .build()
            .expect_err("URL invalide");
        let api = ApiError::from(err);
        assert_eq!(api.kind(), "internal");
        assert!(api
            .to_string()
            .starts_with("Requête invalide vers le serveur Reborn"));
    }
}
