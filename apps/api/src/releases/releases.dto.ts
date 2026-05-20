import {
  IsIn,
  IsOptional,
  IsString,
  IsUrl,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';

const SEMVER_RE = /^\d+\.\d+\.\d+(-[\w.]+)?$/;
const TARGETS = [
  'windows-x86_64',
  'darwin-x86_64',
  'darwin-aarch64',
  'linux-x86_64',
] as const;
type Target = (typeof TARGETS)[number];

export class GetUpdateQueryDto {
  @IsString()
  @Matches(SEMVER_RE, { message: 'current doit etre X.Y.Z (semver basique).' })
  current!: string;

  @IsString()
  @IsIn(TARGETS as unknown as string[])
  target!: Target;

  @IsOptional()
  @IsString()
  @MaxLength(32)
  channel?: string;

  // Tauri passe `{{arch}}` dans l'URL d'endpoint (cf tauri.conf.json).
  // On l'accepte (optionnel, ignore) pour ne pas planter en 400 sur les
  // launchers existants qui mettent ce param systematiquement.
  @IsOptional()
  @IsString()
  @MaxLength(16)
  arch?: string;
}

export class CreateReleaseDto {
  @IsString()
  @Matches(SEMVER_RE, { message: 'version doit etre X.Y.Z (semver basique).' })
  version!: string;

  @IsString()
  @IsIn(TARGETS as unknown as string[])
  target!: Target;

  @IsOptional()
  @IsString()
  @MaxLength(32)
  channel?: string;

  @IsUrl({ require_protocol: true })
  url!: string;

  @IsString()
  @MinLength(64)
  @MaxLength(2000)
  signature!: string;

  @IsOptional()
  @IsString()
  @MaxLength(10_000)
  notes?: string;
}
