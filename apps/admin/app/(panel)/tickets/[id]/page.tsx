'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { use, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { AssignmentBlock } from '@/components/AssignmentBlock';
import { api } from '@/lib/api';
import type { AdminMessage, TicketDetail, TicketStatus } from '@/lib/types';

export default function TicketDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const qc = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'tickets', id],
    queryFn: () => api<TicketDetail>(`/admin/tickets/${id}`),
    // Poll toutes les 5s comme le launcher cote user : permet de voir
    // les nouveaux messages du joueur sans rafraichir manuellement.
    refetchInterval: 5_000,
  });

  const [draft, setDraft] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);
  const lastMessageCountRef = useRef(0);

  // Auto-scroll en bas quand un nouveau message arrive.
  useEffect(() => {
    if (!data) return;
    if (data.messages.length > lastMessageCountRef.current) {
      scrollRef.current?.scrollTo({
        top: scrollRef.current.scrollHeight,
        behavior: 'smooth',
      });
      lastMessageCountRef.current = data.messages.length;
    }
  }, [data]);

  const sendMut = useMutation({
    mutationFn: (content: string) =>
      api<AdminMessage>(`/admin/tickets/${id}/messages`, {
        method: 'POST',
        body: { content },
      }),
    onSuccess: () => {
      setDraft('');
      qc.invalidateQueries({ queryKey: ['admin', 'tickets', id] });
      qc.invalidateQueries({ queryKey: ['admin', 'tickets'] });
    },
    onError: (err) => {
      toast.error("Message non envoye", {
        description: (err as Error).message,
      });
    },
  });

  const statusMut = useMutation({
    mutationFn: (status: TicketStatus) =>
      api(`/admin/tickets/${id}`, {
        method: 'PATCH',
        body: { status },
      }),
    onSuccess: (_data, status) => {
      qc.invalidateQueries({ queryKey: ['admin', 'tickets', id] });
      qc.invalidateQueries({ queryKey: ['admin', 'tickets'] });
      qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
      const labels: Record<TicketStatus, string> = {
        OPEN: 'Ticket re-ouvert',
        IN_PROGRESS: 'Ticket pris en charge',
        RESOLVED: 'Ticket marque resolu',
        CLOSED: 'Ticket ferme',
      };
      toast.success(labels[status]);
    },
    onError: (err) => {
      toast.error('Changement de statut echoue', {
        description: (err as Error).message,
      });
    },
  });

  if (isLoading) {
    return (
      <div className="p-10 text-[var(--color-foreground-subtle)]">
        Chargement…
      </div>
    );
  }
  if (error) {
    return (
      <div className="p-10">
        <Link
          href="/tickets"
          className="text-sm text-[var(--color-accent)] hover:underline"
        >
          ← Retour
        </Link>
        <div className="mt-4 rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      </div>
    );
  }
  if (!data) return null;

  const isClosed = data.status === 'CLOSED';

  return (
    <div className="h-screen flex flex-col">
      <div className="border-b border-[var(--color-border)] bg-[var(--color-surface)] px-10 py-5">
        <Link
          href="/tickets"
          className="text-xs text-[var(--color-accent)] hover:underline"
        >
          ← Tous les tickets
        </Link>
        <div className="mt-2 flex items-start justify-between gap-6">
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 mb-1 text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
              <span>{data.category}</span>
              <span>·</span>
              <TicketStatusBadge status={data.status} />
            </div>
            <h1
              className="text-3xl leading-tight truncate"
              style={{ fontFamily: 'var(--font-display)' }}
            >
              {data.subject}
            </h1>
            <div className="mt-1 text-sm text-[var(--color-foreground-subtle)]">
              par{' '}
              <Link
                href={`/players/${data.user.id}`}
                className="font-medium text-[var(--color-foreground)] hover:text-[var(--color-accent)] hover:underline"
              >
                {data.user.minecraftUsername}
              </Link>
              {data.user.discordUsername && (
                <> · @{data.user.discordUsername}</>
              )}
              {' · '}ouvert le{' '}
              {new Date(data.createdAt).toLocaleString('fr-FR', {
                dateStyle: 'medium',
                timeStyle: 'short',
              })}
            </div>
          </div>
          <div className="w-[240px] shrink-0">
            <AssignmentBlock
              kind="tickets"
              id={data.id}
              assignee={data.assignee}
              assignedAt={data.assignedAt}
            />
          </div>
          <StatusActions
            current={data.status}
            disabled={statusMut.isPending}
            onChange={(s) => statusMut.mutate(s)}
          />
        </div>
      </div>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-10 py-6">
        <div className="max-w-3xl mx-auto space-y-3">
          {data.messages.map((m) => (
            <MessageBubble key={m.id} message={m} />
          ))}
        </div>
      </div>

      <div className="border-t border-[var(--color-border)] bg-[var(--color-surface)] px-10 py-4">
        <div className="max-w-3xl mx-auto">
          {isClosed ? (
            <div className="rounded-[10px] border border-dashed border-[var(--color-border-strong)] py-4 text-center text-sm text-[var(--color-foreground-muted)]">
              Ce ticket est ferme. Re-ouvre-le pour repondre.
            </div>
          ) : (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const content = draft.trim();
                if (content.length === 0 || sendMut.isPending) return;
                sendMut.mutate(content);
              }}
              className="flex items-end gap-3"
            >
              <textarea
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    const content = draft.trim();
                    if (content.length > 0 && !sendMut.isPending) {
                      sendMut.mutate(content);
                    }
                  }
                }}
                rows={2}
                placeholder="Repondre au joueur… (Entree pour envoyer, Maj+Entree pour saut de ligne)"
                className="flex-1 resize-none rounded-[10px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-3 text-sm focus:border-[var(--color-accent)] focus:outline-none"
              />
              <button
                type="submit"
                disabled={draft.trim().length === 0 || sendMut.isPending}
                className="rounded-[10px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] px-5 py-2.5 text-sm font-medium text-white disabled:opacity-40 disabled:cursor-not-allowed shadow-[var(--shadow-glow-accent)]"
              >
                {sendMut.isPending ? 'Envoi…' : 'Envoyer'}
              </button>
            </form>
          )}
          {sendMut.error && (
            <div className="mt-2 text-xs text-[var(--color-danger)]">
              {(sendMut.error as Error).message}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function MessageBubble({ message }: { message: AdminMessage }) {
  const isStaff = message.authorType === 'STAFF';
  return (
    <div className={`flex ${isStaff ? 'justify-end' : 'justify-start'}`}>
      <div
        className={`max-w-[80%] rounded-[12px] px-4 py-3 ${
          isStaff
            ? 'bg-[var(--color-accent-soft)] border border-[var(--color-accent)]/30'
            : 'bg-[var(--color-surface-elevated)] border border-[var(--color-border)]'
        }`}
      >
        <div
          className={`mb-1 text-xs ${
            isStaff
              ? 'text-[var(--color-accent)] font-medium'
              : 'text-[var(--color-foreground-muted)]'
          }`}
        >
          {message.authorName ?? (isStaff ? 'Staff' : 'Joueur')}
          <span className="ml-2 text-[var(--color-foreground-muted)] font-normal">
            {new Date(message.createdAt).toLocaleString('fr-FR', {
              dateStyle: 'short',
              timeStyle: 'short',
            })}
          </span>
        </div>
        <div className="text-sm whitespace-pre-wrap">{message.content}</div>
      </div>
    </div>
  );
}

function StatusActions({
  current,
  disabled,
  onChange,
}: {
  current: TicketStatus;
  disabled: boolean;
  onChange: (s: TicketStatus) => void;
}) {
  // Transitions autorisees depuis le panel. On masque le bouton qui
  // remettrait au statut courant.
  const transitions: Array<{ to: TicketStatus; label: string; tone: 'warning' | 'success' | 'muted' }> = [];
  if (current !== 'IN_PROGRESS') {
    transitions.push({ to: 'IN_PROGRESS', label: 'Prendre en charge', tone: 'warning' });
  }
  if (current !== 'RESOLVED') {
    transitions.push({ to: 'RESOLVED', label: 'Marquer resolu', tone: 'success' });
  }
  if (current !== 'CLOSED') {
    transitions.push({ to: 'CLOSED', label: 'Fermer', tone: 'muted' });
  }
  if (current === 'CLOSED') {
    transitions.unshift({ to: 'OPEN', label: 'Reouvrir', tone: 'warning' });
  }
  return (
    <div className="flex flex-col gap-1.5 shrink-0">
      {transitions.map((t) => (
        <button
          key={t.to}
          type="button"
          disabled={disabled}
          onClick={() => onChange(t.to)}
          className={`rounded-[8px] border px-3 py-1.5 text-xs transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${
            t.tone === 'success'
              ? 'border-[var(--color-success)]/40 text-[var(--color-success)] hover:bg-[var(--color-success-soft)]'
              : t.tone === 'warning'
                ? 'border-[var(--color-warning)]/40 text-[var(--color-warning)] hover:bg-[var(--color-warning-soft)]'
                : 'border-[var(--color-border-strong)] text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)]'
          }`}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

function TicketStatusBadge({ status }: { status: TicketStatus }) {
  const map: Record<TicketStatus, { label: string; cls: string }> = {
    OPEN: {
      label: 'Ouvert',
      cls: 'bg-[var(--color-accent-soft)] text-[var(--color-accent)] border-[var(--color-accent)]/40',
    },
    IN_PROGRESS: {
      label: 'En cours',
      cls: 'bg-[var(--color-warning-soft)] text-[var(--color-warning)] border-[var(--color-warning)]/40',
    },
    RESOLVED: {
      label: 'Resolu',
      cls: 'bg-[var(--color-success-soft)] text-[var(--color-success)] border-[var(--color-success)]/40',
    },
    CLOSED: {
      label: 'Ferme',
      cls: 'border-[var(--color-border-strong)] text-[var(--color-foreground-muted)]',
    },
  };
  const tone = map[status];
  return (
    <span
      className={`inline-block rounded-full border px-2.5 py-0.5 text-[10px] uppercase tracking-wider normal-case-content ${tone.cls}`}
    >
      {tone.label}
    </span>
  );
}
