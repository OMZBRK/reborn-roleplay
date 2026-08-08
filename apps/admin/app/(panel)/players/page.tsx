'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { IconBan, IconClock, IconSearch } from '@/components/icons';
import { RoleBadge } from '@/components/RoleBadge';
import { api } from '@/lib/api';
import type { PlayerListItem } from '@/lib/types';

/** Nombre de lignes renvoyees par l'endpoint /admin/players/search (take). */
const PAGE_SIZE = 30;

export default function PlayersPage() {
  const [draft, setDraft] = useState('');
  const [q, setQ] = useState('');

  // Debounce 250ms — evite de spam l'API a chaque keystroke.
  useEffect(() => {
    const t = window.setTimeout(() => setQ(draft.trim()), 250);
    return () => window.clearTimeout(t);
  }, [draft]);

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['admin', 'players', 'search', q],
    queryFn: () =>
      api<PlayerListItem[]>(
        `/admin/players/search${q ? `?q=${encodeURIComponent(q)}` : ''}`,
      ),
    placeholderData: (prev) => prev,
  });

  const players = data ?? [];
  const capped = players.length >= PAGE_SIZE;

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
          Joueurs
        </h1>
        <p className="mt-3 max-w-xl text-sm text-[var(--color-foreground-muted)]">
          Recherche et consulte les comptes de la communaute : role, derniere
          connexion et acces a la fiche detaillee.
        </p>
        <div className="mt-4 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
      </header>

      <div className="mb-5 relative">
        <IconSearch className="absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--color-foreground-muted)]" />
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Pseudo MC, Discord, UUID…"
          className="w-full rounded-[12px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] py-3 pl-11 pr-24 text-sm focus:border-[var(--color-accent)] focus:outline-none transition-colors"
        />
        <div className="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-3">
          {isFetching && data ? (
            <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-[var(--color-border-strong)] border-t-[var(--color-accent)]" />
          ) : null}
          {draft.length > 0 && (
            <button
              type="button"
              onClick={() => setDraft('')}
              className="text-xs text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground)]"
            >
              Effacer
            </button>
          )}
        </div>
      </div>

      {isLoading && !data ? (
        <TableSkeleton />
      ) : error ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      ) : players.length === 0 ? (
        <div className="rounded-[14px] border border-dashed border-[var(--color-border-strong)] py-20 text-center text-[var(--color-foreground-muted)]">
          {q
            ? `Aucun joueur ne correspond a "${q}".`
            : 'Aucun joueur — soumets une candidature pour commencer.'}
        </div>
      ) : (
        <>
          <div className="mb-3 flex items-center justify-between text-xs text-[var(--color-foreground-muted)]">
            <span>
              {players.length} joueur{players.length > 1 ? 's' : ''}
              {q ? ` pour « ${q} »` : ' — connexions recentes'}
            </span>
          </div>

          <div className="overflow-hidden rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)]">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] border-collapse text-sm">
                <thead>
                  <tr className="bg-[var(--color-surface-elevated)] text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
                    <th className="px-4 py-3 text-left font-medium">Joueur</th>
                    <th className="px-4 py-3 text-left font-medium">Role</th>
                    <th className="px-4 py-3 text-left font-medium">
                      Derniere connexion
                    </th>
                    <th className="px-4 py-3 text-left font-medium">
                      Temps de jeu
                    </th>
                    <th className="px-4 py-3 text-right font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {players.map((p) => (
                    <PlayerRow key={p.id} player={p} />
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {capped && (
            <p className="mt-3 text-center text-xs text-[var(--color-foreground-muted)]">
              Affichage limite aux {PAGE_SIZE} premiers resultats — affine ta
              recherche pour cibler un joueur precis.
            </p>
          )}
        </>
      )}
    </div>
  );
}

