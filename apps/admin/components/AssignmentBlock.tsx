'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import type { Assignee, CurrentUser, Role } from '@/lib/types';

const ROLE_RANKS: Role[] = [
  'PLAYER',
  'WHITELISTED',
  'HELPER',
  'WHITELIST_REVIEWER',
  'MODERATOR',
  'ADMIN',
  'OWNER',
];

function roleAtLeast(role: Role | undefined, min: Role): boolean {
  if (!role) return false;
  return ROLE_RANKS.indexOf(role) >= ROLE_RANKS.indexOf(min);
}

/**
 * Bloc UI dans les pages detail whitelist + ticket qui montre l'etat
 * d'assignation et propose les actions claim/release/force selon le
 * contexte :
 *   - personne assigne → bouton "Prendre en charge"
 *   - assigne a moi    → bouton "Liberer"
 *   - assigne a autre  → label "Pris par @X" ; bouton "Forcer reprise"
 *                        si je suis ADMIN+
 */
export function AssignmentBlock({
  kind,
  id,
  assignee,
  assignedAt,
  /** Si fourni, on affiche aussi la duree depuis le claim ("depuis Xh"). */
  showDuration = true,
}: {
  kind: 'whitelist' | 'tickets';
  id: string;
  assignee: Assignee | null;
  assignedAt: string | null;
  showDuration?: boolean;
}) {
  const qc = useQueryClient();
  const { data: me } = useQuery({
    queryKey: ['admin', 'me'],
    queryFn: () => api<CurrentUser>('/auth/me'),
    staleTime: 5 * 60_000,
  });

  const invalidateAll = () => {
    qc.invalidateQueries({ queryKey: ['admin', kind] });
    qc.invalidateQueries({ queryKey: ['admin', kind, id] });
    qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
  };

  const claimMut = useMutation<unknown, Error, boolean>({
    mutationFn: (force: boolean) =>
      api(`/admin/${kind}/${id}/assign`, {
        method: 'POST',
        body: { force },
      }),
    onSuccess: (_data, force) => {
      invalidateAll();
      toast.success(force ? 'Reprise forcée' : 'Pris en charge');
    },
    onError: (err) => toast.error((err as Error).message),
  });

  const releaseMut = useMutation({
    mutationFn: () =>
      api(`/admin/${kind}/${id}/assign`, { method: 'DELETE' }),
    onSuccess: () => {
      invalidateAll();
      toast.success('Libéré');
    },
    onError: (err) => toast.error((err as Error).message),
  });

  const isMe = !!me && assignee?.id === me.id;
  const isOther = !!assignee && !isMe;
  const isAdmin = roleAtLeast(me?.role, 'ADMIN');

  return (
    <div className="rounded-[10px] border border-[var(--color-border)] bg-[var(--color-surface-elevated)] p-3">
      <div className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)] mb-2">
        Prise en charge
      </div>
      {assignee ? (
        <>
          <div className="text-sm mb-3">
            <span
              className={
                isMe ? 'text-[var(--color-accent)] font-medium' : ''
              }
            >
              @{assignee.username}
            </span>
            {isMe && <span className="text-[var(--color-foreground-muted)]"> (toi)</span>}
            {showDuration && assignedAt && (
              <div className="text-xs text-[var(--color-foreground-muted)] mt-0.5">
                depuis {formatSince(assignedAt)}
              </div>
            )}
          </div>
          {isMe ? (
            <button
              type="button"
              disabled={releaseMut.isPending}
              onClick={() => releaseMut.mutate()}
              className="w-full rounded-[8px] border border-[var(--color-border-strong)] py-2 text-sm hover:bg-[var(--color-surface)] disabled:opacity-40"
            >
              {releaseMut.isPending ? 'Libération…' : 'Libérer'}
            </button>
          ) : isOther && isAdmin ? (
            <button
              type="button"
              disabled={claimMut.isPending}
              onClick={() => claimMut.mutate(true)}
              className="w-full rounded-[8px] border border-[var(--color-warning)]/40 bg-[var(--color-warning-soft)] py-2 text-sm text-[var(--color-warning)] hover:bg-[var(--color-warning-soft)]/80 disabled:opacity-40"
            >
              {claimMut.isPending ? 'Reprise…' : 'Forcer reprise (ADMIN)'}
            </button>
          ) : null}
        </>
      ) : (
        <button
          type="button"
          disabled={claimMut.isPending}
          onClick={() => claimMut.mutate(false)}
          className="w-full rounded-[8px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] py-2 text-sm text-white disabled:opacity-40"
        >
          {claimMut.isPending ? 'Prise en charge…' : 'Prendre en charge'}
        </button>
      )}
    </div>
  );
}

function formatSince(iso: string): string {
  const elapsed = Date.now() - new Date(iso).getTime();
  const minutes = Math.max(1, Math.floor(elapsed / 60_000));
  const hours = Math.floor(minutes / 60);
  if (hours < 1) return `${minutes} min`;
  return `${hours}h${minutes % 60 > 0 ? ` ${minutes % 60}min` : ''}`;
}

/**
 * Variante badge compact pour les listes (whitelist/tickets/page.tsx).
 * Pas de bouton — juste l'indication visuelle.
 */
export function AssigneeBadge({ assignee }: { assignee: Assignee | null }) {
  if (!assignee) {
    return (
      <span className="text-[10px] text-[var(--color-foreground-muted)]">
        non assignée
      </span>
    );
  }
  return (
    <span className="inline-block rounded-full border border-[var(--color-accent)]/40 bg-[var(--color-accent-soft)] px-2 py-0.5 text-[10px] text-[var(--color-accent)]">
      @{assignee.username}
    </span>
  );
}
