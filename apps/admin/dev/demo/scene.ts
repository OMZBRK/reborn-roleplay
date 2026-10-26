/**
 * MODE DÉMO : données fictives du panel (développement seulement).
 *
 * Pseudos, personnages et textes inventés, aucune vraie donnée personnelle,
 * aucune IP, aucun vrai joueur (PANEL_V2 §13). Mêmes noms que les maquettes
 * validées (Maquettes/panel_v2/_sources/data.mjs) pour comparer les captures,
 * avec une scène cohérente : un candidat n'est pas déjà whitelisté, la douane
 * ne reçoit que des écrits validés.
 *
 * Toutes les dates sont relatives au chargement du module (« il y a 2 j 6 h »
 * reste vrai quel que soit le jour de la capture). La séance de douane du
 * jour est en cours au moment du chargement.
 *
 * N'importe de l'application QUE des types.
 */

import type {
  AdminMessage,
  AppStatus,
  AuditLogItem,
  CharacterSex,
  CharacterType,
  DiscoverySource,
  LoginAnomaly,
  NarutoKnowledge,
  OralSlot,
  PhaseCandidature,
  Role,
  RpYears,
  TicketCategory,
  TicketStatus,
  WikiEntry,
  WikiIdea,
  WikiTag,
} from '@/lib/types';
import type { TechniqueSummary } from '@/lib/abilities';
import type { StaffDiscordMessage } from '@/lib/discord';

// ── Temps ─────────────────────────────────────────────────────────────

export const MIN = 60_000;
export const H = 60 * MIN;
export const J = 24 * H;

/** Instant de référence de la scène. */
export const T0 = Date.now();

/** ISO d'un instant passé (`il y a ms`). */
export function avant(ms: number): string {
  return new Date(T0 - ms).toISOString();
}

/** ISO d'un instant futur (`dans ms`). */
export function apres(ms: number): string {
  return new Date(T0 + ms).toISOString();
}

/** Décalage de Paris (ms) à un instant donné. */
function decalageParis(t: number): number {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Paris',
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).formatToParts(new Date(t));
  const v = (k: string) => Number(parts.find((p) => p.type === k)?.value);
  const commeUtc = Date.UTC(v('year'), v('month') - 1, v('day'), v('hour'), v('minute'));
  return commeUtc - Math.floor(t / MIN) * MIN;
}

/** Instant d'un jour (décalé de `jours` depuis aujourd'hui) à hh:mm, heure de Paris. */
export function jourParis(jours: number, hh: number, mm = 0): string {
  const ici = new Date(T0 + decalageParis(T0));
  const local = Date.UTC(ici.getUTCFullYear(), ici.getUTCMonth(), ici.getUTCDate() + jours, hh, mm);
  return new Date(local - decalageParis(local)).toISOString();
}

// ── Comptes ───────────────────────────────────────────────────────────

/** UUID fictif stable tiré du pseudo (format v4, jamais celui d'un vrai compte). */
export function uuidDe(pseudo: string): string {
  let h1 = 0x811c9dc5;
  let h2 = 0x01000193;
  const hex: string[] = [];
  for (let n = 0; n < 4; n++) {
    for (const c of `${pseudo}#${n}`) {
      h1 = Math.imul(h1 ^ c.charCodeAt(0), 16777619) >>> 0;
      h2 = Math.imul(h2 + c.charCodeAt(0), 2246822519) >>> 0;
    }
    hex.push(((h1 ^ h2) >>> 0).toString(16).padStart(8, '0'));
  }
  const s = hex.join('');
  return `${s.slice(0, 8)}-${s.slice(8, 12)}-4${s.slice(13, 16)}-a${s.slice(17, 20)}-${s.slice(20, 32)}`;
}

export interface CompteDemo {
  id: string;
  pseudo: string;
  uuid: string;
  discord: string | null;
  role: Role;
  banni?: { motif: string; jusquA: string | null };
  /** Dernière connexion (ms écoulées), null : jamais. */
  vuIlYa: number | null;
  creeIlYa: number;
  pays: string | null;
}

function compte(
  pseudo: string,
  role: Role,
  vuIlYa: number | null,
  extra: Partial<CompteDemo> = {},
): CompteDemo {
  return {
    id: `u-${pseudo.toLowerCase().replace(/[^a-z0-9]/g, '')}`,
    pseudo,
    uuid: uuidDe(pseudo),
    discord: pseudo.toLowerCase().replace(/_$/, '').replace(/_/g, '.'),
    role,
    vuIlYa,
    creeIlYa: 40 * J,
    pays: 'France',
    ...extra,
  };
}

/** Le staff connecté (« Kenzo · Administrateur » des maquettes). */
export const MOI_PSEUDO = 'Kenzo';

