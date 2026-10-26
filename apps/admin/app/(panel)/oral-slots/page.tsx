'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import { toast } from 'sonner';
import { Garde } from '@/components/Garde';
import { SectionTabs } from '@/components/shell/SectionTabs';
import { Badge } from '@/components/ui/Badge';
import { Button, Mot } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { useConfirm } from '@/components/ui/Dialog';
import { Field, Input } from '@/components/ui/Field';
import { PageHeader } from '@/components/ui/PageHeader';
import { Callout, Chargement, EmptyState, ErrorState, messageErreur } from '@/components/ui/States';
import { api } from '@/lib/api';
import { heureCourte, jourLong } from '@/lib/candidature';
import type { TonBadge } from '@/lib/statuts';
import type { OralSlot } from '@/lib/types';

/**
 * Candidatures › Anciens créneaux (L5, pool global) : un candidat par
 * créneau de test oral. Onglet temporaire, servi pendant la transition et
 * supprimé à la mise en service de la douane en jeu (PANEL_V2 §3.4).
 */

const STATUT_CRENEAU: Record<OralSlot['status'], { label: string; ton: TonBadge }> = {
  OPEN: { label: 'Libre', ton: 'vide' },
  BOOKED: { label: 'Réservé', ton: 'info' },
  DONE: { label: 'Passé', ton: 'succes' },
  CANCELLED: { label: 'Annulé', ton: 'neutre' },
};

export default function AnciensCreneauxPage() {
  return (
    <Garde capacites={['douane.organiser']}>
      <Creneaux />
    </Garde>
  );
}

