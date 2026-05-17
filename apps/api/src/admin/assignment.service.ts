import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { Role } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';

export type AssignKind = 'whitelist' | 'ticket';

// Resolution actor : soit on a un userId Reborn direct (panel staff
// avec JWT), soit on a un discordUserId snowflake (bot apres click sur
// le bouton "Prendre en charge"). On reverse-lookup vers le Reborn User
// dans le second cas.
export interface ActorRef {
  userId?: string;
  discordUserId?: string;
  /** Pour les ADMIN+ qui forcent une reprise sur un cas d'un autre staff. */
  force?: boolean;
}

/** Apres 4h sans suite, le joueur peut reclamer la liberation du cas. */
const RECLAIM_AFTER_MS = 4 * 60 * 60 * 1000;

const STAFF_ROLE_RANKS: Role[] = [
  Role.PLAYER,
  Role.WHITELISTED,
  Role.HELPER,
  Role.WHITELIST_REVIEWER,
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];

function roleAtLeast(role: Role, min: Role): boolean {
  return STAFF_ROLE_RANKS.indexOf(role) >= STAFF_ROLE_RANKS.indexOf(min);
}

/**
 * Centralise la prise/liberation d'un cas (whitelist ou ticket) par
 * un staff. Trois sources de mutation :
 *
 *   1. Panel Next (JWT) : actor.userId = sub.
 *   2. Bot Discord (HMAC) : actor.discordUserId = snowflake → reverse
 *      lookup vers le User Reborn.
 *   3. Joueur lui-meme (JWT) : appelle reclaim* qui valide le delai
 *      de 4h et libere si plus rien ne bouge cote staff.
 */
@Injectable()
export class AssignmentService {
  private readonly logger = new Logger(AssignmentService.name);

  constructor(private readonly prisma: PrismaService) {}

  // ── Whitelist ──────────────────────────────────────────

  async claimWhitelist(applicationId: string, actor: ActorRef) {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
      include: this.assigneeInclude(),
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');
    if (app.status === 'APPROVED' || app.status === 'REJECTED') {
      throw new BadRequestException('Candidature decidee — claim impossible.');
    }
    const staff = await this.resolveActor(actor);
    if (app.assignedToUserId && app.assignedToUserId !== staff.id) {
      if (!actor.force || !roleAtLeast(staff.role, Role.ADMIN)) {
        throw new ConflictException(
          `Deja prise en charge par ${app.assignedTo?.minecraftUsername ?? 'un autre staff'}.`,
        );
      }
    }
    const updated = await this.prisma.whitelistApplication.update({
      where: { id: applicationId },
      data: { assignedToUserId: staff.id, assignedAt: new Date() },
      include: this.assigneeInclude(),
    });
    this.logger.log(
      `whitelist ${applicationId} claim par ${staff.minecraftUsername} (id=${staff.id})`,
    );
    return this.toAssignmentDto('whitelist', updated);
  }

  async releaseWhitelist(applicationId: string, actor: ActorRef) {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
      include: this.assigneeInclude(),
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');
    if (!app.assignedToUserId) {
      // Idempotent : pas d'erreur si deja libere.
      return this.toAssignmentDto('whitelist', app);
    }
    const staff = await this.resolveActor(actor);
    const isSelf = app.assignedToUserId === staff.id;
    const isAdmin = roleAtLeast(staff.role, Role.ADMIN);
    if (!isSelf && !isAdmin) {
      throw new ForbiddenException(
        `Cette candidature est prise par ${app.assignedTo?.minecraftUsername ?? 'un autre staff'} — seul un ADMIN peut forcer.`,
      );
    }
    const updated = await this.prisma.whitelistApplication.update({
      where: { id: applicationId },
      data: { assignedToUserId: null, assignedAt: null },
      include: this.assigneeInclude(),
    });
    this.logger.log(
      `whitelist ${applicationId} release par ${staff.minecraftUsername}` +
        (isSelf ? '' : ' (force ADMIN)'),
    );
    return this.toAssignmentDto('whitelist', updated);
  }

