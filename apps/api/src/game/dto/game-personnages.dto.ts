import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsBoolean,
  IsIn,
  IsInt,
  IsISO8601,
  IsObject,
  IsOptional,
  IsString,
  IsUUID,
  Matches,
  Max,
  MaxLength,
  Min,
} from 'class-validator';
import { MOTIF_UUID_MC } from './game-douane.dto';

/**
 * Corps de POST /v1/game/characters/sync (CANDIDATURE_ET_DOUANE.md §4.5,
 * lot J). Contrat complet : game-personnages.service.ts.
 *
 * L'enveloppe passe par le pipe global ; chaque photo est contrôlée À PART
 * par le service (`validerPhoto`) : une photo invalide est refusée seule, les
 * autres du même envoi sont écrites. Un rattrapage complet au démarrage ne
 * reste donc jamais bloqué par un seul personnage.
 */

/** Photos au plus par envoi : le rattrapage complet découpe en lots. */
export const MAX_PERSONNAGES_PAR_ENVOI = 200;

/** Longueur maximale des textes (prénom, nom, clan, village). Le mod borne déjà le prénom à 20. */
export const LONGUEUR_TEXTE_MAX = 40;

/** Temps de jeu maximal accepté : cent ans, en minutes. */
export const PLAY_MINUTES_MAX = 52_560_000;

export const TYPES_PERSONNAGE = ['NINJA', 'CIVIL'] as const;
export const SEXES_PERSONNAGE = ['HOMME', 'FEMME'] as const;

/** Aucun caractère de contrôle ni de mise en forme invisible. */
const MOTIF_TEXTE = /^[^\p{Cc}\p{Cf}]*$/u;
/** Même chose, avec au moins un caractère visible. */
const MOTIF_TEXTE_REQUIS = /^[^\p{Cc}\p{Cf}]*\S[^\p{Cc}\p{Cf}]*$/u;
/** Code de l'enum Rank de ShinobiCore (GENIN, CHUNIN, TOKUBETSU_JONIN…). */
const MOTIF_GRADE = /^[A-Z][A-Z0-9_]{0,31}$/;
/** Date ISO 8601 avec fuseau explicite (« Z » ou décalage), comme `Instant.toString()` côté Java. */
const MOTIF_FUSEAU = /(?:Z|[+-]\d{2}:?\d{2})$/;

const MSG_DATE = 'date ISO 8601 avec fuseau attendue (Instant.toString()).';
const MSG_TEXTE = `${LONGUEUR_TEXTE_MAX} caractères au plus, sans caractère de contrôle.`;

/** POST /v1/game/characters/sync — enveloppe. */
export class SyncPersonnagesJeuDto {
  @IsArray({ message: 'characters : liste attendue.' })
  @ArrayMinSize(1, { message: 'characters : au moins un personnage.' })
  @ArrayMaxSize(MAX_PERSONNAGES_PAR_ENVOI, {
    message: `characters : ${MAX_PERSONNAGES_PAR_ENVOI} personnages au plus par envoi.`,
  })
  @IsObject({
    each: true,
    message: 'characters : chaque entrée doit être un objet.',
  })
  characters!: Record<string, unknown>[];
}

/**
 * Photo complète d'un personnage. Un champ facultatif absent vaut sa valeur
 * par défaut (null, false, NINJA) : chaque envoi décrit tout le personnage,
 * ce qui rend la synchronisation idempotente. Les champs inconnus sont refusés.
 */
export class PersonnageSyncDto {
  /** ShinobiCharacter.id. */
  @IsUUID('all', { message: 'characterId : UUID du personnage attendu.' })
  characterId!: string;

  /** ShinobiCharacter.ownerId (avec ou sans tirets). */
  @Matches(MOTIF_UUID_MC, { message: 'ownerMcUuid : UUID Minecraft attendu.' })
  ownerMcUuid!: string;

  /** N° (Player ID) ; absent ou null tant qu'il n'est pas attribué (-1 côté plugin). */
  @IsOptional()
  @IsInt({ message: 'playerId : entier attendu.' })
  @Min(0, { message: 'playerId : entier positif ou nul attendu.' })
  @Max(2_147_483_647, { message: 'playerId : trop grand.' })
  playerId?: number | null;

