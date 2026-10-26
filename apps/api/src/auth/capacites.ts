import { Role, TicketCategory } from '@prisma/client';
import { ecritureFichiersPour, zonesFichiersPour } from '../files/files.scopes';

/**
 * Capacités du panel staff (PANEL_V2 §3.3, validé le 25/10).
 *
 * Le rang linéaire de `RolesGuard` donnait aux grades de modération des outils
 * de développeur (Recruteur et Modérateur passaient la garde des techniques)
 * et aux grades techniques des données joueurs. On raisonne désormais en
 * capacités : une table explicite, NON hiérarchique, grade par grade.
 *
 * L'API renvoie la liste au panel (`GET /v1/auth/me`, champ `capacites`) et
 * les routes concernées l'exigent (`@ExigeCapacite`). Le panel ne recopie ni
 * les rangs ni cette table : il lit la liste.
 *
 * Exigence de l'utilisateur (25/10) : les développeurs et modélisateurs
 * gardent TOUS leurs accès de création (fichiers du serveur, modèles,
 * techniques pour le développeur). Seuls les accès hors de leur métier sont
 * revus : fiches joueurs, IP, tickets autres que « Bug ».
 *
 * Network (Q13) : une capacité pourra porter un `serverId` à l'arrivée d'un
 * deuxième serveur (`tickets.traiter@reborn`) ; rien à coder avant le lot L9.
 */

/** Libellés français des grades, à un seul endroit côté API. */
export const LIBELLES_GRADES: Record<Role, string> = {
  [Role.PLAYER]: 'Joueur',
  [Role.WHITELISTED]: 'Whitelisté',
  [Role.HELPER]: 'Helper',
  [Role.MODELISATEUR]: 'Modélisateur',
  [Role.DEVELOPPEUR]: 'Développeur',
  [Role.WHITELIST_REVIEWER]: 'Recruteur',
  [Role.MODERATOR]: 'Modérateur',
  [Role.ADMIN]: 'Administrateur',
  [Role.OWNER]: 'Propriétaire',
};

// ── Groupes de grades (lisibilité de la table) ─────────────────────────────

/** Tout le staff, grades techniques compris. */
const STAFF: readonly Role[] = [
  Role.HELPER,
  Role.MODELISATEUR,
  Role.DEVELOPPEUR,
  Role.WHITELIST_REVIEWER,
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];
/** Staff au contact des joueurs (sans les grades techniques). */
const MODERATION: readonly Role[] = [
  Role.HELPER,
  Role.WHITELIST_REVIEWER,
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];
/** Relecture des candidatures et douane (même règle que douane.regles › estExaminateur). */
const RECRUTEMENT: readonly Role[] = [
  Role.WHITELIST_REVIEWER,
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];
const MODERATEUR_ET_PLUS: readonly Role[] = [
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];
/** Outils de production des techniques : développeur et direction. */
const TECHNIQUE: readonly Role[] = [Role.DEVELOPPEUR, Role.ADMIN, Role.OWNER];
const DIRECTION: readonly Role[] = [Role.ADMIN, Role.OWNER];

/**
 * Table des capacités fixes. Les capacités `fichiers.*` n'y sont pas : elles
 * sont déduites de la carte des droits fichiers (files.scopes.ts).
 */
