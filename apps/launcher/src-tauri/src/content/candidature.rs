//! Candidature v2 et séances de douane (CANDIDATURE_ET_DOUANE.md §2 et §3.3) :
//! commandes fines vers `/v1/whitelist/*` (candidature, brouillon serveur,
//! enregistrement vocal) et `/v1/douane/*` (séances, réservation), plus le
//! contrôle d'accès au jeu que lisent « Jouer » / « Passer la douane » et
//! `launcher_launch_game`.
//!
//! **Passe-plat.** Les réponses de l'API traversent telles quelles (JSON) ;
//! le contrat des formes est dans `src/lib/candidature.ts`. Un champ ajouté
//! par l'API n'est donc jamais perdu en route (fiche §2.1 : « ajouter un champ
//! casse l'envoi »). Ici, on ne contrôle que la forme des corps envoyés
//! (objet JSON, identifiants, octets de la voix) : l'API reste l'autorité sur
//! les règles (bornes, listes fermées, phases).
//!
//! **Format unique.** Tout envoi porte `formVersion: 2`, posé ici quoi qu'envoie
//! le front : l'API refuse tout autre format (complément du 25/10).
//!
//! Les messages du fil (`whitelist_messages_*`), le retrait (`whitelist_withdraw`)
//! et la demande de reprise (`whitelist_reclaim`) restent dans `content/mod.rs`.

use std::time::Duration;

use serde::{Deserialize, Serialize};
use serde_json::{json, Value as Json};
use tauri::ipc::{InvokeBody, Request, Response};
use tauri::State;

use super::{jwt, ContentError};
use crate::api::{api_messages, ApiError, ApiUser};
use crate::auth::AuthState;
use crate::launcher::clock;

/// Version du formulaire posée sur tout envoi et tout brouillon.
const FORM_VERSION: u64 = 2;

/// En-têtes IPC de l'enregistrement vocal. Le `Content-Type` de la requête
/// IPC est réservé à Tauri (`application/octet-stream` pour un corps brut) :
/// le type audio et la durée voyagent donc dans ces en-têtes à part.
pub const EN_TETE_VOIX_TYPE: &str = "x-reborn-voix-type";
pub const EN_TETE_VOIX_DUREE: &str = "x-reborn-voix-duree";

/// Types acceptés par l'API (MediaRecorder en Opus).
const VOIX_TYPES: [&str; 2] = ["audio/webm", "audio/ogg"];

/// Garde-fou local, très au-dessus du plafond de l'API (1,5 Mo par défaut,
/// réglable côté API) : on n'envoie pas des dizaines de Mo pour un refus.
const VOIX_OCTETS_GARDE_FOU: usize = 8 * 1024 * 1024;

/// Délai d'envoi ou de lecture de la voix (au lieu des 15 s du client).
const VOIX_DELAI: Duration = Duration::from_secs(60);

/// Avance de l'entrée sur l'heure de convocation si l'API ne la donne pas.
const AVANCE_ENTREE_DEFAUT_MIN: u32 = 15;

// ──────────────────────────────────────────────────────
//  Contrôles de forme (purs, testés plus bas)
// ──────────────────────────────────────────────────────

fn entree_invalide(message: impl Into<String>) -> ContentError {
    ContentError::InvalidInput {
        message: message.into(),
    }
}

/// Corps de `POST /whitelist` et `PATCH /whitelist/me` : l'objet du front,
/// avec `formVersion: 2` imposé. Les champs ne sont pas filtrés : un champ
/// inconnu ou retiré est refusé par l'API avec un message clair, au lieu
/// d'être perdu sans rien dire.
fn corps_envoi(candidature: Json) -> Result<Json, ContentError> {
    let Json::Object(mut champs) = candidature else {
        return Err(entree_invalide(
            "Candidature illisible : un objet JSON est attendu.",
        ));
    };
    champs.insert("formVersion".into(), Json::from(FORM_VERSION));
    Ok(Json::Object(champs))
}

/// Corps de `PUT /whitelist/me/brouillon` : `{ data, formVersion: 2 }`.
fn corps_brouillon(data: Json) -> Result<Json, ContentError> {
    if !data.is_object() {
        return Err(entree_invalide(
            "Brouillon illisible : un objet JSON est attendu.",
        ));
    }
    Ok(json!({ "data": data, "formVersion": FORM_VERSION }))
}

/// Identifiant d'une séance ou d'une réservation, placé dans un chemin
/// d'URL : UUID canonique seulement (rien qui puisse changer la route).
fn identifiant<'a>(id: &'a str, quoi: &str) -> Result<&'a str, ContentError> {
    let ok = id.len() == 36
        && id.char_indices().all(|(i, c)| match i {
            8 | 13 | 18 | 23 => c == '-',
            _ => c.is_ascii_hexdigit(),
        });
    if ok {
        Ok(id)
    } else {
        Err(entree_invalide(format!("{quoi} invalide.")))
    }
}

/// Enregistrement vocal reçu du front, prêt à partir vers l'API.
#[derive(Debug, PartialEq, Eq)]
struct VoixRecue {
    octets: Vec<u8>,
    type_mime: &'static str,
    duree_sec: u32,
}

/// Octets du corps IPC. Normalement `InvokeBody::Raw` (front : `invoke` avec
/// un `Uint8Array`) ; tableau JSON de nombres accepté en secours, si l'IPC
/// est retombée sur `postMessage`.
fn octets_du_corps(corps: &InvokeBody) -> Result<Vec<u8>, ContentError> {
    match corps {
        InvokeBody::Raw(octets) => Ok(octets.clone()),
        InvokeBody::Json(Json::Array(nombres)) => nombres
            .iter()
            .map(|n| n.as_u64().and_then(|v| u8::try_from(v).ok()))
            .collect::<Option<Vec<u8>>>()
            .ok_or_else(|| entree_invalide("Enregistrement illisible : refais-le.")),
        InvokeBody::Json(_) => Err(entree_invalide(
            "Enregistrement absent : les octets de l'enregistrement sont attendus en corps brut.",
        )),
    }
}

