'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { toast } from 'sonner';
import { IconShield } from '@/components/icons';
import { SkeletonRows } from '@/components/Skeleton';
import { api } from '@/lib/api';

interface LoginAnomaly {
  id: string;
  userId: string;
  ipAddress: string;
  country: string | null;
  reason: string;
  acknowledged: boolean;
  notifiedVia: string[];
  createdAt: string;
}

export default function AnomaliesPage() {
  const qc = useQueryClient();
  const [showAll, setShowAll] = useState(false);

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'anomalies', showAll],
    queryFn: () =>
      api<LoginAnomaly[]>(
        `/admin/anomalies${showAll ? '' : '?unack=true'}`,
      ),
    refetchInterval: 30_000,
  });

  const ackMut = useMutation({
    mutationFn: (id: string) =>
      api(`/admin/anomalies/${id}/ack`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'anomalies'] });
      toast.success('Anomalie acknowledgée');
    },
    onError: (err) => toast.error((err as Error).message),
  });

  return (
    <div className="px-10 py-10 max-w-5xl mx-auto">
      <header className="mb-8">
        <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
          Securite
        </div>
        <h1
          className="mt-1 text-5xl leading-none bg-gradient-to-r from-white to-white/40 bg-clip-text text-transparent"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          Anomalies de login
        </h1>
        <div className="mt-3 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
        <p className="mt-3 text-xs text-[var(--color-foreground-muted)] max-w-2xl">
          Logins detectes depuis un nouveau pays par rapport au precedent
          connu. Ack pour marquer comme verifie (utilisateur en deplacement,
          changement d'IP legitime, etc.).
        </p>
      </header>

      <div className="mb-4 flex gap-2">
        <button
          type="button"
          onClick={() => setShowAll(false)}
          className={`rounded-full px-4 py-1.5 text-sm transition-colors ${
            !showAll
              ? 'bg-[var(--color-accent)] text-white shadow-[var(--shadow-glow-accent)]'
              : 'border border-[var(--color-border-strong)] text-[var(--color-foreground-subtle)]'
          }`}
        >
          À traiter
        </button>
        <button
          type="button"
          onClick={() => setShowAll(true)}
          className={`rounded-full px-4 py-1.5 text-sm transition-colors ${
            showAll
              ? 'bg-[var(--color-accent)] text-white shadow-[var(--shadow-glow-accent)]'
              : 'border border-[var(--color-border-strong)] text-[var(--color-foreground-subtle)]'
          }`}
        >
          Toutes
        </button>
      </div>

      {isLoading && !data ? (
        <SkeletonRows count={4} />
      ) : error ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      ) : !data || data.length === 0 ? (
        <div className="rounded-[14px] border border-dashed border-[var(--color-border-strong)] py-16 text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-[var(--color-success-soft)] text-[var(--color-success)]">
            <IconShield />
          </div>
          <div className="text-sm text-[var(--color-foreground)]">
            Rien à signaler.
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          {data.map((a) => (
            <div
              key={a.id}
              className={`rounded-[12px] border p-4 ${
                a.acknowledged
                  ? 'border-[var(--color-border)] bg-[var(--color-surface)]'
                  : 'border-[var(--color-warning)]/40 bg-[var(--color-warning-soft)]/30'
              }`}
            >
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-[10px] uppercase tracking-wider text-[var(--color-warning)]">
                      ⚠️ {a.country ?? 'pays inconnu'}
                    </span>
                    {a.acknowledged && (
                      <span className="text-[10px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
                        ack
                      </span>
                    )}
                  </div>
                  <div className="text-sm text-[var(--color-foreground)]">
                    {a.reason}
                  </div>
                  <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-[var(--color-foreground-muted)]">
                    <span>
                      User <code className="font-mono">{a.userId.slice(0, 8)}</code>
                    </span>
                    <span>
                      IP <code className="font-mono">{a.ipAddress}</code>
                    </span>
                    <span>{new Date(a.createdAt).toLocaleString('fr-FR')}</span>
                    {a.notifiedVia.length > 0 && (
                      <span>notif: {a.notifiedVia.join(', ')}</span>
                    )}
                  </div>
                </div>
                {!a.acknowledged && (
                  <button
                    type="button"
                    onClick={() => ackMut.mutate(a.id)}
                    disabled={ackMut.isPending}
                    className="rounded-[8px] border border-[var(--color-border-strong)] px-3 py-1.5 text-xs hover:bg-[var(--color-surface-elevated)] disabled:opacity-40"
                  >
                    Marquer vu
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
