import { Type } from 'class-transformer';
import { IsInt, IsOptional, IsString, Max, MaxLength, Min } from 'class-validator';

export class CreateShotDto {
  @IsOptional()
  @IsString()
  @MaxLength(280)
  caption?: string;
}

export class FeedQueryDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  limit?: number;

  /** ID du dernier screenshot déjà chargé (pagination curseur). */
  @IsOptional()
  @IsString()
  cursor?: string;
}
