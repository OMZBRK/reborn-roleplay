'use client';

import { useQuery } from '@tanstack/react-query';
import { FadeUp } from '@/components/anim';
import { Skeleton } from '@/components/Skeleton';
import { api } from '@/lib/api';
import type { DashboardStats } from '@/lib/types';

export default function DashboardPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: () => api<DashboardStats>('/admin/dashboard'),
    refetchInterval: 30_000,
  });

  return (
    <div className="px-10 py-10 max-w-6xl mx-auto">
      <header className="mb-10">
        <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
          Vue d'ensemble
        </div>
        <h1
          className="mt-1 text-5xl leading-none bg-gradient-to-r from-white to-white/40 bg-clip-text text-transparent"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          Dashboard
        </h1>
        <div className="mt-3 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
      </header>

      {isLoading && !data ? (
        <DashboardSkeleton />
      ) : error ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      ) : !data ? null : (
        <div className="space-y-10">
          <FadeUp delay={0}>
            <Section title="Whitelist" hint="Candidatures en cours et historique">
              <StatGrid>
                <StatCard
                  label="A traiter"
                  value={data.whitelist.pending}
                  tone="accent"
                />
                <StatCard
                  label="A reviser"
                  value={data.whitelist.needsRevision}
                  tone="warning"
                />
                <StatCard label="Approuvees" value={data.whitelist.approved} />
                <StatCard label="Refusees" value={data.whitelist.rejected} />
              </StatGrid>
            </Section>
          </FadeUp>

          <FadeUp delay={0.06}>
            <Section title="Tickets" hint="Files par statut">
              <StatGrid>
                <StatCard label="Ouverts" value={data.tickets.open} tone="accent" />
                <StatCard
                  label="En cours"
                  value={data.tickets.inProgress}
                  tone="warning"
                />
                <StatCard label="Resolus" value={data.tickets.resolved} />
                <StatCard label="Fermes" value={data.tickets.closed} />
              </StatGrid>
            </Section>
          </FadeUp>

          <FadeUp delay={0.12}>
            <Section title="Communaute" hint="Population et acquisition">
              <StatGrid cols={3}>
                <StatCard label="Total joueurs" value={data.users.total} />
                <StatCard label="Whitelistes+" value={data.users.whitelisted} />
                <StatCard
                  label="Nouveaux 24h"
                  value={data.users.last24h}
                  tone="accent"
                />
              </StatGrid>
            </Section>
          </FadeUp>
        </div>
      )}
    </div>
  );
}

function Section({
  title,
  hint,
  children,
}: {
  title: string;
  hint: string;
  children: React.ReactNode;
}) {
  return (
    <section>
      <div className="mb-4 flex items-baseline gap-3">
        <h2
          className="text-2xl"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          {title}
        </h2>
        <span className="text-xs text-[var(--color-foreground-muted)]">
          {hint}
        </span>
      </div>
      {children}
    </section>
  );
}

function StatGrid({
  cols = 4,
  children,
}: {
  cols?: 3 | 4;
  children: React.ReactNode;
}) {
  return (
    <div
      className={`grid gap-4 ${
        cols === 3 ? 'grid-cols-1 sm:grid-cols-3' : 'grid-cols-2 sm:grid-cols-4'
      }`}
    >
      {children}
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-10">
      {Array.from({ length: 3 }).map((_, s) => (
        <div key={s}>
          <Skeleton className="h-7 w-40" />
          <div className="mt-4 grid grid-cols-2 sm:grid-cols-4 gap-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <div
                key={i}
                className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-5"
              >
                <Skeleton className="h-3 w-16" />
                <Skeleton className="mt-3 h-9 w-12" />
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

function StatCard({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone?: 'accent' | 'warning';
}) {
  const accentClass =
    tone === 'accent'
      ? 'before:bg-[var(--color-accent)]'
      : tone === 'warning'
        ? 'before:bg-[var(--color-warning)]'
        : 'before:bg-[var(--color-border-strong)]';
  return (
    <div
      className={`relative overflow-hidden rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 before:absolute before:inset-x-0 before:top-0 before:h-[2px] before:content-[''] ${accentClass}`}
    >
      <div className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
        {label}
      </div>
      <div
        className="mt-3 text-4xl leading-none"
        style={{ fontFamily: 'var(--font-display)' }}
      >
        {value}
      </div>
    </div>
  );
}
