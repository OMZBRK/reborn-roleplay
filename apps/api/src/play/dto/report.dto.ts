import { TicketCategory } from '@prisma/client';
import { IsEnum, IsString, MaxLength, MinLength } from 'class-validator';

/**
 * Corps du report in-game (menu ÉCHAP → REPORT). Le mod envoie le play-token
 * lu depuis {@code reborn.playTokenPath} comme preuve d'identité : l'API en
 * vérifie la signature HMAC (sans exiger la fraîcheur — le token a pu être émis
 * >5min avant l'ouverture du menu) pour rattacher le ticket au bon compte.
 */
export class ReportDto {
  @IsString()
  @MinLength(10)
  playToken!: string;

  @IsEnum(TicketCategory)
  category!: TicketCategory;

  @IsString()
  @MinLength(4)
  @MaxLength(120)
  subject!: string;

  @IsString()
  @MinLength(10)
  @MaxLength(4000)
  message!: string;
}
