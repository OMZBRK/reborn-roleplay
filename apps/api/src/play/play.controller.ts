import { Controller, Logger, Post, UseGuards } from '@nestjs/common';
import {
  CurrentUser,
  type RequestUser,
} from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { PlayService } from './play.service';

@Controller('play')
@UseGuards(JwtAuthGuard)
export class PlayController {
  private readonly logger = new Logger(PlayController.name);

  constructor(private readonly play: PlayService) {}

  /**
   * Emet un play-token court (~5min) que le launcher passe a la JVM via
   * sysprop. Le mod Reborn Integrity le pousse au serveur via custom
   * payload `reborn:auth` au moment du JOIN, le plugin Guardian verifie
   * la signature HMAC avec le meme secret.
   */
  @Post('session')
  async issueSession(@CurrentUser() user: RequestUser) {
    return this.play.issuePlayToken(user.sub);
  }
}
