import { Controller, Get } from '@nestjs/common';
import { ServerStatusDto, ServerStatusService } from './server-status.service';

// Endpoint PUBLIC (PLAN §10.7) : aucun JwtAuthGuard n'est appliqué. Dans cette
// codebase il n'y a pas de guard global — les routes protégées opt-in via
// `@UseGuards(JwtAuthGuard)`. L'absence de guard ici rend donc la route
// accessible sans JWT, pour que la home du launcher (et une éventuelle page
// web "live status") affiche l'état réel du serveur Minecraft.
@Controller('server')
export class ServerStatusController {
  constructor(private readonly service: ServerStatusService) {}

  @Get('status')
  async getStatus(): Promise<ServerStatusDto> {
    return this.service.getStatus();
  }
}
