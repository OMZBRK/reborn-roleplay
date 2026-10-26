import { Module } from '@nestjs/common';
import { DouaneModule } from '../douane/douane.module';
import { PrismaModule } from '../prisma/prisma.module';
import { GameDouaneController } from './game-douane.controller';
import { GameDouaneService } from './game-douane.service';
import { GamePersonnagesController } from './game-personnages.controller';
import { GamePersonnagesService } from './game-personnages.service';
import { GameSignatureGuard } from './game-signature.guard';
import { GameSignatureService } from './game-signature.service';
import { GameTachesService } from './game-taches.service';
import { GameController } from './game.controller';
import { GameService } from './game.service';

/**
 * Routes du serveur de jeu.
 *  - `GameController` : GET /game/candidature/:mcUuid (historique, `GameHmacGuard`,
 *    coupée par `REBORN_GAME_CANDIDATURE_HISTORIQUE=0` une fois ShinobiCore passé
 *    à GET /game/acces).
 *  - `GameDouaneController` : accès, dossier, décision et présence de la
 *    douane, signés par `GameSignatureGuard` (CANDIDATURE_ET_DOUANE.md §3.12).
 *  - `GamePersonnagesController` : POST /game/characters/sync, même garde
 *    (§4.5, lot J ; lu par GET /me/characters).
 * La décision d'oral et la présence viennent de `DouaneModule` (lot A3).
 * Audit et Prisma sont globaux ou importés.
 */
@Module({
  imports: [PrismaModule, DouaneModule],
  controllers: [
    GameController,
    GameDouaneController,
    GamePersonnagesController,
  ],
  providers: [
    GameService,
    GameDouaneService,
    GamePersonnagesService,
    GameSignatureService,
    GameSignatureGuard,
    GameTachesService,
  ],
  exports: [GameService],
})
export class GameModule {}