  @IsString({ message: 'firstName : texte attendu.' })
  @MaxLength(LONGUEUR_TEXTE_MAX, { message: `firstName : ${MSG_TEXTE}` })
  @Matches(MOTIF_TEXTE_REQUIS, {
    message: 'firstName : prénom vide ou caractère de contrôle.',
  })
  firstName!: string;

  /** Nom de famille libre (clan « Autre » du mod). */
  @IsOptional()
  @IsString({ message: 'lastName : texte attendu.' })
  @MaxLength(LONGUEUR_TEXTE_MAX, { message: `lastName : ${MSG_TEXTE}` })
  @Matches(MOTIF_TEXTE, { message: `lastName : ${MSG_TEXTE}` })
  lastName?: string | null;

  /** Clan acquis en session de clan ; absent, null ou « Autre » = sans clan. */
  @IsOptional()
  @IsString({ message: 'clan : texte attendu.' })
  @MaxLength(LONGUEUR_TEXTE_MAX, { message: `clan : ${MSG_TEXTE}` })
  @Matches(MOTIF_TEXTE, { message: `clan : ${MSG_TEXTE}` })
  clan?: string | null;

  /** Valeur du mod (« Konohagakure »…). */
  @IsOptional()
  @IsString({ message: 'village : texte attendu.' })
  @MaxLength(LONGUEUR_TEXTE_MAX, { message: `village : ${MSG_TEXTE}` })
  @Matches(MOTIF_TEXTE, { message: `village : ${MSG_TEXTE}` })
  village?: string | null;

  /** Absent : NINJA. */
  @IsOptional()
  @IsIn(TYPES_PERSONNAGE, { message: 'type : NINJA ou CIVIL.' })
  type?: (typeof TYPES_PERSONNAGE)[number] | null;

  /** Code de l'enum Rank (GENIN…), pas le libellé. */
  @IsOptional()
  @Matches(MOTIF_GRADE, {
    message: 'rank : code du grade attendu (GENIN, CHUNIN…).',
  })
  rank?: string | null;

  /** « Homme » / « Femme » côté plugin, envoyés en HOMME / FEMME. */
  @IsOptional()
  @IsIn(SEXES_PERSONNAGE, { message: 'sex : HOMME ou FEMME.' })
  sex?: (typeof SEXES_PERSONNAGE)[number] | null;

  @IsOptional()
  @IsInt({ message: 'age : entier attendu.' })
  @Min(0, { message: 'age : entre 0 et 200.' })
  @Max(200, { message: 'age : entre 0 et 200.' })
  age?: number | null;

  /** Mort RP. */
  @IsBoolean({ message: 'dead : booléen attendu.' })
  dead!: boolean;

  /** Supprimé en jeu ; absent : false. */
  @IsOptional()
  @IsBoolean({ message: 'deleted : booléen attendu.' })
  deleted?: boolean | null;

  /** Minutes jouées avec ce personnage actif, depuis la mise à jour du plugin. */
  @IsInt({ message: 'playMinutes : entier attendu.' })
  @Min(0, { message: 'playMinutes : entier positif ou nul attendu.' })
  @Max(PLAY_MINUTES_MAX, { message: 'playMinutes : trop grand.' })
  playMinutes!: number;

  /** Création du personnage en jeu ; absent si le plugin ne la connaît pas. */
  @IsOptional()
  @IsISO8601({ strict: true }, { message: `createdAt : ${MSG_DATE}` })
  @Matches(MOTIF_FUSEAU, { message: `createdAt : ${MSG_DATE}` })
  createdAt?: string | null;

  /** Dernière partie avec ce personnage actif. */
  @IsOptional()
  @IsISO8601({ strict: true }, { message: `lastSeenAt : ${MSG_DATE}` })
  @Matches(MOTIF_FUSEAU, { message: `lastSeenAt : ${MSG_DATE}` })
  lastSeenAt?: string | null;

  /** Marque `application` du YAML (premier personnage seulement). */
  @IsOptional()
  @IsUUID('all', {
    message: 'applicationId : identifiant de candidature attendu.',
  })
  applicationId?: string | null;

  /** Instant de la photo côté plugin : sert d'ordre entre deux envois. */
  @IsISO8601({ strict: true }, { message: `snapshotAt : ${MSG_DATE}` })
  @Matches(MOTIF_FUSEAU, { message: `snapshotAt : ${MSG_DATE}` })
  snapshotAt!: string;
}