export const COMPTES: CompteDemo[] = [
  // Staff
  compte('Kenzo', 'ADMIN', 2 * MIN, { discord: 'kenzo.rbn', creeIlYa: 120 * J }),
  compte('Asuka', 'MODERATOR', 6 * MIN, { discord: 'asuka.rbn', creeIlYa: 110 * J }),
  compte('Renji', 'WHITELIST_REVIEWER', 25 * MIN, { creeIlYa: 95 * J }),
  compte('PixelJin', 'HELPER', 12 * MIN, { creeIlYa: 80 * J }),
  compte('Sekko', 'DEVELOPPEUR', 3 * H, { creeIlYa: 100 * J }),
  compte('Mokuren', 'MODELISATEUR', 26 * H, { creeIlYa: 70 * J }),
  compte('Raijin', 'OWNER', 2 * J, { creeIlYa: 150 * J }),
  // Joueurs whitelistés
  compte('Tobiwan', 'WHITELISTED', 4 * MIN),
  compte('Mizu_No', 'WHITELISTED', 40 * MIN, { discord: 'mizu.no' }),
  compte('Daisuke_K', 'WHITELISTED', 2 * H, { discord: 'daisukek' }),
  compte('Yumeko', 'WHITELISTED', 3 * H, { discord: 'yumeko_' }),
  compte('Kaito_RS', 'WHITELISTED', 9 * MIN, { discord: 'kaito.rs', pays: 'Belgique' }),
  compte('Lysandre', 'WHITELISTED', 23 * H, { discord: 'lysandre.v' }),
  compte('Zephyrin', 'WHITELISTED', 26 * H),
  compte('Brioche', 'WHITELISTED', 5 * H),
  compte('Lanterne', 'WHITELISTED', 30 * H),
  compte('Nocturne', 'WHITELISTED', 3 * J),
  compte('Tonnerre_', 'WHITELISTED', 4 * J),
  compte('Nekomata', 'WHITELISTED', 6 * J, {
    banni: { motif: 'Triche : vol de sac répété hors RP', jusquA: null },
  }),
  // Candidats
  compte('Hikari_Rose', 'PLAYER', 50 * MIN, { discord: 'hikari.rose', creeIlYa: 4 * J }),
  compte('Kaelrin', 'PLAYER', 5 * H, { creeIlYa: 3 * J }),
  compte('Ombrelune', 'PLAYER', 21 * H, { creeIlYa: 2 * J }),
  compte('Sobaa', 'PLAYER', 2 * J, { creeIlYa: 9 * J }),
  compte('Plume_', 'PLAYER', 6 * H, { creeIlYa: 1 * J }),
  compte('Onde_Bleue', 'PLAYER', 28 * H, { creeIlYa: 8 * J }),
  compte('Grisaille', 'PLAYER', 7 * H, { creeIlYa: 5 * J }),
  compte('Natsuki_', 'PLAYER', 9 * H, { creeIlYa: 10 * J }),
  compte('Frostmane', 'WHITELISTED', 30 * MIN, { creeIlYa: 6 * J }),
  compte('Vespera', 'WHITELISTED', 20 * MIN, { creeIlYa: 6 * J }),
  compte('Akari_', 'PLAYER', 15 * MIN, { creeIlYa: 7 * J }),
  compte('Bambou', 'PLAYER', 18 * MIN, { creeIlYa: 7 * J }),
  compte('Cendre', 'WHITELISTED', 45 * MIN, { creeIlYa: 6 * J }),
  compte('Dorian_K', 'PLAYER', 55 * MIN, { creeIlYa: 6 * J }),
  compte('Eclipse', 'PLAYER', 8 * MIN, { creeIlYa: 5 * J }),
  compte('Fuyu', 'PLAYER', 10 * MIN, { creeIlYa: 5 * J }),
  compte('Gingko', 'PLAYER', 3 * H, { creeIlYa: 5 * J }),
  compte('Hoshi_', 'PLAYER', 4 * H, { creeIlYa: 4 * J }),
  compte('Iroha', 'PLAYER', 2 * MIN, { creeIlYa: 4 * J }),
  compte('Jade_', 'PLAYER', 1 * MIN, { creeIlYa: 4 * J }),
];

export function comptePar(pseudo: string): CompteDemo {
  const c = COMPTES.find((x) => x.pseudo === pseudo);
  if (!c) throw new Error(`Compte démo inconnu : ${pseudo}`);
  return c;
}

export function compteParId(id: string): CompteDemo | undefined {
  return COMPTES.find((x) => x.id === id);
}

// ── Candidatures ──────────────────────────────────────────────────────

export interface CandidatureDemo {
  id: string;
  pseudo: string;
  firstName: string;
  lastName: string;
  village: string;
  phase: PhaseCandidature;
  status: AppStatus;
  hrp: AppStatus;
  rp: AppStatus;
  oral: AppStatus;
  envoyeeIlYa: number;
  relueIlYa: number | null;
  ecritValideIlYa: number | null;
  oralDecideIlYa: number | null;
  /** Pseudo du staff qui a pris le dossier. */
  pris: string | null;
  prisIlYa: number | null;
  relu: string | null;
  examinateur: string | null;
  type: CharacterType;
  sexe: CharacterSex;
  agePerso: number;
  ageJoueur: number;
  rpYears: RpYears;
  naruto: NarutoKnowledge;
  source: DiscoverySource;
  dispo: string;
  support: string;
  histoire: string;
  objectifs: string;
  motif: string | null;
  notePrivee: string | null;
  /** Réservation de douane (id de la séance, rang de vague). */
  reservation: { seance: string; vague: number } | null;
  messages: Array<{ auteur: 'USER' | 'STAFF' | 'SYSTEM'; nom: string | null; texte: string; ilYa: number }>;
}

const HISTOIRE_AIKO = `Aiko est née dans le quartier est de Konoha, au-dessus de l'échoppe de thé que tient sa mère. Son père, chūnin discret, est mort en mission quand elle avait six ans ; on ne lui a jamais dit où ni comment, et ce silence a décidé de beaucoup de choses.

Enfant, elle observe plus qu'elle ne parle. Elle apprend à lire les clients de l'échoppe : qui ment sur son argent, qui attend quelqu'un, qui a peur. Cette habitude devient une manière d'être. À l'Académie, elle n'est ni la plus forte ni la plus rapide, mais elle est celle qui remarque le détail que les autres laissent passer.

Son frère aîné, Daichi, lui a laissé un bandeau trop grand avant de partir pour une longue mission à la frontière. Elle le porte tous les jours, noué deux fois, en attendant son retour.

Aujourd'hui jeune genin, Aiko veut comprendre ce qui est arrivé à son père sans trahir le village qu'il servait. Elle se méfie des adultes qui parlent trop vite et cherche, sans le dire, un maître qui accepterait de lui répondre franchement.`;

const OBJECTIFS_AIKO = `Devenir chūnin en s'appuyant sur l'observation plutôt que sur la force, retrouver la trace de la dernière mission de son père, et protéger l'échoppe de sa mère des dettes qui s'accumulent.`;

