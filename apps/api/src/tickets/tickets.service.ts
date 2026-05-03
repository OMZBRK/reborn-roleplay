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
  authorId: string;
  isStaff: boolean;
  content: string;
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
   * Notifie le bot Discord d'un nouveau ticket. Le bot ouvre un thread
   * prive dans le salon staff. Echec non-bloquant.
   */
  private async notifyStaff(userId: string, ticket: Ticket, firstMessage: string) {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user) return;
    try {
      await this.webhooks.ticketCreated({
        ticketId: ticket.id,
        userPseudo: user.minecraftUsername,
        userId: user.id,
        category: ticket.category,
        subject: ticket.subject,
        message: firstMessage,
        discordUserId: user.discordUserId,
      });
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
    const message = await this.prisma.ticketMessage.create({
      data: {
        ticketId,
        authorId: userId,
        content: dto.content.trim(),
      },
    });
    await this.prisma.ticket.update({
      where: { id: ticketId },
      data: { updatedAt: new Date() },
    });
    return this.toMessageDto(message, userId);
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
    userId: string,
  ): TicketDetail {
    return {
      id: ticket.id,
      category: ticket.category,
      subject: ticket.subject,
      status: ticket.status,
      createdAt: ticket.createdAt.toISOString(),
      updatedAt: ticket.updatedAt.toISOString(),
      messages: messages.map((m) => this.toMessageDto(m, userId)),
    };
  }

  private toMessageDto(message: TicketMessage, viewerId: string): TicketMessageDto {
    return {
      id: message.id,
      authorId: message.authorId,
      isStaff: message.authorId !== viewerId,
      content: message.content,
      createdAt: message.createdAt.toISOString(),
    };
  }
}
