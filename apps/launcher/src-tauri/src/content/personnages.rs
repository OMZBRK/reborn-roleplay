//! « Mes personnages » (CANDIDATURE_ET_DOUANE.md §4.6, lot J) : les
//! personnages du compte sur le serveur de prod, lus sur `GET /v1/me/characters`
//! (JWT). L'API filtre déjà le compte et le serveur ; le N° n'existe que dans
//! cette vue du propriétaire. Écran en lecture seule : on crée et on joue en jeu.
//!
//! **Lecture tolérante.** Contrairement à la candidature (passe-plat), la
//! réponse est relue ici, carte par carte, pour que l'écran tienne toujours :
//! - une carte illisible (identifiant absent, champ du mauvais type) est
//!   écartée et journalisée, les autres restent affichées ;
//! - un code inconnu (type, sexe, état) retombe sur la valeur par défaut du
//!   contrat (`NINJA`, aucun sexe, `ALIVE`) au lieu de faire échouer la liste ;
//! - le N° affiché (`00042`) est reconstruit depuis `playerId` si l'API ne
//!   l'envoie pas ;
//! - textes rognés, vides ramenés à `None` ; « Autre » (clan personnalisé du
//!   mod) vaut « sans clan ».
//!
//! L'ordre de l'API est gardé (création en jeu, puis N°). Le contrat côté front
//! est dans `src/lib/personnages.ts`.

use serde::{Deserialize, Deserializer, Serialize};
use serde_json::Value as Json;
use tauri::State;

use super::{jwt, ContentError};
use crate::auth::AuthState;

/// Plus grand N° possible (entier signé 32 bits côté API et plugin).
const NUMERO_MAX: i64 = i32::MAX as i64;

/// Âge plausible d'un personnage, comme le contrôle de la synchronisation.
const AGE_MAX: i64 = 200;

/// Clan personnalisé du mod : enregistré à null par l'API, relu ici par prudence.
const CLAN_SANS: &str = "Autre";

// ──────────────────────────────────────────────────────
//  Contrat vers le front
// ──────────────────────────────────────────────────────

/// Réponse de `me_characters`.
#[derive(Debug, Clone, Default, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct MesPersonnages {
    pub characters: Vec<PersonnageCompte>,
}

/// Type choisi à la candidature ou à la création (Q3).
#[derive(Debug, Clone, Copy, Default, Serialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum TypePersonnage {
    #[default]
    Ninja,
    Civil,
}

#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum SexePersonnage {
    Homme,
    Femme,
}

/// En vie, mort RP ou supprimé (la suppression passe avant la mort, côté API).
#[derive(Debug, Clone, Copy, Default, Serialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum EtatPersonnage {
    #[default]
    Alive,
    Dead,
    Deleted,
}

/// Dossier d'entrée du personnage issu de la candidature, en lecture seule.
#[derive(Debug, Clone, Default, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct DossierEntree {
    /// Histoire de la candidature (Markdown léger du formulaire).
    #[serde(default, deserialize_with = "texte")]
    pub history: String,
    #[serde(default, deserialize_with = "texte")]
    pub objectives: String,
    /// Date de l'oral (douane), ISO 8601 ; `None` s'il n'est pas daté.
    #[serde(default, deserialize_with = "texte_option")]
    pub oral_decided_at: Option<String>,
}

/// Une carte de « Mes personnages ». Dates en ISO 8601 UTC.
#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct PersonnageCompte {
    /// Identifiant côté jeu (ShinobiCharacter.id).
    #[serde(deserialize_with = "identifiant")]
    pub character_id: String,
    /// N° brut ; `None` tant que le serveur ne l'a pas attribué.
    #[serde(default, deserialize_with = "numero")]
    pub player_id: Option<u32>,
    /// N° affiché (« 00042 »).
    #[serde(default, deserialize_with = "texte_option")]
    pub player_number: Option<String>,
    #[serde(default, deserialize_with = "texte")]
    pub first_name: String,
    /// Nom de famille libre (personnage sans clan).
    #[serde(default, deserialize_with = "texte_option")]
    pub last_name: Option<String>,
    /// Clan acquis en jeu, lors d'une session de clan ; jamais à la création.
    #[serde(default, deserialize_with = "clan")]
    pub clan: Option<String>,
    /// Valeur du mod (« Konohagakure »…).
    #[serde(default, deserialize_with = "texte_option")]
    pub village: Option<String>,
    #[serde(default, rename = "type", deserialize_with = "type_personnage")]
    pub kind: TypePersonnage,
    /// Code du grade (`GENIN`…), jamais le libellé : le front traduit.
    #[serde(default, deserialize_with = "texte_option")]
    pub rank: Option<String>,
    #[serde(default, deserialize_with = "sexe")]
    pub sex: Option<SexePersonnage>,
    #[serde(default, deserialize_with = "age")]
    pub age: Option<u32>,
    #[serde(default, deserialize_with = "etat")]
    pub state: EtatPersonnage,
    #[serde(default, deserialize_with = "minutes")]
    pub play_minutes: u64,
    /// Début du décompte du temps de jeu (première synchronisation).
    #[serde(default, deserialize_with = "texte_option")]
    pub play_minutes_since: Option<String>,
    /// Création EN JEU ; `None` si le serveur ne la connaît pas.
    #[serde(default, deserialize_with = "texte_option")]
    pub created_at: Option<String>,
    #[serde(default, deserialize_with = "texte_option")]
    pub last_seen_at: Option<String>,
    /// Premier personnage, repris de la candidature.
    #[serde(default, deserialize_with = "booleen")]
    pub from_application: bool,
    /// Présent seulement si la candidature liée est bien celle du compte.
    #[serde(default)]
    pub dossier: Option<DossierEntree>,
}

