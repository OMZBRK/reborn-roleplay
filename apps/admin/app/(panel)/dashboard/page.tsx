'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { FadeUp } from '@/components/anim';
import {
  IconClock,
  IconPlayers,
  IconShield,
  IconTickets,
  IconWhitelist,
} from '@/components/icons';
import { Skeleton } from '@/components/Skeleton';
import { api } from '@/lib/api';
import type {
  DashboardStats,
  TicketListItem,
  WhitelistListItem,
} from '@/lib/types';

export default function DashboardPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: () => api<DashboardStats>('/admin/dashboard'),
    refetchInterval: 30_000,
  });

  const { data: inbox } = useQuery({
    queryKey: ['admin', 'me', 'inbox'],
    queryFn: () =>
      api<{ whitelist: WhitelistListItem[]; tickets: TicketListItem[] }>(
        '/admin/me/inbox',
      ),
    refetchInterval: 30_000,
  });

  return (
    <div className="px-10 py-10 max-w-6xl mx-auto">
      <header className="mb-10">
        <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
          Vue d&apos;ensemble
        </div>
        <h1
          className="mt-2 text-6xl leading-none text-[var(--color-foreground)]"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          Tableau de bord
        </h1>
        <p className="mt-3 max-w-xl text-sm text-[var(--color-foreground-subtle)]">
          L&apos;etat en temps reel de la whitelist, des tickets et de la
          communaute Reborn.
        </p>
      </header>

      {isLoading && !data ? (
        <DashboardSkeleton />
      ) : error ? (
        <div className="rounded-[14px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-5 py-4 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      ) : !data ? null : (
        <div className="space-y-12">
          {inbox && (inbox.whitelist.length > 0 || inbox.tickets.length > 0) && (
            <FadeUp delay={0}>
              <Link
                href="/inbox"
                className="group flex items-center justify-between gap-4 rounded-[14px] border border-[var(--color-accent)]/30 bg-[var(--color-accent-soft)]/40 px-6 py-5 transition-colors hover:border-[var(--color-accent)]/60"
              >
                <div className="flex items-center gap-4">
                  <div className="flex h-11 w-11 items-center justify-center rounded-[12px] bg-[var(--color-accent-soft)] text-[var(--color-accent)]">
                    <IconWhitelist />
                  </div>
                  <div>
                    <div className="text-base font-semibold text-[var(--color-foreground)]">
                      {inbox.whitelist.length + inbox.tickets.length} element
                      {inbox.whitelist.length + inbox.tickets.length > 1
                        ? 's'
                        : ''}{' '}
                      m&apos;attendent
                    </div>
                    <div className="mt-0.5 text-sm text-[var(--color-foreground-subtle)]">
                      {inbox.whitelist.length} candidature
                      {inbox.whitelist.length > 1 ? 's' : ''} ·{' '}
                      {inbox.tickets.length} ticket
                      {inbox.tickets.length > 1 ? 's' : ''} assigne
                      {inbox.tickets.length > 1 ? 's' : ''}
                    </div>
                  </div>
                </div>
                <span className="text-sm font-medium text-[var(--color-accent)] transition-transform group-hover:translate-x-0.5">
                  Mon espace →
                </span>
              </Link>
            </FadeUp>
          )}

          <FadeUp delay={0.06}>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <StatCard
                icon={<IconWhitelist />}
                label="Whitelist en attente"
                value={data.whitelist.pending}
                sublabel={`+${data.whitelist.needsRevision} a reviser`}
                tone="accent"
              />
              <StatCard
                icon={<IconTickets />}
                label="Tickets ouverts"
                value={data.tickets.open}
                sublabel={`${data.tickets.inProgress} en cours`}
                tone="warning"
              />
              <StatCard
                icon={<IconPlayers />}
                label="Joueurs whitelistes"
                value={data.users.whitelisted}
                sublabel={`/ ${data.users.total} comptes`}
                tone="success"
              />
              <StatCard
                icon={<IconClock />}
                label="Nouveaux (24h)"
                value={data.users.last24h}
                sublabel="inscriptions recentes"
                tone="accent"
              />
              <PlaceholderCard
                icon={<IconPlayers />}
                label="Joueurs en ligne"
              />
              <PlaceholderCard
                icon={<IconShield />}
                label="Revenus boutique"
              />
            </div>
          </FadeUp>

          <FadeUp delay={0.12}>
            <section>
              <div className="mb-5 flex items-baseline gap-3">
                <h2
                  className="text-3xl leading-none text-[var(--color-foreground)]"
                  style={{ fontFamily: 'var(--font-display)' }}
                >
                  Repartition
                </h2>
                <span className="text-xs text-[var(--color-foreground-muted)]">
                  Detail par statut
                </span>
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                <BreakdownPanel
                  title="Whitelist"
                  rows={[
                    {
                      label: 'A traiter',
                      value: data.whitelist.pending,
                      color: 'var(--color-accent)',
                    },
                    {
                      label: 'A reviser',
                      value: data.whitelist.needsRevision,
                      color: 'var(--color-warning)',
                    },
                    {
                      label: 'Approuvees',
                      value: data.whitelist.approved,
                      color: 'var(--color-success)',
                    },
                    {
                      label: 'Refusees',
                      value: data.whitelist.rejected,
                      color: 'var(--color-danger)',
                    },
                  ]}
                />
                <BreakdownPanel
                  title="Tickets"
                  rows={[
                    {
                      label: 'Ouverts',
                      value: data.tickets.open,
                      color: 'var(--color-accent)',
                    },
                    {
                      label: 'En cours',
                      value: data.tickets.inProgress,
                      color: 'var(--color-warning)',
                    },
                    {
                      label: 'Resolus',
                      value: data.tickets.resolved,
                      color: 'var(--color-success)',
                    },
                    {
                      label: 'Fermes',
                      value: data.tickets.closed,
                      color: 'var(--color-foreground-muted)',
                    },
                  ]}
                />
              </div>
            </section>
          </FadeUp>
        </div>
      )}
    </div>
  );
}

