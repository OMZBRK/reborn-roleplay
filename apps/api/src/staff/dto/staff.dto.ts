import { AppStatus, TicketStatus } from '@prisma/client';
import { IsEnum, IsOptional, IsString, MaxLength } from 'class-validator';

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
