import { AppStatus, Role } from '@prisma/client';
import {
  calculerSuivi,
  debutLendemainParis,
  DELAI_NOUVELLE_CANDIDATURE_MS,
  GARDE_VOIX_MS as GARDE_VOIX_CANDIDATURE_MS,
  heureCourteParis,
  type PhaseCandidature,
} from '../whitelist/whitelist.rules';

/**
 * Règles pures de la douane (docs/gameplay/CANDIDATURE_ET_DOUANE.md §1.2,
 * §3.3, §7). Aucune dépendance à Prisma ni à Nest : testées seules, et
 * réutilisées par le service des séances, la décision d'oral et, plus tard,
 * les routes du jeu (lot B).
 *
 * La phase, les délais (refus, garde de la voix) et le « lendemain » viennent
 * de la candidature (whitelist/whitelist.rules.ts) : une seule règle pour
 * GET /whitelist/me, le panel et la douane. whitelist.rules.ts n'importe rien
 * de la douane : pas d'import circulaire.
 */

// ──────────────────────────── Constantes ────────────────────────────

/** Ouverture de la fenêtre d'entrée : 15 min avant l'heure de convocation H. */
export const AVANCE_ENTREE_MIN = 15;
/** Retard maximal après H (règle unique, Q5). Réglable par DOUANE_RETARD_MAX_MIN. */
export const RETARD_MAX_DEFAUT_MIN = 30;
/** Bornes du réglage du retard maximal. */
export const RETARD_MAX_BORNES_MIN = { min: 5, max: 120 } as const;
/** Pas des vagues de convocation d'une séance longue. */
export const VAGUE_MIN = 30;
/** Jusqu'à cette durée, toute la séance est convoquée à son début. */
export const SEANCE_COURTE_MAX_MIN = 60;
/**
 * Une vague déjà passée reste attribuable tant qu'il reste au moins ce temps
 * de fenêtre : on ne convoque pas quelqu'un dont l'entrée ferme dans 2 min.
 */
export const MARGE_RESERVATION_MIN = 15;
/** Durée d'une séance : de 15 min à 8 h. */
export const DUREE_SEANCE_BORNES_MIN = { min: 15, max: 480 } as const;
/** Capacité d'une séance. */
export const CAPACITE_BORNES = { min: 1, max: 100 } as const;
/** Repère de dimensionnement (Q5) : 4 candidats par heure et par examinateur. */
export const CANDIDATS_PAR_HEURE_PAR_EXAMINATEUR = 4;
/** Refus (écrit ou oral) : nouvelle candidature complète 1 jour plus tard (Q7). Même valeur que la candidature. */
export const DELAI_RECANDIDATURE_MS = DELAI_NOUVELLE_CANDIDATURE_MS;
/** Voix gardée 30 jours après la décision finale (Q2). Même valeur que la candidature. */
export const GARDE_VOIX_MS = GARDE_VOIX_CANDIDATURE_MS;
/** Le « lendemain » se compte à l'heure de Paris, pas en UTC. */
export const FUSEAU = 'Europe/Paris';

const MIN = 60 * 1000;

// ──────────────────────────── Fenêtre d'entrée ────────────────────────────

export type EtatFenetre = 'A_VENIR' | 'OUVERTE' | 'FERMEE';

export interface FenetreEntree {
  ouvreAt: Date;
  fermeAt: Date;
}

/** Fenêtre d'entrée de la règle unique : de H − 15 min à H + retard maximal. */
export function fenetreEntree(
  convocationAt: Date,
  retardMaxMin: number = RETARD_MAX_DEFAUT_MIN,
): FenetreEntree {
  const h = convocationAt.getTime();
  return {
    ouvreAt: new Date(h - AVANCE_ENTREE_MIN * MIN),
    fermeAt: new Date(h + retardMaxMin * MIN),
  };
}

export function etatFenetre(
  convocationAt: Date,
  maintenant: Date,
  retardMaxMin: number = RETARD_MAX_DEFAUT_MIN,
): EtatFenetre {
  const f = fenetreEntree(convocationAt, retardMaxMin);
  const t = maintenant.getTime();
  if (t < f.ouvreAt.getTime()) return 'A_VENIR';
  if (t > f.fermeAt.getTime()) return 'FERMEE';
  return 'OUVERTE';
}

