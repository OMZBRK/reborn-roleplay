'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Icone } from '@/components/ui/Icone';
import { setTokens } from '@/lib/auth';

/**
 * Retour de la connexion Discord. L'API redirige avec `#access=…&refresh=…`
 * (ou `#error=…`, ou `#challenge=…` si la double authentification est
 * active). On lit le fragment plutôt que les paramètres pour que les jetons
 * n'apparaissent ni dans les journaux du proxy ni dans le referer.
 */

/**
 * `#error=…` porte un code de Discord (`access_denied`, avec sa description
 * en anglais), un code de l'API (`missing_params`) ou un message de l'API.
 * On n'affiche jamais un code brut (PANEL_V2 §4.8, §5.5) : codes et anciens
 * messages sans accents sont traduits, un message déjà en français passe tel
 * quel.
 */
function messageConnexion(brut: string): string {
  const m = brut.trim();
  const bas = m.toLowerCase();
  if (bas === 'access_denied' || bas.includes('resource owner') || bas.includes('denied')) {
    return 'Connexion annulée sur Discord.';
  }
  if (bas === 'missing_params') return 'Réponse de Discord incomplète, réessaie.';
  if (bas.startsWith('state invalide')) return 'Lien de connexion expiré : recommence.';
  if (bas.startsWith('aucun compte reborn')) {
    return 'Aucun compte Reborn n’est lié à ce Discord : lie-le depuis le launcher.';
  }
  if (bas.startsWith('role insuffisant') || bas.startsWith('rôle insuffisant')) {
    return 'Ton grade ne donne pas accès au panel staff.';
  }
  if (bas === 'compte banni.' || bas.startsWith('compte banni')) return 'Ce compte est banni : accès refusé.';
  // Code technique inconnu (minuscules et soulignés, sans espace) : jamais affiché tel quel.
  if (/^[a-z0-9_]+$/.test(m)) return 'Connexion impossible pour le moment, réessaie.';
  return m;
}

export default function AuthCallbackPage() {
  const router = useRouter();
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    const hash = window.location.hash.startsWith('#')
      ? window.location.hash.slice(1)
      : window.location.hash;
    const params = new URLSearchParams(hash);

    const err = params.get('error');
    if (err) {
      setErreur(messageConnexion(err));
      return;
    }
    const challenge = params.get('challenge');
    if (challenge) {
      // Double authentification : le défi reste en sessionStorage (volatile).
      window.sessionStorage.setItem('reborn-admin.2fa.challenge', challenge);
      window.history.replaceState({}, '', '/auth/callback');
      router.replace('/auth/2fa');
      return;
    }
    const access = params.get('access');
    const refresh = params.get('refresh');
    if (!access || !refresh) {
      setErreur('Jetons absents de la réponse.');
      return;
    }
    setTokens(access, refresh);
    // On efface l'adresse : une capture ou un retour arrière ne montrent pas les jetons.
    window.history.replaceState({}, '', '/auth/callback');
    // `arrivee` : la page d'arrivée suit le grade (PANEL_V2 §3.2, voir dashboard).
    router.replace('/dashboard?arrivee=1');
  }, [router]);

  return (
    <main className="reborn-auth-bg flex min-h-screen items-center justify-center px-4">
      <div className="card w-full max-w-[400px] px-8 py-9 text-center" role={erreur ? 'alert' : 'status'}>
        {erreur ? (
          <>
            <div className="empty-i mx-auto">
              <Icone nom="lock" taille={18} />
            </div>
            <h1 className="text-[18px] font-semibold text-fg">Connexion refusée</h1>
            <p className="mt-2 text-[13px] text-fg-2">{erreur}</p>
            <a href="/" className="btn btn-block mt-6">
              Réessayer
            </a>
          </>
        ) : (
          <>
            <span className="spin mx-auto block text-fg-3" aria-hidden />
            <h1 className="mt-4 text-[16px] font-semibold text-fg">Connexion en cours…</h1>
          </>
        )}
      </div>
    </main>
  );
}
