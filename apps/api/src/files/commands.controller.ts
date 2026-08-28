import { Body, Controller, Get, Post, UseGuards } from '@nestjs/common';
import { CommandBridgeGuard } from './command-bridge.guard';
import { AckDto } from './dto/files.dto';
import { FilesService } from './files.service';

/**
 * `/v1/files/commands/*` — Pont de commandes serveur, appelé UNIQUEMENT par le
 * plugin ShinobiCore (HMAC via {@link CommandBridgeGuard}, pas de JWT). Le plugin
 * interroge `pending` en sortant, exécute les commandes whitelistées en console,
 * puis `ack` les résultats. Aucun port entrant / RCON requis côté hébergeur.
 */
@Controller('files/commands')
@UseGuards(CommandBridgeGuard)
export class CommandsController {
  constructor(private readonly files: FilesService) {}

  /** Draine les commandes en attente (les passe DISPATCHED). */
  @Get('pending')
  pending() {
    return this.files.drainPending();
  }

  /** Enregistre les résultats d'exécution renvoyés par le pont. */
  @Post('ack')
  ack(@Body() dto: AckDto) {
    return this.files.ack(dto.results);
  }
}
