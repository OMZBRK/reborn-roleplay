import type { CharacterSex, CharacterType } from '@prisma/client';
import { formatNumero } from '../game/game-personnages.regles';
import { SERVER_ID_PATTERN } from '../play/play.service';

/**
 * « Mes personnages » (CANDIDATURE_ET_DOUANE.md §4.6, lot J) : règles pures de
 * GET /v1/me/characters, sans requête.
 *
 * Une seule API sert la prod, le dev et le build : les personnages de test du
 * dev arrivent aussi en base. Le launcher ne montre que les serveurs de prod,
 * lus dans REBORN_CHARACTERS_SERVER_IDS (identifiants séparés par des
 * virgules, mêmes valeurs que `reborn.server-id` de ShinobiCore). Vide :
 * `prod`. Jamais REBORN_SERVER_ID, qui peut viser le dev pendant les essais.
 */

/** Serveurs lus par défaut. */
export const SERVEURS_PERSONNAGES_PAR_DEFAUT: readonly string[] = ['prod'];

export interface ServeursPersonnages {
  serveurs: string[];
  /** Réglages ignorés, à journaliser. */
  avertissements: string[];
}

/** Lit REBORN_CHARACTERS_SERVER_IDS ; aucun identifiant valide : `prod`. */
export function lireServeursPersonnages(brut?: string): ServeursPersonnages {
  const avertissements: string[] = [];
  const serveurs: string[] = [];
  for (const part of (brut ?? '').split(',')) {
    const id = part.trim();
    if (!id) continue;
    if (!SERVER_ID_PATTERN.test(id)) {
      avertissements.push(
        `REBORN_CHARACTERS_SERVER_IDS : « ${id} » ignoré (minuscules, chiffres et tirets, 32 caractères au plus).`,
      );
      continue;
    }
    if (!serveurs.includes(id)) serveurs.push(id);
  }
  if (serveurs.length === 0) {
    if ((brut ?? '').trim()) {
      avertissements.push(
        `REBORN_CHARACTERS_SERVER_IDS : aucun identifiant valide, « Mes personnages » lit ${SERVEURS_PERSONNAGES_PAR_DEFAUT.join(', ')}.`,
      );
    }
    serveurs.push(...SERVEURS_PERSONNAGES_PAR_DEFAUT);
  }
  return { serveurs, avertissements };
}

/** En vie, mort RP, supprimé (la suppression passe avant la mort). */
export type EtatPersonnage = 'ALIVE' | 'DEAD' | 'DELETED';

/** Ligne lue pour la vue (GameCharacter + sa candidature). */
export interface PersonnagePourVue {
  characterId: string;
  playerId: number | null;
  firstName: string;
  lastName: string | null;
  clan: string | null;
  village: string | null;
  type: CharacterType;
  rank: string | null;
  sex: CharacterSex | null;
  age: number | null;
  dead: boolean;
  deletedAt: Date | null;
  playMinutes: number;
  createdAt: Date | null;
  lastSeenAt: Date | null;
  firstSyncedAt: Date;
  applicationId: string | null;
  application: {
    userId: string;
    history: string;
    objectives: string;
    oralDecidedAt: Date | null;
  } | null;
}

/** Dossier d'entrée du personnage issu de la candidature, en lecture seule. */
export interface DossierEntreeVue {
  history: string;
  objectives: string;
  /** Date de l'oral (douane), ou null s'il n'est pas daté. */
  oralDecidedAt: string | null;
}

/** Une carte de « Mes personnages ». */
export interface MonPersonnageVue {
  /** Identifiant côté jeu (ShinobiCharacter.id). */
  characterId: string;
  /** N°, réservé au propriétaire et au staff ; null tant qu'il n'est pas attribué. */
  playerId: number | null;
  /** N° affiché : « 00042 ». */
  playerNumber: string | null;
  firstName: string;
  lastName: string | null;
  clan: string | null;
  village: string | null;
  type: CharacterType;
  /** Code du grade (GENIN…) ; le launcher affiche le libellé. */
  rank: string | null;
  sex: CharacterSex | null;
  age: number | null;
  state: EtatPersonnage;
  playMinutes: number;
  /** Début du décompte du temps de jeu (« compté depuis le JJ/MM ») : la première synchronisation. */
  playMinutesSince: string;
  /** Création en jeu ; null si le serveur ne la connaît pas. */
  createdAt: string | null;
  lastSeenAt: string | null;
  /** Personnage issu de la candidature (premier personnage). */
  fromApplication: boolean;
  dossier: DossierEntreeVue | null;
}

export interface MesPersonnagesVue {
  characters: MonPersonnageVue[];
}

export function etatPersonnage(p: {
  dead: boolean;
  deletedAt: Date | null;
}): EtatPersonnage {
  if (p.deletedAt) return 'DELETED';
  return p.dead ? 'DEAD' : 'ALIVE';
}

/** Ligne → carte. Le dossier n'est montré que si la candidature est bien celle du compte qui lit. */
export function vuePersonnage(
  p: PersonnagePourVue,
  userId: string,
): MonPersonnageVue {
  const app =
    p.application && p.application.userId === userId ? p.application : null;
  return {
    characterId: p.characterId,
    playerId: p.playerId,
    playerNumber: formatNumero(p.playerId),
    firstName: p.firstName,
    lastName: p.lastName,
    clan: p.clan,
    village: p.village,
    type: p.type,
    rank: p.rank,
    sex: p.sex,
    age: p.age,
    state: etatPersonnage(p),
    playMinutes: p.playMinutes,
    playMinutesSince: p.firstSyncedAt.toISOString(),
    createdAt: p.createdAt?.toISOString() ?? null,
    lastSeenAt: p.lastSeenAt?.toISOString() ?? null,
    fromApplication: app !== null,
    dossier: app
      ? {
          history: app.history,
          objectives: app.objectives,
          oralDecidedAt: app.oralDecidedAt?.toISOString() ?? null,
        }
      : null,
  };
}

/** Ordre des cartes : création en jeu (ou première synchronisation), puis N°. */
export function comparerPersonnages(
  a: PersonnagePourVue,
  b: PersonnagePourVue,
): number {
  const ta = (a.createdAt ?? a.firstSyncedAt).getTime();
  const tb = (b.createdAt ?? b.firstSyncedAt).getTime();
  if (ta !== tb) return ta - tb;
  return (
    (a.playerId ?? Number.MAX_SAFE_INTEGER) -
    (b.playerId ?? Number.MAX_SAFE_INTEGER)
  );
}