/// Contrôle de forme de l'enregistrement : type audio accepté (paramètres
/// comme `;codecs=opus` retirés), durée entière, taille non nulle. Les
/// bornes (15 à 60 s, plafond d'octets, signature du fichier) sont celles de
/// l'API, qui répond avec son propre message.
fn lire_voix(
    octets: Vec<u8>,
    type_brut: Option<&str>,
    duree_brute: Option<&str>,
) -> Result<VoixRecue, ContentError> {
    let base = type_brut
        .and_then(|t| t.split(';').next())
        .map(|t| t.trim().to_ascii_lowercase())
        .unwrap_or_default();
    let Some(type_mime) = VOIX_TYPES.iter().copied().find(|t| *t == base) else {
        return Err(entree_invalide(format!(
            "Format d'enregistrement non accepté{}. Formats acceptés : {}.",
            if base.is_empty() {
                String::new()
            } else {
                format!(" (« {base} »)")
            },
            VOIX_TYPES.join(", ")
        )));
    };
    let duree_sec = duree_brute
        .map(str::trim)
        .and_then(|d| d.parse::<u32>().ok())
        .filter(|d| *d > 0)
        .ok_or_else(|| {
            entree_invalide("Durée de l'enregistrement illisible : refais l'enregistrement.")
        })?;
    if octets.is_empty() {
        return Err(entree_invalide("L'enregistrement est vide : refais-le."));
    }
    if octets.len() > VOIX_OCTETS_GARDE_FOU {
        return Err(entree_invalide(format!(
            "Enregistrement trop lourd ({} Ko) : refais-le, une minute au plus.",
            octets.len() / 1024
        )));
    }
    Ok(VoixRecue {
        octets,
        type_mime,
        duree_sec,
    })
}

/// Refus de validation de l'API (400) : TOUS ses messages, sans la
/// troncature des erreurs génériques, en `invalid_input`. Format
/// « history: … ; firstName: … (HTTP 400). », que le front range par champ
/// (`erreursParChamp`). Toute autre erreur passe telle quelle.
fn erreur_de_saisie(e: ApiError) -> ContentError {
    if let ApiError::Status { status: 400, body } = &e {
        let messages = api_messages(body);
        if !messages.is_empty() {
            let texte = messages
                .iter()
                .map(|m| m.trim_end_matches('.'))
                .collect::<Vec<_>>()
                .join(" ; ");
            return entree_invalide(format!("{texte} (HTTP 400)."));
        }
    }
    ContentError::from(e)
}

fn en_tete<'a>(request: &'a Request<'_>, nom: &str) -> Option<&'a str> {
    request.headers().get(nom).and_then(|v| v.to_str().ok())
}

// ──────────────────────────────────────────────────────
//  Candidature : options, suivi, envoi
// ──────────────────────────────────────────────────────

/// `GET /whitelist/options` : villages, clans (pour information), bornes,
/// libellés des listes fermées, contraintes de la voix.
#[tauri::command]
pub async fn candidature_options(state: State<'_, AuthState>) -> Result<Json, ContentError> {
    let token = jwt(state.inner()).await?;
    Ok(state.api.get_json(&token, "/whitelist/options").await?)
}

/// `GET /whitelist/me` : `{ application, suivi }`, la phase calculée par l'API
/// (`suivi.phase`) à lire au lieu du rôle.
#[tauri::command]
pub async fn candidature_me(state: State<'_, AuthState>) -> Result<Json, ContentError> {
    let token = jwt(state.inner()).await?;
    Ok(state.api.get_json(&token, "/whitelist/me").await?)
}

/// `POST /whitelist` : première candidature, ou nouvelle candidature complète
/// après un refus (délai d'un jour passé). `formVersion: 2` posé ici.
#[tauri::command]
pub async fn candidature_envoyer(
    state: State<'_, AuthState>,
    candidature: Json,
) -> Result<Json, ContentError> {
    let corps = corps_envoi(candidature)?;
    let token = jwt(state.inner()).await?;
    state
        .api
        .post_json(&token, "/whitelist", &corps)
        .await
        .map_err(erreur_de_saisie)
}

/// `PATCH /whitelist/me` : renvoi après « À revoir » (seules les parties
/// rouvertes sont lues par l'API). `formVersion: 2` posé ici.
#[tauri::command]
pub async fn candidature_renvoyer(
    state: State<'_, AuthState>,
    candidature: Json,
) -> Result<Json, ContentError> {
    let corps = corps_envoi(candidature)?;
    let token = jwt(state.inner()).await?;
    state
        .api
        .patch_json(&token, "/whitelist/me", &corps)
        .await
        .map_err(erreur_de_saisie)
}

// ──────────────────────────────────────────────────────
//  Brouillon serveur (§2.5)
// ──────────────────────────────────────────────────────

/// `GET /whitelist/me/brouillon` : `{ brouillon: … | null }`.
#[tauri::command]
pub async fn candidature_brouillon_lire(state: State<'_, AuthState>) -> Result<Json, ContentError> {
    let token = jwt(state.inner()).await?;
    Ok(state
        .api
        .get_json(&token, "/whitelist/me/brouillon")
        .await?)
}

/// `PUT /whitelist/me/brouillon` : le formulaire tel quel (contrôle souple
/// côté API, aucun minimum, 256 Ko au plus). Renvoie `{ brouillon }`.
#[tauri::command]
pub async fn candidature_brouillon_ecrire(
    state: State<'_, AuthState>,
    data: Json,
) -> Result<Json, ContentError> {
    let corps = corps_brouillon(data)?;
    let token = jwt(state.inner()).await?;
    state
        .api
        .put_json(&token, "/whitelist/me/brouillon", &corps)
        .await
        .map_err(erreur_de_saisie)
}

/// `DELETE /whitelist/me/brouillon` : efface le brouillon et sa voix.
#[tauri::command]
pub async fn candidature_brouillon_effacer(
    state: State<'_, AuthState>,
) -> Result<(), ContentError> {
    let token = jwt(state.inner()).await?;
    Ok(state
        .api
        .delete_no_content(&token, "/whitelist/me/brouillon")
        .await?)
}

// ──────────────────────────────────────────────────────
//  Enregistrement vocal (§2.3, onglet Voix)
// ──────────────────────────────────────────────────────

