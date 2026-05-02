import { IsString, MinLength } from 'class-validator';

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
