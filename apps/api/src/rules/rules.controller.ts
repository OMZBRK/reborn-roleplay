import { Controller, Get, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { RulesService } from './rules.service';

@Controller('rules')
@UseGuards(JwtAuthGuard)
export class RulesController {
  constructor(private readonly service: RulesService) {}

  @Get('current')
  current() {
    return this.service.getCurrent();
  }
}
