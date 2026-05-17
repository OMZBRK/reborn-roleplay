import { Injectable, NotFoundException } from '@nestjs/common';
import { AppStatus, MessageAuthor, TicketStatus } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import {
  ListTicketsQueryDto,
  ListWhitelistQueryDto,
} from './dto/admin.dto';

export interface DashboardStats {
  whitelist: {
    pending: number;
    needsRevision: number;
    approved: number;
    rejected: number;
  };
  tickets: {
    open: number;
    inProgress: number;
    resolved: number;
    closed: number;
  };
  users: {
    total: number;
    whitelisted: number;
    last24h: number;
  };
}

export interface WhitelistListItem {
  id: string;
  status: AppStatus;
  submittedAt: string;
  reviewedAt: string | null;
  firstName: string;
  lastName: string;
  village: string;
  user: {
    id: string;
    minecraftUsername: string;
    minecraftUuid: string;
    discordUsername: string | null;
  };
}

export interface WhitelistDetail {
  id: string;
  status: AppStatus;
  dob: string;
  motivation: string;
  experience: string;
  availability: string;
  firstName: string;
  lastName: string;
  village: string;
  support: string | null;
  history: string;
  appearance: string;
  objectives: string;
  submittedAt: string;
  reviewedAt: string | null;
  reviewNotes: string | null;
  discordThreadId: string | null;
  user: {
    id: string;
    minecraftUsername: string;
    minecraftUuid: string;
    discordUserId: string | null;
    discordUsername: string | null;
    role: string;
  };
  messages: Array<{
    id: string;
    authorType: MessageAuthor;
    authorName: string | null;
    content: string;
    createdAt: string;
  }>;
}

export interface TicketListItem {
  id: string;
  subject: string;
  category: string;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  lastMessagePreview: string | null;
  user: {
    id: string;
    minecraftUsername: string;
    discordUsername: string | null;
  };
}

export interface TicketDetail {
  id: string;
  subject: string;
  category: string;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  discordThreadId: string | null;
  user: {
    id: string;
    minecraftUsername: string;
    minecraftUuid: string;
    discordUserId: string | null;
    discordUsername: string | null;
    role: string;
  };
  messages: Array<{
    id: string;
    authorType: MessageAuthor;
    authorName: string | null;
    content: string;
    createdAt: string;
  }>;
}

@Injectable()
export class AdminService {
  constructor(private readonly prisma: PrismaService) {}

  async dashboard(): Promise<DashboardStats> {
    const since24h = new Date(Date.now() - 24 * 3600 * 1000);
    const [
      wlPending,
      wlRevision,
      wlApproved,
      wlRejected,
      tkOpen,
      tkProgress,
      tkResolved,
      tkClosed,
      usersTotal,
      usersWhitelisted,
      users24h,
    ] = await Promise.all([
      this.prisma.whitelistApplication.count({ where: { status: 'PENDING' } }),
      this.prisma.whitelistApplication.count({
        where: { status: 'NEEDS_REVISION' },
      }),
      this.prisma.whitelistApplication.count({ where: { status: 'APPROVED' } }),
      this.prisma.whitelistApplication.count({ where: { status: 'REJECTED' } }),
      this.prisma.ticket.count({ where: { status: 'OPEN' } }),
      this.prisma.ticket.count({ where: { status: 'IN_PROGRESS' } }),
      this.prisma.ticket.count({ where: { status: 'RESOLVED' } }),
      this.prisma.ticket.count({ where: { status: 'CLOSED' } }),
      this.prisma.user.count(),
      this.prisma.user.count({
        where: { role: { in: ['WHITELISTED', 'HELPER', 'MODERATOR'] } },
      }),
      this.prisma.user.count({ where: { createdAt: { gte: since24h } } }),
    ]);
    return {
      whitelist: {
        pending: wlPending,
        needsRevision: wlRevision,
        approved: wlApproved,
        rejected: wlRejected,
      },
      tickets: {
        open: tkOpen,
        inProgress: tkProgress,
        resolved: tkResolved,
        closed: tkClosed,
      },
      users: {
        total: usersTotal,
        whitelisted: usersWhitelisted,
        last24h: users24h,
      },
    };
  }

