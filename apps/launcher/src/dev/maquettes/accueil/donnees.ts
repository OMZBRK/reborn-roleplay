// MAQUETTES (développement uniquement) : données fictives des écrans L10
// (accueil) et L11 (Mes personnages). Rien ici ne vient de l'API : les formes
// suivent docs/gameplay/CANDIDATURE_ET_DOUANE.md (§1.2 phases, §3.3 séances
// et fenêtre d'entrée, §4.5 GameCharacter, §5.2 compteur Discord).
//
// Même histoire que les maquettes de la candidature (L1-L9) : le joueur
// Kazuki, son premier personnage Kaito Arashi (ninja de Konohagakure, sans
// clan : la candidature n'en propose aucun, Q4), la séance de douane du
// dimanche 25 octobre réservée en L9 (convocation 21 h 30). Un personnage
// qui a déjà vécu en jeu peut porter un clan, rejoint lors d'une session de
// clan (Ren Hozuki), jamais à la création.

import { HISTOIRE, OBJECTIFS } from "../candidature/donnees";

/** Invitation officielle (DECISIONS.md). Repli constant si l'API ne répond pas (§5.2). */
export const DISCORD_INVITATION = "https://discord.gg/Nw7jpjJ6dg";

// ──────────────────────────────────────────────────────
//  Compte affiché dans la coque (rail, salutation)
// ──────────────────────────────────────────────────────

export type CompteFictif = {
  pseudo: string;
  /** Clé de ROLE_META (components/sidebar/role.ts). */
  role: "player" | "whitelisted";
  coins: number;
  notificationNonLue: boolean;
};

/** Phase DOUANE_RESERVEE : compte PLAYER, écrit accepté. */
export const COMPTE_CANDIDAT: CompteFictif = {
  pseudo: "Kazuki",
  role: "player",
  coins: 0,
  notificationNonLue: true,
};

/** Phase ACCEPTE : joueur whitelisté. */
export const COMPTE_JOUEUR: CompteFictif = {
  pseudo: "Kazuki",
  role: "whitelisted",
  coins: 1250,
  notificationNonLue: true,
};

// ──────────────────────────────────────────────────────
//  Séance de douane (§3.3)
// ──────────────────────────────────────────────────────

/** Minutes depuis minuit. */
export type Minutes = number;

export type SeanceDouane = {
  /** « samedi 24 octobre » */
  jour: string;
  /** « samedi » : libellé court du bouton grisé (« Ta séance : samedi 21 h »). */
  jourCourt: string;
  /** H : heure de convocation de la réservation (convocationAt). */
  convocation: Minutes;
  /** Avance permise : H − 15 min. */
  avanceMin: number;
  /** Retard maximal (réglable côté API) : H + 30 min. */
  retardMax: number;
  /** Place réservée, pour le rappel (« 5 places sur 16 » côté réservation). */
  places: { prises: number; capacite: number };
};

export const SEANCE: SeanceDouane = {
  jour: "dimanche 25 octobre",
  jourCourt: "dimanche",
  convocation: 21 * 60 + 30,
  avanceMin: 15,
  retardMax: 30,
  places: { prises: 11, capacite: 16 },
};

export const ouvertureDe = (s: SeanceDouane): Minutes => s.convocation - s.avanceMin;
export const fermetureDe = (s: SeanceDouane): Minutes => s.convocation + s.retardMax;

/**
 * États du bouton principal pour un compte en phase DOUANE_RESERVEE, avec
 * l'heure fictive « maintenant » de chacun (captures reproductibles : jamais
 * l'horloge réelle).
 */
export type EtatDouane = "avant" | "ouverte" | "en-jeu" | "manquee";

export const MAINTENANT: Record<EtatDouane, Minutes> = {
  avant: 18 * 60 + 13,
  ouverte: 21 * 60 + 22,
  "en-jeu": 21 * 60 + 34,
  manquee: 22 * 60 + 11,
};

// ──────────────────────────────────────────────────────
//  Compteur Discord (§5.2 : /menu/panel → discord {members, online})
// ──────────────────────────────────────────────────────

export type DonneesDiscord =
  | { etat: "chargement" }
  | { etat: "ok"; membres: number; enLigne: number }
  | { etat: "indisponible" };

export const DISCORD_OK: DonneesDiscord = { etat: "ok", membres: 1238, enLigne: 214 };

