import { isIpcError } from "./errors";
import { invoke, invokeRaw } from "./tauri";

// Candidature v2 et séances de douane (docs/gameplay/CANDIDATURE_ET_DOUANE.md
// §2 et §3.3) : wrappers typés des commandes Tauri de
// src-tauri/src/content/candidature.rs. Le front ne parle jamais HTTP.
//
// Le Rust fait passe-plat : les formes ci-dessous sont celles de l'API
// (apps/api/src/whitelist, apps/api/src/douane). Dates en ISO 8601 UTC, à
// afficher à l'heure locale. Tout envoi part avec `formVersion: 2`, posé par
// le Rust : l'API refuse tout autre format.
//
// Erreurs : objets { kind, message } (contrat IPC), à afficher avec
// formatIpcError (lib/errors.ts). Un refus de validation de l'API (envoi,
// renvoi, brouillon) arrive en `kind: "invalid_input"`, avec TOUS ses
// messages : « champ: texte ; champ: texte (HTTP 400). ». erreursParChamp()
// les range par champ, pour les afficher sous le bon champ.
//
// Messages du fil, retrait et demande de reprise : commandes existantes,
// ré-exportées en bas de ce fichier.

// ──────────────────────────────────────────────────────
// Listes fermées et décisions
// ──────────────────────────────────────────────────────

export type StatutDecision = "PENDING" | "APPROVED" | "REJECTED" | "NEEDS_REVISION";

/** Phase calculée par l'API, à lire AU LIEU du rôle (fiche §1.2). */
export type PhaseCandidature =
  | "BROUILLON"
  | "ECRIT_EN_ATTENTE"
  | "ECRIT_A_REVOIR"
  | "DOUANE_A_RESERVER"
  | "DOUANE_RESERVEE"
  | "DOUANE_A_REVOIR"
  | "REFUSE"
  | "ACCEPTE";

export type RpYears =
  | "JAMAIS"
  | "MOINS_D_UN_AN"
  | "UN_A_DEUX_ANS"
  | "TROIS_A_CINQ_ANS"
  | "SIX_ANS_ET_PLUS";

export type NarutoKnowledge = "DEBUTANT" | "QUELQUES_ARCS" | "BONNE_CONNAISSANCE" | "INCOLLABLE";

export type DiscoverySource =
  | "DISCORD"
  | "TIKTOK"
  | "YOUTUBE"
  | "AMI"
  | "AUTRE_SERVEUR"
  | "RECHERCHE"
  | "AUTRE";

export type CharacterType = "NINJA" | "CIVIL";

/** Valeurs du mod : « Homme », « Femme ». */
export type CharacterSex = "HOMME" | "FEMME";

/** Où l'oral a été passé : en jeu (douane) ou hors jeu (transition, secours). */
export type OralVia = "DOUANE" | "HORS_JEU";

export type VoixType = "audio/webm" | "audio/ogg";

export type Bornes = { min: number; max: number };

/** Choix d'une liste fermée : code stocké + libellé français. */
export type ChoixListe<V extends string> = { valeur: V; libelle: string };

// ──────────────────────────────────────────────────────
// Options (GET /whitelist/options)
// ──────────────────────────────────────────────────────

/** Un des six villages, valeur EXACTE du mod (« Konohagakure »…). */
export type VillageOption = { valeur: string; court: string; pays: string };

/** Clan du mod, pour information : aucun clan ne se choisit à la candidature
 *  (Q4, tous en session de clan). Sert à refuser un nom de famille qui serait
 *  un nom de clan, comme le fait l'API. */
export type ClanInfo = { nom: string; village: string; surSession: boolean };

