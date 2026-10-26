import {
  AppStatus,
  CharacterSex,
  CharacterType,
  DiscoverySource,
  NarutoKnowledge,
  Role,
  RpYears,
} from '@prisma/client';

/**
 * Règles de la candidature v2 (docs/gameplay/CANDIDATURE_ET_DOUANE.md §1.2,
 * §2.3, §2.4, §2.7, §7 « Tranché le 24/10 », « Complément du 24/10 » et
 * « Complément du 25/10 »).
 *
 * Tout est pur et testé (whitelist.rules.spec.ts) : listes fermées, bornes,
 * contrôle champ par champ, statut global, phases. Depuis le 25/10, plus de
 * voie « ancien format » : seul `formVersion: 2` s'envoie ; les anciens
 * dossiers (formVersion 1) restent lisibles. L'API reste l'autorité : le
 * launcher affiche les mêmes bornes, lues dans GET /v1/whitelist/options.
 */

// ──────────────────────────────────────────────────────
//  Listes fermées
// ──────────────────────────────────────────────────────

/** Les six villages proposés, valeurs EXACTES du mod (CharacterCreateScreen.VILLAGES). Déserteur exclu. */
export const VILLAGES = [
  { valeur: 'Konohagakure', court: 'Konoha', pays: 'Pays du Feu' },
  { valeur: 'Sunagakure', court: 'Suna', pays: 'Pays du Vent' },
  { valeur: 'Kirigakure', court: 'Kiri', pays: "Pays de l'Eau" },
  { valeur: 'Kumogakure', court: 'Kumo', pays: 'Pays de la Foudre' },
  { valeur: 'Iwagakure', court: 'Iwa', pays: 'Pays de la Terre' },
  { valeur: 'Amegakure', court: 'Ame', pays: 'Pays de la Pluie' },
] as const;

export const VILLAGE_VALEURS: readonly string[] = VILLAGES.map((v) => v.valeur);

/**
 * Clans du mod (CharacterCreateScreen.VILLAGE_CLANS), POUR INFORMATION : depuis
 * le 24/10 (Q4), tous se rejoignent en jeu, en session de clan. La candidature
 * n'en propose aucun ; cette liste sert à refuser un nom de famille qui serait
 * celui d'un clan (règle du mod, CharacterRules.isPredefinedClan). Elle doit
 * rester alignée sur le mod.
 */
export const CLANS: ReadonlyArray<{ nom: string; village: string }> = [
  { nom: 'Uchiha', village: 'Konohagakure' },
  { nom: 'Senju', village: 'Konohagakure' },
  { nom: 'Hyuga', village: 'Konohagakure' },
  { nom: 'Nara', village: 'Konohagakure' },
  { nom: 'Sarutobi', village: 'Konohagakure' },
  { nom: 'Aburame', village: 'Konohagakure' },
  { nom: 'Akimichi', village: 'Konohagakure' },
  { nom: 'Yamanaka', village: 'Konohagakure' },
  { nom: 'Hatake', village: 'Konohagakure' },
  { nom: 'Uzumaki', village: 'Konohagakure' },
  { nom: 'Kurama', village: 'Konohagakure' },
  { nom: 'Sabaku', village: 'Sunagakure' },
  { nom: 'Hoki', village: 'Sunagakure' },
  { nom: 'Hozuki', village: 'Kirigakure' },
  { nom: 'Hoshigaki', village: 'Kirigakure' },
  { nom: 'Kaguya', village: 'Kirigakure' },
  { nom: 'Yuki', village: 'Kirigakure' },
  { nom: 'Terumi', village: 'Kirigakure' },
  { nom: 'Yotsuki', village: 'Kumogakure' },
  { nom: 'Kamizuru', village: 'Iwagakure' },
  { nom: 'Onoki', village: 'Iwagakure' },
];

/** Clan « personnalisé » du mod : celui du premier personnage, arrivé sans clan (§4.2). */
export const SANS_CLAN = 'Autre';

