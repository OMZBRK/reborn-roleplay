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

export interface Assignee {
  id: string;
  username: string;
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
  assignee: Assignee | null;
  assignedAt: string | null;
}

export type OralSlotStatus = 'OPEN' | 'BOOKED' | 'DONE' | 'CANCELLED';

export interface OralSlot {
  id: string;
  startAt: string;
  durationMin: number;
  status: OralSlotStatus;
  bookedBy: {
    id: string;
    minecraftUsername: string;
    displayName: string | null;
  } | null;
  bookedAt: string | null;
  notes: string | null;
}

export interface WhitelistDetail {
  id: string;
  status: AppStatus;
  hrpStatus: AppStatus;
  rpStatus: AppStatus;
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
  assignee: Assignee | null;
  assignedAt: string | null;
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
  assignee: Assignee | null;
  assignedAt: string | null;
}

export interface TicketDetail {
  id: string;
  subject: string;
  category: TicketCategory;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  discordThreadId: string | null;
  assignee: Assignee | null;
  assignedAt: string | null;
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
  /** Pour STAFF : id User Reborn de l'auteur si poste depuis le panel,
   *  sinon snowflake Discord si poste via bot. Pour USER : id User. */
  authorId: string | null;
  authorName: string | null;
  content: string;
  createdAt: string;
}

export interface Paginated<T> {
  total: number;
  items: T[];
}

export interface AuditLogItem {
  id: string;
  action: string;
  actor: { id: string; username: string };
  targetUser: { id: string; username: string } | null;
  targetEntity: string | null;
  metadata: unknown;
  source: 'panel' | 'discord' | 'launcher' | 'system' | string;
  ipAddress: string | null;
  createdAt: string;
}

/** Reponse de GET /v1/auth/me — le staff connecte. */
export interface CurrentUser {
  id: string;
  minecraftUuid: string;
  minecraftUsername: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: Role;
  discord: {
    userId: string;
    username: string;
    linkedAt: string;
  } | null;
}

export interface PlayerListItem {
  id: string;
  minecraftUsername: string;
  minecraftUuid: string;
  discordUsername: string | null;
  role: Role;
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
  role: Role;
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
    category: TicketCategory;
    status: TicketStatus;
    createdAt: string;
    updatedAt: string;
    lastMessagePreview: string | null;
  }>;
}
