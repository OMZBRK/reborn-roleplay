import { Controller, Get, Logger, Param, UseGuards } from '@nestjs/common';
import { GameHmacGuard } from './game-hmac.guard';
import { GameService, type CandidatureView } from './game.service';

/**
 * Endpoints consommes par les plugins serveur (ShinobiCore), signes HMAC
 * (secret partage `REBORN_WEBHOOK_SECRET`), hors JWT.
 */
@Controller('game')
export class GameController {
  private readonly logger = new Logger(GameController.name);

  constructor(private readonly game: GameService) {}

  /**
   * Candidature whitelist validee d'un joueur (village + clan + prenom + staff),
   * pour verrouiller le wizard de creation de perso cote ShinobiCore.
   *
   * GET /v1/game/candidature/:mcUuid
   * Header : X-Reborn-Signature = hex(HMAC-SHA256(REBORN_WEBHOOK_SECRET, mcUuid))
   */
  @Get('candidature/:mcUuid')
  @UseGuards(GameHmacGuard)
  async candidature(
    @Param('mcUuid') mcUuid: string,
  ): Promise<CandidatureView> {
    const view = await this.game.candidatureByMcUuid(mcUuid);
    this.logger.debug(
      `candidature ${mcUuid} → found=${view.found} village=${view.village ?? '-'} clan=${view.clan ?? '-'} staff=${view.staff ?? false}`,
    );
    return view;
  }
}