/** Libellés français des listes fermées (codes ASCII en base). */
export const LIBELLES = {
  rpYears: {
    JAMAIS: 'Jamais',
    MOINS_D_UN_AN: "Moins d'un an",
    UN_A_DEUX_ANS: '1 à 2 ans',
    TROIS_A_CINQ_ANS: '3 à 5 ans',
    SIX_ANS_ET_PLUS: '6 ans et plus',
  } satisfies Record<RpYears, string>,
  narutoKnowledge: {
    DEBUTANT: 'Débutant',
    QUELQUES_ARCS: 'Quelques arcs',
    BONNE_CONNAISSANCE: 'Bonne connaissance',
    INCOLLABLE: 'Incollable, Boruto compris',
  } satisfies Record<NarutoKnowledge, string>,
  discoverySource: {
    DISCORD: 'Discord',
    TIKTOK: 'TikTok',
    YOUTUBE: 'YouTube',
    AMI: 'Un ami',
    AUTRE_SERVEUR: 'Un autre serveur',
    RECHERCHE: 'Une recherche',
    AUTRE: 'Autre',
  } satisfies Record<DiscoverySource, string>,
  characterType: {
    NINJA: 'Ninja',
    CIVIL: 'Civil',
  } satisfies Record<CharacterType, string>,
  /** Valeurs attendues par le mod. */
  characterSex: {
    HOMME: 'Homme',
    FEMME: 'Femme',
  } satisfies Record<CharacterSex, string>,
};

// ──────────────────────────────────────────────────────
//  Bornes
// ──────────────────────────────────────────────────────

export interface Bornes {
  min: number;
  max: number;
}

/** Âge minimum du joueur : `WHITELIST_AGE_MIN`, 15 par défaut (Q1). Borné entre 13 et 99. */
export function ageMinimum(env: NodeJS.ProcessEnv = process.env): number {
  const n = Number(env.WHITELIST_AGE_MIN?.trim());
  return Number.isInteger(n) && n >= 13 && n <= 99 ? n : 15;
}

/** Bornes de la candidature v2 (longueurs en caractères UTF-16, comme `String.length` côté launcher). */
export function bornesV2(env: NodeJS.ProcessEnv = process.env) {
  return {
    playerAge: { min: ageMinimum(env), max: 99 },
    availability: { min: 20, max: 2000 },
    firstName: { min: 3, max: 20 },
    lastName: { min: 3, max: 20 },
    characterAge: { min: 10, max: 60 },
    history: { min: 150, max: 12000 },
    objectives: { min: 400, max: 8000 },
    support: { min: 0, max: 500 },
    voixSec: { min: 15, max: 60 },
  } satisfies Record<string, Bornes>;
}

/**
 * Réponse (400) à tout envoi sans `formVersion: 2`, en POST comme en PATCH
 * (complément du 25/10 : plus de voie « ancien format » ; l'API et le launcher
 * de la candidature v2 sont publiés ensemble).
 */
export const MSG_LAUNCHER_A_JOUR =
  'Mets ton launcher à jour pour envoyer ta candidature.';

/** Délai après un refus, écrit ou oral, avant une nouvelle candidature (Q7 : 1 jour). */
export const DELAI_NOUVELLE_CANDIDATURE_MS = 24 * 3600 * 1000;

/** Durée de garde de la voix après la décision finale (Q2 : 30 jours). */
export const GARDE_VOIX_MS = 30 * 24 * 3600 * 1000;

/**
 * Voix d'un brouillon abandonné (jamais envoyée) : effacée après 30 jours sans
 * activité sur le brouillon (complément du 25/10). L'activité, c'est un
 * enregistrement du brouillon : sauvegarde automatique ou dépôt de voix
 * (`CandidatureBrouillon.updatedAt`).
 */
export const INACTIVITE_BROUILLON_VOIX_MS = 30 * 24 * 3600 * 1000;

/**
 * Fenêtre d'entrée à la douane autour de l'heure de convocation H (§3.3).
 * Valeurs par défaut : le retard maximal réel se règle par DOUANE_RETARD_MAX_MIN,
 * lu par la douane (douane.regles.ts › lireRetardMax) et passé à `calculerSuivi`
 * (`retardMaxMin`), pour que le launcher affiche la même fenêtre que celle du
 * jeton de jeu.
 */