/// Corps brut de `GET /v1/me/characters` : les cartes restent en JSON pour
/// être relues une à une (une carte illisible n'emporte pas les autres).
#[derive(Debug, Default, Deserialize)]
pub struct MesPersonnagesApi {
    #[serde(default)]
    pub characters: Vec<Json>,
}

// ──────────────────────────────────────────────────────
//  Lecture tolérante des champs
// ──────────────────────────────────────────────────────

/// Texte rogné ; vide = `None`.
fn rogne(s: Option<String>) -> Option<String> {
    s.map(|t| t.trim().to_owned()).filter(|t| !t.is_empty())
}

fn texte_option<'de, D: Deserializer<'de>>(d: D) -> Result<Option<String>, D::Error> {
    Ok(rogne(Option::<String>::deserialize(d)?))
}

fn texte<'de, D: Deserializer<'de>>(d: D) -> Result<String, D::Error> {
    Ok(rogne(Option::<String>::deserialize(d)?).unwrap_or_default())
}

/// Identifiant requis, non vide (clé des cartes côté front).
fn identifiant<'de, D: Deserializer<'de>>(d: D) -> Result<String, D::Error> {
    rogne(Some(String::deserialize(d)?)).ok_or_else(|| serde::de::Error::custom("characterId vide"))
}

fn clan<'de, D: Deserializer<'de>>(d: D) -> Result<Option<String>, D::Error> {
    Ok(texte_option(d)?.filter(|c| !c.eq_ignore_ascii_case(CLAN_SANS)))
}

/// Entier dans `[0, max]`, sinon `None`. Un nombre décimal ou hors bornes ne
/// fait pas échouer la carte.
fn entier_borne<'de, D: Deserializer<'de>>(d: D, max: i64) -> Result<Option<u32>, D::Error> {
    let n = Option::<Json>::deserialize(d)?;
    Ok(n.as_ref()
        .and_then(Json::as_i64)
        .filter(|v| (0..=max).contains(v))
        .and_then(|v| u32::try_from(v).ok()))
}

fn numero<'de, D: Deserializer<'de>>(d: D) -> Result<Option<u32>, D::Error> {
    entier_borne(d, NUMERO_MAX)
}

fn age<'de, D: Deserializer<'de>>(d: D) -> Result<Option<u32>, D::Error> {
    entier_borne(d, AGE_MAX)
}

/// Temps de jeu en minutes ; négatif ou illisible = 0.
fn minutes<'de, D: Deserializer<'de>>(d: D) -> Result<u64, D::Error> {
    let n = Option::<Json>::deserialize(d)?;
    Ok(n.as_ref().and_then(Json::as_u64).unwrap_or(0))
}

fn booleen<'de, D: Deserializer<'de>>(d: D) -> Result<bool, D::Error> {
    Ok(Option::<bool>::deserialize(d)?.unwrap_or(false))
}

/// Code d'énumération lu sans tenir compte de la casse.
fn code<'de, D: Deserializer<'de>>(d: D) -> Result<Option<String>, D::Error> {
    Ok(texte_option(d)?.map(|c| c.to_ascii_uppercase()))
}

fn type_personnage<'de, D: Deserializer<'de>>(d: D) -> Result<TypePersonnage, D::Error> {
    Ok(match code(d)?.as_deref() {
        Some("CIVIL") => TypePersonnage::Civil,
        _ => TypePersonnage::Ninja,
    })
}

