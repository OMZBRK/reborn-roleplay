import { IsOptional, IsString, MaxLength } from 'class-validator';

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
