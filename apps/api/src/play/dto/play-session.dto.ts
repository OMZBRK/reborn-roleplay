import { IsOptional, IsString, Matches } from 'class-validator';
import { SERVER_ID_PATTERN } from '../play.service';

/**
 * Corps (facultatif) de `POST /v1/play/session`. Les launchers actuels n'envoient aucun corps :
 * le jeton vise alors le serveur par défaut de l'API (`REBORN_SERVER_ID`).
 *
 * `serveur` : identifiant du serveur que le launcher va rejoindre (même valeur que la clé
 * `server-id` de Guardian). Il doit figurer dans `REBORN_SERVER_IDS` ou valoir
 * `REBORN_SERVER_ID` ; sinon 400.
 */
export class PlaySessionDto {
  @IsOptional()
  @IsString()
  @Matches(SERVER_ID_PATTERN, {
    message:
      'Identifiant de serveur invalide : lettres minuscules, chiffres et tirets, 32 caractères au plus.',
  })
  serveur?: string;
}
