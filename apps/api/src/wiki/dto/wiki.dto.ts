import { WikiEntryStatus, WikiIdeaStatus, WikiTagKind } from '@prisma/client';
import {
  IsArray,
  IsEnum,
  IsOptional,
  IsString,
  IsUUID,
  MaxLength,
  MinLength,
} from 'class-validator';

export class ListEntriesQueryDto {
  @IsOptional()
  @IsString()
  @MaxLength(200)
  q?: string;

  /** Slugs de tags, separes par virgule (repeatable/csv). */
  @IsOptional()
  @IsString()
  @MaxLength(500)
  tag?: string;

  @IsOptional()
  @IsEnum(WikiEntryStatus)
  status?: WikiEntryStatus;

  @IsOptional()
  @IsEnum(WikiTagKind)
  kind?: WikiTagKind;
}

export class CreateEntryDto {
  @IsString()
  @MinLength(1)
  @MaxLength(200)
  title!: string;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  summary?: string;

  @IsString()
  @MinLength(1)
  body!: string;

  @IsOptional()
  @IsEnum(WikiEntryStatus)
  status?: WikiEntryStatus;

  @IsOptional()
  @IsString()
  @MaxLength(2000)
  sources?: string;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  tagSlugs?: string[];
}

export class UpdateEntryDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(200)
  title?: string;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  summary?: string;

  @IsOptional()
  @IsString()
  @MinLength(1)
  body?: string;

  @IsOptional()
  @IsEnum(WikiEntryStatus)
  status?: WikiEntryStatus;

  @IsOptional()
  @IsString()
  @MaxLength(2000)
  sources?: string;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  tagSlugs?: string[];
}

export class CreateTagDto {
  @IsEnum(WikiTagKind)
  kind!: WikiTagKind;

  @IsString()
  @MinLength(1)
  @MaxLength(80)
  label!: string;

  @IsOptional()
  @IsString()
  @MaxLength(32)
  color?: string;
}

export class ListIdeasQueryDto {
  @IsOptional()
  @IsEnum(WikiIdeaStatus)
  status?: WikiIdeaStatus;
}

export class CreateIdeaDto {
  @IsString()
  @MinLength(1)
  @MaxLength(200)
  title!: string;

  @IsString()
  @MinLength(1)
  body!: string;

  @IsOptional()
  @IsString()
  @MaxLength(80)
  category?: string;

  @IsOptional()
  @IsUUID()
  linkedEntryId?: string;
}

export class UpdateIdeaDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(200)
  title?: string;

  @IsOptional()
  @IsString()
  @MinLength(1)
  body?: string;

  @IsOptional()
  @IsEnum(WikiIdeaStatus)
  status?: WikiIdeaStatus;

  @IsOptional()
  @IsString()
  @MaxLength(80)
  category?: string;
}