const TABLE = {
  /** Se connecter au panel staff. */
  'panel.acceder': STAFF,

  // Pilotage (STATISTIQUES Q6)
  /** Pilotage › Maintenant et Staff ; compteurs de l'accueil. */
  'pilotage.voir': STAFF,
  /** Pilotage › Activité, Recrutement, Économie. */
  'pilotage.analyses': MODERATEUR_ET_PLUS,
  'pilotage.demographie': DIRECTION,
  'pilotage.revenus': [Role.OWNER],

  // Joueurs
  /** Liste et fiche joueur, sans l'onglet Sécurité. */
  'joueurs.voir': MODERATION,
  /** IP et pays de connexion d'un joueur : lecture à la demande, journalisée. */
  'joueurs.ip': DIRECTION,
  /** Changer le grade d'un compte (garde-fous dans RoleService). */
  'grades.changer': DIRECTION,

  // Candidatures
  /** Relire les écrits : liste, fiche, voix, décisions, échanges, prise en charge. */
  'candidatures.relire': RECRUTEMENT,
  /** Remettre une candidature à zéro. */
  'candidatures.reinitialiser': DIRECTION,
  /** Planifier et tenir les séances de douane, anciens créneaux d'oral. */
  'douane.organiser': RECRUTEMENT,
  /** Être examinateur d'une séance (Poste de garde en jeu). */
  'douane.examiner': RECRUTEMENT,

  // Modération
  /** Tickets de toutes catégories. */
  'tickets.traiter': MODERATION,
  /** Tickets de catégorie « Bug » (le développeur n'a que ceux-là). */
  'tickets.bug': [...MODERATION, Role.DEVELOPPEUR],
  /** Reprendre un dossier ou un ticket pris par un autre membre du staff. */
  'prises.forcer': DIRECTION,
  /** Casier : lecture (lot L8). */
  'sanctions.voir': MODERATION,
  /** Casier : poser ou lever une sanction. */
  'sanctions.poser': MODERATEUR_ET_PLUS,
  /** Modération › Sécurité : connexions suspectes. */
  'securite.voir': DIRECTION,
  /** Modération › Journal (audit). */
  'audit.voir': DIRECTION,

  // Contenu
  /** Wiki et Idées : lire, écrire. */
  'wiki.editer': STAFF,
  'wiki.supprimer': MODERATEUR_ET_PLUS,
  /** Annonces Discord. */
  'discord.publier': DIRECTION,
  /** Technique Creator : lister, éditer, importer, valider, aperçu en jeu. */
  'techniques.editer': TECHNIQUE,
  /** Déployer les techniques sur le serveur (compilation, SFTP, rechargement). */
  'techniques.deployer': TECHNIQUE,
  'techniques.supprimer': DIRECTION,

  // Réglages
  /** Réglages › Modèles : motifs de candidature et réponses types. */
  'reponses-types.gerer': MODERATEUR_ET_PLUS,
} as const satisfies Record<string, readonly Role[]>;

export type CapaciteFixe = keyof typeof TABLE;
/** `fichiers.<zone>` (zones de files.scopes.ts) et `fichiers.ecrire`. */
export type CapaciteFichiers = `fichiers.${string}`;
export type Capacite = CapaciteFixe | CapaciteFichiers;

/** Toutes les capacités fixes, dans l'ordre de la table. */
export const CAPACITES_FIXES = Object.keys(TABLE) as CapaciteFixe[];

/** Capacités d'un grade, triées : la table, puis les zones de fichiers. */
export function capacitesDe(role: Role): Capacite[] {
  const fixes = CAPACITES_FIXES.filter((c) =>
    (TABLE[c] as readonly Role[]).includes(role),
  );
  const zones: Capacite[] = zonesFichiersPour(role).map(
    (z): CapaciteFichiers => `fichiers.${z}`,
  );
  if (zones.length > 0 && ecritureFichiersPour(role)) {
    zones.push('fichiers.ecrire');
  }
  return [...fixes, ...zones];
}

/** Vrai si le grade a la capacité. */
export function aCapacite(role: Role, capacite: Capacite): boolean {
  if (capacite.startsWith('fichiers.')) {
    return capacitesDe(role).includes(capacite);
  }
  const grades = TABLE[capacite as CapaciteFixe] as readonly Role[] | undefined;
  return grades?.includes(role) ?? false;
}

/** Vrai si le grade a au moins une capacité `fichiers.<zone>`. */
export function aAccesFichiers(role: Role): boolean {
  return zonesFichiersPour(role).length > 0;
}

/**
 * Le grade peut-il traiter un ticket de cette catégorie ? Toutes avec
 * `tickets.traiter`, « Bug » seulement avec `tickets.bug`.
 */
export function peutTraiterTicket(
  role: Role,
  categorie: TicketCategory,
): boolean {
  if (aCapacite(role, 'tickets.traiter')) return true;
  return categorie === TicketCategory.BUG && aCapacite(role, 'tickets.bug');
}

/** Grades qui ont au moins une des capacités, dans l'ordre des grades. */
export function gradesAyant(capacites: readonly Capacite[]): Role[] {
  return (Object.keys(LIBELLES_GRADES) as Role[]).filter((r) =>
    capacites.some((c) => aCapacite(r, c)),
  );
}

/**
 * Message de refus en français, pour le panel : « Réservé aux
 * administrateurs. » ou « Réservé à : Développeur, Administrateur,
 * Propriétaire. » Jamais de code de grade brut.
 */
export function messageRefus(capacites: readonly Capacite[]): string {
  const grades = gradesAyant(capacites);
  if (grades.length === 0) return 'Accès réservé.';
  if (
    grades.length === DIRECTION.length &&
    DIRECTION.every((r) => grades.includes(r))
  ) {
    return 'Réservé aux administrateurs.';
  }
  if (grades.length === 1 && grades[0] === Role.OWNER) {
    return 'Réservé au propriétaire.';
  }
  return `Réservé à : ${grades.map((r) => LIBELLES_GRADES[r]).join(', ')}.`;
}
