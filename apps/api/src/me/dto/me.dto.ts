import { IsIn, IsString, Length, Matches } from 'class-validator';

export class MarkReadDto {
  @IsIn(['tickets', 'patchnotes'])
  scope!: 'tickets' | 'patchnotes';
}

export class UpdateProfileDto {
  /** Nom d'affichage RP (le pseudo Minecraft, lui, n'est pas modifiable). */
  @IsString()
  @Length(2, 24)
  @Matches(/^[\p{L}\p{N} '_.-]+$/u, {
    message: 'displayName: lettres, chiffres, espaces et - _ . \' uniquement',
  })
  displayName!: string;
}
