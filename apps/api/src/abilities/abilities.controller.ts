import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Patch,
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
import { AbilitiesService } from './abilities.service';
import { CreateGraphDto, UpdateGraphDto } from './dto/abilities.dto';
import type { GraphStatus } from './dto/abilities.dto';

/**
 * `/v1/abilities/*` — Technique Creator. Édition des graphes de techniques et
 * déploiement (compile → SFTP → reload). Réservé DEVELOPPEUR+ (qui a déjà le
 * scope d'écriture SFTP sur MagicSpells/MythicMobs/ShinobiAbilities).
 */
@Controller('abilities')
@UseGuards(JwtAuthGuard, RolesGuard)
@MinRole(Role.DEVELOPPEUR)
export class AbilitiesController {
  constructor(private readonly service: AbilitiesService) {}

  @Get()
  list(@Query('status') status?: GraphStatus) {
    return this.service.list(status);
  }

  @Get(':id')
  get(@Param('id', ParseUUIDPipe) id: string) {
    return this.service.get(id);
  }

  @Post()
  @HttpCode(HttpStatus.CREATED)
  create(@Body() dto: CreateGraphDto, @CurrentUser() user: RequestUser) {
    return this.service.create(dto, user.sub);
  }

  @Patch(':id')
  update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateGraphDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.service.update(id, dto, user.sub);
  }

  @Delete(':id')
  @MinRole(Role.ADMIN)
  remove(@Param('id', ParseUUIDPipe) id: string) {
    return this.service.remove(id);
  }

  /** Validation à blanc (schéma + DAG + réfs) — renvoie les avertissements. */
  @Post(':id/validate')
  @HttpCode(HttpStatus.OK)
  validate(@Param('id', ParseUUIDPipe) id: string) {
    return this.service.validateOne(id);
  }

  /** « Tester sur moi » — enfile un aperçu de la technique sur le staff en jeu. */
  @Post(':id/preview')
  @HttpCode(HttpStatus.OK)
  preview(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    return this.service.preview(user.sub, id);
  }

  /** Compile toutes les techniques PUBLISHED + pousse par SFTP + reload. */
  @Post('deploy')
  @HttpCode(HttpStatus.OK)
  deploy(@CurrentUser() user: RequestUser) {
    return this.service.deploy(user.role, user.sub);
  }
}
