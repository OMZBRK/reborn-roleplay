import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { AppStatus, TicketStatus } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { WebhooksService } from '../webhooks/webhooks.service';
import { TicketStatusDto, WhitelistDecisionDto } from './dto/staff.dto';

export interface DecisionActor {
  /** Si fourni, on resoud le nom via User.discordUsername / minecraftUsername. */
  userId?: string;
  /** Fallback affiche dans l'embed Discord si pas de userId. */
  name?: string;
}

@Injectable()
export class StaffService {
  private readonly logger = new Logger(StaffService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly webhooks: WebhooksService,
  ) {}

  /**
   * Resoud le label affiche dans l'embed Discord du status-update :
   * - actor.userId fourni → lookup discordUsername > minecraftUsername
   * - sinon actor.name si fourni
   * - sinon "Staff"
   */
  private async resolveActorName(actor: DecisionActor): Promise<string> {
    if (actor.userId) {
      const user = await this.prisma.user.findUnique({
        where: { id: actor.userId },
        select: { discordUsername: true, minecraftUsername: true },
      });
      if (user) {
        return user.discordUsername ?? user.minecraftUsername ?? 'Staff';
      }
    }
    return actor.name ?? 'Staff';
  }

  async decideWhitelist(
    applicationId: string,
    dto: WhitelistDecisionDto,
    actor: DecisionActor = {},
  ) {
    if (dto.status === AppStatus.PENDING) {
      throw new BadRequestException(
        'PENDING n\'est pas une decision staff (cest letat initial).',
      );
    }
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');

    const updated = await this.prisma.whitelistApplication.update({
      where: { id: applicationId },
      data: {
        status: dto.status,
        reviewedAt: new Date(),
        reviewNotes: dto.reviewNotes ?? null,
      },
    });

    this.logger.log(
      `staff decision ${applicationId} → ${dto.status}${dto.reviewNotes ? ` ("${dto.reviewNotes.slice(0, 60)}")` : ''}`,
    );

    // Si la candidature est acceptee, on remonte le role du joueur a
    // WHITELISTED. Le role par defaut est PLAYER (cf schema.prisma).
    if (dto.status === AppStatus.APPROVED) {
      await this.prisma.user.update({
        where: { id: app.userId },
        data: { role: 'WHITELISTED' },
      });
      this.logger.log(`user ${app.userId} role → WHITELISTED`);
    }

    // Reflete dans Discord : embed recap dans le thread + lock+archive
    // si la decision est terminale (APPROVED/REJECTED) pour eviter que
    // la conversation continue dans le vide cote Discord. Best-effort :
    // un fail webhook ne bloque pas la decision.
    if (app.discordThreadId) {
      const actorName = await this.resolveActorName(actor);
      void this.webhooks
        .statusUpdate({
          kind: 'whitelist',
          threadId: app.discordThreadId,
          status: dto.status,
          actorName,
          reason: dto.reviewNotes ?? undefined,
        })
        .catch((err) =>
          this.logger.warn(
            `statusUpdate whitelist ${applicationId} echec : ${(err as Error).message}`,
          ),
        );
    }

    return {
      id: updated.id,
      status: updated.status,
      reviewedAt: updated.reviewedAt?.toISOString() ?? null,
      reviewNotes: updated.reviewNotes,
      userId: updated.userId,
    };
  }

  async setTicketStatus(
    ticketId: string,
    dto: TicketStatusDto,
    actor: DecisionActor = {},
  ) {
    if (dto.status === TicketStatus.OPEN) {
      throw new BadRequestException(
        'OPEN nest pas une transition staff (cest letat initial).',
      );
    }
    const ticket = await this.prisma.ticket.findUnique({
      where: { id: ticketId },
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');

    const updated = await this.prisma.ticket.update({
      where: { id: ticketId },
      data: { status: dto.status },
    });

    this.logger.log(`staff ticket ${ticketId} → ${dto.status}`);

    if (ticket.discordThreadId) {
      const actorName = await this.resolveActorName(actor);
      void this.webhooks
        .statusUpdate({
          kind: 'ticket',
          threadId: ticket.discordThreadId,
          status: dto.status,
          actorName,
        })
        .catch((err) =>
          this.logger.warn(
            `statusUpdate ticket ${ticketId} echec : ${(err as Error).message}`,
          ),
        );
    }

    return {
      id: updated.id,
      status: updated.status,
      userId: updated.userId,
      subject: updated.subject,
    };
  }
}
