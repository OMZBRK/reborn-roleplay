'use client';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from './api';
import type { AppStatus, FiltreWhitelist, WhitelistListe } from './types';

/**
 * Onglets de Candidatures › Dossiers (PANEL_V2 §4.3), partagés par la liste
 * et la fiche (« 1 / 5 », Suivante, enchaînement après une décision). Les
 * onglets « à faire » sont calculés côté API sur les phases
 * (CANDIDATURE_ET_DOUANE.md §1.2).
 */
export type CleOngletDossiers = 'ECRIT' | 'DOUANE' | 'REVISION' | 'APPROVED' | 'REJECTED' | 'ALL';

export interface OngletDossiers {
  cle: CleOngletDossiers;
  label: string;
  filtre?: FiltreWhitelist;
  status?: AppStatus;
}

export const ONGLETS_DOSSIERS: readonly OngletDossiers[] = [
  { cle: 'ECRIT', label: 'À relire', filtre: 'ECRIT' },
  { cle: 'DOUANE', label: 'Douane', filtre: 'DOUANE' },
  { cle: 'REVISION', label: 'À revoir', filtre: 'REVISION' },
  { cle: 'APPROVED', label: 'Acceptées', status: 'APPROVED' },
  { cle: 'REJECTED', label: 'Refusées', status: 'REJECTED' },
  { cle: 'ALL', label: 'Toutes' },
];

export function ongletDossiers(cle: string | null | undefined): OngletDossiers {
  return ONGLETS_DOSSIERS.find((o) => o.cle === cle) ?? ONGLETS_DOSSIERS[0]!;
}

export function cheminListe(o: OngletDossiers, opts: { q?: string; take: number; skip: number }) {
  const p = new URLSearchParams();
  if (o.filtre) p.set('filtre', o.filtre);
  if (o.status) p.set('status', o.status);
  if (opts.q) p.set('q', opts.q);
  p.set('take', String(opts.take));
  p.set('skip', String(opts.skip));
  return `/admin/whitelist?${p.toString()}`;
}

/** Une page de la liste des dossiers. */
export function useDossiers(o: OngletDossiers, q: string, page: number, parPage: number) {
  return useQuery({
    queryKey: ['admin', 'whitelist', 'liste', o.cle, q, page, parPage],
    queryFn: () => api<WhitelistListe>(cheminListe(o, { q, take: parPage, skip: page * parPage })),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });
}

/** Ordre de l'onglet pour la navigation dans la fiche (200 premiers). */
export function useOrdreDossiers(o: OngletDossiers) {
  return useQuery({
    queryKey: ['admin', 'whitelist', 'ordre', o.cle],
    queryFn: async () => {
      const r = await api<WhitelistListe>(cheminListe(o, { take: 200, skip: 0 }));
      return r.items.map((i) => i.id);
    },
    refetchInterval: 30_000,
  });
}
