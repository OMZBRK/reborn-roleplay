'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import type { Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { toast } from 'sonner';
import { Garde } from '@/components/Garde';
import { SectionTabs } from '@/components/shell/SectionTabs';
import { Badge } from '@/components/ui/Badge';
import { Button, Mot } from '@/components/ui/Button';
import { DataTable, type Colonne } from '@/components/ui/DataTable';
import { Drawer, useConfirm } from '@/components/ui/Dialog';
import { Field as Champ, Input, InputRecherche, Select, Textarea } from '@/components/ui/Field';
import { Icone } from '@/components/ui/Icone';
import { PageHeader } from '@/components/ui/PageHeader';
import { Chargement, EmptyState, ErrorState, messageErreur } from '@/components/ui/States';
import { Segmented } from '@/components/ui/Tabs';
import { useDroits } from '@/lib/droits';
import {
  createEntry,
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

/**
 * Contenu › Wiki (PANEL_V2 §4.5) : liste et lecture des articles du staff,
 * édition dans le tiroir, un seul moteur Markdown (react-markdown, aperçu
 * compris). Un article s'ouvre aussi par l'adresse (?entree=<id>, depuis
 * Ctrl K). La suppression est réservée à `wiki.supprimer` et demande de
 * retaper le titre.
 */

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
  return (
    <Garde capacites={['wiki.editer']}>
      <Wiki />
    </Garde>
  );
}