function candidature(c: Partial<CandidatureDemo> & Pick<CandidatureDemo, 'pseudo' | 'firstName' | 'lastName' | 'village' | 'phase' | 'envoyeeIlYa'>): CandidatureDemo {
  const base: CandidatureDemo = {
    id: `c-${c.pseudo.toLowerCase().replace(/[^a-z0-9]/g, '')}`,
    status: 'PENDING',
    hrp: 'PENDING',
    rp: 'PENDING',
    oral: 'PENDING',
    relueIlYa: null,
    ecritValideIlYa: null,
    oralDecideIlYa: null,
    pris: null,
    prisIlYa: null,
    relu: null,
    examinateur: null,
    type: 'NINJA',
    sexe: 'HOMME',
    agePerso: 16,
    ageJoueur: 18,
    rpYears: 'UN_A_DEUX_ANS',
    naruto: 'BONNE_CONNAISSANCE',
    source: 'DISCORD',
    dispo: 'Soirs de semaine, week-end',
    support: 'Ninjutsu',
    histoire: `${c.firstName} a grandi à ${c.village}, entre l'Académie et les petits travaux qui faisaient vivre sa famille. Son histoire est encore à écrire : c'est pour ça qu'il veut rejoindre Reborn.`,
    objectifs: `Trouver sa place au village et apprendre auprès d'un maître.`,
    motif: null,
    notePrivee: null,
    reservation: null,
    messages: [],
    ...c,
  } as CandidatureDemo;
  return base;
}

export const SEANCE_DU_JOUR = 's-jour';

export const CANDIDATURES: CandidatureDemo[] = [
  // Écrits à relire (le plus ancien d'abord)
  candidature({
    pseudo: 'Hikari_Rose',
    firstName: 'Aiko',
    lastName: 'Tsukimori',
    village: 'Konohagakure',
    phase: 'ECRIT_EN_ATTENTE',
    envoyeeIlYa: 2 * J + 6 * H,
    pris: MOI_PSEUDO,
    prisIlYa: 40 * MIN,
    sexe: 'FEMME',
    agePerso: 15,
    ageJoueur: 17,
    rpYears: 'TROIS_A_CINQ_ANS',
    source: 'TIKTOK',
    support: 'Genjutsu',
    histoire: HISTOIRE_AIKO,
    objectifs: OBJECTIFS_AIKO,
    messages: [
      { auteur: 'USER', nom: 'Hikari_Rose', texte: 'Bonjour ! Je voulais savoir si je peux préciser le nom de famille plus tard en jeu ?', ilYa: 26 * H },
    ],
  }),
  candidature({ pseudo: 'Kaelrin', firstName: 'Ren', lastName: 'Kazehara', village: 'Konohagakure', phase: 'ECRIT_EN_ATTENTE', envoyeeIlYa: J + 20 * H }),
  candidature({ pseudo: 'Ombrelune', firstName: 'Mei', lastName: 'Shiranami', village: 'Kirigakure', phase: 'ECRIT_EN_ATTENTE', envoyeeIlYa: J + 3 * H, sexe: 'FEMME' }),
  candidature({
    pseudo: 'Sobaa',
    firstName: 'Haru',
    lastName: 'Kurobane',
    village: 'Konohagakure',
    phase: 'ECRIT_EN_ATTENTE',
    hrp: 'APPROVED',
    envoyeeIlYa: 14 * H,
    relueIlYa: 3 * J,
    relu: 'Renji',
    motif: 'Ton histoire manque de détails sur l’enfance et la formation de ton personnage.',
  }),
  candidature({ pseudo: 'Plume_', firstName: 'Sayuri', lastName: 'Hanabusa', village: 'Amegakure', phase: 'ECRIT_EN_ATTENTE', envoyeeIlYa: 6 * H, sexe: 'FEMME', type: 'CIVIL', support: 'Médecine' }),
  // Douane
  candidature({ pseudo: 'Grisaille', firstName: 'Tetsu', lastName: 'Ishikawa', village: 'Iwagakure', phase: 'DOUANE_A_RESERVER', hrp: 'APPROVED', rp: 'APPROVED', envoyeeIlYa: 3 * J, relueIlYa: 2 * J, ecritValideIlYa: 2 * J, pris: 'Asuka', relu: 'Asuka' }),
  candidature({ pseudo: 'Frostmane', firstName: 'Shiro', lastName: 'Fuyutsuki', village: 'Kirigakure', phase: 'DOUANE_RESERVEE', hrp: 'APPROVED', rp: 'APPROVED', envoyeeIlYa: 4 * J, relueIlYa: 3 * J, ecritValideIlYa: 3 * J, pris: 'Renji', relu: 'Renji', reservation: { seance: SEANCE_DU_JOUR, vague: 0 } }),
  candidature({ pseudo: 'Vespera', firstName: 'Emi', lastName: 'Asahina', village: 'Konohagakure', phase: 'DOUANE_RESERVEE', hrp: 'APPROVED', rp: 'APPROVED', envoyeeIlYa: 3 * J + 4 * H, relueIlYa: 2 * J, ecritValideIlYa: 2 * J, pris: 'Renji', relu: 'Renji', sexe: 'FEMME', reservation: { seance: SEANCE_DU_JOUR, vague: 0 } }),
  ...(
    [
      ['Akari_', 'Akari', 'Minase', 'Konohagakure', 0, 'FEMME'],
      ['Bambou', 'Kenji', 'Oguri', 'Kumogakure', 0, 'HOMME'],
      ['Cendre', 'Rin', 'Kanzaki', 'Kirigakure', 1, 'FEMME'],
      ['Dorian_K', 'Daiki', 'Morioka', 'Kirigakure', 1, 'HOMME'],
      ['Eclipse', 'Yoru', 'Tachibana', 'Konohagakure', 1, 'HOMME'],
      ['Fuyu', 'Fuyumi', 'Shimizu', 'Kirigakure', 2, 'FEMME'],
      ['Gingko', 'Ginta', 'Hayashida', 'Iwagakure', 2, 'HOMME'],
      ['Hoshi_', 'Hoshiko', 'Amamiya', 'Amegakure', 2, 'FEMME'],
      ['Iroha', 'Iroha', 'Sakuraba', 'Konohagakure', 3, 'FEMME'],
      ['Jade_', 'Jun', 'Takasuna', 'Sunagakure', 3, 'HOMME'],
    ] as const
  ).map(([pseudo, firstName, lastName, village, vague, sexe], i) =>
    candidature({
      pseudo,
      firstName,
      lastName,
      village,
      phase: 'DOUANE_RESERVEE',
      hrp: 'APPROVED',
      rp: 'APPROVED',
      sexe,
      envoyeeIlYa: (4 + (i % 3)) * J,
      relueIlYa: (2 + (i % 3)) * J,
      ecritValideIlYa: (2 + (i % 3)) * J,
      pris: i % 2 ? 'Asuka' : 'Renji',
      relu: i % 2 ? 'Asuka' : 'Renji',
      reservation: { seance: SEANCE_DU_JOUR, vague },
    }),
  ),
  // À revoir (chez le joueur)
  candidature({
    pseudo: 'Onde_Bleue',
    firstName: 'Nami',
    lastName: 'Umeda',
    village: 'Kirigakure',
    phase: 'ECRIT_A_REVOIR',
    status: 'NEEDS_REVISION',
    hrp: 'APPROVED',
    rp: 'NEEDS_REVISION',
    envoyeeIlYa: 5 * J,
    relueIlYa: 4 * J,
    pris: 'Asuka',
    relu: 'Asuka',
    sexe: 'FEMME',
    motif: 'Un élément de ton histoire contredit le lore de Reborn : précise lequel tu veux garder.',
  }),
  // Décidées
  candidature({ pseudo: 'Brioche', firstName: 'Kaji', lastName: 'Okuda', village: 'Konohagakure', phase: 'ACCEPTE', status: 'APPROVED', hrp: 'APPROVED', rp: 'APPROVED', oral: 'APPROVED', envoyeeIlYa: 6 * J, relueIlYa: 5 * J, ecritValideIlYa: 5 * J, oralDecideIlYa: 4 * J, pris: 'Renji', relu: 'Renji', examinateur: 'Renji' }),
  candidature({ pseudo: 'Natsuki_', firstName: 'Natsu', lastName: 'Hoshizora', village: 'Sunagakure', phase: 'REFUSE', status: 'REJECTED', hrp: 'APPROVED', rp: 'REJECTED', envoyeeIlYa: 6 * J + 2 * H, relueIlYa: 5 * J, pris: 'Asuka', relu: 'Asuka', motif: 'Ton personnage ressemble trop à un personnage de l’œuvre.' }),
  candidature({ pseudo: 'Lanterne', firstName: 'Toru', lastName: 'Kagehara', village: 'Konohagakure', phase: 'ACCEPTE', status: 'APPROVED', hrp: 'APPROVED', rp: 'APPROVED', oral: 'APPROVED', envoyeeIlYa: 8 * J, relueIlYa: 7 * J, ecritValideIlYa: 7 * J, oralDecideIlYa: 6 * J, pris: MOI_PSEUDO, relu: MOI_PSEUDO, examinateur: 'Asuka' }),
  candidature({ pseudo: 'Nocturne', firstName: 'Yoru', lastName: 'Mizushima', village: 'Kirigakure', phase: 'ACCEPTE', status: 'APPROVED', hrp: 'APPROVED', rp: 'APPROVED', oral: 'APPROVED', envoyeeIlYa: 9 * J, relueIlYa: 8 * J, ecritValideIlYa: 8 * J, oralDecideIlYa: 7 * J, pris: 'Renji', relu: 'Renji', examinateur: 'Renji' }),
  candidature({ pseudo: 'Tonnerre_', firstName: 'Kai', lastName: 'Raikawa', village: 'Kumogakure', phase: 'ACCEPTE', status: 'APPROVED', hrp: 'APPROVED', rp: 'APPROVED', oral: 'APPROVED', envoyeeIlYa: 9 * J + 5 * H, relueIlYa: 8 * J, ecritValideIlYa: 8 * J, oralDecideIlYa: 7 * J, pris: 'Asuka', relu: 'Asuka', examinateur: MOI_PSEUDO }),
];

