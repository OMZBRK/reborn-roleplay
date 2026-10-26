'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { API_BASE } from '@/lib/api';
import { isAuthenticated } from '@/lib/auth';
import logo from '@/components/shell/logo.png';

/**
 * Connexion (PANEL_V2 §4.8) : logo, « Se connecter avec Discord », une
 * phrase. Le grade minimum n'est plus écrit en anglais : l'API refuse un
 * compte qui n'est pas du staff, avec un message en français.
 */
export default function ConnexionPage() {
  const router = useRouter();

  useEffect(() => {
    if (isAuthenticated()) router.replace('/dashboard?arrivee=1');
  }, [router]);

  return (
    <main className="reborn-auth-bg flex min-h-screen items-center justify-center px-4">
      <div className="card w-full max-w-[400px] px-8 py-9 text-center">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={logo.src} alt="" width={48} height={48} className="mx-auto rounded-[8px]" />
        <h1 className="mt-4 font-display text-[32px] leading-none tracking-[0.06em] text-fg">Reborn</h1>
        <p className="mt-1 text-[13px] text-fg-3">Panel staff</p>

        <a href={`${API_BASE}/auth/discord/staff/start`} className="btn btn-primary btn-block mt-8">
          Se connecter avec Discord
        </a>

        <p className="mt-6 text-[13px] leading-relaxed text-fg-3">
          Réservé au staff de Reborn. Ton compte Discord doit être lié à ton compte Reborn depuis le launcher.
        </p>
      </div>
    </main>
  );
}