// ──────────────────────────────────────────────────────
//  Actualités (cartes réelles NewsCard, contenu fictif)
// ──────────────────────────────────────────────────────

export const ACTU_PATCH = {
  kicker: "Patch 0.4.192",
  title: "Menu R avec accord, masques et HUD n°4",
  excerpt:
    "Porter ou fouiller quelqu'un demande désormais son accord, un masque rend inconnu, volume vocal par joueur et nouveau HUD.",
  link: "Voir le patch",
};

export const ACTU_CANDIDATURE_DOUANE = {
  kicker: "Candidature",
  title: "Écrit accepté · douane ce soir",
  excerpt:
    "Présente-toi entre 21 h 15 et 22 h, micro branché. Un examinateur t'appellera depuis le hall.",
  link: "Voir ma séance",
};

// ──────────────────────────────────────────────────────
//  Mes personnages (§4.5 GameCharacter, §4.6 écran)
// ──────────────────────────────────────────────────────

export type VillageId = "konoha" | "suna" | "kiri" | "kumo" | "iwa" | "ame";

export const NOM_VILLAGE: Record<VillageId, string> = {
  konoha: "Konohagakure",
  suna: "Sunagakure",
  kiri: "Kirigakure",
  kumo: "Kumogakure",
  iwa: "Iwagakure",
  ame: "Amegakure",
};

/** Dossier d'entrée du premier personnage : histoire, objectifs et date de
 *  la douane (« Caractère et apparence » n'existe plus en v2). */
export type DossierEntree = {
  /** Date de la douane réussie. */
  douaneLe: string;
  histoire: string;
  objectifs: string;
};

export type Personnage = {
  id: string;
  /** N° de personnage : propriétaire et panel staff seulement (GAMEPLAN §1.4). */
  numero: string;
  prenom: string;
  /** Clé canonique ASCII du mod (« nara », « hozuki ») ; null = « Sans clan ». */
  clan: string | null;
  /** Date de la session de clan où il l'a rejoint (Q4 : jamais à la création). */
  clanRejointLe?: string;
  /** Nom affiché du clan, ou nom de famille libre si `clan` est null. */
  nomFamille: string;
  village: VillageId;
  /** Q3 : type choisi à la candidature (premier personnage) ou à la création. */
  type: "Ninja" | "Civil";
  grade: string;
  age: number;
  sexe: "Homme" | "Femme";
  etat: "vivant" | "mort";
  mortLe?: string;
  creeLe: string;
  dernierePartie: string;
  tempsDeJeu: string;
  /** Premier personnage : repris de la candidature (§4.4). */
  issuDeCandidature: boolean;
  dossier?: DossierEntree;
};

export const EMPLACEMENTS = { base: 2, debloques: 1 };

/** Le temps de jeu n'est compté que depuis la mise en service de la synchro (§4.5). */
export const TEMPS_COMPTE_DEPUIS = "26/10/2026";

/** Texte du dossier sans la syntaxe Markdown (titres, gras, citations, puces). */
function texteBrut(md: string): string {
  return md
    .split("\n")
    .filter((l) => !l.startsWith("## "))
    .map((l) => l.replace(/^> /, "").replace(/^- /, "· ").replace(/\*\*|\*|~~/g, ""))
    .join("\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export const PERSONNAGES: Personnage[] = [
  {
    id: "c-kaito",
    numero: "00042",
    prenom: "Kaito",
    clan: null,
    nomFamille: "Arashi",
    village: "konoha",
    type: "Ninja",
    grade: "Genin",
    age: 16,
    sexe: "Homme",
    etat: "vivant",
    creeLe: "25/10/2026",
    dernierePartie: "hier, 23 h 14",
    tempsDeJeu: "34 h 12",
    issuDeCandidature: true,
    dossier: {
      douaneLe: "25/10/2026",
      histoire: texteBrut(HISTOIRE),
      objectifs: OBJECTIFS,
    },
  },
  {
    id: "c-ren",
    numero: "00117",
    prenom: "Ren",
    clan: "hozuki",
    clanRejointLe: "05/12/2026",
    nomFamille: "Hozuki",
    village: "kiri",
    type: "Ninja",
    grade: "Genin confirmé",
    age: 19,
    sexe: "Homme",
    etat: "mort",
    mortLe: "21/12/2026",
    creeLe: "14/11/2026",
    dernierePartie: "21/12/2026",
    tempsDeJeu: "12 h 40",
    issuDeCandidature: false,
  },
];
