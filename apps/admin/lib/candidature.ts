/**
 * Libellés et formats de la candidature v2 et de la douane, côté panel
 * (CANDIDATURE_ET_DOUANE.md §1.2, §2.3, §3.3). Les codes viennent de l'API
 * (listes fermées ASCII) ; les libellés sont les mêmes que ceux du launcher
 * (apps/api/src/whitelist/whitelist.rules.ts › LIBELLES).
 */

import type {
  AppStatus,
  CharacterSex,
  CharacterType,
  DiscoverySource,
  DouaneReservationStatut,
  DouaneSeanceStatut,
  EtatFenetre,
  NarutoKnowledge,
  OralVia,
  PhaseCandidature,
  RpYears,
} from './types';

// ──────────────────────────── Listes fermées ────────────────────────────

export const LIBELLE_RP_YEARS: Record<RpYears, string> = {
  JAMAIS: 'Jamais',
  MOINS_D_UN_AN: "Moins d'un an",
  UN_A_DEUX_ANS: '1 à 2 ans',
  TROIS_A_CINQ_ANS: '3 à 5 ans',
  SIX_ANS_ET_PLUS: '6 ans et plus',
};

export const LIBELLE_NARUTO: Record<NarutoKnowledge, string> = {
  DEBUTANT: 'Débutant',
  QUELQUES_ARCS: 'Quelques arcs',
  BONNE_CONNAISSANCE: 'Bonne connaissance',
  INCOLLABLE: 'Incollable, Boruto compris',
};

export const LIBELLE_DECOUVERTE: Record<DiscoverySource, string> = {
  DISCORD: 'Discord',
  TIKTOK: 'TikTok',
  YOUTUBE: 'YouTube',
  AMI: 'Un ami',
  AUTRE_SERVEUR: 'Un autre serveur',
  RECHERCHE: 'Une recherche',
  AUTRE: 'Autre',
};

export const LIBELLE_TYPE_PERSO: Record<CharacterType, string> = {
  NINJA: 'Ninja',
  CIVIL: 'Civil',
};

export const LIBELLE_SEXE: Record<CharacterSex, string> = {
  HOMME: 'Homme',
  FEMME: 'Femme',
};

/** Tranche d'âge du personnage, bornes du jeu (§2.3). */
export function trancheAge(age: number): string {
  if (age <= 12) return 'Académie';
  if (age <= 16) return 'Jeune genin';
  return 'Adulte';
}

// ──────────────────────────── Statuts ────────────────────────────

/**
 * Ton d'un statut de la candidature. Converti en pastille v2 par
 * components/candidature/ui.tsx (accent → info, warning → attention…).
 */
export type Ton = 'accent' | 'warning' | 'success' | 'danger' | 'muted';

/** Une partie (Écrit HRP, Écrit RP) ou l'oral. */
export const STATUT_PARTIE: Record<AppStatus, { label: string; ton: Ton }> = {
  PENDING: { label: 'À relire', ton: 'accent' },
  APPROVED: { label: 'Validé', ton: 'success' },
  NEEDS_REVISION: { label: 'À revoir', ton: 'warning' },
  REJECTED: { label: 'Refusé', ton: 'danger' },
};

/** Statut global de la candidature (APPROVED seulement après l'oral). */
export const STATUT_GLOBAL: Record<AppStatus, { label: string; ton: Ton }> = {
  PENDING: { label: 'En cours', ton: 'accent' },
  APPROVED: { label: 'Acceptée', ton: 'success' },
  NEEDS_REVISION: { label: 'À revoir', ton: 'warning' },
  REJECTED: { label: 'Refusée', ton: 'danger' },
};

export const PHASE: Record<PhaseCandidature, { label: string; ton: Ton }> = {
  BROUILLON: { label: 'Brouillon', ton: 'muted' },
  ECRIT_EN_ATTENTE: { label: 'Écrit à relire', ton: 'accent' },
  ECRIT_A_REVOIR: { label: 'Écrit à revoir', ton: 'warning' },
  DOUANE_A_RESERVER: { label: 'Douane à réserver', ton: 'accent' },
  DOUANE_RESERVEE: { label: 'Douane réservée', ton: 'accent' },
  DOUANE_A_REVOIR: { label: 'Douane à revoir', ton: 'warning' },
  REFUSE: { label: 'Refusée', ton: 'danger' },
  ACCEPTE: { label: 'Acceptée', ton: 'success' },
};

