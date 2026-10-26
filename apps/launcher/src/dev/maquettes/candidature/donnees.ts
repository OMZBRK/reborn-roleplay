// MAQUETTES — développement uniquement (jamais importé par l'application).
//
// Données FICTIVES et règles de la candidature v2, d'après
// docs/gameplay/CANDIDATURE_ET_DOUANE.md (§2.3 champs, §2.4 messages, §3.3
// séances). §7 : les décisions de l'utilisateur du 24/10 priment sur les
// recommandations : Q1 15 ans, Q2 voix obligatoire 15-60 s, Q3 type Ninja /
// Civil dès la beta, Q4 tous les clans passent par une session de clan (la
// candidature ne propose aucun clan : premier personnage sans clan, nom de
// famille libre, on ne choisit que le village), Q10 histoire 150-12 000, Q11
// aucun délai promis, Q13 à la création en jeu : prénom pré-rempli
// modifiable, village et âge verrouillés, sexe libre.
//
// Personnage d'exemple, le même sur tous les écrans : Kaito Arashi, ninja de
// Konohagakure, sans clan.
//
// Rien ici ne parle à l'API : tout est figé pour que chaque écran se
// photographie à l'identique.

import villageKonoha from "./assets/village_konoha.png";
import villageSuna from "./assets/village_suna.png";
import villageKiri from "./assets/village_kiri.png";
import villageKumo from "./assets/village_kumo.png";
import villageIwa from "./assets/village_iwa.png";
import villageAme from "./assets/village_ame.png";
import clanUchiha from "./assets/clan_uchiha.png";
import clanSenju from "./assets/clan_senju.png";
import clanHyuga from "./assets/clan_hyuga.png";
import clanNara from "./assets/clan_nara.png";
import clanSarutobi from "./assets/clan_sarutobi.png";
import clanAburame from "./assets/clan_aburame.png";
import clanAkimichi from "./assets/clan_akimichi.png";
import clanYamanaka from "./assets/clan_yamanaka.png";
import clanHatake from "./assets/clan_hatake.png";
import clanUzumaki from "./assets/clan_uzumaki.png";
import clanKurama from "./assets/clan_kurama.png";
import clanSabaku from "./assets/clan_sabaku.png";
import clanHoki from "./assets/clan_hoki.png";
import clanHozuki from "./assets/clan_hozuki.png";
import clanHoshigaki from "./assets/clan_hoshigaki.png";
import clanKaguya from "./assets/clan_kaguya.png";
import clanYuki from "./assets/clan_yuki.png";
import clanTerumi from "./assets/clan_terumi.png";
import clanYotsuki from "./assets/clan_yotsuki.png";
import clanKamizuru from "./assets/clan_kamizuru.png";
import clanOnoki from "./assets/clan_onoki.png";
import clanAutre from "./assets/clan_autre.png";

// ──────────────────────────────────────────────────────
//  Onglets
// ──────────────────────────────────────────────────────

export type OngletId = "vous" | "personnage" | "village" | "histoire" | "voix" | "envoi";

export const ONGLETS: { id: OngletId; numero: number; libelle: string; partie: "HRP" | "RP" | null }[] = [
  { id: "vous", numero: 1, libelle: "Vous", partie: "HRP" },
  { id: "personnage", numero: 2, libelle: "Personnage", partie: "RP" },
  { id: "village", numero: 3, libelle: "Village", partie: "RP" },
  { id: "histoire", numero: 4, libelle: "Histoire", partie: "RP" },
  { id: "voix", numero: 5, libelle: "Voix", partie: "RP" },
  { id: "envoi", numero: 6, libelle: "Envoi", partie: null },
];

// ──────────────────────────────────────────────────────
//  Bornes (l'API reste l'autorité ; mêmes valeurs des deux côtés)
// ──────────────────────────────────────────────────────

export type Bornes = { min: number; max: number };

export const BORNES = {
  playerAge: { min: 15, max: 99 },
  availability: { min: 20, max: 2000 },
  firstName: { min: 3, max: 20 },
  characterAge: { min: 10, max: 60 },
  objectives: { min: 400, max: 8000 },
  history: { min: 150, max: 12000 },
  support: { min: 0, max: 500 },
  nomFamille: { min: 3, max: 20 },
  voix: { min: 15, max: 60 },
} satisfies Record<string, Bornes>;