function Wiki() {
  const qc = useQueryClient();
  const params = useSearchParams();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<WikiEntryStatus | 'ALL'>('ALL');
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  // null = tiroir fermé, 'new' = création, sinon id de l'entrée éditée.
  const [drawer, setDrawer] = useState<string | 'new' | null>(null);
  // null = liste, sinon id de l'entrée lue (?entree=<id> : ouverte depuis Ctrl K).
  const [reading, setReading] = useState<string | null>(params.get('entree'));

  useEffect(() => {
    const e = params.get('entree');
    if (e) setReading(e);
  }, [params]);

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

  const colonnes: Colonne<WikiEntry>[] = [
    {
      cle: 'titre',
      titre: 'Article',
      rendu: (entry) => (
        <span className="two block min-w-0">
          <span className="block truncate font-medium text-fg">{entry.title}</span>
          {entry.summary && <span className="sub2 truncate">{entry.summary}</span>}
        </span>
      ),
    },
    {
      cle: 'statut',
      titre: 'Statut',
      serre: true,
      rendu: (entry) => <EntryStatusBadge status={entry.status} />,
    },
    {
      cle: 'tags',
      titre: 'Étiquettes',
      cacherEtroit: true,
      rendu: (entry) => (
        <span className="flex flex-wrap gap-1.5">
          {entry.tags.slice(0, 4).map((t) => (
            <TagPill key={t.id} tag={t} />
          ))}
          {entry.tags.length > 4 && (
            <span className="text-[12px] text-fg-3">+{entry.tags.length - 4}</span>
          )}
        </span>
      ),
    },
    {
      cle: 'maj',
      titre: 'Modifié',
      serre: true,
      rendu: (entry) => (
        <span title={new Date(entry.updatedAt).toLocaleString('fr-FR')}>
          {new Date(entry.updatedAt).toLocaleDateString('fr-FR', { dateStyle: 'medium' })}
        </span>
      ),
    },
  ];

  return (
    <div className="page">
      {reading ? (
        <ArticleView
          id={reading}
          onBack={() => setReading(null)}
          onEdit={() => setDrawer(reading)}
        />
      ) : (
        <>
          <PageHeader
            fil={[{ label: 'Contenu' }]}
            titre="Contenu"
            sousTitre="Wiki du staff : lore, règles, mécaniques"
            actions={
              <Button variante="primary" icone="plus" onClick={() => setDrawer('new')}>
                Nouvelle entrée
              </Button>
            }
          />
          <SectionTabs section="contenu" />

          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <Segmented<WikiEntryStatus | 'ALL'>
              label="Statut des entrées"
              valeur={status}
              onChange={setStatus}
              options={STATUS_TABS.map((t) => ({ valeur: t.value, label: t.label }))}
            />
            <InputRecherche
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Titres, résumés, contenus"
              aria-label="Rechercher dans le wiki"
              raccourci="/"
              conteneur="w-full sm:w-80"
            />
          </div>

          {(tagsQuery.data?.length ?? 0) > 0 && (
            <div className="card mb-4 flex flex-col gap-3 px-4 py-3">
              {KIND_ORDER.map((kind) => {
                const tags = tagsByKind[kind];
                if (tags.length === 0) return null;
                return (
                  <div key={kind} className="flex flex-wrap items-center gap-2">
                    <span className="w-24 shrink-0 text-[12px] text-fg-3">{KIND_LABEL[kind]}</span>
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
                <Mot ton="neutre" className="self-start" onClick={() => setSelectedTags([])}>
                  Retirer les filtres ({selectedTags.length})
                </Mot>
              )}
            </div>
          )}

          <DataTable<WikiEntry>
            label="Entrées du wiki"
            colonnes={colonnes}
            lignes={entriesQuery.data}
            cle={(e) => e.id}
            chargement={entriesQuery.isLoading}
            erreur={entriesQuery.error as Error | null}
            onReessayer={() => void entriesQuery.refetch()}
            onOuvrir={(e) => setReading(e.id)}
            vide={
              <EmptyState
                icone="book-open"
                titre="Aucune entrée ne correspond."
                aide="Crée-en une avec « Nouvelle entrée »."
              />
            }
          />
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
          onDeleted={() => setReading(null)}
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
  const headings = useMemo(() => (entry ? parseHeadings(entry.body) : []), [entry]);

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

  if (entryQuery.isLoading) return <Chargement lignes={6} />;
  if (entryQuery.error || !entry) {
    return (
      <>
        <Mot ton="neutre" onClick={onBack} className="mb-4">
          <span className="inline-flex items-center gap-1.5">
            <Icone nom="chevron-left" taille={14} /> Retour au wiki
          </span>
        </Mot>
        <ErrorState erreur={entryQuery.error ?? new Error('Entrée introuvable.')} />
      </>
    );
  }

  return (
    <>
      <PageHeader
        fil={[{ label: 'Contenu' }, { label: 'Wiki' }]}
        titre={entry.title}
        apresTitre={<EntryStatusBadge status={entry.status} />}
        sousTitre={
          <>
            Modifié le {new Date(entry.updatedAt).toLocaleDateString('fr-FR', { dateStyle: 'long' })}
            {entry._count && (
              <>
                {' · '}
                {entry._count.revisions} révision{entry._count.revisions > 1 ? 's' : ''}
              </>
            )}
          </>
        }
        actions={
          <>
            <Mot ton="neutre" onClick={onBack}>
              <span className="inline-flex items-center gap-1.5">
                <Icone nom="chevron-left" taille={14} /> Retour au wiki
              </span>
            </Mot>
            <Button icone="pencil" onClick={onEdit}>
              Éditer
            </Button>
          </>
        }
      />

      {(entry.summary || entry.tags.length > 0 || entry.sources) && (
        <div className="mb-6 flex flex-col gap-3">
          {entry.summary && <p className="max-w-[72ch] text-[15px] leading-relaxed text-fg-2">{entry.summary}</p>}
          {entry.tags.length > 0 && (
            <div className="flex flex-wrap items-center gap-1.5">
              {KIND_ORDER.flatMap((kind) => tagsByKind[kind].map((t) => <TagPill key={t.id} tag={t} />))}
            </div>
          )}
          {entry.sources && (
            <p className="text-[12px] text-fg-3">
              <span className="font-semibold text-fg-2">Sources · </span>
              {entry.sources}
            </p>
          )}
        </div>
      )}

      <div className="flex gap-10">
        <article className="min-w-0 flex-1">
          {headings.length > 0 && (
            <nav className="card mb-6 px-4 py-3 lg:hidden" aria-label="Sommaire">
              <div className="eyebrow mb-2">Sommaire</div>
              <ul className="flex flex-col gap-1">
                {headings.map((h) => (
                  <li key={h.slug} style={{ paddingLeft: h.level === 3 ? 12 : 0 }}>
                    <a
                      href={`#${h.slug}`}
                      onClick={(e) => {
                        e.preventDefault();
                        scrollTo(h.slug);
                      }}
                      className="text-[13px] text-fg-2 lien-discret"
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

        {headings.length > 0 && (
          <aside className="hidden w-56 shrink-0 lg:block" aria-label="Sur cette page">
            <div className="sticky top-8">
              <div className="eyebrow mb-3">Sur cette page</div>
              <ul className="flex flex-col gap-1 border-l border-[var(--color-border)]">
                {headings.map((h) => (
                  <li key={h.slug}>
                    <a
                      href={`#${h.slug}`}
                      onClick={(e) => {
                        e.preventDefault();
                        scrollTo(h.slug);
                      }}
                      className="-ml-px block border-l-2 border-transparent py-0.5 text-[13px] text-fg-2 hover:border-[var(--color-gold)] hover:text-fg"
                      style={{ paddingLeft: h.level === 3 ? 24 : 12 }}
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
  );
}

// ── Tiroir d'édition ──────────────────────────────────────

function EntryDrawer({
  mode,
  entryId,
  allTags,
  onClose,
  onSaved,
  onDeleted,
}: {
  mode: 'new' | 'edit';
  entryId: string | null;
  allTags: WikiTag[];
  onClose: () => void;
  onSaved: () => void;
  onDeleted: () => void;
}) {
  const { peut } = useDroits();
  const { confirmer } = useConfirm();
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
      toast.success(mode === 'new' ? 'Entrée créée' : 'Entrée enregistrée');
      onSaved();
      onClose();
    },
    onError: (e) => toast.error('Entrée non enregistrée', { description: messageErreur(e) }),
  });

  const deleteMut = useMutation({
    mutationFn: () => deleteEntry(entryId as string),
    onSuccess: () => {
      toast.success('Entrée supprimée');
      onSaved();
      onDeleted();
      onClose();
    },
    onError: (e) => toast.error('Suppression impossible', { description: messageErreur(e) }),
  });

  function toggleTag(slug: string) {
    setTagSlugs((prev) => (prev.includes(slug) ? prev.filter((s) => s !== slug) : [...prev, slug]));
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
    <Drawer
      ouvert
      large
      onFermer={onClose}
      titre={mode === 'new' ? 'Nouvelle entrée' : 'Éditer l’entrée'}
      pied={
        <>
          {mode === 'edit' && peut('wiki.supprimer') && (
            <Button
              variante="danger"
              enCours={deleteMut.isPending}
              onClick={async () => {
                const ok = await confirmer({
                  titre: `Supprimer « ${title || 'cette entrée'} » ?`,
                  message: 'L’entrée et ses révisions sont supprimées définitivement.',
                  confirmer: 'Supprimer',
                  danger: true,
                  retaper: title.trim() || undefined,
                });
                if (ok) deleteMut.mutate();
              }}
            >
              Supprimer
            </Button>
          )}
          <span className="ml-auto" />
          <Button variante="ghost" onClick={onClose}>
            Annuler
          </Button>
          <Button
            variante="primary"
            enCours={saveMut.isPending}
            disabled={title.trim().length === 0 || body.length === 0}
            onClick={() => saveMut.mutate()}
          >
            Enregistrer
          </Button>
        </>
      }
    >
      {loading ? (
        <Chargement lignes={5} />
      ) : (
        <>
          <Champ label="Titre">
            {(fid) => (
              <Input
                id={fid}
                data-autofocus
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Ex. : Sharingan, mécaniques et paliers"
              />
            )}
          </Champ>
          <Champ label="Résumé (facultatif)">
            {(fid) => (
              <Input id={fid} value={summary} onChange={(e) => setSummary(e.target.value)} placeholder="Une phrase de contexte" />
            )}
          </Champ>
          <div className="flex flex-wrap gap-4">
            <Champ label="Statut">
              {(fid) => (
                <Select id={fid} value={status} onChange={(e) => setStatus(e.target.value as WikiEntryStatus)}>
                  <option value="DRAFT">Brouillon</option>
                  <option value="PUBLISHED">Publié</option>
                  <option value="ARCHIVED">Archivé</option>
                </Select>
              )}
            </Champ>
            {mode === 'edit' && detailQuery.data?._count && (
              <div className="field">
                <span className="label">Révisions</span>
                <span className="py-2 text-[14px] text-fg-2">{detailQuery.data._count.revisions}</span>
              </div>
            )}
          </div>

          {allTags.length > 0 && (
            <div className="field">
              <span className="label">Étiquettes</span>
              {KIND_ORDER.map((kind) => {
                const tags = tagsByKind[kind];
                if (tags.length === 0) return null;
                return (
                  <div key={kind} className="flex flex-wrap items-center gap-2">
                    <span className="w-24 shrink-0 text-[12px] text-fg-3">{KIND_LABEL[kind]}</span>
                    {tags.map((tag) => (
                      <TagChip key={tag.id} tag={tag} active={tagSlugs.includes(tag.slug)} onClick={() => toggleTag(tag.slug)} />
                    ))}
                  </div>
                );
              })}
            </div>
          )}

          <div className="field">
            <div className="label">
              <span>Contenu (Markdown)</span>
              <Mot onClick={() => setPreview((p) => !p)}>{preview ? 'Éditer' : 'Aperçu'}</Mot>
            </div>
            {preview ? (
              <div className="card min-h-[240px] px-4 py-3">
                {body.trim() ? (
                  <WikiMarkdown body={body} headings={parseHeadings(body)} />
                ) : (
                  <span className="text-fg-3">Rien à prévisualiser.</span>
                )}
              </div>
            ) : (
              <Textarea
                aria-label="Contenu en Markdown"
                value={body}
                onChange={(e) => setBody(e.target.value)}
                rows={14}
                placeholder={'# Titre\n\nRédige en Markdown…'}
                className="font-mono text-[13px]"
              />
            )}
          </div>

          <Champ label="Sources et références (facultatif)">
            {(fid) => (
              <Input
                id={fid}
                value={sources}
                onChange={(e) => setSources(e.target.value)}
                placeholder="Ex. : manga tome 25 ch. 239, Databook 3, création Reborn"
              />
            )}
          </Champ>
        </>
      )}
    </Drawer>
  );
}

// ── Petits composants ─────────────────────────────────────

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
    <button type="button" className="chip" aria-pressed={active} onClick={onClick}>
      {tag.color && (
        <span className="h-2 w-2 rounded-full" style={{ background: tag.color }} aria-hidden />
      )}
      {tag.label}
    </button>
  );
}

function TagPill({ tag }: { tag: WikiTag }) {
  return (
    <span className="tag" title={KIND_LABEL[tag.kind]}>
      {tag.color && (
        <span className="mr-1.5 h-1.5 w-1.5 rounded-full" style={{ background: tag.color }} aria-hidden />
      )}
      {tag.label}
    </span>
  );
}

function EntryStatusBadge({ status }: { status: WikiEntryStatus }) {
  const ton = status === 'PUBLISHED' ? 'succes' : status === 'DRAFT' ? 'attention' : 'neutre';
  return <Badge ton={ton}>{STATUS_LABEL[status]}</Badge>;
}
