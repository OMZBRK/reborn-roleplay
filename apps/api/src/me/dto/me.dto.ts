import { IsIn } from 'class-validator';

export class MarkReadDto {
  @IsIn(['tickets', 'patchnotes'])
  scope!: 'tickets' | 'patchnotes';
}
