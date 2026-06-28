import { Body, Controller, Get, Post, UseGuards } from '@nestjs/common';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { MarkReadDto } from './dto/me.dto';
import { MeService } from './me.service';

@Controller('me')
@UseGuards(JwtAuthGuard)
export class MeController {
  constructor(private readonly service: MeService) {}

  /** Compteurs non-lus (cloche + sidebar) + solde de monnaie. */
  @Get('badges')
  badges(@CurrentUser() user: RequestUser) {
    return this.service.getBadges(user.sub);
  }

  /** Marque une section (tickets | patchnotes) comme lue. */
  @Post('badges/read')
  markRead(@CurrentUser() user: RequestUser, @Body() dto: MarkReadDto) {
    return this.service.markRead(user.sub, dto.scope);
  }
}
