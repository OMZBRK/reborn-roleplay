import { AppStatus, DouaneSeanceStatut } from '@prisma/client';
import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  IsArray,
  IsBoolean,
  IsEnum,
  IsIn,
  IsInt,
  IsISO8601,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';
import { CAPACITE_BORNES } from '../douane.regles';

/**
 * Corps et requêtes des routes de la douane (CANDIDATURE_ET_DOUANE.md §3.3,
 * §3.8, §3.11). Messages en français : ils remontent tels quels au launcher
 * et au panel.
 */

const MSG_DATE =
  'Date attendue au format ISO 8601 (ex. 2026-10-31T20:00:00.000Z).';
const MSG_CAPACITE = `La capacité va de ${CAPACITE_BORNES.min} à ${CAPACITE_BORNES.max} places.`;

// ──────────────────────────── Candidat ────────────────────────────

/** POST /v1/douane/reservations */
export class ReserverSeanceDto {
  @IsUUID('all', { message: 'Séance invalide.' })
  seanceId!: string;

  /**
   * true : remplace la place déjà réservée, dans la même transaction
   * (« Changer de séance » : l'ancienne place n'est rendue que si la
   * nouvelle est obtenue).
   */
  @IsOptional()
  @IsBoolean({ message: 'remplacer doit valoir true ou false.' })
  remplacer?: boolean;
}

// ──────────────────────────── Staff : séances ────────────────────────────

/** POST /v1/admin/douane/seances */
export class CreerSeanceDto {
  @IsISO8601({}, { message: `Début : ${MSG_DATE}` })
  startAt!: string;

  @IsISO8601({}, { message: `Fin : ${MSG_DATE}` })
  endAt!: string;

  @IsInt({ message: MSG_CAPACITE })
  @Min(CAPACITE_BORNES.min, { message: MSG_CAPACITE })
  @Max(CAPACITE_BORNES.max, { message: MSG_CAPACITE })
  capacite!: number;

  /** Examinateurs prévus : ids de comptes Reborn, rôle WHITELIST_REVIEWER ou plus. */
  @IsOptional()
  @IsArray({ message: 'examinateurIds doit être une liste.' })
  @ArrayMaxSize(20, { message: '20 examinateurs au plus.' })
  @IsUUID('all', { each: true, message: 'Examinateur invalide.' })
  examinateurIds?: string[];
}

/** PATCH /v1/admin/douane/seances/:id — tout est facultatif. */
export class ModifierSeanceDto {
  @IsOptional()
  @IsISO8601({}, { message: `Début : ${MSG_DATE}` })
  startAt?: string;

  @IsOptional()
  @IsISO8601({}, { message: `Fin : ${MSG_DATE}` })
  endAt?: string;

  @IsOptional()
  @IsInt({ message: MSG_CAPACITE })
  @Min(CAPACITE_BORNES.min, { message: MSG_CAPACITE })
  @Max(CAPACITE_BORNES.max, { message: MSG_CAPACITE })
  capacite?: number;

  /** Remplace toute la liste des examinateurs. */
  @IsOptional()
  @IsArray({ message: 'examinateurIds doit être une liste.' })
  @ArrayMaxSize(20, { message: '20 examinateurs au plus.' })
  @IsUUID('all', { each: true, message: 'Examinateur invalide.' })
  examinateurIds?: string[];

  /** OUVERTE (réservable) ou FERMEE (plus de nouvelle réservation). Clôture et annulation ont leur route. */
  @IsOptional()
  @IsIn([DouaneSeanceStatut.OUVERTE, DouaneSeanceStatut.FERMEE], {
    message:
      'Statut : OUVERTE ou FERMEE (clôture et annulation ont leur propre action).',
  })
  statut?: 'OUVERTE' | 'FERMEE';
}

/** POST /v1/admin/douane/seances/:id/cloture */
export class CloturerSeanceDto {
  /** Notes PRIVÉES du staff sur la séance. */
  @IsOptional()
  @IsString()
  @MaxLength(4000, { message: 'Notes : 4 000 caractères au plus.' })
  notes?: string;
}

