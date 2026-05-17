import {
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import {
  Ticket,
  TicketCategory,
  TicketMessage,
  TicketStatus,
} from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { WebhooksService } from '../webhooks/webhooks.service';
import { CreateTicketDto, PostMessageDto } from './dto/tickets.dto';

export interface TicketSummary {
  id: string;
  category: TicketCategory;
  subject: string;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  lastMessagePreview: string | null;
}

export interface TicketMessageDto {
  id: string;
  authorId: string | null;
  authorName: string | null;
  isStaff: boolean;
  content: string;
  attachments: { url: string }[];
  createdAt: string;
}

export interface TicketDetail {
  id: string;
  category: TicketCategory;
  subject: string;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  messages: TicketMessageDto[];
}

export interface PostStaffMessageInput {
  discordMessageId: string;
  authorDiscordId: string;
  authorName: string;
  content: string;
  attachmentUrls?: string[];
}

@Injectable()
export class TicketsService {
  private readonly logger = new Logger(TicketsService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly webhooks: WebhooksService,
  ) {}

  async listMine(userId: string): Promise<TicketSummary[]> {
    const tickets = await this.prisma.ticket.findMany({
      where: { userId },
      orderBy: { updatedAt: 'desc' },
      include: {
        messages: { orderBy: { createdAt: 'desc' }, take: 1 },
      },
    });
    return tickets.map((t) => this.toSummary(t, t.messages[0]));
  }

  async create(userId: string, dto: CreateTicketDto): Promise<TicketDetail> {
    const ticket = await this.prisma.ticket.create({
      data: {
        userId,
        category: dto.category,
        subject: dto.subject.trim(),
        status: TicketStatus.OPEN,
        messages: {
          create: {
            authorType: 'USER',
            authorId: userId,
            content: dto.message.trim(),
          },
        },
      },
      include: { messages: { orderBy: { createdAt: 'asc' } } },
    });
    await this.notifyStaff(userId, ticket, dto.message.trim());
    return this.toDetail(ticket, ticket.messages, userId);
  }

  /**
   * Supprime un ticket dont le user est proprietaire. Autorise tant
   * que le statut est OPEN ou IN_PROGRESS — un ticket RESOLVED/CLOSED
   * fait partie de l'historique consultable, on ne laisse pas le user
   * effacer ce que le staff a deja traite.
   */
  async remove(userId: string, ticketId: string): Promise<void> {
    const ticket = await this.prisma.ticket.findUnique({ where: { id: ticketId } });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');
    if (ticket.userId !== userId) {
      throw new ForbiddenException("Ce ticket ne t'appartient pas.");
    }
    if (
      ticket.status === TicketStatus.RESOLVED ||
      ticket.status === TicketStatus.CLOSED
    ) {
      throw new ForbiddenException(
        'Ce ticket est cloture. Tu peux le consulter mais pas le supprimer.',
      );
    }
    // Cascade configurée côté schema (onDelete: Cascade), pas besoin de
    // deleteMany en amont.
    await this.prisma.ticket.delete({ where: { id: ticketId } });
  }

  /**
   * Notifie le bot Discord d'un nouveau ticket. Le bot ouvre un thread
   * public dans le salon staff. Echec non-bloquant. On persiste le
   * threadId retourne pour relayer les messages user → discord.
   */
  private async notifyStaff(userId: string, ticket: Ticket, firstMessage: string) {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user) return;
    try {
      const result = await this.webhooks.ticketCreated({
        ticketId: ticket.id,
        userPseudo: user.minecraftUsername,
        userId: user.id,
        category: ticket.category,
        subject: ticket.subject,
        message: firstMessage,
        discordUserId: user.discordUserId,
      });
      if (result?.threadId) {
        await this.prisma.ticket.update({
          where: { id: ticket.id },
          data: { discordThreadId: result.threadId },
        });
        this.logger.log(`ticket ${ticket.id} → thread ${result.threadId}`);
      }
    } catch (err) {
      this.logger.warn(
        `Webhook ticket non transmis : ${(err as Error).message}`,
      );
    }
  }

  async getOne(userId: string, ticketId: string): Promise<TicketDetail> {
    const ticket = await this.prisma.ticket.findUnique({
      where: { id: ticketId },
      include: { messages: { orderBy: { createdAt: 'asc' } } },
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');
    if (ticket.userId !== userId) {
      throw new ForbiddenException("Ce ticket ne t'appartient pas.");
    }
    return this.toDetail(ticket, ticket.messages, userId);
  }

  async postMessage(
    userId: string,
    ticketId: string,
    dto: PostMessageDto,
  ): Promise<TicketMessageDto> {
    const ticket = await this.prisma.ticket.findUnique({ where: { id: ticketId } });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');
    if (ticket.userId !== userId) {
      throw new ForbiddenException("Ce ticket ne t'appartient pas.");
    }
    if (ticket.status === TicketStatus.CLOSED) {
      throw new ForbiddenException(
        'Ce ticket est ferme. Ouvre un nouveau ticket pour continuer.',
      );
    }
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    const message = await this.prisma.ticketMessage.create({
      data: {
        ticketId,
        authorType: 'USER',
        authorId: userId,
        authorName: user?.minecraftUsername ?? null,
        content: dto.content.trim(),
      },
    });
    await this.prisma.ticket.update({
      where: { id: ticketId },
      data: { updatedAt: new Date() },
    });

    // Relay best-effort vers le thread Discord. Skip si pas de threadId
    // stocke (ex: bot etait down a la creation du ticket).
    if (ticket.discordThreadId) {
      try {
        const relay = await this.webhooks.ticketMessageRelay({
          threadId: ticket.discordThreadId,
          authorPseudo: user?.minecraftUsername ?? 'Joueur',
          content: dto.content.trim(),
        });
        if (relay?.messageId) {
          await this.prisma.ticketMessage.update({
            where: { id: message.id },
            data: { discordMessageId: relay.messageId },
          });
        }
      } catch (err) {
        this.logger.warn(
          `Relay ticket message échec : ${(err as Error).message}`,
        );
      }
    }

    return this.toMessageDto(message, userId);
  }

  /**
   * Reception d'un message staff depuis le bot Discord (listener
   * messageCreate). Idempotent : dédupliqué par discordMessageId.
   */
  async postStaffMessage(
    ticketId: string,
    input: PostStaffMessageInput,
  ): Promise<TicketMessageDto> {
    const ticket = await this.prisma.ticket.findUnique({
      where: { id: ticketId },
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');

    const existing = await this.prisma.ticketMessage.findFirst({
      where: { ticketId, discordMessageId: input.discordMessageId },
    });
    if (existing) {
      return this.toMessageDto(existing, ticket.userId);
    }

    const created = await this.prisma.ticketMessage.create({
      data: {
        ticketId,
        authorType: 'STAFF',
        authorId: input.authorDiscordId,
        authorName: input.authorName,
        content: input.content,
        attachments: (input.attachmentUrls ?? []).map((url) => ({ url })),
        discordMessageId: input.discordMessageId,
      },
    });
    await this.prisma.ticket.update({
      where: { id: ticketId },
      data: { updatedAt: new Date() },
    });
    this.logger.log(
      `ticket ${ticketId} ← staff message ${created.id} from ${input.authorName}`,
    );
    return this.toMessageDto(created, ticket.userId);
  }

  /**
   * Message staff envoye depuis le panel Next (apps/admin). Distinct du
   * flow Discord : pas de discordMessageId source (le staff a tape dans
   * le panel, pas dans Discord), donc on relaie vers le thread Discord
   * apres persistance pour que la conversation reste consultable des
   * deux cotes.
   *
   * Pas de risque de boucle : la relay genere un message poste par le
   * bot lui-meme, et le listener `messageCreate` ignore ses propres
   * posts via `client.user.id` (cf apps/bot/src/thread-listener.ts).
   */
  async postPanelStaffMessage(
    ticketId: string,
    input: { staffUserId: string; content: string },
  ): Promise<TicketMessageDto> {
    const ticket = await this.prisma.ticket.findUnique({
      where: { id: ticketId },
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');
    if (ticket.status === TicketStatus.CLOSED) {
      throw new ForbiddenException(
        'Ce ticket est ferme. Reouvre-le avant de poster un message.',
      );
    }
    const staff = await this.prisma.user.findUnique({
      where: { id: input.staffUserId },
      select: { id: true, minecraftUsername: true, discordUsername: true },
    });
    const authorName = staff?.discordUsername ?? staff?.minecraftUsername ?? 'Staff';

    const created = await this.prisma.ticketMessage.create({
      data: {
        ticketId,
        authorType: 'STAFF',
        authorId: input.staffUserId,
        authorName,
        content: input.content.trim(),
      },
    });
    await this.prisma.ticket.update({
      where: { id: ticketId },
      data: { updatedAt: new Date() },
    });

    if (ticket.discordThreadId) {
      try {
        const relay = await this.webhooks.ticketMessageRelay({
          threadId: ticket.discordThreadId,
          authorPseudo: `[Staff] ${authorName}`,
          content: created.content,
        });
        if (relay?.messageId) {
          await this.prisma.ticketMessage.update({
            where: { id: created.id },
            data: { discordMessageId: relay.messageId },
          });
        }
      } catch (err) {
        this.logger.warn(
          `Relay staff message échec : ${(err as Error).message}`,
        );
      }
    }

    return this.toMessageDto(created, ticket.userId);
  }

  private toSummary(ticket: Ticket, lastMessage?: TicketMessage): TicketSummary {
    return {
      id: ticket.id,
      category: ticket.category,
      subject: ticket.subject,
      status: ticket.status,
      createdAt: ticket.createdAt.toISOString(),
      updatedAt: ticket.updatedAt.toISOString(),
      lastMessagePreview: lastMessage
        ? lastMessage.content.slice(0, 140)
        : null,
    };
  }

  private toDetail(
    ticket: Ticket,
    messages: TicketMessage[],
    viewerId: string,
  ): TicketDetail {
    return {
      id: ticket.id,
      category: ticket.category,
      subject: ticket.subject,
      status: ticket.status,
      createdAt: ticket.createdAt.toISOString(),
      updatedAt: ticket.updatedAt.toISOString(),
      messages: messages.map((m) => this.toMessageDto(m, viewerId)),
    };
  }

  private toMessageDto(message: TicketMessage, viewerId: string): TicketMessageDto {
    let attachments: { url: string }[] = [];
    if (Array.isArray(message.attachments)) {
      attachments = (message.attachments as unknown[])
        .filter((a): a is { url: string } => {
          return typeof a === 'object' && a !== null && 'url' in a;
        })
        .map((a) => ({ url: a.url }));
    }
    // isStaff = true si authorType=STAFF, ou si authorId !== viewerId pour
    // les anciens messages avant migration (backward compat).
    const isStaff =
      message.authorType === 'STAFF' ||
      (message.authorType === 'USER' && message.authorId !== viewerId);
    return {
      id: message.id,
      authorId: message.authorId,
      authorName: message.authorName,
      isStaff,
      content: message.content,
      attachments,
      createdAt: message.createdAt.toISOString(),
    };
  }
}