/** Phases où la carte « Oral hors jeu » peut trancher (douane.regles.ts › PHASES_ORAL). */
export const PHASES_ORAL: readonly PhaseCandidature[] = [
  'DOUANE_A_RESERVER',
  'DOUANE_RESERVEE',
  'DOUANE_A_REVOIR',
];

export const LIBELLE_VIA: Record<OralVia, string> = {
  DOUANE: 'En jeu (douane)',
  HORS_JEU: 'Hors jeu',
};

export const STATUT_SEANCE: Record<
  DouaneSeanceStatut,
  { label: string; ton: Ton }
> = {
  OUVERTE: { label: 'Ouverte', ton: 'accent' },
  FERMEE: { label: 'Réservations fermées', ton: 'warning' },
  TERMINEE: { label: 'Close', ton: 'success' },
  ANNULEE: { label: 'Annulée', ton: 'muted' },
};

export const STATUT_RESERVATION: Record<
  DouaneReservationStatut,
  { label: string; ton: Ton }
> = {
  BOOKED: { label: 'Réservée', ton: 'accent' },
  DONE: { label: 'Passée', ton: 'success' },
  MISSED: { label: 'Manquée', ton: 'danger' },
  CANCELLED: { label: 'Annulée', ton: 'muted' },
};

export const ETAT_FENETRE: Record<EtatFenetre, string> = {
  A_VENIR: 'Entrée pas encore ouverte',
  OUVERTE: 'Entrée ouverte',
  FERMEE: 'Entrée fermée',
};

/** Résultat d'un passage (« Accepté », « À revoir », « Refusé »). */
export const RESULTAT_ORAL: Record<AppStatus, { label: string; ton: Ton }> = {
  PENDING: { label: 'En attente', ton: 'accent' },
  APPROVED: { label: 'Accepté', ton: 'success' },
  NEEDS_REVISION: { label: 'À revoir', ton: 'warning' },
  REJECTED: { label: 'Refusé', ton: 'danger' },
};

/**
 * État de l'oral pour la colonne « Oral » de la liste et l'en-tête de la
 * fiche : tient compte de l'oral déjà passé sur le compte et de la place
 * réservée.
 */
export function etatOral(e: {
  oralStatus: AppStatus;
  oralRequis: boolean;
  phase: PhaseCandidature;
  convocationAt?: string | null;
}): { label: string; ton: Ton; detail: string | null } {
  if (e.oralStatus === 'APPROVED') {
    return { label: 'Validé', ton: 'success', detail: null };
  }
  if (e.oralStatus === 'REJECTED') {
    return { label: 'Refusé', ton: 'danger', detail: null };
  }
  if (!e.oralRequis) {
    return { label: 'Déjà passé', ton: 'success', detail: 'oral réussi sur ce compte' };
  }
  if (e.phase === 'DOUANE_RESERVEE') {
    return {
      label: 'Réservé',
      ton: 'accent',
      detail: e.convocationAt
        ? `${jourCourt(e.convocationAt)} · ${heureCourte(e.convocationAt)}`
        : null,
    };
  }
  if (e.phase === 'DOUANE_A_REVOIR' || e.oralStatus === 'NEEDS_REVISION') {
    return { label: 'À revoir', ton: 'warning', detail: null };
  }
  if (e.phase === 'DOUANE_A_RESERVER') {
    return { label: 'À réserver', ton: 'accent', detail: null };
  }
  return { label: '—', ton: 'muted', detail: "après l'écrit" };
}

// ──────────────────────────── Heure de Paris ────────────────────────────

/**
 * Fuseau de toutes les heures de la candidature et de la douane : l'API
 * (douane.regles.ts › FUSEAU), les messages système et le jeton de jeu sont en
 * heure de Paris. Le panel affiche et saisit dans ce fuseau, quel que soit
 * celui du navigateur du staff.
 */
export const FUSEAU = 'Europe/Paris';

