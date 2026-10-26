import {
  Body,
  Controller,
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
import { OralVia, Role } from '@prisma/client';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { MinRole } from '../auth/roles.decorator';
import { RolesGuard } from '../auth/roles.guard';
import {
  AnnulerReservationDto,
  AnnulerSeanceDto,
  CloturerSeanceDto,
  CreerSeanceDto,
  ListerSeancesQueryDto,
  ModifierSeanceDto,
  OralHorsJeuDto,
  PresenceDto,
} from './dto/douane.dto';
import { DouaneOralService } from './douane-oral.service';
import { DouaneService } from './douane.service';

/**
 * Panel staff : séances de douane, réservations, présence, clôture
 * (CANDIDATURE_ET_DOUANE.md §3.3, §3.8) et carte « Oral » hors jeu (§3.11,
 * Q9). Même garde que `AdminController` : JWT du panel, rôle
 * WHITELIST_REVIEWER au minimum.
 *
 * Préfixe `admin` partagé avec `AdminController` : les routes d'ici sont
 * sous `admin/douane/*`, plus `admin/whitelist/:id/oral` (à côté de
 * `admin/whitelist/:id/decision`, la décision de l'écrit).
 */
@Controller('admin')
@UseGuards(JwtAuthGuard, RolesGuard)
@MinRole(Role.WHITELIST_REVIEWER)
export class DouaneAdminController {
  constructor(
    private readonly douane: DouaneService,
    private readonly oral: DouaneOralService,
  ) {}

  // ── Séances ─────────────────────────────────────────────

  @Get('douane/seances')
  lister(@Query() query: ListerSeancesQueryDto) {
    return this.douane.listerSeances(query);
  }

  @Get('douane/seances/:id')
  detail(@Param('id', ParseUUIDPipe) id: string) {
    return this.douane.detailSeance(id);
  }

  @Post('douane/seances')
  @HttpCode(HttpStatus.CREATED)
  creer(@Body() dto: CreerSeanceDto, @CurrentUser() user: RequestUser) {
    return this.douane.creerSeance(user.sub, dto);
  }

  @Patch('douane/seances/:id')
  modifier(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: ModifierSeanceDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.douane.modifierSeance(user.sub, id, dto);
  }

  @Post('douane/seances/:id/cloture')
  @HttpCode(HttpStatus.OK)
  cloturer(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: CloturerSeanceDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.douane.cloturerSeance(user.sub, id, dto);
  }

  @Post('douane/seances/:id/annulation')
  @HttpCode(HttpStatus.OK)
  annulerSeance(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: AnnulerSeanceDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.douane.annulerSeance(user.sub, id, dto);
  }

  // ── Réservations ────────────────────────────────────────

  @Patch('douane/reservations/:id/presence')
  presence(@Param('id', ParseUUIDPipe) id: string, @Body() dto: PresenceDto) {
    return this.douane.presence(id, dto.evenement);
  }

  @Post('douane/reservations/:id/absence')
  @HttpCode(HttpStatus.OK)
  absence(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    return this.douane.marquerAbsent(user.sub, id);
  }

  @Post('douane/reservations/:id/annulation')
  @HttpCode(HttpStatus.OK)
  annulerReservation(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: AnnulerReservationDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.douane.annulerReservationStaff(user.sub, id, dto);
  }

  /** Bloc « Douane » de la fiche candidature : phase, oral, historique des places. */
  @Get('douane/candidatures/:applicationId')
  candidature(@Param('applicationId', ParseUUIDPipe) applicationId: string) {
    return this.douane.douaneDeLaCandidature(applicationId);
  }

  // ── Oral hors jeu (carte « Oral ») ──────────────────────

  @Patch('whitelist/:id/oral')
  oralHorsJeu(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: OralHorsJeuDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.oral.decideOral(
      id,
      user.sub,
      dto.resultat,
      dto.motif,
      dto.notes,
      {
        via: OralVia.HORS_JEU,
        source: 'panel',
        motifHorsJeu: dto.motifHorsJeu,
      },
    );
  }
}
