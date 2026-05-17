'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { Toaster } from 'sonner';

export function QueryProvider({ children }: { children: React.ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            retry: (failureCount, error) => {
              if (
                error instanceof Error &&
                'status' in error &&
                (error as { status?: number }).status === 401
              ) {
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
      {children}
      <Toaster
        position="bottom-right"
        theme="dark"
        richColors
        toastOptions={{
          style: {
            background: 'var(--color-surface-elevated)',
            border: '1px solid var(--color-border-strong)',
            color: 'var(--color-foreground)',
          },
        }}
      />
    </QueryClientProvider>
  );
}
