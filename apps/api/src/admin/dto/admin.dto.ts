import { AppStatus, Role, TicketStatus } from '@prisma/client';
import {
  IsEnum,
  IsIn,
  IsInt,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';
import { Type } from 'class-transformer';

/**
 * Corps de PATCH /v1/admin/players/:id/role (panel staff). Le motif est
 * obligatoire (PANEL_V2 §3.4, §6.6) : il est inscrit au journal avec le
 * changement.
 */
export class ChangeRoleDto {
  @IsEnum(Role, { message: 'Grade inconnu.' })
  role!: Role;

  @IsString({ message: 'Le motif doit être un texte.' })
  @MinLength(3, { message: 'Motif : 3 caractères au moins.' })
  @MaxLength(500, { message: 'Motif : 500 caractères au plus.' })
  motif!: string;
}

/**
 * Onglets « à faire » de la liste whitelist (maquette P1), calculés sur les
 * mêmes règles que la phase (CANDIDATURE_ET_DOUANE.md §1.2) :
 *  - ECRIT : écrit à relire (ECRIT_EN_ATTENTE) ;
 *  - DOUANE : « Douane à faire » (DOUANE_A_RESERVER, DOUANE_RESERVEE,
 *    DOUANE_A_REVOIR) ;
 *  - REVISION : écrit renvoyé au candidat (ECRIT_A_REVOIR).
 */
export const FILTRES_WHITELIST = ['ECRIT', 'DOUANE', 'REVISION'] as const;
export type FiltreWhitelist = (typeof FILTRES_WHITELIST)[number];

export class ListWhitelistQueryDto {
  @IsOptional()
  @IsEnum(AppStatus)
  status?: AppStatus;

  @IsOptional()
  @IsIn(FILTRES_WHITELIST, {
    message: 'Filtre : ECRIT, DOUANE ou REVISION.',
  })
  filtre?: FiltreWhitelist;

  /** Recherche : pseudo Minecraft, pseudo Discord, prénom ou nom du personnage. */
  @IsOptional()
  @IsString()
  @MaxLength(64, { message: 'Recherche : 64 caractères au plus.' })
  q?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(200)
  take?: number = 50;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  skip?: number = 0;
}

export class ListTicketsQueryDto {
  @IsOptional()
  @IsEnum(TicketStatus)
  status?: TicketStatus;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(200)
  take?: number = 50;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  skip?: number = 0;
}

export class TicketStatusUpdateDto {
  @IsEnum(TicketStatus)
  status!: TicketStatus;
}

/**
 * PATCH /v1/admin/whitelist/:id/note-privee — note PRIVÉE du staff sur
 * l'écrit (writtenStaffNotes) : les points à creuser à l'oral. Jamais
 * renvoyée au joueur. Chaîne vide = note effacée.
 */
export class NotePriveeEcritDto {
  @IsString({ message: 'La note doit être un texte.' })
  @MaxLength(4000, { message: 'Note privée : 4 000 caractères au plus.' })
  note!: string;
}

export class PanelMessageDto {
  @IsString()
  @MinLength(1)
  @MaxLength(4000)
  content!: string;
}

/** Filtre Statut de la liste Joueurs (admin.service › whereFiltresJoueurs). */
export const STATUTS_JOUEUR = ['whiteliste', 'non', 'banni'] as const;

export class SearchPlayersQueryDto {
  @IsOptional()
  @IsString()
  @MaxLength(128)
  q?: string = '';

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  take?: number = 30;

  /** Filtre Grade, appliqué en base avant la limite. */
  @IsOptional()
  @IsEnum(Role, { message: 'Grade inconnu.' })
  role?: Role;

  /** Filtre Statut : whitelistés, non whitelistés ou bannis. */
  @IsOptional()
  @IsIn(STATUTS_JOUEUR, {
    message: 'Statut : whiteliste, non ou banni.',
  })
  statut?: (typeof STATUTS_JOUEUR)[number];
}

/** GET /v1/admin/anomalies : non vues seulement, ou celles d'un joueur. */
export class ListAnomaliesQueryDto {
  @IsOptional()
  @IsIn(['true', '1', 'false', '0'])
  unack?: string;

  @IsOptional()
  @IsUUID('all', { message: 'Identifiant de joueur invalide.' })
  userId?: string;
}

export class ListAuditQueryDto {
  @IsOptional()
  @IsString()
  @MaxLength(128)
  actor?: string;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  action?: string;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  source?: string;

  @IsOptional()
  @IsString()
  @MaxLength(128)
  targetUserId?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(200)
  take?: number = 50;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  skip?: number = 0;
}
