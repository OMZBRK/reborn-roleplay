'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { IconBan, IconSearch } from '@/components/icons';
import { MCAvatar } from '@/components/MCAvatar';
import { RoleBadge } from '@/components/RoleBadge';
import { api } from '@/lib/api';
import type { PlayerListItem } from '@/lib/types';

export default function PlayersPage() {
  const [draft, setDraft] = useState('');
  const [q, setQ] = useState('');

  // Debounce 250ms — evite de spam l'API a chaque keystroke.
  useEffect(() => {
    const t = window.setTimeout(() => setQ(draft.trim()), 250);
    return () => window.clearTimeout(t);
  }, [draft]);

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'players', 'search', q],
    queryFn: () =>
      api<PlayerListItem[]>(
        `/admin/players/search${q ? `?q=${encodeURIComponent(q)}` : ''}`,
      ),
    placeholderData: (prev) => prev,
  });

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
        <div className="mt-3 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
      </header>

      <div className="mb-6 relative">
        <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-foreground-muted)]" />
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Pseudo MC, Discord, UUID…"
          className="w-full rounded-[12px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] py-3 pl-11 pr-4 text-sm focus:border-[var(--color-accent)] focus:outline-none transition-colors"
        />
        {draft.length > 0 && (
          <button
            type="button"
            onClick={() => setDraft('')}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground)]"
          >
            Effacer
          </button>
        )}
      </div>

      {isLoading && !data ? (
        <SkeletonGrid />
      ) : error ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      ) : !data || data.length === 0 ? (
        <div className="rounded-[14px] border border-dashed border-[var(--color-border-strong)] py-16 text-center text-[var(--color-foreground-muted)]">
          {q
            ? `Aucun joueur ne correspond a "${q}".`
            : "Aucun joueur — soumets une candidature pour commencer."}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {data.map((p) => (
            <PlayerCard key={p.id} player={p} />
          ))}
        </div>
      )}
    </div>
  );
}

function PlayerCard({ player }: { player: PlayerListItem }) {
  return (
    <Link
      href={`/players/${player.id}`}
      className="group block rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 hover:bg-[var(--color-surface-elevated)] hover:border-[var(--color-accent)]/40 hover:-translate-y-0.5 transition-all"
    >
      <div className="flex items-start gap-3">
        <div className="relative shrink-0">
          <MCAvatar
            uuid={player.minecraftUuid}
            username={player.minecraftUsername}
            size={44}
            rounded="lg"
            className={`ring-2 ${
              player.banned
                ? 'ring-[var(--color-danger)]/40'
                : 'ring-[var(--color-accent)]/30'
            }`}
          />
          {player.banned && (
            <div className="absolute -bottom-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full bg-[var(--color-danger)] text-white">
              <IconBan className="h-3 w-3" />
            </div>
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium">
            {player.minecraftUsername}
          </div>
          {player.discordUsername && (
            <div className="truncate text-xs text-[var(--color-foreground-muted)]">
              @{player.discordUsername}
            </div>
          )}
          <div className="mt-2 flex items-center gap-2">
            <RoleBadge role={player.role} />
            {player.lastLoginAt && (
              <span className="text-[10px] text-[var(--color-foreground-muted)]">
                {formatLastSeen(player.lastLoginAt)}
              </span>
            )}
          </div>
        </div>
      </div>
    </Link>
  );
}

function SkeletonGrid() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
      {Array.from({ length: 6 }).map((_, i) => (
        <div
          key={i}
          className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 animate-pulse"
        >
          <div className="flex items-start gap-3">
            <div className="h-11 w-11 rounded-[14px] bg-[var(--color-surface-elevated)]" />
            <div className="flex-1">
              <div className="h-4 w-32 rounded bg-[var(--color-surface-elevated)]" />
              <div className="mt-2 h-3 w-20 rounded bg-[var(--color-surface-elevated)]" />
              <div className="mt-3 h-5 w-16 rounded-full bg-[var(--color-surface-elevated)]" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

function formatLastSeen(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60_000);
  if (m < 1) return "a l'instant";
  if (m < 60) return `il y a ${m} min`;
  const h = Math.floor(m / 60);
  if (h < 24) return `il y a ${h}h`;
  const d = Math.floor(h / 24);
  if (d < 30) return `il y a ${d}j`;
  return new Date(iso).toLocaleDateString('fr-FR');
}
