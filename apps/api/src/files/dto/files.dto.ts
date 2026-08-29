import { Type } from 'class-transformer';
import {
  IsArray,
  IsBoolean,
  IsInt,
  IsOptional,
  IsString,
  Matches,
  Max,
  MaxLength,
  Min,
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

/**
 * Corps de `POST /v1/files/nexo/animated-item` — générateur « item animé » en
 * un coup : à partir d'une spritesheet PNG, l'API écrit dans le pack Nexo le
 * modèle plat (réfs texture correctes), le `.png.mcmeta` d'animation et l'entrée
 * `items/<id>.yml`, puis file un `nexo reload`. Élimine les 2 pièges silencieux
 * (réf Blockbench + `.mcmeta` mal nommé/orienté).
 */
export class CreateAnimatedItemDto {
  /** Identifiant Nexo (minuscules/chiffres/underscore) → `nexo:<id>`. */
  @IsString()
  @Matches(/^[a-z0-9_]+$/, {
    message: 'id : minuscules, chiffres et underscore uniquement.',
  })
  @MaxLength(48)
  id!: string;

  /** Spritesheet PNG (verticale : frames carrées empilées) en base64. */
  @IsString()
  @MaxLength(B64_MAX)
  spriteBase64!: string;

  /** Nom affiché de l'item (défaut = id). */
  @IsOptional()
  @IsString()
  @MaxLength(64)
  name?: string;

  /** Nombre de frames. Auto-détecté depuis la PNG (hauteur/largeur) si absent. */
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(256)
  frames?: number;

  /** Ticks par frame (défaut 2). */
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(200)
  frametime?: number;

  /** Force l'animation on/off ; sinon déduit des dimensions (H > L → animé). */
  @IsOptional()
  @IsBoolean()
  animated?: boolean;
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