export const FENETRE_AVANT_MS = 15 * 60 * 1000;
export const FENETRE_APRES_MS = 30 * 60 * 1000;

// ──────────────────────────────────────────────────────
//  Mesures et formats
// ──────────────────────────────────────────────────────

const NF = new Intl.NumberFormat('fr-FR');
export const nombre = (n: number): string => NF.format(n);

/** Prénom et nom de famille : lettres, espaces, apostrophes, tirets ; une lettre en premier (règle du mod). */
const MOTIF_NOM = /^\p{L}[\p{L} '’-]*$/u;

/** Comparaison sans casse ni accents : « Hyûga » vaut « Hyuga ». */
function cleNom(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').trim().toLowerCase();
}

/** Vrai si le nom est celui d'un clan du mod, ou le clan réservé « Autre ». */
export function estNomDeClan(nom: string): boolean {
  const k = cleNom(nom);
  return k === cleNom(SANS_CLAN) || CLANS.some((c) => cleNom(c.nom) === k);
}

/** Parties de date et d'heure à Paris (l'heure que lisent les joueurs). */
function partiesParis(d: Date) {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Paris',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(d);
  const n = (t: string) => Number(parts.find((p) => p.type === t)?.value);
  return {
    an: n('year'),
    mois: n('month'),
    jour: n('day'),
    h: n('hour'),
    mi: n('minute'),
    s: n('second'),
  };
}

/** Écart entre l'heure de Paris et UTC à cet instant, en ms (+1 h ou +2 h). */
function decalageParis(d: Date): number {
  const p = partiesParis(d);
  const commeUtc = Date.UTC(p.an, p.mois - 1, p.jour, p.h, p.mi, p.s);
  return commeUtc - Math.floor(d.getTime() / 1000) * 1000;
}

/** Minuit, heure de Paris, du jour qui suit `d` (« dès le lendemain », Q7). */
export function debutLendemainParis(d: Date): Date {
  const p = partiesParis(d);
  const minuit = Date.UTC(p.an, p.mois - 1, p.jour + 1, 0, 0, 0);
  // Deux passes : le décalage peut changer entre `d` et minuit (changement d'heure).
  let t = minuit - decalageParis(new Date(minuit));
  t = minuit - decalageParis(new Date(t));
  return new Date(t);
}

/**
 * « 21 h », « 21 h 30 » ou « 9 h 05 », heure de Paris : sans zéro devant l'heure,
 * sans minutes nulles. Seul format d'heure des textes montrés aux joueurs
 * (candidature, douane, jeton de jeu), comme les maquettes L9 et L10.
 */
export function heureCourteParis(d: Date): string {
  const p = partiesParis(d);
  return p.mi === 0 ? `${p.h} h` : `${p.h} h ${String(p.mi).padStart(2, '0')}`;
}

/** « 26/10 à 21 h » ou « 26/10 à 21 h 05 », heure de Paris. */
export function formatDateParis(d: Date): string {
  const p = partiesParis(d);
  const dd = String(p.jour).padStart(2, '0');
  const mm = String(p.mois).padStart(2, '0');
  return `${dd}/${mm} à ${heureCourteParis(d)}`;
}

/**
 * Âge révolu à `now` (date de Paris) d'une date de naissance AAAA-MM-JJ.
 * null si la date est illisible. Sert à la lecture des anciens dossiers, qui
 * portent une date de naissance au lieu d'un âge déclaré.
 */
export function ageDepuisNaissance(dob: string, now: Date): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(dob);
  if (!m) return null;
  const [an, mois, jour] = [Number(m[1]), Number(m[2]), Number(m[3])];
  if (mois < 1 || mois > 12 || jour < 1 || jour > 31) return null;
  const p = partiesParis(now);
  const pasEncore = p.mois < mois || (p.mois === mois && p.jour < jour);
  return p.an - an - (pasEncore ? 1 : 0);
}

// ──────────────────────────────────────────────────────
//  Contrôle des champs
// ──────────────────────────────────────────────────────

/**
 * Vrai seulement si le launcher annonce `formVersion: 2`. Sinon (ancien
 * launcher, sans formVersion, ou version inconnue), l'envoi est refusé d'un
 * bloc, avant tout contrôle de champ, avec MSG_LAUNCHER_A_JOUR : un ancien
 * launcher ne saurait pas corriger une erreur de champ de la v2.
 */
