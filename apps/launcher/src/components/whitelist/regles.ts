// Candidature v2 : onglets, formulaire et règles de validation
// (docs/gameplay/CANDIDATURE_ET_DOUANE.md §2.3, §2.4 et §7 « Tranché le 24/10 »,
// « Complément du 24/10 », « Complément du 25/10 »).
//
// Les règles sont celles de l'API (apps/api/src/whitelist/whitelist.rules.ts),
// mêmes messages : le launcher prévient, l'API reste l'autorité. Les bornes
// viennent de GET /whitelist/options ; OPTIONS_DEFAUT (mêmes valeurs que
// l'API) ne sert que tant que les options ne sont pas arrivées.
//
// Retirés le 24/10 (l'oral en jeu les remplace) : « Ton expérience du
// roleplay », « Pourquoi Reborn ? », « Caractère et apparence ». Aucun clan
// ne se choisit (Q4) : premier personnage sans clan, nom de famille libre.

import type {
  Bornes,
  CandidatureEnvoi,
  CandidatureOptions,
  CandidatureRenvoi,
  CandidatureV2,
  CharacterSex,
  CharacterType,
  DiscoverySource,
  DonneesBrouillon,
  NarutoKnowledge,
  RpYears,
} from "../../lib/candidature";

// ──────────────────────────────────────────────────────
//  Onglets
// ──────────────────────────────────────────────────────

export type OngletId = "vous" | "personnage" | "village" | "histoire" | "voix" | "envoi";
export type Partie = "HRP" | "RP";

export const ONGLETS: { id: OngletId; numero: number; libelle: string; partie: Partie | null }[] = [
  { id: "vous", numero: 1, libelle: "Vous", partie: "HRP" },
  { id: "personnage", numero: 2, libelle: "Personnage", partie: "RP" },
  { id: "village", numero: 3, libelle: "Village", partie: "RP" },
  { id: "histoire", numero: 4, libelle: "Histoire", partie: "RP" },
  { id: "voix", numero: 5, libelle: "Voix", partie: "RP" },
  { id: "envoi", numero: 6, libelle: "Envoi", partie: null },
];

export const ONGLETS_FORMULAIRE = ONGLETS.filter((o) => o.partie !== null);

export function partieDe(o: OngletId): Partie | null {
  return ONGLETS.find((x) => x.id === o)?.partie ?? null;
}

// ──────────────────────────────────────────────────────
//  Options de repli (mêmes valeurs que l'API)
// ──────────────────────────────────────────────────────

const CLANS_DU_MOD: Array<[string, string]> = [
  ["Uchiha", "Konohagakure"],
  ["Senju", "Konohagakure"],
  ["Hyuga", "Konohagakure"],
  ["Nara", "Konohagakure"],
  ["Sarutobi", "Konohagakure"],
  ["Aburame", "Konohagakure"],
  ["Akimichi", "Konohagakure"],
  ["Yamanaka", "Konohagakure"],
  ["Hatake", "Konohagakure"],
  ["Uzumaki", "Konohagakure"],
  ["Kurama", "Konohagakure"],
  ["Sabaku", "Sunagakure"],
  ["Hoki", "Sunagakure"],
  ["Hozuki", "Kirigakure"],
  ["Hoshigaki", "Kirigakure"],
  ["Kaguya", "Kirigakure"],
  ["Yuki", "Kirigakure"],
  ["Terumi", "Kirigakure"],
  ["Yotsuki", "Kumogakure"],
  ["Kamizuru", "Iwagakure"],
  ["Onoki", "Iwagakure"],
];

