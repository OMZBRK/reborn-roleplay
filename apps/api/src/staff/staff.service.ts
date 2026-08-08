import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { AppStatus, TicketStatus } from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import { PrismaService } from '../prisma/prisma.service';
import { WebhooksService } from '../webhooks/webhooks.service';
import { WhitelistMessagesService } from '../whitelist/whitelist-messages.service';
import {
  TicketStatusDto,
  WhitelistDecisionDto,
  WhitelistPartDecisionDto,
} from './dto/staff.dto';

export interface DecisionActor {
  /** Si fourni, on resoud le nom via User.discordUsername / minecraftUsername. */
  userId?: string;
  /** Fallback affiche dans l'embed Discord si pas de userId. */
  name?: string;
}

/** Texte du message système posté dans le fil selon la décision de partie. */
function systemMessageForPart(
  part: 'HRP' | 'RP',
  status: AppStatus,
  bothApproved: boolean,
): string {
  const label = part === 'HRP' ? 'HRP' : 'RP';
  if (status === AppStatus.APPROVED) {
    if (part === 'HRP') {
      return '✅ Section HRP approuvée ! La section RP va être examinée prochainement.';
    }
    return bothApproved
      ? '🎉 Félicitations, ta candidature est acceptée ! Bienvenue sur Reborn.'
      : '✅ Section RP approuvée.';
  }
  if (status === AppStatus.NEEDS_REVISION) {
    return `✏️ Une révision de la partie ${label} est demandée — modifie ta candidature puis renvoie-la.`;
  }
  if (status === AppStatus.REJECTED) {
    return `❌ Section ${label} refusée.`;
  }
  return `Section ${label} mise à jour.`;
}