function PlayerRow({ player }: { player: PlayerListItem }) {
  return (
    <tr className="border-t border-[var(--color-border)] transition-colors hover:bg-[var(--color-surface-elevated)]">
      {/* Joueur */}
      <td className="px-4 py-3">
        <div className="flex items-center gap-3">
          <div className="relative shrink-0">
            <InitialAvatar
              name={player.minecraftUsername}
              banned={player.banned}
            />
            {player.banned && (
              <div className="absolute -bottom-1 -right-1 flex h-[18px] w-[18px] items-center justify-center rounded-full border-2 border-[var(--color-surface)] bg-[var(--color-danger)] text-white">
                <IconBan className="h-2.5 w-2.5" />
              </div>
            )}
          </div>
          <div className="min-w-0">
            <div className="truncate font-medium text-[var(--color-foreground)]">
              {player.minecraftUsername}
            </div>
            {player.discordUsername && (
              <div className="truncate text-xs text-[var(--color-foreground-muted)]">
                @{player.discordUsername}
              </div>
            )}
          </div>
        </div>
      </td>

      {/* Role */}
      <td className="px-4 py-3">
        <RoleBadge role={player.role} />
      </td>

      {/* Derniere connexion */}
      <td className="px-4 py-3">
        {player.lastLoginAt ? (
          <span className="text-[var(--color-foreground-subtle)]">
            {formatLastSeen(player.lastLoginAt)}
          </span>
        ) : (
          <span className="text-[var(--color-foreground-muted)]">—</span>
        )}
      </td>

      {/* Temps de jeu — pas de source de donnees encore */}
      <td className="px-4 py-3">
        <span
          className="inline-flex items-center gap-1.5 text-[var(--color-foreground-muted)]"
          title="Le suivi du temps de jeu n'est pas encore branche."
        >
          <IconClock className="h-3.5 w-3.5 opacity-60" />
          <span>—</span>
          <span className="text-[10px] uppercase tracking-wider opacity-60">
            a brancher
          </span>
        </span>
      </td>

      {/* Actions */}
      <td className="px-4 py-3 text-right">
        <Link
          href={`/players/${player.id}`}
          className="inline-flex items-center rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface-elevated)] px-3 py-1.5 text-xs font-medium text-[var(--color-foreground)] transition-colors hover:border-[var(--color-accent)] hover:text-[var(--color-accent)]"
        >
          Voir
        </Link>
      </td>
    </tr>
  );
}

/** Avatar circulaire a initiale — teinte accent, ou danger si banni. */
function InitialAvatar({ name, banned }: { name: string; banned: boolean }) {
  const letter = (name?.trim()?.[0] ?? '?').toUpperCase();
  return (
    <div
      className={`flex h-10 w-10 items-center justify-center rounded-full text-sm font-semibold ${
        banned
          ? 'bg-[var(--color-danger-soft)] text-[var(--color-danger)] ring-1 ring-[var(--color-danger)]/40'
          : 'bg-[var(--color-accent-soft)] text-[var(--color-accent)] ring-1 ring-[var(--color-accent)]/30'
      }`}
    >
      {letter}
    </div>
  );
}

function TableSkeleton() {
  return (
    <div className="overflow-hidden rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)]">
      <div className="bg-[var(--color-surface-elevated)] px-4 py-3">
        <div className="h-3 w-24 rounded bg-[var(--color-border)]" />
      </div>
      {Array.from({ length: 8 }).map((_, i) => (
        <div
          key={i}
          className="flex items-center gap-3 border-t border-[var(--color-border)] px-4 py-3"
        >
          <div className="h-10 w-10 shrink-0 animate-pulse rounded-full bg-[var(--color-surface-elevated)]" />
          <div className="flex-1">
            <div className="h-4 w-40 animate-pulse rounded bg-[var(--color-surface-elevated)]" />
            <div className="mt-2 h-3 w-24 animate-pulse rounded bg-[var(--color-surface-elevated)]" />
          </div>
          <div className="h-5 w-20 animate-pulse rounded-full bg-[var(--color-surface-elevated)]" />
          <div className="h-4 w-24 animate-pulse rounded bg-[var(--color-surface-elevated)]" />
          <div className="h-7 w-14 animate-pulse rounded-[8px] bg-[var(--color-surface-elevated)]" />
        </div>
      ))}
    </div>
  );
}

/** Temps relatif FR compact depuis un ISO. "il y a 5 min" / "il y a 3 j". */
function formatLastSeen(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60_000);
  if (m < 1) return "a l'instant";
  if (m < 60) return `il y a ${m} min`;
  const h = Math.floor(m / 60);
  if (h < 24) return `il y a ${h} h`;
  const d = Math.floor(h / 24);
  if (d < 30) return `il y a ${d} j`;
  const mo = Math.floor(d / 30);
  if (mo < 12) return `il y a ${mo} mois`;
  return new Date(iso).toLocaleDateString('fr-FR');
}
