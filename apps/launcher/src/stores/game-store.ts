import { create } from "zustand";
import { onTauriEvent, type UnlistenFn } from "../lib/tauri";
import { getGameStatus } from "../lib/launcher";
import { initLogsFeed } from "./logs-store";

// Etat « jeu en cours » partage par tout le front. Alimente uniquement par
// les evenements du backend (game:started / game:window-ready / game:exited
// / game:crashed) via initGameStore(), appele une fois depuis le layout
// authentifie. Sert notamment a mettre en pause les sondages periodiques
// (usePolling) pendant que Minecraft tourne : le launcher est alors reduit
// et chaque requete prendrait de la bande passante et du CPU au jeu.

type GameStoreState = {
  /** True de game:started jusqu'a game:exited / game:crashed. */
  running: boolean;
  /** True des que la fenetre Minecraft est reellement ouverte. */
  windowReady: boolean;
  /** PID de la JVM en cours, null sinon. */
  pid: number | null;
  /** Horodatage (ms) du dernier game:started. */
  startedAt: number | null;
  /** Code de sortie de la derniere partie (-1 si illisible), null avant. */
  lastExitCode: number | null;
};

export const useGameStore = create<GameStoreState>(() => ({
  running: false,
  windowReady: false,
  pid: null,
  startedAt: null,
  lastExitCode: null,
}));

/** True tant qu'une partie est en cours. */
export function useGameRunning(): boolean {
  return useGameStore((s) => s.running);
}

/** True quand la fenetre Minecraft est ouverte (fin de l'ecran de chargement). */
export function useGameWindowReady(): boolean {
  return useGameStore((s) => s.windowReady);
}

// Compteur d'utilisateurs : initGameStore() peut etre appele plusieurs fois
// (StrictMode monte/demonte/remonte les effets en dev, logout/login
// remonte le layout) sans jamais empiler d'ecouteurs.
let users = 0;
let teardown: (() => void) | null = null;

function subscribe(): () => void {
  let cancelled = false;
  const unlistens: UnlistenFn[] = [];
  const keep = (p: Promise<UnlistenFn>) =>
    p
      .then((fn) => (cancelled ? fn() : unlistens.push(fn)))
      .catch((e: unknown) => console.warn("[game-store] écoute impossible :", e));

  // Un event recu apres l'abonnement est plus frais que la resynchronisation.
  let eventSeen = false;
  const ended = (code: number | null) =>
    useGameStore.setState((s) => ({
      running: false,
      windowReady: false,
      pid: null,
      lastExitCode: code ?? s.lastExitCode,
    }));

  keep(
    onTauriEvent("game:started", (g) => {
      eventSeen = true;
      useGameStore.setState({
        running: true,
        windowReady: false,
        pid: g?.pid ?? null,
        startedAt: Date.now(),
      });
    }),
  );
  keep(
    onTauriEvent("game:window-ready", () => {
      eventSeen = true;
      useGameStore.setState({ running: true, windowReady: true });
    }),
  );
  keep(
    onTauriEvent("game:exited", (code) => {
      eventSeen = true;
      ended(typeof code === "number" ? code : null);
    }),
  );
  keep(
    onTauriEvent("game:crashed", (c) => {
      eventSeen = true;
      ended(c?.exitCode ?? null);
    }),
  );

  // WebView rechargee en pleine partie, ou layout remonte apres une partie
  // terminee pendant qu'il etait demonte (deconnexion, changement de compte) :
  // les events emis entre-temps sont perdus, on relit l'etat du backend dans
  // les deux sens (un event arrive entre-temps a la priorite).
  void getGameStatus()
    .then((st) => {
      if (cancelled || eventSeen || !st) return;
      const local = useGameStore.getState().running;
      if (st.running) {
        if (!local) useGameStore.setState({ running: true, windowReady: true, pid: st.pid ?? null });
      } else if (local) {
        ended(null);
      }
    })
    .catch((e: unknown) => console.warn("[game-store] état du jeu illisible :", e));

  return () => {
    cancelled = true;
    for (const fn of unlistens.splice(0)) fn();
  };
}

/**
 * Branche le store sur les evenements du jeu. A appeler une fois depuis le
 * layout authentifie : `useEffect(() => initGameStore(), [])`. Renvoie la
 * fonction de liberation (le dernier utilisateur retire les ecouteurs).
 * Demarre aussi la collecte des logs du jeu (stores/logs-store.ts).
 */
export function initGameStore(): () => void {
  users += 1;
  if (users === 1) {
    const stopGame = subscribe();
    const stopLogs = initLogsFeed();
    teardown = () => {
      stopGame();
      stopLogs();
      // Plus personne n'ecoute game:exited : on ne garde pas un « en jeu »
      // qui pourrait ne jamais redescendre. La resynchronisation du prochain
      // montage le repose si une partie tourne vraiment.
      useGameStore.setState({ running: false, windowReady: false, pid: null });
    };
  }
  let released = false;
  return () => {
    if (released) return;
    released = true;
    users -= 1;
    if (users === 0 && teardown) {
      teardown();
      teardown = null;
    }
  };
}