// ── Douane ────────────────────────────────────────────────────────────

/** Début de la séance du jour : en cours depuis une heure environ (le jour même à Paris). */
function debutSeanceDuJour(): string {
  const arrondi = Math.floor((T0 - 60 * MIN) / (30 * MIN)) * 30 * MIN;
  const minuit = Date.parse(jourParis(0, 0, 10));
  return new Date(Math.max(arrondi, minuit)).toISOString();
}

export interface SeanceDemo {
  id: string;
  startAt: string;
  endAt: string;
  capacite: number;
  statut: 'OUVERTE' | 'FERMEE' | 'TERMINEE' | 'ANNULEE';
  examinateurs: string[];
  notes: string | null;
  motifAnnulation: string | null;
  /** Comptes figés des séances passées (sans réservation détaillée). */
  passe?: { faites: number; manquees: number; annulees: number };
  /** Inscrits d'une séance à venir (réservations non détaillées). */
  inscrits?: number;
}

const debutJour = debutSeanceDuJour();

export const SEANCES: SeanceDemo[] = [
  {
    id: SEANCE_DU_JOUR,
    startAt: debutJour,
    endAt: new Date(Date.parse(debutJour) + 2 * H).toISOString(),
    capacite: 16,
    statut: 'FERMEE',
    examinateurs: [MOI_PSEUDO, 'Renji'],
    notes: null,
    motifAnnulation: null,
  },
  { id: 's-jeudi', startAt: jourParis(2, 21), endAt: jourParis(2, 23), capacite: 16, statut: 'OUVERTE', examinateurs: ['Renji', 'Asuka'], notes: null, motifAnnulation: null, inscrits: 9 },
  { id: 's-samedi', startAt: jourParis(4, 15), endAt: jourParis(4, 17), capacite: 8, statut: 'OUVERTE', examinateurs: ['Asuka'], notes: null, motifAnnulation: null, inscrits: 7 },
  { id: 's-dimanche', startAt: jourParis(5, 21), endAt: jourParis(5, 22), capacite: 8, statut: 'OUVERTE', examinateurs: ['Renji'], notes: null, motifAnnulation: null, inscrits: 3 },
  { id: 's-p1', startAt: jourParis(-3, 21), endAt: jourParis(-3, 23), capacite: 16, statut: 'TERMINEE', examinateurs: [MOI_PSEUDO, 'Renji'], notes: 'Bonne séance, deux candidats arrivés en retard.', motifAnnulation: null, passe: { faites: 13, manquees: 2, annulees: 1 } },
  { id: 's-p2', startAt: jourParis(-5, 15), endAt: jourParis(-5, 17), capacite: 8, statut: 'TERMINEE', examinateurs: ['Asuka'], notes: null, motifAnnulation: null, passe: { faites: 7, manquees: 1, annulees: 0 } },
  { id: 's-p3', startAt: jourParis(-7, 21), endAt: jourParis(-7, 22), capacite: 8, statut: 'ANNULEE', examinateurs: ['Renji'], notes: null, motifAnnulation: 'Maintenance du serveur', passe: { faites: 0, manquees: 0, annulees: 5 } },
  { id: 's-p4', startAt: jourParis(-10, 21), endAt: jourParis(-10, 23), capacite: 16, statut: 'TERMINEE', examinateurs: [MOI_PSEUDO, 'Asuka'], notes: null, motifAnnulation: null, passe: { faites: 15, manquees: 1, annulees: 0 } },
];