export function estEnvoiV2(o: object | null | undefined): boolean {
  return (o as { formVersion?: unknown } | null | undefined)?.formVersion === 2;
}

/**
 * Champs de l'ancien formulaire, retirés de la v2 (complément du 24/10 : l'oral
 * en jeu les remplace). Encore déclarés dans le DTO pour qu'un envoi de l'ancien
 * launcher reçoive MSG_LAUNCHER_A_JOUR plutôt que « champ inconnu » ; refusés
 * dans un envoi v2. Les anciens dossiers gardent leurs valeurs, en lecture.
 */
const CHAMPS_V1_SEULEMENT = [
  'dob',
  'motivation',
  'experience',
  'appearance',
] as const;

/**
 * Champs de l'envoi par partie (noms du corps de POST /whitelist et PATCH
 * /whitelist/me) ; `verifierChamp` refuse lui-même les champs retirés de la
 * v2. Le renvoi après « À revoir » ne contrôle que les parties rouvertes
 * (§2.7).
 */
export const CHAMPS_ENVOI_HRP = [
  'dob',
  'playerAge',
  'rpYears',
  'narutoKnowledge',
  'discoverySource',
  'motivation',
  'experience',
  'availability',
] as const;
export const CHAMPS_ENVOI_RP = [
  'firstName',
  'lastName',
  'village',
  'characterType',
  'characterSex',
  'characterAge',
  'support',
  'history',
  'appearance',
  'objectives',
] as const;
/** Champs communs à tout envoi (onglet Envoi). */
export const CHAMPS_ENVOI_COMMUNS = [
  'formVersion',
  'rulesAccepted',
  'voiceConsent',
] as const;
export const CHAMPS_ENVOI = [
  ...CHAMPS_ENVOI_COMMUNS,
  ...CHAMPS_ENVOI_HRP,
  ...CHAMPS_ENVOI_RP,
] as const;

const absent = (v: unknown) => v === undefined || v === null || v === '';

/** Message d'un nom de famille qui est un nom de clan (ou le clan réservé « Autre »), sinon null. */
function refusNomDeClan(valeur: unknown): string | null {
  if (typeof valeur !== 'string' || !estNomDeClan(valeur)) return null;
  const t = valeur.trim();
  return cleNom(t) === cleNom(SANS_CLAN)
    ? `« ${t} » est réservé par le jeu : choisis un autre nom.`
    : `« ${t} » est un nom de clan. Les clans se rejoignent en jeu, lors des sessions de clan : choisis un autre nom.`;
}

function texte(v: unknown, b: Bornes, obligatoire = true): string | null {
  if (v === undefined || v === null || v === '') {
    return obligatoire
      ? `Encore ${nombre(b.min)} caractères (${nombre(b.min)} minimum).`
      : null;
  }
  if (typeof v !== 'string') return 'Texte attendu.';
  const n = v.length;
  if (n > b.max) {
    const trop = n - b.max;
    return `${nombre(trop)} caractère${trop > 1 ? 's' : ''} de trop (${nombre(b.max)} maximum).`;
  }
  if (n < b.min) {
    const manque = b.min - n;
    return `Encore ${nombre(manque)} caractère${manque > 1 ? 's' : ''} (${nombre(b.min)} minimum).`;
  }
  return null;
}

function nomPropre(v: unknown, b: Bornes, vide: string): string | null {
  if (absent(v)) return vide;
  if (typeof v !== 'string') return 'Texte attendu.';
  const t = v.trim();
  if (t.length < b.min || t.length > b.max || !MOTIF_NOM.test(t)) {
    return `Lettres, espaces, apostrophes et tirets, de ${b.min} à ${b.max} caractères.`;
  }
  return null;
}

function entier(
  v: unknown,
  b: Bornes,
  vide: string,
  horsBornes: string,
): string | null {
  if (v === undefined || v === null || v === '') return vide;
  if (typeof v !== 'number' || !Number.isInteger(v))
    return 'Un nombre entier, sans lettres.';
  if (v < b.min || v > b.max) return horsBornes;
  return null;
}

