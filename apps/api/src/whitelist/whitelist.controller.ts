import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Patch,
  Post,
  UseGuards,
} from '@nestjs/common';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { SubmitWhitelistDto } from './dto/whitelist.dto';
import { WhitelistService } from './whitelist.service';

@Controller('whitelist')
@UseGuards(JwtAuthGuard)
export class WhitelistController {
  constructor(private readonly service: WhitelistService) {}

  @Get('me')
  async getMine(@CurrentUser() user: RequestUser) {
    return this.service.getMine(user.sub);
  }

  @Post()
  @HttpCode(HttpStatus.CREATED)
  async submit(@CurrentUser() user: RequestUser, @Body() dto: SubmitWhitelistDto) {
    return this.service.submit(user.sub, dto);
  }

  @Patch('me')
  async resubmit(@CurrentUser() user: RequestUser, @Body() dto: SubmitWhitelistDto) {
    return this.service.resubmit(user.sub, dto);
  }

  @Delete('me')
  @HttpCode(HttpStatus.NO_CONTENT)
  async withdraw(@CurrentUser() user: RequestUser) {
    await this.service.withdraw(user.sub);
  }
}
