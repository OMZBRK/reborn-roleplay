import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  Header,
  Headers,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Put,
  Query,
  Req,
  StreamableFile,
  UseGuards,
} from '@nestjs/common';
import type { Request } from 'express';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { AssignmentService } from '../admin/assignment.service';
import { OralSlotsService } from '../oral-slots/oral-slots.service';
import {
  BrouillonDto,
  PostMessageDto,
  RenvoiWhitelistDto,
  SubmitWhitelistDto,
} from './dto/whitelist.dto';
import { lireCorpsBrut, voixOctetsMax } from './voix.config';
import { WhitelistService } from './whitelist.service';
import { WhitelistMessagesService } from './whitelist-messages.service';
import {
  SOURCES_VOIX_CANDIDAT,
  type SourceVoixCandidat,
  WhitelistVoixService,
} from './whitelist-voix.service';

@Controller('whitelist')
@UseGuards(JwtAuthGuard)
export class WhitelistController {
  constructor(
    private readonly service: WhitelistService,
    private readonly messages: WhitelistMessagesService,
    private readonly assignment: AssignmentService,
    private readonly oralSlots: OralSlotsService,
    private readonly voix: WhitelistVoixService,
  ) {}

  /** Candidature du joueur (sans les notes privées du staff) + suivi calculé (phase). */
  @Get('me')
  async getMine(@CurrentUser() user: RequestUser) {
    return this.service.getMine(user.sub);
  }

  /** Villages, clans (pour information), bornes, libellés des listes fermées, contraintes de la voix. */
  @Get('options')
  options() {
    return this.service.options();
  }

  @Post()
  @HttpCode(HttpStatus.CREATED)
  async submit(
    @CurrentUser() user: RequestUser,
    @Body() dto: SubmitWhitelistDto,
  ) {
    return this.service.submit(user.sub, dto);
  }

  /**
   * Renvoi après « À revoir » (parties rouvertes seulement) ou après un refus
   * (candidature complète). Contrôle champ par champ fait par le service.
   */
  @Patch('me')
  async resubmit(
    @CurrentUser() user: RequestUser,
    @Body() dto: RenvoiWhitelistDto,
  ) {
    return this.service.resubmit(user.sub, dto);
  }

  @Delete('me')
  @HttpCode(HttpStatus.NO_CONTENT)
  async withdraw(@CurrentUser() user: RequestUser) {
    await this.service.withdraw(user.sub);
  }

  // ── Brouillon serveur (§2.5) — `draft` : nom de la fiche, gardé en alias ──
  @Get(['me/brouillon', 'me/draft'])
  async getBrouillon(@CurrentUser() user: RequestUser) {
    return this.service.getBrouillon(user.sub);
  }

  @Put(['me/brouillon', 'me/draft'])
  async putBrouillon(
    @CurrentUser() user: RequestUser,
    @Body() dto: BrouillonDto,
  ) {
    return this.service.putBrouillon(user.sub, dto);
  }

  @Delete(['me/brouillon', 'me/draft'])
  @HttpCode(HttpStatus.NO_CONTENT)
  async deleteBrouillon(@CurrentUser() user: RequestUser) {
    await this.service.deleteBrouillon(user.sub);
  }

  /**
   * Enregistrement vocal, en CORPS BRUT (Content-Type audio/webm ou
   * audio/ogg), durée déclarée en `?dureeSec=`. Rangé dans le brouillon, il
   * rejoint la candidature à l'envoi. `voice` : nom de la fiche, en alias.
   */
  @Post(['me/voix', 'me/voice'])
  @HttpCode(HttpStatus.CREATED)
  async deposerVoix(
    @CurrentUser() user: RequestUser,
    @Req() req: Request,
    @Headers('content-type') contentType: string | undefined,
    @Query('dureeSec') dureeSec: string | undefined,
  ) {
    const corps = await lireCorpsBrut(req, voixOctetsMax());
    return this.service.deposerVoix(user.sub, contentType, dureeSec, corps);
  }

  /**
   * Réécoute de son propre enregistrement (maquettes L5, L6, L8) : celui du
   * brouillon s'il y en a un, sinon celui de la candidature ;
   * `?source=candidature` (« Mon dossier ») : la voix envoyée seulement.
   * Fichier privé, servi au seul propriétaire ; le launcher le lit par une
   * commande Tauri.
   */
  @Get(['me/voix', 'me/voice'])
  @Header('Cache-Control', 'private, no-store')
  @Header('X-Content-Type-Options', 'nosniff')
  async ecouterMaVoix(
    @CurrentUser() user: RequestUser,
    @Query('source') sourceBrute: string | undefined,
  ): Promise<StreamableFile> {
    const source = sourceBrute?.trim() || undefined;
    if (
      source !== undefined &&
      !(SOURCES_VOIX_CANDIDAT as readonly string[]).includes(source)
    ) {
      throw new BadRequestException('source : brouillon ou candidature.');
    }
    const v = await this.voix.ouvrirPourCandidat(
      user.sub,
      source as SourceVoixCandidat | undefined,
    );
    return new StreamableFile(v.flux, {
      type: v.type,
      length: v.taille,
      disposition: 'inline',
    });
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
  // Gardé pendant la transition (§3.11) ; remplacé par les séances de douane.
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