function choix(
  v: unknown,
  valeurs: readonly string[],
  obligatoire: boolean,
): string | null {
  if (v === undefined || v === null || v === '')
    return obligatoire ? 'Choisis une réponse.' : null;
  if (typeof v !== 'string' || !valeurs.includes(v))
    return `Valeur inconnue (${valeurs.join(', ')}).`;
  return null;
}

/**
 * Contrôle d'UN champ de l'envoi v2 (POST /whitelist, PATCH /whitelist/me).
 * Renvoie le message à montrer (français, sans détail interne) ou null.
 * `formVersion` doit valoir 2 : l'appelant refuse un envoi d'un autre format
 * avant de contrôler ses champs (voir `estEnvoiV2`).
 */
export function verifierChamp(
  champ: string,
  valeur: unknown,
  env: NodeJS.ProcessEnv = process.env,
): string | null {
  if (champ === 'formVersion') {
    return valeur === 2 ? null : MSG_LAUNCHER_A_JOUR;
  }
  if ((CHAMPS_V1_SEULEMENT as readonly string[]).includes(champ)) {
    return absent(valeur)
      ? null
      : 'Champ retiré de la candidature v2 : ne pas l’envoyer.';
  }

  const b2 = bornesV2(env);
  switch (champ) {
    // ── Partie HRP ──
    case 'playerAge':
      return entier(
        valeur,
        b2.playerAge,
        'Indique ton âge.',
        typeof valeur === 'number' && valeur < b2.playerAge.min
          ? `Reborn accueille les joueurs à partir de ${b2.playerAge.min} ans.`
          : `Entre ${b2.playerAge.min} et ${b2.playerAge.max}.`,
      );
    case 'rpYears':
      return choix(valeur, Object.values(RpYears), true);
    case 'narutoKnowledge':
      return choix(valeur, Object.values(NarutoKnowledge), true);
    case 'discoverySource':
      return choix(valeur, Object.values(DiscoverySource), false);
    case 'availability':
      return texte(valeur, b2.availability);

    // ── Partie RP ──
    case 'firstName':
      return nomPropre(
        valeur,
        b2.firstName,
        'Indique le prénom de ton personnage.',
      );
    // Nom de famille : jamais un nom de clan (Q4 : aucun clan dans la
    // candidature ; le jeu lirait sinon ce nom comme le clan du personnage).
    case 'lastName':
      return (
        refusNomDeClan(valeur) ??
        nomPropre(
          valeur,
          b2.lastName,
          'Indique le nom de famille de ton personnage.',
        )
      );
    // Village dans la liste fermée : « Déserteur » exclu depuis le 24/10.
    case 'village':
      return typeof valeur === 'string' && VILLAGE_VALEURS.includes(valeur)
        ? null
        : `Choisis un des six villages (${VILLAGE_VALEURS.join(', ')}).`;
    case 'characterType':
      return choix(valeur, Object.values(CharacterType), true);
    case 'characterSex':
      return choix(valeur, Object.values(CharacterSex), true);
    case 'characterAge':
      return entier(
        valeur,
        b2.characterAge,
        "Indique l'âge de ton personnage.",
        `Entre ${b2.characterAge.min} et ${b2.characterAge.max} ans, les bornes du jeu.`,
      );
    case 'support': {
      if (valeur === undefined || valeur === null) return null;
      if (typeof valeur !== 'string') return 'Texte attendu.';
      const max = b2.support.max;
      if (valeur.length > max)
        return `${nombre(valeur.length - max)} caractères de trop (${nombre(max)} maximum).`;
      return /^\s*$|^\s*https:\/\/\S+\s*$/.test(valeur)
        ? null
        : 'Un lien complet, qui commence par https://';
    }
    case 'history':
      return texte(valeur, b2.history);
    case 'objectives':
      return texte(valeur, b2.objectives);

    // ── Onglet Envoi ──
    case 'rulesAccepted':
      return valeur === true
        ? null
        : 'Coche « J’ai lu le règlement et ces réponses sont de moi ».';
    case 'voiceConsent':
      return valeur === true
        ? null
        : 'Coche « J’accepte que ma voix soit enregistrée pour ma candidature ».';
    default:
      return null;
  }
}