fn sexe<'de, D: Deserializer<'de>>(d: D) -> Result<Option<SexePersonnage>, D::Error> {
    Ok(match code(d)?.as_deref() {
        Some("HOMME") => Some(SexePersonnage::Homme),
        Some("FEMME") => Some(SexePersonnage::Femme),
        _ => None,
    })
}

fn etat<'de, D: Deserializer<'de>>(d: D) -> Result<EtatPersonnage, D::Error> {
    Ok(match code(d)?.as_deref() {
        Some("DEAD") => EtatPersonnage::Dead,
        Some("DELETED") => EtatPersonnage::Deleted,
        _ => EtatPersonnage::Alive,
    })
}

/// N° affiché : 42 → « 00042 » (PlayerIdRegistry.format côté plugin).
fn format_numero(n: u32) -> String {
    format!("{n:05}")
}

/// Une carte de l'API, ou la raison de son rejet.
fn lire_personnage(brut: Json) -> Result<PersonnageCompte, serde_json::Error> {
    let mut p: PersonnageCompte = serde_json::from_value(brut)?;
    if p.player_number.is_none() {
        p.player_number = p.player_id.map(format_numero);
    }
    Ok(p)
}

/// Réponse de l'API → cartes affichables, dans l'ordre reçu.
fn lire_personnages(brut: MesPersonnagesApi) -> MesPersonnages {
    let characters = brut
        .characters
        .into_iter()
        .enumerate()
        .filter_map(|(i, carte)| {
            lire_personnage(carte)
                .inspect_err(|e| {
                    tracing::warn!("Mes personnages : carte n° {i} illisible, ignorée ({e})")
                })
                .ok()
        })
        .collect();
    MesPersonnages { characters }
}

// ──────────────────────────────────────────────────────
//  Commande
// ──────────────────────────────────────────────────────

