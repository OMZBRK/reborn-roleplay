import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  UseGuards,
} from '@nestjs/common';
import {
  StaffMessageDto,
  TicketStatusDto,
  WhitelistDecisionDto,
} from './dto/staff.dto';
import { HmacSignatureGuard } from './hmac-signature.guard';
import { StaffService } from './staff.service';
import { WhitelistMessagesService } from '../whitelist/whitelist-messages.service';
import { TicketsService } from '../tickets/tickets.service';

/**
 * Endpoints appeles par le bot Discord (slash commands staff + relais
 * messages thread). Auth exclusivement par signature HMAC du body — pas
 * de JWT, pas d'OAuth. Le bot signe avec REBORN_WEBHOOK_SECRET.
 */
@Controller('staff')
@UseGuards(HmacSignatureGuard)
export class StaffController {
  constructor(
    private readonly service: StaffService,
    private readonly whitelistMessages: WhitelistMessagesService,
    private readonly tickets: TicketsService,
  ) {}

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

  // Messages staff postés depuis Discord (relais via le listener
  // messageCreate du bot). Idempotent : dédupliqué par discordMessageId.
  @Post('whitelist/:id/messages')
  @HttpCode(HttpStatus.CREATED)
  postWhitelistMessage(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: StaffMessageDto,
  ) {
    return this.whitelistMessages.postStaffMessage(id, {
      discordMessageId: dto.discordMessageId,
      authorDiscordId: dto.authorDiscordId,
      authorName: dto.authorName,
      content: dto.content,
      attachmentUrls: dto.attachmentUrls,
    });
  }

  @Post('tickets/:id/messages')
  @HttpCode(HttpStatus.CREATED)
  postTicketMessage(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: StaffMessageDto,
  ) {
    return this.tickets.postStaffMessage(id, {
      discordMessageId: dto.discordMessageId,
      authorDiscordId: dto.authorDiscordId,
      authorName: dto.authorName,
      content: dto.content,
      attachmentUrls: dto.attachmentUrls,
    });
  }
}
