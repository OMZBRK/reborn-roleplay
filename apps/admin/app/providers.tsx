'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { Toaster } from 'sonner';
import { ConfirmProvider } from '@/components/ui/Dialog';

/**
 * Rafraîchissement unifié (PANEL_V2 §5.6) : listes et compteurs à 30 s,
 * fiche ouverte à 10 s, séance en cours à 5 s (réglé par requête) ; rien
 * quand l'onglet du navigateur est caché (comportement par défaut de React
 * Query : `refetchIntervalInBackground` reste à false).
 */
export function QueryProvider({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            retry: (failureCount, error) => {
              const status =
                error instanceof Error && 'status' in error
                  ? (error as { status?: number }).status
                  : undefined;
              // Pas de nouvel essai sur un refus : 401, 403, 404 ne changent pas.
              if (status === 401 || status === 403 || status === 404) {
                return false;
              }
              return failureCount < 2;
            },
          },
        },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <ConfirmProvider>{children}</ConfirmProvider>
      <Toaster
        position="bottom-right"
        theme="dark"
        toastOptions={{
          style: {
            background: 'var(--color-surface-2)',
            border: '1px solid var(--color-border-strong)',
            color: 'var(--color-fg)',
            fontFamily: 'var(--font-sans)',
          },
        }}
      />
    </QueryClientProvider>
  );
}
