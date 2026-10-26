'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from './api';
import type { CurrentUser } from './types';

/**
 * Droits du panel (PANEL_V2 §3.3) : le panel ne lit QUE la liste
 * `capacites` renvoyée par GET /v1/auth/me. Il ne recopie ni les rangs ni la
 * table des grades : l'API (auth/capacites.ts) est la seule source, et refuse
 * de toute façon ce qui n'est pas permis. Ici on se contente de ne pas
 * afficher une entrée, un onglet ou un bouton qui mènerait à un refus.
 *
 * Network (Q13) : une capacité pourra porter un serveur (`tickets.traiter@reborn`)
 * à l'arrivée d'un deuxième serveur ; `aCapacite` compare alors le préfixe.
 */

/** Capacités fixes connues du panel (mêmes clés que l'API). */
export type CapaciteFixe =
  | 'panel.acceder'
  | 'pilotage.voir'
  | 'pilotage.analyses'
  | 'pilotage.demographie'
  | 'pilotage.revenus'
  | 'joueurs.voir'
  | 'joueurs.ip'
  | 'grades.changer'
  | 'candidatures.relire'
  | 'candidatures.reinitialiser'
  | 'douane.organiser'
  | 'douane.examiner'
  | 'tickets.traiter'
  | 'tickets.bug'
  | 'prises.forcer'
  | 'sanctions.voir'
  | 'sanctions.poser'
  | 'securite.voir'
  | 'audit.voir'
  | 'wiki.editer'
  | 'wiki.supprimer'
  | 'discord.publier'
  | 'techniques.editer'
  | 'techniques.deployer'
  | 'techniques.supprimer'
  | 'reponses-types.gerer';

/** `fichiers.<zone>` (zones de files.scopes.ts) et `fichiers.ecrire`. */
export type CapaciteFichiers = `fichiers.${string}`;
export type Capacite = CapaciteFixe | CapaciteFichiers;

/** Pseudo-capacité : « au moins une zone de fichiers ». */
export const UNE_ZONE_FICHIERS = 'fichiers.*' as const;
export type ExigenceCapacite = Capacite | typeof UNE_ZONE_FICHIERS;

/** Capacités qui donnent une file « À traiter ». */
export const CAPACITES_FILE: readonly Capacite[] = [
  'candidatures.relire',
  'tickets.traiter',
  'tickets.bug',
  'securite.voir',
];

/** Clé React Query du profil (partagée par tout le panel). */
export const CLE_MOI = ['admin', 'me'] as const;

function correspond(possedee: string, voulue: string): boolean {
  // Une capacité portée par serveur (« x@serveur ») vaut la capacité « x ».
  return possedee === voulue || possedee.startsWith(`${voulue}@`);
}

export function aCapacite(
  capacites: readonly string[] | undefined,
  voulue: ExigenceCapacite,
): boolean {
  if (!capacites) return false;
  if (voulue === UNE_ZONE_FICHIERS) return aAccesFichiers(capacites);
  return capacites.some((c) => correspond(c, voulue));
}

/** Vrai si le compte a au moins une des capacités (liste vide : vrai). */
export function aUneCapacite(
  capacites: readonly string[] | undefined,
  voulues: readonly ExigenceCapacite[],
): boolean {
  if (voulues.length === 0) return true;
  return voulues.some((v) => aCapacite(capacites, v));
}

/** Au moins une zone `fichiers.<zone>` (`fichiers.ecrire` seul ne suffit pas). */
export function aAccesFichiers(capacites: readonly string[] | undefined): boolean {
  return (capacites ?? []).some(
    (c) => c.startsWith('fichiers.') && c !== 'fichiers.ecrire',
  );
}

/** Profil du staff connecté, relu toutes les 5 minutes (le grade peut changer). */
export function useMoi() {
  return useQuery({
    queryKey: CLE_MOI,
    queryFn: () => api<CurrentUser>('/auth/me'),
    staleTime: 60_000,
    refetchInterval: 5 * 60_000,
  });
}

/** Droits du compte connecté, pour l'affichage. */
export function useDroits() {
  const { data, isLoading, error } = useMoi();
  const capacites = data?.capacites ?? [];
  return {
    moi: data,
    charge: isLoading,
    erreur: error as Error | null,
    capacites,
    peut: (c: ExigenceCapacite) => aCapacite(capacites, c),
    peutUne: (...cs: ExigenceCapacite[]) => aUneCapacite(capacites, cs),
  };
}
