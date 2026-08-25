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
import {
  CreateEntryDto,
  CreateIdeaDto,
  CreateTagDto,
  ListEntriesQueryDto,
  ListIdeasQueryDto,
  UpdateEntryDto,
  UpdateIdeaDto,
} from './dto/wiki.dto';
import { WikiService } from './wiki.service';

/**
 * `/v1/wiki/*` — Base de connaissances staff (Naruto) + banque d'idées.
 *
 * Réservée au staff : lecture/écriture gated HELPER, suppression gated
 * MODERATOR. Authentifiée via le même JWT que le panel (`/v1/admin/*`).
 */
@Controller('wiki')
@UseGuards(JwtAuthGuard, RolesGuard)
@MinRole(Role.HELPER)
export class WikiController {
  constructor(private readonly service: WikiService) {}

  // ── Entries ────────────────────────────────────────────

  @Get('entries')
  listEntries(@Query() query: ListEntriesQueryDto) {
    return this.service.listEntries(query);
  }

  @Get('entries/:id')
  getEntry(@Param('id', ParseUUIDPipe) id: string) {
    return this.service.getEntry(id);
  }

  @Post('entries')
  @HttpCode(HttpStatus.CREATED)
  createEntry(@Body() dto: CreateEntryDto, @CurrentUser() user: RequestUser) {
    return this.service.createEntry(dto, user.sub);
  }

  @Patch('entries/:id')
  updateEntry(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateEntryDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.service.updateEntry(id, dto, user.sub);
  }

  @Delete('entries/:id')
  @MinRole(Role.MODERATOR)
  deleteEntry(@Param('id', ParseUUIDPipe) id: string) {
    return this.service.deleteEntry(id);
  }

  @Get('entries/:id/revisions')
  listRevisions(@Param('id', ParseUUIDPipe) id: string) {
    return this.service.listRevisions(id);
  }

  // ── Tags ───────────────────────────────────────────────

  @Get('tags')
  listTags() {
    return this.service.listTags();
  }

  @Post('tags')
  @HttpCode(HttpStatus.CREATED)
  createTag(@Body() dto: CreateTagDto) {
    return this.service.createTag(dto);
  }

  // ── Ideas ──────────────────────────────────────────────

  @Get('ideas')
  listIdeas(@Query() query: ListIdeasQueryDto) {
    return this.service.listIdeas(query.status);
  }

  @Post('ideas')
  @HttpCode(HttpStatus.CREATED)
  createIdea(@Body() dto: CreateIdeaDto, @CurrentUser() user: RequestUser) {
    return this.service.createIdea(dto, user.sub);
  }

  @Patch('ideas/:id')
  updateIdea(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateIdeaDto,
  ) {
    return this.service.updateIdea(id, dto);
  }
}
