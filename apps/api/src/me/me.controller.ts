import { Body, Controller, Get, Patch, Post, UseGuards } from '@nestjs/common';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { MarkReadDto, UpdateProfileDto } from './dto/me.dto';
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

  /** Met à jour le nom d'affichage RP (displayName) du joueur. */
  @Patch('profile')
  updateProfile(@CurrentUser() user: RequestUser, @Body() dto: UpdateProfileDto) {
    return this.service.updateProfile(user.sub, dto.displayName);
  }
}
