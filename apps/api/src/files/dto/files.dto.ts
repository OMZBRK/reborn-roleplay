import { Type } from 'class-transformer';
import {
  IsArray,
  IsBoolean,
  IsOptional,
  IsString,
  MaxLength,
  ValidateNested,
} from 'class-validator';

/** Longueur max d'un chemin relatif accepté (garde-fou anti-abus). */
const PATH_MAX = 512;
/** Contenu texte max éditable via le panel (~2 Mo — configs, jamais des gros binaires). */
const CONTENT_MAX = 2_000_000;
/** Payload base64 max pour un upload binaire (~10 Mo → ~7,5 Mo de fichier). */
const B64_MAX = 10_000_000;

export class ListDirDto {
  @IsOptional()
  @IsString()
  @MaxLength(PATH_MAX)
  path?: string;
}

export class ReadFileDto {
  @IsString()
  @MaxLength(PATH_MAX)
  path!: string;
}

export class DeleteFileDto {
  @IsString()
  @MaxLength(PATH_MAX)
  path!: string;
}

export class WriteFileDto {
  @IsString()
  @MaxLength(PATH_MAX)
  path!: string;

  @IsString()
  @MaxLength(CONTENT_MAX)
  content!: string;
}

export class UploadFileDto {
  @IsString()
  @MaxLength(PATH_MAX)
  path!: string;

  @IsString()
  @MaxLength(B64_MAX)
  contentBase64!: string;
}

export class ReloadDto {
  @IsString()
  @MaxLength(64)
  target!: string;
}

export class MkdirDto {
  @IsString()
  @MaxLength(PATH_MAX)
  path!: string;
}

export class MoveDto {
  @IsString()
  @MaxLength(PATH_MAX)
  from!: string;

  @IsString()
  @MaxLength(PATH_MAX)
  to!: string;
}

/** Un résultat d'exécution renvoyé par le pont (une commande drainée). */
export class AckItemDto {
  @IsString()
  @MaxLength(64)
  id!: string;

  @IsBoolean()
  ok!: boolean;

  @IsOptional()
  @IsString()
  @MaxLength(4000)
  output?: string;
}

/** Corps de `POST /v1/files/commands/ack` (pont → API). */
export class AckDto {
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => AckItemDto)
  results!: AckItemDto[];
}