// ──────────────────────────────────────────────────────
//  Listes fermées
// ──────────────────────────────────────────────────────

export const RP_YEARS = ["Jamais", "Moins d'un an", "1 à 2 ans", "3 à 5 ans", "6 ans et plus"] as const;
export type RpYears = (typeof RP_YEARS)[number];

export const CONNAISSANCES = [
  "Débutant",
  "Quelques arcs",
  "Bonne connaissance",
  "Incollable, Boruto compris",
] as const;
export type Connaissance = (typeof CONNAISSANCES)[number];

export const DECOUVERTE = [
  "Pas de réponse",
  "Discord",
  "TikTok",
  "YouTube",
  "Un ami",
  "Un autre serveur",
  "Une recherche",
  "Autre",
] as const;

export const SEXES = ["Homme", "Femme"] as const;
export type Sexe = (typeof SEXES)[number];

/** Q3 (décision du 24/10) : type de personnage dès la beta, comme Ketto. */
export const TYPES = ["Ninja", "Civil"] as const;
export type TypePerso = (typeof TYPES)[number];

/** Tranche affichée à côté de l'âge du personnage (bornes du mod). */
export function trancheAge(age: number, type: TypePerso | null = "Ninja"): string | null {
  if (!Number.isFinite(age) || age < 10 || age > 60) return null;
  if (type === "Civil") return age <= 12 ? "Enfant" : age <= 16 ? "Adolescent" : "Adulte";
  if (age <= 12) return "Académie";
  if (age <= 16) return "Jeune genin";
  return "Adulte";
}

export type VillageId = "konoha" | "suna" | "kiri" | "kumo" | "iwa" | "ame";

export type Village = {
  id: VillageId;
  /** Valeur exacte envoyée (CharacterCreateScreen.VILLAGES). */
  nom: string;
  court: string;
  pays: string;
  emblem: string;
  /** Couleur du mod (V_COLOR), en filet discret. */
  teinte: string;
  description: string;
};

// Déserteur exclu, comme en jeu (§2.3). Descriptions réécrites, courtes.
export const VILLAGES: Village[] = [
  {
    id: "konoha",
    nom: "Konohagakure",
    court: "Konoha",
    pays: "Pays du Feu",
    emblem: villageKonoha,
    teinte: "#3fa34d",
    description: "La Feuille. Berceau des grands clans et de la Volonté du Feu.",
  },
  {
    id: "suna",
    nom: "Sunagakure",
    court: "Suna",
    pays: "Pays du Vent",
    emblem: villageSuna,
    teinte: "#cb9a5a",
    description: "Le Sable. Un désert rude, des marionnettistes endurants.",
  },
  {
    id: "kiri",
    nom: "Kirigakure",
    court: "Kiri",
    pays: "Pays de l'Eau",
    emblem: villageKiri,
    teinte: "#4f8c8c",
    description: "La Brume. Des îles, des bretteurs, un passé lourd.",
  },
  {
    id: "kumo",
    nom: "Kumogakure",
    court: "Kumo",
    pays: "Pays de la Foudre",
    emblem: villageKumo,
    teinte: "#7fa8c9",
    description: "Les Nuages. Des sommets, de la foudre et de la fierté.",
  },
  {
    id: "iwa",
    nom: "Iwagakure",
    court: "Iwa",
    pays: "Pays de la Terre",
    emblem: villageIwa,
    teinte: "#9a7b54",
    description: "La Roche. Une défense que rien n'ébranle.",
  },
  {
    id: "ame",
    nom: "Amegakure",
    court: "Ame",
    pays: "Pays de la Pluie",
    emblem: villageAme,
    teinte: "#5c6b78",
    description: "La Pluie. Secrète, méfiante, marquée par la guerre.",
  },
];

export type Clan = {
  /** Valeur canonique ASCII, la même que le mod. */
  nom: string;
  village: VillageId;
  emblem: string;
  trait: string;
};

