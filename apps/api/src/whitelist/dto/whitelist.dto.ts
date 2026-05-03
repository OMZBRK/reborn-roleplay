import { IsInt, IsString, MaxLength, Min, MinLength, Max } from 'class-validator';

export class SubmitWhitelistDto {
  @IsString()
  @MinLength(2)
  @MaxLength(32)
  characterName!: string;

  @IsInt()
  @Min(13)
  @Max(120)
  characterAge!: number;

  @IsString()
  @MinLength(50)
  @MaxLength(4000)
  background!: string;

  @IsString()
  @MinLength(20)
  @MaxLength(2000)
  motivation!: string;
}
