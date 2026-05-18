import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
  Res,
  UseGuards,
} from '@nestjs/common';
import type { Response } from 'express';
import { Role } from '@prisma/client';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { MinRole } from '../auth/roles.decorator';
import { RolesGuard } from '../auth/roles.guard';
import { CreateReleaseDto, GetUpdateQueryDto } from './releases.dto';
import { ReleasesService } from './releases.service';

@Controller('launcher')
export class LauncherUpdateController {
  constructor(private readonly releases: ReleasesService) {}

  /**
   * Endpoint POLLE par le launcher au boot + toutes les 30min via
   * tauri-plugin-updater. Pas d'auth — public, le launcher n'a pas
   * encore de JWT au moment du check (avant login).
   *
   * Retourne 200 + JSON Tauri si update dispo, ou 204 No Content
   * sinon (le plugin Tauri considere 204 comme "deja a jour").
   */
  @Get('update')
  async update(
    @Query() query: GetUpdateQueryDto,
    @Res({ passthrough: true }) res: Response,
  ) {
    const found = await this.releases.findUpdate(query);
    if (!found) {
      res.status(HttpStatus.NO_CONTENT);
      return null;
    }
    return found;
  }
}

@Controller('admin/releases')
@UseGuards(JwtAuthGuard, RolesGuard)
@MinRole(Role.ADMIN)
export class AdminReleasesController {
  constructor(private readonly releases: ReleasesService) {}

  @Get()
  list() {
    return this.releases.list();
  }

  @Post()
  @HttpCode(HttpStatus.CREATED)
  create(@Body() dto: CreateReleaseDto, @CurrentUser() user: RequestUser) {
    return this.releases.create(dto, user.sub);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    await this.releases.remove(id, user.sub);
  }
}
