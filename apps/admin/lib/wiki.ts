import { api } from './api';
import type {
  WikiEntry,
  WikiEntryStatus,
  WikiIdea,
  WikiIdeaStatus,
  WikiRevision,
  WikiTag,
  WikiTagKind,
} from './types';

/**
 * Wrappers typés sur `/v1/wiki/*` (base de connaissances staff + idées).
 * Passent tous par le fetch authentifié `api()` (bearer + refresh auto).
 */

export interface EntryFilters {
  q?: string;
  /** Slugs de tags. Envoyés en CSV côté query. */
  tags?: string[];
  status?: WikiEntryStatus;
  kind?: WikiTagKind;
}

export interface CreateEntryInput {
  title: string;
  summary?: string;
  body: string;
  status?: WikiEntryStatus;
  sources?: string;
  tagSlugs?: string[];
}

export type UpdateEntryInput = Partial<CreateEntryInput>;

export interface CreateIdeaInput {
  title: string;
  body: string;
  category?: string;
  linkedEntryId?: string;
}

export interface UpdateIdeaInput {
  title?: string;
  body?: string;
  status?: WikiIdeaStatus;
  category?: string;
}

// ── Entries ────────────────────────────────────────────

export function listEntries(filters: EntryFilters = {}): Promise<WikiEntry[]> {
  const params = new URLSearchParams();
  if (filters.q) params.set('q', filters.q);
  if (filters.tags && filters.tags.length > 0)
    params.set('tag', filters.tags.join(','));
  if (filters.status) params.set('status', filters.status);
  if (filters.kind) params.set('kind', filters.kind);
  const qs = params.toString();
  return api<WikiEntry[]>(`/wiki/entries${qs ? `?${qs}` : ''}`);
}

export function getEntry(id: string): Promise<WikiEntry> {
  return api<WikiEntry>(`/wiki/entries/${id}`);
}

export function createEntry(input: CreateEntryInput): Promise<WikiEntry> {
  return api<WikiEntry>('/wiki/entries', { method: 'POST', body: input });
}

export function updateEntry(
  id: string,
  input: UpdateEntryInput,
): Promise<WikiEntry> {
  return api<WikiEntry>(`/wiki/entries/${id}`, { method: 'PATCH', body: input });
}

export function deleteEntry(id: string): Promise<{ deleted: boolean }> {
  return api<{ deleted: boolean }>(`/wiki/entries/${id}`, { method: 'DELETE' });
}

export function listRevisions(id: string): Promise<WikiRevision[]> {
  return api<WikiRevision[]>(`/wiki/entries/${id}/revisions`);
}

// ── Tags ───────────────────────────────────────────────

export function listTags(): Promise<WikiTag[]> {
  return api<WikiTag[]>('/wiki/tags');
}

export function createTag(input: {
  kind: WikiTagKind;
  label: string;
  color?: string;
}): Promise<WikiTag> {
  return api<WikiTag>('/wiki/tags', { method: 'POST', body: input });
}

// ── Ideas ──────────────────────────────────────────────

export function listIdeas(status?: WikiIdeaStatus): Promise<WikiIdea[]> {
  return api<WikiIdea[]>(`/wiki/ideas${status ? `?status=${status}` : ''}`);
}

export function createIdea(input: CreateIdeaInput): Promise<WikiIdea> {
  return api<WikiIdea>('/wiki/ideas', { method: 'POST', body: input });
}

export function updateIdea(
  id: string,
  input: UpdateIdeaInput,
): Promise<WikiIdea> {
  return api<WikiIdea>(`/wiki/ideas/${id}`, { method: 'PATCH', body: input });
}
