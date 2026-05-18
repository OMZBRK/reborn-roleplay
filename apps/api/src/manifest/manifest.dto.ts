import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  IsArray,
  IsBoolean,
  IsInt,
  IsISO8601,
  IsString,
  Matches,
  MaxLength,
  Min,
  MinLength,
  ValidateNested,
} from 'class-validator';

class ManifestFileDto {
  @IsString()
  @MaxLength(512)
  path!: string;

  @IsString()
  @Matches(/^[a-f0-9]{64}$/i, { message: 'sha256 doit etre 64 hex chars' })
  sha256!: string;

  @IsInt()
  @Min(0)
  size!: number;

  @IsString()
  @MaxLength(2048)
  url!: string;

  @IsBoolean()
  required!: boolean;
}

export class PublishManifestDto {
  @IsString()
  @Matches(/^\d+\.\d+\.\d+(-[\w.]+)?$/, {
    message: 'version doit etre semver X.Y.Z[-suffix]',
  })
  version!: string;

  @IsString()
  @MinLength(3)
  @MaxLength(32)
  minecraftVersion!: string;

  @IsString()
  @MinLength(3)
  @MaxLength(32)
  minLauncherVersion!: string;

  @IsISO8601()
  issuedAt!: string;

  @IsISO8601()
  expiresAt!: string;

  @IsArray()
  @ArrayMaxSize(500)
  @ValidateNested({ each: true })
  @Type(() => ManifestFileDto)
  files!: ManifestFileDto[];

  @IsString()
  @MinLength(16)
  @MaxLength(512)
  signature!: string;
}
