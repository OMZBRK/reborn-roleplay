'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import type { OralSlot } from '@/lib/types';

/**
 * Gestion des créneaux de test oral (L5, pool global). Le staff ouvre des
 * créneaux de 30 min ; les candidats en réservent un libre depuis le launcher.
 * Ici : ouverture d'un lot, vue du planning, annulation / clôture.
 */
export default function OralSlotsPage() {
  const qc = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'oral-slots'],
    queryFn: () => api<OralSlot[]>('/admin/oral-slots'),
    refetchInterval: 10_000,
  });

  // Formulaire d'ouverture : une date + une heure de départ + un nombre de
  // créneaux consécutifs de `durationMin`.
  const [date, setDate] = useState('');
  const [time, setTime] = useState('20:00');
  const [count, setCount] = useState(4);
  const [durationMin, setDurationMin] = useState(30);

  const openMut = useMutation({
    mutationFn: (slots: { startAt: string; durationMin: number }[]) =>
      api<{ created: number }>('/admin/oral-slots', {
        method: 'POST',
        body: { slots },
      }),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['admin', 'oral-slots'] });
      toast.success(`${res.created} créneau(x) ouvert(s)`);
    },
    onError: (err) =>
      toast.error('Échec de l’ouverture', { description: (err as Error).message }),
  });

  const cancelMut = useMutation({
    mutationFn: (id: string) =>
      api(`/admin/oral-slots/${id}/cancel`, { method: 'PATCH' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'oral-slots'] });
      toast.success('Créneau annulé');
    },
    onError: (err) => toast.error('Échec', { description: (err as Error).message }),
  });

  const doneMut = useMutation({
    mutationFn: (id: string) =>
      api(`/admin/oral-slots/${id}/done`, { method: 'PATCH', body: {} }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'oral-slots'] });
      toast.success('Oral clôturé');
    },
    onError: (err) => toast.error('Échec', { description: (err as Error).message }),
  });

  function handleOpen() {
    if (!date || !time) {
      toast.error('Renseigne une date et une heure.');
      return;
    }
    const base = new Date(`${date}T${time}`);
    if (Number.isNaN(base.getTime())) {
      toast.error('Date/heure invalide.');
      return;
    }
    const slots = Array.from({ length: count }, (_, i) => ({
      startAt: new Date(base.getTime() + i * durationMin * 60_000).toISOString(),
      durationMin,
    }));
    openMut.mutate(slots);
  }

  // Regroupe les créneaux par jour pour l'affichage.
  const groups = groupByDay(data ?? []);

  return (
    <div className="px-10 py-10 max-w-5xl mx-auto">
      <header className="mb-8">
        <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
          Whitelist
        </div>
        <h1 className="mt-1 text-4xl leading-none" style={{ fontFamily: 'var(--font-display)' }}>
          Créneaux oraux
        </h1>
        <p className="mt-2 text-sm text-[var(--color-foreground-subtle)]">
          Ouvre des créneaux de test oral. Les candidats en réservent un libre
          depuis leur launcher (pool global).
        </p>
      </header>

      {/* Ouverture d'un lot */}
      <div className="mb-8 rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-5">
        <div className="mb-4 text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
          Ouvrir des créneaux
        </div>
        <div className="flex flex-wrap items-end gap-4">
          <Field label="Date">
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-2 text-sm focus:border-[var(--color-accent)] focus:outline-none"
            />
          </Field>
          <Field label="Heure de début">
            <input
              type="time"
              value={time}
              onChange={(e) => setTime(e.target.value)}
              className="rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-2 text-sm focus:border-[var(--color-accent)] focus:outline-none"
            />
          </Field>
          <Field label="Nb de créneaux">
            <input
              type="number"
              min={1}
              max={32}
              value={count}
              onChange={(e) => setCount(Math.max(1, Math.min(32, Number(e.target.value))))}
              className="rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-2 text-sm focus:border-[var(--color-accent)] focus:outline-none w-24"
            />
          </Field>
          <Field label="Durée (min)">
            <input
              type="number"
              min={5}
              max={180}
              step={5}
              value={durationMin}
              onChange={(e) => setDurationMin(Math.max(5, Math.min(180, Number(e.target.value))))}
              className="rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-2 text-sm focus:border-[var(--color-accent)] focus:outline-none w-24"
            />
          </Field>
          <button
            type="button"
            disabled={openMut.isPending}
            onClick={handleOpen}
            className="rounded-[8px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] px-5 py-2.5 text-sm font-medium text-white disabled:opacity-40"
          >
            {openMut.isPending ? 'Ouverture…' : `Ouvrir ${count} créneau${count > 1 ? 'x' : ''}`}
          </button>
        </div>
      </div>

      {/* Planning */}
      {isLoading && (
        <div className="text-sm text-[var(--color-foreground-subtle)]">Chargement…</div>
      )}
      {error && (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      )}
      {data && data.length === 0 && (
        <div className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] px-6 py-10 text-center text-sm text-[var(--color-foreground-subtle)]">
          Aucun créneau ouvert. Ouvre-en un lot ci-dessus.
        </div>
      )}

      <div className="space-y-6">
        {groups.map(({ day, slots }) => (
          <div key={day}>
            <div className="mb-2 text-sm font-medium text-[var(--color-foreground)]">
              {day}
            </div>
            <div className="space-y-2">
              {slots.map((s) => (
                <div
                  key={s.id}
                  className="flex items-center justify-between gap-4 rounded-[10px] border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-3"
                >
                  <div className="flex items-center gap-3">
                    <span className="font-mono text-sm text-[var(--color-foreground)]">
                      {fmtTime(s.startAt)}
                    </span>
                    <span className="text-xs text-[var(--color-foreground-muted)]">
                      {s.durationMin} min
                    </span>
                    <SlotBadge status={s.status} />
                    {s.bookedBy && (
                      <span className="text-xs text-[var(--color-foreground-subtle)]">
                        · {s.bookedBy.displayName ?? s.bookedBy.minecraftUsername}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    {s.status === 'BOOKED' && (
                      <button
                        type="button"
                        disabled={doneMut.isPending}
                        onClick={() => doneMut.mutate(s.id)}
                        className="rounded-[6px] border border-[var(--color-success)]/40 px-3 py-1.5 text-xs text-[var(--color-success)] hover:bg-[var(--color-success-soft)] disabled:opacity-40"
                      >
                        Clôturer l’oral
                      </button>
                    )}
                    {(s.status === 'OPEN' || s.status === 'BOOKED') && (
                      <button
                        type="button"
                        disabled={cancelMut.isPending}
                        onClick={() => cancelMut.mutate(s.id)}
                        className="rounded-[6px] border border-[var(--color-danger)]/40 px-3 py-1.5 text-xs text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)] disabled:opacity-40"
                      >
                        Annuler
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-[11px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
        {label}
      </span>
      {children}
    </label>
  );
}

function SlotBadge({ status }: { status: OralSlot['status'] }) {
  const map: Record<OralSlot['status'], { label: string; cls: string }> = {
    OPEN: {
      label: 'Libre',
      cls: 'bg-[var(--color-accent-soft)] text-[var(--color-accent)] border-[var(--color-accent)]/40',
    },
    BOOKED: {
      label: 'Réservé',
      cls: 'bg-[var(--color-warning-soft)] text-[var(--color-warning)] border-[var(--color-warning)]/40',
    },
    DONE: {
      label: 'Passé',
      cls: 'bg-[var(--color-success-soft)] text-[var(--color-success)] border-[var(--color-success)]/40',
    },
    CANCELLED: {
      label: 'Annulé',
      cls: 'bg-[var(--color-surface-elevated)] text-[var(--color-foreground-muted)] border-[var(--color-border-strong)]',
    },
  };
  const t = map[status];
  return (
    <span className={`inline-block rounded-full border px-2.5 py-0.5 text-[10px] ${t.cls}`}>
      {t.label}
    </span>
  );
}

function fmtTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('fr-FR', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function groupByDay(slots: OralSlot[]): { day: string; slots: OralSlot[] }[] {
  const sorted = [...slots].sort(
    (a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime(),
  );
  const out: { day: string; slots: OralSlot[] }[] = [];
  for (const s of sorted) {
    const day = new Date(s.startAt).toLocaleDateString('fr-FR', {
      weekday: 'long',
      day: '2-digit',
      month: 'long',
    });
    const last = out[out.length - 1];
    if (last && last.day === day) last.slots.push(s);
    else out.push({ day, slots: [s] });
  }
  return out;
}
