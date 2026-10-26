import { AppStatus, Role } from '@prisma/client';
import {
  AVANCE_ENTREE_MIN,
  etatFenetre,
  fenetreEntree,
} from '../douane/douane.regles';
import { formatConvocation } from '../play/douane-fenetre';
import {
  calculerSuivi,
  type PhaseCandidature,
} from '../whitelist/whitelist.rules';

/**
 * Aiguillage du serveur de jeu (CANDIDATURE_ET_DOUANE.md §3.3, §3.10, §3.12) :
 * à la connexion, ShinobiCore demande `GET /v1/game/acces/:mcUuid` et envoie le
 * joueur en sélection (`play`), au hall de la douane (`douane`) ou dehors
 * (`aucune`).
 *
 * Règle pure, sans Prisma ni Nest. Elle reprend, dans le même ordre et avec les
 * mêmes textes, les contrôles du jeton de jeu (`play.service.ts` ›
 * `issuePlayToken` et `assertDouaneAccess`) : le serveur et Guardian voient la
 * même portée. `issuePlayToken` pourra se brancher dessus (phase calculée à un
 * seul endroit).
 *
 * Un seul ajout par rapport au jeton : la portée `douane` exige aussi la phase
 * DOUANE_RESERVEE, celle que la décision d'oral revérifie (`decideOral`). Un
 * candidat admis au hall peut donc toujours être tranché.
 */

export type PorteeJeu = 'play' | 'douane' | 'aucune';

/** Codes stables, lus par le plugin (le texte, lui, peut changer). */
export type RaisonAcces =
  /** Compte staff (HELPER ou plus, modélisateur, développeur) : sélection, jamais la douane. */
  | 'STAFF'
  /** Compte WHITELISTED : sélection. */
  | 'ACCEPTE'
  /** Compte PLAYER, place réservée, fenêtre d'entrée ouverte, douane active : hall. */
  | 'DOUANE_FENETRE_OUVERTE'
  /** Aucun compte Reborn pour cet UUID Minecraft. */
  | 'COMPTE_INCONNU'
  | 'BANNI'
  /** Pas (ou plus) de candidature qui mène à la douane : brouillon, écrit en attente ou à revoir, refus. */
  | 'CANDIDATURE_REQUISE'
  /** Candidature acceptée mais rôle encore PLAYER : incohérence à corriger par le staff. */
  | 'COMPTE_PAS_A_JOUR'
  /** REBORN_DOUANE_ENABLED coupé. */
  | 'DOUANE_COUPEE'
  /** Écrit accepté, aucune place réservée. */
  | 'DOUANE_A_RESERVER'
  /** Place réservée, entrée pas encore ouverte (avant H − 15 min). */
  | 'DOUANE_A_VENIR'
  /** Place réservée, entrée fermée (après H + retard maximal). */
  | 'DOUANE_FENETRE_PASSEE';

/**
 * Rôles staff : choix libres en jeu, jamais la douane (§3.10). Même liste que
 * le wizard de création (`CandidatureView.staff`).
 */
export const ROLES_STAFF: ReadonlySet<Role> = new Set<Role>([
  Role.HELPER,
  Role.MODELISATEUR,
  Role.DEVELOPPEUR,
  Role.MODERATOR,
  Role.WHITELIST_REVIEWER,
  Role.ADMIN,
  Role.OWNER,
]);

// Textes : les mêmes que le jeton de jeu (play.service.ts), montrés au joueur expulsé.
export const MSG_CANDIDATURE_REQUISE =
  'Ta candidature doit être acceptée avant de rejoindre le serveur.';
export const MSG_COMPTE_PAS_A_JOUR =
  "Ton accès est validé mais ton compte n'est pas à jour : ouvre un ticket.";
export const MSG_BANNI =
  'Ton compte est banni : tu ne peux pas rejoindre le serveur.';
export const MSG_COMPTE_INCONNU =
  'Compte Reborn introuvable : lance le jeu depuis le launcher Reborn.';
export const MSG_DOUANE_A_RESERVER =
  "Ton écrit est accepté : réserve ta séance de douane dans le launcher pour passer l'oral.";
export const MSG_DOUANE_FENETRE_PASSEE =
  "L'heure d'entrée de ta séance de douane est passée : réserve une nouvelle séance dans le launcher.";

export interface EntreeAcces {
  /** null : aucun compte Reborn lié à cet UUID Minecraft. */
  compte: {
    role: Role;
    banned: boolean;
    oralPassedAt: Date | null;
    whitelistRejectedAt: Date | null;
  } | null;
  candidature: {
    status: AppStatus;
    hrpStatus: AppStatus;
    rpStatus: AppStatus;
    oralStatus: AppStatus;
    oralDecidedAt: Date | null;
    reviewedAt: Date | null;
    writtenApprovedAt: Date | null;
  } | null;
  /**
   * Place BOOKED de cette candidature dans une séance OUVERTE ou FERMEE, la plus
   * proche (même requête que le jeton de jeu).
   */
  reservation: {
    id: string;
    seanceId: string;
    convocationAt: Date;
    presentAt?: Date | null;
  } | null;
  maintenant: Date;
  /** REBORN_DOUANE_ENABLED. */
  douaneActive: boolean;
  /** DOUANE_RETARD_MAX_MIN, lu par `lireRetardMax`. */
  retardMaxMin: number;
}

