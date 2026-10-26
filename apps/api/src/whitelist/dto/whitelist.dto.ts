import {
  Allow,
  IsArray,
  IsInt,
  IsObject,
  IsOptional,
  IsString,
  IsUrl,
  Max,
  MaxLength,
  Min,
  MinLength,
  Validate,
  ValidationArguments,
  ValidatorConstraint,
  ValidatorConstraintInterface,
} from 'class-validator';
import { estEnvoiV2, verifierChamp } from '../whitelist.rules';

/**
 * Contrôle d'un champ de la candidature v2 (règles dans whitelist.rules.ts ›
 * verifierChamp). Le message renvoyé commence par le nom du champ,
 * « history: … », pour que le launcher le rattache au bon champ.
 *
 * Un envoi sans `formVersion: 2` n'est pas contrôlé champ par champ ici : le
 * service le refuse d'un bloc, en 400 « Mets ton launcher à jour pour envoyer
 * ta candidature. » (complément du 25/10), le même message en POST et en PATCH.
 */
@ValidatorConstraint({ name: 'regleCandidature', async: false })
export class RegleCandidature implements ValidatorConstraintInterface {
  validate(valeur: unknown, args: ValidationArguments): boolean {
    if (!estEnvoiV2(args.object)) return true;
    return verifierChamp(args.property, valeur) === null;
  }

  defaultMessage(args: ValidationArguments): string {
    const msg = verifierChamp(args.property, args.value);
    return `${args.property}: ${msg ?? 'valeur invalide.'}`;
  }
}

/**
 * Corps de POST /v1/whitelist (PATCH /v1/whitelist/me : RenvoiWhitelistDto,
 * mêmes règles appliquées par le service selon les parties rouvertes).
 *
 * Seule la candidature v2 s'envoie (complément du 25/10, plus de voie « ancien
 * format ») : `formVersion: 2`, règles strictes vérifiées ici (âge ≥
 * WHITELIST_AGE_MIN, village dans la liste fermée, nom de famille libre de 3 à
 * 20 lettres qui n'est pas un nom de clan, histoire de 150 à 12 000, maximums
 * partout). Sans `formVersion: 2`, le service répond 400 « Mets ton launcher à
 * jour pour envoyer ta candidature. ». Les champs retirés (« Ton expérience du
 * roleplay », « Pourquoi Reborn ? », « Caractère et apparence », date de
 * naissance) restent déclarés pour que l'ancien launcher reçoive ce message
 * plutôt que « champ inconnu » ; dans un envoi v2, ils sont refusés.
 *
 * La voix ne passe pas ici : elle part avant, en corps brut, sur
 * POST /v1/whitelist/me/voix (brouillon), puis rejoint la candidature à l'envoi.
 */
export class SubmitWhitelistDto {
  /** 2 obligatoirement (candidature v2) ; absent ou autre valeur = 400 « mets ton launcher à jour ». */
  @Validate(RegleCandidature)
  formVersion?: number;

  // ── Partie HRP ──
  /** Retiré (ancien formulaire) : refusé dans un envoi v2. */
  @Validate(RegleCandidature)
  dob?: string;

  /** v2 : âge déclaré, entier de WHITELIST_AGE_MIN (15) à 99. */
  @Validate(RegleCandidature)
  playerAge?: number;

  /** v2 : RpYears. */
  @Validate(RegleCandidature)
  rpYears?: string;

  /** v2 : NarutoKnowledge. */
  @Validate(RegleCandidature)
  narutoKnowledge?: string;

  /** v2, facultatif : DiscoverySource ; null ou absent = « Pas de réponse ». */
  @Validate(RegleCandidature)
  discoverySource?: string | null;

  /** Retiré (« Pourquoi Reborn ? ») : refusé dans un envoi v2. */
  @Validate(RegleCandidature)
  motivation?: string;