const PARTIES_PARIS = new Intl.DateTimeFormat('en-GB', {
  timeZone: FUSEAU,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

/** Date et heure de Paris d'un instant (même calcul que whitelist.rules.ts › partiesParis). */
export function partiesParis(d: Date): {
  an: number;
  mois: number;
  jour: number;
  h: number;
  mi: number;
  s: number;
} {
  const parts = PARTIES_PARIS.formatToParts(d);
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

/**
 * Instant correspondant à une date et une heure lues à Paris. Le jour peut
 * déborder (jour + 1 = lendemain, Date.UTC normalise). Deux passes : le
 * décalage peut changer autour d'un changement d'heure.
 */
export function instantParis(
  an: number,
  mois: number,
  jour: number,
  h: number,
  mi: number,
): Date {
  const mur = Date.UTC(an, mois - 1, jour, h, mi, 0);
  let t = mur - decalageParis(new Date(mur));
  t = mur - decalageParis(new Date(t));
  return new Date(t);
}

// ──────────────────────────── Formats ────────────────────────────

const FORMAT_JOUR_LONG = new Intl.DateTimeFormat('fr-FR', {
  timeZone: FUSEAU,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

const FORMAT_JOUR_COURT = new Intl.DateTimeFormat('fr-FR', {
  timeZone: FUSEAU,
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});

const FORMAT_JOUR_MOIS = new Intl.DateTimeFormat('fr-FR', {
  timeZone: FUSEAU,
  day: 'numeric',
  month: 'short',
});

const FORMAT_JOUR_MOIS_AN = new Intl.DateTimeFormat('fr-FR', {
  timeZone: FUSEAU,
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});

const FORMAT_DATE = new Intl.DateTimeFormat('fr-FR', {
  timeZone: FUSEAU,
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});

/** « 21 h », « 21 h 30 », « 9 h 05 » (heure de Paris, comme les messages aux candidats). */
export function heureCourte(iso: string | Date): string {
  const d = typeof iso === 'string' ? new Date(iso) : iso;
  const { h, mi } = partiesParis(d);
  // Espaces insécables : « 22 h 30 » ne se coupe jamais en fin de ligne.
  return mi === 0 ? `${h}\u00a0h` : `${h}\u00a0h\u00a0${String(mi).padStart(2, '0')}`;
}

/**
 * « ce soir », « aujourd’hui », « demain » ou « samedi 31 octobre » (date de
 * Paris) : pour un rendez-vous proche, comme une convocation à la douane.
 */
export function jourProche(iso: string | Date): string {
  const d = typeof iso === 'string' ? new Date(iso) : iso;
  const x = partiesParis(d);
  const ici = partiesParis(new Date());
  const demain = partiesParis(new Date(Date.now() + 86_400_000));
  if (x.an === ici.an && x.mois === ici.mois && x.jour === ici.jour) {
    return x.h >= 18 ? 'ce soir' : 'aujourd’hui';
  }
  if (x.an === demain.an && x.mois === demain.mois && x.jour === demain.jour) return 'demain';
  return jourLong(d);
}

/** « samedi 31 octobre » (date de Paris). */
export function jourLong(iso: string | Date): string {
  const d = typeof iso === 'string' ? new Date(iso) : iso;
  return FORMAT_JOUR_LONG.format(d);
}

/** « sam. 31 oct. » (date de Paris). */
export function jourCourt(iso: string | Date): string {
  const d = typeof iso === 'string' ? new Date(iso) : iso;
  return FORMAT_JOUR_COURT.format(d);
}

/**
 * « 31 oct., 21 h 30 » (heure de Paris, même écriture des heures que le
 * reste du panel) ; l'année n'apparaît que si ce n'est pas l'année en cours.
 */
export function dateHeure(iso: string): string {
  const d = new Date(iso);
  const memeAnnee = partiesParis(d).an === partiesParis(new Date()).an;
  return `${(memeAnnee ? FORMAT_JOUR_MOIS : FORMAT_JOUR_MOIS_AN).format(d)}, ${heureCourte(d)}`;
}

/** « 0:42 ». */
export function dureeAudio(sec: number): string {
  const s = Math.max(0, Math.round(sec));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** « il y a 3 h », « il y a 2 j »… */
export function ilYa(iso: string): string {
  const minutes = Math.floor((Date.now() - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return "à l'instant";
  if (minutes < 60) return `il y a ${minutes} min`;
  const heures = Math.floor(minutes / 60);
  if (heures < 24) return `il y a ${heures} h`;
  const jours = Math.floor(heures / 24);
  if (jours < 30) return `il y a ${jours} j`;
  return FORMAT_DATE.format(new Date(iso));
}

/** Nombre de mots d'un texte (indicatif, comme le compteur du launcher). */
export function compterMots(texte: string): number {
  const t = texte.trim();
  return t.length === 0 ? 0 : t.split(/\s+/u).length;
}

const NF = new Intl.NumberFormat('fr-FR');
export const nombre = (n: number): string => NF.format(n);

/**
 * Nom affiché d'un compte staff ou candidat : le pseudo Minecraft, celui du
 * jeu et du reste du panel (« Pris par », journal), pas le pseudo Discord.
 */
export function nomCompte(c: {
  minecraftUsername: string;
  discordUsername: string | null;
}): string {
  return c.minecraftUsername;
}
