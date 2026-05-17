'use client';

import { useQuery } from '@tanstack/react-query';
import { MCAvatar } from './MCAvatar';
import { RoleBadge } from './RoleBadge';
import { api } from '@/lib/api';
import type { CurrentUser } from '@/lib/types';

/**
 * Bloc identite affiche en bas de la sidebar — montre qui est connecte
 * pour donner du contexte au staff (utile quand plusieurs comptes ou
 * quand on a oublie sous quelle identite on a login).
 *
 * Fait UNE seule fetch a /v1/auth/me, cache aggressif (5 min) car les
 * infos changent rarement pendant une session. Si la fetch fail (401
 * typiquement = token expire), on rend null — le layout redirige deja.
 */
export function StaffIdentity() {
  const { data } = useQuery({
    queryKey: ['admin', 'me'],
    queryFn: () => api<CurrentUser>('/auth/me'),
    staleTime: 5 * 60_000,
  });

  if (!data) {
    return (
      <div className="mx-3 mb-3 flex items-center gap-3 rounded-[10px] border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-3 py-2.5">
        <div className="h-9 w-9 animate-pulse rounded-full bg-[var(--color-surface)]" />
        <div className="flex-1">
          <div className="h-3 w-20 animate-pulse rounded bg-[var(--color-surface)]" />
          <div className="mt-1.5 h-3 w-14 animate-pulse rounded bg-[var(--color-surface)]" />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-3 mb-3 flex items-center gap-3 rounded-[10px] border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-3 py-2.5">
      <MCAvatar
        uuid={data.minecraftUuid}
        username={data.minecraftUsername}
        size={36}
      />
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-medium text-[var(--color-foreground)]">
          {data.displayName ?? data.minecraftUsername}
        </div>
        <div className="mt-0.5">
          <RoleBadge role={data.role} />
        </div>
      </div>
    </div>
  );
}