export const OPTIONS_DEFAUT: CandidatureOptions = {
  formVersion: 2,
  villages: [
    { valeur: "Konohagakure", court: "Konoha", pays: "Pays du Feu" },
    { valeur: "Sunagakure", court: "Suna", pays: "Pays du Vent" },
    { valeur: "Kirigakure", court: "Kiri", pays: "Pays de l'Eau" },
    { valeur: "Kumogakure", court: "Kumo", pays: "Pays de la Foudre" },
    { valeur: "Iwagakure", court: "Iwa", pays: "Pays de la Terre" },
    { valeur: "Amegakure", court: "Ame", pays: "Pays de la Pluie" },
  ],
  clans: CLANS_DU_MOD.map(([nom, village]) => ({ nom, village, surSession: true })),
  sansClan: "Autre",
  bornes: {
    playerAge: { min: 15, max: 99 },
    availability: { min: 20, max: 2000 },
    firstName: { min: 3, max: 20 },
    lastName: { min: 3, max: 20 },
    characterAge: { min: 10, max: 60 },
    history: { min: 150, max: 12000 },
    objectives: { min: 400, max: 8000 },
    support: { min: 0, max: 500 },
    voixSec: { min: 15, max: 60 },
  },
  listes: {
    rpYears: [
      { valeur: "JAMAIS", libelle: "Jamais" },
      { valeur: "MOINS_D_UN_AN", libelle: "Moins d'un an" },
      { valeur: "UN_A_DEUX_ANS", libelle: "1 à 2 ans" },
      { valeur: "TROIS_A_CINQ_ANS", libelle: "3 à 5 ans" },
      { valeur: "SIX_ANS_ET_PLUS", libelle: "6 ans et plus" },
    ],
    narutoKnowledge: [
      { valeur: "DEBUTANT", libelle: "Débutant" },
      { valeur: "QUELQUES_ARCS", libelle: "Quelques arcs" },
      { valeur: "BONNE_CONNAISSANCE", libelle: "Bonne connaissance" },
      { valeur: "INCOLLABLE", libelle: "Incollable, Boruto compris" },
    ],
    discoverySource: [
      { valeur: "DISCORD", libelle: "Discord" },
      { valeur: "TIKTOK", libelle: "TikTok" },
      { valeur: "YOUTUBE", libelle: "YouTube" },
      { valeur: "AMI", libelle: "Un ami" },
      { valeur: "AUTRE_SERVEUR", libelle: "Un autre serveur" },
      { valeur: "RECHERCHE", libelle: "Une recherche" },
      { valeur: "AUTRE", libelle: "Autre" },
    ],
    characterType: [
      { valeur: "NINJA", libelle: "Ninja" },
      { valeur: "CIVIL", libelle: "Civil" },
    ],
    characterSex: [
      { valeur: "HOMME", libelle: "Homme" },
      { valeur: "FEMME", libelle: "Femme" },
    ],
  },
  voix: { types: ["audio/webm", "audio/ogg"], octetsMax: 1536 * 1024, dureeMinSec: 15, dureeMaxSec: 60 },
  delaiNouvelleCandidatureHeures: 24,
};

/** Libellé français d'un code de liste fermée (le code lui-même en repli). */
export function libelleDe<V extends string>(liste: { valeur: V; libelle: string }[], v: V | null): string | null {
  if (v === null) return null;
  return liste.find((c) => c.valeur === v)?.libelle ?? v;
}

// ──────────────────────────────────────────────────────
//  Le formulaire (état de l'écran, brouillon)
// ──────────────────────────────────────────────────────

/** Formulaire tel que l'écran le tient. Les âges restent du texte pendant la
 *  frappe (chiffres seulement) ; ils partent en nombres. */
export type Formulaire = {
  // 1 — Vous (partie HRP)
  playerAge: string;
  rpYears: RpYears | null;
  narutoKnowledge: NarutoKnowledge | null;
  discoverySource: DiscoverySource | null;
  availability: string;
  // 2 — Personnage (partie RP)
  firstName: string;
  /** Nom de famille libre, jamais un nom de clan (Q4). */
  lastName: string;
  characterType: CharacterType | null;
  characterSex: CharacterSex | null;
  characterAge: string;
  // 3 — Village (partie RP) : valeur exacte du mod (« Konohagakure »)
  village: string | null;
  // 4 — Histoire (partie RP)
  history: string;
  objectives: string;
  support: string;
  // 5 — Voix : le micro a été testé sur ce compte (l'enregistrement lui-même
  // est déposé sur le serveur, à part)
  micTeste: boolean;
};

