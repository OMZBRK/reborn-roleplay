'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { use, useState } from 'react';
import { toast } from 'sonner';
import { AssignmentBlock } from '@/components/AssignmentBlock';
import { ChatPanel } from '@/components/ChatPanel';
import { api } from '@/lib/api';
import type { TicketDetail, TicketStatus } from '@/lib/types';

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

  // Envoi des messages + scroll auto delegues a <ChatPanel /> (composant
  // unifie). Plus de state local pour le draft ici.

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

      <div className="flex-1 p-4 overflow-hidden">
        <ChatPanel
          endpoint={`/admin/tickets/${data.id}/messages`}
          queryKey={['admin', 'tickets', data.id]}
          messages={data.messages}
          canSend={!isClosed}
          playerUuid={data.user.minecraftUuid}
          playerName={data.user.minecraftUsername}
          emptyLabel={`Aucun message — répondez à ${data.user.minecraftUsername} ci-dessous.`}
          closedLabel="Ce ticket est fermé. Réouvre-le pour répondre."
        />
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