/// `POST /whitelist/me/voix` : dépose l'enregistrement dans le brouillon (il
/// rejoint la candidature à l'envoi ; un nouveau dépôt remplace l'ancien).
///
/// Le front l'appelle en **corps brut** (`invoke` avec un `Uint8Array`, lu
/// ici par `tauri::ipc::Request`, `InvokeBody::Raw`), jamais en tableau JSON
/// de nombres (trois à quatre fois plus lourd). En-têtes IPC :
/// [`EN_TETE_VOIX_TYPE`] (`audio/webm` ou `audio/ogg`) et
/// [`EN_TETE_VOIX_DUREE`] (secondes entières). Renvoie `{ voix }`.
#[tauri::command]
pub async fn candidature_voix_deposer(
    state: State<'_, AuthState>,
    request: Request<'_>,
) -> Result<Json, ContentError> {
    let voix = lire_voix(
        octets_du_corps(request.body())?,
        en_tete(&request, EN_TETE_VOIX_TYPE),
        en_tete(&request, EN_TETE_VOIX_DUREE),
    )?;
    let token = jwt(state.inner()).await?;
    let chemin = format!("/whitelist/me/voix?dureeSec={}", voix.duree_sec);
    Ok(state
        .api
        .post_raw(&token, &chemin, voix.type_mime, voix.octets, VOIX_DELAI)
        .await?)
}

/// Sources de la réécoute : `brouillon` (onglets Voix et Envoi) ou
/// `candidature` (« Mon dossier » : la voix ENVOYÉE, même quand un nouvel
/// enregistrement attend dans le brouillon).
const VOIX_SOURCES: [&str; 2] = ["brouillon", "candidature"];

/// Chemin de `GET /whitelist/me/voix`, avec la source demandée. Sans source :
/// la règle de l'API (brouillon, sinon candidature). Valeur hors liste
/// refusée : rien d'autre ne part dans l'adresse.
fn chemin_voix(source: Option<&str>) -> Result<String, ContentError> {
    match source.map(str::trim).filter(|s| !s.is_empty()) {
        None => Ok("/whitelist/me/voix".to_string()),
        Some(s) if VOIX_SOURCES.contains(&s) => Ok(format!("/whitelist/me/voix?source={s}")),
        Some(_) => Err(entree_invalide("Source de l'enregistrement inconnue.")),
    }
}

/// `GET /whitelist/me/voix` : réécoute de son propre enregistrement (celui du
/// brouillon, sinon celui de la candidature ; `source: "candidature"` pour la
/// voix envoyée). Octets bruts : le front les reçoit en `ArrayBuffer` et prend
/// le type dans `voix.mimeType` du brouillon ou de la candidature.
///
/// L'API honore `?source=` (`brouillon` ou `candidature`, 400 sinon) ; une API
/// plus ancienne l'ignore et sert la voix du brouillon d'abord.
#[tauri::command]
pub async fn candidature_voix_ecouter(
    state: State<'_, AuthState>,
    source: Option<String>,
) -> Result<Response, ContentError> {
    let chemin = chemin_voix(source.as_deref())?;
    let token = jwt(state.inner()).await?;
    let octets = state.api.get_bytes(&token, &chemin, VOIX_DELAI).await?;
    Ok(Response::new(octets))
}

// ──────────────────────────────────────────────────────
//  Séances de douane (§3.3)
// ──────────────────────────────────────────────────────

/// `GET /douane/me` : phase, place réservée avec sa fenêtre d'entrée,
/// séances ouvertes avec leurs places, règles (avance, retard, douane en jeu).
#[tauri::command]
pub async fn douane_etat(state: State<'_, AuthState>) -> Result<Json, ContentError> {
    let token = jwt(state.inner()).await?;
    Ok(state.api.get_json(&token, "/douane/me").await?)
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ReserverSeance<'a> {
    seance_id: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    remplacer: Option<bool>,
}

/// `POST /douane/reservations` : réserve une place. `remplacer: true` change
/// de séance sans perdre l'ancienne place si la nouvelle échoue.
#[tauri::command]
pub async fn douane_reserver(
    state: State<'_, AuthState>,
    seance_id: String,
    remplacer: Option<bool>,
) -> Result<Json, ContentError> {
    let seance_id = identifiant(&seance_id, "Séance")?;
    let token = jwt(state.inner()).await?;
    Ok(state
        .api
        .post_json(
            &token,
            "/douane/reservations",
            &ReserverSeance {
                seance_id,
                remplacer,
            },
        )
        .await?)
}

/// `DELETE /douane/reservations/:id` : annule sa propre place. `{ ok: true }`.
#[tauri::command]
pub async fn douane_annuler(
    state: State<'_, AuthState>,
    reservation_id: String,
) -> Result<Json, ContentError> {
    let id = identifiant(&reservation_id, "Réservation")?;
    let token = jwt(state.inner()).await?;
    Ok(state
        .api
        .delete_json(&token, &format!("/douane/reservations/{id}"))
        .await?)
}

// ──────────────────────────────────────────────────────
//  Accès au jeu : la barrière lit la PHASE, plus seulement le rôle
// ──────────────────────────────────────────────────────

/// Codes stables des refus de lancement (`kind` de l'erreur du lancement,
/// `refus.kind` de [`AccesJeu`]).
pub const REFUS_CANDIDATURE: &str = "candidature_required";
pub const REFUS_HORS_JEU: &str = "douane_hors_jeu";
pub const REFUS_PAS_OUVERTE: &str = "douane_not_open";
pub const REFUS_FERMEE: &str = "douane_closed";
/// API d'avant la candidature v2 (pas de `/douane/me`) : ancienne règle du rôle.
pub const REFUS_ANCIENNE_API: &str = "not_whitelisted";

const MSG_ANCIENNE_API: &str = "Ta candidature whitelist n'a pas encore été acceptée : tu pourras rejoindre le serveur dès qu'elle le sera.";

/// Portée de jeton attendue au lancement (l'API la décide, le jeton fait foi).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Portee {
    /// Jeu normal : compte whitelisté ou staff.
    Play,
    /// Hall de la douane seulement : candidat dans la fenêtre de sa séance.
    Douane,
}

