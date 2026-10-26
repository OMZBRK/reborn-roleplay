'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { CodeSixCases } from '@/components/CodeSixCases';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/States';
import { api } from '@/lib/api';
import { setTokens } from '@/lib/auth';

interface VerifyResponse {
  accessToken: string;
  refreshToken: string;
}

/**
 * Double authentification après la connexion Discord : lit le défi posé par
 * /auth/callback (sessionStorage), demande le code à six chiffres, l'envoie à
 * /auth/2fa/verify et reçoit les vrais jetons.
 */
export default function DoubleAuthPage() {
  const router = useRouter();
  const [defi, setDefi] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<unknown>(null);

  useEffect(() => {
    const c = window.sessionStorage.getItem('reborn-admin.2fa.challenge');
    if (!c) {
      router.replace('/');
      return;
    }
    setDefi(c);
  }, [router]);

  async function verifier(valeur: string) {
    if (!defi || valeur.length < 6 || envoi) return;
    setEnvoi(true);
    setErreur(null);
    try {
      const jetons = await api<VerifyResponse>('/auth/2fa/verify', {
        method: 'POST',
        body: { challenge: defi, code: valeur },
        authenticated: false,
      });
      setTokens(jetons.accessToken, jetons.refreshToken);
      window.sessionStorage.removeItem('reborn-admin.2fa.challenge');
      router.replace('/dashboard?arrivee=1');
    } catch (e) {
      setErreur(e);
      setCode('');
    } finally {
      setEnvoi(false);
    }
  }

  if (!defi) return null;

  return (
    <main className="reborn-auth-bg flex min-h-screen items-center justify-center px-4">
      <form
        className="card w-full max-w-[420px] px-8 py-9 text-center"
        onSubmit={(e) => {
          e.preventDefault();
          void verifier(code);
        }}
      >
        <h1 className="font-display text-[28px] leading-none tracking-[0.04em] text-fg">Double authentification</h1>
        <p className="mt-3 text-[13px] text-fg-2">
          Saisis le code à six chiffres affiché par ton application d’authentification.
        </p>
        <div className="mt-6">
          <CodeSixCases valeur={code} onChange={setCode} onComplet={(v) => void verifier(v)} autoFocus desactive={envoi} />
        </div>
        {erreur ? (
          <div className="mt-4 text-left">
            <ErrorState erreur={erreur} />
          </div>
        ) : null}
        <Button type="submit" variante="primary" bloc className="mt-6" disabled={code.length < 6} enCours={envoi}>
          Vérifier
        </Button>
        <Button
          variante="ghost"
          bloc
          className="mt-2"
          onClick={() => {
            window.sessionStorage.removeItem('reborn-admin.2fa.challenge');
            router.replace('/');
          }}
        >
          Annuler
        </Button>
      </form>
    </main>
  );
}