  async listWhitelist(
    query: ListWhitelistQueryDto,
  ): Promise<{ total: number; items: WhitelistListItem[] }> {
    const where = query.status ? { status: query.status } : {};
    const [total, rows] = await Promise.all([
      this.prisma.whitelistApplication.count({ where }),
      this.prisma.whitelistApplication.findMany({
        where,
        orderBy: [{ status: 'asc' }, { submittedAt: 'desc' }],
        take: query.take ?? 50,
        skip: query.skip ?? 0,
        include: {
          user: {
            select: {
              id: true,
              minecraftUsername: true,
              minecraftUuid: true,
              discordUsername: true,
            },
          },
        },
      }),
    ]);
    return {
      total,
      items: rows.map((row) => ({
        id: row.id,
        status: row.status,
        submittedAt: row.submittedAt.toISOString(),
        reviewedAt: row.reviewedAt?.toISOString() ?? null,
        firstName: row.firstName,
        lastName: row.lastName,
        village: row.village,
        user: row.user,
      })),
    };
  }

  async getWhitelist(id: string): Promise<WhitelistDetail> {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id },
      include: {
        user: {
          select: {
            id: true,
            minecraftUsername: true,
            minecraftUuid: true,
            discordUserId: true,
            discordUsername: true,
            role: true,
          },
        },
        messages: { orderBy: { createdAt: 'asc' } },
      },
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');
    return {
      id: app.id,
      status: app.status,
      dob: app.dob.toISOString().slice(0, 10),
      motivation: app.motivation,
      experience: app.experience,
      availability: app.availability,
      firstName: app.firstName,
      lastName: app.lastName,
      village: app.village,
      support: app.support,
      history: app.history,
      appearance: app.appearance,
      objectives: app.objectives,
      submittedAt: app.submittedAt.toISOString(),
      reviewedAt: app.reviewedAt?.toISOString() ?? null,
      reviewNotes: app.reviewNotes,
      discordThreadId: app.discordThreadId,
      user: app.user,
      messages: app.messages.map((m) => ({
        id: m.id,
        authorType: m.authorType,
        authorName: m.authorName,
        content: m.content,
        createdAt: m.createdAt.toISOString(),
      })),
    };
  }

  async listTickets(
    query: ListTicketsQueryDto,
  ): Promise<{ total: number; items: TicketListItem[] }> {
    const where = query.status ? { status: query.status } : {};
    const [total, rows] = await Promise.all([
      this.prisma.ticket.count({ where }),
      this.prisma.ticket.findMany({
        where,
        orderBy: [{ status: 'asc' }, { updatedAt: 'desc' }],
        take: query.take ?? 50,
        skip: query.skip ?? 0,
        include: {
          user: {
            select: {
              id: true,
              minecraftUsername: true,
              discordUsername: true,
            },
          },
          messages: { orderBy: { createdAt: 'desc' }, take: 1 },
        },
      }),
    ]);
    return {
      total,
      items: rows.map((row) => ({
        id: row.id,
        subject: row.subject,
        category: row.category,
        status: row.status,
        createdAt: row.createdAt.toISOString(),
        updatedAt: row.updatedAt.toISOString(),
        lastMessagePreview: row.messages[0]?.content.slice(0, 140) ?? null,
        user: row.user,
      })),
    };
  }

  async getTicket(id: string): Promise<TicketDetail> {
    const ticket = await this.prisma.ticket.findUnique({
      where: { id },
      include: {
        user: {
          select: {
            id: true,
            minecraftUsername: true,
            minecraftUuid: true,
            discordUserId: true,
            discordUsername: true,
            role: true,
          },
        },
        messages: { orderBy: { createdAt: 'asc' } },
      },
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');
    return {
      id: ticket.id,
      subject: ticket.subject,
      category: ticket.category,
      status: ticket.status,
      createdAt: ticket.createdAt.toISOString(),
      updatedAt: ticket.updatedAt.toISOString(),
      discordThreadId: ticket.discordThreadId,
      user: ticket.user,
      messages: ticket.messages.map((m) => ({
        id: m.id,
        authorType: m.authorType,
        authorName: m.authorName,
        content: m.content,
        createdAt: m.createdAt.toISOString(),
      })),
    };
  }
}
