/**
 * Dictionnaire central des libellés du panel (PANEL_V2 §2 principe 9, §5.4) :
 * grades, catégories et statuts de ticket, actions du journal, sources,
 * serveurs. Un seul endroit : aucune page ne recopie un libellé ni n'affiche
 * un code brut. Les tons d'affichage des statuts sont dans lib/statuts.ts.
 */

import type { Role, TicketCategory, TicketStatus } from './types';

// ──────────────────────────── Grades ────────────────────────────

/**
 * Ordre des grades, du joueur au propriétaire. Sert à l'affichage (listes,
 * fenêtre « Changer de grade ») ; l'API reste seule juge des droits.
 */
export const ORDRE_GRADES: readonly Role[] = [
  'PLAYER',
  'WHITELISTED',
  'HELPER',
  'MODELISATEUR',
  'DEVELOPPEUR',
  'WHITELIST_REVIEWER',
  'MODERATOR',
  'ADMIN',
  'OWNER',
];

/** Libellés des grades (mêmes mots que l'API, auth/capacites.ts). */
export const LIBELLES_GRADES: Record<Role, string> = {
  PLAYER: 'Joueur',
  WHITELISTED: 'Whitelisté',
  HELPER: 'Helper',
  MODELISATEUR: 'Modélisateur',
  DEVELOPPEUR: 'Développeur',
  WHITELIST_REVIEWER: 'Recruteur',
  MODERATOR: 'Modérateur',
  ADMIN: 'Administrateur',
  OWNER: 'Propriétaire',
};

/** Ce que le grade ouvre au panel, en une ligne (fenêtre « Changer de grade »). */
export const RESUME_GRADES: Record<Role, string> = {
  PLAYER: 'Aucun accès au panel.',
  WHITELISTED: 'Joueur whitelisté, aucun accès au panel.',
  HELPER: 'Tickets, fiche joueur sans sécurité, wiki.',
  MODELISATEUR: 'Fichiers du serveur (Nexo, ressources du créateur, emotes), wiki.',
  DEVELOPPEUR: 'Techniques, fichiers élargis, tickets « Bug », wiki.',
  WHITELIST_REVIEWER: 'Candidatures, séances de douane, examens en jeu, tickets.',
  MODERATOR: 'En plus : sanctions, indicateurs d’activité, modèles de réponses.',
  ADMIN: 'Tout, dont la sécurité, le journal et les grades.',
  OWNER: 'Tout, dont la racine du serveur.',
};

export function libelleGrade(role: string | null | undefined): string {
  if (!role) return '—';
  return LIBELLES_GRADES[role as Role] ?? 'Grade inconnu';
}

/** Rang d'un grade dans ORDRE_GRADES (−1 si inconnu). */
export function rangGrade(role: string | null | undefined): number {
  return role ? ORDRE_GRADES.indexOf(role as Role) : -1;
}

export function estStaff(role: string | null | undefined): boolean {
  return rangGrade(role) >= rangGrade('HELPER');
}

// ──────────────────────────── Tickets ────────────────────────────

export const LIBELLE_CATEGORIE_TICKET: Record<TicketCategory, string> = {
  BUG: 'Bug',
  REPORT_PLAYER: 'Signalement',
  WHITELIST_APPEAL: 'Contestation',
  PURCHASE_ISSUE: 'Achat',
  OTHER: 'Autre',
};

/** Forme longue (filtre, panneau latéral). */
export const LIBELLE_CATEGORIE_TICKET_LONG: Record<TicketCategory, string> = {
  BUG: 'Bug',
  REPORT_PLAYER: 'Signalement d’un joueur',
  WHITELIST_APPEAL: 'Contestation de whitelist',
  PURCHASE_ISSUE: 'Achat',
  OTHER: 'Autre',
};

export const CATEGORIES_TICKET: readonly TicketCategory[] = [
  'BUG',
  'REPORT_PLAYER',
  'WHITELIST_APPEAL',
  'PURCHASE_ISSUE',
  'OTHER',
];

export function libelleCategorie(c: string): string {
  return LIBELLE_CATEGORIE_TICKET[c as TicketCategory] ?? 'Autre';
}

export const LIBELLE_STATUT_TICKET: Record<TicketStatus, string> = {
  OPEN: 'Ouvert',
  IN_PROGRESS: 'En cours',
  RESOLVED: 'Résolu',
  CLOSED: 'Fermé',
};

// ──────────────────────────── Journal (audit) ────────────────────────────

export const LIBELLE_SOURCE: Record<string, string> = {
  panel: 'Panel',
  discord: 'Discord',
  launcher: 'Launcher',
  system: 'Système',
  game: 'Jeu',
};

export function libelleSource(s: string): string {
  return LIBELLE_SOURCE[s] ?? 'Autre';
}

/** Familles d'actions, pour le filtre du journal (préfixe du code). */
export const FAMILLES_AUDIT: ReadonlyArray<{ prefixe: string; label: string }> = [
  { prefixe: 'whitelist', label: 'Candidatures' },
  { prefixe: 'douane', label: 'Douane' },
  { prefixe: 'ticket', label: 'Tickets' },
  { prefixe: 'role', label: 'Grades' },
  { prefixe: 'player', label: 'Joueurs' },
  { prefixe: 'files', label: 'Fichiers' },
  { prefixe: 'abilities', label: 'Techniques' },
  { prefixe: 'discord', label: 'Annonces Discord' },
  { prefixe: 'release', label: 'Versions du launcher' },
  { prefixe: 'manifest', label: 'Manifeste du jeu' },
];

