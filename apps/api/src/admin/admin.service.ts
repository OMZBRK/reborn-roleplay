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
  // Assignation staff (cf C3 — flow DM bot). null si personne ne s'en
  // occupe.
  assignee: { id: string; username: string } | null;
  assignedAt: string | null;
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
  assignee: { id: string; username: string } | null;
  assignedAt: string | null;
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
    authorId: string | null;
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
  assignee: { id: string; username: string } | null;
  assignedAt: string | null;
}

export interface TicketDetail {
  id: string;
  subject: string;
  category: string;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  discordThreadId: string | null;
  assignee: { id: string; username: string } | null;
  assignedAt: string | null;
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
    authorId: string | null;
    authorName: string | null;
    content: string;
    createdAt: string;
  }>;
}

export interface PlayerListItem {
  id: string;
  minecraftUsername: string;
  minecraftUuid: string;
  discordUsername: string | null;
  role: string;
  banned: boolean;
  lastLoginAt: string | null;
}

export interface PlayerDetail {
  id: string;
  minecraftUsername: string;
  minecraftUuid: string;
  discordUserId: string | null;
  discordUsername: string | null;
  steamUsername: string | null;
  role: string;
  banned: boolean;
  banReason: string | null;
  bannedUntil: string | null;
  createdAt: string;
  lastLoginAt: string | null;
  lastKnownIp: string | null;
  lastKnownCountry: string | null;
  whitelist: {
    id: string;
    status: AppStatus;
    firstName: string;
    lastName: string;
    village: string;
    submittedAt: string;
    reviewedAt: string | null;
  } | null;
  tickets: Array<{
    id: string;
    subject: string;
    category: string;
    status: TicketStatus;
    createdAt: string;
    updatedAt: string;
    lastMessagePreview: string | null;
  }>;
}

const PLAYER_SELECT = {
  id: true,
  minecraftUsername: true,
  minecraftUuid: true,
  discordUsername: true,
  role: true,
  banned: true,
  lastLoginAt: true,
} as const;