/** Lit DOUANE_RETARD_MAX_MIN ; valeur absente ou hors bornes → 30 min. */
export function lireRetardMax(
  brut: string | number | undefined | null,
): number {
  const n =
    typeof brut === 'number' ? brut : Number.parseInt(String(brut ?? ''), 10);
  if (!Number.isFinite(n)) return RETARD_MAX_DEFAUT_MIN;
  if (n < RETARD_MAX_BORNES_MIN.min || n > RETARD_MAX_BORNES_MIN.max) {
    return RETARD_MAX_DEFAUT_MIN;
  }
  return Math.round(n);
}

// ──────────────────────────── Vagues et convocation ────────────────────────────

/**
 * Heures de convocation d'une séance (§3.3). Jusqu'à 1 h : tout le monde au
 * début. Au-delà : une vague toutes les 30 min, la dernière au moins 30 min
 * avant la fin. Même calcul que la maquette L9 (`vagues` dans
 * apps/launcher/src/dev/maquettes/candidature/donnees.ts).
 */
export function vaguesConvocation(startAt: Date, endAt: Date): Date[] {
  const debut = startAt.getTime();
  const dureeMin = (endAt.getTime() - debut) / MIN;
  if (dureeMin <= SEANCE_COURTE_MAX_MIN) return [new Date(debut)];
  const vagues: Date[] = [];
  for (let t = 0; t < dureeMin - VAGUE_MIN; t += VAGUE_MIN) {
    vagues.push(new Date(debut + t * MIN));
  }
  return vagues;
}

/**
 * Heure de convocation attribuée à une nouvelle réservation : la première
 * vague encore ouverte qui n'a pas sa part (capacité / nombre de vagues).
 * Si toutes ont leur part, la moins remplie (la capacité totale, contrôlée
 * à part, autorise ce débordement). `null` : plus aucune vague attribuable,
 * la séance est trop avancée pour réserver.
 *
 * @param prises heures de convocation des places déjà prises (BOOKED, DONE)
 */
export function attribuerConvocation(
  seance: { startAt: Date; endAt: Date; capacite: number },
  prises: Date[],
  maintenant: Date,
  retardMaxMin: number = RETARD_MAX_DEFAUT_MIN,
): Date | null {
  const vagues = vaguesConvocation(seance.startAt, seance.endAt);
  const parVague = Math.max(1, Math.ceil(seance.capacite / vagues.length));
  const compte = new Map<number, number>();
  for (const p of prises)
    compte.set(p.getTime(), (compte.get(p.getTime()) ?? 0) + 1);
  const nb = (v: Date) => compte.get(v.getTime()) ?? 0;

  const margeMin = Math.min(MARGE_RESERVATION_MIN, retardMaxMin);
  const limite = maintenant.getTime() - (retardMaxMin - margeMin) * MIN;
  const ouvertes = vagues.filter((v) => v.getTime() >= limite);
  if (ouvertes.length === 0) return null;

  const libre = ouvertes.find((v) => nb(v) < parVague);
  if (libre) return libre;
  return ouvertes.reduce((a, b) => (nb(b) < nb(a) ? b : a));
}

/** Capacité conseillée (Q5) : examinateurs × heures × 4. `null` sans examinateur. */
export function capaciteConseillee(
  startAt: Date,
  endAt: Date,
  nbExaminateurs: number,
): number | null {
  if (nbExaminateurs <= 0) return null;
  const heures = (endAt.getTime() - startAt.getTime()) / (60 * MIN);
  return Math.max(
    1,
    Math.round(nbExaminateurs * heures * CANDIDATS_PAR_HEURE_PAR_EXAMINATEUR),
  );
}

// ──────────────────────────── Phase (§1.2) ────────────────────────────

export type { PhaseCandidature };

/** Phases où le candidat peut prendre une place (§3.3 : écrit accepté, ou oral à revoir). */
export const PHASES_RESERVABLES: readonly PhaseCandidature[] = [
  'DOUANE_A_RESERVER',
  'DOUANE_A_REVOIR',
];

/** Phases où l'oral peut être tranché hors jeu (carte « Oral », §3.11, Q9). */
export const PHASES_ORAL: readonly PhaseCandidature[] = [
  'DOUANE_A_RESERVER',
  'DOUANE_RESERVEE',
  'DOUANE_A_REVOIR',
];

export interface EntreePhase {
  role: Role;
  oralPassedAt: Date | null;
  /**
   * Refus encore en cours sur le compte : sans candidature (retrait après un
   * refus), la phase reste REFUSE tant que le délai court.
   */
  whitelistRejectedAt?: Date | null;
  application: {
    status: AppStatus;
    hrpStatus: AppStatus;
    rpStatus: AppStatus;
    oralStatus: AppStatus;
    /** Pour information : l'écrit compte comme accepté quand HRP et RP sont validés (la décision pose cette date avec). */
    writtenApprovedAt: Date | null;
    oralDecidedAt?: Date | null;
    reviewedAt?: Date | null;
  } | null;
  /** Une réservation BOOKED existe pour ce compte. */
  reservationActive: boolean;
}