function Creneaux() {
  const qc = useQueryClient();
  const { confirmer } = useConfirm();
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin', 'oral-slots'],
    queryFn: () => api<OralSlot[]>('/admin/oral-slots'),
    refetchInterval: 10_000,
  });

  // Ouverture : une date, une heure de départ, un nombre de créneaux consécutifs.
  const [date, setDate] = useState('');
  const [time, setTime] = useState('20:00');
  const [count, setCount] = useState(4);
  const [durationMin, setDurationMin] = useState(30);

  const openMut = useMutation({
    mutationFn: (slots: { startAt: string; durationMin: number }[]) =>
      api<{ created: number }>('/admin/oral-slots', { method: 'POST', body: { slots } }),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['admin', 'oral-slots'] });
      toast.success(`${res.created} créneau${res.created > 1 ? 'x' : ''} ouvert${res.created > 1 ? 's' : ''}`);
    },
    onError: (e) => toast.error('Créneaux non ouverts', { description: messageErreur(e) }),
  });
  const cancelMut = useMutation({
    mutationFn: (id: string) => api(`/admin/oral-slots/${id}/cancel`, { method: 'PATCH' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'oral-slots'] });
      toast.success('Créneau annulé');
    },
    onError: (e) => toast.error('Échec', { description: messageErreur(e) }),
  });
  const doneMut = useMutation({
    mutationFn: (id: string) => api(`/admin/oral-slots/${id}/done`, { method: 'PATCH', body: {} }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'oral-slots'] });
      toast.success('Oral clôturé');
    },
    onError: (e) => toast.error('Échec', { description: messageErreur(e) }),
  });

  function ouvrir() {
    if (!date || !time) {
      toast.error('Renseigne une date et une heure.');
      return;
    }
    const base = new Date(`${date}T${time}`);
    if (Number.isNaN(base.getTime())) {
      toast.error('Date ou heure invalide.');
      return;
    }
    openMut.mutate(
      Array.from({ length: count }, (_, i) => ({
        startAt: new Date(base.getTime() + i * durationMin * 60_000).toISOString(),
        durationMin,
      })),
    );
  }

  const groupes = grouperParJour(data ?? []);

  return (
    <div className="page">
      <PageHeader
        fil={[{ label: 'Candidatures', href: '/whitelist' }]}
        titre="Candidatures"
        sousTitre="Anciens créneaux d’oral, un candidat par créneau, servis pendant la transition"
      />
      <SectionTabs section="candidatures" />

      <div className="mb-5">
        <Callout ton="neutre">
          Ancien système. Les séances avec capacité se planifient dans{' '}
          <Link href="/douane" className="lien">
            Séances de douane
          </Link>
          .
        </Callout>
      </div>

      <Card titre="Ouvrir des créneaux" className="mb-6">
        <form
          className="flex flex-wrap items-end gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            ouvrir();
          }}
        >
          <Field label="Date">
            {(id) => <Input id={id} type="date" value={date} onChange={(e) => setDate(e.target.value)} />}
          </Field>
          <Field label="Heure de début">
            {(id) => <Input id={id} type="time" value={time} onChange={(e) => setTime(e.target.value)} />}
          </Field>
          <Field label="Nombre de créneaux">
            {(id) => (
              <Input
                id={id}
                type="number"
                min={1}
                max={32}
                className="w-24"
                value={count}
                onChange={(e) => setCount(Math.max(1, Math.min(32, Number(e.target.value))))}
              />
            )}
          </Field>
          <Field label="Durée (min)">
            {(id) => (
              <Input
                id={id}
                type="number"
                min={5}
                max={180}
                step={5}
                className="w-24"
                value={durationMin}
                onChange={(e) => setDurationMin(Math.max(5, Math.min(180, Number(e.target.value))))}
              />
            )}
          </Field>
          <Button type="submit" enCours={openMut.isPending}>
            Ouvrir {count} créneau{count > 1 ? 'x' : ''}
          </Button>
        </form>
      </Card>

      {isLoading && <Chargement />}
      {error && <ErrorState erreur={error} onReessayer={() => void refetch()} />}
      {data && data.length === 0 && <EmptyState icone="clock" titre="Aucun créneau ouvert." aide="Ouvre-en un lot ci-dessus." />}

      <div className="flex flex-col gap-5">
        {groupes.map(({ jour, creneaux }) => (
          <div key={jour}>
            <div className="eyebrow mb-2">{jour}</div>
            <div className="dt-wrap">
              <table className="dt empilable">
                <caption className="sr-only">Créneaux du {jour}</caption>
                {/* Mêmes colonnes d'un jour à l'autre : les tableaux s'alignent. */}
                <colgroup>
                  <col style={{ width: '22%' }} />
                  <col style={{ width: '18%' }} />
                  <col />
                  <col style={{ width: '26%' }} />
                </colgroup>
                <thead>
                  <tr>
                    <th scope="col">Heure</th>
                    <th scope="col">Statut</th>
                    <th scope="col">Candidat</th>
                    <th scope="col" className="r">
                      <span className="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {creneaux.map((s) => {
                    const st = STATUT_CRENEAU[s.status];
                    return (
                      <tr key={s.id}>
                        <td className="m nw tabular">
                          {heureCourte(s.startAt)} <span className="text-fg-3">· {s.durationMin} min</span>
                        </td>
                        <td className="nw">
                          <Badge ton={st.ton}>{st.label}</Badge>
                        </td>
                        <td>{s.bookedBy ? (s.bookedBy.displayName ?? s.bookedBy.minecraftUsername) : '—'}</td>
                        <td className="r nw">
                          <span className="inline-flex gap-4">
                            {s.status === 'BOOKED' && (
                              <Mot disabled={doneMut.isPending} onClick={() => doneMut.mutate(s.id)}>
                                Clôturer l’oral
                              </Mot>
                            )}
                            {(s.status === 'OPEN' || s.status === 'BOOKED') && (
                              <Mot
                                ton="danger"
                                disabled={cancelMut.isPending}
                                onClick={async () => {
                                  const ok =
                                    s.status === 'OPEN' ||
                                    (await confirmer({
                                      titre: 'Annuler ce créneau réservé ?',
                                      message: 'Le candidat perd sa place.',
                                      confirmer: 'Annuler le créneau',
                                      annuler: 'Garder',
                                      danger: true,
                                    }));
                                  if (ok) cancelMut.mutate(s.id);
                                }}
                              >
                                Annuler
                              </Mot>
                            )}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function grouperParJour(slots: OralSlot[]): { jour: string; creneaux: OralSlot[] }[] {
  const tries = [...slots].sort((a, b) => a.startAt.localeCompare(b.startAt));
  const out: { jour: string; creneaux: OralSlot[] }[] = [];
  for (const s of tries) {
    const jour = jourLong(s.startAt);
    const dernier = out[out.length - 1];
    if (dernier && dernier.jour === jour) dernier.creneaux.push(s);
    else out.push({ jour, creneaux: [s] });
  }
  return out;
}
