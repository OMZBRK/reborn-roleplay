import { invoke } from "./tauri";

// ──────────────────────────────────────────────────────
// Patch notes
// ──────────────────────────────────────────────────────

export type PatchNoteSummary = {
  id: string;
  version: string;
  title: string;
  thumbnail: string | null;
  pinned: boolean;
  publishedAt: string;
  excerpt: string;
};

export type PatchNoteListResponse = {
  items: PatchNoteSummary[];
  total: number;
};

export type PatchNoteDetail = PatchNoteSummary & { content: string };

export async function fetchPatchnotes(
  page = 1,
  size = 10,
): Promise<PatchNoteListResponse> {
  return invoke<PatchNoteListResponse>("patchnotes_list", { page, size });
}

export async function fetchPatchnote(id: string): Promise<PatchNoteDetail> {
  return invoke<PatchNoteDetail>("patchnotes_detail", { id });
}

// ──────────────────────────────────────────────────────
// Reglement
// ──────────────────────────────────────────────────────

export type RulesDocument = {
  version: string;
  content: string;
  publishedAt: string;
};

export async function fetchRules(): Promise<RulesDocument> {
  return invoke<RulesDocument>("rules_current");
}

// ──────────────────────────────────────────────────────
// Lore
// ──────────────────────────────────────────────────────

export type LoreDocument = {
  version: string;
  content: string;
  publishedAt: string;
};

export async function fetchLore(): Promise<LoreDocument> {
  return invoke<LoreDocument>("lore_current");
}

// ──────────────────────────────────────────────────────
// Whitelist
// ──────────────────────────────────────────────────────

export type WhitelistAppStatus = "PENDING" | "APPROVED" | "REJECTED" | "NEEDS_REVISION";

// Schéma riche v2 — chaque champ correspond à un input du wizard 2 étapes
// (cf stores/whitelist-store.ts WhitelistDraft + apps/api Prisma model).
export type WhitelistApplication = {
  id: string;
  status: WhitelistAppStatus;
  // Étape 1
  dob: string; // ISO YYYY-MM-DD
  motivation: string;
  experience: string;
  availability: string;
  // Étape 2
  firstName: string;
  lastName: string;
  village: string;
  support: string | null;
  history: string;
  appearance: string;
  objectives: string;
  // Méta
  submittedAt: string;
  reviewedAt: string | null;
  reviewNotes: string | null;
};

export type WhitelistMeResponse = {
  application: WhitelistApplication | null;
};

export async function fetchWhitelistMe(): Promise<WhitelistMeResponse> {
  return invoke<WhitelistMeResponse>("whitelist_me");
}

export type WhitelistSubmitInput = {
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
};

export async function submitWhitelist(
  input: WhitelistSubmitInput,
): Promise<WhitelistApplication> {
  return invoke<WhitelistApplication>("whitelist_submit", input);
}

export async function resubmitWhitelist(
  input: WhitelistSubmitInput,
): Promise<WhitelistApplication> {
  return invoke<WhitelistApplication>("whitelist_resubmit", input);
}

export async function withdrawWhitelist(): Promise<void> {
  await invoke<void>("whitelist_withdraw");
}

// ──────────────────────────────────────────────────────
// Tickets
// ──────────────────────────────────────────────────────

export type TicketCategory =
  | "BUG"
  | "REPORT_PLAYER"
  | "WHITELIST_APPEAL"
  | "PURCHASE_ISSUE"
  | "OTHER";

export type TicketStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";

export type TicketSummary = {
  id: string;
  category: TicketCategory;
  subject: string;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  lastMessagePreview: string | null;
};

export type TicketMessage = {
  id: string;
  authorId: string;
  isStaff: boolean;
  content: string;
  createdAt: string;
};

export type TicketDetail = {
  id: string;
  category: TicketCategory;
  subject: string;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  messages: TicketMessage[];
};

export async function fetchTickets(): Promise<TicketSummary[]> {
  return invoke<TicketSummary[]>("tickets_list");
}

export async function fetchTicket(id: string): Promise<TicketDetail> {
  return invoke<TicketDetail>("tickets_detail", { id });
}

export type CreateTicketInput = {
  category: TicketCategory;
  subject: string;
  message: string;
};

export async function createTicket(input: CreateTicketInput): Promise<TicketDetail> {
  return invoke<TicketDetail>("tickets_create", input);
}

export async function postTicketMessage(
  id: string,
  content: string,
): Promise<TicketMessage> {
  return invoke<TicketMessage>("tickets_post_message", { id, content });
}

export async function deleteTicket(id: string): Promise<void> {
  await invoke<void>("tickets_delete", { id });
}
