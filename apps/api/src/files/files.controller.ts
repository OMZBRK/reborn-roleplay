import {
  Body,
  Controller,
  Delete,
  Get,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import { Role } from '@prisma/client';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { MinRole } from '../auth/roles.decorator';
import { RolesGuard } from '../auth/roles.guard';
import {
  DeleteFileDto,
  ListDirDto,
  ReadFileDto,
  ReloadDto,
  UploadFileDto,
  WriteFileDto,
} from './dto/files.dto';
import { FilesService } from './files.service';

/**
 * `/v1/files/*` — Gestionnaire de fichiers du serveur de DEV (Phase 1).
 *
 * Gaté au minimum MODELISATEUR (les grades techniques + admin/owner). L'accès
 * réel (racines autorisées, écriture) est déterminé PAR GRADE dans
 * {@link FilesService} (carte de scopes), pas par le rang linéaire — un
 * modélisateur ne voit que Nexo, un dev que MagicSpells/MythicMobs/ModelEngine.
 * Chaque lecture/écriture/suppression est journalisée dans l'AuditLog.
 */
@Controller('files')
@UseGuards(JwtAuthGuard, RolesGuard)
@MinRole(Role.MODELISATEUR)
export class FilesController {
  constructor(private readonly files: FilesService) {}

  /** Racines autorisées + droit d'écriture du grade appelant. */
  @Get('scopes')
  scopes(@CurrentUser() user: RequestUser) {
    return this.files.scopesFor(user.role);
  }

  @Get('list')
  list(@Query() q: ListDirDto, @CurrentUser() user: RequestUser) {
    return this.files.list(user.role, q.path);
  }

  @Get('read')
  read(@Query() q: ReadFileDto, @CurrentUser() user: RequestUser) {
    return this.files.read(user.role, user.sub, q.path);
  }

  @Post('write')
  write(@Body() dto: WriteFileDto, @CurrentUser() user: RequestUser) {
    return this.files.write(user.role, user.sub, dto.path, dto.content);
  }

  @Post('upload')
  upload(@Body() dto: UploadFileDto, @CurrentUser() user: RequestUser) {
    return this.files.upload(user.role, user.sub, dto.path, dto.contentBase64);
  }

  @Delete()
  remove(@Query() q: DeleteFileDto, @CurrentUser() user: RequestUser) {
    return this.files.remove(user.role, user.sub, q.path);
  }

  @Post('reload')
  reload(@Body() dto: ReloadDto, @CurrentUser() user: RequestUser) {
    return this.files.reload(user.role, user.sub, dto.target);
  }
}