// Q4 (décision du 24/10) : TOUS les clans passent par une session de clan,
// en jeu, annoncée sur le Discord. La candidature n'en propose aucun : le
// premier personnage arrive « sans clan » (clan « Autre » du mod, §4.2) avec
// un nom de famille libre. La liste ne sert ici qu'à montrer, pour
// information, les clans du village, et à refuser un nom de famille qui
// serait celui d'un clan (CharacterRules.isPredefinedClan).
export const SANS_CLAN = "Autre";
export const EMBLEM_SANS_CLAN = clanAutre;

export const CLANS: Clan[] = [
  // Konoha (ordre du mod, VILLAGE_CLANS)
  { nom: "Uchiha", village: "konoha", emblem: clanUchiha, trait: "Sharingan" },
  { nom: "Senju", village: "konoha", emblem: clanSenju, trait: "Vitalité du Bois" },
  { nom: "Hyuga", village: "konoha", emblem: clanHyuga, trait: "Byakugan" },
  { nom: "Nara", village: "konoha", emblem: clanNara, trait: "Ombres" },
  { nom: "Sarutobi", village: "konoha", emblem: clanSarutobi, trait: "Feu et devoir" },
  { nom: "Aburame", village: "konoha", emblem: clanAburame, trait: "Insectes" },
  { nom: "Akimichi", village: "konoha", emblem: clanAkimichi, trait: "Expansion" },
  { nom: "Yamanaka", village: "konoha", emblem: clanYamanaka, trait: "Esprit" },
  { nom: "Hatake", village: "konoha", emblem: clanHatake, trait: "Crocs blancs" },
  { nom: "Uzumaki", village: "konoha", emblem: clanUzumaki, trait: "Sceaux" },
  { nom: "Kurama", village: "konoha", emblem: clanKurama, trait: "Illusions" },
  // Suna
  { nom: "Sabaku", village: "suna", emblem: clanSabaku, trait: "Sable" },
  { nom: "Hoki", village: "suna", emblem: clanHoki, trait: "Marionnettes" },
  // Kiri
  { nom: "Hozuki", village: "kiri", emblem: clanHozuki, trait: "Hydrification" },
  { nom: "Hoshigaki", village: "kiri", emblem: clanHoshigaki, trait: "Chakra immense" },
  { nom: "Kaguya", village: "kiri", emblem: clanKaguya, trait: "Ossements" },
  { nom: "Yuki", village: "kiri", emblem: clanYuki, trait: "Glace" },
  { nom: "Terumi", village: "kiri", emblem: clanTerumi, trait: "Lave et Vapeur" },
  // Kumo
  { nom: "Yotsuki", village: "kumo", emblem: clanYotsuki, trait: "Foudre" },
  // Iwa
  { nom: "Kamizuru", village: "iwa", emblem: clanKamizuru, trait: "Abeilles" },
  { nom: "Onoki", village: "iwa", emblem: clanOnoki, trait: "Poussière" },
];

export function villageDe(id: VillageId | null): Village | null {
  return VILLAGES.find((v) => v.id === id) ?? null;
}

export function clanDe(nom: string | null): Clan | null {
  return CLANS.find((c) => c.nom === nom) ?? null;
}

// ──────────────────────────────────────────────────────
//  Mesures et messages (§2.4)
// ──────────────────────────────────────────────────────

const NF = new Intl.NumberFormat("fr-FR");
export const nombre = (n: number) => NF.format(n);

/** Caractères comptés comme l'API (String.length, UTF-16). */
export const longueur = (s: string) => s.length;

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

/** Message sous le champ (null = rien à dire). */
export function messageTexte(valeur: string, b: Bornes, obligatoire = true): string | null {
  const n = longueur(valeur);
  if (n === 0) return obligatoire ? `Encore ${nombre(b.min)} caractères (${nombre(b.min)} minimum).` : null;
  if (n < b.min) return `Encore ${nombre(b.min - n)} caractères (${nombre(b.min)} minimum).`;
  if (n > b.max) return `${nombre(n - b.max)} caractères de trop (${nombre(b.max)} maximum).`;
  return null;
}