/**
 * Déroulé de la séance du jour par candidat, en minutes depuis le début de la
 * séance (négatif : avant). La séance a commencé il y a 60 à 90 min : tout ce
 * qui suit est déjà passé. Vagues : 0 (début), 1 (+30 min), 2 (+60 min).
 */
export const DEROULE: Record<string, { vague: number; arrive?: number; appele?: number; sorti?: number; resultat?: AppStatus; examinateur?: string; absent?: boolean }> = {
  Frostmane: { vague: 0, arrive: -8, appele: 4, sorti: 13, resultat: 'APPROVED', examinateur: MOI_PSEUDO },
  Vespera: { vague: 0, arrive: -5, appele: 6, sorti: 16, resultat: 'APPROVED', examinateur: 'Renji' },
  Akari_: { vague: 0, arrive: -2, appele: 15, sorti: 28, resultat: 'NEEDS_REVISION', examinateur: MOI_PSEUDO },
  Bambou: { vague: 0, absent: true },
  Cendre: { vague: 1, arrive: 22, appele: 31, sorti: 44, resultat: 'APPROVED', examinateur: 'Renji' },
  Dorian_K: { vague: 1, arrive: 24, appele: 46, examinateur: MOI_PSEUDO },
  Eclipse: { vague: 1, arrive: 27, appele: 50, examinateur: 'Renji' },
  Fuyu: { vague: 2, arrive: 50 },
  Gingko: { vague: 2, arrive: 55 },
  Hoshi_: { vague: 2 },
  Iroha: { vague: 2 },
  Jade_: { vague: 2 },
};

export const VAGUE_MIN = 30;

// ── Tickets ───────────────────────────────────────────────────────────

export interface TicketDemo {
  id: string;
  numero: number;
  subject: string;
  category: TicketCategory;
  status: TicketStatus;
  pseudo: string;
  creeIlYa: number;
  majIlYa: number;
  pris: string | null;
  prisIlYa: number | null;
  messages: Array<{ auteur: 'USER' | 'STAFF' | 'SYSTEM'; nom: string | null; texte: string; ilYa: number }>;
}

