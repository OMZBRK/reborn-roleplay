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
import { AssignmentService } from '../admin/assignment.service';
import { OralSlotsService } from '../oral-slots/oral-slots.service';
import {
  Param,
  ParseUUIDPipe,
} from '@nestjs/common';
import { PostMessageDto, SubmitWhitelistDto } from './dto/whitelist.dto';
import { WhitelistService } from './whitelist.service';
import { WhitelistMessagesService } from './whitelist-messages.service';

@Controller('whitelist')
@UseGuards(JwtAuthGuard)
export class WhitelistController {
  constructor(
    private readonly service: WhitelistService,
    private readonly messages: WhitelistMessagesService,
    private readonly assignment: AssignmentService,
    private readonly oralSlots: OralSlotsService,
  ) {}

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

  // ── Messages staff↔candidat ─────────────────────────────────
  // Listing pollé par le launcher (StatusChatPage) toutes les ~5s pour
  // afficher les réponses du staff arrivées via Discord. Quand on aura
  // SSE on pourra dropper le polling.
  @Get('me/messages')
  async listMessages(@CurrentUser() user: RequestUser) {
    return this.messages.listMine(user.sub);
  }

  @Post('me/messages')
  @HttpCode(HttpStatus.CREATED)
  async postMessage(
    @CurrentUser() user: RequestUser,
    @Body() dto: PostMessageDto,
  ) {
    return this.messages.postMine(user.sub, {
      content: dto.content,
      attachmentUrls: dto.attachmentUrls,
    });
  }

  /**
   * Le joueur demande la liberation de sa candidature si > 4h sans
   * suite de la part du staff assigne. Idempotent en cas de
   * re-deja-libere : l'API refuse avec 400 si pas eligible (pas
   * assigne, ou < 4h).
   */
  @Post('me/reclaim')
  @HttpCode(HttpStatus.OK)
  async reclaim(@CurrentUser() user: RequestUser) {
    return this.assignment.reclaimWhitelistByUser(user.sub);
  }

  // ── Test oral (L5) : créneaux ouverts + réservation ─────────
  /** Créneaux OUVERTS à venir + la réservation du candidat le cas échéant. */
  @Get('me/oral-slots')
  async myOralSlots(@CurrentUser() user: RequestUser) {
    return this.oralSlots.listForPlayer(user.sub);
  }

  @Post('oral-slots/:id/book')
  @HttpCode(HttpStatus.OK)
  async bookOralSlot(
    @CurrentUser() user: RequestUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.oralSlots.book(user.sub, id);
  }

  @Delete('oral-slots/:id/book')
  @HttpCode(HttpStatus.OK)
  async cancelOralSlot(
    @CurrentUser() user: RequestUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.oralSlots.cancelOwnBooking(user.sub, id);
  }
}
