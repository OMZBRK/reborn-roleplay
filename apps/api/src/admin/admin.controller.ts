import {
  Body,
  Controller,
  Delete,
  Get,
  Header,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
  StreamableFile,
  UseGuards,
} from '@nestjs/common';
import { ExigeCapacite } from '../auth/capacite.decorator';
import { CurrentUser } from '../auth/current-user.decorator';
import type { RequestUser } from '../auth/current-user.decorator';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { RolesGuard } from '../auth/roles.guard';
import { LoginAnomalyService } from '../security/login-anomaly.service';
import { OralSlotsService } from '../oral-slots/oral-slots.service';
import {
  MarkSlotDoneDto,
  OpenSlotsDto,
} from '../oral-slots/dto/oral-slots.dto';
import { StaffService } from '../staff/staff.service';
import { WhitelistPartDecisionDto } from '../staff/dto/staff.dto';
import { TicketsService } from '../tickets/tickets.service';
import { WhitelistMessagesService } from '../whitelist/whitelist-messages.service';
import { WhitelistVoixService } from '../whitelist/whitelist-voix.service';
import { AdminService } from './admin.service';
import { AssignmentService } from './assignment.service';
import { RoleService } from './role.service';
import {
  ChangeRoleDto,
  ListAnomaliesQueryDto,
  ListAuditQueryDto,
  ListTicketsQueryDto,
  ListWhitelistQueryDto,
  NotePriveeEcritDto,
  PanelMessageDto,
  SearchPlayersQueryDto,
  TicketStatusUpdateDto,
} from './dto/admin.dto';

/**
 * `/v1/admin/*` — Staff panel API (Next.js app at apps/admin).
 *
 * Authenticated via the JWT issued by the Discord staff login flow
 * (`/v1/auth/discord/staff/*`). Chaque route exige une capacité
 * (auth/capacites.ts, PANEL_V2 §3.3) ; par défaut `panel.acceder` (tout le
 * staff), les routes plus strictes la remplacent localement. Plus de rang
 * linéaire ici : les grades techniques n'ont plus les fiches joueurs ni les
 * tickets hors « Bug », la modération n'a plus d'outil de développeur.
 *
 * Distinct from `/v1/staff/*` (HMAC-signed bot→API endpoints) — different
 * namespace, different guard, different consumer.
 */
@Controller('admin')
@UseGuards(JwtAuthGuard, RolesGuard)
@ExigeCapacite('panel.acceder')
export class AdminController {
  constructor(
    private readonly admin: AdminService,
    private readonly staff: StaffService,
    private readonly tickets: TicketsService,
    private readonly whitelistMessages: WhitelistMessagesService,
    private readonly assignment: AssignmentService,
    private readonly anomalies: LoginAnomalyService,
    private readonly roles: RoleService,
    private readonly oralSlots: OralSlotsService,
    private readonly voix: WhitelistVoixService,
  ) {}

  /** Pilotage : Comptes et Recrutement à null sans `pilotage.analyses`. */
  @Get('dashboard')
  @ExigeCapacite('pilotage.voir')
  dashboard(@CurrentUser() user: RequestUser) {
    return this.admin.dashboard(user.role);
  }

  @Get('me/inbox')
  myInbox(@CurrentUser() user: RequestUser) {
    return this.admin.myInbox(user.sub, user.role);
  }

  // ── Whitelist ──────────────────────────────────────────

  @Get('whitelist')
  @ExigeCapacite('candidatures.relire')
  listWhitelist(@Query() query: ListWhitelistQueryDto) {
    return this.admin.listWhitelist(query);
  }

  @Get('whitelist/:id')
  @ExigeCapacite('candidatures.relire')
  getWhitelist(@Param('id', ParseUUIDPipe) id: string) {
    return this.admin.getWhitelist(id);
  }

  // Plus de décision globale (HRP et RP d'un coup) au panel : l'ancienne route
  // PATCH /admin/whitelist/:id est retirée. L'écrit se tranche partie par
  // partie (PATCH whitelist/:id/decision, commentaire obligatoire pour « À
  // revoir » et « Refusé »), l'oral par PATCH whitelist/:id/oral.

  @Post('whitelist/:id/messages')
  @ExigeCapacite('candidatures.relire')
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
  @ExigeCapacite('candidatures.relire')
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
  @ExigeCapacite('candidatures.relire')
  releaseWhitelist(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    return this.assignment.releaseWhitelist(id, { userId: user.sub });
  }

