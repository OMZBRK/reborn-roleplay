import { useCallback, useEffect, useLayoutEffect, useRef } from "react";
import { useGameStore } from "../stores/game-store";

// Sondage periodique propre, partage par tout le front :
//   - pas de chevauchement : le tour suivant est programme APRES la fin du
//     precedent (setTimeout en chaine, pas setInterval) ;
//   - pause pendant une partie (pauseWhileGame, par defaut) : le launcher
//     est reduit, inutile de prendre du reseau et du CPU au jeu ; a la fin
//     de la partie, un tour part immediatement (donnees perimees). Seule la
//     cadence est suspendue : le premier chargement et trigger() partent
//     quand meme ;
//   - annulation : un AbortSignal est passe a la fonction et avorte a l'arret
//     ou a la mise en pause ; aucun tour ne repart apres l'arret.
// Les erreurs doivent etre gerees par la fonction ; celles qui s'echappent
// sont seulement journalisees, le sondage continue.

export type PollFn = (signal: AbortSignal) => unknown;

export type PollingOptions = {
  /** Faux = sondage arrete (defaut : vrai). */
  enabled?: boolean;
  /** Met le sondage en pause tant qu'une partie est en cours (defaut : vrai). */
  pauseWhileGame?: boolean;
  /** Lance un premier tour tout de suite (defaut : vrai). */
  immediate?: boolean;
};

export type PollingHandle = {
  /** Arrete definitivement ce sondage. */
  stop: () => void;
  /** Lance un tour tout de suite (ex. apres un envoi, meme en pause) et recale le minuteur. */
  trigger: () => void;
};

/** Version hors React (modules, stores). Le hook usePolling s'appuie dessus. */
export function startPolling(
  fn: PollFn,
  intervalMs: number,
  { pauseWhileGame = true, immediate = true }: Omit<PollingOptions, "enabled"> = {},
): PollingHandle {
  let stopped = false;
  let paused = pauseWhileGame && useGameStore.getState().running;
  let timer: number | null = null;
  let controller: AbortController | null = null;
  let inFlight = false;
  let rerun = false;

  const clear = () => {
    if (timer !== null) {
      window.clearTimeout(timer);
      timer = null;
    }
  };
  const schedule = () => {
    clear();
    if (!stopped && !paused) timer = window.setTimeout(() => void run(), intervalMs);
  };
  // force : tour explicite (premier chargement, trigger()) qui passe outre la
  // pause ; seule la cadence periodique est suspendue pendant une partie.
  let rerunForced = false;
  const run = async (force = false) => {
    timer = null;
    if (stopped || (paused && !force)) return;
    if (inFlight) {
      rerun = true;
      rerunForced = rerunForced || force;
      return;
    }
    inFlight = true;
    const ctrl = new AbortController();
    controller = ctrl;
    try {
      await fn(ctrl.signal);
    } catch (e) {
      if (!stopped && !ctrl.signal.aborted) console.warn("[polling] tour en échec :", e);
    } finally {
      inFlight = false;
      controller = null;
    }
    if (rerun) {
      const forced = rerunForced;
      rerun = false;
      rerunForced = false;
      void run(forced);
    } else {
      schedule();
    }
  };

  const unsubscribe = pauseWhileGame
    ? useGameStore.subscribe((s, prev) => {
        if (s.running === prev.running) return;
        paused = s.running;
        if (paused) {
          clear();
          rerun = false;
          rerunForced = false;
          controller?.abort();
        } else {
          void run();
        }
      })
    : () => {};

  // Le premier chargement part meme pendant une partie (launcher rouvert
  // par Alt+Tab, onGameStart = "stay") : sans lui, l'ecran resterait sur son
  // indicateur de chargement jusqu'a la fin de la partie.
  if (immediate) void run(true);
  else schedule();

  return {
    stop: () => {
      if (stopped) return;
      stopped = true;
      clear();
      controller?.abort();
      unsubscribe();
    },
    trigger: () => {
      // Action explicite (« Réessayer », rafraichissement apres envoi) :
      // elle part meme pendant une partie.
      if (stopped) return;
      clear();
      void run(true);
    },
  };
}

/**
 * Sondage periodique dans un composant. `fn` est toujours la derniere
 * version (pas besoin de useCallback). Renvoie `trigger()` pour forcer un
 * tour immediat.
 *
 *   usePolling(async (signal) => { const s = await fetchStatus(); if (!signal.aborted) setStatus(s); }, 20_000);
 */
export function usePolling(
  fn: PollFn,
  intervalMs: number,
  { enabled = true, pauseWhileGame = true, immediate = true }: PollingOptions = {},
): () => void {
  const fnRef = useRef(fn);
  useLayoutEffect(() => {
    fnRef.current = fn;
  });
  const handleRef = useRef<PollingHandle | null>(null);

  useEffect(() => {
    if (!enabled || !(intervalMs > 0)) return;
    const handle = startPolling((signal) => fnRef.current(signal), intervalMs, {
      pauseWhileGame,
      immediate,
    });
    handleRef.current = handle;
    return () => {
      handle.stop();
      if (handleRef.current === handle) handleRef.current = null;
    };
  }, [enabled, intervalMs, pauseWhileGame, immediate]);

  return useCallback(() => handleRef.current?.trigger(), []);
}
