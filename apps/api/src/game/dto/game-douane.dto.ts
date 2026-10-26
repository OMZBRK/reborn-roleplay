import {
  BadRequestException,
  Injectable,
  type PipeTransform,
} from '@nestjs/common';
import {
  IsIn,
  IsOptional,
  IsString,
  IsUUID,
  Matches,
  MaxLength,
} from 'class-validator';
import { RESULTATS_ORAL, type ResultatOral } from '../../douane/dto/douane.dto';

/**
 * Corps et paramètres des routes signées du serveur de jeu
 * (CANDIDATURE_ET_DOUANE.md §3.6, §3.7, §3.9). Les champs inconnus sont
 * refusés (pipe global) : le plugin envoie exactement ces champs.
 */

/** UUID Minecraft, avec tirets (forme de Bukkit) ou sans (32 hex). */
export const MOTIF_UUID_MC =
  /^(?:[0-9a-fA-F]{32}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/;

const MSG_UUID_MC = 'UUID Minecraft attendu.';

/** Paramètre de route `:mcUuid`. */
@Injectable()
export class ParseMcUuidPipe implements PipeTransform<string, string> {
  transform(valeur: string): string {
    if (typeof valeur !== 'string' || !MOTIF_UUID_MC.test(valeur)) {
      throw new BadRequestException(`mcUuid : ${MSG_UUID_MC}`);
    }
    return valeur;
  }
}

/** GET /v1/game/douane/dossier/:mcUuid?examinateur=<uuid> */
export class DossierDouaneQueryDto {
  /** UUID Minecraft de l'examinateur qui ouvre le dossier (rôle web revérifié, audit). */
  @Matches(MOTIF_UUID_MC, { message: `examinateur : ${MSG_UUID_MC}` })
  examinateur!: string;
}

/** POST /v1/game/douane/decision */
export class DecisionDouaneJeuDto {
  @Matches(MOTIF_UUID_MC, { message: `mcUuidCandidat : ${MSG_UUID_MC}` })
  mcUuidCandidat!: string;

  @Matches(MOTIF_UUID_MC, { message: `mcUuidExaminateur : ${MSG_UUID_MC}` })
  mcUuidExaminateur!: string;

  /**
   * Place tranchée (`douane.reservationId` de GET /game/acces, ou
   * `passage.reservationId` du dossier). Refus 409 si ce n'est plus la place en
   * cours : une décision en attente d'envoi ne tranche jamais une place plus récente.
   */
  @IsUUID('all', { message: 'reservationId : identifiant de place attendu.' })
  reservationId!: string;

  @IsIn(RESULTATS_ORAL, {
    message: 'resultat : APPROVED, NEEDS_REVISION ou REJECTED.',
  })
  resultat!: ResultatOral;

  /** Motif VISIBLE du candidat ; obligatoire pour NEEDS_REVISION et REJECTED (contrôlé par decideOral). */
  @IsOptional()
  @IsString({ message: 'motif : texte attendu.' })
  @MaxLength(2000, { message: 'motif : 2 000 caractères au plus.' })
  motif?: string;

  /** Notes PRIVÉES de l'examinateur, jamais montrées au candidat. */
  @IsOptional()
  @IsString({ message: 'notesPrivees : texte attendu.' })
  @MaxLength(4000, { message: 'notesPrivees : 4 000 caractères au plus.' })
  notesPrivees?: string;
}

/**
 * `arrivee`, `appel`, `depart` : présence (§3.6). `retrait` (« Retirer de la
 * douane », motif et examinateur obligatoires) et `report` (séance reportée
 * faute d'examinateur, §3.10) : la place passe CANCELLED avec son motif, sans
 * pénalité ; le candidat peut réserver une autre séance.
 */
export const EVENEMENTS_PRESENCE_JEU = [
  'arrivee',
  'appel',
  'depart',
  'retrait',
  'report',
] as const;
export type EvenementPresenceJeu = (typeof EVENEMENTS_PRESENCE_JEU)[number];

/** POST /v1/game/douane/presence */
export class PresenceDouaneJeuDto {
  @Matches(MOTIF_UUID_MC, { message: `mcUuid : ${MSG_UUID_MC}` })
  mcUuid!: string;

  @IsIn(EVENEMENTS_PRESENCE_JEU, {
    message: 'evenement : arrivee, appel, depart, retrait ou report.',
  })
  evenement!: EvenementPresenceJeu;

  /** Facultatif : la place visée. Sans elle, la place réservée en cours (ou, pour `depart`, la dernière tranchée). */
  @IsOptional()
  @IsUUID('all', { message: 'reservationId : identifiant de place attendu.' })
  reservationId?: string;

  /**
   * `appel` : l'examinateur qui appelle (inscrit à l'audit s'il est reconnu).
   * `retrait` : obligatoire, examinateur reconnu (rôle web revérifié).
   */
  @IsOptional()
  @Matches(MOTIF_UUID_MC, { message: `mcUuidExaminateur : ${MSG_UUID_MC}` })
  mcUuidExaminateur?: string;

  /** `retrait` seulement : motif VISIBLE du candidat (obligatoire). Ignoré pour les autres événements. */
  @IsOptional()
  @IsString({ message: 'motif : texte attendu.' })
  @MaxLength(2000, { message: 'motif : 2 000 caractères au plus.' })
  motif?: string;
}
