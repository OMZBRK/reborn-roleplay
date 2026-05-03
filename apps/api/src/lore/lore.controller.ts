import { Controller, Get, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { LoreService } from './lore.service';

@Controller('lore')
@UseGuards(JwtAuthGuard)
export class LoreController {
  constructor(private readonly service: LoreService) {}

  @Get('current')
  current() {
    return this.service.getCurrent();
  }
}
