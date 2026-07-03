import {
  BadRequestException,
  Body,
  Controller,
  Logger,
  Post,
  UnauthorizedException,
} from '@nestjs/common';
import { TicketsService } from '../tickets/tickets.service';
import { ReportDto } from './dto/report.dto';
import { PlayService } from './play.service';

/**
 * Report in-game (menu ÉCHAP → REPORT). Contrôleur PUBLIC séparé de
 * {@link PlayController} (qui est sous JwtAuthGuard au niveau classe) : ici
 * l'identité vient du play-token, pas d'un JWT. Le mod n'a pas le JWT Reborn
 * (il est dans le keyring du launcher) mais lit le play-token via la sysprop
 * {@code reborn.playTokenPath} — on s'appuie dessus pour rattacher le ticket
 * au bon compte. La conversation continue ensuite côté launcher + thread
 * Discord, exactement comme un ticket créé depuis le launcher.
 */
@Controller('play')
export class PlayReportController {
  private readonly logger = new Logger(PlayReportController.name);

  constructor(
    private readonly play: PlayService,
    private readonly tickets: TicketsService,
  ) {}

  @Post('report')
  async report(@Body() dto: ReportDto) {
    const payload = this.play.verifyPlayTokenSignature(dto.playToken);
    if (!payload || !payload.sub) {
      throw new UnauthorizedException('play-token invalide');
    }
    try {
      const ticket = await this.tickets.create(payload.sub, {
        category: dto.category,
        subject: dto.subject,
        message: dto.message,
      });
      this.logger.log(
        `report in-game → ticket ${ticket.id} (${dto.category}) pour ${payload.mcUsername}`,
      );
      return { ticketId: ticket.id };
    } catch (err) {
      // sub référence un user supprimé, etc.
      this.logger.warn(`report in-game échec: ${(err as Error).message}`);
      throw new BadRequestException('impossible de créer le ticket');
    }
  }
}