export type CandidatureOptions = {
  formVersion: 2;
  villages: VillageOption[];
  clans: ClanInfo[];
  /** Clan « personnalisé » du mod (« Autre ») : réservé, refusé comme nom de famille. */
  sansClan: string;
  /** Longueurs en caractères UTF-16 (`String.length`), comme l'API. */
  bornes: {
    playerAge: Bornes;
    availability: Bornes;
    firstName: Bornes;
    lastName: Bornes;
    characterAge: Bornes;
    history: Bornes;
    objectives: Bornes;
    support: Bornes;
    voixSec: Bornes;
  };
  listes: {
    rpYears: ChoixListe<RpYears>[];
    narutoKnowledge: ChoixListe<NarutoKnowledge>[];
    discoverySource: ChoixListe<DiscoverySource>[];
    characterType: ChoixListe<CharacterType>[];
    characterSex: ChoixListe<CharacterSex>[];
  };
  voix: { types: VoixType[]; octetsMax: number; dureeMinSec: number; dureeMaxSec: number };
  /** « Refusé » : nouvelle candidature complète après ce délai (24 h). */
  delaiNouvelleCandidatureHeures: number;
};

// ──────────────────────────────────────────────────────
// Candidature et suivi (GET /whitelist/me)
// ──────────────────────────────────────────────────────

export type VoixVue = {
  mimeType: string;
  durationSec: number | null;
  uploadedAt: string | null;
};

/** Candidature telle que le candidat la voit : jamais les notes privées du
 *  staff ni de l'examinateur. Les champs de l'ancien formulaire (dob,
 *  motivation, experience, appearance) valent "" en v2. */
export type CandidatureV2 = {
  id: string;
  status: StatutDecision;
  hrpStatus: StatutDecision;
  rpStatus: StatutDecision;
  /** 1 = ancien formulaire (dossier migré), 2 = candidature v2. */
  formVersion: number;
  phase: PhaseCandidature;
  // Partie HRP (onglet Vous)
  playerAge: number | null;
  dob: string;
  rpYears: RpYears | null;
  narutoKnowledge: NarutoKnowledge | null;
  discoverySource: DiscoverySource | null;
  availability: string;
  motivation: string;
  experience: string;
  // Partie RP (Personnage, Village, Histoire, Voix)
  firstName: string;
  /** Nom de famille libre, jamais un nom de clan. */
  lastName: string;
  village: string;
  characterType: CharacterType | null;
  characterSex: CharacterSex | null;
  characterAge: number | null;
  support: string | null;
  history: string;
  appearance: string;
  objectives: string;
  /** Enregistrement joint, ou null (ancien dossier, ou effacé : voiceDeletedAt). */
  voix: VoixVue | null;
  voiceDeletedAt: string | null;
  rulesAcceptedAt: string | null;
  voiceConsentAt: string | null;
  // Méta et décisions visibles du joueur
  submittedAt: string;
  reviewedAt: string | null;
  /** Commentaire de l'ÉCRIT, visible du joueur. */
  reviewNotes: string | null;
  /** Pseudo du staff qui a tranché l'écrit en dernier. */
  reviewerName: string | null;
  writtenApprovedAt: string | null;
  oralStatus: StatutDecision;
  oralDecidedAt: string | null;
  oralVia: OralVia | null;
  /** Motif de l'oral, visible du joueur. */
  oralReason: string | null;
  /** « Pris en charge par @X depuis Yh » ; reprise possible après 4 h. */
  assignee: { username: string | null } | null;
  assignedAt: string | null;
};

export type SuiviCandidature = {
  phase: PhaseCandidature;
  /** REFUSE : nouvelle candidature complète possible à partir de… */
  canReapplyAt: string | null;
  /** REFUSE : refus de l'écrit ou de l'oral. */
  refusPar: "ECRIT" | "ORAL" | null;
  /** ECRIT_A_REVOIR : parties rouvertes ; les autres restent verrouillées. */
  partiesRouvertes: Array<"HRP" | "RP">;
  /** false si l'oral est déjà passé sur ce compte : écrit validé = accepté. */
  oralRequis: boolean;
  /** DOUANE_RESERVEE : la place et sa fenêtre d'entrée (H − 15 min à H + 30 min). */
  reservation: {
    id: string;
    seanceId: string;
    convocationAt: string;
    entreeOuvreAt: string;
    entreeFermeAt: string;
  } | null;
  /** DOUANE_A_REVOIR : nouvelle séance réservable à partir de… (lendemain). */
  reservableDes: string | null;
  /** false pendant la transition : oral hors jeu, ni « Passer la douane » ni textes « en jeu ». */
  douaneEnJeu: boolean;
};