  /**
   * Reset propre d'une candidature (supprime + retrograde WHITELISTED→PLAYER,
   * jamais le staff). Remplace le DELETE SQL brut pour re-tester le flow.
   */
  @Post('whitelist/:id/reset')
  @ExigeCapacite('candidatures.reinitialiser')
  @HttpCode(HttpStatus.OK)
  resetWhitelist(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    return this.staff.resetWhitelist(id, { userId: user.sub });
  }

  /**
   * Écoute de l'enregistrement vocal de la candidature (fichier privé, jamais
   * servi en statique). Le panel le lit en blob avec son jeton, puis le joue.
   * Chaque écoute est tracée à l'audit. `voice` : nom de la fiche, en alias.
   */
  @Get(['whitelist/:id/voix', 'whitelist/:id/voice'])
  @ExigeCapacite('candidatures.relire')
  @Header('Cache-Control', 'private, no-store')
  @Header('X-Content-Type-Options', 'nosniff')
  async ecouterVoix(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ): Promise<StreamableFile> {
    const v = await this.voix.ouvrirPourStaff(id, user.sub);
    return new StreamableFile(v.flux, {
      type: v.type,
      length: v.taille,
      disposition: 'inline',
    });
  }

  /**
   * Décision sur une partie de l'ÉCRIT (HRP ou RP). Les deux validées = écrit
   * accepté (le candidat réserve sa douane) ; l'oral se tranche par
   * PATCH /admin/whitelist/:id/oral (module douane).
   */
  @Patch('whitelist/:id/decision')
  @ExigeCapacite('candidatures.relire')
  decideWhitelistPart(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: WhitelistPartDecisionDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.staff.decideWhitelistPart(id, dto, { userId: user.sub });
  }

  /**
   * Note PRIVÉE sur l'écrit (writtenStaffNotes), enregistrée sans trancher.
   * Distincte du commentaire visible du joueur (reviewNotes, posé avec la
   * décision). Transmise à l'examinateur, jamais au joueur.
   */
  @Patch('whitelist/:id/note-privee')
  @ExigeCapacite('candidatures.relire')
  notePriveeEcrit(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: NotePriveeEcritDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.admin.majNotePriveeEcrit(id, dto.note, user.sub);
  }

  // ── Douane : examinateurs proposés aux séances ─────────────
  // Les séances elles-mêmes vivent dans le module douane (admin/douane/*).

  @Get('examinateurs')
  @ExigeCapacite('douane.organiser')
  listerExaminateurs() {
    return this.admin.listerExaminateurs();
  }

  // ── Créneaux de test oral (L5, pool global) ────────────────
  @Get('oral-slots')
  @ExigeCapacite('douane.organiser')
  listOralSlots() {
    return this.oralSlots.listForStaff();
  }

  @Post('oral-slots')
  @ExigeCapacite('douane.organiser')
  @HttpCode(HttpStatus.CREATED)
  openOralSlots(@Body() dto: OpenSlotsDto, @CurrentUser() user: RequestUser) {
    return this.oralSlots.openSlots(user.sub, dto.slots);
  }

  @Patch('oral-slots/:id/cancel')
  @ExigeCapacite('douane.organiser')
  cancelOralSlot(@Param('id', ParseUUIDPipe) id: string) {
    return this.oralSlots.cancelSlot(id);
  }

  @Patch('oral-slots/:id/done')
  @ExigeCapacite('douane.organiser')
  markOralSlotDone(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: MarkSlotDoneDto,
  ) {
    return this.oralSlots.markDone(id, dto.notes);
  }

  // ── Tickets ────────────────────────────────────────────
  // `tickets.traiter` : toutes catégories ; `tickets.bug` seul (développeur) :
  // la liste est filtrée sur « Bug » et chaque route d'un ticket vérifie sa
  // catégorie avant d'agir (AdminService.verifierAccesTicket).

  @Get('tickets')
  @ExigeCapacite('tickets.traiter', 'tickets.bug')
  listTickets(
    @Query() query: ListTicketsQueryDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.admin.listTickets(query, user.role);
  }

  @Get('tickets/:id')
  @ExigeCapacite('tickets.traiter', 'tickets.bug')
  async getTicket(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    await this.admin.verifierAccesTicket(id, user.role);
    return this.admin.getTicket(id);
  }

  @Patch('tickets/:id')
  @ExigeCapacite('tickets.traiter', 'tickets.bug')
  async setTicketStatus(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: TicketStatusUpdateDto,
    @CurrentUser() user: RequestUser,
  ) {
    await this.admin.verifierAccesTicket(id, user.role);
    return this.staff.setTicketStatus(id, dto, { userId: user.sub });
  }