export const TICKETS: TicketDemo[] = [
  {
    id: 't-1047',
    numero: 1047,
    subject: 'Refus de mon écrit, je ne comprends pas le motif',
    category: 'WHITELIST_APPEAL',
    status: 'OPEN',
    pseudo: 'Natsuki_',
    creeIlYa: J + 2 * H,
    majIlYa: J + 2 * H,
    pris: null,
    prisIlYa: null,
    messages: [
      { auteur: 'USER', nom: 'Natsuki_', texte: 'Bonjour, mon écrit a été refusé parce que mon personnage serait trop proche d’un canon. Pouvez-vous me dire lequel ? Je l’ai inventé entièrement.', ilYa: J + 2 * H },
    ],
  },
  {
    id: 't-1046',
    numero: 1046,
    subject: 'RP forcé au marché de Kiri',
    category: 'REPORT_PLAYER',
    status: 'IN_PROGRESS',
    pseudo: 'Mizu_No',
    creeIlYa: 9 * H,
    majIlYa: 40 * MIN,
    pris: MOI_PSEUDO,
    prisIlYa: 65 * MIN,
    messages: [
      { auteur: 'SYSTEM', nom: null, texte: 'Ticket ouvert depuis le launcher', ilYa: 9 * H },
      { auteur: 'USER', nom: 'Mizu_No', texte: 'Bonsoir, hier vers 22 h au marché de Kiri, Daisuke Nara m’a attaqué sans aucune interaction RP avant, puis a pris ma bourse. J’ai la vidéo de la scène.', ilYa: 9 * H },
      { auteur: 'STAFF', nom: MOI_PSEUDO, texte: 'Merci pour la vidéo, je la regarde. Peux-tu me dire si un autre joueur était présent sur la scène ?', ilYa: 65 * MIN },
      { auteur: 'USER', nom: 'Mizu_No', texte: 'Oui, Yume Kosaka (Yumeko) était à côté de l’échoppe, elle a tout vu.', ilYa: 40 * MIN },
    ],
  },
  {
    id: 't-1045',
    numero: 1045,
    subject: 'Je tombe dans le vide au spawn de Suna',
    category: 'BUG',
    status: 'IN_PROGRESS',
    pseudo: 'Tobiwan',
    creeIlYa: 5 * H,
    majIlYa: 12 * MIN,
    pris: MOI_PSEUDO,
    prisIlYa: 2 * H,
    messages: [
      { auteur: 'USER', nom: 'Tobiwan', texte: 'Quand je me connecte à Suna je tombe sous la carte et je meurs. Ça m’est arrivé trois fois ce soir.', ilYa: 5 * H },
      { auteur: 'STAFF', nom: MOI_PSEUDO, texte: 'Je préviens le développeur. Tu te connectes depuis quel endroit exactement ?', ilYa: 2 * H },
      { auteur: 'USER', nom: 'Tobiwan', texte: 'Devant la porte nord, à côté des marchands.', ilYa: 12 * MIN },
    ],
  },
  {
    id: 't-1044',
    numero: 1044,
    subject: 'Changer le prénom affiché de mon personnage',
    category: 'OTHER',
    status: 'OPEN',
    pseudo: 'Yumeko',
    creeIlYa: 3 * H,
    majIlYa: 3 * H,
    pris: null,
    prisIlYa: null,
    messages: [{ auteur: 'USER', nom: 'Yumeko', texte: 'Je voudrais corriger l’orthographe du prénom de mon personnage : Yume au lieu de Yumé.', ilYa: 3 * H }],
  },
  {
    id: 't-1043',
    numero: 1043,
    subject: 'Mon personnage a perdu sa bourse après le crash',
    category: 'BUG',
    status: 'IN_PROGRESS',
    pseudo: 'Lysandre',
    creeIlYa: J + 4 * H,
    majIlYa: 23 * H,
    pris: 'Asuka',
    prisIlYa: J + 3 * H,
    messages: [
      { auteur: 'USER', nom: 'Lysandre', texte: 'Le serveur a planté pendant que je payais au marché, ma bourse est vide depuis.', ilYa: J + 4 * H },
      { auteur: 'STAFF', nom: 'Asuka', texte: 'Je vérifie les journaux du serveur et je reviens vers toi.', ilYa: 23 * H },
    ],
  },
  {
    id: 't-1042',
    numero: 1042,
    subject: 'Les sons du hall de la douane sont trop forts',
    category: 'BUG',
    status: 'RESOLVED',
    pseudo: 'Kaito_RS',
    creeIlYa: J + 6 * H,
    majIlYa: 24 * H,
    pris: 'PixelJin',
    prisIlYa: J + 5 * H,
    messages: [{ auteur: 'STAFF', nom: 'PixelJin', texte: 'Corrigé dans la dernière mise à jour, merci !', ilYa: 24 * H }],
  },
  {
    id: 't-1041',
    numero: 1041,
    subject: 'Signalement : insultes au tchat HRP',
    category: 'REPORT_PLAYER',
    status: 'RESOLVED',
    pseudo: 'Zephyrin',
    creeIlYa: J + 8 * H,
    majIlYa: J + 3 * H,
    pris: 'Asuka',
    prisIlYa: J + 7 * H,
    messages: [{ auteur: 'STAFF', nom: 'Asuka', texte: 'Sanction posée, merci pour le signalement.', ilYa: J + 3 * H }],
  },
  {
    id: 't-1040',
    numero: 1040,
    subject: 'Question sur les clans sous session',
    category: 'OTHER',
    status: 'CLOSED',
    pseudo: 'Sobaa',
    creeIlYa: 4 * J,
    majIlYa: 3 * J,
    pris: 'Renji',
    prisIlYa: 4 * J,
    messages: [{ auteur: 'STAFF', nom: 'Renji', texte: 'Les clans ouvrent par session : la prochaine est annoncée sur le Discord.', ilYa: 3 * J }],
  },
  {
    id: 't-1039',
    numero: 1039,
    subject: 'Achat de RBCoins non reçu',
    category: 'PURCHASE_ISSUE',
    status: 'RESOLVED',
    pseudo: 'Brioche',
    creeIlYa: 5 * J,
    majIlYa: 4 * J,
    pris: MOI_PSEUDO,
    prisIlYa: 5 * J,
    messages: [{ auteur: 'STAFF', nom: MOI_PSEUDO, texte: 'Les RBCoins sont crédités, désolé pour l’attente.', ilYa: 4 * J }],
  },
];

// ── Connexions suspectes ──────────────────────────────────────────────

export const ANOMALIES: Array<Omit<LoginAnomaly, 'userId'> & { pseudo: string }> = [
  { id: 'a-1', pseudo: 'Kaito_RS', ipConnue: true, country: 'Belgique', reason: 'Nouveau pays : Belgique (habituellement France)', acknowledged: false, notifiedVia: ['discord'], createdAt: avant(40 * MIN) },
  { id: 'a-2', pseudo: 'Lysandre', ipConnue: true, country: 'France', reason: 'Trois comptes depuis la même adresse en 24 h', acknowledged: true, notifiedVia: ['discord'], createdAt: avant(2 * J) },
  { id: 'a-3', pseudo: 'Nekomata', ipConnue: true, country: 'Canada', reason: 'Connexion après un bannissement', acknowledged: true, notifiedVia: ['discord', 'panel'], createdAt: avant(6 * J) },
];

// ── Journal ───────────────────────────────────────────────────────────