export type ChampFormulaire = keyof Formulaire;

export const FORMULAIRE_VIDE: Formulaire = {
  playerAge: "",
  rpYears: null,
  narutoKnowledge: null,
  discoverySource: null,
  availability: "",
  firstName: "",
  lastName: "",
  characterType: null,
  characterSex: null,
  characterAge: "",
  village: null,
  history: "",
  objectives: "",
  support: "",
  micTeste: false,
};

const entierOuNull = (s: string): number | null => (/^\d+$/.test(s.trim()) ? Number(s.trim()) : null);

/** Formulaire → données du brouillon serveur (formes de l'API : nombres, codes). */
export function versBrouillon(f: Formulaire): DonneesBrouillon {
  return {
    playerAge: entierOuNull(f.playerAge),
    rpYears: f.rpYears,
    narutoKnowledge: f.narutoKnowledge,
    discoverySource: f.discoverySource,
    availability: f.availability,
    firstName: f.firstName,
    lastName: f.lastName,
    characterType: f.characterType,
    characterSex: f.characterSex,
    characterAge: entierOuNull(f.characterAge),
    village: f.village,
    history: f.history,
    objectives: f.objectives,
    support: f.support,
    micTeste: f.micTeste,
  };
}

function texteDe(v: unknown): string {
  return typeof v === "string" ? v : "";
}

function ageDe(v: unknown): string {
  if (typeof v === "number" && Number.isInteger(v) && v >= 0 && v < 1000) return String(v);
  if (typeof v === "string" && /^\d{1,3}$/.test(v.trim())) return v.trim();
  return "";
}

function codeDe<V extends string>(v: unknown, liste: { valeur: V }[]): V | null {
  return typeof v === "string" && liste.some((c) => c.valeur === v) ? (v as V) : null;
}

/** Données d'un brouillon (serveur ou PC) → formulaire. Tolérant : une valeur
 *  illisible ou hors liste retombe à vide, sans rien casser. */
export function depuisBrouillon(data: DonneesBrouillon | null | undefined, options: CandidatureOptions): Formulaire {
  const d = data ?? {};
  const l = options.listes;
  const village = typeof d.village === "string" ? d.village : null;
  return {
    playerAge: ageDe(d.playerAge),
    rpYears: codeDe(d.rpYears, l.rpYears),
    narutoKnowledge: codeDe(d.narutoKnowledge, l.narutoKnowledge),
    discoverySource: codeDe(d.discoverySource, l.discoverySource),
    availability: texteDe(d.availability),
    firstName: texteDe(d.firstName),
    lastName: texteDe(d.lastName),
    characterType: codeDe(d.characterType, l.characterType),
    characterSex: codeDe(d.characterSex, l.characterSex),
    characterAge: ageDe(d.characterAge),
    village: village && options.villages.some((v) => v.valeur === village) ? village : null,
    history: texteDe(d.history),
    objectives: texteDe(d.objectives),
    support: texteDe(d.support),
    micTeste: d.micTeste === true,
  };
}

/** Candidature envoyée → formulaire (relecture, « À revoir », nouvelle
 *  candidature après un refus, qui repart du dossier précédent). */
export function depuisCandidature(app: CandidatureV2, options: CandidatureOptions): Formulaire {
  return depuisBrouillon(
    {
      playerAge: app.playerAge,
      rpYears: app.rpYears,
      narutoKnowledge: app.narutoKnowledge,
      discoverySource: app.discoverySource,
      availability: app.availability,
      firstName: app.firstName,
      lastName: app.lastName,
      characterType: app.characterType,
      characterSex: app.characterSex,
      characterAge: app.characterAge,
      village: app.village,
      history: app.history,
      objectives: app.objectives,
      support: app.support ?? "",
      micTeste: app.voix !== null,
    },
    options,
  );
}

/** Mêmes données ? (comparaison des brouillons du PC et du serveur) */
export function memesDonnees(a: Formulaire, b: Formulaire): boolean {
  return JSON.stringify(versBrouillon(a)) === JSON.stringify(versBrouillon(b));
}

