'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '@/lib/api';
import type { Paginated, TicketListItem, TicketStatus } from '@/lib/types';

const STATUS_TABS: Array<{ value: TicketStatus | 'ALL'; label: string }> = [
  { value: 'OPEN', label: 'Ouverts' },
  { value: 'IN_PROGRESS', label: 'En cours' },
  { value: 'RESOLVED', label: 'Resolus' },
  { value: 'CLOSED', label: 'Fermes' },
  { value: 'ALL', label: 'Tous' },
];

export default function TicketsListPage() {
  const [status, setStatus] = useState<TicketStatus | 'ALL'>('OPEN');

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'tickets', status],
    queryFn: () => {
      const q = status === 'ALL' ? '' : `?status=${status}`;
      return api<Paginated<TicketListItem>>(`/admin/tickets${q}`);
    },
  });

  return (
    <div className="px-10 py-10 max-w-6xl mx-auto">
      <header className="mb-8">
        <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
          Support
        </div>
        <h1
          className="mt-1 text-5xl leading-none bg-gradient-to-r from-white to-white/40 bg-clip-text text-transparent"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          Tickets
        </h1>
        <div className="mt-3 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
      </header>

      <div className="mb-6 flex flex-wrap gap-2">
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

      {isLoading ? (
        <div className="text-[var(--color-foreground-subtle)]">Chargement…</div>
      ) : error ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      ) : !data || data.items.length === 0 ? (
        <div className="rounded-[14px] border border-dashed border-[var(--color-border-strong)] py-16 text-center text-[var(--color-foreground-muted)]">
          Aucun ticket dans cette categorie.
        </div>
      ) : (
        <div className="space-y-3">
          {data.items.map((item) => (
            <Link
              key={item.id}
              href={`/tickets/${item.id}`}
              className="block rounded-[12px] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 hover:bg-[var(--color-surface-elevated)] hover:border-[var(--color-accent)]/40 transition-colors"
            >
              <div className="flex items-start gap-4">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
                      {item.category}
                    </span>
                    <TicketStatusBadge status={item.status} />
                  </div>
                  <div className="text-sm font-medium truncate">
                    {item.subject}
                  </div>
                  {item.lastMessagePreview && (
                    <div className="mt-1 text-xs text-[var(--color-foreground-subtle)] truncate">
                      {item.lastMessagePreview}
                    </div>
                  )}
                </div>
                <div className="text-right shrink-0">
                  <div className="text-sm">{item.user.minecraftUsername}</div>
                  <div className="text-xs text-[var(--color-foreground-muted)]">
                    {new Date(item.updatedAt).toLocaleString('fr-FR', {
                      dateStyle: 'short',
                      timeStyle: 'short',
                    })}
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
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
      className={`inline-block rounded-full border px-2.5 py-0.5 text-xs ${tone.cls}`}
    >
      {tone.label}
    </span>
  );
}