export const JOURNAL: Array<Omit<AuditLogItem, 'actor' | 'targetUser' | 'ipAddress'> & { acteur: string; cible: string | null }> = [
  { id: 'l-1', action: 'ticket.claim', acteur: MOI_PSEUDO, cible: 'Mizu_No', targetEntity: 'ticket:t-1046', metadata: { ticket: 1046 }, source: 'panel', createdAt: avant(65 * MIN) },
  { id: 'l-2', action: 'whitelist.claim', acteur: MOI_PSEUDO, cible: 'Hikari_Rose', targetEntity: 'whitelist:c-hikarirose', metadata: null, source: 'panel', createdAt: avant(40 * MIN) },
  { id: 'l-3', action: 'douane.jeu.decision', acteur: MOI_PSEUDO, cible: 'Frostmane', targetEntity: 'douane-seance:s-jour', metadata: { resultat: 'APPROVED' }, source: 'game', createdAt: avant(50 * MIN) },
  { id: 'l-4', action: 'douane.jeu.decision', acteur: 'Renji', cible: 'Vespera', targetEntity: 'douane-seance:s-jour', metadata: { resultat: 'APPROVED' }, source: 'game', createdAt: avant(47 * MIN) },
  { id: 'l-5', action: 'douane.jeu.decision', acteur: MOI_PSEUDO, cible: 'Akari_', targetEntity: 'douane-seance:s-jour', metadata: { resultat: 'NEEDS_REVISION', motif: 'Hésitations sur le rôle du personnage' }, source: 'game', createdAt: avant(33 * MIN) },
  { id: 'l-6', action: 'whitelist.approved', acteur: 'Asuka', cible: 'Grisaille', targetEntity: 'whitelist:c-grisaille', metadata: { partie: 'RP', decision: 'APPROVED' }, source: 'panel', createdAt: avant(2 * J) },
  { id: 'l-7', action: 'role.change', acteur: MOI_PSEUDO, cible: 'PixelJin', targetEntity: null, metadata: { de: 'WHITELISTED', vers: 'HELPER', motif: 'Recrutement helper d’octobre' }, source: 'panel', createdAt: avant(3 * J) },
  { id: 'l-8', action: 'ticket.resolved', acteur: 'Asuka', cible: 'Zephyrin', targetEntity: 'ticket:t-1041', metadata: { statut: 'RESOLVED' }, source: 'panel', createdAt: avant(J + 3 * H) },
  { id: 'l-9', action: 'files.write', acteur: 'Mokuren', cible: null, targetEntity: 'file:plugins/Nexo/items/parchemins.yml', metadata: { taille: 4120 }, source: 'panel', createdAt: avant(26 * H) },
  { id: 'l-10', action: 'abilities.deploy', acteur: 'Sekko', cible: null, targetEntity: null, metadata: { techniques: 4, serveur: 'Reborn' }, source: 'panel', createdAt: avant(28 * H) },
  { id: 'l-11', action: 'douane.seance.create', acteur: 'Renji', cible: null, targetEntity: 'douane-seance:s-jeudi', metadata: null, source: 'panel', createdAt: avant(2 * J - 2 * H) },
  { id: 'l-12', action: 'player.ip.view', acteur: MOI_PSEUDO, cible: 'Nekomata', targetEntity: null, metadata: null, source: 'panel', createdAt: avant(6 * J) },
  { id: 'l-13', action: 'whitelist.reject', acteur: 'Asuka', cible: 'Natsuki_', targetEntity: 'whitelist:c-natsuki', metadata: { partie: 'RP', decision: 'REJECTED' }, source: 'panel', createdAt: avant(5 * J) },
  { id: 'l-14', action: 'discord.message.publish', acteur: MOI_PSEUDO, cible: null, targetEntity: null, metadata: { canal: 'dev-updates' }, source: 'panel', createdAt: avant(2 * J + 5 * H) },
];

// ── Anciens créneaux d'oral ───────────────────────────────────────────

export const CRENEAUX: Array<Omit<OralSlot, 'bookedBy'> & { pseudo: string | null }> = [
  { id: 'o-1', startAt: jourParis(1, 20), durationMin: 20, status: 'BOOKED', pseudo: 'Grisaille', bookedAt: avant(20 * H), notes: null },
  { id: 'o-2', startAt: jourParis(1, 20, 30), durationMin: 20, status: 'OPEN', pseudo: null, bookedAt: null, notes: null },
  { id: 'o-3', startAt: jourParis(2, 21), durationMin: 20, status: 'OPEN', pseudo: null, bookedAt: null, notes: null },
  { id: 'o-4', startAt: jourParis(-2, 21), durationMin: 20, status: 'DONE', pseudo: 'Nocturne', bookedAt: avant(4 * J), notes: 'Oral réussi.' },
];

// ── Wiki ──────────────────────────────────────────────────────────────

function tag(id: string, kind: WikiTag['kind'], label: string): WikiTag {
  return { id, kind, label, slug: label.toLowerCase().replace(/\s+/g, '-'), color: null, createdAt: avant(60 * J) };
}

export const TAGS: WikiTag[] = [
  tag('g-canon', 'CANON', 'Canon'),
  tag('g-reborn', 'CANON', 'Propre à Reborn'),
  tag('g-lieu', 'TYPE', 'Lieu'),
  tag('g-regle', 'TYPE', 'Règle'),
  tag('g-clan', 'TYPE', 'Clan'),
  tag('g-staff', 'AUDIENCE', 'Staff'),
  tag('g-joueurs', 'AUDIENCE', 'Joueurs'),
  tag('g-manga', 'SOURCE', 'Manga'),
];

const tg = (...ids: string[]) => TAGS.filter((t) => ids.includes(t.id));

export const WIKI: WikiEntry[] = [
  {
    id: 'w-douane',
    title: 'Tenir une séance de douane',
    slug: 'tenir-une-seance-de-douane',
    summary: 'Déroulé d’une séance : hall, appel, bureau, décision, clôture.',
    body: '## Avant la séance\n\nVérifie la liste des inscrits et les notes privées des écrits.\n\n## Pendant\n\n- Appelle les candidats par vague, dans l’ordre d’arrivée.\n- Pose les trois questions du guide, puis laisse le candidat jouer une courte scène.\n- Décide au Poste de garde : **Accepté**, **À revoir** ou **Refusé**.\n\n## Après\n\nClos la séance depuis le panel avec une note pour l’équipe.',
    status: 'PUBLISHED',
    sources: null,
    createdById: comptePar('Renji').id,
    createdAt: avant(20 * J),
    updatedAt: avant(2 * J),
    tags: tg('g-reborn', 'g-regle', 'g-staff'),
    _count: { revisions: 4 },
  },
  {
    id: 'w-kiri',
    title: 'Kirigakure, le village caché de la Brume',
    slug: 'kirigakure',
    summary: 'Géographie, institutions et ambiance du village tel qu’il est joué sur Reborn.',
    body: '## Géographie\n\nUn archipel de brume, des ponts de bois et un port qui ne dort jamais.\n\n## Institutions\n\nLe Mizukage, le conseil des clans et la garde du port.',
    status: 'PUBLISHED',
    sources: 'Tomes 3 et 4, épisodes 19 à 21',
    createdById: comptePar('Asuka').id,
    createdAt: avant(30 * J),
    updatedAt: avant(5 * J),
    tags: tg('g-canon', 'g-lieu', 'g-joueurs', 'g-manga'),
    _count: { revisions: 7 },
  },
  {
    id: 'w-clans',
    title: 'Clans : ouverture par session',
    slug: 'clans-ouverture-par-session',
    summary: 'Pourquoi aucun premier personnage n’a de clan, et comment se passe une session de clan.',
    body: 'Tous les clans passent par une **session de candidature** dédiée. Le premier personnage d’un joueur n’a pas de clan.',
    status: 'PUBLISHED',
    sources: null,
    createdById: comptePar(MOI_PSEUDO).id,
    createdAt: avant(12 * J),
    updatedAt: avant(12 * J),
    tags: tg('g-reborn', 'g-clan', 'g-regle', 'g-joueurs'),
    _count: { revisions: 1 },
  },
  {
    id: 'w-marche',
    title: 'Le marché de Kiri',
    slug: 'le-marche-de-kiri',
    summary: 'Échoppes, horaires et figurants du marché.',
    body: 'Brouillon en cours.',
    status: 'DRAFT',
    sources: null,
    createdById: comptePar('Asuka').id,
    createdAt: avant(3 * J),
    updatedAt: avant(26 * H),
    tags: tg('g-reborn', 'g-lieu'),
    _count: { revisions: 2 },
  },
];

