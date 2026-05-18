'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { setTokens } from '@/lib/auth';

interface VerifyResponse {
  accessToken: string;
  refreshToken: string;
}

/**
 * Page intermediaire affichee apres le Discord OAuth callback quand
 * le compte staff a le 2FA actif. Lit le `challenge` depuis
 * sessionStorage (mis par /auth/callback), demande le code TOTP,
 * POSTe vers /auth/2fa/verify, recoit les tokens reels.
 */
export default function TwoFactorChallengePage() {
  const router = useRouter();
  const [challenge, setChallenge] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const c = window.sessionStorage.getItem('reborn-admin.2fa.challenge');
    if (!c) {
      // Pas de challenge en attente — retour login.
      router.replace('/');
      return;
    }
    setChallenge(c);
  }, [router]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!challenge || code.trim().length < 6) return;
    setSubmitting(true);
    setError(null);
    try {
      const tokens = await api<VerifyResponse>('/auth/2fa/verify', {
        method: 'POST',
        body: { challenge, code: code.trim() },
        authenticated: false,
      });
      setTokens(tokens.accessToken, tokens.refreshToken);
      window.sessionStorage.removeItem('reborn-admin.2fa.challenge');
      router.replace('/dashboard');
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSubmitting(false);
    }
  }

  if (!challenge) return null;

  return (
    <main className="reborn-auth-bg min-h-screen flex items-center justify-center px-6">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-md rounded-[14px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] p-8 shadow-[var(--shadow-md)]"
      >
        <div className="mb-6 text-center">
          <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
            Vérification
          </div>
          <h1
            className="mt-2 text-3xl leading-none"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            Code 2FA
          </h1>
          <p className="mt-3 text-sm text-[var(--color-foreground-subtle)]">
            Saisis le code à 6 chiffres affiché par ton application
            d'authentification.
          </p>
        </div>

        <input
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          autoFocus
          value={code}
          onChange={(e) =>
            setCode(e.target.value.replace(/\D/g, '').slice(0, 6))
          }
          placeholder="000000"
          className="w-full rounded-[12px] border border-[var(--color-border-strong)] bg-[var(--color-background)] py-3 px-4 text-center text-2xl font-mono tracking-[0.5em] focus:border-[var(--color-accent)] focus:outline-none"
        />

        {error && (
          <div className="mt-3 rounded-[8px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-3 py-2 text-xs text-[var(--color-danger)]">
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={code.length < 6 || submitting}
          className="mt-6 block w-full rounded-[10px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] py-3 text-center font-medium text-white disabled:opacity-40 disabled:cursor-not-allowed shadow-[var(--shadow-glow-accent)] transition-colors"
        >
          {submitting ? 'Vérification…' : 'Vérifier'}
        </button>

        <button
          type="button"
          onClick={() => {
            window.sessionStorage.removeItem('reborn-admin.2fa.challenge');
            router.replace('/');
          }}
          className="mt-3 block w-full text-center text-xs text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground)]"
        >
          Annuler
        </button>
      </form>
    </main>
  );
}
