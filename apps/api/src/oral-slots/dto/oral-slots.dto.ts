import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsInt,
  IsISO8601,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';

/** Un créneau à ouvrir (branche horaire). */
export class OpenSlotItemDto {
  @IsISO8601()
  startAt!: string;

  @IsOptional()
  @IsInt()
  @Min(5)
  @Max(180)
  durationMin?: number;
}

/** Le staff ouvre un lot de créneaux d'un coup (pool global). */
export class OpenSlotsDto {
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(64)
  @ValidateNested({ each: true })
  @Type(() => OpenSlotItemDto)
  slots!: OpenSlotItemDto[];
}

/** Le staff clôt un oral (créneau passé) avec des notes optionnelles. */
export class MarkSlotDoneDto {
  @IsOptional()
  @IsString()
  @MaxLength(2000)
  notes?: string;
}
