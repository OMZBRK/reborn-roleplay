import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsBoolean,
  IsIn,
  IsOptional,
  IsString,
  Length,
  Matches,
  MaxLength,
  ValidateNested,
} from 'class-validator';
import { STAFF_MEDIA_ID_RE } from '../staff-media.config';

export const CANAUX = ['annonces', 'dev-updates'] as const;
export const PINGS = ['aucun', 'testeurs', 'everyone'] as const;
export const ACTIONS = ['brouillon', 'apercu', 'publier'] as const;
export type Canal = (typeof CANAUX)[number];
export type PingOption = (typeof PINGS)[number];
export type ActionMessage = (typeof ACTIONS)[number];

/** Bouton-lien (5 max par rangée, libellé ≤ 80 — limites Discord). */
export class LienDto {
  @IsString()
  @Length(1, 80)
  libelle!: string;

  @IsString()
  @MaxLength(512)
  @Matches(/^https?:\/\/\S+$/, { message: 'url doit commencer par http(s)://' })
  url!: string;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  emoji?: string;
}

/** Un bloc du cadre : texte, galerie, séparateur ou rangée de liens. */
export class BlocDto {
  @IsOptional()
  @IsString()
  @MaxLength(4000)
  texte?: string;

  @IsOptional()
  @IsArray()
  @ArrayMaxSize(10)
  @Matches(STAFF_MEDIA_ID_RE, {
    each: true,
    message: 'image inconnue (téléverse-la d’abord)',
  })
  images?: string[];

  @IsOptional()
  @IsBoolean()
  separateur?: boolean;

  @IsOptional()
  @IsArray()
  @ArrayMaxSize(5)
  @ValidateNested({ each: true })
  @Type(() => LienDto)
  liens?: LienDto[];
}

/** Corps commun création / modification. */
export class StaffMessageBodyDto {
  @IsIn(CANAUX)
  canal!: Canal;

  @IsOptional()
  @Matches(/^#[0-9a-fA-F]{6}$/, { message: 'couleur au format #RRGGBB' })
  couleur?: string;

  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(30)
  @ValidateNested({ each: true })
  @Type(() => BlocDto)
  blocs!: BlocDto[];

  @IsOptional()
  @IsIn(PINGS)
  ping?: PingOption;

  /** brouillon (défaut à la création) · apercu · publier. */
  @IsOptional()
  @IsIn(ACTIONS)
  action?: ActionMessage;
}

export class ListStaffMessagesQueryDto {
  @IsOptional()
  @IsIn(CANAUX)
  canal?: Canal;
}
