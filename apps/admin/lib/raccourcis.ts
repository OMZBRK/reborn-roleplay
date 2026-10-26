'use client';

import { useEffect, useRef } from 'react';
import { fenetreOuverte } from '@/components/ui/Dialog';
import { preferences } from './preferences';

/**
 * Raccourcis clavier d'une page (PANEL_V2 §11). Inactifs dans un champ de
 * saisie (sauf les combinaisons avec Ctrl, déclarées `ctrl`), quand une
 * fenêtre ou un tiroir est ouvert, et quand le staff les a coupés dans
 * Réglages › Préférences. Un geste destructif n'a jamais de touche seule.
 */
export interface Raccourci {
  /** `KeyboardEvent.key` attendu (« v », « 1 », « Enter », « ArrowDown », « ? »). */
  touche: string;
  action: (e: KeyboardEvent) => void;
  /** Avec Ctrl (ou Cmd) : actif même dans un champ. */
  ctrl?: boolean;
  maj?: boolean;
}

export function dansUnChamp(cible: EventTarget | null): boolean {
  const el = cible as HTMLElement | null;
  if (!el) return false;
  const tag = el.tagName;
  return (
    tag === 'INPUT' ||
    tag === 'TEXTAREA' ||
    tag === 'SELECT' ||
    el.isContentEditable === true
  );
}

export function useRaccourcis(liste: Raccourci[], actif = true) {
  const ref = useRef(liste);
  ref.current = liste;

  useEffect(() => {
    if (!actif) return;
    function surTouche(e: KeyboardEvent) {
      if (e.defaultPrevented || e.isComposing) return;
      if (!preferences().raccourcis) return;
      if (fenetreOuverte()) return;
      const ctrl = e.ctrlKey || e.metaKey;
      for (const r of ref.current) {
        if (e.key.toLowerCase() !== r.touche.toLowerCase()) continue;
        if (!!r.ctrl !== ctrl) continue;
        if (r.maj !== undefined && r.maj !== e.shiftKey) continue;
        if (e.altKey) continue;
        if (!r.ctrl && dansUnChamp(e.target)) continue;
        e.preventDefault();
        r.action(e);
        return;
      }
    }
    window.addEventListener('keydown', surTouche);
    return () => window.removeEventListener('keydown', surTouche);
  }, [actif]);
}