export function messageAge(brut: string): string | null {
  if (brut.trim() === "") return "Indique ton âge.";
  const n = Number(brut);
  if (!Number.isInteger(n)) return "Un nombre entier, sans lettres.";
  if (n < BORNES.playerAge.min) return "Reborn accueille les joueurs à partir de 15 ans.";
  if (n > BORNES.playerAge.max) return "Entre 15 et 99.";
  return null;
}

export function messagePrenom(v: string): string | null {
  const t = v.trim();
  if (t === "") return "Indique le prénom de ton personnage.";
  if (!/^[\p{L}][\p{L} '-]*$/u.test(t) || t.length < BORNES.firstName.min || t.length > BORNES.firstName.max) {
    return "Lettres, espaces, apostrophes et tirets, de 3 à 20 caractères.";
  }
  return null;
}

export function messageAgePerso(brut: string): string | null {
  if (brut.trim() === "") return "Indique l'âge de ton personnage.";
  const n = Number(brut);
  if (!Number.isInteger(n) || n < BORNES.characterAge.min || n > BORNES.characterAge.max) {
    return "Entre 10 et 60 ans, les bornes du jeu.";
  }
  return null;
}

/** Nom de famille libre, règles du mod : 3 à 20 lettres, jamais le nom d'un clan. */
export function messageNomFamille(v: string): string | null {
  const t = v.trim();
  if (t === "") return "Indique le nom de famille de ton personnage.";
  if (CLANS.some((c) => c.nom.toLowerCase() === t.toLowerCase())) {
    return `« ${t} » est un nom de clan. Les clans se rejoignent en jeu, lors des sessions de clan : choisis un autre nom.`;
  }
  if (!/^[\p{L}][\p{L} '-]*$/u.test(t) || t.length < BORNES.nomFamille.min || t.length > BORNES.nomFamille.max) {
    return "Lettres, espaces, apostrophes et tirets, de 3 à 20 caractères.";
  }
  return null;
}

export function messageSupport(v: string): string | null {
  const t = v.trim();
  if (t === "") return null;
  if (t.length > BORNES.support.max) return `${nombre(t.length - BORNES.support.max)} caractères de trop (500 maximum).`;
  if (!/^https:\/\/\S+$/.test(t)) return "Un lien complet, qui commence par https://";
  return null;
}

// ──────────────────────────────────────────────────────
//  Le dossier (brouillon)
// ──────────────────────────────────────────────────────

export type Dossier = {
  // 1 — Vous (HRP)
  playerAge: string;
  rpYears: RpYears | null;
  narutoKnowledge: Connaissance | null;
  availability: string;
  discoverySource: string;
  // 2 — Personnage (RP) : prénom, nom de famille libre (pas de clan, Q4), type (Q3)
  firstName: string;
  nomFamille: string;
  characterType: TypePerso | null;
  characterSex: Sexe | null;
  characterAge: string;
  // 3 — Village (RP) : le seul choix de faction ; les clans en information
  village: VillageId | null;
  // 4 — Histoire (RP)
  history: string;
  objectives: string;
  support: string;
  // 5 — Voix (RP)
  micTeste: boolean;
  voixDuree: number | null;
  // 6 — Envoi
  reglement: boolean;
  consentVoix: boolean;
};

export type Manque = { onglet: OngletId; champ: string; libelle: string };

/** Manques par onglet : sert aux coches des onglets et à « Il reste à compléter ». */
export function manques(d: Dossier): Manque[] {
  const out: Manque[] = [];
  const add = (onglet: OngletId, champ: string, libelle: string) => out.push({ onglet, champ, libelle });
  const texte = (onglet: OngletId, champ: keyof Dossier, libelle: string, b: Bornes) => {
    if (messageTexte(d[champ] as string, b) !== null) add(onglet, champ, libelle);
  };

  if (messageAge(d.playerAge)) add("vous", "playerAge", "Ton âge");
  if (!d.rpYears) add("vous", "rpYears", "Années de roleplay");
  if (!d.narutoKnowledge) add("vous", "narutoKnowledge", "Connaissance de l'univers");
  texte("vous", "availability", "Tes disponibilités", BORNES.availability);

  if (messagePrenom(d.firstName)) add("personnage", "firstName", "Prénom");
  if (messageNomFamille(d.nomFamille)) add("personnage", "nomFamille", "Nom de famille");
  if (!d.characterType) add("personnage", "characterType", "Type de personnage");
  if (!d.characterSex) add("personnage", "characterSex", "Sexe");
  if (messageAgePerso(d.characterAge)) add("personnage", "characterAge", "Âge du personnage");

  if (!d.village) add("village", "village", "Village");

  texte("histoire", "history", "Histoire du personnage", BORNES.history);
  texte("histoire", "objectives", "Objectifs roleplay", BORNES.objectives);
  if (messageSupport(d.support)) add("histoire", "support", "Support visuel");

  if (!d.micTeste) add("voix", "micTeste", "Test du micro");
  if (d.voixDuree === null || d.voixDuree < BORNES.voix.min) add("voix", "voixDuree", "Enregistrement vocal");

  return out;
}

export function manquesDe(d: Dossier, onglet: OngletId): Manque[] {
  return manques(d).filter((m) => m.onglet === onglet);
}

/** Nom affiché en jeu : prénom + nom de famille libre. */
export function nomComplet(d: Dossier): string {
  return [d.firstName.trim(), d.nomFamille.trim()].filter(Boolean).join(" ");
}

// ──────────────────────────────────────────────────────
//  Textes fictifs (écrits pour la maquette)
// ──────────────────────────────────────────────────────

const DISPONIBILITES = "En semaine après 20 h, le samedi toute la journée. Moins le dimanche.";

const DISPONIBILITES_COURTES = "Le soir, surtout.";

/** Au-delà des 2 000 caractères (L7 « texte trop long »). */
const DISPONIBILITES_LONGUES = ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"]
  .map(
    (jour) =>
      `Le ${jour}, je suis là de 20 h à minuit environ, parfois plus tard quand une scène est en cours et que les autres joueurs veulent la terminer. ` +
      "Je préviens toujours sur Discord quand je dois partir plus tôt, et je reprends la scène le lendemain si besoin, au même endroit, avec les mêmes personnes.",
  )
  .join("\n\n");

export const OBJECTIFS =
  "À court terme : réussir l'examen chūnin qu'il a raté une première fois, et gagner la confiance de son chef d'équipe. À moyen terme : intégrer la cellule de renseignement du village, pour laquelle son sens de l'analyse le désigne. Plus loin : comprendre pourquoi son père a été rayé des registres du village, quitte à fouiller là où personne ne veut qu'il fouille. Je veux aussi le confronter à la peur : il réfléchit trop et agit trop tard, et j'aimerais que le RP l'oblige à changer.";

const OBJECTIFS_COURTS =
  "Réussir l'examen chūnin qu'il a raté une première fois, puis intégrer la cellule de renseignement du village. Comprendre pourquoi son père a été rayé des registres du village.";

export const HISTOIRE = `## Une enfance à l'ombre

Kaito est né à Konoha, chez les **Arashi**, une famille sans clan qui tient les registres du village depuis trois générations. Son père, archiviste, lui a appris à lire avant de lui apprendre à lancer un kunai, et c'est dans les rayonnages poussiéreux des archives qu'il a passé ses premières années.

À huit ans, son père disparaît du jour au lendemain. Son nom est *rayé* des registres. Personne n'en parle, et Kaito apprend vite à ne plus poser la question.

## L'Académie

Élève moyen, il s'ennuie pendant les cours et brille pendant les examens écrits. Il sort de l'Académie à douze ans, dans une équipe qu'on lui a présentée comme « celle des cas à part » :

- Aiko, une fille de marchand qui veut prouver qu'elle vaut les héritiers de clan ;
- Daichi, trop fort, trop sûr de lui ;
- et leur sensei, Haru, un jōnin fatigué qui ne sourit jamais.

## Aujourd'hui

À seize ans, Kaito a raté son premier examen chūnin en réfléchissant une seconde de trop. Il s'entraîne seul, la nuit, à agir avant de penser.

> « Une ombre ne doute pas. Elle suit. »

Il ne sait pas encore s'il croit à cette phrase.`;

// ──────────────────────────────────────────────────────
//  Préréglages (un par état photographié)
// ──────────────────────────────────────────────────────

/** Brouillon en cours : seules les disponibilités sont trop courtes. */
export const DOSSIER_EN_COURS: Dossier = {
  playerAge: "19",
  rpYears: "3 à 5 ans",
  narutoKnowledge: "Bonne connaissance",
  availability: DISPONIBILITES_COURTES,
  discoverySource: "Un ami",
  firstName: "Kaito",
  nomFamille: "Arashi",
  characterType: "Ninja",
  characterSex: "Homme",
  characterAge: "16",
  village: "konoha",
  history: HISTOIRE,
  objectives: OBJECTIFS,
  support: "",
  micTeste: true,
  voixDuree: 42,
  reglement: false,
  consentVoix: false,
};

/** Plusieurs onglets incomplets (onglet Envoi, « Il reste à compléter »). */
export const DOSSIER_A_COMPLETER: Dossier = {
  ...DOSSIER_EN_COURS,
  characterAge: "",
  objectives: OBJECTIFS_COURTS,
  micTeste: false,
  voixDuree: null,
};

/** Tout est rempli : relecture, cases cochées, bouton actif. */
export const DOSSIER_COMPLET: Dossier = {
  ...DOSSIER_EN_COURS,
  availability: DISPONIBILITES,
  support: "https://www.canva.com/design/kaito-arashi",
  reglement: true,
  consentVoix: true,
};

/** États d'erreur : âge hors bornes, texte trop long (le texte trop court est en L1). */
export const DOSSIER_ERREURS: Dossier = {
  ...DOSSIER_EN_COURS,
  playerAge: "14",
  availability: DISPONIBILITES_LONGUES,
};

/** Nom de famille refusé : c'est celui d'un clan (les clans se rejoignent en session, Q4). */
export const DOSSIER_NOM_CLAN: Dossier = { ...DOSSIER_EN_COURS, nomFamille: "Uchiha" };

/** Envoi refusé par l'API : le fichier de voix n'est pas arrivé (envoi interrompu). */
export const DOSSIER_REFUS_API: Dossier = { ...DOSSIER_COMPLET };

// ──────────────────────────────────────────────────────
//  Joueur fictif, horloge figée
// ──────────────────────────────────────────────────────

export const JOUEUR = {
  pseudo: "Kazuki",
  discord: "kazuki.rp",
  rbcoins: 0,
};

/** « Maintenant » des maquettes : samedi 24 octobre 2026, 18 h 30 (heure locale). */
export const MAINTENANT = new Date(2026, 9, 24, 18, 30);

const JOUR = new Intl.DateTimeFormat("fr-FR", { weekday: "long", day: "numeric", month: "long" });
const JOUR_COURT = new Intl.DateTimeFormat("fr-FR", { weekday: "short", day: "numeric" });
const MOIS_COURT = new Intl.DateTimeFormat("fr-FR", { month: "short" });

/** « 21 h » / « 21 h 30 » (typographie française). */
export function heure(d: Date): string {
  const h = d.getHours();
  const m = d.getMinutes();
  return m === 0 ? `${h} h` : `${h} h ${String(m).padStart(2, "0")}`;
}
export const jourLong = (d: Date) => JOUR.format(d);
export const jourCourt = (d: Date) => JOUR_COURT.format(d).replace(".", "");
export const moisCourt = (d: Date) => MOIS_COURT.format(d).replace(".", "");

/** « dans 1 j 2 h » / « dans 22 h 35 min ». */
export function compteARebours(cible: Date, depuis: Date = MAINTENANT): string {
  // Heure murale (sans le changement d'heure du 25 octobre) : c'est ce que lit le joueur.
  const mural = (d: Date) => Date.UTC(d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes());
  let min = Math.max(0, Math.round((mural(cible) - mural(depuis)) / 60000));
  const j = Math.floor(min / 1440);
  min -= j * 1440;
  const h = Math.floor(min / 60);
  min -= h * 60;
  if (j > 0) return `dans ${j} j ${h} h`;
  if (h > 0) return `dans ${h} h ${String(min).padStart(2, "0")} min`;
  return `dans ${min} min`;
}

// ──────────────────────────────────────────────────────
//  Suivi après envoi (§2.7)
// ──────────────────────────────────────────────────────

export const ENVOYE_LE = new Date(2026, 9, 23, 18, 12);
export const RELU_LE = new Date(2026, 9, 24, 14, 5);
export const REFUSE_LE = new Date(2026, 9, 24, 17, 5);
/** Délai de 1 jour après un refus. */
export const NOUVELLE_CANDIDATURE_LE = new Date(2026, 9, 25, 17, 5);

export const NOTE_STAFF_ACCEPTE =
  "Dossier propre et une histoire qui tient debout. On veut entendre Kaito à la douane : prépare-toi à parler de son père.";

export const NOTE_STAFF_REVOIR =
  "La partie HRP est validée. Côté RP, ton histoire donne à Kaito le Byakugan, appris auprès d'un Hyuga : Kaito arrive sans clan, et les clans se rejoignent en jeu, lors des sessions de clan, jamais par la candidature. Reprends ce passage, et précise ses objectifs : ils restent trop vagues.";

export const NOTE_STAFF_REFUS =
  "Plusieurs passages de l'histoire sont copiés d'un autre serveur, mot pour mot. Une candidature doit être personnelle. Tu peux en écrire une nouvelle après le délai.";

export type MessageFil = {
  id: string;
  author: "me" | "staff" | "system";
  authorName: string | null;
  content: string;
  createdAt: string;
};

export const FIL_SUIVI: MessageFil[] = [
  {
    id: "m1",
    author: "staff",
    authorName: "Ayame",
    content:
      "Salut Kazuki ! Je prends ta candidature. Petite question en attendant : Kaito est sorti de l'Académie à quel âge ?",
    createdAt: "2026-10-24T10:42:00",
  },
  {
    id: "m2",
    author: "me",
    authorName: null,
    content: "Salut ! À douze ans, juste avant l'examen chūnin raté dont je parle dans l'histoire.",
    createdAt: "2026-10-24T11:03:00",
  },
  {
    id: "m3",
    author: "staff",
    authorName: "Ayame",
    content: "Parfait, merci. La relecture continue, la réponse arrivera ici.",
    createdAt: "2026-10-24T11:10:00",
  },
];

// ──────────────────────────────────────────────────────
//  Séances de douane (§3.3)
// ──────────────────────────────────────────────────────

export type Seance = {
  id: string;
  debut: Date;
  fin: Date;
  capacite: number;
  prises: number;
  examinateurs: number;
};

const s = (id: string, j: number, h1: number, h2: number, capacite: number, prises: number, examinateurs: number): Seance => ({
  id,
  debut: new Date(2026, 9, j, h1, 0),
  fin: new Date(2026, 9, j, h2, 0),
  capacite,
  prises,
  examinateurs,
});

export const SEANCES: Seance[] = [
  s("s1", 25, 21, 23, 16, 11, 2),
  s("s2", 27, 21, 22, 8, 3, 1),
  s("s3", 30, 21, 23, 16, 7, 2),
  s("s4", 31, 15, 16, 8, 8, 2),
  s("s5", 31, 21, 23, 16, 12, 2),
];

/** Convocations échelonnées par vagues de 30 min (séance de plus d'une heure). */
export function vagues(se: Seance): Date[] {
  const out: Date[] = [];
  const duree = (se.fin.getTime() - se.debut.getTime()) / 60000;
  if (duree <= 60) return [se.debut];
  for (let t = 0; t < duree - 30; t += 30) out.push(new Date(se.debut.getTime() + t * 60000));
  return out;
}

/** Convocation attribuée par l'API pour la maquette : la première vague qui a encore de la place. */
export function convocation(se: Seance): Date {
  const v = vagues(se);
  const parVague = Math.ceil(se.capacite / v.length);
  return v[Math.min(v.length - 1, Math.floor(se.prises / parVague))];
}

/** Fenêtre d'entrée : de H − 15 min à H + 30 min. */
export function fenetre(h: Date): { ouvre: Date; ferme: Date } {
  return { ouvre: new Date(h.getTime() - 15 * 60000), ferme: new Date(h.getTime() + 30 * 60000) };
}
