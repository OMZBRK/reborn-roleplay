import { Role } from '@prisma/client';

/**
 * Carte des droits fichiers, sortie de FilesService pour être lue aussi par
 * les capacités du panel (auth/capacites.ts) sans charger le client SFTP.
 * Elle reste la seule source pour les dossiers : les capacités `fichiers.*`
 * en sont déduites, jamais recopiées (PANEL_V2 §3.3).
 */

/**
 * Cibles de rechargement autorisées : clé logique → commande console résolue +
 * racine de fichiers requise (le grade doit avoir accès à cette racine pour
 * pouvoir recharger le plugin). Le pont côté jeu ne reçoit JAMAIS de texte
 * libre — seulement une de ces commandes fixes.
 */
export const RELOAD_TARGETS: Record<
  string,
  { command: string; root: string; label: string }
> = {
  nexo: { command: 'nexo reload', root: 'plugins/Nexo', label: 'Nexo' },
  creator: {
    command: 'creator reload',
    root: 'plugins/ShinobiCore/creator-assets',
    label: 'Character creator (assets live)',
  },
  magicspells: {
    command: 'ms reload',
    root: 'plugins/MagicSpells',
    label: 'MagicSpells',
  },
  mythicmobs: {
    command: 'mm reload',
    root: 'plugins/MythicMobs',
    label: 'MythicMobs',
  },
  modelengine: {
    command: 'meg reload',
    root: 'plugins/ModelEngine',
    label: 'ModelEngine',
  },
  emotes: {
    command: 'playemote reload',
    root: 'plugins/ShinobiCore/emotes',
    label: 'Emotes',
  },
  abilities: {
    command: 'sa reload',
    root: 'plugins/ShinobiAbilities',
    label: 'Techniques (ShinobiAbilities)',
  },
};

/** Une racine autorisée pour un grade (chemin relatif à la base SFTP + libellé UI). */
export interface ScopeRoot {
  path: string;
  label: string;
}
export interface Scope {
  roots: ScopeRoot[];
  write: boolean;
}
/** Réponse de `GET /v1/files/scopes`. */
export interface FileScopes {
  server: string;
  canWrite: boolean;
  roots: ScopeRoot[];
}

/**
 * Carte de scopes fichiers PAR GRADE — source de vérité de « qui peut toucher
 * quoi ». Volontairement explicite et NON hiérarchique (un modélisateur n'est
 * pas « au-dessus » d'un dev, ils ont des périmètres différents). Les grades
 * absents de cette carte n'ont AUCUN accès fichier, même s'ils passent la garde
 * de rôle du contrôleur.
 */
export const SCOPES: Partial<Record<Role, Scope>> = {
  [Role.MODELISATEUR]: {
    write: true,
    roots: [
      { path: 'plugins/Nexo', label: 'Nexo — modèles & items' },
      { path: 'plugins/ShinobiCore/emotes', label: 'Emotes RP' },
      {
        path: 'plugins/ShinobiCore/creator-assets',
        label: 'Character creator — tenues/cheveux/yeux (diffusion live)',
      },
    ],
  },
  [Role.DEVELOPPEUR]: {
    write: true,
    roots: [
      { path: 'plugins/Nexo', label: 'Nexo — modèles & items' },
      {
        path: 'plugins/ShinobiCore/creator-assets',
        label: 'Character creator — tenues/cheveux/yeux (diffusion live)',
      },
      { path: 'plugins/MagicSpells', label: 'MagicSpells' },
      { path: 'plugins/MythicMobs', label: 'MythicMobs' },
      { path: 'plugins/ModelEngine', label: 'ModelEngine' },
      {
        path: 'plugins/ShinobiAbilities',
        label: 'Techniques — abilities.yml généré',
      },
      { path: 'plugins/ShinobiCore/emotes', label: 'Emotes RP' },
    ],
  },
  [Role.ADMIN]: {
    write: true,
    roots: [{ path: 'plugins', label: 'plugins/' }],
  },
  [Role.OWNER]: {
    write: true,
    roots: [{ path: '', label: 'Serveur (racine)' }],
  },
};

/** Vrai si la racine `racine` couvre le chemin `chemin` (lui-même ou un descendant). */
export function racineCouvre(racine: string, chemin: string): boolean {
  return racine === '' || chemin === racine || chemin.startsWith(racine + '/');
}

/**
 * Zones de fichiers exposées en capacités `fichiers.<zone>` : une par cible de
 * rechargement (même clé), plus `plugins` (tout le dossier des plugins) et
 * `racine` (tout le serveur). Un grade a la zone si l'une de ses racines la
 * couvre : l'admin, qui a `plugins/`, a donc aussi `nexo`, `emotes`…
 */
export const ZONES_FICHIERS: ReadonlyArray<{ zone: string; chemin: string }> = [
  ...Object.entries(RELOAD_TARGETS).map(([zone, t]) => ({
    zone,
    chemin: t.root,
  })),
  { zone: 'plugins', chemin: 'plugins' },
  { zone: 'racine', chemin: '' },
];

/** Zones de fichiers accessibles à un grade (vide si la carte ne le connaît pas). */
export function zonesFichiersPour(role: Role): string[] {
  const roots = SCOPES[role]?.roots ?? [];
  return ZONES_FICHIERS.filter((z) =>
    roots.some((r) => racineCouvre(r.path, z.chemin)),
  ).map((z) => z.zone);
}

/** Vrai si le grade peut écrire dans au moins une de ses racines. */
export function ecritureFichiersPour(role: Role): boolean {
  const s = SCOPES[role];
  return !!s && s.write && s.roots.length > 0;
}
