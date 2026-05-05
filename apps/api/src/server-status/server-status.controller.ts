import { Controller, Get, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { ServerStatusService } from './server-status.service';

// Endpoint JWT-protégé : seul un user authentifié peut voir l'état du
// serveur (cohérent avec le reste du launcher — la sidebar n'apparaît
// qu'après login). Si on veut le rendre public un jour (page web "live
// status"), splitter en deux : /v1/server/status (public, basique) et
// /v1/server/status/full (JWT, infos étendues).
@Controller('server')
@UseGuards(JwtAuthGuard)
export class ServerStatusController {
  constructor(private readonly service: ServerStatusService) {}

  @Get('status')
  async getStatus() {
    return this.service.getStatus();
  }
}
