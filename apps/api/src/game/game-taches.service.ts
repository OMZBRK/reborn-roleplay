import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { GameSignatureService } from './game-signature.service';

/**
 * Tâches planifiées des routes signées du jeu, à part du service pour qu'il
 * reste testable seul (comme DouaneTachesService).
 *
 * Toutes les 5 min : les nonces plus vieux que leur durée de garde
 * (GARDE_NONCE_MS, 3 min : 3 × la tolérance de 60 s) sont effacés de
 * `GameRequestNonce`.
 */
@Injectable()
export class GameTachesService {
  private readonly logger = new Logger(GameTachesService.name);

  constructor(private readonly signature: GameSignatureService) {}

  @Cron(CronExpression.EVERY_5_MINUTES)
  async toutesLesCinqMinutes(): Promise<void> {
    if (!this.signature.actif) return;
    try {
      const n = await this.signature.purgerNonces();
      if (n > 0) this.logger.debug(`jeu : ${n} nonce(s) purgé(s)`);
    } catch (err) {
      this.logger.warn(
        `purge des nonces du jeu échouée : ${(err as Error).message}`,
      );
    }
  }
}