const PARTIE: Record<string, string> = { hrp: 'HRP', rp: 'RP' };
const DECISION: Record<string, string> = {
  approved: 'validé',
  needs_revision: 'à revoir',
  rejected: 'refusé',
};

/** Actions connues (codes en minuscules). */
const ACTIONS: Record<string, string> = {
  'whitelist.approve': 'Candidature acceptée',
  'whitelist.reject': 'Candidature refusée',
  'whitelist.revise': 'Candidature à revoir',
  'whitelist.approved': 'Écrit validé (HRP et RP)',
  'whitelist.needs_revision': 'Écrit à revoir (HRP et RP)',
  'whitelist.rejected': 'Écrit refusé (HRP et RP)',
  'whitelist.claim': 'Candidature prise',
  'whitelist.release': 'Candidature relâchée',
  'whitelist.release.force': 'Candidature reprise de force',
  'whitelist.reset': 'Candidature remise à zéro',
  'whitelist.note_privee': 'Note privée de l’écrit modifiée',
  'whitelist.voix.ecoute': 'Voix écoutée',
  'whitelist.oral.approved': 'Oral accepté',
  'whitelist.oral.needs_revision': 'Oral à revoir',
  'whitelist.oral.rejected': 'Oral refusé',
  'ticket.claim': 'Ticket pris',
  'ticket.release': 'Ticket relâché',
  'ticket.release.force': 'Ticket repris de force',
  'ticket.open': 'Ticket rouvert',
  'ticket.in_progress': 'Ticket passé en cours',
  'ticket.resolved': 'Ticket résolu',
  'ticket.closed': 'Ticket fermé',
  'role.change': 'Grade changé',
  'player.ip.view': 'Adresse IP affichée',
  'anomaly.ip.view': 'Adresse IP d’une connexion suspecte affichée',
  'douane.seance.create': 'Séance de douane planifiée',
  'douane.seance.update': 'Séance de douane modifiée',
  'douane.seance.cloture': 'Séance de douane close',
  'douane.seance.cancel': 'Séance de douane annulée',
  'douane.reservation.missed': 'Séance manquée',
  'douane.reservation.cancel': 'Candidat retiré d’une séance',
  'douane.presence.arrivee': 'Arrivée au hall',
  'douane.presence.appel': 'Appel au bureau',
  'douane.presence.depart': 'Sortie de la douane',
  'douane.presence.retrait': 'Retrait du hall',
  'douane.presence.report': 'Passage reporté',
  'douane.dossier.read': 'Dossier lu au Poste de garde',
  'douane.jeu.decision': 'Oral tranché en jeu',
  'files.read': 'Fichier lu',
  'files.write': 'Fichier enregistré',
  'files.upload': 'Fichier envoyé',
  'files.delete': 'Fichier supprimé',
  'files.mkdir': 'Dossier créé',
  'files.move': 'Fichier déplacé',
  'files.reload': 'Plugin rechargé',
  'files.nexo.animated-item': 'Item animé Nexo créé',
  'abilities.deploy': 'Techniques déployées',
  'discord.message.create': 'Annonce créée',
  'discord.message.edit': 'Annonce modifiée',
  'discord.message.update': 'Annonce enregistrée',
  'discord.message.preview': 'Annonce envoyée en aperçu',
  'discord.message.publish': 'Annonce publiée',
  'discord.message.remove': 'Annonce retirée',
  'discord.message.delete': 'Annonce supprimée',
  'release.publish': 'Version du launcher publiée',
  'release.delete': 'Version du launcher supprimée',
  'manifest.publish': 'Manifeste du jeu publié',
};

/**
 * Libellé français d'une action du journal. Les décisions par partie
 * (`whitelist.hrp.approved`) sont composées ; une action inconnue prend le nom
 * de sa famille, jamais le code brut.
 */
export function libelleAction(code: string): string {
  const c = code.toLowerCase();
  const connu = ACTIONS[c];
  if (connu) return connu;
  const partie = /^whitelist\.(hrp|rp)\.(approved|needs_revision|rejected)$/.exec(c);
  if (partie) return `Écrit ${PARTIE[partie[1]!]} ${DECISION[partie[2]!]}`;
  const famille = FAMILLES_AUDIT.find((f) => c.startsWith(`${f.prefixe}.`));
  return famille ? `${famille.label} · autre action` : 'Autre action';
}

// ──────────────────────────── Serveurs ────────────────────────────

/**
 * Serveurs connus (identifiant renvoyé par l'API → nom affiché). Aucune
 * adresse ici. Le sélecteur de serveur reste caché tant qu'il n'y en a qu'un.
 */
const SERVEURS: Record<string, string> = {
  dev: 'Développement',
  prod: 'Production',
  build: 'Build',
};

export function libelleServeur(id: string | null | undefined): string {
  if (!id) return '—';
  return SERVEURS[id] ?? id;
}