export const IDEES: WikiIdea[] = [
  { id: 'i-1', title: 'Examen chūnin en public', body: 'Ouvrir les tribunes de l’arène aux joueurs pendant la phase finale.', status: 'PROPOSED', category: 'Événement', linkedEntryId: null, linkedEntry: null, createdById: comptePar('Asuka').id, createdAt: avant(4 * J), updatedAt: avant(4 * J) },
  { id: 'i-2', title: 'Journal du village', body: 'Une gazette RP hebdomadaire écrite par des joueurs.', status: 'ACCEPTED', category: 'Communauté', linkedEntryId: null, linkedEntry: null, createdById: comptePar('PixelJin').id, createdAt: avant(9 * J), updatedAt: avant(6 * J) },
  { id: 'i-3', title: 'Porte animée de Kiri', body: 'Entités d’affichage et modèle de l’équipe.', status: 'IN_PROGRESS', category: 'Carte', linkedEntryId: 'w-kiri', linkedEntry: { id: 'w-kiri', title: 'Kirigakure, le village caché de la Brume', slug: 'kirigakure' }, createdById: comptePar('Mokuren').id, createdAt: avant(14 * J), updatedAt: avant(2 * J) },
  { id: 'i-4', title: 'Guide de la douane', body: 'Fiche du déroulé pour les examinateurs.', status: 'DONE', category: 'Staff', linkedEntryId: 'w-douane', linkedEntry: { id: 'w-douane', title: 'Tenir une séance de douane', slug: 'tenir-une-seance-de-douane' }, createdById: comptePar('Renji').id, createdAt: avant(25 * J), updatedAt: avant(20 * J) },
  { id: 'i-5', title: 'Courses de chevaux', body: 'Hors univers, écarté.', status: 'REJECTED', category: 'Événement', linkedEntryId: null, linkedEntry: null, createdById: comptePar('Tobiwan').id, createdAt: avant(18 * J), updatedAt: avant(16 * J) },
];

// ── Techniques ────────────────────────────────────────────────────────

export const TECHNIQUES: TechniqueSummary[] = [
  { id: 'k-1', slug: 'katon_goukakyuu', name: 'Katon : boule de feu suprême', category: 'Ninjutsu', status: 'PUBLISHED', updatedAt: avant(28 * H) },
  { id: 'k-2', slug: 'suiton_mur', name: 'Suiton : mur d’eau', category: 'Ninjutsu', status: 'PUBLISHED', updatedAt: avant(30 * H) },
  { id: 'k-3', slug: 'doton_mur_de_terre', name: 'Doton : mur de terre', category: 'Ninjutsu', status: 'DRAFT', updatedAt: avant(3 * H) },
  { id: 'k-4', slug: 'kawarimi', name: 'Permutation', category: 'Base', status: 'PUBLISHED', updatedAt: avant(9 * J) },
  { id: 'k-5', slug: 'lame_chakra', name: 'Lame de chakra', category: 'Taijutsu', status: 'DRAFT', updatedAt: avant(50 * MIN) },
];

// ── Annonces Discord ──────────────────────────────────────────────────

export const MESSAGES_DISCORD: StaffDiscordMessage[] = [
  {
    id: 'd-1',
    canal: 'dev-updates',
    statut: 'publie',
    titre: 'Mise à jour du 23 octobre',
    couleur: '#6A2177',
    blocs: [
      { texte: '## Mise à jour du 23 octobre\nMenu R avec accord, masques, HUD n° 4 et images par seconde débridées.' },
      { separateur: true },
      { texte: '- Porter et fouiller sur demande acceptée\n- Un masque porté cache l’identité' },
    ],
    ping: 'aucun',
    discordUrl: null,
    discordMessageId: null,
    auteur: MOI_PSEUDO,
    modifiePar: null,
    creeLe: avant(2 * J + 6 * H),
    modifieLe: avant(2 * J + 5 * H),
    publieLe: avant(2 * J + 5 * H),
    retireLe: null,
    medias: {},
  },
  {
    id: 'd-2',
    canal: 'annonces',
    statut: 'brouillon',
    titre: 'Ouverture des candidatures',
    couleur: '#6A2177',
    blocs: [{ texte: '## Ouverture des candidatures\nLa candidature se fait depuis le launcher.' }],
    ping: 'aucun',
    discordUrl: null,
    discordMessageId: null,
    auteur: 'Raijin',
    modifiePar: MOI_PSEUDO,
    creeLe: avant(J),
    modifieLe: avant(5 * H),
    publieLe: null,
    retireLe: null,
    medias: {},
  },
];

// ── Messages (helper) ─────────────────────────────────────────────────

export function messagesDe(
  prefixe: string,
  liste: Array<{ auteur: 'USER' | 'STAFF' | 'SYSTEM'; nom: string | null; texte: string; ilYa: number }>,
): AdminMessage[] {
  return liste.map((m, i) => ({
    id: `${prefixe}-m${i}`,
    authorType: m.auteur,
    authorId: m.nom ? (COMPTES.find((c) => c.pseudo === m.nom)?.id ?? null) : null,
    authorName: m.nom,
    content: m.texte,
    createdAt: avant(m.ilYa),
  }));
}