function toPlayerListItem(row: {
  id: string;
  minecraftUsername: string;
  minecraftUuid: string;
  discordUsername: string | null;
  role: string;
  banned: boolean;
  lastLoginAt: Date | null;
}): PlayerListItem {
  return {
    id: row.id,
    minecraftUsername: row.minecraftUsername,
    minecraftUuid: row.minecraftUuid,
    discordUsername: row.discordUsername,
    role: row.role,
    banned: row.banned,
    lastLoginAt: row.lastLoginAt?.toISOString() ?? null,
  };
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
          assignedTo: {
            select: { id: true, minecraftUsername: true, discordUsername: true },
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
        assignee: row.assignedTo
          ? {
              id: row.assignedTo.id,
              username:
                row.assignedTo.discordUsername ??
                row.assignedTo.minecraftUsername,
            }
          : null,
        assignedAt: row.assignedAt?.toISOString() ?? null,
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
        assignedTo: {
          select: { id: true, minecraftUsername: true, discordUsername: true },
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
      assignee: app.assignedTo
        ? {
            id: app.assignedTo.id,
            username:
              app.assignedTo.discordUsername ??
              app.assignedTo.minecraftUsername,
          }
        : null,
      assignedAt: app.assignedAt?.toISOString() ?? null,
      user: app.user,
      messages: app.messages.map((m) => ({
        id: m.id,
        authorType: m.authorType,
        authorId: m.authorId,
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
          assignedTo: {
            select: { id: true, minecraftUsername: true, discordUsername: true },
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
        assignee: row.assignedTo
          ? {
              id: row.assignedTo.id,
              username:
                row.assignedTo.discordUsername ??
                row.assignedTo.minecraftUsername,
            }
          : null,
        assignedAt: row.assignedAt?.toISOString() ?? null,
      })),
    };
  }

  async searchPlayers(
    q: string,
    take = 30,
  ): Promise<PlayerListItem[]> {
    const term = q.trim();
    if (term.length === 0) {
      // Sans query : retourne les derniers connectes pour donner au staff
      // une vue de demarrage utile (plutot qu'une liste vide).
      const rows = await this.prisma.user.findMany({
        orderBy: { lastLoginAt: { sort: 'desc', nulls: 'last' } },
        take,
        select: PLAYER_SELECT,
      });
      return rows.map(toPlayerListItem);
    }
    const rows = await this.prisma.user.findMany({
      where: {
        OR: [
          { minecraftUsername: { contains: term, mode: 'insensitive' } },
          { discordUsername: { contains: term, mode: 'insensitive' } },
          { minecraftUuid: { contains: term, mode: 'insensitive' } },
          { id: { equals: term } },
        ],
      },
      orderBy: { minecraftUsername: 'asc' },
      take,
      select: PLAYER_SELECT,
    });
    return rows.map(toPlayerListItem);
  }

  async getPlayer(id: string): Promise<PlayerDetail> {
    const user = await this.prisma.user.findUnique({
      where: { id },
      include: {
        whitelistApp: true,
        tickets: {
          orderBy: { updatedAt: 'desc' },
          include: { messages: { orderBy: { createdAt: 'desc' }, take: 1 } },
        },
      },
    });
    if (!user) throw new NotFoundException('Joueur introuvable.');
    return {
      id: user.id,
      minecraftUsername: user.minecraftUsername,
      minecraftUuid: user.minecraftUuid,
      discordUserId: user.discordUserId,
      discordUsername: user.discordUsername,
      steamUsername: user.steamUsername,
      role: user.role,
      banned: user.banned,
      banReason: user.banReason,
      bannedUntil: user.bannedUntil?.toISOString() ?? null,
      createdAt: user.createdAt.toISOString(),
      lastLoginAt: user.lastLoginAt?.toISOString() ?? null,
      lastKnownIp: user.lastKnownIp,
      lastKnownCountry: user.lastKnownCountry,
      whitelist: user.whitelistApp
        ? {
            id: user.whitelistApp.id,
            status: user.whitelistApp.status,
            firstName: user.whitelistApp.firstName,
            lastName: user.whitelistApp.lastName,
            village: user.whitelistApp.village,
            submittedAt: user.whitelistApp.submittedAt.toISOString(),
            reviewedAt: user.whitelistApp.reviewedAt?.toISOString() ?? null,
          }
        : null,
      tickets: user.tickets.map((t) => ({
        id: t.id,
        subject: t.subject,
        category: t.category,
        status: t.status,
        createdAt: t.createdAt.toISOString(),
        updatedAt: t.updatedAt.toISOString(),
        lastMessagePreview: t.messages[0]?.content.slice(0, 140) ?? null,
      })),
    };
  }

  /**
   * Renvoie la liste de tout ce qui est assigne au staff donne :
   * candidatures whitelist (non terminees) + tickets ouverts (non
   * CLOSED). Sert a la page "Mes prises en charge" du panel.
   */
  async myInbox(staffUserId: string): Promise<{
    whitelist: WhitelistListItem[];
    tickets: TicketListItem[];
  }> {
    const [whitelistRows, ticketRows] = await Promise.all([
      this.prisma.whitelistApplication.findMany({
        where: {
          assignedToUserId: staffUserId,
          status: { in: ['PENDING', 'NEEDS_REVISION'] },
        },
        orderBy: { assignedAt: 'desc' },
        include: {
          user: {
            select: {
              id: true,
              minecraftUsername: true,
              minecraftUuid: true,
              discordUsername: true,
            },
          },
          assignedTo: {
            select: { id: true, minecraftUsername: true, discordUsername: true },
          },
        },
      }),
      this.prisma.ticket.findMany({
        where: {
          assignedToUserId: staffUserId,
          status: { in: ['OPEN', 'IN_PROGRESS'] },
        },
        orderBy: { assignedAt: 'desc' },
        include: {
          user: {
            select: {
              id: true,
              minecraftUsername: true,
              discordUsername: true,
            },
          },
          assignedTo: {
            select: { id: true, minecraftUsername: true, discordUsername: true },
          },
          messages: { orderBy: { createdAt: 'desc' }, take: 1 },
        },
      }),
    ]);
    return {
      whitelist: whitelistRows.map((row) => ({
        id: row.id,
        status: row.status,
        submittedAt: row.submittedAt.toISOString(),
        reviewedAt: row.reviewedAt?.toISOString() ?? null,
        firstName: row.firstName,
        lastName: row.lastName,
        village: row.village,
        user: row.user,
        assignee: row.assignedTo
          ? {
              id: row.assignedTo.id,
              username:
                row.assignedTo.discordUsername ??
                row.assignedTo.minecraftUsername,
            }
          : null,
        assignedAt: row.assignedAt?.toISOString() ?? null,
      })),
      tickets: ticketRows.map((row) => ({
        id: row.id,
        subject: row.subject,
        category: row.category,
        status: row.status,
        createdAt: row.createdAt.toISOString(),
        updatedAt: row.updatedAt.toISOString(),
        lastMessagePreview: row.messages[0]?.content.slice(0, 140) ?? null,
        user: row.user,
        assignee: row.assignedTo
          ? {
              id: row.assignedTo.id,
              username:
                row.assignedTo.discordUsername ??
                row.assignedTo.minecraftUsername,
            }
          : null,
        assignedAt: row.assignedAt?.toISOString() ?? null,
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
        assignedTo: {
          select: { id: true, minecraftUsername: true, discordUsername: true },
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
      assignee: ticket.assignedTo
        ? {
            id: ticket.assignedTo.id,
            username:
              ticket.assignedTo.discordUsername ??
              ticket.assignedTo.minecraftUsername,
          }
        : null,
      assignedAt: ticket.assignedAt?.toISOString() ?? null,
      user: ticket.user,
      messages: ticket.messages.map((m) => ({
        id: m.id,
        authorType: m.authorType,
        authorId: m.authorId,
        authorName: m.authorName,
        content: m.content,
        createdAt: m.createdAt.toISOString(),
      })),
    };
  }
}
