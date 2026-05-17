import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import { Role } from '@prisma/client';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { MinRole } from '../auth/roles.decorator';
import { RolesGuard } from '../auth/roles.guard';
import { StaffService } from '../staff/staff.service';
import { TicketsService } from '../tickets/tickets.service';
import { WhitelistMessagesService } from '../whitelist/whitelist-messages.service';
import { AdminService } from './admin.service';
import {
  ListTicketsQueryDto,
  ListWhitelistQueryDto,
  PanelMessageDto,
  SearchPlayersQueryDto,
  TicketStatusUpdateDto,
  WhitelistDecisionDto,
} from './dto/admin.dto';

/**
 * `/v1/admin/*` — Staff panel API (Next.js app at apps/admin).
 *
 * Authenticated via the JWT issued by the Discord staff login flow
 * (`/v1/auth/discord/staff/*`). Every route is gated on a minimum role
 * via `@MinRole`; the class-level default is HELPER. Stricter routes
 * override it locally.
 *
 * Distinct from `/v1/staff/*` (HMAC-signed bot→API endpoints) — different
 * namespace, different guard, different consumer.
 */
@Controller('admin')
@UseGuards(JwtAuthGuard, RolesGuard)
@MinRole(Role.HELPER)
export class AdminController {
  constructor(
    private readonly admin: AdminService,
    private readonly staff: StaffService,
    private readonly tickets: TicketsService,
    private readonly whitelistMessages: WhitelistMessagesService,
  ) {}

  @Get('dashboard')
  dashboard() {
    return this.admin.dashboard();
  }

  // ── Whitelist ──────────────────────────────────────────

  @Get('whitelist')
  @MinRole(Role.WHITELIST_REVIEWER)
  listWhitelist(@Query() query: ListWhitelistQueryDto) {
    return this.admin.listWhitelist(query);
  }

  @Get('whitelist/:id')
  @MinRole(Role.WHITELIST_REVIEWER)
  getWhitelist(@Param('id', ParseUUIDPipe) id: string) {
    return this.admin.getWhitelist(id);
  }

  @Patch('whitelist/:id')
  @MinRole(Role.WHITELIST_REVIEWER)
  decideWhitelist(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: WhitelistDecisionDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.staff.decideWhitelist(id, dto, { userId: user.sub });
  }

  @Post('whitelist/:id/messages')
  @MinRole(Role.WHITELIST_REVIEWER)
  @HttpCode(HttpStatus.CREATED)
  postWhitelistMessage(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: PanelMessageDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.whitelistMessages.postPanelStaffMessage(id, {
      staffUserId: user.sub,
      content: dto.content,
    });
  }

  // ── Tickets ────────────────────────────────────────────

  @Get('tickets')
  listTickets(@Query() query: ListTicketsQueryDto) {
    return this.admin.listTickets(query);
  }

  @Get('tickets/:id')
  getTicket(@Param('id', ParseUUIDPipe) id: string) {
    return this.admin.getTicket(id);
  }

  @Patch('tickets/:id')
  setTicketStatus(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: TicketStatusUpdateDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.staff.setTicketStatus(id, dto, { userId: user.sub });
  }

  @Post('tickets/:id/messages')
  @HttpCode(HttpStatus.CREATED)
  postTicketMessage(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: PanelMessageDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.tickets.postPanelStaffMessage(id, {
      staffUserId: user.sub,
      content: dto.content,
    });
  }

  // ── Players ────────────────────────────────────────────

  @Get('players/search')
  searchPlayers(@Query() query: SearchPlayersQueryDto) {
    return this.admin.searchPlayers(query.q ?? '', query.take);
  }

  @Get('players/:id')
  getPlayer(@Param('id', ParseUUIDPipe) id: string) {
    return this.admin.getPlayer(id);
  }
}
