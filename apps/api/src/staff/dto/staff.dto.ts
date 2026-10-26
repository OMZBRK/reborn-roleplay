import { AppStatus, Role, TicketStatus } from '@prisma/client';
import {
  IsArray,
  IsEnum,
  IsIn,
  IsOptional,
  IsString,
  IsUrl,
  MaxLength,
  MinLength,
} from 'class-validator';

/**
 * Corps de POST /v1/staff/role (bot Discord `/role set`). `actorDiscordId` =
 * snowflake du staff qui exécute la commande (interaction.user.id) — l'API
 * l'utilise pour le garde-fou de rôle ; le bot ne fait aucune vérif lui-même.
 */
export class StaffRoleDto {
  @IsString()
  actorDiscordId!: string;

  @IsString()
  @MinLength(2)
  @MaxLength(32)
  minecraftUsername!: string;

  @IsEnum(Role)
  role!: Role;
}

/**
 * Ancienne décision globale (boutons du bot, `/whitelist accept`, PATCH
 * /admin/whitelist/:id). Depuis la candidature v2, elle tranche l'ÉCRIT en
 * entier (HRP et RP ensemble) : APPROVED = « écrit accepté », la whitelist
 * attend l'oral (CANDIDATURE_ET_DOUANE.md §1.3, §2.9).
 */
export class WhitelistDecisionDto {
  /** Doit etre APPROVED, REJECTED ou NEEDS_REVISION (pas PENDING). */
  @IsEnum(AppStatus)
  status!: AppStatus;

  /** Commentaire de l'écrit, VISIBLE du joueur. */
  @IsOptional()
  @IsString()
  @MaxLength(2000)
  reviewNotes?: string;

  /**
   * Snowflake Discord du staff qui décide (bot). Facultatif, pour l'ancien bot :
   * s'il est fourni et lié à un compte Reborn, la décision est tracée à son nom.
   */
  @IsOptional()
  @IsString()
  @MaxLength(64)
  actorDiscordId?: string;
}

/** Les deux parties de l'ÉCRIT, tranchées séparément (§1.3). L'oral a sa propre route (module douane). */
export const PARTIES_ECRIT = ['HRP', 'RP'] as const;
export type PartieEcrit = (typeof PARTIES_ECRIT)[number];

/**
 * Décision sur UNE partie de l'écrit (PATCH /admin/whitelist/:id/decision).
 * Les deux validées = « écrit accepté » (writtenApprovedAt) : le candidat
 * réserve sa séance de douane ; la whitelist attend l'oral, sauf si l'oral est
 * déjà passé sur ce compte (User.oralPassedAt). L'oral se tranche ailleurs :
 * PATCH /admin/whitelist/:id/oral (module douane).
 */
export class WhitelistPartDecisionDto {
  @IsIn(PARTIES_ECRIT)
  part!: PartieEcrit;

  /** APPROVED, REJECTED ou NEEDS_REVISION (pas PENDING). */
  @IsEnum(AppStatus)
  status!: AppStatus;

  /** Commentaire de l'écrit, VISIBLE du joueur (reviewNotes). */
  @IsOptional()
  @IsString()
  @MaxLength(2000)
  reviewNotes?: string;

  /**
   * Note PRIVÉE sur l'écrit (writtenStaffNotes) : les points à creuser,
   * transmise à l'examinateur, jamais montrée au joueur.
   */
  @IsOptional()
  @IsString()
  @MaxLength(4000)
  staffNotes?: string;
}

/**
 * Décision sur UNE partie de l'écrit depuis le bot (boutons du MP de prise en
 * charge, `/whitelist hrp|rp`) : PATCH /v1/staff/whitelist/:id/decision. Même
 * service que le panel. `actorDiscordId` est obligatoire : l'API retrouve le
 * compte Reborn lié, exige le rôle WHITELIST_REVIEWER ou plus et inscrit la
 * décision à son nom. Pas de note privée depuis Discord (elle se saisit au
 * panel).
 */
export class WhitelistPartDecisionBotDto {
  @IsIn(PARTIES_ECRIT, { message: 'part : HRP ou RP.' })
  part!: PartieEcrit;

  /** APPROVED, REJECTED ou NEEDS_REVISION (pas PENDING). */
  @IsEnum(AppStatus)
  status!: AppStatus;

  /** Commentaire de l'écrit, VISIBLE du joueur (reviewNotes). */
  @IsOptional()
  @IsString()
  @MaxLength(2000)
  reviewNotes?: string;

  /** Snowflake Discord du staff qui décide (interaction.user.id). */
  @IsString()
  @MinLength(1)
  @MaxLength(64)
  actorDiscordId!: string;
}

export class TicketStatusDto {
  /** IN_PROGRESS, RESOLVED ou CLOSED. */
  @IsEnum(TicketStatus)
  status!: TicketStatus;
}

// Posté par le bot quand un staff clique sur "Prendre en charge" ou
// "Liberer" depuis Discord. Le snowflake Discord est resolu cote API
// en User Reborn via discordUserId.
export class AssignFromDiscordDto {
  @IsString()
  @MinLength(1)
  @MaxLength(64)
  discordUserId!: string;

  @IsOptional()
  force?: boolean;
}

// Posté par le bot quand un staff répond dans le thread Discord d'une
// candidature whitelist ou d'un ticket. authorDiscordId est le snowflake
// Discord du staff (utilisé pour debug + idempotence).
export class StaffMessageDto {
  @IsString()
  @MinLength(1)
  @MaxLength(64)
  discordMessageId!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(64)
  authorDiscordId!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(128)
  authorName!: string;

  // Le content peut être vide si le staff a envoyé uniquement des pièces
  // jointes (les attachments suffisent à matérialiser le message).
  @IsString()
  @MaxLength(4000)
  content!: string;

  @IsOptional()
  @IsArray()
  @IsUrl({}, { each: true })
  attachmentUrls?: string[];
}
