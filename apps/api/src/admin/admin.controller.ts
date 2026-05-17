import {
  Body,
  Controller,
  Get,
  Param,
  ParseUUIDPipe,
  Patch,
  Query,
  UseGuards,
} from '@nestjs/common';
import { Role } from '@prisma/client';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { MinRole } from '../auth/roles.decorator';
import { RolesGuard } from '../auth/roles.guard';
import { StaffService } from '../staff/staff.service';
import { AdminService } from './admin.service';
import {
  ListTicketsQueryDto,
  ListWhitelistQueryDto,
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
  ) {
    return this.staff.decideWhitelist(id, dto);
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
  ) {
    return this.staff.setTicketStatus(id, dto);
  }
}
