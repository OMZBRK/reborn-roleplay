'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  IconAudit,
  IconBook,
  IconBulb,
  IconDashboard,
  IconInbox,
  IconLogout,
  IconPlayers,
  IconServer,
  IconShield,
  IconTickets,
  IconWhitelist,
} from '@/components/icons';
import { StaffIdentity } from '@/components/StaffIdentity';
import { clearTokens, isAuthenticated } from '@/lib/auth';

interface NavItem {
  href: string;
  label: string;
  Icon: React.FC<{ className?: string }>;
}

const NAV: NavItem[] = [
  { href: '/dashboard', label: 'Dashboard', Icon: IconDashboard },
  { href: '/inbox', label: 'Mes prises', Icon: IconInbox },
  { href: '/whitelist', label: 'Whitelist', Icon: IconWhitelist },
  { href: '/oral-slots', label: 'Créneaux oraux', Icon: IconWhitelist },
  { href: '/tickets', label: 'Tickets', Icon: IconTickets },
  { href: '/players', label: 'Joueurs', Icon: IconPlayers },
  { href: '/files', label: 'Fichiers', Icon: IconServer },
  { href: '/wiki', label: 'Wiki', Icon: IconBook },
  { href: '/wiki/ideas', label: 'Idées', Icon: IconBulb },
  { href: '/audit', label: 'Audit', Icon: IconAudit },
  { href: '/anomalies', label: 'Anomalies', Icon: IconShield },
];

export default function PanelLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!isAuthenticated()) {
      router.replace('/');
      return;
    }
    setReady(true);
  }, [router]);

  if (!ready) {
    return null;
  }

  return (
    <div className="min-h-screen flex">
      <aside className="w-64 shrink-0 border-r border-[var(--color-border)] bg-[var(--color-surface)] flex flex-col">
        <div className="px-5 py-6">
          <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
            Reborn
          </div>
          <div
            className="mt-1 text-2xl leading-none"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            Panel Staff
          </div>
          <div className="mt-3 h-[2px] w-12 rounded-full bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
        </div>

        <nav className="flex-1 px-3 space-y-1">
          {(() => {
            // Route active = le lien dont le href est le PLUS SPÉCIFIQUE à matcher
            // le chemin courant (sinon /wiki/ideas surlignerait aussi /wiki).
            const activeHref = NAV.reduce<string | null>((best, item) => {
              const matches =
                pathname === item.href || pathname?.startsWith(`${item.href}/`);
              if (matches && item.href.length > (best?.length ?? -1)) {
                return item.href;
              }
              return best;
            }, null);
            return NAV.map(({ href, label, Icon }) => {
              const active = href === activeHref;
              return (
              <Link
                key={href}
                href={href}
                className={`group flex items-center gap-3 rounded-[10px] px-3 py-2.5 text-sm transition-colors ${
                  active
                    ? 'bg-[var(--color-accent-soft)] text-[var(--color-foreground)] shadow-[inset_0_0_0_1px_var(--color-accent)]'
                    : 'text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]'
                }`}
              >
                <Icon
                  className={`shrink-0 ${
                    active
                      ? 'text-[var(--color-accent)]'
                      : 'text-[var(--color-foreground-muted)] group-hover:text-[var(--color-foreground-subtle)]'
                  }`}
                />
                {label}
              </Link>
              );
            });
          })()}
        </nav>

        <StaffIdentity />

        <button
          type="button"
          onClick={() => {
            clearTokens();
            router.replace('/');
          }}
          className="mx-3 mb-4 flex items-center justify-center gap-2 rounded-[10px] border border-[var(--color-border-strong)] py-2 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-danger-soft)] hover:text-[var(--color-danger)] hover:border-[var(--color-danger)] transition-colors"
        >
          <IconLogout />
          Deconnexion
        </button>
      </aside>

      <main className="flex-1 min-w-0">{children}</main>
    </div>
  );
}