export function formulaireVide(f: Formulaire): boolean {
  return memesDonnees(f, FORMULAIRE_VIDE);
}

export type Cases = { rulesAccepted: boolean; voiceConsent: boolean };

/** Champs HRP et RP de l'envoi (noms du corps de POST /whitelist). */
function champsHrp(f: Formulaire) {
  return {
    playerAge: entierOuNull(f.playerAge) as number,
    rpYears: f.rpYears as RpYears,
    narutoKnowledge: f.narutoKnowledge as NarutoKnowledge,
    discoverySource: f.discoverySource,
    availability: f.availability,
  };
}

function champsRp(f: Formulaire) {
  return {
    firstName: f.firstName.trim(),
    lastName: f.lastName.trim(),
    village: f.village ?? "",
    characterType: f.characterType as CharacterType,
    characterSex: f.characterSex as CharacterSex,
    characterAge: entierOuNull(f.characterAge) as number,
    support: f.support.trim() === "" ? null : f.support.trim(),
    history: f.history,
    objectives: f.objectives,
  };
}

/** Corps de la candidature complète (première, ou nouvelle après un refus). */
export function versEnvoi(f: Formulaire, cases: Cases): CandidatureEnvoi {
  return { ...champsHrp(f), ...champsRp(f), ...cases };
}

/** Corps du renvoi après « À revoir » : seules les parties rouvertes. */
export function versRenvoi(f: Formulaire, parties: Partie[], cases: Cases): CandidatureRenvoi {
  return {
    ...(parties.includes("HRP") ? champsHrp(f) : {}),
    ...(parties.includes("RP") ? champsRp(f) : {}),
    ...cases,
  };
}

// ──────────────────────────────────────────────────────
//  Mesures et messages (mêmes textes que l'API, §2.4)
// ──────────────────────────────────────────────────────

const NF = new Intl.NumberFormat("fr-FR");
export const nombre = (n: number) => NF.format(n);

/** Caractères comptés comme l'API (String.length, UTF-16 : un émoji compte pour 2). */
export const longueur = (s: string) => s.length;

/**
 * Texte du joueur prêt pour le rendu Markdown (aperçu, relecture) : aucune
 * balise HTML ni lien écrit (`[texte](adresse)`) ne passe (le composant
 * Markdown ne nettoie pas son HTML, il est fait pour les textes de l'API).
 * Gras, italique, barré, titres, listes et citations restent. Les adresses
 * nues (https://…, www.…) deviennent encore des liens automatiques : à
 * afficher avec MarkdownSansLien (ui.tsx), qui bloque leur clic.
 */
export function markdownSur(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/\[/g, "&#91;").replace(/\]/g, "&#93;");
}

