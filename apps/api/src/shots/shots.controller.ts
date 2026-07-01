import {
  Body,
  Controller,
  Delete,
  FileTypeValidator,
  Get,
  HttpCode,
  HttpStatus,
  MaxFileSizeValidator,
  Param,
  ParseFilePipe,
  Post,
  Query,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { maxUploadBytes } from '../upload/upload.config';
import type { UploadedImageFile } from '../upload/upload.service';
import { CreateShotDto, FeedQueryDto } from './dto/shots.dto';
import { ShotsService } from './shots.service';

/**
 * Galerie sociale de screenshots. JWT-protégé. Le launcher upload l'image
 * (il détient l'auth), l'API stocke + expose le feed + les likes.
 */
@Controller('shots')
@UseGuards(JwtAuthGuard)
export class ShotsController {
  constructor(private readonly service: ShotsService) {}

  /** Partage un screenshot (form-data `file` + `caption` optionnel). */
  @Post()
  @HttpCode(HttpStatus.CREATED)
  @UseInterceptors(
    FileInterceptor('file', { limits: { fileSize: maxUploadBytes() } }),
  )
  create(
    @CurrentUser() user: RequestUser,
    @UploadedFile(
      new ParseFilePipe({
        validators: [
          new MaxFileSizeValidator({ maxSize: maxUploadBytes() }),
          new FileTypeValidator({ fileType: /^image\/(png|jpe?g|webp|gif)$/ }),
        ],
      }),
    )
    file: UploadedImageFile,
    @Body() dto: CreateShotDto,
  ) {
    return this.service.create(user.sub, file, dto.caption);
  }

  /** Feed public (plus récents d'abord), pagination curseur. */
  @Get('feed')
  feed(@CurrentUser() user: RequestUser, @Query() query: FeedQueryDto) {
    return this.service.feed(user.sub, query.limit, query.cursor);
  }

  /** Mes screenshots partagés. */
  @Get('mine')
  mine(@CurrentUser() user: RequestUser) {
    return this.service.mine(user.sub);
  }

  /** Toggle like. */
  @Post(':id/like')
  like(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.service.toggleLike(user.sub, id);
  }

  /** Supprime son propre screenshot. */
  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  remove(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.service.remove(user.sub, id);
  }
}