/// Refus de lancement : `kind` stable pour le front, `message` complet
/// (cause et quoi faire).
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct RefusAcces {
    pub kind: &'static str,
    pub message: String,
}

/// Réponse de `douane_acces`, et décision lue par `launcher_launch_game`.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AccesJeu {
    /// Vrai : le jeu peut être lancé maintenant.
    pub autorise: bool,
    /// Portée attendue si autorisé, sinon `None`.
    pub portee: Option<Portee>,
    /// Phase de la candidature (`ACCEPTE` pour un compte whitelisté ou staff).
    pub phase: Option<String>,
    /// Pourquoi le lancement est refusé, sinon `None`.
    pub refus: Option<RefusAcces>,
    /// Place de douane (phase `DOUANE_RESERVEE`) : heure de convocation et
    /// fenêtre d'entrée, ISO 8601 UTC.
    pub convocation_at: Option<String>,
    pub ouvre_at: Option<String>,
    pub ferme_at: Option<String>,
}

impl AccesJeu {
    fn autorise(portee: Portee, phase: Option<String>) -> Self {
        Self {
            autorise: true,
            portee: Some(portee),
            phase,
            refus: None,
            convocation_at: None,
            ouvre_at: None,
            ferme_at: None,
        }
    }

    fn refuse(phase: Option<String>, kind: &'static str, message: impl Into<String>) -> Self {
        Self {
            autorise: false,
            portee: None,
            phase,
            refus: Some(RefusAcces {
                kind,
                message: message.into(),
            }),
            convocation_at: None,
            ouvre_at: None,
            ferme_at: None,
        }
    }
}

/// Ce que le contrôle d'accès lit de `GET /douane/me` (les autres champs
/// sont ignorés).
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct EtatDouaneLu {
    pub phase: String,
    #[serde(default)]
    pub reservation: Option<ReservationLue>,
    /// Sans place en cours : la dernière place perdue depuis la dernière
    /// décision (maquette L10, « séance manquée »).
    #[serde(default)]
    pub derniere_reservation: Option<DernierePlaceLue>,
    #[serde(default)]
    pub regles: Option<ReglesLues>,
}

/// Dernière place perdue (`derniereReservation` de `GET /douane/me`).
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct DernierePlaceLue {
    /// `MISSED` (entrée fermée sans arrivée) ou `CANCELLED`.
    pub statut: String,
    pub convocation_at: String,
    /// Fin de la fenêtre d'entrée (« entrée fermée à 22 h »).
    pub ferme_at: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ReservationLue {
    pub convocation_at: String,
    pub fenetre: FenetreLue,
}

/// Fenêtre d'entrée calculée par l'API, avec SON horloge : une horloge de PC
/// décalée ne bloque ni n'ouvre l'entrée à tort.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct FenetreLue {
    pub ouvre_at: String,
    pub ferme_at: String,
    /// `A_VENIR`, `OUVERTE` ou `FERMEE`.
    pub etat: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ReglesLues {
    #[serde(default = "avance_defaut")]
    pub avance_entree_min: u32,
    #[serde(default)]
    pub douane_en_jeu: bool,
}

fn avance_defaut() -> u32 {
    AVANCE_ENTREE_DEFAUT_MIN
}

/// Message d'un compte PLAYER dont la phase ne permet pas d'entrer. L'entrée
/// du rail s'appelle « Whitelist ».
fn message_phase(phase: &str) -> &'static str {
    match phase {
        "BROUILLON" => "Ta candidature n'est pas encore envoyée : termine-la dans l'onglet Whitelist pour rejoindre le serveur.",
        "ECRIT_EN_ATTENTE" => "Ta candidature est en cours de relecture : suis son avancement dans l'onglet Whitelist. Tu passeras la douane une fois ton écrit accepté.",
        "ECRIT_A_REVOIR" => "Ta candidature est à revoir : corrige les parties demandées dans l'onglet Whitelist, puis renvoie-la.",
        "DOUANE_A_RESERVER" => "Ton écrit est accepté : réserve ta séance de douane dans l'onglet Whitelist.",
        "DOUANE_A_REVOIR" => "Ta douane est à revoir : réserve une nouvelle séance dans l'onglet Whitelist.",
        "REFUSE" => "Ta candidature a été refusée : le commentaire du staff et la date d'une nouvelle candidature sont dans l'onglet Whitelist.",
        _ => "Ta candidature doit être acceptée avant de rejoindre le serveur : suis son avancement dans l'onglet Whitelist.",
    }
}

/// Une séance manquée reste annoncée (« SÉANCE MANQUÉE », maquette L10)
/// pendant ce délai après la fermeture de l'entrée ; ensuite, l'accueil
/// revient à « réserve ta douane ».
const MANQUEE_VISIBLE_MS: i64 = 12 * 60 * 60 * 1000;

const MSG_MANQUEE: &str = "Tu n'es pas entré à la douane pendant la fenêtre de ta séance : réserve une autre séance dans l'onglet Whitelist.";

/// Compte encore PLAYER alors que la phase vaut ACCEPTE (écrit et oral
/// validés sans promotion) : l'API refuserait le jeton, relancer n'y change
/// rien.
const MSG_SANS_PROMOTION: &str =
    "Ton accès est validé mais ton compte n'est pas à jour : ouvre un ticket.";

/// Place manquée récente (`derniereReservation` MISSED dont l'entrée a fermé
/// il y a moins de [`MANQUEE_VISIBLE_MS`]), douane en jeu : refus
/// « séance manquée » avec l'heure de convocation et de fermeture.
fn seance_manquee(etat: &EtatDouaneLu, phase: Option<String>, now_ms: i64) -> Option<AccesJeu> {
    let en_jeu = etat.regles.as_ref().is_some_and(|r| r.douane_en_jeu);
    let place = etat.derniere_reservation.as_ref()?;
    if !en_jeu || place.statut != "MISSED" {
        return None;
    }
    let ferme = clock::parse_rfc3339_ms(&place.ferme_at)?;
    let depuis = now_ms - ferme;
    if !(0..MANQUEE_VISIBLE_MS).contains(&depuis) {
        return None;
    }
    let mut acces = AccesJeu::refuse(phase, REFUS_FERMEE, MSG_MANQUEE);
    acces.convocation_at = Some(place.convocation_at.clone());
    acces.ferme_at = Some(place.ferme_at.clone());
    Some(acces)
}

