'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { API_BASE } from '@/lib/api';
import { isAuthenticated } from '@/lib/auth';

export default function LoginPage() {
  const router = useRouter();

  useEffect(() => {
    if (isAuthenticated()) {
      router.replace('/dashboard');
    }
  }, [router]);

  return (
    <main className="reborn-auth-bg min-h-screen flex items-center justify-center px-6">
      <div className="w-full max-w-md rounded-[14px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] p-8 shadow-[var(--shadow-md)]">
        <div className="mb-8 text-center">
          <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
            Reborn Roleplay
          </div>
          <h1
            className="mt-2 text-[42px] leading-none font-display"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            Panel Staff
          </h1>
          <div className="mt-3 h-[2px] w-16 mx-auto rounded-full bg-gradient-to-r from-transparent via-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
        </div>

        <p className="mb-6 text-sm text-[var(--color-foreground-subtle)] text-center leading-relaxed">
          Connecte-toi avec ton compte Discord lie a un utilisateur Reborn
          de rang <strong className="text-[var(--color-foreground)]">HELPER</strong> ou plus.
        </p>

        <a
          href={`${API_BASE}/auth/discord/staff/start`}
          className="block w-full rounded-[10px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] active:bg-[var(--color-accent-pressed)] py-3 text-center font-medium text-white shadow-[var(--shadow-glow-accent)] transition-colors"
        >
          Se connecter avec Discord
        </a>

        <div className="mt-6 text-xs text-[var(--color-foreground-muted)] text-center leading-relaxed">
          Pas de compte ? Lance le launcher, connecte-toi via Microsoft,
          puis demande au lead staff de te promouvoir.
        </div>
      </div>
    </main>
  );
}