export type CandidatureMe = {
  application: CandidatureV2 | null;
  suivi: SuiviCandidature;
};

// ──────────────────────────────────────────────────────
// Envoi (POST /whitelist, PATCH /whitelist/me)
// ──────────────────────────────────────────────────────

/** Champs de l'envoi v2 (`formVersion` posé par le Rust). Retirés le 24/10, à
 *  ne pas envoyer : experience, motivation, appearance (et dob). La voix ne
 *  passe pas ici : déposée avant dans le brouillon (deposerVoix). */
export type CandidatureEnvoi = {
  // Partie HRP
  playerAge: number;
  rpYears: RpYears;
  narutoKnowledge: NarutoKnowledge;
  /** Facultatif : null ou absent = « Pas de réponse ». */
  discoverySource?: DiscoverySource | null;
  availability: string;
  // Partie RP
  firstName: string;
  lastName: string;
  village: string;
  characterType: CharacterType;
  characterSex: CharacterSex;
  characterAge: number;
  /** Facultatif, lien https:// */
  support?: string | null;
  history: string;
  objectives: string;
  // Onglet Envoi : les deux cases cochées
  rulesAccepted: boolean;
  voiceConsent: boolean;
};

/** Renvoi après « À revoir » : seuls les champs des parties rouvertes
 *  (suivi.partiesRouvertes) sont lus ; les cases de l'onglet Envoi toujours. */
export type CandidatureRenvoi = Partial<CandidatureEnvoi> &
  Pick<CandidatureEnvoi, "rulesAccepted" | "voiceConsent">;

// ──────────────────────────────────────────────────────
// Brouillon serveur et voix
// ──────────────────────────────────────────────────────

/** Formulaire tel que l'écran le garde : objet libre, contrôle souple côté API
 *  (aucun minimum, 256 Ko et 100 champs au plus, textes jusqu'à deux fois leur
 *  maximum). */
export type DonneesBrouillon = Record<string, unknown>;

export type BrouillonVue = {
  formVersion: number;
  data: DonneesBrouillon;
  updatedAt: string;
  /** Enregistrement déposé dans le brouillon, s'il y en a un. */
  voix: VoixVue | null;
};

export type VoixDeposee = {
  mimeType: VoixType;
  durationSec: number;
  uploadedAt: string;
  octets: number;
};

// ──────────────────────────────────────────────────────
// Séances de douane (GET /douane/me…)
// ──────────────────────────────────────────────────────

export type EtatFenetre = "A_VENIR" | "OUVERTE" | "FERMEE";

/** Fenêtre d'entrée, calculée par l'API avec son horloge. */
export type FenetreEntree = { ouvreAt: string; fermeAt: string; etat: EtatFenetre };

export type StatutReservation = "BOOKED" | "DONE" | "MISSED" | "CANCELLED";

export type SeanceDouane = {
  id: string;
  startAt: string;
  endAt: string;
  placesTotal: number;
  placesRestantes: number;
  complet: boolean;
  /** Nombre seulement : le candidat ne voit pas qui examine. */
  nbExaminateurs: number;
  /** Heures de convocation (une seule si la séance dure 1 h au plus). */
  vagues: string[];
  /** Heure qu'obtiendrait une réservation maintenant (définitive à la réservation). */
  convocationIndicative: string | null;
};

export type ReservationDouane = {
  id: string;
  seanceId: string;
  statut: StatutReservation;
  convocationAt: string;
  fenetre: FenetreEntree;
  seance: { startAt: string; endAt: string };
  createdAt: string;
};

