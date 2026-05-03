import {
  Body,
  Controller,
  Param,
  ParseUUIDPipe,
  Patch,
  UseGuards,
} from '@nestjs/common';
import { TicketStatusDto, WhitelistDecisionDto } from './dto/staff.dto';
import { HmacSignatureGuard } from './hmac-signature.guard';
import { StaffService } from './staff.service';

/**
 * Endpoints appeles par le bot Discord (slash commands staff). Auth
 * exclusivement par signature HMAC du body — pas de JWT, pas d'OAuth.
 * Le bot signe avec REBORN_WEBHOOK_SECRET, partage avec l'API via .env.
 */
@Controller('staff')
@UseGuards(HmacSignatureGuard)
export class StaffController {
  constructor(private readonly service: StaffService) {}

  @Patch('whitelist/:id')
  decideWhitelist(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: WhitelistDecisionDto,
  ) {
    return this.service.decideWhitelist(id, dto);
  }

  @Patch('tickets/:id')
  setTicketStatus(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: TicketStatusDto,
  ) {
    return this.service.setTicketStatus(id, dto);
  }
}
