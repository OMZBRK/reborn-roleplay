'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { toast } from 'sonner';
import { Garde } from '@/components/Garde';
import { SectionTabs } from '@/components/shell/SectionTabs';
import { Button } from '@/components/ui/Button';
import { PageHeader } from '@/components/ui/PageHeader';
import { createIdea, listIdeas, updateIdea } from '@/lib/wiki';
import type { WikiIdea, WikiIdeaStatus } from '@/lib/types';

const STATUS_ORDER: WikiIdeaStatus[] = [
  'PROPOSED',
  'ACCEPTED',
  'IN_PROGRESS',
  'DONE',
  'REJECTED',
];

const STATUS_LABEL: Record<WikiIdeaStatus, string> = {
  PROPOSED: 'Proposé',
  ACCEPTED: 'Accepté',
  IN_PROGRESS: 'En cours',
  DONE: 'Fait',
  REJECTED: 'Rejeté',
};

const STATUS_ACCENT: Record<WikiIdeaStatus, string> = {
  PROPOSED: 'var(--color-foreground-muted)',
  ACCEPTED: 'var(--color-info)',
  IN_PROGRESS: 'var(--color-warning)',
  DONE: 'var(--color-success)',
  REJECTED: 'var(--color-danger)',
};

export default function WikiIdeasPage() {
  return (
    <Garde capacites={['wiki.editer']}>
      <Idees />
    </Garde>
  );
}

/**
 * Contenu › Idées (PANEL_V2 §4.5, Q5) : la banque d'idées du staff, onglet
 * du wiki, kanban gardé tel quel.
 */
function Idees() {
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [category, setCategory] = useState('');

  const ideasQuery = useQuery({
    queryKey: ['wiki', 'ideas'],
    queryFn: () => listIdeas(),
  });

  const createMut = useMutation({
    mutationFn: () =>
      createIdea({
        title: title.trim(),
        body: body.trim(),
        category: category.trim() || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['wiki', 'ideas'] });
      toast.success('Idée ajoutée');
      setTitle('');
      setBody('');
      setCategory('');
      setShowForm(false);
    },
    onError: (err) =>
      toast.error('Échec', { description: (err as Error).message }),
  });

  const statusMut = useMutation({
    mutationFn: ({ id, status }: { id: string; status: WikiIdeaStatus }) =>
      updateIdea(id, { status }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['wiki', 'ideas'] });
      toast.success('Statut mis à jour');
    },
    onError: (err) =>
      toast.error('Échec', { description: (err as Error).message }),
  });

  const columns = useMemo(() => {
    const map: Record<WikiIdeaStatus, WikiIdea[]> = {
      PROPOSED: [],
      ACCEPTED: [],
      IN_PROGRESS: [],
      DONE: [],
      REJECTED: [],
    };
    for (const idea of ideasQuery.data ?? []) map[idea.status].push(idea);
    return map;
  }, [ideasQuery.data]);

  return (
    <div className="page">
      <PageHeader
        fil={[{ label: 'Contenu' }]}
        titre="Contenu"
        sousTitre="Banque d’idées du staff : propose, trie, suis l’avancement"
        actions={
          <Button variante={showForm ? 'secondary' : 'primary'} icone={showForm ? 'x' : 'plus'} onClick={() => setShowForm((v) => !v)}>
            {showForm ? 'Fermer' : 'Nouvelle idée'}
          </Button>
        }
      />
      <SectionTabs section="contenu" />

      {showForm && (
        <div className="mb-8 rounded-[8px] border border-[var(--color-border)] bg-[var(--color-surface)] p-5">
          <div className="mb-4 text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
            Proposer une idée
          </div>
          <div className="space-y-3">
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Titre de l’idée"
              className="w-full rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-2.5 text-sm focus:border-[var(--color-accent)] focus:outline-none"
            />
            <input
              type="text"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              placeholder="Catégorie (event / technique / build / system…)"
              className="w-full rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-2.5 text-sm focus:border-[var(--color-accent)] focus:outline-none"
            />
            <textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              rows={4}
              placeholder="Décris l’idée…"
              className="w-full resize-y rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-3 text-sm focus:border-[var(--color-accent)] focus:outline-none"
            />
            <div className="flex justify-end">
              <button
                type="button"
                disabled={
                  createMut.isPending ||
                  title.trim().length === 0 ||
                  body.trim().length === 0
                }
                onClick={() => createMut.mutate()}
                className="rounded-[8px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] px-5 py-2.5 text-sm font-medium text-[var(--color-on-gold)] disabled:opacity-40"
              >
                {createMut.isPending ? 'Ajout…' : 'Ajouter'}
              </button>
            </div>
          </div>
        </div>
      )}

      {ideasQuery.isLoading && !ideasQuery.data ? (
        <div className="text-sm text-[var(--color-foreground-subtle)]">
          Chargement…
        </div>
      ) : ideasQuery.error ? (
        <div className="rounded-[8px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(ideasQuery.error as Error).message}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3 xl:grid-cols-5">
          {STATUS_ORDER.map((st) => (
            <div key={st} className="flex flex-col">
              <div className="mb-3 flex items-center gap-2">
                <span
                  className="inline-block h-2 w-2 rounded-full"
                  style={{ backgroundColor: STATUS_ACCENT[st] }}
                />
                <span className="text-sm font-medium">{STATUS_LABEL[st]}</span>
                <span className="text-xs text-[var(--color-foreground-muted)]">
                  {columns[st].length}
                </span>
              </div>
              <div className="flex-1 space-y-3 rounded-[8px] border border-dashed border-[var(--color-border)] bg-[var(--color-surface)]/40 p-3 min-h-[120px]">
                {columns[st].length === 0 ? (
                  <div className="py-6 text-center text-xs text-[var(--color-foreground-muted)]">
                    Vide
                  </div>
                ) : (
                  columns[st].map((idea) => (
                    <IdeaCard
                      key={idea.id}
                      idea={idea}
                      onMove={(status) =>
                        statusMut.mutate({ id: idea.id, status })
                      }
                    />
                  ))
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function IdeaCard({
  idea,
  onMove,
}: {
  idea: WikiIdea;
  onMove: (status: WikiIdeaStatus) => void;
}) {
  return (
    <div className="rounded-[8px] border border-[var(--color-border)] bg-[var(--color-surface)] p-3">
      <div className="mb-1 flex items-center gap-2">
        {idea.category && (
          <span className="rounded-full border border-[var(--color-border-strong)] px-2 py-0.5 text-[10px] text-[var(--color-foreground-muted)]">
            {idea.category}
          </span>
        )}
      </div>
      <div className="text-sm font-medium">{idea.title}</div>
      <div className="mt-1 text-xs text-[var(--color-foreground-subtle)] line-clamp-4 whitespace-pre-wrap">
        {idea.body}
      </div>
      {idea.linkedEntry && (
        <div className="mt-2 text-[11px] text-[var(--color-accent)]">
          ↳ {idea.linkedEntry.title}
        </div>
      )}
      <div className="mt-3">
        <select
          value={idea.status}
          onChange={(e) => onMove(e.target.value as WikiIdeaStatus)}
          className="select h-8 rounded-[6px] text-xs"
        >
          {STATUS_ORDER.map((st) => (
            <option key={st} value={st}>
              {STATUS_LABEL[st]}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
