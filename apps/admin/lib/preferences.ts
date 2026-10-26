'use client';

import { useCallback, useSyncExternalStore } from 'react';

/**
 * Préférences propres à chaque membre du staff (Réglages › Préférences,
 * PANEL_V2 §4.7). Ce sont des conforts d'affichage : elles vivent dans le
 * navigateur (localStorage), se perdent sans dommage et ne remontent jamais à
 * l'API. Toute lecture ou écriture est protégée (navigation privée, stockage
 * bloqué) : le panel marche sans.
 */

export interface Preferences {
  /** Lignes de tableau de 36 px (compacte) ou 44 px (confortable). */
  densite: 'compacte' | 'confortable';
  /** Ouvrir le dossier suivant du même onglet après une décision. */
  enchainer: boolean;
  /** Raccourcis clavier actifs. */
  raccourcis: boolean;
  /** Une décision ou une première réponse prend le dossier ou le ticket. */
  priseAuto: boolean;
}

const DEFAUT: Preferences = {
  densite: 'confortable',
  enchainer: true,
  raccourcis: true,
  priseAuto: true,
};

const CLE = 'reborn-admin.preferences';
const abonnes = new Set<() => void>();
let cache: Preferences | null = null;

function lire(): Preferences {
  if (cache) return cache;
  let lu: Partial<Preferences> = {};
  try {
    const brut = window.localStorage.getItem(CLE);
    if (brut) lu = JSON.parse(brut) as Partial<Preferences>;
  } catch {
    lu = {};
  }
  cache = { ...DEFAUT, ...lu };
  return cache;
}

function ecrire(p: Preferences) {
  cache = p;
  try {
    window.localStorage.setItem(CLE, JSON.stringify(p));
  } catch {
    /* stockage indisponible : la préférence vaut pour la session */
  }
  abonnes.forEach((f) => f());
}

function abonner(f: () => void) {
  abonnes.add(f);
  return () => abonnes.delete(f);
}

/** Préférences courantes (valeurs par défaut côté serveur). */
export function usePreferences(): [Preferences, (patch: Partial<Preferences>) => void] {
  const prefs = useSyncExternalStore(abonner, lire, () => DEFAUT);
  const maj = useCallback((patch: Partial<Preferences>) => {
    ecrire({ ...lire(), ...patch });
  }, []);
  return [prefs, maj];
}

/** Lecture ponctuelle hors composant (gestionnaires de touches). */
export function preferences(): Preferences {
  if (typeof window === 'undefined') return DEFAUT;
  return lire();
}

/**
 * Seuils de retard (Q10) : au-delà, l'attente s'écrit en rouge dans la file
 * et dans le compteur du menu. Réglables par un administrateur quand la route
 * de réglage d'équipe existera ; d'ici là, valeurs de la fiche.
 */
export const SEUIL_ECRIT_MS = 48 * 3_600_000;
export const SEUIL_TICKET_MS = 24 * 3_600_000;
