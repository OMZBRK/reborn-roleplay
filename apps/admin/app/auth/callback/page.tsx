'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { setTokens } from '@/lib/auth';

export default function AuthCallbackPage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // L'API redirige avec `#access=...&refresh=...` (ou `#error=...`).
    // On lit hash plutot que searchParams pour eviter que les tokens
    // n'apparaissent dans les logs proxy / referer.
    const hash = window.location.hash.startsWith('#')
      ? window.location.hash.slice(1)
      : window.location.hash;
    const params = new URLSearchParams(hash);

    const err = params.get('error');
    if (err) {
      setError(err);
      return;
    }
    const challenge = params.get('challenge');
    if (challenge) {
      // 2FA actif cote API → on stocke le challenge en sessionStorage
      // (volatile, non-persistant) et on bascule sur la page de
      // verification TOTP.
      window.sessionStorage.setItem('reborn-admin.2fa.challenge', challenge);
      window.history.replaceState({}, '', '/auth/callback');
      router.replace('/auth/2fa');
      return;
    }
    const access = params.get('access');
    const refresh = params.get('refresh');
    if (!access || !refresh) {
      setError('Tokens manquants dans la reponse.');
      return;
    }
    setTokens(access, refresh);
    // Scrub the URL so a screenshot/back-button can't leak the tokens.
    window.history.replaceState({}, '', '/auth/callback');
    router.replace('/dashboard');
  }, [router]);

  return (
    <main className="reborn-auth-bg min-h-screen flex items-center justify-center px-6">
      <div className="w-full max-w-md rounded-[14px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] p-8 shadow-[var(--shadow-md)] text-center">
        {error ? (
          <>
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-[var(--color-danger-soft)] text-[var(--color-danger)] text-2xl">
              !
            </div>
            <h1 className="text-lg font-semibold mb-2">Connexion echouee</h1>
            <p className="text-sm text-[var(--color-foreground-subtle)] mb-6">
              {error}
            </p>
            <a
              href="/"
              className="inline-block rounded-[8px] border border-[var(--color-border-strong)] px-4 py-2 text-sm hover:bg-[var(--color-surface-elevated)] transition-colors"
            >
              Reessayer
            </a>
          </>
        ) : (
          <>
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-[var(--color-accent-soft)] text-[var(--color-accent)] text-xl">
              ↻
            </div>
            <h1 className="text-lg font-semibold mb-2">Connexion en cours…</h1>
            <p className="text-sm text-[var(--color-foreground-subtle)]">
              Redirection vers le dashboard.
            </p>
          </>
        )}
      </div>
    </main>
  );
}
