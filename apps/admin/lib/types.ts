/**
 * DTOs partages entre l'API et le panel. Mirror cote-pour-cote des
 * interfaces declarees dans apps/api/src/admin/admin.service.ts. Si
 * tu modifies un champ cote API, mets a jour ici aussi (et inversement)
 * jusqu'a ce qu'on extraie un package partage @reborn/shared-types.
 */

export type AppStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'NEEDS_REVISION';
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type TicketCategory =
  | 'BUG'
  | 'REPORT_PLAYER'
  | 'WHITELIST_APPEAL'
  | 'PURCHASE_ISSUE'
  | 'OTHER';
export type MessageAuthor = 'USER' | 'STAFF' | 'SYSTEM';
export type Role =
  | 'PLAYER'
  | 'WHITELISTED'
  | 'HELPER'
  | 'WHITELIST_REVIEWER'
  | 'MODERATOR'
  | 'ADMIN'
  | 'OWNER';

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
    role: Role;
  };
  messages: AdminMessage[];
}

export interface TicketListItem {
  id: string;
  subject: string;
  category: TicketCategory;
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
  category: TicketCategory;
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
    role: Role;
  };
  messages: AdminMessage[];
}

export interface AdminMessage {
  id: string;
  authorType: MessageAuthor;
  authorName: string | null;
  content: string;
  createdAt: string;
}

export interface Paginated<T> {
  total: number;
  items: T[];
}
