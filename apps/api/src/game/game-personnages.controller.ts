import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Post,
  UseGuards,
} from '@nestjs/common';
import { SyncPersonnagesJeuDto } from './dto/game-personnages.dto';
import { GamePersonnagesService } from './game-personnages.service';
import {
  GameSignatureGuard,
  LimiteJeuRoute,
  ServeurJeu,
} from './game-signature.guard';

/**
 * Personnages poussés par le serveur de jeu (ShinobiCore), hors JWT, signés
 * par le même garde que la douane (CANDIDATURE_ET_DOUANE.md §3.12 et §4.5 ;
 * contrat : game-personnages.service.ts). Jamais `GameHmacGuard` ici.
 *
 * Déclencheurs côté plugin : création, changement de personnage actif,
 * déconnexion, sauvegarde automatique, mort RP, suppression, actions du staff,
 * rattrapage complet au démarrage, file de reprise.
 */
@Controller('game')
@UseGuards(GameSignatureGuard)
export class GamePersonnagesController {
  constructor(private readonly personnages: GamePersonnagesService) {}

  /** Photos complètes des personnages, idempotent ; un résultat par photo. */
  @Post('characters/sync')
  @HttpCode(HttpStatus.OK)
  @LimiteJeuRoute('personnages', 120)
  sync(@Body() dto: SyncPersonnagesJeuDto, @ServeurJeu() serveur: string) {
    return this.personnages.synchroniser(dto, serveur);
  }
}
