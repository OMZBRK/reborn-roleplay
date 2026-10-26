import type { CharacterSex, CharacterType } from "./candidature";
import { invoke } from "./tauri";

// « Mes personnages » (docs/gameplay/CANDIDATURE_ET_DOUANE.md §4.6, lot J) :
// wrapper typé de la commande Tauri `me_characters`
// (src-tauri/src/content/personnages.rs → GET /v1/me/characters). Le front ne
// parle jamais HTTP.
//
// L'API ne renvoie que les personnages du compte, sur le serveur de prod
// (REBORN_CHARACTERS_SERVER_IDS), supprimés compris. Le N° n'existe que dans
// cette vue du propriétaire : ne jamais le réafficher dans une vue sociale.
// Le Rust relit la réponse avec tolérance (code inconnu = valeur par défaut,
// carte illisible écartée, N° reconstruit depuis playerId) : les formes
// ci-dessous sont garanties. Dates en ISO 8601 UTC, à afficher à l'heure
// locale.
//
// Erreurs : objets { kind, message } (contrat IPC), à afficher avec
// formatIpcError (lib/errors.ts).

/** En vie, mort RP, supprimé. */
export type EtatPersonnage = "ALIVE" | "DEAD" | "DELETED";

/** Dossier d'entrée du premier personnage, repris de la candidature. */
export type DossierEntree = {
  /** Markdown léger du formulaire (titres, gras, listes, citations). */
  history: string;
  objectives: string;
  /** Date de l'oral (douane) ; null s'il n'est pas daté. */
  oralDecidedAt: string | null;
};

export type PersonnageCompte = {
  /** Identifiant côté jeu (ShinobiCharacter.id). */
  characterId: string;
  /** N° brut ; null tant que le serveur ne l'a pas attribué. */
  playerId: number | null;
  /** N° affiché : « 00042 ». */
  playerNumber: string | null;
  firstName: string;
  /** Nom de famille libre (personnage sans clan). */
  lastName: string | null;
  /** Clan acquis en jeu, lors d'une session de clan ; null = sans clan. */
  clan: string | null;
  /** Valeur du mod : « Konohagakure »… */
  village: string | null;
  type: CharacterType;
  /** Code du grade (GENIN…) ; libellé : libelleGrade(). */
  rank: string | null;
  sex: CharacterSex | null;
  age: number | null;
  state: EtatPersonnage;
  playMinutes: number;
  /** Début du décompte du temps de jeu (première synchronisation). */
  playMinutesSince: string | null;
  /** Création EN JEU ; null si le serveur ne la connaît pas. */
  createdAt: string | null;
  lastSeenAt: string | null;
  /** Premier personnage, repris de la candidature. */
  fromApplication: boolean;
  /** Présent seulement si la candidature liée est bien celle du compte. */
  dossier: DossierEntree | null;
};

export type MesPersonnages = { characters: PersonnageCompte[] };

/** Personnages du compte connecté, dans l'ordre de l'API (création en jeu, puis N°). */
export async function fetchMesPersonnages(): Promise<MesPersonnages> {
  const r = await invoke<MesPersonnages | undefined>("me_characters");
  // Vite dans un simple navigateur, sans le mode démo : invoke ne renvoie rien.
  return { characters: r?.characters ?? [] };
}

// ──────────────────────────────────────────────────────
// Libellés
// ──────────────────────────────────────────────────────

/** Libellés de l'enum Rank de ShinobiCore (character/Rank.java). */
const GRADES: Record<string, string> = {
  ACADEMICIEN: "Académicien",
  GENIN: "Genin",
  GENIN_CONFIRME: "Genin confirmé",
  CHUNIN: "Chūnin",
  KONIN: "Konin",
  TOKUBETSU_JONIN: "Tokubetsu Jōnin",
  JONIN: "Jōnin",
  COMMANDANT_JONIN: "Commandant Jōnin",
  KAGE: "Kage",
};

/** « GENIN_CONFIRME » → « Genin confirmé » ; code inconnu mis en forme (« NOUVEAU_GRADE » → « Nouveau grade »). */
export function libelleGrade(code: string | null): string | null {
  if (!code) return null;
  const connu = GRADES[code.toUpperCase()];
  if (connu) return connu;
  const mots = code.toLowerCase().replace(/_/g, " ").trim();
  return mots ? mots.charAt(0).toUpperCase() + mots.slice(1) : null;
}

export const LIBELLE_TYPE: Record<CharacterType, string> = { NINJA: "Ninja", CIVIL: "Civil" };

export const LIBELLE_SEXE: Record<CharacterSex, string> = { HOMME: "Homme", FEMME: "Femme" };
