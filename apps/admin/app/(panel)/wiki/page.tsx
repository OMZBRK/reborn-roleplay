'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import type { Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { toast } from 'sonner';
import { FadeUp, StaggerItem } from '@/components/anim';
import { IconArrowLeft, IconSearch, IconX } from '@/components/icons';
import { SkeletonRows } from '@/components/Skeleton';
import {
  createEntry,
  createTag,
  deleteEntry,
  getEntry,
  listEntries,
  listTags,
  updateEntry,
  type CreateEntryInput,
} from '@/lib/wiki';
import type {
  WikiEntry,
  WikiEntryStatus,
  WikiTag,
  WikiTagKind,
} from '@/lib/types';

const KIND_ORDER: WikiTagKind[] = ['SOURCE', 'CANON', 'TYPE', 'AUDIENCE'];
const KIND_LABEL: Record<WikiTagKind, string> = {
  SOURCE: 'Source',
  CANON: 'Canonicité',
  TYPE: 'Type',
  AUDIENCE: 'Audience',
};

const STATUS_TABS: Array<{ value: WikiEntryStatus | 'ALL'; label: string }> = [
  { value: 'ALL', label: 'Tous' },
  { value: 'PUBLISHED', label: 'Publiés' },
  { value: 'DRAFT', label: 'Brouillons' },
  { value: 'ARCHIVED', label: 'Archivés' },
];

const STATUS_LABEL: Record<WikiEntryStatus, string> = {
  DRAFT: 'Brouillon',
  PUBLISHED: 'Publié',
  ARCHIVED: 'Archivé',
};