/// « 12 min », « 2 h 05 », « 3 jours » : délai avant l'ouverture, arrondi à
/// la minute supérieure. `None` si le délai est écoulé (horloges décalées).
fn delai_lisible(ms: i64) -> Option<String> {
    if ms <= 0 {
        return None;
    }
    let minutes = (ms + 59_999) / 60_000;
    Some(if minutes < 60 {
        format!("{minutes} min")
    } else if minutes < 24 * 60 {
        let (h, m) = (minutes / 60, minutes % 60);
        if m == 0 {
            format!("{h} h")
        } else {
            format!("{h} h {m:02}")
        }
    } else {
        let jours = minutes / (24 * 60);
        format!("{jours} jour{}", if jours > 1 { "s" } else { "" })
    })
}

/// Décision d'accès d'un compte PLAYER, d'après la phase et la fenêtre
/// calculées par l'API (`GET /douane/me`). Pure : `now_ms` ne sert qu'au
/// délai affiché avant l'ouverture et à l'âge d'une séance manquée.
///
/// Séance manquée : l'API compte une place fermée sans arrivée comme perdue,
/// la phase repasse donc à DOUANE_A_RESERVER (ou DOUANE_A_REVOIR) avec
/// `derniereReservation` MISSED. Une place encore « réservée » dont l'entrée
/// est fermée est celle d'un candidat ARRIVÉ : refus neutre, pas « manquée ».
pub(crate) fn decider_acces(etat: &EtatDouaneLu, now_ms: i64) -> AccesJeu {
    let phase = Some(etat.phase.clone());
    match etat.phase.as_str() {
        "ACCEPTE" => AccesJeu::autorise(Portee::Play, phase),
        "DOUANE_A_RESERVER" | "DOUANE_A_REVOIR" => seance_manquee(etat, phase.clone(), now_ms)
            .unwrap_or_else(|| {
                AccesJeu::refuse(phase, REFUS_CANDIDATURE, message_phase(&etat.phase))
            }),
        "DOUANE_RESERVEE" => {
            let Some(place) = etat.reservation.as_ref() else {
                return AccesJeu::refuse(
                    phase,
                    REFUS_CANDIDATURE,
                    "Ta place de douane est introuvable : actualise l'onglet Whitelist, puis réserve une séance si besoin.",
                );
            };
            let (en_jeu, avance) = etat
                .regles
                .as_ref()
                .map_or((false, AVANCE_ENTREE_DEFAUT_MIN), |r| {
                    (r.douane_en_jeu, r.avance_entree_min)
                });
            let mut acces = if !en_jeu {
                AccesJeu::refuse(
                    phase,
                    REFUS_HORS_JEU,
                    "Ton oral se passe hors du jeu pour le moment : suis les consignes du staff dans l'onglet Whitelist.",
                )
            } else {
                match place.fenetre.etat.as_str() {
                    "OUVERTE" => AccesJeu::autorise(Portee::Douane, phase),
                    // Place encore réservée, entrée fermée : l'API ne garde
                    // ainsi que la place d'un candidat arrivé au hall.
                    "FERMEE" => AccesJeu::refuse(
                        phase,
                        REFUS_CANDIDATURE,
                        "L'entrée de ta séance de douane est fermée et ton arrivée est enregistrée : la décision de l'examinateur arrivera dans l'onglet Whitelist. Déconnecté pendant l'examen ? Préviens le staff dans le fil.",
                    ),
                    _ => {
                        let delai = clock::parse_rfc3339_ms(&place.fenetre.ouvre_at)
                            .and_then(|ouvre| delai_lisible(ouvre - now_ms));
                        let quand = match delai {
                            Some(d) => format!("ouvre dans {d}"),
                            None => "ouvre dans un instant".to_string(),
                        };
                        AccesJeu::refuse(
                            phase,
                            REFUS_PAS_OUVERTE,
                            format!(
                                "L'entrée de ta séance de douane {quand} ({avance} min avant ta convocation) : relance le jeu à ce moment-là."
                            ),
                        )
                    }
                }
            };
            acces.convocation_at = Some(place.convocation_at.clone());
            acces.ouvre_at = Some(place.fenetre.ouvre_at.clone());
            acces.ferme_at = Some(place.fenetre.ferme_at.clone());
            acces
        }
        autre => AccesJeu::refuse(phase, REFUS_CANDIDATURE, message_phase(autre)),
    }
}

/// Compte dont le rôle relu (`/auth/me`) est encore PLAYER : un accès `play`
/// (phase ACCEPTE) serait refusé par l'API au moment du jeton, après toute
/// l'installation. On refuse donc tout de suite, avec la marche à suivre.
fn sans_promotion(acces: AccesJeu) -> AccesJeu {
    if acces.autorise && acces.portee == Some(Portee::Play) {
        AccesJeu::refuse(acces.phase, REFUS_CANDIDATURE, MSG_SANS_PROMOTION)
    } else {
        acces
    }
}

/// Contrôle d'accès au jeu d'un compte (lancement, bouton « Jouer »).
///
/// - rôle WHITELISTED ou plus : jeu normal (`play`), sans appel ;
/// - PLAYER : le rôle en cache peut dater d'avant l'oral (promotion pendant
///   que le launcher était ouvert). On le relit d'abord (`/auth/me`), puis la
///   phase et la fenêtre (`/douane/me`). Rôle relu encore PLAYER avec une
///   phase ACCEPTE : refus avant l'installation (l'API refuserait le jeton) ;
///   relecture impossible : l'API tranche au moment du jeton ;
/// - API sans `/douane/me` (404, ancienne API) : ancienne règle du rôle.
///
/// `Err` : phase illisible (réseau, session expirée…), à montrer telle quelle.
pub(crate) async fn controler_acces(
    auth: &AuthState,
    token: &str,
    role: &str,
) -> Result<AccesJeu, ApiError> {
    let accepte = || AccesJeu::autorise(Portee::Play, Some("ACCEPTE".to_string()));
    if role != "PLAYER" {
        return Ok(accepte());
    }
    let mut toujours_player = false;
    match auth.api.get_json::<ApiUser>(token, "/auth/me").await {
        Ok(user) => {
            let promu = user.role != "PLAYER";
            auth.set_user(Some(user)).await;
            if promu {
                return Ok(accepte());
            }
            toujours_player = true;
        }
        Err(e) => tracing::warn!("relecture du compte avant lancement impossible : {e}"),
    }
    match auth.api.get_json::<EtatDouaneLu>(token, "/douane/me").await {
        Ok(etat) => {
            let acces = decider_acces(&etat, clock::now_ms());
            Ok(if toujours_player {
                sans_promotion(acces)
            } else {
                acces
            })
        }
        Err(e) if e.status() == Some(404) => {
            Ok(AccesJeu::refuse(None, REFUS_ANCIENNE_API, MSG_ANCIENNE_API))
        }
        Err(e) => Err(e),
    }
}

