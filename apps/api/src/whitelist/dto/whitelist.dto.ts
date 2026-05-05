import {
  IsArray,
  IsDateString,
  IsOptional,
  IsString,
  IsUrl,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';

// DTO design v2 — schéma riche aligné sur le wizard 2 étapes du launcher
// (cf apps/launcher/src/stores/whitelist-store.ts WhitelistDraft).
// Les contraintes de longueur reproduisent celles validées côté client
// (lib/whitelist-validation.ts) — l'API reste source de vérité.
export class SubmitWhitelistDto {
  // ── Étape 1 : HRP ─────────────────────────────────────────
  @IsDateString({}, { message: 'dob doit etre une date ISO YYYY-MM-DD' })
  // Pas de regex sur YYYY-MM-DD ici car IsDateString accepte aussi le format
  // long ; on reformate côté service avec new Date(...).
  dob!: string;

  @IsString()
  @MinLength(150)
  @MaxLength(4000)
  motivation!: string;

  @IsString()
  @MinLength(150)
  @MaxLength(4000)
  experience!: string;

  @IsString()
  @MinLength(20)
  @MaxLength(2000)
  availability!: string;

  // ── Étape 2 : RP ──────────────────────────────────────────
  @IsString()
  @MinLength(1)
  @MaxLength(64)
  firstName!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(64)
  lastName!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(64)
  village!: string;

  @IsOptional()
  @IsString()
  @MaxLength(500)
  // Accepte une URL ou une chaîne vide (côté front, support reste optionnel).
  @Matches(/^(|https?:\/\/.+|\s*)$/, {
    message: 'support doit etre une URL http(s) ou vide',
  })
  support?: string;

  @IsString()
  @MinLength(150)
  @MaxLength(4000)
  history!: string;

  @IsString()
  @MinLength(150)
  @MaxLength(4000)
  appearance!: string;

  @IsString()
  @MinLength(400)
  @MaxLength(8000)
  objectives!: string;
}

// DTO pour POST /whitelist/me/messages (user → staff via Discord).
export class PostMessageDto {
  @IsString()
  @MinLength(1)
  @MaxLength(4000)
  content!: string;

  // URLs des pièces jointes — l'upload réel se fait ailleurs (TODO :
  // route dédiée ou direct upload Discord CDN). Ici on accepte juste
  // les URLs déjà accessibles publiquement.
  @IsOptional()
  @IsArray()
  @IsUrl({}, { each: true })
  attachmentUrls?: string[];
}
