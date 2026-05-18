import {
  Body,
  Controller,
  Delete,
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
import { LoginAnomalyService } from '../security/login-anomaly.service';
import { StaffService } from '../staff/staff.service';
import { TicketsService } from '../tickets/tickets.service';
import { WhitelistMessagesService } from '../whitelist/whitelist-messages.service';
import { AdminService } from './admin.service';
import { AssignmentService } from './assignment.service';
import {
  ListAuditQueryDto,
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
    private readonly assignment: AssignmentService,
    private readonly anomalies: LoginAnomalyService,
  ) {}

  @Get('dashboard')
  dashboard() {
    return this.admin.dashboard();
  }

  @Get('me/inbox')
  myInbox(@CurrentUser() user: RequestUser) {
    return this.admin.myInbox(user.sub);
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

  @Post('whitelist/:id/assign')
  @MinRole(Role.WHITELIST_REVIEWER)
  claimWhitelist(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
    @Body() body: { force?: boolean } = {},
  ) {
    return this.assignment.claimWhitelist(id, {
      userId: user.sub,
      force: body.force === true,
    });
  }

  @Delete('whitelist/:id/assign')
  @MinRole(Role.WHITELIST_REVIEWER)
  releaseWhitelist(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    return this.assignment.releaseWhitelist(id, { userId: user.sub });
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

  @Post('tickets/:id/assign')
  claimTicket(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
    @Body() body: { force?: boolean } = {},
  ) {
    return this.assignment.claimTicket(id, {
      userId: user.sub,
      force: body.force === true,
    });
  }

  @Delete('tickets/:id/assign')
  releaseTicket(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    return this.assignment.releaseTicket(id, { userId: user.sub });
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

  // ── Audit ─────────────────────────────────────────────

  @Get('audit')
  @MinRole(Role.ADMIN)
  listAudit(@Query() query: ListAuditQueryDto) {
    return this.admin.listAudit(query);
  }

  // ── Anomalies de login ────────────────────────────────

  @Get('anomalies')
  @MinRole(Role.ADMIN)
  listAnomalies(@Query('unack') unack?: string) {
    return this.anomalies.list({
      unacknowledgedOnly: unack === 'true' || unack === '1',
    });
  }

  @Post('anomalies/:id/ack')
  @MinRole(Role.ADMIN)
  @HttpCode(HttpStatus.NO_CONTENT)
  async ackAnomaly(@Param('id', ParseUUIDPipe) id: string) {
    await this.anomalies.acknowledge(id);
  }
}
