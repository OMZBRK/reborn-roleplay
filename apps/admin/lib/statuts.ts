/**
 * Libellé + ton de pastille par statut (PANEL_V2 §5.4, dictionnaire central).
 *
 * Règle des pastilles : par défaut « point + mot », sans fond. Teintée
 * (`teinte: true`) seulement quand le staff doit agir (info) ou que c'est
 * tranché (succès, danger) — une seule par ligne. L'or n'est jamais un statut ;
 * l'ambre (attention) n'est qu'un point à côté d'un mot.
 */

import type {
  AppStatus,
  DouaneReservationStatut,
  DouaneSeanceStatut,
  PhaseCandidature,
  TicketStatus,
} from './types';
import { LIBELLE_STATUT_TICKET } from './libelles';

export type TonBadge = 'neutre' | 'info' | 'succes' | 'attention' | 'danger' | 'vide';

export interface Statut {
  label: string;
  ton: TonBadge;
  /** Pastille teintée (au plus une par ligne). */
  teinte?: boolean;
}

export const STATUT_TICKET: Record<TicketStatus, Statut> = {
  OPEN: { label: LIBELLE_STATUT_TICKET.OPEN, ton: 'attention' },
  IN_PROGRESS: { label: LIBELLE_STATUT_TICKET.IN_PROGRESS, ton: 'info' },
  RESOLVED: { label: LIBELLE_STATUT_TICKET.RESOLVED, ton: 'succes' },
  CLOSED: { label: LIBELLE_STATUT_TICKET.CLOSED, ton: 'neutre' },
};

/**
 * Phase d'une candidature, la seule pastille de sa ligne. Teintée si le
 * staff doit agir (écrit en attente) ou si c'est tranché ; neutre quand
 * l'action est chez le candidat (à revoir, douane à réserver, réservée).
 */
export const STATUT_PHASE: Record<PhaseCandidature, Statut> = {
  BROUILLON: { label: 'Brouillon', ton: 'neutre' },
  ECRIT_EN_ATTENTE: { label: 'Écrit en attente', ton: 'info', teinte: true },
  ECRIT_A_REVOIR: { label: 'Écrit à revoir', ton: 'neutre', teinte: true },
  DOUANE_A_RESERVER: { label: 'Douane à réserver', ton: 'neutre', teinte: true },
  DOUANE_RESERVEE: { label: 'Douane réservée', ton: 'neutre', teinte: true },
  DOUANE_A_REVOIR: { label: 'Douane à revoir', ton: 'neutre', teinte: true },
  REFUSE: { label: 'Refusée', ton: 'danger', teinte: true },
  ACCEPTE: { label: 'Acceptée', ton: 'succes', teinte: true },
};

/** Une partie de l'écrit (HRP, RP) dans le panneau de décision. */
export const STATUT_PARTIE_ECRIT: Record<AppStatus, Statut> = {
  PENDING: { label: 'En attente', ton: 'attention' },
  APPROVED: { label: 'Validé', ton: 'succes' },
  NEEDS_REVISION: { label: 'À revoir', ton: 'attention' },
  REJECTED: { label: 'Refusé', ton: 'danger' },
};

export const STATUT_SEANCE_DOUANE: Record<DouaneSeanceStatut, Statut> = {
  OUVERTE: { label: 'Ouverte', ton: 'info' },
  FERMEE: { label: 'Réservations fermées', ton: 'attention' },
  TERMINEE: { label: 'Close', ton: 'succes' },
  ANNULEE: { label: 'Annulée', ton: 'neutre' },
};

export const STATUT_RESERVATION_DOUANE: Record<DouaneReservationStatut, Statut> = {
  BOOKED: { label: 'Réservée', ton: 'info' },
  DONE: { label: 'Passée', ton: 'succes' },
  MISSED: { label: 'Manquée', ton: 'danger' },
  CANCELLED: { label: 'Annulée', ton: 'neutre' },
};

export const STATUT_RESULTAT_ORAL: Record<AppStatus, Statut> = {
  PENDING: { label: 'En attente', ton: 'info' },
  APPROVED: { label: 'Accepté', ton: 'succes' },
  NEEDS_REVISION: { label: 'À revoir', ton: 'attention' },
  REJECTED: { label: 'Refusé', ton: 'danger' },
};

/** Statut d'un compte dans la liste Joueurs. */
export function statutCompte(c: { role: string; banned: boolean }): Statut {
  if (c.banned) return { label: 'Banni', ton: 'danger' };
  if (c.role === 'PLAYER') return { label: 'Non whitelisté', ton: 'vide' };
  return { label: 'Whitelisté', ton: 'succes' };
}
