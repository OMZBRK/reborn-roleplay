import { Controller, Get, Logger, Param, UseGuards } from '@nestjs/common';
import { GameHmacGuard } from './game-hmac.guard';
import { GameService, type CandidatureView } from './game.service';

/**
 * Route HISTORIQUE consommée par ShinobiCore, signée HMAC (secret partagé
 * `REBORN_WEBHOOK_SECRET`), hors JWT. Les routes nouvelles du jeu sont dans
 * `GameDouaneController` (garde `GameSignatureGuard`).
 */
@Controller('game')
export class GameController {
  private readonly logger = new Logger(GameController.name);

  constructor(private readonly game: GameService) {}

  /**
   * Candidature whitelist acceptée d'un joueur (prénom, nom de famille,
   * village, âge, staff), pour pré-remplir et verrouiller la création de
   * personnage côté ShinobiCore.
   *
   * Remplacée par le bloc `candidature` de `GET /v1/game/acces/:mcUuid` (même
   * vue). Elle reste ouverte tant que ShinobiCore ne lit pas cette route-là ;
   * `REBORN_GAME_CANDIDATURE_HISTORIQUE=0` la coupe (410), voir GameHmacGuard.
   *
   * GET /v1/game/candidature/:mcUuid
   * Header : X-Reborn-Signature = hex(HMAC-SHA256(REBORN_WEBHOOK_SECRET, mcUuid))
   */
  @Get('candidature/:mcUuid')
  @UseGuards(GameHmacGuard)
  async candidature(@Param('mcUuid') mcUuid: string): Promise<CandidatureView> {
    const view = await this.game.candidatureByMcUuid(mcUuid);
    this.logger.debug(
      `candidature ${mcUuid} → found=${view.found} village=${view.village ?? '-'} clan=${view.clan ?? '-'} staff=${view.staff ?? false}`,
    );
    return view;
  }
}
