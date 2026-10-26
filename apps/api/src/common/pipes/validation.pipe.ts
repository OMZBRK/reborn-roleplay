import { ValidationError, ValidationPipe } from '@nestjs/common';

/**
 * ValidationPipe global (main.ts) : whitelist + forbidNonWhitelisted +
 * transform, comme avant. Seule différence : un champ inconnu ne renvoie plus
 * le message anglais de class-validator (« property x should not exist ») mais
 * un message français rattaché au champ, au format « champ: message » attendu
 * par le launcher (maquette L7). Les autres messages sont inchangés.
 */

/** Contrainte posée par class-validator sur un champ non déclaré (forbidNonWhitelisted). */
export const CONTRAINTE_CHAMP_INCONNU = 'whitelistValidation';

/** Corps envoyés par le formulaire de candidature du launcher (whitelist/dto/whitelist.dto.ts). */
const DTO_CANDIDATURE = new Set([
  'SubmitWhitelistDto',
  'RenvoiWhitelistDto',
  'BrouillonDto',
]);

/** Message d'un champ inconnu, au format « champ: message ». */
export function messageChampInconnu(
  champ: string,
  candidature: boolean,
): string {
  return candidature
    ? `${champ}: champ inconnu de la candidature (mets à jour le launcher).`
    : `${champ}: champ inconnu.`;
}

/** Remplace, en profondeur, le message des champs inconnus par sa version française. */
export function traduireChampsInconnus(
  erreurs: ValidationError[],
): ValidationError[] {
  for (const e of erreurs) {
    if (e.constraints?.[CONTRAINTE_CHAMP_INCONNU]) {
      const classe = (e.target as object | undefined)?.constructor?.name ?? '';
      // Champ imbriqué : le pipe préfixe lui-même le message du chemin parent.
      e.constraints[CONTRAINTE_CHAMP_INCONNU] = messageChampInconnu(
        e.property,
        DTO_CANDIDATURE.has(classe),
      );
    }
    if (e.children?.length) traduireChampsInconnus(e.children);
  }
  return erreurs;
}

export class RebornValidationPipe extends ValidationPipe {
  constructor() {
    super({ whitelist: true, forbidNonWhitelisted: true, transform: true });
  }

  createExceptionFactory() {
    const parDefaut = super.createExceptionFactory();
    return (erreurs: ValidationError[] = []) =>
      parDefaut(traduireChampsInconnus(erreurs));
  }
}