type Tone = 'accent' | 'warning' | 'success';

const TONE_MAP: Record<Tone, { bg: string; fg: string; bar: string }> = {
  accent: {
    bg: 'var(--color-accent-soft)',
    fg: 'var(--color-accent)',
    bar: 'var(--color-accent)',
  },
  warning: {
    bg: 'var(--color-warning-soft)',
    fg: 'var(--color-warning)',
    bar: 'var(--color-warning)',
  },
  success: {
    bg: 'var(--color-success-soft)',
    fg: 'var(--color-success)',
    bar: 'var(--color-success)',
  },
};

function StatCard({
  icon,
  label,
  value,
  sublabel,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  sublabel?: string;
  tone: Tone;
}) {
  const t = TONE_MAP[tone];
  return (
    <div className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-6 transition-colors hover:border-[var(--color-border-strong)]">
      <div
        className="flex h-11 w-11 items-center justify-center rounded-[12px]"
        style={{ backgroundColor: t.bg, color: t.fg }}
      >
        {icon}
      </div>
      <div
        className="mt-5 text-5xl font-bold leading-none text-[var(--color-foreground)]"
        style={{ fontFamily: 'var(--font-display)' }}
      >
        {value}
      </div>
      <div className="mt-3 text-sm font-medium text-[var(--color-foreground)]">
        {label}
      </div>
      {sublabel && (
        <div className="mt-1 text-xs text-[var(--color-foreground-muted)]">
          {sublabel}
        </div>
      )}
    </div>
  );
}

function PlaceholderCard({
  icon,
  label,
}: {
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <div className="rounded-[14px] border border-dashed border-[var(--color-border-strong)] bg-transparent p-6">
      <div className="flex items-start justify-between">
        <div className="flex h-11 w-11 items-center justify-center rounded-[12px] bg-[var(--color-surface-elevated)] text-[var(--color-foreground-muted)]">
          {icon}
        </div>
        <span className="rounded-full border border-[var(--color-border-strong)] px-2 py-0.5 text-[10px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
          a brancher
        </span>
      </div>
      <div
        className="mt-5 text-5xl font-bold leading-none text-[var(--color-foreground-muted)]"
        style={{ fontFamily: 'var(--font-display)' }}
      >
        —
      </div>
      <div className="mt-3 text-sm font-medium text-[var(--color-foreground-subtle)]">
        {label}
      </div>
      <div className="mt-1 text-xs text-[var(--color-foreground-muted)]">
        source non connectee
      </div>
    </div>
  );
}

function BreakdownPanel({
  title,
  rows,
}: {
  title: string;
  rows: { label: string; value: number; color: string }[];
}) {
  const total = rows.reduce((sum, r) => sum + r.value, 0);
  return (
    <div className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-6">
      <div className="mb-5 flex items-baseline justify-between">
        <h3 className="text-sm font-semibold uppercase tracking-wider text-[var(--color-foreground)]">
          {title}
        </h3>
        <span className="text-xs text-[var(--color-foreground-muted)]">
          {total} total
        </span>
      </div>
      <div className="space-y-4">
        {rows.map((r) => {
          const pct = total > 0 ? Math.round((r.value / total) * 100) : 0;
          return (
            <div key={r.label}>
              <div className="mb-1.5 flex items-baseline justify-between text-sm">
                <span className="text-[var(--color-foreground-subtle)]">
                  {r.label}
                </span>
                <span className="tabular-nums font-medium text-[var(--color-foreground)]">
                  {r.value}
                </span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-[var(--color-surface-elevated)]">
                <div
                  className="h-full rounded-full transition-all"
                  style={{
                    width: `${pct}%`,
                    backgroundColor: r.color,
                  }}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-12">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 6 }).map((_, i) => (
          <div
            key={i}
            className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-6"
          >
            <Skeleton className="h-11 w-11 rounded-[12px]" />
            <Skeleton className="mt-5 h-10 w-16" />
            <Skeleton className="mt-3 h-3 w-28" />
          </div>
        ))}
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        {Array.from({ length: 2 }).map((_, i) => (
          <div
            key={i}
            className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-6"
          >
            <Skeleton className="h-4 w-24" />
            <div className="mt-6 space-y-4">
              {Array.from({ length: 4 }).map((_, j) => (
                <div key={j}>
                  <Skeleton className="h-3 w-full" />
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