@Injectable()
export class StaffService {
  private readonly logger = new Logger(StaffService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly webhooks: WebhooksService,
    private readonly audit: AuditService,
    private readonly whitelistMessages: WhitelistMessagesService,
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

    // Reflete dans Discord : embed recap dans le thread (legacy) ou edit
    // du message public C3 (nouveau flow DM). Best-effort.
    if (app.discordThreadId || app.discordMessageId) {
      const actorName = await this.resolveActorName(actor);
      void this.webhooks
        .statusUpdate({
          kind: 'whitelist',
          threadId: app.discordThreadId,
          messageId: app.discordMessageId,
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

    // Audit log : actorId fourni → on log avec source panel/discord.
    if (actor.userId) {
      void this.audit.log({
        actorId: actor.userId,
        action: `whitelist.${dto.status.toLowerCase()}`,
        targetUserId: app.userId,
        targetEntity: `whitelist:${applicationId}`,
        metadata: {
          previousStatus: app.status,
          newStatus: dto.status,
          reviewNotes: dto.reviewNotes ?? null,
        },
        source: 'panel',
      });
    }

    return {
      id: updated.id,
      status: updated.status,
      reviewedAt: updated.reviewedAt?.toISOString() ?? null,
      reviewNotes: updated.reviewNotes,
      userId: updated.userId,
    };
  }

  /**
   * Reset propre d'une candidature : supprime la candidature (les
   * `WhitelistMessage` liés cascadent) pour que le joueur puisse en
   * re-soumettre une fraiche, et remet son role a PLAYER — UNIQUEMENT s'il
   * etait WHITELISTED. Un HELPER/REVIEWER/ADMIN/OWNER garde son role : jamais
   * de retrogradation du staff (c'est ce qui reverrouillait la publication en
   * re-testant le flow). Remplace le `DELETE FROM WhitelistApplication` brut.
   */
  async resetWhitelist(applicationId: string, actor: DecisionActor = {}) {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
      select: { id: true, userId: true, status: true },
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');

    const target = await this.prisma.user.findUnique({
      where: { id: app.userId },
      select: { role: true },
    });
    // Retrograde WHITELISTED → PLAYER seulement. Jamais le staff.
    const demote = target?.role === 'WHITELISTED';

    await this.prisma.$transaction(async (tx) => {
      await tx.whitelistApplication.delete({ where: { id: applicationId } });
      if (demote) {
        await tx.user.update({
          where: { id: app.userId },
          data: { role: 'PLAYER' },
        });
      }
    });

    this.logger.log(
      `whitelist reset ${applicationId} (user ${app.userId})` +
        (demote ? ' — role → PLAYER' : ' — role conserve'),
    );

    if (actor.userId) {
      void this.audit.log({
        actorId: actor.userId,
        action: 'whitelist.reset',
        targetUserId: app.userId,
        targetEntity: `whitelist:${applicationId}`,
        metadata: { previousStatus: app.status, roleDemoted: demote },
        source: 'panel',
      });
    }

    return { ok: true, userId: app.userId, roleDemoted: demote };
  }

  /**
   * Décision sur UNE partie (HRP ou RP) de la candidature — L5. Met à jour le
   * statut de la partie, recalcule le statut global (APPROVED seulement si les
   * deux le sont), et n'accorde WHITELISTED que quand tout est validé — sans
   * jamais rétrograder un staff (promotion seulement si le rôle est PLAYER).
   */
  async decideWhitelistPart(
    applicationId: string,
    dto: WhitelistPartDecisionDto,
    actor: DecisionActor = {},
  ) {
    if (dto.status === AppStatus.PENDING) {
      throw new BadRequestException('PENDING n\'est pas une décision staff.');
    }
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');

    const hrp = dto.part === 'HRP' ? dto.status : app.hrpStatus;
    const rp = dto.part === 'RP' ? dto.status : app.rpStatus;
    const bothApproved =
      hrp === AppStatus.APPROVED && rp === AppStatus.APPROVED;
    const anyRejected =
      hrp === AppStatus.REJECTED || rp === AppStatus.REJECTED;
    const anyRevision =
      hrp === AppStatus.NEEDS_REVISION || rp === AppStatus.NEEDS_REVISION;
    const globalStatus = bothApproved
      ? AppStatus.APPROVED
      : anyRejected
        ? AppStatus.REJECTED
        : anyRevision
          ? AppStatus.NEEDS_REVISION
          : AppStatus.PENDING;

    const field = dto.part === 'HRP' ? 'hrpStatus' : 'rpStatus';
    const updated = await this.prisma.whitelistApplication.update({
      where: { id: applicationId },
      data: {
        [field]: dto.status,
        status: globalStatus,
        reviewedAt: new Date(),
        reviewNotes: dto.reviewNotes ?? app.reviewNotes,
      },
    });

    this.logger.log(
      `staff decision ${applicationId} ${dto.part} → ${dto.status} (global ${globalStatus})`,
    );

    // Message système dans le fil du candidat (visible côté launcher) — MVP du
    // fil d'événements type maquette « Section HRP approuvée ! ».
    void this.whitelistMessages.postSystemMessage(
      applicationId,
      systemMessageForPart(dto.part, dto.status, bothApproved),
    );

    // Promotion whitelist uniquement quand tout est validé, jamais un staff.
    if (bothApproved) {
      await this.prisma.user.updateMany({
        where: { id: app.userId, role: 'PLAYER' },
        data: { role: 'WHITELISTED' },
      });
      this.logger.log(`user ${app.userId} whitelist complète → WHITELISTED (si PLAYER)`);
    }

    if (app.discordThreadId || app.discordMessageId) {
      const actorName = await this.resolveActorName(actor);
      void this.webhooks
        .statusUpdate({
          kind: 'whitelist',
          threadId: app.discordThreadId,
          messageId: app.discordMessageId,
          status: globalStatus,
          actorName,
          reason: dto.reviewNotes ?? undefined,
        })
        .catch((err) =>
          this.logger.warn(
            `statusUpdate whitelist ${applicationId} échec : ${(err as Error).message}`,
          ),
        );
    }

    if (actor.userId) {
      void this.audit.log({
        actorId: actor.userId,
        action: `whitelist.${dto.part.toLowerCase()}.${dto.status.toLowerCase()}`,
        targetUserId: app.userId,
        targetEntity: `whitelist:${applicationId}`,
        metadata: {
          part: dto.part,
          partStatus: dto.status,
          globalStatus,
          reviewNotes: dto.reviewNotes ?? null,
        },
        source: 'panel',
      });
    }

    return {
      id: updated.id,
      status: updated.status,
      hrpStatus: updated.hrpStatus,
      rpStatus: updated.rpStatus,
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

    if (ticket.discordThreadId || ticket.discordMessageId) {
      const actorName = await this.resolveActorName(actor);
      void this.webhooks
        .statusUpdate({
          kind: 'ticket',
          threadId: ticket.discordThreadId,
          messageId: ticket.discordMessageId,
          status: dto.status,
          actorName,
        })
        .catch((err) =>
          this.logger.warn(
            `statusUpdate ticket ${ticketId} echec : ${(err as Error).message}`,
          ),
        );
    }

    if (actor.userId) {
      void this.audit.log({
        actorId: actor.userId,
        action: `ticket.${dto.status.toLowerCase()}`,
        targetUserId: ticket.userId,
        targetEntity: `ticket:${ticketId}`,
        metadata: {
          previousStatus: ticket.status,
          newStatus: dto.status,
          subject: ticket.subject,
        },
        source: 'panel',
      });
    }

    return {
      id: updated.id,
      status: updated.status,
      userId: updated.userId,
      subject: updated.subject,
    };
  }
}
