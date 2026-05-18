'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { IconAudit, IconSearch } from '@/components/icons';
import { SkeletonTable } from '@/components/Skeleton';
import { api } from '@/lib/api';
import type { AuditLogItem, Paginated } from '@/lib/types';

/**
 * Visualisation du log d'audit. Read-only, accessible ADMIN+ uniquement
 * (le RolesGuard côté API rejette si rôle < ADMIN, donc une route non-admin
 * verra juste un 403 dans le toast erreur).
 *
 * Le filtre client-side reste minimal — pour scaler on poussera les
 * filtres serveur quand le log depassera ~10000 entrees.
 */
export default function AuditPage() {
  const [actionFilter, setActionFilter] = useState('');
  const [sourceFilter, setSourceFilter] = useState<'all' | 'panel' | 'discord' | 'launcher' | 'system'>('all');

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'audit', actionFilter, sourceFilter],
    queryFn: () => {
      const qs = new URLSearchParams();
      if (actionFilter.trim()) qs.set('action', actionFilter.trim());
      if (sourceFilter !== 'all') qs.set('source', sourceFilter);
      return api<Paginated<AuditLogItem>>(
        `/admin/audit${qs.toString() ? `?${qs}` : ''}`,
      );
    },
    refetchInterval: 30_000,
  });

  return (
    <div className="px-10 py-10 max-w-6xl mx-auto">
      <header className="mb-8">
        <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
          Securite
        </div>
        <h1
          className="mt-1 text-5xl leading-none bg-gradient-to-r from-white to-white/40 bg-clip-text text-transparent"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          Audit log
        </h1>
        <div className="mt-3 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
        <p className="mt-3 text-xs text-[var(--color-foreground-muted)] max-w-2xl">
          Toutes les actions staff (decisions whitelist, status tickets,
          claim/release, publication release) sont loggees avec une chaine
          de hash SHA-256. Casser une entree casse toute la chaine en aval.
        </p>
      </header>

      <div className="mb-4 flex flex-wrap gap-2">
        {(['all', 'panel', 'discord', 'launcher', 'system'] as const).map(
          (s) => {
            const active = sourceFilter === s;
            return (
              <button
                key={s}
                type="button"
                onClick={() => setSourceFilter(s)}
                className={`rounded-full px-4 py-1.5 text-sm transition-colors ${
                  active
                    ? 'bg-[var(--color-accent)] text-white shadow-[var(--shadow-glow-accent)]'
                    : 'border border-[var(--color-border-strong)] text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]'
                }`}
              >
                {s === 'all' ? 'Tous' : s}
              </button>
            );
          },
        )}
      </div>

      <div className="mb-6 relative">
        <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-foreground-muted)]" />
        <input
          type="text"
          value={actionFilter}
          onChange={(e) => setActionFilter(e.target.value)}
          placeholder="Filtrer par action (ex: whitelist.approve, ticket.close, release)…"
          className="w-full rounded-[12px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] py-2.5 pl-11 pr-4 text-sm focus:border-[var(--color-accent)] focus:outline-none"
        />
      </div>

      {isLoading && !data ? (
        <SkeletonTable rows={8} />
      ) : error ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      ) : !data || data.items.length === 0 ? (
        <div className="rounded-[14px] border border-dashed border-[var(--color-border-strong)] py-16 text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[var(--color-accent-soft)] text-[var(--color-accent)]">
            <IconAudit />
          </div>
          <div className="text-sm text-[var(--color-foreground)]">
            Aucun evenement audite pour ce filtre.
          </div>
        </div>
      ) : (
        <div className="overflow-hidden rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)]">
          <table className="w-full text-sm">
            <thead className="bg-[var(--color-surface-elevated)] text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
              <tr>
                <th className="px-4 py-3 text-left">Quand</th>
                <th className="px-4 py-3 text-left">Acteur</th>
                <th className="px-4 py-3 text-left">Action</th>
                <th className="px-4 py-3 text-left">Cible</th>
                <th className="px-4 py-3 text-left">Source</th>
                <th className="px-4 py-3 text-left">Meta</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((row) => (
                <tr
                  key={row.id}
                  className="border-t border-[var(--color-border)] hover:bg-[var(--color-surface-elevated)] transition-colors"
                >
                  <td className="px-4 py-2.5 text-xs text-[var(--color-foreground-muted)] whitespace-nowrap">
                    {formatTimestamp(row.createdAt)}
                  </td>
                  <td className="px-4 py-2.5 text-[var(--color-foreground)] whitespace-nowrap">
                    @{row.actor.username}
                  </td>
                  <td className="px-4 py-2.5">
                    <code className="rounded bg-[var(--color-background)] px-1.5 py-0.5 text-[11px] text-[var(--color-accent)]">
                      {row.action}
                    </code>
                  </td>
                  <td className="px-4 py-2.5 text-xs text-[var(--color-foreground-subtle)]">
                    {row.targetUser ? `@${row.targetUser.username}` : ''}
                    {row.targetEntity && (
                      <div className="text-[10px] text-[var(--color-foreground-muted)] font-mono break-all">
                        {row.targetEntity}
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-2.5">
                    <SourceBadge source={row.source} />
                  </td>
                  <td className="px-4 py-2.5 text-[11px] text-[var(--color-foreground-muted)] font-mono max-w-[280px] truncate">
                    {row.metadata
                      ? JSON.stringify(row.metadata).slice(0, 80)
                      : ''}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {data.total > data.items.length && (
            <div className="border-t border-[var(--color-border)] px-4 py-2.5 text-xs text-[var(--color-foreground-muted)]">
              {data.items.length} / {data.total} entrees
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function SourceBadge({ source }: { source: string }) {
  const map: Record<string, { cls: string; label: string }> = {
    panel: {
      cls: 'bg-[var(--color-accent-soft)] text-[var(--color-accent)] border-[var(--color-accent)]/40',
      label: 'panel',
    },
    discord: {
      cls: 'bg-[#8b5cf6]/15 text-[#a78bfa] border-[#8b5cf6]/40',
      label: 'discord',
    },
    launcher: {
      cls: 'bg-[var(--color-success-soft)] text-[var(--color-success)] border-[var(--color-success)]/40',
      label: 'launcher',
    },
    system: {
      cls: 'border-[var(--color-border-strong)] text-[var(--color-foreground-muted)]',
      label: 'system',
    },
  };
  const t = map[source] ?? map.system!;
  return (
    <span
      className={`inline-block rounded-full border px-2 py-0.5 text-[10px] ${t.cls}`}
    >
      {t.label}
    </span>
  );
}

function formatTimestamp(iso: string): string {
  return new Date(iso).toLocaleString('fr-FR', {
    dateStyle: 'short',
    timeStyle: 'medium',
  });
}