  /** Retiré (« Ton expérience du roleplay ») : refusé dans un envoi v2. */
  @Validate(RegleCandidature)
  experience?: string;

  @Validate(RegleCandidature)
  availability!: string;

  // ── Partie RP ──
  @Validate(RegleCandidature)
  firstName!: string;

  /** Nom de famille libre, jamais un nom de clan. */
  @Validate(RegleCandidature)
  lastName!: string;

  /** v2 : une des six valeurs exactes du mod (« Konohagakure »…). */
  @Validate(RegleCandidature)
  village!: string;

  /** v2 : CharacterType. */
  @Validate(RegleCandidature)
  characterType?: string;

  /** v2 : CharacterSex. */
  @Validate(RegleCandidature)
  characterSex?: string;

  /** v2 : 10 à 60. */
  @Validate(RegleCandidature)
  characterAge?: number;

  /** Facultatif : lien https://, ou vide. */
  @Validate(RegleCandidature)
  support?: string | null;

  @Validate(RegleCandidature)
  history!: string;

  /** Retiré (« Caractère et apparence ») : refusé dans un envoi v2. */
  @Validate(RegleCandidature)
  appearance?: string;

  @Validate(RegleCandidature)
  objectives!: string;

  // ── Onglet Envoi (les deux cases doivent être cochées) ──
  @Validate(RegleCandidature)
  rulesAccepted?: boolean;

  @Validate(RegleCandidature)
  voiceConsent?: boolean;
}

/** Champs de l'envoi, tels que le service les lit (POST et PATCH), tous contrôlés avant usage. */
export interface ChampsCandidature {
  formVersion?: number;
  dob?: string;
  playerAge?: number;
  rpYears?: string;
  narutoKnowledge?: string;
  discoverySource?: string | null;
  motivation?: string;
  experience?: string;
  availability?: string;
  firstName?: string;
  lastName?: string;
  village?: string;
  characterType?: string;
  characterSex?: string;
  characterAge?: number;
  support?: string | null;
  history?: string;
  appearance?: string;
  objectives?: string;
  rulesAccepted?: boolean;
  voiceConsent?: boolean;
}

/**
 * Corps de PATCH /v1/whitelist/me : mêmes champs que l'envoi, contrôlés par le
 * SERVICE (whitelist.rules.ts › verifierEnvoi), pas ici. Après « À revoir »,
 * seules les parties rouvertes sont exigées ; les champs d'une partie
 * verrouillée sont ignorés et jamais écrits (§2.7). Après un refus, tout le
 * formulaire est contrôlé. `@Allow()` déclare chaque champ : un champ inconnu
 * reste refusé par le ValidationPipe global (forbidNonWhitelisted).
 */
export class RenvoiWhitelistDto implements ChampsCandidature {
  @Allow() formVersion?: number;
  @Allow() dob?: string;
  @Allow() playerAge?: number;
  @Allow() rpYears?: string;
  @Allow() narutoKnowledge?: string;
  @Allow() discoverySource?: string | null;
  @Allow() motivation?: string;
  @Allow() experience?: string;
  @Allow() availability?: string;
  @Allow() firstName?: string;
  @Allow() lastName?: string;
  @Allow() village?: string;
  @Allow() characterType?: string;
  @Allow() characterSex?: string;
  @Allow() characterAge?: number;
  @Allow() support?: string | null;
  @Allow() history?: string;
  @Allow() appearance?: string;
  @Allow() objectives?: string;
  @Allow() rulesAccepted?: boolean;
  @Allow() voiceConsent?: boolean;
}

/**
 * Corps de PUT /v1/whitelist/me/brouillon : le formulaire tel quel (contrôle
 * souple, aucun minimum). Le service borne la taille (256 Ko) et la longueur des
 * textes ; les colonnes de voix ne s'écrivent jamais par ici.
 */
export class BrouillonDto {
  @IsObject()
  data!: Record<string, unknown>;

  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(2)
  formVersion?: number;
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