/** Place réservée dont seule l'existence compte pour la phase. */
const PLACE_SANS_DETAIL = { id: '', seanceId: '', convocationAt: new Date(0) };

/**
 * Phase du parcours (§1.2), lue au lieu du rôle. Délègue à `calculerSuivi`
 * (whitelist.rules.ts), la règle de GET /whitelist/me et du panel : la douane
 * et le launcher voient toujours la même phase. Exemple de cas qui divergeait :
 * un dossier migré dont l'ancien oral est validé mais le RP encore à relire
 * reste ECRIT_EN_ATTENTE, pas ACCEPTE.
 *
 * Une place BOOKED compte ici tant qu'elle l'est (décision d'oral, panel) ; la
 * vue du candidat, elle, appelle `calculerSuivi` avec `placeFermeeManquee`.
 */
export function calculerPhase(e: EntreePhase): PhaseCandidature {
  const app = e.application;
  return calculerSuivi({
    role: e.role,
    oralPassedAt: e.oralPassedAt,
    whitelistRejectedAt: e.whitelistRejectedAt ?? null,
    app: app
      ? {
          status: app.status,
          hrpStatus: app.hrpStatus,
          rpStatus: app.rpStatus,
          oralStatus: app.oralStatus,
          oralDecidedAt: app.oralDecidedAt ?? null,
          reviewedAt: app.reviewedAt ?? null,
        }
      : null,
    reservation: e.reservationActive ? PLACE_SANS_DETAIL : null,
    now: new Date(),
  }).phase;
}

/** REBORN_DOUANE_ENABLED et réglages du même genre : « true », « 1 », « oui » ou « yes ». */
export function estActif(raw: string | undefined | null): boolean {
  const v = raw?.trim().toLowerCase();
  return v === 'true' || v === '1' || v === 'oui' || v === 'yes';
}

/** Pourquoi le candidat ne peut pas réserver, selon sa phase (texte montré tel quel). */
export function raisonPhase(phase: PhaseCandidature): string {
  switch (phase) {
    case 'BROUILLON':
      return "Envoie d'abord ta candidature écrite.";
    case 'ECRIT_EN_ATTENTE':
      return "Ton écrit est en cours de relecture : la douane se réserve une fois l'écrit accepté.";
    case 'ECRIT_A_REVOIR':
      return 'Ton écrit est à revoir : corrige-le et renvoie-le avant de réserver la douane.';
    case 'DOUANE_RESERVEE':
      return 'Tu as déjà une place réservée.';
    case 'REFUSE':
      return 'Ta candidature a été refusée : une nouvelle candidature complète sera possible après le délai.';
    case 'ACCEPTE':
      return 'Tu es déjà accepté : pas de douane à passer.';
    default:
      return '';
  }
}

// ──────────────────────────── Rôles ────────────────────────────

/**
 * Rôles web qui peuvent trancher un oral : WHITELIST_REVIEWER ou plus, dans
 * l'ordre de `RolesGuard` (MODERATOR y est au-dessus du relecteur).
 */
const ROLES_EXAMINATEUR: readonly Role[] = [
  Role.WHITELIST_REVIEWER,
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];

export function estExaminateur(role: Role): boolean {
  return ROLES_EXAMINATEUR.includes(role);
}

// ──────────────────────────── Heure de Paris ────────────────────────────

/**
 * Minuit du lendemain, heure de Paris. « À revoir » : nouvelle séance dès le
 * lendemain (Q7), pas le soir même. Même calcul que `reservableDes` dans
 * GET /whitelist/me (whitelist.rules.ts › debutLendemainParis).
 */
export function debutDuLendemainParis(d: Date): Date {
  return debutLendemainParis(d);
}

const FORMAT_JOUR = new Intl.DateTimeFormat('fr-FR', {
  timeZone: FUSEAU,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

/**
 * « 21 h », « 21 h 30 » ou « 9 h 05 », heure de Paris (messages système). Même
 * format que la candidature et le jeton de jeu (whitelist.rules.ts ›
 * heureCourteParis), comme les maquettes L9 et L10.
 */
export function heureParis(d: Date): string {
  return heureCourteParis(d);
}

/** « lundi 26 octobre », heure de Paris (messages système). */
export function jourParis(d: Date): string {
  return FORMAT_JOUR.format(d);
}
