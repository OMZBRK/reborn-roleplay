import {
  Body,
  Controller,
  Delete,
  Get,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ExigeCapacite } from '../auth/capacite.decorator';
import type { Capacite } from '../auth/capacites';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { RolesGuard } from '../auth/roles.guard';
import {
  CreateAnimatedItemDto,
  DeleteFileDto,
  ListDirDto,
  MkdirDto,
  MoveDto,
  ReadFileDto,
  ReloadDto,
  UploadFileDto,
  WriteFileDto,
} from './dto/files.dto';
import { FilesService } from './files.service';
import { ZONES_FICHIERS } from './files.scopes';

/** N'importe quelle zone de fichiers : la garde laisse passer, la carte tranche. */
const UNE_ZONE_FICHIERS: Capacite[] = ZONES_FICHIERS.map(
  (z): Capacite => `fichiers.${z.zone}`,
);

/**
 * `/v1/files/*` — Gestionnaire de fichiers du serveur de DEV (Phase 1).
 *
 * Garde : au moins une capacité `fichiers.<zone>` (modélisateur, développeur,
 * admin, owner — inchangé pour eux). Recruteur et Modérateur passaient
 * l'ancienne garde de rang sans rien voir ; ils sont refusés dès la garde.
 * L'accès réel (racines autorisées, écriture) reste déterminé PAR GRADE dans
 * la carte de files.scopes.ts — un modélisateur ne voit que Nexo, emotes et
 * creator-assets, un dev en plus MagicSpells/MythicMobs/ModelEngine/abilities.
 * Chaque lecture/écriture/suppression est journalisée dans l'AuditLog.
 */
@Controller('files')
@UseGuards(JwtAuthGuard, RolesGuard)
@ExigeCapacite(...UNE_ZONE_FICHIERS)
export class FilesController {
  constructor(private readonly files: FilesService) {}

  /**
   * Racines autorisées + droit d'écriture du grade appelant. Ouvert à tout le
   * staff (réponse vide sans zone) : le panel s'en sert pour savoir quoi
   * afficher, comme avant.
   */
  @Get('scopes')
  @ExigeCapacite('panel.acceder')
  scopes(@CurrentUser() user: RequestUser) {
    return this.files.scopesFor(user.role);
  }

  /** Cibles de rechargement (plugins) accessibles au grade appelant (vide sans zone). */
  @Get('reload-targets')
  @ExigeCapacite('panel.acceder')
  reloadTargets(@CurrentUser() user: RequestUser) {
    return this.files.reloadTargetsFor(user.role);
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

  @Post('mkdir')
  mkdir(@Body() dto: MkdirDto, @CurrentUser() user: RequestUser) {
    return this.files.mkdir(user.role, user.sub, dto.path);
  }

  @Post('move')
  move(@Body() dto: MoveDto, @CurrentUser() user: RequestUser) {
    return this.files.move(user.role, user.sub, dto.from, dto.to);
  }

  @Post('reload')
  reload(@Body() dto: ReloadDto, @CurrentUser() user: RequestUser) {
    return this.files.reload(user.role, user.sub, dto.target);
  }

  /**
   * Générateur « item animé Nexo » — une spritesheet PNG → modèle + animation +
   * entrée Nexo écrits d'un coup, puis `nexo reload`. Renvoie `nexo:<id>` prêt à
   * coller dans un effet MagicSpells `itemdisplay`.
   */
  @Post('nexo/animated-item')
  createAnimatedItem(
    @Body() dto: CreateAnimatedItemDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.files.createAnimatedItem(user.role, user.sub, dto);
  }
}
