import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import {
  DecisionDouaneJeuDto,
  DossierDouaneQueryDto,
  ParseMcUuidPipe,
  PresenceDouaneJeuDto,
} from './dto/game-douane.dto';
import { GameDouaneService } from './game-douane.service';
import {
  GameSignatureGuard,
  LimiteJeuRoute,
  ServeurJeu,
} from './game-signature.guard';

/**
 * Routes appelées par le serveur de jeu (ShinobiCore), hors JWT, signées par
 * le nouveau garde (CANDIDATURE_ET_DOUANE.md §3.12 ; contrat :
 * game-signature.ts). Jamais `GameHmacGuard` ici.
 *
 * Limites par serveur et par minute : accès 300, dossier 60, décision 30,
 * présence 300.
 */
@Controller('game')
@UseGuards(GameSignatureGuard)
export class GameDouaneController {
  constructor(private readonly douane: GameDouaneService) {}

  /** Aiguillage à la connexion : portée `play`, `douane` ou `aucune` (§3.3). */
  @Get('acces/:mcUuid')
  @LimiteJeuRoute('acces', 300)
  acces(
    @Param('mcUuid', ParseMcUuidPipe) mcUuid: string,
    @ServeurJeu() serveur: string,
  ) {
    return this.douane.acces(mcUuid, serveur);
  }

  /** Fiche résumée du candidat pour l'examinateur (§3.7). */
  @Get('douane/dossier/:mcUuid')
  @LimiteJeuRoute('dossier', 60)
  dossier(
    @Param('mcUuid', ParseMcUuidPipe) mcUuid: string,
    @Query() query: DossierDouaneQueryDto,
    @ServeurJeu() serveur: string,
  ) {
    return this.douane.dossier(mcUuid, query.examinateur, serveur);
  }

  /** Décision de l'oral passé en jeu (§3.9). */
  @Post('douane/decision')
  @HttpCode(HttpStatus.OK)
  @LimiteJeuRoute('decision', 30)
  decision(@Body() dto: DecisionDouaneJeuDto, @ServeurJeu() serveur: string) {
    return this.douane.decision(dto, serveur);
  }

  /** Arrivée au hall, appel au bureau, sortie (§3.6, facultatif). */
  @Post('douane/presence')
  @HttpCode(HttpStatus.OK)
  @LimiteJeuRoute('presence', 300)
  presence(@Body() dto: PresenceDouaneJeuDto, @ServeurJeu() serveur: string) {
    return this.douane.presence(dto, serveur);
  }
}
