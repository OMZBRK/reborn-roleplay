import type { Role } from '@/lib/types';

const TONE: Record<Role, { label: string; cls: string }> = {
  PLAYER: {
    label: 'Joueur',
    cls: 'border-[var(--color-border-strong)] text-[var(--color-foreground-muted)]',
  },
  WHITELISTED: {
    label: 'Whiteliste',
    cls: 'border-[#8b5cf6]/40 text-[#a78bfa] bg-[#8b5cf6]/10',
  },
  HELPER: {
    label: 'Helper',
    cls: 'border-[var(--color-success)]/40 text-[var(--color-success)] bg-[var(--color-success-soft)]',
  },
  WHITELIST_REVIEWER: {
    label: 'WL Reviewer',
    cls: 'border-[var(--color-accent)]/40 text-[var(--color-accent)] bg-[var(--color-accent-soft)]',
  },
  MODERATOR: {
    label: 'Moderateur',
    cls: 'border-[var(--color-accent)]/40 text-[var(--color-accent)] bg-[var(--color-accent-soft)]',
  },
  ADMIN: {
    label: 'Admin',
    cls: 'border-[var(--color-danger)]/40 text-[var(--color-danger)] bg-[var(--color-danger-soft)]',
  },
  OWNER: {
    label: 'Owner',
    cls: 'border-[var(--color-warning)]/40 text-[var(--color-warning)] bg-[var(--color-warning-soft)]',
  },
};

export function RoleBadge({ role }: { role: Role }) {
  const t = TONE[role];
  return (
    <span
      className={`inline-block rounded-full border px-2.5 py-0.5 text-xs ${t.cls}`}
    >
      {t.label}
    </span>
  );
}
