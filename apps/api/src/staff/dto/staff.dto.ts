import { AppStatus, TicketStatus } from '@prisma/client';
import {
  IsArray,
  IsEnum,
  IsOptional,
  IsString,
  IsUrl,
  MaxLength,
  MinLength,
} from 'class-validator';

export class WhitelistDecisionDto {
  /** Doit etre APPROVED, REJECTED ou NEEDS_REVISION (pas PENDING). */
  @IsEnum(AppStatus)
  status!: AppStatus;

  @IsOptional()
  @IsString()
  @MaxLength(2000)
  reviewNotes?: string;
}

export class TicketStatusDto {
  /** IN_PROGRESS, RESOLVED ou CLOSED. */
  @IsEnum(TicketStatus)
  status!: TicketStatus;
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
