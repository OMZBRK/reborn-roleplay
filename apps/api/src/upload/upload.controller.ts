import {
  Controller,
  FileTypeValidator,
  HttpCode,
  HttpStatus,
  MaxFileSizeValidator,
  ParseFilePipe,
  Post,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { maxUploadBytes } from './upload.config';
import { UploadService } from './upload.service';
import type { UploadedImageFile } from './upload.service';

/**
 * POST /v1/upload — upload d'une image (screenshot) attachée à une candidature
 * whitelist ou à un ticket. JWT-protégé (même pattern que TicketsController).
 *
 * Form-data, champ unique `file`. Memory storage multer : le service écrit le
 * buffer sur disque sous un nom aléatoire. Le plafond de taille du multer
 * borne la mémoire (dépassement → 413 via le transform multer de Nest) ;
 * `FileTypeValidator` rejette les non-images en 400.
 */
@Controller('upload')
@UseGuards(JwtAuthGuard)
export class UploadController {
  constructor(private readonly service: UploadService) {}

  @Post()
  @HttpCode(HttpStatus.CREATED)
  @UseInterceptors(
    FileInterceptor('file', {
      limits: { fileSize: maxUploadBytes() },
    }),
  )
  upload(
    @UploadedFile(
      new ParseFilePipe({
        validators: [
          new MaxFileSizeValidator({ maxSize: maxUploadBytes() }),
          new FileTypeValidator({ fileType: /^image\/(png|jpe?g|webp|gif)$/ }),
        ],
      }),
    )
    file: UploadedImageFile,
  ): Promise<{ url: string }> {
    return this.service.store(file);
  }
}