/** Mots, à titre indicatif (ne bloque rien). */
export function mots(s: string): number {
  const t = s.replace(/[#>*_~`-]/g, " ").trim();
  return t ? t.split(/\s+/).length : 0;
}

export type EtatTexte = "vide" | "court" | "ok" | "proche" | "trop";

export function etatTexte(valeur: string, b: Bornes): EtatTexte {
  const n = longueur(valeur);
  if (n === 0) return "vide";
  if (n > b.max) return "trop";
  if (n < b.min) return "court";
  if (n >= b.max * 0.9) return "proche";
  return "ok";
}

const s_ = (n: number) => (n > 1 ? "s" : "");

/** Texte borné (whitelist.rules.ts › texte). null = rien à dire. */
export function messageTexte(valeur: string, b: Bornes, obligatoire = true): string | null {
  const n = longueur(valeur);
  if (n === 0) return obligatoire ? `Encore ${nombre(b.min)} caractères (${nombre(b.min)} minimum).` : null;
  if (n > b.max) {
    const trop = n - b.max;
    return `${nombre(trop)} caractère${s_(trop)} de trop (${nombre(b.max)} maximum).`;
  }
  if (n < b.min) {
    const manque = b.min - n;
    return `Encore ${nombre(manque)} caractère${s_(manque)} (${nombre(b.min)} minimum).`;
  }
  return null;
}

/** Âge du joueur : nombre entier, de WHITELIST_AGE_MIN (15) à 99. */
export function messageAge(brut: string, b: Bornes): string | null {
  const t = brut.trim();
  if (t === "") return "Indique ton âge.";
  if (!/^\d+$/.test(t)) return "Un nombre entier, sans lettres.";
  const n = Number(t);
  if (n < b.min) return `Reborn accueille les joueurs à partir de ${b.min} ans.`;
  if (n > b.max) return `Entre ${b.min} et ${b.max}.`;
  return null;
}

export function messageAgePerso(brut: string, b: Bornes): string | null {
  const t = brut.trim();
  if (t === "") return "Indique l'âge de ton personnage.";
  if (!/^\d+$/.test(t)) return "Un nombre entier, sans lettres.";
  const n = Number(t);
  if (n < b.min || n > b.max) return `Entre ${b.min} et ${b.max} ans, les bornes du jeu.`;
  return null;
}

/** Prénom et nom de famille : lettres, espaces, apostrophes, tirets ; une lettre en premier (règle du mod). */
const MOTIF_NOM = /^\p{L}[\p{L} '’-]*$/u;

function nomPropre(v: string, b: Bornes, vide: string): string | null {
  const t = v.trim();
  if (t === "") return vide;
  if (t.length < b.min || t.length > b.max || !MOTIF_NOM.test(t)) {
    return `Lettres, espaces, apostrophes et tirets, de ${b.min} à ${b.max} caractères.`;
  }
  return null;
}

export function messagePrenom(v: string, b: Bornes): string | null {
  return nomPropre(v, b, "Indique le prénom de ton personnage.");
}

/** Comparaison sans casse ni accents : « Hyûga » vaut « Hyuga » (comme l'API). */
function cleNom(s: string): string {
  return s.normalize("NFD").replace(/\p{M}/gu, "").trim().toLowerCase();
}

/** Nom de famille libre : jamais le nom d'un clan du mod, ni le clan réservé « Autre ». */
export function messageNomFamille(v: string, b: Bornes, options: CandidatureOptions): string | null {
  const t = v.trim();
  if (t !== "") {
    const k = cleNom(t);
    if (k === cleNom(options.sansClan)) return `« ${t} » est réservé par le jeu : choisis un autre nom.`;
    if (options.clans.some((c) => cleNom(c.nom) === k)) {
      return `« ${t} » est un nom de clan. Les clans se rejoignent en jeu, lors des sessions de clan : choisis un autre nom.`;
    }
  }
  return nomPropre(v, b, "Indique le nom de famille de ton personnage.");
}

export function messageSupport(v: string, b: Bornes): string | null {
  if (v.length > b.max) return `${nombre(v.length - b.max)} caractères de trop (${nombre(b.max)} maximum).`;
  return /^\s*$|^\s*https:\/\/\S+\s*$/.test(v) ? null : "Un lien complet, qui commence par https://";
}

/** Tranche affichée à côté de l'âge du personnage (bornes du mod). */
export function trancheAge(brut: string, type: CharacterType | null): string | null {
  const age = Number(brut);
  if (brut.trim() === "" || !Number.isInteger(age) || age < 10 || age > 60) return null;
  if (type === "CIVIL") return age <= 12 ? "Enfant" : age <= 16 ? "Adolescent" : "Adulte";
  if (age <= 12) return "Académie";
  if (age <= 16) return "Jeune genin";
  return "Adulte";
}

/** Nom affiché en jeu : prénom + nom de famille libre. */
export function nomComplet(f: Pick<Formulaire, "firstName" | "lastName">): string {
  return [f.firstName.trim(), f.lastName.trim()].filter(Boolean).join(" ");
}

// ──────────────────────────────────────────────────────
//  Manques (coches des onglets, « Il reste à compléter »)
// ──────────────────────────────────────────────────────

export type Manque = { onglet: OngletId; champ: string; libelle: string; dur: boolean };

export type ContexteManques = {
  options: CandidatureOptions;
  /** Un enregistrement valable est disponible (brouillon, ou candidature à revoir). */
  voixOk: boolean;
  /** Onglets modifiables (« À revoir » : seules les parties rouvertes). */
  editable: (o: OngletId) => boolean;
};

/**
 * Ce qui manque, par onglet modifiable. `dur` : erreur bloquante visible tout
 * de suite (texte trop long, âge saisi hors bornes, nom de clan) ; l'onglet
 * porte alors une pastille rouge.
 */
export function manques(f: Formulaire, c: ContexteManques): Manque[] {
  const b = c.options.bornes;
  const out: Manque[] = [];
  const add = (onglet: OngletId, champ: string, libelle: string, dur = false) => {
    if (c.editable(onglet)) out.push({ onglet, champ, libelle, dur });
  };
  const texte = (onglet: OngletId, champ: "availability" | "history" | "objectives", libelle: string, bornes: Bornes) => {
    if (messageTexte(f[champ], bornes) !== null) add(onglet, champ, libelle, longueur(f[champ]) > bornes.max);
  };

  if (messageAge(f.playerAge, b.playerAge)) add("vous", "playerAge", "Ton âge", f.playerAge.trim() !== "");
  if (!f.rpYears) add("vous", "rpYears", "Années de roleplay");
  if (!f.narutoKnowledge) add("vous", "narutoKnowledge", "Connaissance de l'univers");
  texte("vous", "availability", "Tes disponibilités", b.availability);

  if (messagePrenom(f.firstName, b.firstName)) add("personnage", "firstName", "Prénom");
  if (messageNomFamille(f.lastName, b.lastName, c.options)) {
    add("personnage", "lastName", "Nom de famille", f.lastName.trim() !== "");
  }
  if (!f.characterType) add("personnage", "characterType", "Type de personnage");
  if (!f.characterSex) add("personnage", "characterSex", "Sexe");
  if (messageAgePerso(f.characterAge, b.characterAge)) {
    add("personnage", "characterAge", "Âge du personnage", f.characterAge.trim() !== "");
  }

  if (!f.village) add("village", "village", "Village");

  texte("histoire", "history", "Histoire du personnage", b.history);
  texte("histoire", "objectives", "Objectifs roleplay", b.objectives);
  if (messageSupport(f.support, b.support)) add("histoire", "support", "Support visuel", true);

  if (!c.voixOk) {
    if (!f.micTeste) add("voix", "micTeste", "Test du micro");
    add("voix", "voix", "Enregistrement vocal");
  }
  return out;
}

// ──────────────────────────────────────────────────────
//  Erreurs de l'API, rangées par champ
// ──────────────────────────────────────────────────────

/** Onglet et libellé d'un champ cité par l'API (« history: … »). */
export const CHAMPS_API: Record<string, { onglet: OngletId; libelle: string }> = {
  playerAge: { onglet: "vous", libelle: "Ton âge" },
  rpYears: { onglet: "vous", libelle: "Années de roleplay" },
  narutoKnowledge: { onglet: "vous", libelle: "Univers Naruto" },
  discoverySource: { onglet: "vous", libelle: "Découverte" },
  availability: { onglet: "vous", libelle: "Disponibilités" },
  firstName: { onglet: "personnage", libelle: "Prénom" },
  lastName: { onglet: "personnage", libelle: "Nom de famille" },
  characterType: { onglet: "personnage", libelle: "Type" },
  characterSex: { onglet: "personnage", libelle: "Sexe" },
  characterAge: { onglet: "personnage", libelle: "Âge du personnage" },
  village: { onglet: "village", libelle: "Village" },
  history: { onglet: "histoire", libelle: "Histoire" },
  objectives: { onglet: "histoire", libelle: "Objectifs" },
  support: { onglet: "histoire", libelle: "Support visuel" },
  voix: { onglet: "voix", libelle: "Enregistrement" },
  rulesAccepted: { onglet: "envoi", libelle: "Règlement" },
  voiceConsent: { onglet: "envoi", libelle: "Consentement voix" },
};