  @Post('tickets/:id/messages')
  @ExigeCapacite('tickets.traiter', 'tickets.bug')
  @HttpCode(HttpStatus.CREATED)
  async postTicketMessage(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: PanelMessageDto,
    @CurrentUser() user: RequestUser,
  ) {
    await this.admin.verifierAccesTicket(id, user.role);
    return this.tickets.postPanelStaffMessage(id, {
      staffUserId: user.sub,
      content: dto.content,
    });
  }

  @Post('tickets/:id/assign')
  @ExigeCapacite('tickets.traiter', 'tickets.bug')
  async claimTicket(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
    @Body() body: { force?: boolean } = {},
  ) {
    await this.admin.verifierAccesTicket(id, user.role);
    return this.assignment.claimTicket(id, {
      userId: user.sub,
      force: body.force === true,
    });
  }

  // Relâcher reste permis sans contrôle de catégorie : on doit toujours pouvoir
  // rendre un ticket qu'on a pris (AssignmentService refuse celui d'un autre).
  @Delete('tickets/:id/assign')
  @ExigeCapacite('tickets.traiter', 'tickets.bug')
  releaseTicket(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    return this.assignment.releaseTicket(id, { userId: user.sub });
  }

  // ── Players ────────────────────────────────────────────

  @Get('players/search')
  @ExigeCapacite('joueurs.voir')
  searchPlayers(@Query() query: SearchPlayersQueryDto) {
    return this.admin.searchPlayers(query.q ?? '', query.take, {
      role: query.role,
      statut: query.statut,
    });
  }

  /**
   * Fiche joueur, IP et pays masqués (`lastKnownIp` et `lastKnownCountry`
   * toujours null, `ipConnue`).
   */
  @Get('players/:id')
  @ExigeCapacite('joueurs.voir')
  getPlayer(@Param('id', ParseUUIDPipe) id: string) {
    return this.admin.getPlayer(id);
  }

  /**
   * IP et pays de dernière connexion, à la demande (« Afficher » de l'onglet
   * Sécurité). Chaque lecture est journalisée (`player.ip.view`).
   */
  @Get('players/:id/ip')
  @ExigeCapacite('joueurs.ip')
  @Header('Cache-Control', 'private, no-store')
  ipJoueur(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    return this.admin.revelerIp(id, user.sub);
  }

  /** Change le rôle Reborn d'un joueur. ADMIN+ uniquement ; garde-fous dans
   *  RoleService (pas de self-change, pas de pair/supérieur). Le motif est
   *  obligatoire et inscrit au journal avec le changement. */
  @Patch('players/:id/role')
  @ExigeCapacite('grades.changer')
  changePlayerRole(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: ChangeRoleDto,
    @CurrentUser() user: RequestUser,
  ) {
    return this.roles.changeRoleByUserId(user.sub, id, dto.role, dto.motif);
  }

  // ── Audit ─────────────────────────────────────────────

  @Get('audit')
  @ExigeCapacite('audit.voir')
  listAudit(@Query() query: ListAuditQueryDto) {
    return this.admin.listAudit(query);
  }

  // ── Anomalies de login ────────────────────────────────

  /**
   * Connexions suspectes, sans leur IP (`ipConnue`). `userId` : celles d'un
   * joueur (onglet Sécurité de sa fiche).
   */
  @Get('anomalies')
  @ExigeCapacite('securite.voir')
  listAnomalies(@Query() query: ListAnomaliesQueryDto) {
    return this.admin.listerAnomalies({
      nonVues: query.unack === 'true' || query.unack === '1',
      userId: query.userId,
    });
  }

  /**
   * IP d'une connexion suspecte, à la demande (« Afficher »). Chaque lecture
   * est journalisée (`anomaly.ip.view`, cible = l'anomalie).
   */
  @Get('anomalies/:id/ip')
  @ExigeCapacite('joueurs.ip')
  @Header('Cache-Control', 'private, no-store')
  ipAnomalie(
    @Param('id', ParseUUIDPipe) id: string,
    @CurrentUser() user: RequestUser,
  ) {
    return this.admin.revelerIpAnomalie(id, user.sub);
  }

  @Post('anomalies/:id/ack')
  @ExigeCapacite('securite.voir')
  @HttpCode(HttpStatus.NO_CONTENT)
  async ackAnomaly(@Param('id', ParseUUIDPipe) id: string) {
    await this.anomalies.acknowledge(id);
  }
}