/// Peut-on lancer le jeu maintenant ? Même décision que `launcher_launch_game`,
/// pour le bouton « Jouer » / « Passer la douane » (texte, grisé, heure).
/// Appelle l'API (`/douane/me`) pour un compte PLAYER : à lire à l'ouverture
/// de l'accueil, au retour de la fenêtre et toutes les minutes au plus.
#[tauri::command]
pub async fn douane_acces(state: State<'_, AuthState>) -> Result<AccesJeu, ContentError> {
    let user = state
        .current_user()
        .await
        .ok_or(ContentError::NotAuthenticated)?;
    let token = jwt(state.inner()).await?;
    Ok(controler_acces(state.inner(), &token, &user.role).await?)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn envoi_impose_le_format_v2_sans_filtrer_les_champs() {
        let corps = corps_envoi(json!({
            "formVersion": 1,
            "firstName": "Itsuki",
            "champInconnu": true
        }))
        .unwrap();
        assert_eq!(corps["formVersion"], 2);
        assert_eq!(corps["firstName"], "Itsuki");
        // Un champ inconnu part tel quel : l'API le refuse avec un message.
        assert_eq!(corps["champInconnu"], true);
        let err = corps_envoi(json!(["pas", "un", "objet"])).unwrap_err();
        assert_eq!(err.kind(), "invalid_input");
    }

    #[test]
    fn refus_de_validation_complet_en_invalid_input() {
        let body = r#"{"statusCode":400,"message":["history: Encore 38 caractères (150 minimum).","firstName: Lettres, espaces, apostrophes et tirets, de 3 à 20 caractères."],"error":"Bad Request"}"#;
        let err = erreur_de_saisie(ApiError::Status {
            status: 400,
            body: body.into(),
        });
        assert_eq!(err.kind(), "invalid_input");
        assert_eq!(
            err.to_string(),
            "history: Encore 38 caractères (150 minimum) ; firstName: Lettres, espaces, apostrophes et tirets, de 3 à 20 caractères (HTTP 400)."
        );
        // Autre code : erreur API habituelle.
        let conflit = erreur_de_saisie(ApiError::Status {
            status: 409,
            body: r#"{"message":"Ta candidature est déjà acceptée."}"#.into(),
        });
        assert_eq!(conflit.kind(), "conflict");
    }

    #[test]
    fn brouillon_enveloppe_data_et_format() {
        let corps = corps_brouillon(json!({ "history": "Né à Kiri" })).unwrap();
        assert_eq!(
            corps,
            json!({ "data": { "history": "Né à Kiri" }, "formVersion": 2 })
        );
        assert!(corps_brouillon(json!("texte")).is_err());
        assert!(corps_brouillon(Json::Null).is_err());
    }

    #[test]
    fn identifiants_canoniques_seulement() {
        let ok = "5b8e2d14-7a3c-4f91-8e06-c4d2a9b7f310";
        assert_eq!(identifiant(ok, "Séance").unwrap(), ok);
        for mauvais in [
            "",
            "5b8e2d147a3c4f918e06c4d2a9b7f310",
            "../admin/douane/seances-aaaaaaaaaaaa",
            "5b8e2d14-7a3c-4f91-8e06-c4d2a9b7f31g",
            "urn:uuid:5b8e2d14-7a3c-4f91-8e06-c4d",
        ] {
            let err = identifiant(mauvais, "Séance").unwrap_err();
            assert_eq!(err.to_string(), "Séance invalide.");
        }
    }

    #[test]
    fn voix_en_corps_brut_ou_tableau_de_secours() {
        let brut = InvokeBody::Raw(vec![0x1a, 0x45, 0xdf, 0xa3]);
        assert_eq!(
            octets_du_corps(&brut).unwrap(),
            vec![0x1a, 0x45, 0xdf, 0xa3]
        );
        let tableau = InvokeBody::Json(json!([79, 103, 103, 83]));
        assert_eq!(octets_du_corps(&tableau).unwrap(), b"OggS".to_vec());
        assert!(octets_du_corps(&InvokeBody::Json(json!([300]))).is_err());
        assert!(octets_du_corps(&InvokeBody::Json(json!({ "octets": [1] }))).is_err());
    }

    #[test]
    fn voix_type_duree_et_taille() {
        let ok = lire_voix(vec![1; 2048], Some("audio/webm;codecs=opus"), Some(" 42 ")).unwrap();
        assert_eq!(ok.type_mime, "audio/webm");
        assert_eq!(ok.duree_sec, 42);
        assert_eq!(
            lire_voix(vec![1; 10], Some("AUDIO/OGG"), Some("15"))
                .unwrap()
                .type_mime,
            "audio/ogg"
        );

        let err = lire_voix(vec![1; 10], Some("audio/mpeg"), Some("20")).unwrap_err();
        assert!(err.to_string().contains("« audio/mpeg »"));
        assert!(lire_voix(vec![1; 10], None, Some("20")).is_err());
        assert!(lire_voix(vec![1; 10], Some("audio/webm"), Some("12.5")).is_err());
        assert!(lire_voix(vec![1; 10], Some("audio/webm"), Some("0")).is_err());
        assert!(lire_voix(vec![1; 10], Some("audio/webm"), None).is_err());
        assert!(lire_voix(Vec::new(), Some("audio/webm"), Some("20")).is_err());
        let lourd = lire_voix(
            vec![0; VOIX_OCTETS_GARDE_FOU + 1],
            Some("audio/webm"),
            Some("20"),
        );
        assert!(lourd.unwrap_err().to_string().contains("trop lourd"));
    }

    fn etat(phase: &str, fenetre: Option<(&str, &str)>, en_jeu: bool) -> EtatDouaneLu {
        EtatDouaneLu {
            phase: phase.to_string(),
            reservation: fenetre.map(|(etat, ouvre)| ReservationLue {
                convocation_at: "2026-10-31T20:00:00.000Z".into(),
                fenetre: FenetreLue {
                    ouvre_at: ouvre.into(),
                    ferme_at: "2026-10-31T20:30:00.000Z".into(),
                    etat: etat.into(),
                },
            }),
            derniere_reservation: None,
            regles: Some(ReglesLues {
                avance_entree_min: 15,
                douane_en_jeu: en_jeu,
            }),
        }
    }

    const OUVRE: &str = "2026-10-31T19:45:00.000Z";

    fn ms(iso: &str) -> i64 {
        clock::parse_rfc3339_ms(iso).unwrap()
    }

    #[test]
    fn acces_accepte_et_douane_ouverte() {
        let a = decider_acces(&etat("ACCEPTE", None, true), 0);
        assert!(a.autorise);
        assert_eq!(a.portee, Some(Portee::Play));

        let d = decider_acces(&etat("DOUANE_RESERVEE", Some(("OUVERTE", OUVRE)), true), 0);
        assert!(d.autorise);
        assert_eq!(d.portee, Some(Portee::Douane));
        assert_eq!(d.ouvre_at.as_deref(), Some(OUVRE));
        assert_eq!(
            d.convocation_at.as_deref(),
            Some("2026-10-31T20:00:00.000Z")
        );
    }

    #[test]
    fn douane_a_venir_donne_le_delai() {
        let now = ms("2026-10-31T17:40:00.000Z");
        let a = decider_acces(
            &etat("DOUANE_RESERVEE", Some(("A_VENIR", OUVRE)), true),
            now,
        );
        assert!(!a.autorise);
        let refus = a.refus.unwrap();
        assert_eq!(refus.kind, REFUS_PAS_OUVERTE);
        assert_eq!(
            refus.message,
            "L'entrée de ta séance de douane ouvre dans 2 h 05 (15 min avant ta convocation) : relance le jeu à ce moment-là."
        );
        // Horloge du PC en avance : l'API dit « à venir », pas de délai négatif.
        let tard = decider_acces(
            &etat("DOUANE_RESERVEE", Some(("A_VENIR", OUVRE)), true),
            ms("2026-10-31T19:50:00.000Z"),
        );
        assert!(tard
            .refus
            .unwrap()
            .message
            .contains("ouvre dans un instant"));
    }

    #[test]
    fn douane_fermee_hors_jeu_et_autres_phases() {
        // Place encore réservée, entrée fermée : candidat arrivé, pas « manquée ».
        let f = decider_acces(&etat("DOUANE_RESERVEE", Some(("FERMEE", OUVRE)), true), 0);
        assert!(!f.autorise);
        let refus = f.refus.unwrap();
        assert_eq!(refus.kind, REFUS_CANDIDATURE);
        assert!(refus.message.contains("arrivée est enregistrée"));
        assert_eq!(
            f.convocation_at.as_deref(),
            Some("2026-10-31T20:00:00.000Z")
        );

        let h = decider_acces(&etat("DOUANE_RESERVEE", Some(("OUVERTE", OUVRE)), false), 0);
        assert!(!h.autorise);
        assert_eq!(h.refus.unwrap().kind, REFUS_HORS_JEU);

        let sans_place = decider_acces(&etat("DOUANE_RESERVEE", None, true), 0);
        assert_eq!(sans_place.refus.unwrap().kind, REFUS_CANDIDATURE);

        for phase in [
            "BROUILLON",
            "ECRIT_EN_ATTENTE",
            "ECRIT_A_REVOIR",
            "DOUANE_A_RESERVER",
            "DOUANE_A_REVOIR",
            "REFUSE",
            "PHASE_FUTURE",
        ] {
            let a = decider_acces(&etat(phase, None, true), 0);
            assert!(!a.autorise, "{phase}");
            let refus = a.refus.unwrap();
            assert_eq!(refus.kind, REFUS_CANDIDATURE, "{phase}");
            // L'entrée du rail s'appelle « Whitelist ».
            assert!(refus.message.contains("onglet Whitelist"), "{phase}");
            assert!(!refus.message.contains("onglet Candidature"), "{phase}");
            assert_eq!(a.phase.as_deref(), Some(phase));
        }
    }

    fn manquee(phase: &str, statut: &str, ferme: &str, en_jeu: bool) -> EtatDouaneLu {
        let mut e = etat(phase, None, en_jeu);
        e.derniere_reservation = Some(DernierePlaceLue {
            statut: statut.into(),
            convocation_at: "2026-10-31T20:30:00.000Z".into(),
            ferme_at: ferme.into(),
        });
        e
    }

    #[test]
    fn seance_manquee_recente_en_refus_ferme() {
        const FERME: &str = "2026-10-31T21:00:00.000Z";
        let apres = ms("2026-10-31T21:20:00.000Z");
        for phase in ["DOUANE_A_RESERVER", "DOUANE_A_REVOIR"] {
            let a = decider_acces(&manquee(phase, "MISSED", FERME, true), apres);
            assert!(!a.autorise, "{phase}");
            let refus = a.refus.unwrap();
            assert_eq!(refus.kind, REFUS_FERMEE, "{phase}");
            assert!(refus.message.contains("onglet Whitelist"));
            assert_eq!(
                a.convocation_at.as_deref(),
                Some("2026-10-31T20:30:00.000Z")
            );
            assert_eq!(a.ferme_at.as_deref(), Some(FERME));
            assert_eq!(a.phase.as_deref(), Some(phase));
        }
        // Trop ancienne : retour à « réserve ta douane ».
        let vieille = decider_acces(
            &manquee("DOUANE_A_RESERVER", "MISSED", FERME, true),
            ms("2026-11-01T10:00:00.000Z"),
        );
        assert_eq!(vieille.refus.unwrap().kind, REFUS_CANDIDATURE);
        // Annulée (staff, séance annulée) : pas une séance manquée.
        let annulee = decider_acces(
            &manquee("DOUANE_A_RESERVER", "CANCELLED", FERME, true),
            apres,
        );
        assert_eq!(annulee.refus.unwrap().kind, REFUS_CANDIDATURE);
        // Oral hors jeu : aucune arrivée ne peut être relevée en jeu.
        let hors_jeu = decider_acces(&manquee("DOUANE_A_RESERVER", "MISSED", FERME, false), apres);
        assert_eq!(hors_jeu.refus.unwrap().kind, REFUS_CANDIDATURE);
        // Date illisible : refus habituel, sans heure.
        let illisible = decider_acces(&manquee("DOUANE_A_RESERVER", "MISSED", "hier", true), apres);
        assert_eq!(illisible.refus.unwrap().kind, REFUS_CANDIDATURE);
        assert_eq!(illisible.ferme_at, None);
    }

    #[test]
    fn etat_douane_lu_avec_derniere_place() {
        let e: EtatDouaneLu = serde_json::from_value(json!({
            "phase": "DOUANE_A_RESERVER",
            "reservation": null,
            "derniereReservation": {
                "statut": "MISSED",
                "convocationAt": "2026-10-31T20:30:00.000Z",
                "fermeAt": "2026-10-31T21:00:00.000Z",
                "motifAnnulation": null
            },
            "regles": { "avanceEntreeMin": 15, "retardMaxMin": 30, "douaneEnJeu": true }
        }))
        .unwrap();
        let a = decider_acces(&e, ms("2026-10-31T21:05:00.000Z"));
        assert_eq!(a.refus.unwrap().kind, REFUS_FERMEE);
    }

    #[test]
    fn accepte_sans_promotion_refuse_avant_installation() {
        let a = sans_promotion(decider_acces(&etat("ACCEPTE", None, true), 0));
        assert!(!a.autorise);
        assert_eq!(a.portee, None);
        assert_eq!(a.phase.as_deref(), Some("ACCEPTE"));
        let refus = a.refus.unwrap();
        assert_eq!(refus.kind, REFUS_CANDIDATURE);
        assert_eq!(refus.message, MSG_SANS_PROMOTION);
        // La douane dans sa fenêtre et les refus passent tels quels.
        let d = sans_promotion(decider_acces(
            &etat("DOUANE_RESERVEE", Some(("OUVERTE", OUVRE)), true),
            0,
        ));
        assert!(d.autorise);
        assert_eq!(d.portee, Some(Portee::Douane));
        let b = sans_promotion(decider_acces(&etat("BROUILLON", None, true), 0));
        assert!(b.refus.unwrap().message.contains("pas encore envoyée"));
    }

    #[test]
    fn chemin_de_la_voix_selon_la_source() {
        assert_eq!(chemin_voix(None).unwrap(), "/whitelist/me/voix");
        assert_eq!(chemin_voix(Some(" ")).unwrap(), "/whitelist/me/voix");
        assert_eq!(
            chemin_voix(Some("candidature")).unwrap(),
            "/whitelist/me/voix?source=candidature"
        );
        assert_eq!(
            chemin_voix(Some("brouillon")).unwrap(),
            "/whitelist/me/voix?source=brouillon"
        );
        for mauvais in ["autre", "candidature&x=1", "../brouillon"] {
            assert_eq!(
                chemin_voix(Some(mauvais)).unwrap_err().kind(),
                "invalid_input"
            );
        }
    }

    #[test]
    fn delais_lisibles() {
        assert_eq!(delai_lisible(0), None);
        assert_eq!(delai_lisible(-5).as_deref(), None);
        assert_eq!(delai_lisible(1).as_deref(), Some("1 min"));
        assert_eq!(delai_lisible(59 * 60_000).as_deref(), Some("59 min"));
        assert_eq!(delai_lisible(60 * 60_000).as_deref(), Some("1 h"));
        assert_eq!(delai_lisible(125 * 60_000).as_deref(), Some("2 h 05"));
        assert_eq!(delai_lisible(24 * 3_600_000).as_deref(), Some("1 jour"));
        assert_eq!(delai_lisible(73 * 3_600_000).as_deref(), Some("3 jours"));
    }

    #[test]
    fn acces_serialise_en_camel_case() {
        let a = decider_acces(&etat("DOUANE_RESERVEE", Some(("OUVERTE", OUVRE)), true), 0);
        let v = serde_json::to_value(&a).unwrap();
        assert_eq!(v["autorise"], true);
        assert_eq!(v["portee"], "douane");
        assert_eq!(v["phase"], "DOUANE_RESERVEE");
        assert_eq!(v["refus"], Json::Null);
        assert_eq!(v["ouvreAt"], OUVRE);
        assert_eq!(v["fermeAt"], "2026-10-31T20:30:00.000Z");
        let r = serde_json::to_value(decider_acces(&etat("BROUILLON", None, true), 0)).unwrap();
        assert_eq!(r["refus"]["kind"], REFUS_CANDIDATURE);
        assert_eq!(r["portee"], Json::Null);
    }

    #[test]
    fn etat_douane_lu_tolere_les_champs_absents() {
        let e: EtatDouaneLu = serde_json::from_value(json!({
            "phase": "DOUANE_RESERVEE",
            "peutReserver": false,
            "reservation": {
                "id": "r1",
                "convocationAt": "2026-10-31T20:00:00.000Z",
                "fenetre": { "ouvreAt": OUVRE, "fermeAt": "2026-10-31T20:30:00.000Z", "etat": "OUVERTE" }
            },
            "regles": { "retardMaxMin": 30, "douaneEnJeu": true },
            "seances": []
        }))
        .unwrap();
        let r = e.regles.as_ref().unwrap();
        assert_eq!(r.avance_entree_min, AVANCE_ENTREE_DEFAUT_MIN);
        assert!(decider_acces(&e, 0).autorise);
    }
}
