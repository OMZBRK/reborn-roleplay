import { IsString, Matches, MinLength } from 'class-validator';

export class LoginDto {
  @IsString()
  @MinLength(40)
  mcAccessToken!: string;
}

export class RefreshDto {
  @IsString()
  @MinLength(20)
  refreshToken!: string;
}

export class DevLoginDto {
  @IsString()
  @MinLength(2)
  @Matches(/^[A-Za-z0-9_]{2,16}$/)
  username!: string;
}
