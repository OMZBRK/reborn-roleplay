import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  NotFoundException,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
  Res,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { Role } from '@prisma/client';
import type { Response } from 'express';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { MinRole } from '../auth/roles.decorator';
import { RolesGuard } from '../auth/roles.guard';
import type { UploadedImageFile } from '../upload/upload.service';
import {
  ListStaffMessagesQueryDto,
  StaffMessageBodyDto,
} from './dto/staff-messages.dto';
import {
  STAFF_MEDIA_MAX_BYTES,
  staffMediaMime,
  verifyStaffMediaSignature,
} from './staff-media.config';
import { StaffMediaService } from './staff-media.service';
import { StaffMessagesService } from './staff-messages.service';

/**
 * `/v1/admin/discord/*` — annonces et dev updates du Discord STAFF rédigées
 * dans le panel (page « Discord »), postées par le bot via le webhook signé
 * `/webhooks/staff-message`.
 *
 * Accès : ADMIN et plus (rang le plus proche de « Direction / Lead » dans la
 * hiérarchie Reborn : PLAYER < … < MODERATOR < ADMIN < OWNER). Chaque action
 * est journalisée (module audit, actions `discord.message.*`).
 */
@Controller('admin/discord')
@UseGuards(JwtAuthGuard, RolesGuard)
@MinRole(Role.ADMIN)
export class StaffMessagesController {
  constructor(
    private readonly messages: StaffMessagesService,
    private readonly media: StaffMediaService,
  ) {}

  @Get('messages')
  list(@Query() q: ListStaffMessagesQueryDto) {
    return this.messages.list(q.canal);
  }

  @Get('messages/:id')
  get(@Param('id', ParseUUIDPipe) id: string) {
    return this.messages.get(id);
  }

  /** Crée un brouillon ; `action` = apercu | publier l'enchaîne. */
  @Post('messages')
  create(@CurrentUser() user: RequestUser, @Body() dto: StaffMessageBodyDto) {
    return this.messages.create(user, dto);
  }

  /** Modifie (contenu complet) ; sur un message publié, `publier` réécrit le message Discord. */
  @Patch('messages/:id')
  update(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
    @Body() dto: StaffMessageBodyDto,
  ) {
    return this.messages.update(id, user, dto);
  }

  /** Publié : retiré de Discord (gardé en historique). Brouillon : effacé. */
  @Delete('messages/:id')
  remove(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    return this.messages.remove(id, user);
  }

  /** Téléverse une image (multipart, champ `file`, 8 Mo max, PNG/JPEG/WebP/GIF vérifiés aux octets). */
  @Post('media')
  @HttpCode(HttpStatus.CREATED)
  @UseInterceptors(
    FileInterceptor('file', { limits: { fileSize: STAFF_MEDIA_MAX_BYTES } }),
  )
  upload(@UploadedFile() file: UploadedImageFile) {
    return this.media.store(file);
  }
}

/**
 * Lecture d'une image par URL signée (pas de JWT : une balise <img> ne porte
 * pas d'en-tête Authorization). La signature HMAC + expiration est émise par
 * l'API dans les réponses ci-dessus, valable 1 h.
 */
@Controller('admin/discord/media')
export class StaffMediaPublicController {
  constructor(private readonly media: StaffMediaService) {}

  @Get(':id')
  read(
    @Param('id') id: string,
    @Query('exp') exp: string,
    @Query('sig') sig: string,
    @Res() res: Response,
  ) {
    if (!verifyStaffMediaSignature(id, Number(exp), String(sig ?? ''))) {
      throw new NotFoundException();
    }
    res.setHeader('Content-Type', staffMediaMime(id));
    res.setHeader('Cache-Control', 'private, max-age=3600');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.sendFile(this.media.path(id), (err) => {
      if (err && !res.headersSent) res.status(404).end();
    });
  }
}
