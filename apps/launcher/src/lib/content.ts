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
// Whitelist
// ──────────────────────────────────────────────────────

export type WhitelistStatus = "PENDING" | "APPROVED" | "REJECTED" | "NEEDS_REVISION";

export type WhitelistApplication = {
  id: string;
  status: WhitelistStatus;
  characterName: string;
  characterAge: number;
  background: string;
  motivation: string;
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
  characterName: string;
  characterAge: number;
  background: string;
  motivation: string;
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
