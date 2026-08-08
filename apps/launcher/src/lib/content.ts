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
  // Cf API C3 — flow DM bot. Si non-null, un staff a clique "Prendre
  // en charge" : le launcher peut afficher "@X depuis Yh" et, apres 4h
  // sans suite, proposer "Demander une reprise" (cf reclaimWhitelist).
  assignee: { username: string | null } | null;
  assignedAt: string | null;
};

export type WhitelistMeResponse = {
  application: WhitelistApplication | null;
};

export async function fetchWhitelistMe(): Promise<WhitelistMeResponse> {
  return invoke<WhitelistMeResponse>("whitelist_me");
}

/**
 * Libere l'assignation staff si elle date de plus de 4h et que la
 * candidature n'est pas decidee. L'API refuse avec 400 si pas eligible.
 * Cote bot, le message public du salon staff redevient disponible pour
 * un autre staff.
 */
export async function reclaimWhitelist(): Promise<void> {
  await invoke("whitelist_reclaim");
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

// ── Messages whitelist (chat staff↔candidat) ───────────────────────

export type WhitelistMessageAuthor = "USER" | "STAFF" | "SYSTEM";

export type WhitelistMessage = {
  id: string;
  applicationId: string;
  authorType: WhitelistMessageAuthor;
  authorId: string | null;
  authorName: string | null;
  content: string;
  attachments: { url: string }[];
  createdAt: string;
};

export async function fetchWhitelistMessages(): Promise<WhitelistMessage[]> {
  return invoke<WhitelistMessage[]>("whitelist_messages_list");
}

export async function postWhitelistMessage(
  content: string,
  attachmentUrls?: string[],
): Promise<WhitelistMessage> {
  return invoke<WhitelistMessage>("whitelist_messages_post", {
    content,
    attachmentUrls: attachmentUrls ?? null,
  });
}

// ──────────────────────────────────────────────────────
// Upload de pièces jointes (screenshots whitelist / tickets)
// ──────────────────────────────────────────────────────

/**
 * Upload une pièce jointe (image) et retourne son URL publique à joindre à
 * un message. Le WebView ne fait pas d'HTTP direct (cf §3.1) : on lit le
 * fichier en base64 puis on délègue le POST multipart au backend Rust.
 */
export async function uploadAttachment(file: File): Promise<string> {
  const dataBase64 = await fileToBase64(file);
  return invoke<string>("upload_attachment", {
    fileName: file.name,
    mimeType: file.type || "application/octet-stream",
    dataBase64,
  });
}

// ──────────────────────────────────────────────────────
// Badges (compteurs non-lus + monnaie)
// ──────────────────────────────────────────────────────

export type Badges = {
  unreadTickets: number;
  unreadPatchnotes: number;
  coins: number;
};

export async function fetchBadges(): Promise<Badges> {
  return invoke<Badges>("me_badges");
}

export async function markRead(
  scope: "tickets" | "patchnotes",
): Promise<Badges> {
  return invoke<Badges>("me_mark_read", { scope });
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      // readAsDataURL → "data:<mime>;base64,XXXX" : on ne garde que le payload.
      const result = reader.result as string;
      const comma = result.indexOf(",");
      resolve(comma >= 0 ? result.slice(comma + 1) : result);
    };
    reader.onerror = () =>
      reject(reader.error ?? new Error("Lecture du fichier échouée"));
    reader.readAsDataURL(file);
  });
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
  authorId: string | null;
  authorName: string | null;
  isStaff: boolean;
  content: string;
  attachments: { url: string }[];
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
  // Cf C5 — assignation staff. Si != null, un staff a clique
  // "Prendre en charge" cote Discord. assignedAt > 4h → bouton
  // "Demander une reprise" disponible (reclaimTicket).
  assignee: { username: string | null } | null;
  assignedAt: string | null;
};

export async function reclaimTicket(id: string): Promise<void> {
  await invoke("tickets_reclaim", { id });
}

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
  attachmentUrls?: string[],
): Promise<TicketMessage> {
  return invoke<TicketMessage>("tickets_post_message", {
    id,
    content,
    attachmentUrls: attachmentUrls ?? null,
  });
}

export async function deleteTicket(id: string): Promise<void> {
  await invoke<void>("tickets_delete", { id });
}

// ──────────────────────────────────────────────────────
// Server status
// ──────────────────────────────────────────────────────

export type ServerStatus = {
  online: boolean;
  players: { online: number; max: number };
  version: string | null;
  motd: string | null;
  latencyMs: number | null;
  fetchedAt: string;
};

export async function fetchServerStatus(): Promise<ServerStatus> {
  return invoke<ServerStatus>("server_status");
}

// ── Profil : modifier le nom d'affichage RP ────────────────────────
export async function updateDisplayName(displayName: string): Promise<{ displayName: string }> {
  return invoke<{ displayName: string }>("update_display_name", { displayName });
}
