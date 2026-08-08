'use client';

import Link from 'next/link';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { AssigneeBadge } from '@/components/AssignmentBlock';
import { IconSearch, IconArrowLeft } from '@/components/icons';
import { StaggerItem } from '@/components/anim';
import { SkeletonRows } from '@/components/Skeleton';
import { api } from '@/lib/api';
import type { AppStatus, Paginated, WhitelistListItem } from '@/lib/types';

const STATUS_TABS: Array<{ value: AppStatus | 'ALL'; label: string }> = [
  { value: 'PENDING', label: 'A traiter' },
  { value: 'NEEDS_REVISION', label: 'A reviser' },
  { value: 'APPROVED', label: 'Approuvees' },
  { value: 'REJECTED', label: 'Refusees' },
  { value: 'ALL', label: 'Toutes' },
];

const PAGE_SIZE = 20;

export default function WhitelistListPage() {
  const [status, setStatus] = useState<AppStatus | 'ALL'>('PENDING');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  // Reset a la premiere page quand on change de filtre.
  useEffect(() => {
    setPage(0);
  }, [status]);

  const { data, isLoading, isFetching, error } = useQuery({
    queryKey: ['admin', 'whitelist', status, page],
    queryFn: () => {
      const params = new URLSearchParams();
      if (status !== 'ALL') params.set('status', status);
      params.set('take', String(PAGE_SIZE));
      params.set('skip', String(page * PAGE_SIZE));
      return api<Paginated<WhitelistListItem>>(
        `/admin/whitelist?${params.toString()}`,
      );
    },
    placeholderData: keepPreviousData,
  });

  // Filtre client : ne couvre que la page chargee (borne a PAGE_SIZE
  // items). Pour scaler on poussera le filtre cote API.
  const filtered = useMemo(() => {
    if (!data) return null;
    const term = search.trim().toLowerCase();
    if (term.length === 0) return data.items;
    return data.items.filter((item) => {
      const haystack = [
        item.user.minecraftUsername,
        item.user.discordUsername ?? '',
        item.firstName,
        item.lastName,
        item.village,
      ]
        .join(' ')
        .toLowerCase();
      return haystack.includes(term);
    });
  }, [data, search]);

  const total = data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const firstIndex = total === 0 ? 0 : page * PAGE_SIZE + 1;
  const lastIndex = Math.min(total, (page + 1) * PAGE_SIZE);

  return (
    <div className="px-10 py-10 max-w-6xl mx-auto">
      <header className="mb-8">
        <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
          Communaute
        </div>
        <h1
          className="mt-1 text-5xl leading-none bg-gradient-to-r from-white to-white/40 bg-clip-text text-transparent"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          Whitelist
        </h1>
        <p className="mt-3 text-sm text-[var(--color-foreground-subtle)]">
          Passe en revue les candidatures des joueurs et suis leur avancement.
        </p>
        <div className="mt-4 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
      </header>

      {/* Barre de controles : onglets de filtre + recherche */}
      <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-wrap gap-2">
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

        <div className="relative lg:w-80">
          <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-foreground-muted)]" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filtrer cette page…"
            className="w-full rounded-[10px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] py-2.5 pl-11 pr-4 text-sm focus:border-[var(--color-accent)] focus:outline-none transition-colors"
          />
        </div>
      </div>

      {isLoading && !data ? (
        <SkeletonRows count={6} />
      ) : error ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      ) : !filtered || filtered.length === 0 ? (
        <div className="rounded-[14px] border border-dashed border-[var(--color-border-strong)] py-20 text-center text-[var(--color-foreground-muted)]">
          {search.trim().length > 0
            ? `Aucune candidature ne matche "${search}" sur cette page.`
            : 'Aucune candidature dans cette categorie.'}
        </div>
      ) : (
        <div
          className={`space-y-3 transition-opacity ${
            isFetching ? 'opacity-60' : 'opacity-100'
          }`}
        >
          {filtered.map((item, i) => (
            <StaggerItem key={item.id} index={i}>
              <div className="group relative flex items-center gap-4 rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 transition-colors hover:border-[var(--color-accent)]/40 hover:bg-[var(--color-surface-elevated)]">
                {/* Lien plein-carte (stretched) vers le detail */}
                <Link
                  href={`/whitelist/${item.id}`}
                  aria-label={`Ouvrir la candidature de ${item.user.minecraftUsername}`}
                  className="absolute inset-0 z-0 rounded-[14px]"
                />

                {/* Avatar initiale */}
                <div className="relative z-10 flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-[var(--color-border-strong)] bg-[var(--color-surface-elevated)] text-sm font-medium uppercase text-[var(--color-foreground-subtle)]">
                  {item.user.minecraftUsername.slice(0, 2)}
                </div>

                {/* Identite joueur */}
                <div className="pointer-events-none relative z-10 min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="truncate text-sm font-medium text-[var(--color-foreground)] group-hover:text-[var(--color-accent)]">
                      {item.user.minecraftUsername}
                    </span>
                    {item.user.discordUsername && (
                      <span className="truncate text-xs text-[var(--color-foreground-muted)]">
                        @{item.user.discordUsername}
                      </span>
                    )}
                  </div>
                  <div className="mt-0.5 truncate text-xs text-[var(--color-foreground-subtle)]">
                    {item.firstName} {item.lastName}
                    <span className="text-[var(--color-foreground-muted)]">
                      {' '}
                      · {item.village}
                    </span>
                  </div>
                </div>

                {/* Statut + assignation */}
                <div className="relative z-10 hidden shrink-0 flex-col items-end gap-1.5 sm:flex">
                  <StatusBadge status={item.status} />
                  <AssigneeBadge assignee={item.assignee} />
                </div>

                {/* Date relative + lien profil */}
                <div className="relative z-10 hidden w-28 shrink-0 text-right md:block">
                  <div
                    className="text-xs text-[var(--color-foreground-muted)]"
                    title={new Date(item.submittedAt).toLocaleString('fr-FR', {
                      dateStyle: 'long',
                      timeStyle: 'short',
                    })}
                  >
                    {formatRelative(item.submittedAt)}
                  </div>
                  <Link
                    href={`/players/${item.user.id}`}
                    className="mt-1 inline-block text-[10px] text-[var(--color-accent)] hover:underline"
                  >
                    Voir profil →
                  </Link>
                </div>

                {/* Chevron ouvrir */}
                <IconArrowLeft className="relative z-10 shrink-0 rotate-180 text-[var(--color-foreground-muted)] transition-colors group-hover:text-[var(--color-accent)]" />
              </div>
            </StaggerItem>
          ))}
        </div>
      )}

      {/* Pagination */}
      {!error && total > 0 && (
        <div className="mt-6 flex items-center justify-between text-xs text-[var(--color-foreground-muted)]">
          <div>
            {search.trim().length > 0 ? (
              <>
                {filtered?.length ?? 0} resultat(s) filtre(s) sur cette page
              </>
            ) : (
              <>
                {firstIndex}–{lastIndex} sur {total}
              </>
            )}
          </div>
          {pageCount > 1 && search.trim().length === 0 && (
            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={page === 0 || isFetching}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="rounded-[8px] border border-[var(--color-border-strong)] px-3 py-1.5 text-sm text-[var(--color-foreground-subtle)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)] disabled:cursor-not-allowed disabled:opacity-40"
              >
                Precedent
              </button>
              <span className="px-1 tabular-nums">
                {page + 1} / {pageCount}
              </span>
              <button
                type="button"
                disabled={page + 1 >= pageCount || isFetching}
                onClick={() => setPage((p) => p + 1)}
                className="rounded-[8px] border border-[var(--color-border-strong)] px-3 py-1.5 text-sm text-[var(--color-foreground-subtle)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)] disabled:cursor-not-allowed disabled:opacity-40"
              >
                Suivant
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function formatRelative(iso: string): string {
  const elapsed = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(elapsed / 60_000);
  if (minutes < 1) return "a l'instant";
  if (minutes < 60) return `il y a ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `il y a ${hours} h`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `il y a ${days} j`;
  return new Date(iso).toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

function StatusBadge({ status }: { status: AppStatus }) {
  const map: Record<AppStatus, { label: string; cls: string }> = {
    PENDING: {
      label: 'A traiter',
      cls: 'bg-[var(--color-accent-soft)] text-[var(--color-accent)] border-[var(--color-accent)]/40',
    },
    NEEDS_REVISION: {
      label: 'Revision',
      cls: 'bg-[var(--color-warning-soft)] text-[var(--color-warning)] border-[var(--color-warning)]/40',
    },
    APPROVED: {
      label: 'Approuvee',
      cls: 'bg-[var(--color-success-soft)] text-[var(--color-success)] border-[var(--color-success)]/40',
    },
    REJECTED: {
      label: 'Refusee',
      cls: 'bg-[var(--color-danger-soft)] text-[var(--color-danger)] border-[var(--color-danger)]/40',
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