/** POST /v1/admin/douane/seances/:id/annulation */
export class AnnulerSeanceDto {
  /** Transmis aux candidats prévenus. */
  @IsString({ message: "Indique le motif de l'annulation." })
  @MinLength(3, {
    message: "Indique le motif de l'annulation (3 caractères au moins).",
  })
  @MaxLength(500, { message: 'Motif : 500 caractères au plus.' })
  motif!: string;
}

/** GET /v1/admin/douane/seances */
export class ListerSeancesQueryDto {
  @IsOptional()
  @IsEnum(DouaneSeanceStatut, { message: 'Statut inconnu.' })
  statut?: DouaneSeanceStatut;

  /** Séances qui finissent après cette date. Défaut : depuis 24 h. */
  @IsOptional()
  @IsISO8601({}, { message: `depuis : ${MSG_DATE}` })
  depuis?: string;

  /** Séances qui commencent avant cette date. */
  @IsOptional()
  @IsISO8601({}, { message: `jusqua : ${MSG_DATE}` })
  jusqua?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(200)
  take?: number = 100;
}

// ──────────────────────────── Staff : réservations ────────────────────────────

export const EVENEMENTS_PRESENCE = ['ARRIVEE', 'APPEL', 'DEPART'] as const;
export type EvenementPresence = (typeof EVENEMENTS_PRESENCE)[number];

/** PATCH /v1/admin/douane/reservations/:id/presence */
export class PresenceDto {
  @IsIn(EVENEMENTS_PRESENCE, {
    message: 'Événement : ARRIVEE, APPEL ou DEPART.',
  })
  evenement!: EvenementPresence;
}

/** POST /v1/admin/douane/reservations/:id/annulation (« Retirer de la douane »). */
export class AnnulerReservationDto {
  /** Visible du candidat (message système). */
  @IsString({ message: "Indique le motif de l'annulation." })
  @MinLength(3, {
    message: "Indique le motif de l'annulation (3 caractères au moins).",
  })
  @MaxLength(500, { message: 'Motif : 500 caractères au plus.' })
  motif!: string;
}

// ──────────────────────────── Staff : oral hors jeu ────────────────────────────

export const RESULTATS_ORAL = [
  AppStatus.APPROVED,
  AppStatus.NEEDS_REVISION,
  AppStatus.REJECTED,
] as const;
export type ResultatOral = (typeof RESULTATS_ORAL)[number];

/**
 * PATCH /v1/admin/whitelist/:id/oral — carte « Oral » du panel : oral passé
 * hors jeu (transition §3.11, secours Q9), décidé par l'examinateur.
 */
export class OralHorsJeuDto {
  @IsIn(RESULTATS_ORAL, {
    message: 'Résultat : APPROVED, NEEDS_REVISION ou REJECTED.',
  })
  resultat!: ResultatOral;

  /**
   * Pourquoi l'oral s'est fait hors jeu (« crash », « micro du jeu »…). Privé,
   * tracé à l'audit. Obligatoire une fois la douane ouverte en jeu
   * (REBORN_DOUANE_ENABLED, secours Q9, contrôlé par le service) ; facultatif
   * pendant la transition, où tous les oraux sont hors jeu.
   */
  @IsOptional()
  @IsString({ message: "Indique pourquoi l'oral s'est passé hors jeu." })
  @MinLength(3, {
    message:
      "Indique pourquoi l'oral s'est passé hors jeu (3 caractères au moins).",
  })
  @MaxLength(300, { message: 'Motif du hors jeu : 300 caractères au plus.' })
  motifHorsJeu?: string;

  /** Motif VISIBLE du joueur. Obligatoire pour NEEDS_REVISION et REJECTED (contrôlé par le service). */
  @IsOptional()
  @IsString()
  @MaxLength(2000, { message: 'Motif : 2 000 caractères au plus.' })
  motif?: string;

  /** Notes PRIVÉES de l'examinateur, jamais montrées au joueur. */
  @IsOptional()
  @IsString()
  @MaxLength(4000, { message: 'Notes : 4 000 caractères au plus.' })
  notes?: string;
}
