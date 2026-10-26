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
  UseGuards,
} from '@nestjs/common';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { ReserverSeanceDto } from './dto/douane.dto';
import { DouaneService } from './douane.service';

/**
 * `/v1/douane/*` — côté candidat (launcher, JWT). Le launcher n'appelle
 * jamais l'API en direct : ces routes passent par les commandes Tauri
 * (CLAUDE.md, « Three-layer launcher »).
 */
@Controller('douane')
@UseGuards(JwtAuthGuard)
export class DouaneController {
  constructor(private readonly douane: DouaneService) {}

  /** Phase, place réservée (avec sa fenêtre d'entrée) et séances ouvertes avec leurs places. */
  @Get('me')
  etat(@CurrentUser() user: RequestUser) {
    return this.douane.etatCandidat(user.sub);
  }

  /** Réserve une place ; `remplacer: true` pour changer de séance sans perdre l'ancienne place en cas d'échec. */
  @Post('reservations')
  @HttpCode(HttpStatus.CREATED)
  reserver(@CurrentUser() user: RequestUser, @Body() dto: ReserverSeanceDto) {
    return this.douane.reserver(user.sub, dto.seanceId, dto.remplacer === true);
  }

  /** Annule sa propre place. */
  @Delete('reservations/:id')
  @HttpCode(HttpStatus.OK)
  annuler(
    @CurrentUser() user: RequestUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.douane.annulerMaReservation(user.sub, id);
  }
}
