'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { StaggerItem } from '@/components/anim';
import {
  IconInbox,
  IconTickets,
  IconWhitelist,
} from '@/components/icons';
import { SkeletonRows } from '@/components/Skeleton';
import { api } from '@/lib/api';
import type { TicketListItem, WhitelistListItem } from '@/lib/types';

interface InboxResponse {
  whitelist: WhitelistListItem[];
  tickets: TicketListItem[];
}

export default function InboxPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'me', 'inbox'],
    queryFn: () => api<InboxResponse>('/admin/me/inbox'),
    refetchInterval: 30_000,
  });

  const total = (data?.whitelist.length ?? 0) + (data?.tickets.length ?? 0);

  return (
    <div className="px-10 py-10 max-w-5xl mx-auto">
      <header className="mb-8 flex items-end justify-between">
        <div>
          <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
            Mon espace
          </div>
          <h1
            className="mt-1 text-5xl leading-none bg-gradient-to-r from-white to-white/40 bg-clip-text text-transparent"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            Mes prises en charge
          </h1>
          <div className="mt-3 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
        </div>
        {data && (
          <div className="text-sm text-[var(--color-foreground-subtle)]">
            {total === 0 ? 'rien en cours' : `${total} en cours`}
          </div>
        )}
      </header>

      {isLoading && !data ? (
        <SkeletonRows count={4} />
      ) : error ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      ) : total === 0 ? (
        <EmptyState />
      ) : (
        <div className="space-y-8">
          {data!.whitelist.length > 0 && (
            <Section
              title="Whitelist"
              count={data!.whitelist.length}
              Icon={IconWhitelist}
            >
              <div className="space-y-3">
                {data!.whitelist.map((item, i) => (
                  <StaggerItem key={item.id} index={i}>
                    <Link
                      href={`/whitelist/${item.id}`}
                      className="block rounded-[12px] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 hover:bg-[var(--color-surface-elevated)] hover:border-[var(--color-accent)]/40 transition-colors"
                    >
                      <div className="flex items-start justify-between gap-4">
                        <div className="min-w-0">
                          <div className="text-sm font-medium">
                            {item.firstName} {item.lastName}
                          </div>
                          <div className="text-xs text-[var(--color-foreground-muted)]">
                            par {item.user.minecraftUsername} ·{' '}
                            {item.village}
                          </div>
                        </div>
                        <div className="text-right text-xs text-[var(--color-foreground-muted)]">
                          <div>{item.status}</div>
                          {item.assignedAt && (
                            <div>
                              pris {formatRelative(item.assignedAt)}
                            </div>
                          )}
                        </div>
                      </div>
                    </Link>
                  </StaggerItem>
                ))}
              </div>
            </Section>
          )}
          {data!.tickets.length > 0 && (
            <Section
              title="Tickets"
              count={data!.tickets.length}
              Icon={IconTickets}
            >
              <div className="space-y-3">
                {data!.tickets.map((item, i) => (
                  <StaggerItem key={item.id} index={i}>
                    <Link
                      href={`/tickets/${item.id}`}
                      className="block rounded-[12px] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 hover:bg-[var(--color-surface-elevated)] hover:border-[var(--color-accent)]/40 transition-colors"
                    >
                      <div className="flex items-start justify-between gap-4">
                        <div className="min-w-0">
                          <div className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
                            {item.category}
                          </div>
                          <div className="text-sm font-medium truncate">
                            {item.subject}
                          </div>
                          <div className="text-xs text-[var(--color-foreground-muted)]">
                            par {item.user.minecraftUsername}
                          </div>
                        </div>
                        <div className="text-right text-xs text-[var(--color-foreground-muted)]">
                          <div>{item.status}</div>
                          {item.assignedAt && (
                            <div>
                              pris {formatRelative(item.assignedAt)}
                            </div>
                          )}
                        </div>
                      </div>
                    </Link>
                  </StaggerItem>
                ))}
              </div>
            </Section>
          )}
        </div>
      )}
    </div>
  );
}

function Section({
  title,
  count,
  Icon,
  children,
}: {
  title: string;
  count: number;
  Icon: React.FC<{ className?: string }>;
  children: React.ReactNode;
}) {
  return (
    <section>
      <div className="mb-3 flex items-center gap-2 text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
        <Icon className="text-[var(--color-accent)]" />
        <span>{title}</span>
        <span className="rounded-full bg-[var(--color-accent-soft)] px-2 py-0.5 text-[10px] text-[var(--color-accent)]">
          {count}
        </span>
      </div>
      {children}
    </section>
  );
}

function EmptyState() {
  return (
    <div className="rounded-[14px] border border-dashed border-[var(--color-border-strong)] py-16 text-center">
      <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[var(--color-accent-soft)] text-[var(--color-accent)]">
        <IconInbox />
      </div>
      <div className="text-sm text-[var(--color-foreground)]">
        Rien sur ton bureau.
      </div>
      <div className="mt-1 text-xs text-[var(--color-foreground-muted)]">
        Va dans Whitelist ou Tickets pour prendre un cas en charge.
      </div>
    </div>
  );
}

function formatRelative(iso: string): string {
  const elapsed = Date.now() - new Date(iso).getTime();
  const minutes = Math.max(1, Math.floor(elapsed / 60_000));
  const hours = Math.floor(minutes / 60);
  if (hours < 1) return `il y a ${minutes}min`;
  const days = Math.floor(hours / 24);
  if (days < 1) return `il y a ${hours}h`;
  return `il y a ${days}j`;
}