/**
 * Contrôle d'une liste de champs de l'envoi v2. Messages au format du
 * ValidationPipe global, « champ: message », pour que le launcher les rattache
 * au bon champ. Liste vide = envoi valide.
 */
export function verifierEnvoi(
  objet: Record<string, unknown>,
  champs: readonly string[],
  env: NodeJS.ProcessEnv = process.env,
): string[] {
  const out: string[] = [];
  for (const champ of champs) {
    const msg = verifierChamp(champ, objet[champ], env);
    if (msg) out.push(`${champ}: ${msg}`);
  }
  return out;
}

// ──────────────────────────────────────────────────────
//  Brouillon serveur (§2.5)
// ──────────────────────────────────────────────────────

/** Taille maximale du brouillon, JSON compris. */
export const BROUILLON_OCTETS_MAX = 256 * 1024;
const BROUILLON_CLES_MAX = 100;
/** Texte d'un champ inconnu : deux fois le plus long maximum (l'histoire). */
const BROUILLON_TEXTE_MAX_DEFAUT = 24000;

/**
 * Contrôle souple du brouillon : aucun minimum, et un texte trop long n'est
 * jamais coupé (§2.4). Il reste enregistrable jusqu'à DEUX FOIS son maximum,
 * pour que l'enregistrement automatique ne casse pas pendant que le joueur le
 * raccourcit. Renvoie `{ code, message }` (413 ou 400) ou null.
 */
export function verifierBrouillon(
  data: unknown,
  env: NodeJS.ProcessEnv = process.env,
): { code: 400 | 413; message: string } | null {
  if (typeof data !== 'object' || data === null || Array.isArray(data)) {
    return { code: 400, message: 'data: un objet JSON est attendu.' };
  }
  const octets = Buffer.byteLength(JSON.stringify(data), 'utf8');
  if (octets > BROUILLON_OCTETS_MAX) {
    return {
      code: 413,
      message: `Brouillon trop lourd (${nombre(BROUILLON_OCTETS_MAX / 1024)} Ko au plus).`,
    };
  }
  const cles = Object.keys(data);
  if (cles.length > BROUILLON_CLES_MAX) {
    return {
      code: 400,
      message: `data: ${BROUILLON_CLES_MAX} champs au plus.`,
    };
  }
  const bornes: Record<string, Bornes> = bornesV2(env);
  for (const cle of cles) {
    const v = (data as Record<string, unknown>)[cle];
    if (typeof v !== 'string') continue;
    const max = bornes[cle] ? bornes[cle].max * 2 : BROUILLON_TEXTE_MAX_DEFAUT;
    if (v.length > max) {
      return {
        code: 400,
        message: `${cle}: ${nombre(v.length - max)} caractères de trop pour un brouillon (${nombre(max)} au plus).`,
      };
    }
  }
  return null;
}

// ──────────────────────────────────────────────────────
//  Statut global et phases
// ──────────────────────────────────────────────────────

/**
 * Statut global dérivé des trois décisions (§1.3) : APPROVED seulement après
 * l'oral (ou si l'oral est déjà passé sur ce compte). Écrit validé, oral à
 * passer ou à revoir = PENDING : l'écrit n'est pas à reprendre.
 */
export function statutGlobal(
  hrp: AppStatus,
  rp: AppStatus,
  oral: AppStatus,
  oralDejaPasse: boolean,
): AppStatus {
  if (
    hrp === AppStatus.REJECTED ||
    rp === AppStatus.REJECTED ||
    oral === AppStatus.REJECTED
  ) {
    return AppStatus.REJECTED;
  }
  if (hrp === AppStatus.NEEDS_REVISION || rp === AppStatus.NEEDS_REVISION) {
    return AppStatus.NEEDS_REVISION;
  }
  if (hrp === AppStatus.APPROVED && rp === AppStatus.APPROVED) {
    return oral === AppStatus.APPROVED || oralDejaPasse
      ? AppStatus.APPROVED
      : AppStatus.PENDING;
  }
  return AppStatus.PENDING;
}