/** Dernière place perdue depuis la dernière décision (« séance manquée »). */
export type DernierePlace = {
  statut: "MISSED" | "CANCELLED";
  convocationAt: string;
  fermeAt: string;
  motifAnnulation: string | null;
};

export type EtatDouane = {
  phase: PhaseCandidature;
  /** Peut prendre une place (DOUANE_A_RESERVER, DOUANE_A_REVOIR). */
  peutReserver: boolean;
  /** A une place et peut en changer : reserverSeance(id, true). */
  peutChanger: boolean;
  /** Texte à afficher quand aucune des deux actions n'est possible. */
  raison: string | null;
  /** Après un oral « à revoir » : première date de séance permise. */
  prochaineSeanceDes: string | null;
  reservation: ReservationDouane | null;
  derniereReservation: DernierePlace | null;
  seances: SeanceDouane[];
  regles: { avanceEntreeMin: number; retardMaxMin: number; douaneEnJeu: boolean };
};

// ──────────────────────────────────────────────────────
// Accès au jeu (bouton « Jouer » / « Passer la douane »)
// ──────────────────────────────────────────────────────

export type PorteeJeu = "play" | "douane";

/** Codes des refus de lancement : même `kind` que le rejet de launchGame(). */
export type RefusAccesKind =
  | "candidature_required"
  | "douane_hors_jeu"
  | "douane_not_open"
  | "douane_closed"
  | "not_whitelisted";

export type AccesJeu = {
  /** true : le jeu peut être lancé maintenant (même décision que launchGame). */
  autorise: boolean;
  /** "douane" : le bouton devient « Passer la douane ». null si refusé. */
  portee: PorteeJeu | null;
  phase: PhaseCandidature | null;
  refus: { kind: RefusAccesKind; message: string } | null;
  /** DOUANE_RESERVEE : heure de convocation et fenêtre d'entrée. */
  convocationAt: string | null;
  ouvreAt: string | null;
  fermeAt: string | null;
};

// ──────────────────────────────────────────────────────
// Commandes
// ──────────────────────────────────────────────────────

/** En-têtes IPC de l'enregistrement vocal (mêmes noms que candidature.rs). */
const EN_TETE_VOIX_TYPE = "x-reborn-voix-type";
const EN_TETE_VOIX_DUREE = "x-reborn-voix-duree";

/** Villages, clans (pour information), bornes, libellés, contraintes de la voix. */
export async function lireOptions(): Promise<CandidatureOptions> {
  return invoke<CandidatureOptions>("candidature_options");
}

/** Candidature (null avant l'envoi) et suivi : phase, parties rouvertes, place, délais. */
export async function lireCandidature(): Promise<CandidatureMe> {
  return invoke<CandidatureMe>("candidature_me");
}

/** Première candidature, ou nouvelle candidature complète après un refus. */
export async function envoyerCandidature(candidature: CandidatureEnvoi): Promise<CandidatureV2> {
  return invoke<CandidatureV2>("candidature_envoyer", { candidature });
}

/** Renvoi après « À revoir » (parties rouvertes seulement). */
export async function renvoyerCandidature(candidature: CandidatureRenvoi): Promise<CandidatureV2> {
  return invoke<CandidatureV2>("candidature_renvoyer", { candidature });
}

export async function lireBrouillon(): Promise<{ brouillon: BrouillonVue | null }> {
  return invoke<{ brouillon: BrouillonVue | null }>("candidature_brouillon_lire");
}

/** Enregistrement automatique (1,5 s après la dernière frappe, au changement
 *  d'onglet, à la perte du focus). Refusé (409) une fois accepté. */
export async function ecrireBrouillon(data: DonneesBrouillon): Promise<{ brouillon: BrouillonVue }> {
  return invoke<{ brouillon: BrouillonVue }>("candidature_brouillon_ecrire", { data });
}

/** Efface le brouillon serveur et sa voix. */
export async function effacerBrouillon(): Promise<void> {
  await invoke<void>("candidature_brouillon_effacer");
}