export default function WikiPage() {
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<WikiEntryStatus | 'ALL'>('ALL');
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  // null = drawer fermé, 'new' = création, sinon id de l'entrée éditée.
  const [drawer, setDrawer] = useState<string | 'new' | null>(null);
  // null = liste, sinon id de l'entrée lue en mode article.
  const [reading, setReading] = useState<string | null>(null);

  const tagsQuery = useQuery({
    queryKey: ['wiki', 'tags'],
    queryFn: () => listTags(),
  });

  const entriesQuery = useQuery({
    queryKey: ['wiki', 'entries', search, status, selectedTags],
    queryFn: () =>
      listEntries({
        q: search.trim() || undefined,
        status: status === 'ALL' ? undefined : status,
        tags: selectedTags.length > 0 ? selectedTags : undefined,
      }),
  });

  const tagsByKind = useMemo(() => {
    const map: Record<WikiTagKind, WikiTag[]> = {
      SOURCE: [],
      CANON: [],
      TYPE: [],
      AUDIENCE: [],
    };
    for (const t of tagsQuery.data ?? []) map[t.kind].push(t);
    return map;
  }, [tagsQuery.data]);

  function toggleTag(slug: string) {
    setSelectedTags((prev) =>
      prev.includes(slug) ? prev.filter((s) => s !== slug) : [...prev, slug],
    );
  }

  return (
    <div className="px-10 py-10 max-w-6xl mx-auto">
      {reading ? (
        <ArticleView
          id={reading}
          onBack={() => setReading(null)}
          onEdit={() => setDrawer(reading)}
        />
      ) : (
        <>
      <header className="mb-8 flex items-start justify-between gap-4">
        <div>
          <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
            Base de connaissances
          </div>
          <h1
            className="mt-1 text-5xl leading-none bg-gradient-to-r from-white to-white/40 bg-clip-text text-transparent"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            Wiki
          </h1>
          <div className="mt-3 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
        </div>
        <button
          type="button"
          onClick={() => setDrawer('new')}
          className="shrink-0 rounded-[10px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] px-5 py-2.5 text-sm font-medium text-white"
        >
          Nouvelle entrée
        </button>
      </header>

      {/* Statut */}
      <div className="mb-4 flex flex-wrap gap-2">
        {STATUS_TABS.map((tab) => {
          const active = status === tab.value;
          return (
            <button
              key={tab.value}
              type="button"
              onClick={() => setStatus(tab.value)}
              className={`rounded-full px-4 py-1.5 text-sm transition-colors ${
                active
                  ? 'bg-[var(--color-accent)] text-white shadow-[var(--shadow-glow-accent)]'
                  : 'border border-[var(--color-border-strong)] text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]'
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Recherche */}
      <div className="mb-5 relative">
        <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-foreground-muted)]" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Rechercher dans les titres, résumés, contenus…"
          className="w-full rounded-[12px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] py-2.5 pl-11 pr-4 text-sm focus:border-[var(--color-accent)] focus:outline-none transition-colors"
        />
      </div>

      {/* Filtres par tag, groupés par axe */}
      <div className="mb-6 space-y-3 rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        {KIND_ORDER.map((kind) => {
          const tags = tagsByKind[kind];
          if (tags.length === 0) return null;
          return (
            <div key={kind} className="flex flex-wrap items-center gap-2">
              <span className="w-24 shrink-0 text-[11px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
                {KIND_LABEL[kind]}
              </span>
              {tags.map((tag) => (
                <TagChip
                  key={tag.id}
                  tag={tag}
                  active={selectedTags.includes(tag.slug)}
                  onClick={() => toggleTag(tag.slug)}
                />
              ))}
            </div>
          );
        })}
        {selectedTags.length > 0 && (
          <button
            type="button"
            onClick={() => setSelectedTags([])}
            className="text-xs text-[var(--color-foreground-subtle)] hover:text-[var(--color-foreground)] underline"
          >
            Réinitialiser les filtres ({selectedTags.length})
          </button>
        )}
      </div>

      {/* Liste */}
      {entriesQuery.isLoading && !entriesQuery.data ? (
        <SkeletonRows count={5} />
      ) : entriesQuery.error ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(entriesQuery.error as Error).message}
        </div>
      ) : !entriesQuery.data || entriesQuery.data.length === 0 ? (
        <div className="rounded-[14px] border border-dashed border-[var(--color-border-strong)] py-16 text-center text-[var(--color-foreground-muted)]">
          Aucune entrée ne correspond. Crée-en une avec « Nouvelle entrée ».
        </div>
      ) : (
        <div className="space-y-3">
          {entriesQuery.data.map((entry, i) => (
            <StaggerItem key={entry.id} index={i}>
              <button
                type="button"
                onClick={() => setReading(entry.id)}
                className="block w-full text-left rounded-[12px] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 hover:bg-[var(--color-surface-elevated)] hover:border-[var(--color-accent)]/40 transition-colors"
              >
                <div className="flex items-start gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="mb-1 flex items-center gap-2">
                      <EntryStatusBadge status={entry.status} />
                      {entry.tags.slice(0, 5).map((t) => (
                        <TagPill key={t.id} tag={t} />
                      ))}
                      {entry.tags.length > 5 && (
                        <span className="text-xs text-[var(--color-foreground-muted)]">
                          +{entry.tags.length - 5}
                        </span>
                      )}
                    </div>
                    <div className="text-sm font-medium truncate">
                      {entry.title}
                    </div>
                    {entry.summary && (
                      <div className="mt-1 text-xs text-[var(--color-foreground-subtle)] line-clamp-2">
                        {entry.summary}
                      </div>
                    )}
                  </div>
                  <div className="shrink-0 text-right text-xs text-[var(--color-foreground-muted)]">
                    {new Date(entry.updatedAt).toLocaleDateString('fr-FR', {
                      dateStyle: 'short',
                    })}
                  </div>
                </div>
              </button>
            </StaggerItem>
          ))}
        </div>
      )}
        </>
      )}

      {drawer && (
        <EntryDrawer
          mode={drawer === 'new' ? 'new' : 'edit'}
          entryId={drawer === 'new' ? null : drawer}
          allTags={tagsQuery.data ?? []}
          onClose={() => setDrawer(null)}
          onSaved={() => {
            qc.invalidateQueries({ queryKey: ['wiki', 'entries'] });
            qc.invalidateQueries({ queryKey: ['wiki', 'tags'] });
            qc.invalidateQueries({ queryKey: ['wiki', 'entry'] });
          }}
        />
      )}
    </div>
  );
}

// ── Vue article (lecture) ─────────────────────────────────

interface Heading {
  /** Ligne 1-based dans le markdown source (pour matcher le node hast). */
  line: number;
  level: 2 | 3;
  text: string;
  slug: string;
}

/** Slug ASCII stable : minuscules, sans accents, séparateurs → tirets. */
function slugify(input: string): string {
  return (
    input
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'section'
  );
}

/** Fabrique un slugger dédupliquant (`foo`, `foo-1`, `foo-2`…). */
function makeSlugger(): (text: string) => string {
  const seen = new Map<string, number>();
  return (text: string) => {
    const base = slugify(text);
    const n = seen.get(base) ?? 0;
    seen.set(base, n + 1);
    return n === 0 ? base : `${base}-${n}`;
  };
}

/** Retire le markdown inline le plus courant pour un libellé de sommaire propre. */
function stripInline(text: string): string {
  return text
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/__([^_]+)__/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .trim();
}

/**
 * Extrait les titres `##` / `###` du markdown pour le sommaire. On ignore
 * les blocs de code (```/~~~) et on numérote les lignes pour matcher la
 * position des nodes hast rendus par react-markdown (même string source).
 */
function parseHeadings(body: string): Heading[] {
  const lines = body.split('\n');
  const slugger = makeSlugger();
  const out: Heading[] = [];
  let inFence = false;
  lines.forEach((raw, i) => {
    const line = raw.trim();
    if (line.startsWith('```') || line.startsWith('~~~')) {
      inFence = !inFence;
      return;
    }
    if (inFence) return;
    const m = /^(#{2,3})\s+(.+?)\s*#*$/.exec(line);
    if (!m) return;
    const level = m[1].length === 2 ? 2 : 3;
    const text = stripInline(m[2]);
    out.push({ line: i + 1, level, text, slug: slugger(text) });
  });
  return out;
}

/** Concatène le texte brut d'un arbre de children React (pour fallback slug). */
function textContent(node: React.ReactNode): string {
  if (node == null || typeof node === 'boolean') return '';
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(textContent).join('');
  if (
    typeof node === 'object' &&
    'props' in node &&
    (node as { props?: { children?: React.ReactNode } }).props
  ) {
    return textContent(
      (node as { props: { children?: React.ReactNode } }).props.children,
    );
  }
  return '';
}

/** Rendu markdown riche et sûr (pas de HTML brut). */
function WikiMarkdown({
  body,
  headings,
}: {
  body: string;
  headings: Heading[];
}) {
  const lineToSlug = useMemo(() => {
    const map = new Map<number, string>();
    for (const h of headings) map.set(h.line, h.slug);
    return map;
  }, [headings]);

  const components = useMemo<Components>(() => {
    const heading =
      (Tag: 'h2' | 'h3') =>
      ({
        node,
        children,
        ...props
      }: {
        node?: { position?: { start?: { line?: number } } };
        children?: React.ReactNode;
      }) => {
        const line = node?.position?.start?.line;
        const id =
          (line != null ? lineToSlug.get(line) : undefined) ??
          slugify(textContent(children));
        return (
          <Tag id={id} {...props}>
            {children}
          </Tag>
        );
      };
    return {
      h2: heading('h2'),
      h3: heading('h3'),
      table: ({ node: _node, children, ...props }) => (
        <div className="wiki-table-wrap">
          <table {...props}>{children}</table>
        </div>
      ),
    };
  }, [lineToSlug]);

  return (
    <div className="wiki-prose">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {body}
      </ReactMarkdown>
    </div>
  );
}

function ArticleView({
  id,
  onBack,
  onEdit,
}: {
  id: string;
  onBack: () => void;
  onEdit: () => void;
}) {
  const entryQuery = useQuery({
    queryKey: ['wiki', 'entry', id],
    queryFn: () => getEntry(id),
  });

  const entry = entryQuery.data;
  const headings = useMemo(
    () => (entry ? parseHeadings(entry.body) : []),
    [entry],
  );

  function scrollTo(slug: string) {
    const el = document.getElementById(slug);
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  const tagsByKind = useMemo(() => {
    const map: Record<WikiTagKind, WikiTag[]> = {
      SOURCE: [],
      CANON: [],
      TYPE: [],
      AUDIENCE: [],
    };
    for (const t of entry?.tags ?? []) map[t.kind].push(t);
    return map;
  }, [entry]);

  return (
    <FadeUp>
      <button
        type="button"
        onClick={onBack}
        className="mb-6 inline-flex items-center gap-1.5 text-sm text-[var(--color-foreground-subtle)] hover:text-[var(--color-foreground)] transition-colors"
      >
        <IconArrowLeft className="h-4 w-4" /> Retour
      </button>

      {entryQuery.isLoading ? (
        <div className="text-sm text-[var(--color-foreground-subtle)]">
          Chargement de l’article…
        </div>
      ) : entryQuery.error || !entry ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {entryQuery.error
            ? (entryQuery.error as Error).message
            : 'Entrée introuvable.'}
        </div>
      ) : (
        <>
          {/* En-tête d'article : titre + ambiance accent */}
          <header className="relative mb-8 overflow-hidden rounded-[16px] border border-[var(--color-border)] bg-[var(--color-surface)] px-8 py-8">
            <div
              aria-hidden
              className="pointer-events-none absolute -right-6 -top-10 select-none text-[10rem] leading-none text-[var(--color-accent)]/[0.06]"
              style={{ fontFamily: 'var(--font-display)' }}
            >
              忍
            </div>
            <div
              aria-hidden
              className="pointer-events-none absolute inset-0"
              style={{
                background:
                  'radial-gradient(ellipse 70% 90% at 0% 0%, var(--color-accent-glow) 0%, transparent 55%)',
                opacity: 0.5,
              }}
            />
            <div className="relative">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
                    Base de connaissances
                  </div>
                  <h1
                    className="mt-2 text-4xl md:text-5xl leading-none text-[var(--color-foreground)]"
                    style={{ fontFamily: 'var(--font-display)' }}
                  >
                    {entry.title}
                  </h1>
                  <div className="mt-4 h-[2px] w-40 max-w-full bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
                </div>
                <button
                  type="button"
                  onClick={onEdit}
                  className="shrink-0 rounded-[10px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] px-5 py-2.5 text-sm font-medium text-white shadow-[var(--shadow-glow-accent)]"
                >
                  Éditer
                </button>
              </div>

              {entry.summary && (
                <p className="mt-4 max-w-2xl text-[15px] leading-relaxed text-[var(--color-foreground-subtle)]">
                  {entry.summary}
                </p>
              )}

              {/* Métadonnées : badges tags + statut */}
              <div className="mt-5 flex flex-wrap items-center gap-2">
                <EntryStatusBadge status={entry.status} />
                {KIND_ORDER.flatMap((kind) =>
                  tagsByKind[kind].map((t) => <TagPill key={t.id} tag={t} />),
                )}
              </div>

              {/* Ligne d'infos */}
              <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-[var(--color-foreground-muted)]">
                <span>
                  Modifié le{' '}
                  {new Date(entry.updatedAt).toLocaleDateString('fr-FR', {
                    dateStyle: 'long',
                  })}
                </span>
                {entry._count && (
                  <span>
                    {entry._count.revisions} révision
                    {entry._count.revisions > 1 ? 's' : ''}
                  </span>
                )}
              </div>

              {entry.sources && (
                <div className="mt-4 rounded-[10px] border border-[var(--color-border)] bg-[var(--color-background)]/60 px-4 py-2.5 text-xs text-[var(--color-foreground-subtle)]">
                  <span className="mr-1.5 uppercase tracking-wider text-[var(--color-foreground-muted)]">
                    Sources
                  </span>
                  {entry.sources}
                </div>
              )}
            </div>
          </header>

          {/* Corps + sommaire */}
          <div className="flex gap-10">
            <article className="min-w-0 flex-1 max-w-3xl">
              {/* Sommaire replié sur petit écran */}
              {headings.length > 0 && (
                <nav className="mb-6 rounded-[12px] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 lg:hidden">
                  <div className="mb-2 text-[11px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
                    Sommaire
                  </div>
                  <ul className="space-y-1">
                    {headings.map((h) => (
                      <li
                        key={h.slug}
                        style={{ paddingLeft: h.level === 3 ? 12 : 0 }}
                      >
                        <a
                          href={`#${h.slug}`}
                          onClick={(e) => {
                            e.preventDefault();
                            scrollTo(h.slug);
                          }}
                          className="text-sm text-[var(--color-foreground-subtle)] hover:text-[var(--color-accent)] transition-colors"
                        >
                          {h.text}
                        </a>
                      </li>
                    ))}
                  </ul>
                </nav>
              )}

              <WikiMarkdown body={entry.body} headings={headings} />
            </article>

            {/* Sommaire sticky (lg+) */}
            {headings.length > 0 && (
              <aside className="hidden w-56 shrink-0 lg:block">
                <div className="sticky top-8">
                  <div className="mb-3 text-[11px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
                    Sur cette page
                  </div>
                  <ul className="space-y-1 border-l border-[var(--color-border-strong)]">
                    {headings.map((h) => (
                      <li key={h.slug}>
                        <a
                          href={`#${h.slug}`}
                          onClick={(e) => {
                            e.preventDefault();
                            scrollTo(h.slug);
                          }}
                          className="block border-l-2 border-transparent py-0.5 text-sm text-[var(--color-foreground-subtle)] hover:border-[var(--color-accent)] hover:text-[var(--color-foreground)] transition-colors"
                          style={{
                            paddingLeft: h.level === 3 ? 24 : 12,
                          }}
                        >
                          {h.text}
                        </a>
                      </li>
                    ))}
                  </ul>
                </div>
              </aside>
            )}
          </div>
        </>
      )}
    </FadeUp>
  );
}

// ── Drawer view/edit ──────────────────────────────────────

function EntryDrawer({
  mode,
  entryId,
  allTags,
  onClose,
  onSaved,
}: {
  mode: 'new' | 'edit';
  entryId: string | null;
  allTags: WikiTag[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [title, setTitle] = useState('');
  const [summary, setSummary] = useState('');
  const [body, setBody] = useState('');
  const [sources, setSources] = useState('');
  const [status, setStatus] = useState<WikiEntryStatus>('DRAFT');
  const [tagSlugs, setTagSlugs] = useState<string[]>([]);
  const [preview, setPreview] = useState(false);

  const detailQuery = useQuery({
    queryKey: ['wiki', 'entry', entryId],
    queryFn: () => getEntry(entryId as string),
    enabled: mode === 'edit' && !!entryId,
  });

  useEffect(() => {
    if (detailQuery.data) {
      const e = detailQuery.data;
      setTitle(e.title);
      setSummary(e.summary ?? '');
      setBody(e.body);
      setSources(e.sources ?? '');
      setStatus(e.status);
      setTagSlugs(e.tags.map((t) => t.slug));
    }
  }, [detailQuery.data]);

  const saveMut = useMutation({
    mutationFn: async () => {
      const payload: CreateEntryInput = {
        title: title.trim(),
        summary: summary.trim() || undefined,
        body,
        status,
        sources: sources.trim() || undefined,
        tagSlugs,
      };
      if (mode === 'new') return createEntry(payload);
      return updateEntry(entryId as string, payload);
    },
    onSuccess: () => {
      toast.success(mode === 'new' ? 'Entrée créée' : 'Entrée mise à jour');
      onSaved();
      onClose();
    },
    onError: (err) =>
      toast.error('Échec de l’enregistrement', {
        description: (err as Error).message,
      }),
  });

  const deleteMut = useMutation({
    mutationFn: () => deleteEntry(entryId as string),
    onSuccess: () => {
      toast.success('Entrée supprimée');
      onSaved();
      onClose();
    },
    onError: (err) =>
      toast.error('Suppression impossible', {
        description: (err as Error).message,
      }),
  });

  function toggleTag(slug: string) {
    setTagSlugs((prev) =>
      prev.includes(slug) ? prev.filter((s) => s !== slug) : [...prev, slug],
    );
  }

  const tagsByKind = useMemo(() => {
    const map: Record<WikiTagKind, WikiTag[]> = {
      SOURCE: [],
      CANON: [],
      TYPE: [],
      AUDIENCE: [],
    };
    for (const t of allTags) map[t.kind].push(t);
    return map;
  }, [allTags]);

  const loading = mode === 'edit' && detailQuery.isLoading;

  return (
    <div className="fixed inset-0 z-40 flex justify-end">
      <div
        className="absolute inset-0 bg-black/50"
        onClick={onClose}
        aria-hidden
      />
      <div className="relative z-10 flex h-full w-full max-w-2xl flex-col border-l border-[var(--color-border)] bg-[var(--color-background)] shadow-2xl">
        <div className="flex items-center justify-between border-b border-[var(--color-border)] px-6 py-4">
          <div className="text-sm uppercase tracking-wider text-[var(--color-foreground-muted)]">
            {mode === 'new' ? 'Nouvelle entrée' : 'Éditer l’entrée'}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground)]"
            aria-label="Fermer"
          >
            <IconX />
          </button>
        </div>

        {loading ? (
          <div className="p-6 text-sm text-[var(--color-foreground-subtle)]">
            Chargement…
          </div>
        ) : (
          <div className="flex-1 space-y-5 overflow-y-auto px-6 py-6">
            <Field label="Titre">
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Ex : Sharingan — mécaniques et paliers"
                className="w-full rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] p-2.5 text-sm focus:border-[var(--color-accent)] focus:outline-none"
              />
            </Field>

            <Field label="Résumé (optionnel)">
              <input
                type="text"
                value={summary}
                onChange={(e) => setSummary(e.target.value)}
                placeholder="Une phrase de contexte"
                className="w-full rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] p-2.5 text-sm focus:border-[var(--color-accent)] focus:outline-none"
              />
            </Field>

            <div className="flex flex-wrap gap-4">
              <Field label="Statut">
                <select
                  value={status}
                  onChange={(e) =>
                    setStatus(e.target.value as WikiEntryStatus)
                  }
                  className="rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] p-2.5 text-sm focus:border-[var(--color-accent)] focus:outline-none"
                >
                  <option value="DRAFT">Brouillon</option>
                  <option value="PUBLISHED">Publié</option>
                  <option value="ARCHIVED">Archivé</option>
                </select>
              </Field>
              {mode === 'edit' && detailQuery.data?._count && (
                <Field label="Révisions">
                  <div className="p-2.5 text-sm text-[var(--color-foreground-subtle)]">
                    {detailQuery.data._count.revisions}
                  </div>
                </Field>
              )}
            </div>

            {/* Tags par axe */}
            <div className="space-y-2">
              <span className="text-[11px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
                Tags
              </span>
              {KIND_ORDER.map((kind) => {
                const tags = tagsByKind[kind];
                if (tags.length === 0) return null;
                return (
                  <div key={kind} className="flex flex-wrap items-center gap-2">
                    <span className="w-24 shrink-0 text-[11px] text-[var(--color-foreground-muted)]">
                      {KIND_LABEL[kind]}
                    </span>
                    {tags.map((tag) => (
                      <TagChip
                        key={tag.id}
                        tag={tag}
                        active={tagSlugs.includes(tag.slug)}
                        onClick={() => toggleTag(tag.slug)}
                      />
                    ))}
                  </div>
                );
              })}
            </div>

            {/* Corps markdown */}
            <div>
              <div className="mb-1 flex items-center justify-between">
                <span className="text-[11px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
                  Contenu (markdown)
                </span>
                <button
                  type="button"
                  onClick={() => setPreview((p) => !p)}
                  className="text-xs text-[var(--color-accent)] hover:underline"
                >
                  {preview ? 'Éditer' : 'Aperçu'}
                </button>
              </div>
              {preview ? (
                <div className="min-h-[240px] rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] p-4 text-sm">
                  <MarkdownPreview source={body} />
                </div>
              ) : (
                <textarea
                  value={body}
                  onChange={(e) => setBody(e.target.value)}
                  rows={14}
                  placeholder="# Titre&#10;&#10;Rédige en markdown…"
                  className="w-full resize-y rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] p-3 font-mono text-sm leading-relaxed focus:border-[var(--color-accent)] focus:outline-none"
                />
              )}
            </div>

            <Field label="Sources / références (optionnel)">
              <input
                type="text"
                value={sources}
                onChange={(e) => setSources(e.target.value)}
                placeholder="Ex : Manga tome 25 ch.239, Databook 3, homemade Reborn"
                className="w-full rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] p-2.5 text-sm focus:border-[var(--color-accent)] focus:outline-none"
              />
            </Field>
          </div>
        )}

        <div className="flex items-center justify-between gap-3 border-t border-[var(--color-border)] px-6 py-4">
          {mode === 'edit' ? (
            <button
              type="button"
              disabled={deleteMut.isPending}
              onClick={() => {
                if (confirm('Supprimer définitivement cette entrée ?'))
                  deleteMut.mutate();
              }}
              className="rounded-[8px] border border-[var(--color-danger)]/40 px-4 py-2 text-sm text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)] disabled:opacity-40"
            >
              Supprimer
            </button>
          ) : (
            <span />
          )}
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-[8px] border border-[var(--color-border-strong)] px-4 py-2 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)]"
            >
              Annuler
            </button>
            <button
              type="button"
              disabled={saveMut.isPending || title.trim().length === 0 || body.length === 0}
              onClick={() => saveMut.mutate()}
              className="rounded-[8px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] px-5 py-2 text-sm font-medium text-white disabled:opacity-40"
            >
              {saveMut.isPending ? 'Enregistrement…' : 'Enregistrer'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Petits composants ─────────────────────────────────────

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-[11px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
        {label}
      </span>
      {children}
    </label>
  );
}

function TagChip({
  tag,
  active,
  onClick,
}: {
  tag: WikiTag;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-full border px-3 py-1 text-xs transition-colors ${
        active
          ? 'text-white'
          : 'text-[var(--color-foreground-subtle)] hover:text-[var(--color-foreground)]'
      }`}
      style={
        active
          ? {
              backgroundColor: tag.color ?? 'var(--color-accent)',
              borderColor: tag.color ?? 'var(--color-accent)',
            }
          : { borderColor: 'var(--color-border-strong)' }
      }
    >
      {tag.label}
    </button>
  );
}

function TagPill({ tag }: { tag: WikiTag }) {
  return (
    <span
      className="inline-block rounded-full border px-2 py-0.5 text-[10px]"
      style={{
        borderColor: (tag.color ?? 'var(--color-border-strong)') as string,
        color: tag.color ?? 'var(--color-foreground-muted)',
      }}
    >
      {tag.label}
    </span>
  );
}

function EntryStatusBadge({ status }: { status: WikiEntryStatus }) {
  const map: Record<WikiEntryStatus, string> = {
    DRAFT:
      'bg-[var(--color-warning-soft)] text-[var(--color-warning)] border-[var(--color-warning)]/40',
    PUBLISHED:
      'bg-[var(--color-success-soft)] text-[var(--color-success)] border-[var(--color-success)]/40',
    ARCHIVED:
      'border-[var(--color-border-strong)] text-[var(--color-foreground-muted)]',
  };
  return (
    <span
      className={`inline-block rounded-full border px-2.5 py-0.5 text-[10px] ${map[status]}`}
    >
      {STATUS_LABEL[status]}
    </span>
  );
}

/**
 * Rendu markdown minimaliste (pas de dépendance externe) : titres #/##/###,
 * gras **texte**, italique *texte*, listes à puces, et paragraphes. Suffisant
 * pour un aperçu staff — le stockage reste du markdown brut.
 */
function MarkdownPreview({ source }: { source: string }) {
  if (!source.trim()) {
    return (
      <span className="text-[var(--color-foreground-muted)]">
        (Rien à prévisualiser)
      </span>
    );
  }
  const lines = source.split('\n');
  const blocks: React.ReactNode[] = [];
  let list: string[] = [];

  const flushList = (key: string) => {
    if (list.length === 0) return;
    blocks.push(
      <ul key={key} className="my-2 list-disc pl-5 space-y-1">
        {list.map((item, i) => (
          <li key={i}>{renderInline(item)}</li>
        ))}
      </ul>,
    );
    list = [];
  };

  lines.forEach((raw, idx) => {
    const line = raw.trimEnd();
    if (line.startsWith('### ')) {
      flushList(`l-${idx}`);
      blocks.push(
        <h3 key={idx} className="mt-3 mb-1 text-base font-semibold">
          {renderInline(line.slice(4))}
        </h3>,
      );
    } else if (line.startsWith('## ')) {
      flushList(`l-${idx}`);
      blocks.push(
        <h2 key={idx} className="mt-4 mb-1 text-lg font-semibold">
          {renderInline(line.slice(3))}
        </h2>,
      );
    } else if (line.startsWith('# ')) {
      flushList(`l-${idx}`);
      blocks.push(
        <h1 key={idx} className="mt-2 mb-2 text-xl font-bold">
          {renderInline(line.slice(2))}
        </h1>,
      );
    } else if (line.startsWith('- ') || line.startsWith('* ')) {
      list.push(line.slice(2));
    } else if (line.trim() === '') {
      flushList(`l-${idx}`);
    } else {
      flushList(`l-${idx}`);
      blocks.push(
        <p key={idx} className="my-1.5 leading-relaxed">
          {renderInline(line)}
        </p>,
      );
    }
  });
  flushList('l-final');

  return <div>{blocks}</div>;
}

/** Gras **x** + italique *x* → JSX (approche simple par tokens). */
function renderInline(text: string): React.ReactNode {
  const parts = text.split(/(\*\*[^*]+\*\*|\*[^*]+\*)/g);
  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={i}>{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith('*') && part.endsWith('*')) {
      return <em key={i}>{part.slice(1, -1)}</em>;
    }
    return <span key={i}>{part}</span>;
  });
}