  /**
   * Joueur lui-meme libere la prise en charge si > 4h sans bouger.
   * Ne touche pas au statut — le cas redevient simplement non-assigne,
   * et un autre staff peut le reprendre.
   */
  async reclaimWhitelistByUser(userId: string) {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId },
      include: this.assigneeInclude(),
    });
    if (!app) throw new NotFoundException('Aucune candidature en cours.');
    return this.reclaimGeneric('whitelist', app);
  }

  // ── Tickets ────────────────────────────────────────────

  async claimTicket(ticketId: string, actor: ActorRef) {
    const ticket = await this.prisma.ticket.findUnique({
      where: { id: ticketId },
      include: this.assigneeInclude(),
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');
    if (ticket.status === 'CLOSED' || ticket.status === 'RESOLVED') {
      throw new BadRequestException(`Ticket ${ticket.status} — claim impossible.`);
    }
    const staff = await this.resolveActor(actor);
    if (ticket.assignedToUserId && ticket.assignedToUserId !== staff.id) {
      if (!actor.force || !roleAtLeast(staff.role, Role.ADMIN)) {
        throw new ConflictException(
          `Deja pris en charge par ${ticket.assignedTo?.minecraftUsername ?? 'un autre staff'}.`,
        );
      }
    }
    const updated = await this.prisma.ticket.update({
      where: { id: ticketId },
      data: { assignedToUserId: staff.id, assignedAt: new Date() },
      include: this.assigneeInclude(),
    });
    this.logger.log(
      `ticket ${ticketId} claim par ${staff.minecraftUsername}`,
    );
    return this.toAssignmentDto('ticket', updated);
  }

  async releaseTicket(ticketId: string, actor: ActorRef) {
    const ticket = await this.prisma.ticket.findUnique({
      where: { id: ticketId },
      include: this.assigneeInclude(),
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');
    if (!ticket.assignedToUserId) {
      return this.toAssignmentDto('ticket', ticket);
    }
    const staff = await this.resolveActor(actor);
    const isSelf = ticket.assignedToUserId === staff.id;
    const isAdmin = roleAtLeast(staff.role, Role.ADMIN);
    if (!isSelf && !isAdmin) {
      throw new ForbiddenException(
        `Ce ticket est pris par ${ticket.assignedTo?.minecraftUsername ?? 'un autre staff'} — seul un ADMIN peut forcer.`,
      );
    }
    const updated = await this.prisma.ticket.update({
      where: { id: ticketId },
      data: { assignedToUserId: null, assignedAt: null },
      include: this.assigneeInclude(),
    });
    this.logger.log(
      `ticket ${ticketId} release par ${staff.minecraftUsername}` +
        (isSelf ? '' : ' (force ADMIN)'),
    );
    return this.toAssignmentDto('ticket', updated);
  }

  async reclaimTicketByUser(userId: string, ticketId: string) {
    const ticket = await this.prisma.ticket.findUnique({
      where: { id: ticketId },
      include: this.assigneeInclude(),
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');
    if (ticket.userId !== userId) {
      throw new ForbiddenException("Ce ticket ne t'appartient pas.");
    }
    return this.reclaimGeneric('ticket', ticket);
  }

  // ── helpers ────────────────────────────────────────────

  private async reclaimGeneric(
    kind: AssignKind,
    entity: {
      id: string;
      status: string;
      assignedToUserId: string | null;
      assignedAt: Date | null;
      assignedTo: { id: string; minecraftUsername: string } | null;
    },
  ) {
    if (
      kind === 'whitelist' &&
      (entity.status === 'APPROVED' || entity.status === 'REJECTED')
    ) {
      throw new BadRequestException(
        'Candidature decidee — pas de reclamation possible.',
      );
    }
    if (
      kind === 'ticket' &&
      (entity.status === 'RESOLVED' || entity.status === 'CLOSED')
    ) {
      throw new BadRequestException(
        `Ticket ${entity.status} — pas de reclamation possible.`,
      );
    }
    if (!entity.assignedAt) {
      throw new BadRequestException(
        'Personne ne s\'en occupe encore — pas de reclamation possible.',
      );
    }
    const elapsed = Date.now() - entity.assignedAt.getTime();
    if (elapsed < RECLAIM_AFTER_MS) {
      const remainingMin = Math.ceil((RECLAIM_AFTER_MS - elapsed) / 60_000);
      throw new BadRequestException(
        `Reclamation possible dans ${remainingMin} min (apres 4h sans suite).`,
      );
    }
    const updated =
      kind === 'whitelist'
        ? await this.prisma.whitelistApplication.update({
            where: { id: entity.id },
            data: { assignedToUserId: null, assignedAt: null },
            include: this.assigneeInclude(),
          })
        : await this.prisma.ticket.update({
            where: { id: entity.id },
            data: { assignedToUserId: null, assignedAt: null },
            include: this.assigneeInclude(),
          });
    this.logger.log(
      `${kind} ${entity.id} reclame par le joueur (etait assigne a ${entity.assignedTo?.minecraftUsername ?? '?'})`,
    );
    return this.toAssignmentDto(kind, updated);
  }

  private async resolveActor(actor: ActorRef) {
    if (actor.userId) {
      const user = await this.prisma.user.findUnique({
        where: { id: actor.userId },
        select: {
          id: true,
          minecraftUsername: true,
          discordUsername: true,
          role: true,
        },
      });
      if (!user) {
        throw new ForbiddenException('Compte staff introuvable.');
      }
      return user;
    }
    if (actor.discordUserId) {
      const user = await this.prisma.user.findUnique({
        where: { discordUserId: actor.discordUserId },
        select: {
          id: true,
          minecraftUsername: true,
          discordUsername: true,
          role: true,
        },
      });
      if (!user) {
        throw new ForbiddenException(
          'Aucun compte Reborn lie a ce Discord. Connecte-toi via le launcher pour lier ton compte.',
        );
      }
      if (!roleAtLeast(user.role, Role.HELPER)) {
        throw new ForbiddenException(
          `Role insuffisant (${user.role}) pour prendre un cas en charge.`,
        );
      }
      return user;
    }
    throw new BadRequestException('Actor non identifie.');
  }

  private assigneeInclude() {
    return {
      assignedTo: {
        select: { id: true, minecraftUsername: true, discordUsername: true },
      },
    } as const;
  }

  private toAssignmentDto(
    kind: AssignKind,
    entity: {
      id: string;
      assignedToUserId: string | null;
      assignedAt: Date | null;
      assignedTo: {
        id: string;
        minecraftUsername: string;
        discordUsername: string | null;
      } | null;
    },
  ) {
    return {
      kind,
      id: entity.id,
      assignee: entity.assignedTo
        ? {
            id: entity.assignedTo.id,
            minecraftUsername: entity.assignedTo.minecraftUsername,
            discordUsername: entity.assignedTo.discordUsername,
          }
        : null,
      assignedAt: entity.assignedAt?.toISOString() ?? null,
    };
  }
}
