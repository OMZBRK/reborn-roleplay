import { Controller, Get } from '@nestjs/common';
import { MenuPanelDto, MenuService } from './menu.service';

// Endpoint PUBLIC (aucun JwtAuthGuard) — consommé par le menu ESC in-game du
// mod Fabric, qui n'a pas de JWT. Ne renvoie que des données non sensibles :
// compteur Discord, dernières patch notes, streams. Même pattern d'opt-out que
// server-status.controller.ts.
@Controller('menu')
export class MenuController {
  constructor(private readonly service: MenuService) {}

  @Get('panel')
  async getPanel(): Promise<MenuPanelDto> {
    return this.service.getPanel();
  }
}
