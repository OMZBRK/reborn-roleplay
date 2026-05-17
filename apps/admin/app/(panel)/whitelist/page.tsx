'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { AssigneeBadge } from '@/components/AssignmentBlock';
import { IconSearch } from '@/components/icons';
import { FadeUp } from '@/components/anim';
import { SkeletonTable } from '@/components/Skeleton';
import { api } from '@/lib/api';
import type { AppStatus, Paginated, WhitelistListItem } from '@/lib/types';

const STATUS_TABS: Array<{ value: AppStatus | 'ALL'; label: string }> = [
  { value: 'PENDING', label: 'A traiter' },
  { value: 'NEEDS_REVISION', label: 'A reviser' },
  { value: 'APPROVED', label: 'Approuvees' },
  { value: 'REJECTED', label: 'Refusees' },
  { value: 'ALL', label: 'Toutes' },
];

export default function WhitelistListPage() {
  const [status, setStatus] = useState<AppStatus | 'ALL'>('PENDING');
  const [search, setSearch] = useState('');

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'whitelist', status],
    queryFn: () => {
      const q = status === 'ALL' ? '' : `?status=${status}`;
      return api<Paginated<WhitelistListItem>>(`/admin/whitelist${q}`);
    },
  });

  // Filtre client : la pagination serveur reste bornee a 50 items par
  // page, le search ne couvre que la page chargee. Pour scaler on
  // poussera le filtre cote API quand on ajoutera la pagination UI.
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
        <div className="mt-3 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
      </header>

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

      <div className="mb-6 relative">
        <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-foreground-muted)]" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Filtrer par pseudo, personnage, village…"
          className="w-full rounded-[12px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] py-2.5 pl-11 pr-4 text-sm focus:border-[var(--color-accent)] focus:outline-none transition-colors"
        />
      </div>

      {isLoading && !data ? (
        <SkeletonTable rows={6} />
      ) : error ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      ) : !filtered || filtered.length === 0 ? (
        <div className="rounded-[14px] border border-dashed border-[var(--color-border-strong)] py-16 text-center text-[var(--color-foreground-muted)]">
          {search.trim().length > 0
            ? `Aucune candidature ne matche "${search}" dans cette categorie.`
            : 'Aucune candidature dans cette categorie.'}
        </div>
      ) : (
        <FadeUp className="overflow-hidden rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)]">
          <table className="w-full text-sm">
            <thead className="bg-[var(--color-surface-elevated)] text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
              <tr>
                <th className="px-4 py-3 text-left">Joueur</th>
                <th className="px-4 py-3 text-left">Personnage</th>
                <th className="px-4 py-3 text-left">Village</th>
                <th className="px-4 py-3 text-left">Statut</th>
                <th className="px-4 py-3 text-left">Soumise le</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((item) => (
                <tr
                  key={item.id}
                  className="border-t border-[var(--color-border)] hover:bg-[var(--color-surface-elevated)] transition-colors"
                >
                  <td className="px-4 py-3">
                    <Link
                      href={`/whitelist/${item.id}`}
                      className="block text-[var(--color-foreground)] hover:text-[var(--color-accent)]"
                    >
                      {item.user.minecraftUsername}
                    </Link>
                    {item.user.discordUsername && (
                      <div className="text-xs text-[var(--color-foreground-muted)]">
                        @{item.user.discordUsername}
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-3 text-[var(--color-foreground-subtle)]">
                    <Link
                      href={`/whitelist/${item.id}`}
                      className="hover:text-[var(--color-foreground)]"
                    >
                      {item.firstName} {item.lastName}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-[var(--color-foreground-subtle)]">
                    {item.village}
                  </td>
                  <td className="px-4 py-3">
                    <div className="space-y-1">
                      <StatusBadge status={item.status} />
                      <div>
                        <AssigneeBadge assignee={item.assignee} />
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-[var(--color-foreground-muted)]">
                    {new Date(item.submittedAt).toLocaleString('fr-FR', {
                      dateStyle: 'short',
                      timeStyle: 'short',
                    })}
                    <Link
                      href={`/players/${item.user.id}`}
                      className="mt-1 block text-[10px] text-[var(--color-accent)] hover:underline"
                    >
                      Voir profil →
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {data && data.total > data.items.length && (
            <div className="border-t border-[var(--color-border)] px-4 py-3 text-xs text-[var(--color-foreground-muted)]">
              Affichage de {filtered.length} sur {data.total}
              {search.trim().length > 0 && data.items.length !== filtered.length && (
                <> · filtre actif sur la page courante</>
              )}.
            </div>
          )}
        </FadeUp>
      )}
    </div>
  );
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