export interface AccesJeu {
  portee: PorteeJeu;
  raison: RaisonAcces;
  /** Texte d'expulsion pour `aucune` ; null sinon. */
  message: string | null;
  /** Phase du parcours (§1.2), comme le launcher la voit ; null sans compte. */
  phase: PhaseCandidature | null;
  /** true pour un compte staff. */
  staff: boolean;
}

/** Portée d'un compte au moment de sa connexion. */
export function evaluerAcces(e: EntreeAcces): AccesJeu {
  const compte = e.compte;
  if (!compte) {
    return refus('COMPTE_INCONNU', MSG_COMPTE_INCONNU, null, false);
  }
  const staff = ROLES_STAFF.has(compte.role);
  const phase = calculerSuivi({
    role: compte.role,
    oralPassedAt: compte.oralPassedAt,
    whitelistRejectedAt: compte.whitelistRejectedAt,
    app: e.candidature,
    reservation: e.reservation,
    now: e.maintenant,
    retardMaxMin: e.retardMaxMin,
    // Vue du launcher : une place dont l'entrée est fermée, sans arrivée, compte déjà comme manquée.
    placeFermeeManquee: true,
    douaneEnJeu: e.douaneActive,
  }).phase;

  if (compte.banned) return refus('BANNI', MSG_BANNI, phase, staff);

  if (compte.role !== Role.PLAYER) {
    return {
      portee: 'play',
      raison: staff ? 'STAFF' : 'ACCEPTE',
      message: null,
      phase,
      staff,
    };
  }

  // Compte PLAYER : seule la douane peut l'admettre. Même ordre que le jeton.
  if (compte.oralPassedAt) {
    return phase === 'ACCEPTE'
      ? refus('COMPTE_PAS_A_JOUR', MSG_COMPTE_PAS_A_JOUR, phase, staff)
      : refus('CANDIDATURE_REQUISE', MSG_CANDIDATURE_REQUISE, phase, staff);
  }
  if (!e.douaneActive) {
    return refus('DOUANE_COUPEE', MSG_CANDIDATURE_REQUISE, phase, staff);
  }

  const app = e.candidature;
  const ecritAccepte =
    !!app &&
    !!app.writtenApprovedAt &&
    app.status !== AppStatus.REJECTED &&
    app.oralStatus !== AppStatus.REJECTED &&
    app.oralStatus !== AppStatus.APPROVED;
  if (!ecritAccepte) {
    return refus('CANDIDATURE_REQUISE', MSG_CANDIDATURE_REQUISE, phase, staff);
  }

  const place = e.reservation;
  if (!place) {
    return refus('DOUANE_A_RESERVER', MSG_DOUANE_A_RESERVER, phase, staff);
  }
  const etat = etatFenetre(place.convocationAt, e.maintenant, e.retardMaxMin);
  if (etat === 'A_VENIR') {
    return refus(
      'DOUANE_A_VENIR',
      `Ta séance de douane est prévue ${formatConvocation(place.convocationAt)}. ` +
        `L'entrée ouvre ${AVANCE_ENTREE_MIN} min avant.`,
      phase,
      staff,
    );
  }
  if (etat === 'FERMEE') {
    return refus(
      'DOUANE_FENETRE_PASSEE',
      MSG_DOUANE_FENETRE_PASSEE,
      phase,
      staff,
    );
  }
  if (phase !== 'DOUANE_RESERVEE') {
    // Écrit rouvert après coup (date d'écrit restée posée) : la décision serait refusée.
    return refus('CANDIDATURE_REQUISE', MSG_CANDIDATURE_REQUISE, phase, staff);
  }
  return {
    portee: 'douane',
    raison: 'DOUANE_FENETRE_OUVERTE',
    message: null,
    phase,
    staff,
  };
}

/** Fenêtre d'entrée d'une place, en ISO (ouverture, fermeture). */
export function fenetreIso(
  convocationAt: Date,
  retardMaxMin: number,
): { entreeOuvreAt: string; entreeFermeAt: string } {
  const f = fenetreEntree(convocationAt, retardMaxMin);
  return {
    entreeOuvreAt: f.ouvreAt.toISOString(),
    entreeFermeAt: f.fermeAt.toISOString(),
  };
}

function refus(
  raison: RaisonAcces,
  message: string,
  phase: PhaseCandidature | null,
  staff: boolean,
): AccesJeu {
  return { portee: 'aucune', raison, message, phase, staff };
}
