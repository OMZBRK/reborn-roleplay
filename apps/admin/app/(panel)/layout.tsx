'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  IconDashboard,
  IconLogout,
  IconPlayers,
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
  { href: '/whitelist', label: 'Whitelist', Icon: IconWhitelist },
  { href: '/tickets', label: 'Tickets', Icon: IconTickets },
  { href: '/players', label: 'Joueurs', Icon: IconPlayers },
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
      <aside className="w-60 shrink-0 border-r border-[var(--color-border)] bg-[var(--color-surface)] flex flex-col">
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
          {NAV.map(({ href, label, Icon }) => {
            const active =
              pathname === href || pathname?.startsWith(`${href}/`);
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
          })}
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

      <main className="flex-1 overflow-x-hidden">{children}</main>
    </div>
  );
}