export type PhaseCandidature =
  | 'BROUILLON'
  | 'ECRIT_EN_ATTENTE'
  | 'ECRIT_A_REVOIR'
  | 'DOUANE_A_RESERVER'
  | 'DOUANE_RESERVEE'
  | 'DOUANE_A_REVOIR'
  | 'REFUSE'
  | 'ACCEPTE';

export interface EntreePhase {
  role: Role;
  oralPassedAt: Date | null;
  whitelistRejectedAt: Date | null;
  app: {
    status: AppStatus;
    hrpStatus: AppStatus;
    rpStatus: AppStatus;
    oralStatus: AppStatus;
    oralDecidedAt: Date | null;
    reviewedAt: Date | null;
  } | null;
  /** Réservation BOOKED du compte dans une séance de douane, s'il y en a une. */
  reservation: {
    id: string;
    seanceId: string;
    convocationAt: Date;
    /** Arrivée au hall enregistrée (lue seulement avec `placeFermeeManquee`). */
    presentAt?: Date | null;
  } | null;
  now: Date;
  /** Retard maximal après H, en minutes (DOUANE_RETARD_MAX_MIN) ; 30 par défaut. */
  retardMaxMin?: number;
  /**
   * Vue du candidat (launcher) : une place encore BOOKED, sans arrivée, dont la
   * fenêtre d'entrée est fermée compte déjà comme manquée (comme le fait la
   * réservation suivante), sans attendre que la tâche planifiée la solde après
   * la fin de la séance. Absent : la place compte tant qu'elle est BOOKED
   * (panel, décision d'oral).
   */
  placeFermeeManquee?: boolean;
  /** REBORN_DOUANE_ENABLED : la douane se passe en jeu (sinon, oral hors jeu de la transition, §3.11). */
  douaneEnJeu?: boolean;
}

/** Une place BOOKED sans arrivée dont la fenêtre d'entrée (H + retard maximal) est passée. */
export function placeManquee(
  reservation: { convocationAt: Date; presentAt?: Date | null },
  now: Date,
  retardMaxMin?: number,
): boolean {
  if (reservation.presentAt) return false;
  const ferme =
    reservation.convocationAt.getTime() +
    (retardMaxMin !== undefined ? retardMaxMin * 60 * 1000 : FENETRE_APRES_MS);
  return now.getTime() > ferme;
}

export interface SuiviCandidature {
  phase: PhaseCandidature;
  /** REFUSE : date à partir de laquelle une nouvelle candidature complète est possible (ISO). */
  canReapplyAt: string | null;
  /** REFUSE : refus de l'écrit ou de l'oral. */
  refusPar: 'ECRIT' | 'ORAL' | null;
  /** ECRIT_A_REVOIR : parties rouvertes ; les autres restent verrouillées (§2.7). */
  partiesRouvertes: Array<'HRP' | 'RP'>;
  /** false si l'oral est déjà passé sur ce compte : l'écrit validé mène directement à ACCEPTE. */
  oralRequis: boolean;
  /** DOUANE_RESERVEE : la place réservée et la fenêtre d'entrée (H − 15 min à H + 30 min). */
  reservation: {
    id: string;
    seanceId: string;
    convocationAt: string;
    entreeOuvreAt: string;
    entreeFermeAt: string;
  } | null;
  /** DOUANE_A_REVOIR : nouvelle séance réservable à partir de cette date (« dès le lendemain »). */
  reservableDes: string | null;
  /**
   * true : la douane se passe en jeu (REBORN_DOUANE_ENABLED). false pendant la
   * transition : l'oral se passe hors jeu, le launcher n'affiche ni « Passer la
   * douane » ni les textes « en jeu ».
   */
  douaneEnJeu: boolean;
}

