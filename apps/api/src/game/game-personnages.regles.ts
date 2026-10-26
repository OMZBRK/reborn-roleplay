import type { CharacterSex, CharacterType } from '@prisma/client';
import { plainToInstance } from 'class-transformer';
import { validateSync, type ValidationError } from 'class-validator';
import { traduireChampsInconnus } from '../common/pipes/validation.pipe';
import { SANS_CLAN } from '../whitelist/whitelist.rules';
import { PersonnageSyncDto } from './dto/game-personnages.dto';

/**
 * Règles pures de la synchronisation des personnages (CANDIDATURE_ET_DOUANE.md
 * §4.5, lot J), sans requête : contrôle d'une photo, normalisation, données
 * écrites. Partagées par GamePersonnagesService et GET /v1/me/characters.
 */

/** Une photo datée de plus de 5 min dans le futur est refusée : elle bloquerait les suivantes. */
export const AVANCE_PHOTO_MAX_MS = 5 * 60_000;

/**
 * UUID Minecraft en forme canonique : minuscules, avec tirets. Bukkit envoie
 * la forme avec tirets, le compte Reborn garde parfois la forme compacte : on
 * compare toujours cette forme. Une valeur qui n'a pas 32 chiffres
 * hexadécimaux est rendue en minuscules, telle quelle.
 */
export function normaliserMcUuid(brut: string): string {
  const hex = brut.trim().toLowerCase().replace(/-/g, '');
  if (!/^[0-9a-f]{32}$/.test(hex)) return brut.trim().toLowerCase();
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** N° affiché : 42 → « 00042 » (PlayerIdRegistry.format côté plugin). */
export function formatNumero(n: number | null): string | null {
  return n === null || n < 0 ? null : String(n).padStart(5, '0');
}

export type PhotoLue =
  | { ok: true; photo: PersonnageSyncDto }
  | { ok: false; characterId: string | null; message: string };

/** Messages « champ : message », en profondeur, joints par « ; ». */
function messages(erreurs: ValidationError[]): string {
  const sortie: string[] = [];
  const parcourir = (liste: ValidationError[]) => {
    for (const e of liste) {
      if (e.constraints) sortie.push(...Object.values(e.constraints));
      if (e.children?.length) parcourir(e.children);
    }
  };
  parcourir(traduireChampsInconnus(erreurs));
  return sortie.join(' ; ');
}

/**
 * Contrôle d'UNE photo, avec les règles du pipe global (champs inconnus
 * refusés). `characterId` est renvoyé dès qu'il est lisible, pour que le
 * plugin rattache le refus au bon personnage.
 */
export function validerPhoto(brut: unknown): PhotoLue {
  const objet =
    brut !== null && typeof brut === 'object' && !Array.isArray(brut)
      ? (brut as Record<string, unknown>)
      : null;
  const id = objet?.characterId;
  const characterId =
    typeof id === 'string' && id.length <= 64 ? id.toLowerCase() : null;
  if (!objet) {
    return { ok: false, characterId: null, message: 'objet attendu.' };
  }
  const photo = plainToInstance(PersonnageSyncDto, objet);
  const erreurs = validateSync(photo, {
    whitelist: true,
    forbidNonWhitelisted: true,
  });
  if (erreurs.length > 0) {
    return { ok: false, characterId, message: messages(erreurs) };
  }
  // ISO 8601 admet des formes (semaine, jour de l'année) que Date ne lit pas.
  const illisibles = (['createdAt', 'lastSeenAt', 'snapshotAt'] as const)
    .filter((champ) => photo[champ] && dateOuNull(photo[champ]) === null)
    .map((champ) => `${champ} : date illisible (forme AAAA-MM-JJTHH:MM:SSZ).`);
  if (illisibles.length > 0) {
    return { ok: false, characterId, message: illisibles.join(' ; ') };
  }
  return { ok: true, photo };
}

/** Texte rogné, ou null s'il est absent ou vide. */
export function texteOuNull(v: string | null | undefined): string | null {
  const t = v?.trim();
  return t ? t : null;
}

/** Clan acquis en session ; « Autre » (le clan personnalisé du mod) vaut sans clan. */
export function clanOuNull(v: string | null | undefined): string | null {
  const t = texteOuNull(v);
  return t && t.toLowerCase() !== SANS_CLAN.toLowerCase() ? t : null;
}

/** Date d'une chaîne ISO déjà contrôlée, ou null. */
export function dateOuNull(v: string | null | undefined): Date | null {
  if (!v) return null;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** Ce que l'API a établi elle-même, hors de la photo. */
export interface ContexteEcriture {
  ownerMcUuid: string;
  /** Compte Reborn retrouvé par ownerMcUuid, ou null. */
  userId: string | null;
  /** Candidature vérifiée (même compte), ou null. */
  applicationId: string | null;
  snapshotAt: Date;
  /** Date de suppression déjà en base : elle ne bouge plus tant que le personnage reste supprimé. */
  deletedAtExistant: Date | null;
}

/** Colonnes écrites à chaque synchronisation (création ou mise à jour). */
export interface DonneesPersonnage {
  ownerMcUuid: string;
  userId: string | null;
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
  applicationId: string | null;
  snapshotAt: Date;
}

/** Photo complète → colonnes : un champ absent vaut sa valeur par défaut. */
export function donneesDepuisPhoto(
  photo: PersonnageSyncDto,
  ctx: ContexteEcriture,
): DonneesPersonnage {
  return {
    ownerMcUuid: ctx.ownerMcUuid,
    userId: ctx.userId,
    playerId: photo.playerId ?? null,
    firstName: photo.firstName.trim(),
    lastName: texteOuNull(photo.lastName),
    clan: clanOuNull(photo.clan),
    village: texteOuNull(photo.village),
    type: photo.type ?? 'NINJA',
    rank: photo.rank ?? null,
    sex: photo.sex ?? null,
    age: photo.age ?? null,
    dead: photo.dead,
    deletedAt: photo.deleted ? (ctx.deletedAtExistant ?? ctx.snapshotAt) : null,
    playMinutes: photo.playMinutes,
    createdAt: dateOuNull(photo.createdAt),
    lastSeenAt: dateOuNull(photo.lastSeenAt),
    applicationId: ctx.applicationId,
    snapshotAt: ctx.snapshotAt,
  };
}
