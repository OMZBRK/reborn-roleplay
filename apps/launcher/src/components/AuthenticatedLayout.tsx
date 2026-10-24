import { Suspense, useEffect, useRef } from "react";
import { Outlet, useLocation } from "react-router";
import { Sidebar } from "./Sidebar";
import { DiagnosticToast } from "./DiagnosticToast";
import { OfflineBanner } from "./system/OfflineBanner";
import { GameCrashModal } from "./system/GameCrashModal";
import { CrashStub } from "./system/CrashStub";
import { DevHelpers } from "./system/DevHelpers";
import { RouteErrorBoundary, RouteFallback } from "./shell/RouteBoundary";
import { startNetworkStatusPolling } from "../lib/network-status";
import { useBadgesStore } from "../stores/badges-store";
import { initGameStore, useGameRunning } from "../stores/game-store";
import { usePolling } from "../hooks/use-polling";
import { refreshAccess, refreshMe } from "../lib/auth";
import { useAuthStore } from "../stores/auth-store";
import { useWhitelistStore } from "../stores/whitelist-store";

// L'access token API vit 15 min : on le fait tourner toutes les 10 min, et
// au retour de focus si la fenêtre est restée en arrière-plan plus longtemps
// (les timers WebView sont ralentis quand la fenêtre est masquée).
const ACCESS_REFRESH_MS = 10 * 60_000;
// Compteurs non-lus (cloche + sidebar).
const BADGES_REFRESH_MS = 30_000;

// AppShell : rail 232px + main 1fr. WindowControls + drag-region sont
// rendus depuis App.tsx (top-level).
//
// Modals système et banner offline montés ici car ils n'ont de sens que
// pour un utilisateur connecté :
//   - OfflineBanner : piloté par network-store (ping API /health 12s)
//   - GameCrashModal : piloté par crash-store, alimenté par l'event Tauri
//     game:crashed (et window.__reborn.crash en dev via DevHelpers)
//   - CrashStub : pose le listener game:crashed -> crash-store. Composant
//     invisible.
//   - DiagnosticToast : toasts in-app du LogAnalyzer pendant le launch.
//
// Pendant une partie (game-store.running), les sondages périodiques sont en
// pause (launcher réduit : pas de réseau ni de CPU pris au jeu) et un tour
// part dès la fin de la partie. Seul le rafraîchissement du jeton d'accès
// continue : une partie peut durer plus longtemps que le jeton.
//
// UpdateController est rendu top-level dans App.tsx pour poller dès le
// boot même sur l'écran de login (sinon un user qui ne se connecte pas
// rate la modale de MAJ).
export function AuthenticatedLayout() {
  const { pathname } = useLocation();
  const gameRunning = useGameRunning();
  const mainRef = useRef<HTMLDivElement>(null);

  // État « jeu en cours » + collecte des logs (compteur de références :
  // CrashStub l'appelle aussi, sans doublon d'écouteurs).
  useEffect(() => initGameStore(), []);

  // <html data-game="running"> fige les animations CSS en boucle pendant la
  // partie (cf globals.css « Jeu en cours »).
  useEffect(() => {
    const root = document.documentElement;
    if (gameRunning) root.dataset.game = "running";
    else delete root.dataset.game;
    return () => {
      delete root.dataset.game;
    };
  }, [gameRunning]);

  // Ping /v1/health toutes les 12s pour piloter l'OfflineBanner. Coupé
  // pendant une partie, relancé (avec un ping immédiat) à la fin.
  useEffect(() => {
    if (gameRunning) return;
    return startNetworkStatusPolling();
  }, [gameRunning]);

  // Rotation du jeton d'accès : jamais en pause (cf en-tête).
  const lastRefreshAt = useRef(Date.now());
  const refreshNow = usePolling(
    async () => {
      lastRefreshAt.current = Date.now();
      try {
        const fresh = await refreshAccess();
        if (fresh) useAuthStore.getState().setUser(fresh);
      } catch (e) {
        // Sans conséquence immédiate : la prochaine requête refusée
        // renverra un « Session expirée : reconnecte-toi » explicite.
        console.warn("[auth] rafraîchissement du jeton d'accès impossible :", e);
      }
    },
    ACCESS_REFRESH_MS,
    { pauseWhileGame: false, immediate: false },
  );
  useEffect(() => {
    const onFocus = () => {
      if (Date.now() - lastRefreshAt.current > ACCESS_REFRESH_MS) refreshNow();
    };
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [refreshNow]);

  // Whitelist acceptée pendant la session : le rôle (PLAYER → membre) n'est
  // relu nulle part ailleurs, « Whitelist requise » resterait affiché
  // jusqu'au redémarrage. Un seul appel par navigation tant que l'API
  // renvoie encore PLAYER (pas de boucle : le rôle ne change pas).
  const whitelistStatus = useWhitelistStore((s) => s.status);
  const role = useAuthStore((s) => s.user?.role);
  useEffect(() => {
    if (whitelistStatus !== "accepted" || role !== "PLAYER") return;
    let cancelled = false;
    refreshMe()
      .then((fresh) => {
        if (!cancelled && fresh) useAuthStore.getState().setUser(fresh);
      })
      .catch((e: unknown) => console.warn("[auth] relecture du compte impossible :", e));
    return () => {
      cancelled = true;
    };
  }, [whitelistStatus, role, pathname]);

  // Compteurs non-lus : au montage puis toutes les 30s (erreurs gérées
  // par le store : on garde la dernière valeur connue).
  usePolling(() => useBadgesStore.getState().refresh(), BADGES_REFRESH_MS);

  return (
    <div className="reborn-app-shell flex h-full w-full overflow-hidden">
      <button
        type="button"
        className="reborn-skip-link"
        onClick={() => mainRef.current?.focus()}
      >
        Aller au contenu
      </button>
      <Sidebar />
      <main className="relative flex flex-1 flex-col overflow-hidden bg-transparent">
        <OfflineBanner />
        {/* Suspense et garde d'erreur AU-DESSUS du conteneur keyé : pendant
            une navigation (transition React Router), la page courante reste
            affichée jusqu'à ce que le chunk de la suivante soit prêt, au lieu
            d'un écran vide. Une page qui plante n'emporte que la zone de
            contenu, la sidebar reste utilisable. */}
        <RouteErrorBoundary resetKey={pathname}>
          <Suspense fallback={<RouteFallback />}>
            {/* key = pathname : chaque page repart en haut (le conteneur de
                scroll était partagé, on atterrissait à mi-page en changeant
                d'onglet) + fondu d'entrée court. Opacité seule : pas de
                transform qui piégerait les position:fixed des pages. */}
            <div
              key={pathname}
              ref={mainRef}
              id="reborn-main"
              tabIndex={-1}
              className="reborn-route-view flex-1 overflow-y-auto"
            >
              <Outlet />
            </div>
          </Suspense>
        </RouteErrorBoundary>
      </main>
      <DiagnosticToast />
      <GameCrashModal />
      <CrashStub />
      <DevHelpers />
    </div>
  );
}