/** « audio/webm;codecs=opus » → « audio/webm ». */
function typeDeBase(mime: string): string {
  return mime.split(";", 1)[0].trim().toLowerCase();
}

/**
 * Dépose l'enregistrement vocal dans le brouillon (remplace le précédent).
 * Octets envoyés en corps brut (MediaRecorder en Opus : audio/webm ou
 * audio/ogg), durée en secondes entières (15 à 60, contrôlée par l'API).
 */
export async function deposerVoix(enregistrement: Blob, dureeSec: number): Promise<{ voix: VoixDeposee }> {
  const octets = new Uint8Array(await enregistrement.arrayBuffer());
  return invokeRaw<{ voix: VoixDeposee }>("candidature_voix_deposer", octets, {
    [EN_TETE_VOIX_TYPE]: typeDeBase(enregistrement.type),
    [EN_TETE_VOIX_DUREE]: String(Math.round(dureeSec)),
  });
}

/**
 * Réécoute de son propre enregistrement (brouillon, sinon candidature).
 * `mimeType` : celui de `voix` dans le brouillon ou la candidature. Le Blob
 * se joue avec URL.createObjectURL (à révoquer après usage).
 *
 * `source: "candidature"` (« Mon dossier ») : la voix ENVOYÉE, même quand un
 * nouvel enregistrement attend dans le brouillon (« À revoir », nouvelle
 * candidature après un refus). Une API plus ancienne ignore ce choix et sert
 * d'abord celle du brouillon.
 */
export async function ecouterMaVoix(mimeType: string, source?: "brouillon" | "candidature"): Promise<Blob> {
  const octets = await invoke<ArrayBuffer>("candidature_voix_ecouter", source ? { source } : undefined);
  return new Blob([octets], { type: mimeType });
}

/** Phase, place réservée avec sa fenêtre, séances ouvertes et leurs places. */
export async function lireDouane(): Promise<EtatDouane> {
  return invoke<EtatDouane>("douane_etat");
}

/** Réserve une place ; `remplacer` : changer de séance sans perdre l'ancienne
 *  place si la nouvelle échoue. */
export async function reserverSeance(seanceId: string, remplacer = false): Promise<ReservationDouane> {
  return invoke<ReservationDouane>("douane_reserver", { seanceId, remplacer });
}

export async function annulerReservation(reservationId: string): Promise<{ ok: true }> {
  return invoke<{ ok: true }>("douane_annuler", { reservationId });
}

/** Peut-on lancer le jeu maintenant ? Même décision que launchGame(). Appelle
 *  l'API pour un candidat : à lire à l'ouverture de l'accueil, au retour de
 *  la fenêtre et toutes les minutes au plus. */
export async function lireAccesJeu(): Promise<AccesJeu> {
  return invoke<AccesJeu>("douane_acces");
}

/**
 * Messages d'un refus de validation de l'API, rangés par champ :
 * « history: Encore 38 caractères (150 minimum) ; firstName: … (HTTP 400). »
 * → { history: "Encore 38 caractères (150 minimum)", firstName: "…" }.
 * Objet vide si l'erreur n'est pas un refus par champ.
 */
export function erreursParChamp(err: unknown): Record<string, string> {
  const message = isIpcError(err) && typeof err.message === "string" ? err.message : "";
  const corps = message.replace(/\s*\(HTTP \d{3}\)\.?\s*$/, "");
  const out: Record<string, string> = {};
  for (const morceau of corps.split(" ; ")) {
    const m = /^([A-Za-z]+):\s*(.+)$/.exec(morceau.trim());
    if (m && !(m[1] in out)) out[m[1]] = m[2].trim();
  }
  return out;
}

// Fil avec le staff, retrait et demande de reprise : commandes existantes.
export {
  fetchWhitelistMessages as lireMessages,
  postWhitelistMessage as envoyerMessage,
  withdrawWhitelist as retirerCandidature,
  reclaimWhitelist as demanderReprise,
} from "./content";
export type { WhitelistMessage as MessageCandidature } from "./content";
