'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { use } from 'react';
import {
  IconArrowLeft,
  IconBan,
  IconCheck,
  IconClock,
  IconShield,
  IconWhitelist,
} from '@/components/icons';
import { RoleBadge } from '@/components/RoleBadge';
import { api } from '@/lib/api';
import type {
  AppStatus,
  PlayerDetail,
  TicketStatus,
} from '@/lib/types';

export default function PlayerProfilePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'players', id],
    queryFn: () => api<PlayerDetail>(`/admin/players/${id}`),
  });

  if (isLoading) {
    return (
      <div className="p-10 text-[var(--color-foreground-subtle)]">
        Chargement…
      </div>
    );
  }
  if (error) {
    return (
      <div className="p-10">
        <Link
          href="/players"
          className="inline-flex items-center gap-1 text-sm text-[var(--color-accent)] hover:underline"
        >
          <IconArrowLeft /> Retour
        </Link>
        <div className="mt-4 rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      </div>
    );
  }
  if (!data) return null;

  const initials = data.minecraftUsername
    .replace(/[^a-z]/gi, '')
    .slice(0, 2)
    .toUpperCase();

  return (
    <div className="px-10 py-10 max-w-5xl mx-auto">
      <Link
        href="/players"
        className="inline-flex items-center gap-1 text-sm text-[var(--color-accent)] hover:underline"
      >
        <IconArrowLeft /> Tous les joueurs
      </Link>

      <header className="mt-4 mb-8 flex items-start gap-5">
        <div
          className={`flex h-20 w-20 shrink-0 items-center justify-center rounded-full border-2 text-2xl font-medium ${
            data.banned
              ? 'border-[var(--color-danger)]/50 bg-[var(--color-danger-soft)] text-[var(--color-danger)]'
              : 'border-[var(--color-accent)]/50 bg-[var(--color-accent-soft)] text-[var(--color-accent)]'
          }`}
          style={{ fontFamily: 'var(--font-display)' }}
        >
          {initials || '?'}
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
            Profil joueur
          </div>
          <div className="mt-1 flex items-center gap-3">
            <h1
              className="text-4xl leading-none"
              style={{ fontFamily: 'var(--font-display)' }}
            >
              {data.minecraftUsername}
            </h1>
            <RoleBadge role={data.role} />
            {data.banned && (
              <span className="inline-flex items-center gap-1 rounded-full border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-2.5 py-0.5 text-xs text-[var(--color-danger)]">
                <IconBan /> Banni
              </span>
            )}
          </div>
          {data.discordUsername && (
            <div className="mt-1 text-sm text-[var(--color-foreground-subtle)]">
              @{data.discordUsername}
            </div>
          )}
        </div>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <Card title="Candidature whitelist" Icon={IconWhitelist}>
            {data.whitelist ? (
              <Link
                href={`/whitelist/${data.whitelist.id}`}
                className="group block rounded-[10px] border border-[var(--color-border)] bg-[var(--color-surface-elevated)] p-4 hover:border-[var(--color-accent)]/40 transition-colors"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="text-sm font-medium">
                      {data.whitelist.firstName} {data.whitelist.lastName}
                    </div>
                    <div className="text-xs text-[var(--color-foreground-muted)]">
                      Village · {data.whitelist.village}
                    </div>
                    <div className="mt-1 text-[10px] text-[var(--color-foreground-muted)]">
                      Soumise le{' '}
                      {new Date(data.whitelist.submittedAt).toLocaleString(
                        'fr-FR',
                        { dateStyle: 'short', timeStyle: 'short' },
                      )}
                    </div>
                  </div>
                  <AppStatusBadge status={data.whitelist.status} />
                </div>
              </Link>
            ) : (
              <div className="rounded-[10px] border border-dashed border-[var(--color-border-strong)] py-6 text-center text-sm text-[var(--color-foreground-muted)]">
                Aucune candidature soumise.
              </div>
            )}
          </Card>

          <Card title={`Tickets (${data.tickets.length})`} Icon={IconClock}>
            {data.tickets.length === 0 ? (
              <div className="rounded-[10px] border border-dashed border-[var(--color-border-strong)] py-6 text-center text-sm text-[var(--color-foreground-muted)]">
                Aucun ticket.
              </div>
            ) : (
              <div className="space-y-2 -m-2">
                {data.tickets.map((t) => (
                  <Link
                    key={t.id}
                    href={`/tickets/${t.id}`}
                    className="block rounded-[10px] border border-[var(--color-border)] bg-[var(--color-surface-elevated)] p-3 hover:border-[var(--color-accent)]/40 transition-colors"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
                          {t.category}
                        </div>
                        <div className="text-sm truncate">{t.subject}</div>
                        {t.lastMessagePreview && (
                          <div className="mt-1 text-xs text-[var(--color-foreground-subtle)] truncate">
                            {t.lastMessagePreview}
                          </div>
                        )}
                      </div>
                      <TicketStatusBadge status={t.status} />
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </Card>
        </div>

        <aside className="space-y-6">
          <Card title="Identite" Icon={IconShield}>
            <Field label="Pseudo MC">{data.minecraftUsername}</Field>
            <Field label="UUID">
              <code className="text-[10px] font-mono text-[var(--color-foreground-subtle)] break-all">
                {data.minecraftUuid}
              </code>
            </Field>
            <Field label="Discord">
              {data.discordUsername ? `@${data.discordUsername}` : '—'}
            </Field>
            <Field label="Steam">{data.steamUsername ?? '—'}</Field>
            <Field label="Role">
              <RoleBadge role={data.role} />
            </Field>
          </Card>

          <Card title="Activite" Icon={IconClock}>
            <Field label="Cree le">
              {new Date(data.createdAt).toLocaleDateString('fr-FR', {
                dateStyle: 'medium',
              })}
            </Field>
            <Field label="Derniere connexion">
              {data.lastLoginAt
                ? new Date(data.lastLoginAt).toLocaleString('fr-FR', {
                    dateStyle: 'short',
                    timeStyle: 'short',
                  })
                : 'jamais'}
            </Field>
            {data.lastKnownIp && (
              <Field label="Derniere IP">
                <code className="text-xs font-mono">{data.lastKnownIp}</code>
              </Field>
            )}
            {data.lastKnownCountry && (
              <Field label="Pays">{data.lastKnownCountry}</Field>
            )}
          </Card>

          {data.banned && (
            <Card title="Sanction active" Icon={IconBan}>
              <Field label="Raison" block>
                {data.banReason ?? '*non specifiee*'}
              </Field>
              {data.bannedUntil && (
                <Field label="Jusqu'au">
                  {new Date(data.bannedUntil).toLocaleString('fr-FR', {
                    dateStyle: 'medium',
                    timeStyle: 'short',
                  })}
                </Field>
              )}
            </Card>
          )}
        </aside>
      </div>
    </div>
  );
}

function Card({
  title,
  Icon,
  children,
}: {
  title: string;
  Icon: React.FC<{ className?: string }>;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-5">
      <div className="mb-4 flex items-center gap-2 text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
        <Icon className="text-[var(--color-accent)]" />
        {title}
      </div>
      <div className="space-y-3">{children}</div>
    </div>
  );
}

function Field({
  label,
  block,
  children,
}: {
  label: string;
  block?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className={block ? '' : 'flex items-baseline gap-3'}>
      <div
        className={`text-xs text-[var(--color-foreground-muted)] ${
          block ? 'mb-1' : 'min-w-[110px]'
        }`}
      >
        {label}
      </div>
      <div
        className={`text-sm ${
          block ? 'whitespace-pre-wrap text-[var(--color-foreground)]' : ''
        }`}
      >
        {children}
      </div>
    </div>
  );
}

function AppStatusBadge({ status }: { status: AppStatus }) {
  const map: Record<AppStatus, { label: string; cls: string; Icon: React.FC<{className?: string}> }> = {
    PENDING: {
      label: 'En attente',
      cls: 'bg-[var(--color-accent-soft)] text-[var(--color-accent)] border-[var(--color-accent)]/40',
      Icon: IconClock,
    },
    NEEDS_REVISION: {
      label: 'Revision',
      cls: 'bg-[var(--color-warning-soft)] text-[var(--color-warning)] border-[var(--color-warning)]/40',
      Icon: IconClock,
    },
    APPROVED: {
      label: 'Approuvee',
      cls: 'bg-[var(--color-success-soft)] text-[var(--color-success)] border-[var(--color-success)]/40',
      Icon: IconCheck,
    },
    REJECTED: {
      label: 'Refusee',
      cls: 'bg-[var(--color-danger-soft)] text-[var(--color-danger)] border-[var(--color-danger)]/40',
      Icon: IconBan,
    },
  };
  const t = map[status];
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs ${t.cls}`}
    >
      <t.Icon /> {t.label}
    </span>
  );
}

function TicketStatusBadge({ status }: { status: TicketStatus }) {
  const map: Record<TicketStatus, { label: string; cls: string }> = {
    OPEN: {
      label: 'Ouvert',
      cls: 'bg-[var(--color-accent-soft)] text-[var(--color-accent)] border-[var(--color-accent)]/40',
    },
    IN_PROGRESS: {
      label: 'En cours',
      cls: 'bg-[var(--color-warning-soft)] text-[var(--color-warning)] border-[var(--color-warning)]/40',
    },
    RESOLVED: {
      label: 'Resolu',
      cls: 'bg-[var(--color-success-soft)] text-[var(--color-success)] border-[var(--color-success)]/40',
    },
    CLOSED: {
      label: 'Ferme',
      cls: 'border-[var(--color-border-strong)] text-[var(--color-foreground-muted)]',
    },
  };
  const t = map[status];
  return (
    <span
      className={`inline-block shrink-0 rounded-full border px-2.5 py-0.5 text-xs ${t.cls}`}
    >
      {t.label}
    </span>
  );
}