/** Calcule la phase lue par le launcher (et plus tard par le serveur de jeu) au lieu du rôle (§1.2). */
export function calculerSuivi(e: EntreePhase): SuiviCandidature {
  const base: SuiviCandidature = {
    phase: 'BROUILLON',
    canReapplyAt: null,
    refusPar: null,
    partiesRouvertes: [],
    oralRequis: e.oralPassedAt === null,
    reservation: null,
    reservableDes: null,
    douaneEnJeu: e.douaneEnJeu === true,
  };
  if (e.role !== Role.PLAYER)
    return { ...base, phase: 'ACCEPTE', oralRequis: false };

  const app = e.app;
  const delai = (d: Date | null) =>
    d
      ? new Date(d.getTime() + DELAI_NOUVELLE_CANDIDATURE_MS).toISOString()
      : null;

  if (!app) {
    // Retrait après un refus : le délai court toujours (whitelistRejectedAt vit sur le compte).
    if (
      e.whitelistRejectedAt &&
      e.now.getTime() <
        e.whitelistRejectedAt.getTime() + DELAI_NOUVELLE_CANDIDATURE_MS
    ) {
      return {
        ...base,
        phase: 'REFUSE',
        canReapplyAt: delai(e.whitelistRejectedAt),
      };
    }
    return base;
  }

  if (app.status === AppStatus.APPROVED)
    return { ...base, phase: 'ACCEPTE', oralRequis: false };

  const refusOral = app.oralStatus === AppStatus.REJECTED;
  if (
    refusOral ||
    app.status === AppStatus.REJECTED ||
    app.hrpStatus === AppStatus.REJECTED ||
    app.rpStatus === AppStatus.REJECTED
  ) {
    const refusLe =
      e.whitelistRejectedAt ??
      (refusOral ? app.oralDecidedAt : app.reviewedAt) ??
      e.now;
    return {
      ...base,
      phase: 'REFUSE',
      refusPar: refusOral ? 'ORAL' : 'ECRIT',
      canReapplyAt: delai(refusLe),
    };
  }

  const ecritValide =
    app.hrpStatus === AppStatus.APPROVED && app.rpStatus === AppStatus.APPROVED;
  if (
    !ecritValide &&
    (app.status === AppStatus.NEEDS_REVISION ||
      app.hrpStatus === AppStatus.NEEDS_REVISION ||
      app.rpStatus === AppStatus.NEEDS_REVISION)
  ) {
    // Seules les parties « à revoir » rouvrent (§2.7) : une partie encore en
    // relecture reste fermée. Statut global « à revoir » ancien, sans partie
    // marquée : les parties non validées rouvrent toutes.
    const parties: Array<'HRP' | 'RP'> = [];
    if (app.hrpStatus === AppStatus.NEEDS_REVISION) parties.push('HRP');
    if (app.rpStatus === AppStatus.NEEDS_REVISION) parties.push('RP');
    if (parties.length === 0) {
      if (app.hrpStatus !== AppStatus.APPROVED) parties.push('HRP');
      if (app.rpStatus !== AppStatus.APPROVED) parties.push('RP');
    }
    return { ...base, phase: 'ECRIT_A_REVOIR', partiesRouvertes: parties };
  }

  if (!ecritValide) return { ...base, phase: 'ECRIT_EN_ATTENTE' };

  if (e.oralPassedAt || app.oralStatus === AppStatus.APPROVED) {
    return { ...base, phase: 'ACCEPTE', oralRequis: false };
  }
  const place =
    e.reservation &&
    !(
      e.placeFermeeManquee && placeManquee(e.reservation, e.now, e.retardMaxMin)
    )
      ? e.reservation
      : null;
  if (place) {
    const h = place.convocationAt.getTime();
    return {
      ...base,
      phase: 'DOUANE_RESERVEE',
      reservation: {
        id: place.id,
        seanceId: place.seanceId,
        convocationAt: place.convocationAt.toISOString(),
        entreeOuvreAt: new Date(h - FENETRE_AVANT_MS).toISOString(),
        entreeFermeAt: new Date(
          h +
            (e.retardMaxMin !== undefined
              ? e.retardMaxMin * 60 * 1000
              : FENETRE_APRES_MS),
        ).toISOString(),
      },
    };
  }
  if (app.oralStatus === AppStatus.NEEDS_REVISION) {
    return {
      ...base,
      phase: 'DOUANE_A_REVOIR',
      reservableDes: debutLendemainParis(
        app.oralDecidedAt ?? e.now,
      ).toISOString(),
    };
  }
  return { ...base, phase: 'DOUANE_A_RESERVER' };
}
