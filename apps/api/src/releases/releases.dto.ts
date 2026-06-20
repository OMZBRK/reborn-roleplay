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

/**
 * Plateformes courtes envoyees par Tauri 2 quand le placeholder
 * `{{target}}` est utilise seul (au lieu de `{{target}}-{{arch}}`).
 * La service combine target + arch quand on recoit la forme courte,
 * pour rester retro-compatible avec les launchers v0.3.0 -> v0.3.2 dont
 * la config endpoint envoie `target={{target}}&arch={{arch}}`.
 */
const SHORT_TARGETS = ['windows', 'darwin', 'linux'] as const;
const ACCEPTED_TARGETS = [...TARGETS, ...SHORT_TARGETS];

export class GetUpdateQueryDto {
  @IsString()
  @Matches(SEMVER_RE, { message: 'current doit etre X.Y.Z (semver basique).' })
  current!: string;

  @IsString()
  @IsIn(ACCEPTED_TARGETS as unknown as string[])
  target!: string;

  @IsOptional()
  @IsString()
  @MaxLength(32)
  channel?: string;

  // Tauri passe `{{arch}}` dans l'URL d'endpoint (cf tauri.conf.json).
  // Quand `target` est la forme courte ("windows"), on combine avec
  // `arch` pour reconstruire "windows-x86_64". Cf
  // ReleasesService.normalizeTarget.
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
