import { AppStatus, Role } from '@prisma/client';
import { ROLES_STAFF } from '../game/game-acces.regles';
import type { PhaseCandidature } from '../whitelist/whitelist.rules';

/**
 * Oral à repasser (décision du 01/11, qui amende celle du 19/10) : le staff peut
 * exiger qu'un joueur repasse la douane (candidature de clan, longue absence,
 * essai du parcours avec des comptes de test). Motif obligatoire, montré au
 * joueur et inscrit à l'audit.
 *
 * Aucune règle ne change : le compte est remis dans l'état que toutes les règles
 * lisent déjà comme « écrit accepté, oral à passer » (rôle PLAYER, oralPassedAt
 * effacé, oral PENDING) ; quand, qui et pourquoi vivent à part, sur le compte
 * (User.oralRetake*). Règles pures, sans Prisma ni Nest : le service
 * (StaffService.exigerNouvelOral) et les fiches du panel (`peutExigerOral`)
 * lisent les mêmes.
 */

export const REFUS_ORAL_COMPTE_STAFF =
  "Compte staff : retire d'abord son grade (Changer de grade) pour lui faire passer la douane.";
export const REFUS_ORAL_SANS_CANDIDATURE =
  "Aucune candidature sur ce compte : l'oral à repasser s'exige sur une candidature reçue.";
export const REFUS_ORAL_CANDIDATURE_REFUSEE =
  "Candidature refusée : pas de douane avant une nouvelle candidature. Pour rejouer tout le parcours, écrit puis oral : « Remettre à zéro », case « Refaire aussi l'oral ».";
export const REFUS_ORAL_DEJA_A_PASSER =
  "L'oral est déjà à passer pour ce joueur.";
/**
 * Décision sur l'écrit pendant un oral à repasser : l'écrit déjà accepté reste
 * acquis. Sans ce verrou, exiger l'oral puis refuser l'écrit retirerait la
 * whitelist en deux gestes, hors de la remise à zéro (direction).
 */
export const REFUS_ECRIT_ORAL_A_REPASSER =
  "Oral à repasser en cours : l'écrit reste accepté, seul l'oral se décide (douane, ou « Lever »). Pour rouvrir l'écrit : remise à zéro (direction).";

/**
 * Pourquoi « Exiger un nouvel oral » refuserait ce compte (409), ou null s'il
 * peut être renvoyé à la douane. Le contrôle « son propre compte » (403) reste
 * au service : le panel affiche le bouton d'après ce seul résultat. Un compte
 * banni passe : l'exigence prend effet à la levée du bannissement.
 */
export function refusRepasserOral(
  compte: { role: Role; oralPassedAt: Date | null },
  candidature: {
    status: AppStatus;
    hrpStatus: AppStatus;
    rpStatus: AppStatus;
    oralStatus: AppStatus;
  } | null,
): string | null {
  // Jamais la douane pour le staff (§3.10) : son grade ne se touche pas ici.
  if (ROLES_STAFF.has(compte.role)) return REFUS_ORAL_COMPTE_STAFF;
  if (!candidature) return REFUS_ORAL_SANS_CANDIDATURE;
  // Refusée (mêmes cas que la phase REFUSE) : le compte n'irait pas à la douane
  // mais vers une candidature complète, et le joueur perdrait son accès.
  if (
    [
      candidature.status,
      candidature.hrpStatus,
      candidature.rpStatus,
      candidature.oralStatus,
    ].includes(AppStatus.REJECTED)
  ) {
    return REFUS_ORAL_CANDIDATURE_REFUSEE;
  }
  const dejaAPasser =
    compte.role === Role.PLAYER &&
    !compte.oralPassedAt &&
    candidature.status !== AppStatus.APPROVED &&
    candidature.oralStatus !== AppStatus.APPROVED;
  return dejaAPasser ? REFUS_ORAL_DEJA_A_PASSER : null;
}

/** Bloc « Oral à repasser » des fiches du panel (joueur, candidature). */
export interface OralARepasserVue {
  /** Motif du staff, montré au joueur. */
  motif: string;
  /** ISO 8601. */
  demandeLe: string;
  /** Compte staff qui l'a exigé ; pseudo null si ce compte n'existe plus. */
  demandePar: { id: string; minecraftUsername: string | null } | null;
}

export function vueOralARepasser(
  compte: {
    oralRetakeRequestedAt: Date | null;
    oralRetakeRequestedById: string | null;
    oralRetakeReason: string | null;
  },
  demandeur: { id: string; minecraftUsername: string } | null,
): OralARepasserVue | null {
  if (!compte.oralRetakeRequestedAt) return null;
  const id = compte.oralRetakeRequestedById;
  return {
    motif: compte.oralRetakeReason ?? '',
    demandeLe: compte.oralRetakeRequestedAt.toISOString(),
    demandePar: id
      ? { id, minecraftUsername: demandeur?.minecraftUsername ?? null }
      : null,
  };
}

/**
 * Message système posté dans le fil du joueur (visible dans le launcher),
 * repris en MP. La consigne suit la phase obtenue : un écrit encore en
 * relecture passe avant la réservation, une place déjà prise est gardée.
 */
export function messageRepasserOral(
  motif: string,
  phase: PhaseCandidature,
): string {
  const fin = /[.!?…]$/u.test(motif) ? '' : '.';
  return `Le staff te demande de repasser l'oral : ${motif}${fin} ${consigneRepasserOral(phase)}`;
}

function consigneRepasserOral(phase: PhaseCandidature): string {
  switch (phase) {
    case 'ECRIT_EN_ATTENTE':
    case 'ECRIT_A_REVOIR':
      return "Une fois ton écrit accepté, réserve une séance de douane dans le launcher (onglet Whitelist) pour retrouver l'accès au jeu.";
    case 'DOUANE_RESERVEE':
      return "Ta place de douane est gardée : présente-toi à ta séance pour retrouver l'accès au jeu.";
    default:
      return "Réserve une séance de douane dans le launcher (onglet Whitelist) pour retrouver l'accès au jeu.";
  }
}
