import { Controller, Get } from '@nestjs/common';
import { AppService } from './app.service';

@Controller()
export class AppController {
  constructor(private readonly appService: AppService) {}

  @Get()
  getHello(): string {
    return this.appService.getHello();
  }

  /** Endpoint liveness pour le healthcheck Docker / k8s. Pas d'auth,
   *  juste 200 si le process repond. */
  @Get('health')
  health() {
    return { status: 'ok', uptime: process.uptime() };
  }
}
