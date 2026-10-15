import { IsBoolean, IsOptional, IsString, Matches, MinLength } from 'class-validator';

export class LoginDto {
  @IsString()
  @MinLength(40)
  mcAccessToken!: string;
}

export class RefreshDto {
  @IsString()
  @MinLength(20)
  refreshToken!: string;

  /**
   * Ouvre une nouvelle famille de sessions sans consommer ce refresh token (outils staff comme manifest-uploader,
   * qui ne doivent pas faire tourner les jetons du launcher). Réservé ADMIN / OWNER.
   */
  @IsOptional()
  @IsBoolean()
  nouvelleFamille?: boolean;
}

export class DevLoginDto {
  @IsString()
  @MinLength(2)
  @Matches(/^[A-Za-z0-9_]{2,16}$/)
  username!: string;
}
