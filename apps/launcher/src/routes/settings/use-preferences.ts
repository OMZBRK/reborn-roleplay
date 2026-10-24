import { useCallback, useEffect, useRef, useState } from "react";
import { getPrefs, setPrefs, type Preferences } from "../../lib/prefs";
import { formatIpcError } from "../../lib/errors";

// Lecture / ecriture des preferences du jeu (prefs.json cote Rust) pour
// l'onglet Jeu. Par rapport a l'ancien code :
//   - les ecritures sont serialisees (une seule a la fois, toujours avec la
//     derniere valeur) : deux clics rapides ne peuvent plus arriver dans le
//     desordre et ecraser le plus recent ;
//   - le curseur de RAM passe par un delai (debounceMs) au lieu d'ecrire a
//     chaque pixel ; une ecriture en attente part quand meme au demontage ;
//   - un echec d'ecriture n'efface plus tout l'onglet : message reel +
//     « Réessayer », les reglages restent affiches.

export type SaveState = "idle" | "saving" | "saved";

export type PreferencesEditor = {
  prefs: Preferences | null;
  loadError: string | null;
  loading: boolean;
  saveState: SaveState;
  saveError: string | null;
  reload: () => void;
  update: (patch: Partial<Preferences>, opts?: { debounceMs?: number }) => void;
  retrySave: () => void;
};

export function usePreferences(): PreferencesEditor {
  const [prefs, setPrefsState] = useState<Preferences | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saveState, setSaveState] = useState<SaveState>("idle");
  const [saveError, setSaveError] = useState<string | null>(null);

  const mounted = useRef(false);
  const latest = useRef<Preferences | null>(null);
  const debounce = useRef<number | null>(null);
  const savedTimer = useRef<number | null>(null);
  const queue = useRef<Promise<void>>(Promise.resolve());
  const pending = useRef(0);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const p = await getPrefs();
      if (!mounted.current) return;
      latest.current = p;
      setPrefsState(p);
    } catch (err) {
      // Pas de contexte ici : prefs_get / prefs_set le portent déjà
      // (« Impossible de lire tes préférences : … »).
      if (mounted.current) setLoadError(formatIpcError(err));
    } finally {
      if (mounted.current) setLoading(false);
    }
  }, []);

  // Une ecriture = la valeur la plus recente au moment ou elle part ; les
  // ecritures s'enchainent dans `queue` (jamais deux prefs_set en parallele).
  const persist = useCallback(() => {
    if (debounce.current !== null) {
      window.clearTimeout(debounce.current);
      debounce.current = null;
    }
    pending.current += 1;
    if (mounted.current) {
      setSaveState("saving");
      setSaveError(null);
    }
    queue.current = queue.current.then(async () => {
      const value = latest.current;
      let error: unknown = null;
      try {
        if (value) await setPrefs(value);
      } catch (err) {
        error = err;
      }
      pending.current -= 1;
      if (!mounted.current) return;
      if (error !== null) {
        setSaveState("idle");
        setSaveError(formatIpcError(error));
      } else if (pending.current === 0) {
        setSaveState("saved");
        if (savedTimer.current !== null) window.clearTimeout(savedTimer.current);
        savedTimer.current = window.setTimeout(() => {
          if (mounted.current) setSaveState("idle");
        }, 1500);
      }
    });
  }, []);

  const update = useCallback(
    (patch: Partial<Preferences>, opts?: { debounceMs?: number }) => {
      const current = latest.current;
      if (!current) return;
      const next = { ...current, ...patch };
      latest.current = next;
      setPrefsState(next);
      const delay = opts?.debounceMs ?? 0;
      if (delay > 0) {
        if (debounce.current !== null) window.clearTimeout(debounce.current);
        debounce.current = window.setTimeout(persist, delay);
      } else {
        persist();
      }
    },
    [persist],
  );

  useEffect(() => {
    mounted.current = true;
    void load();
    return () => {
      mounted.current = false;
      if (savedTimer.current !== null) window.clearTimeout(savedTimer.current);
      // Changement encore en attente (curseur relache juste avant de
      // quitter l'onglet) : on l'ecrit quand meme.
      if (debounce.current !== null) persist();
    };
  }, [load, persist]);

  return {
    prefs,
    loadError,
    loading,
    saveState,
    saveError,
    reload: () => void load(),
    update,
    retrySave: persist,
  };
}