/// Personnages du compte connecté, sur le serveur de prod.
#[tauri::command]
pub async fn me_characters(state: State<'_, AuthState>) -> Result<MesPersonnages, ContentError> {
    let token = jwt(state.inner()).await?;
    let brut = state.api.fetch_my_characters(&token).await?;
    Ok(lire_personnages(brut))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn lire(v: Json) -> MesPersonnages {
        lire_personnages(serde_json::from_value(v).unwrap())
    }

    #[test]
    fn lit_une_reponse_complete_et_la_rend_en_camel_case() {
        let r = lire(json!({
            "characters": [{
                "characterId": "1b4e28ba-2fa1-11d2-883f-0016d3cca427",
                "playerId": 42,
                "playerNumber": "00042",
                "firstName": "Kaito",
                "lastName": "Arashi",
                "clan": null,
                "village": "Konohagakure",
                "type": "NINJA",
                "rank": "GENIN",
                "sex": "HOMME",
                "age": 16,
                "state": "ALIVE",
                "playMinutes": 2052,
                "playMinutesSince": "2026-10-26T18:00:00.000Z",
                "createdAt": "2026-10-25T21:42:00.000Z",
                "lastSeenAt": "2026-11-03T22:14:00.000Z",
                "fromApplication": true,
                "dossier": {
                    "history": "## Enfance\n\nNé à Konoha.",
                    "objectives": "Devenir chūnin.",
                    "oralDecidedAt": "2026-10-25T21:30:00.000Z"
                }
            }]
        }));
        assert_eq!(r.characters.len(), 1);
        let p = &r.characters[0];
        assert_eq!(p.player_id, Some(42));
        assert_eq!(p.player_number.as_deref(), Some("00042"));
        assert_eq!(p.kind, TypePersonnage::Ninja);
        assert_eq!(p.sex, Some(SexePersonnage::Homme));
        assert_eq!(p.state, EtatPersonnage::Alive);
        assert_eq!(p.play_minutes, 2052);
        assert!(p.from_application);
        let dossier = p.dossier.as_ref().unwrap();
        assert_eq!(dossier.objectives, "Devenir chūnin.");

        let sortie = serde_json::to_value(&r).unwrap();
        let c = &sortie["characters"][0];
        assert_eq!(c["characterId"], "1b4e28ba-2fa1-11d2-883f-0016d3cca427");
        assert_eq!(c["playerNumber"], "00042");
        assert_eq!(c["firstName"], "Kaito");
        assert_eq!(c["type"], "NINJA");
        assert_eq!(c["sex"], "HOMME");
        assert_eq!(c["state"], "ALIVE");
        assert_eq!(c["playMinutesSince"], "2026-10-26T18:00:00.000Z");
        assert_eq!(c["fromApplication"], true);
        assert_eq!(c["dossier"]["oralDecidedAt"], "2026-10-25T21:30:00.000Z");
        assert_eq!(c["clan"], Json::Null);
    }

    #[test]
    fn champs_absents_valeurs_par_defaut_du_contrat() {
        let r = lire(json!({
            "characters": [{ "characterId": "c-1", "firstName": "Ren" }]
        }));
        let p = &r.characters[0];
        assert_eq!(p.kind, TypePersonnage::Ninja);
        assert_eq!(p.state, EtatPersonnage::Alive);
        assert_eq!(p.player_id, None);
        assert_eq!(p.player_number, None);
        assert_eq!(p.sex, None);
        assert_eq!(p.age, None);
        assert_eq!(p.play_minutes, 0);
        assert!(!p.from_application);
        assert_eq!(p.dossier, None);
        // Enveloppe vide ou sans liste : état vide, pas d'erreur.
        assert_eq!(lire(json!({})).characters, vec![]);
        assert_eq!(lire(json!({ "characters": [] })).characters, vec![]);
    }

    #[test]
    fn numero_reconstruit_depuis_player_id() {
        let r = lire(json!({
            "characters": [
                { "characterId": "a", "firstName": "A", "playerId": 7 },
                { "characterId": "b", "firstName": "B", "playerId": 0, "playerNumber": "  " },
                { "characterId": "c", "firstName": "C", "playerId": 117, "playerNumber": "00117" }
            ]
        }));
        let numeros: Vec<_> = r
            .characters
            .iter()
            .map(|p| p.player_number.as_deref())
            .collect();
        assert_eq!(numeros, vec![Some("00007"), Some("00000"), Some("00117")]);
    }

    #[test]
    fn codes_inconnus_et_bornes() {
        let r = lire(json!({
            "characters": [{
                "characterId": "x",
                "firstName": "  Kaito  ",
                "lastName": "",
                "clan": "autre",
                "village": "   ",
                "type": "civil",
                "sex": "AUTRE",
                "state": "dead",
                "age": 250,
                "playerId": -1,
                "playMinutes": -5,
                "fromApplication": null
            }, {
                "characterId": "y",
                "firstName": "Ren",
                "type": "SAMOURAI",
                "state": "ARCHIVE",
                "age": 19.5,
                "playerId": 2147483648_i64
            }]
        }));
        let a = &r.characters[0];
        assert_eq!(a.first_name, "Kaito");
        assert_eq!(a.last_name, None);
        assert_eq!(a.clan, None, "« Autre » vaut sans clan");
        assert_eq!(a.village, None);
        assert_eq!(a.kind, TypePersonnage::Civil);
        assert_eq!(a.sex, None);
        assert_eq!(a.state, EtatPersonnage::Dead);
        assert_eq!(a.age, None);
        assert_eq!(a.player_id, None);
        assert_eq!(a.player_number, None);
        assert_eq!(a.play_minutes, 0);
        assert!(!a.from_application);

        let b = &r.characters[1];
        assert_eq!(b.kind, TypePersonnage::Ninja);
        assert_eq!(b.state, EtatPersonnage::Alive);
        assert_eq!(b.age, None);
        assert_eq!(b.player_id, None);
    }

    #[test]
    fn supprime_et_clan_acquis() {
        let r = lire(json!({
            "characters": [{
                "characterId": "z",
                "firstName": "Ren",
                "clan": "Hozuki",
                "state": "DELETED"
            }]
        }));
        let p = &r.characters[0];
        assert_eq!(p.clan.as_deref(), Some("Hozuki"));
        assert_eq!(p.state, EtatPersonnage::Deleted);
        assert_eq!(serde_json::to_value(p).unwrap()["state"], "DELETED");
    }

    #[test]
    fn une_carte_illisible_n_emporte_pas_les_autres() {
        let r = lire(json!({
            "characters": [
                { "firstName": "Sans identifiant" },
                { "characterId": "", "firstName": "Identifiant vide" },
                { "characterId": "ok", "firstName": 12 },
                "texte",
                { "characterId": "bon", "firstName": "Kaito" }
            ]
        }));
        let ids: Vec<_> = r
            .characters
            .iter()
            .map(|p| p.character_id.as_str())
            .collect();
        assert_eq!(ids, vec!["bon"]);
    }

    #[test]
    fn garde_l_ordre_de_l_api() {
        let r = lire(json!({
            "characters": [
                { "characterId": "2", "firstName": "B" },
                { "characterId": "1", "firstName": "A" }
            ]
        }));
        let ids: Vec<_> = r
            .characters
            .iter()
            .map(|p| p.character_id.as_str())
            .collect();
        assert_eq!(ids, vec!["2", "1"]);
    }
}
