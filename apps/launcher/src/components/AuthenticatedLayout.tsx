import { useEffect } from "react";
import { Outlet, useLocation } from "react-router";
import { Sidebar } from "./Sidebar";
import { DiagnosticToast } from "./DiagnosticToast";
import { OfflineBanner } from "./system/OfflineBanner";
import { GameCrashModal } from "./system/GameCrashModal";
import { CrashStub } from "./system/CrashStub";
import { DevHelpers } from "./system/DevHelpers";
import { startNetworkStatusPolling } from "../lib/network-status";
import { useBadgesStore } from "../stores/badges-store";
import { refreshAccess } from "../lib/auth";

// L'access token API vit 15 min : on le fait tourner toutes les 10 min, et
// au retour de focus si la fenetre est restee en arriere-plan plus longtemps
// (les timers WebView sont ralentis quand la fenetre est masquee).
const ACCESS_REFRESH_MS = 10 * 60_000;

// AppShell : sidebar 72px + main 1fr. WindowControls + drag-region sont
// rendus depuis App.tsx (top-level).
//
// Modals systeme et banner offline montes ici car ils n'ont de sens que
// pour un utilisateur connecte :
//   - OfflineBanner : pilote par network-store (ping API /health 12s)
//   - GameCrashModal : pilote par crash-store, alimente par l'event Tauri
//     game:crashed (et window.__reborn.crash en dev via DevHelpers)
//   - CrashStub : pose le listener game:crashed -> crash-store. Composant
//     invisible.
//   - DiagnosticToast : toasts in-app du LogAnalyzer pendant le launch.
//
// UpdateController est rendu top-level dans App.tsx pour poller des le
// boot meme sur l'ecran de login (sinon un user qui ne se connecte pas
// rate la modale de MAJ).
export function AuthenticatedLayout() {
  const { pathname } = useLocation();

  // Polling /v1/health toutes les 12s pour piloter l'OfflineBanner.
  // Demarre quand l'utilisateur est connecte, stoppe au logout (cleanup).
  useEffect(() => {
    return startNetworkStatusPolling();
  }, []);

  useEffect(() => {
    let last = Date.now();
    const tick = () => {
      last = Date.now();
      refreshAccess().catch((e) => console.warn("[auth] refresh access failed:", e));
    };
    const id = window.setInterval(tick, ACCESS_REFRESH_MS);
    const onFocus = () => {
      if (Date.now() - last > ACCESS_REFRESH_MS) tick();
    };
    window.addEventListener("focus", onFocus);
    return () => {
      window.clearInterval(id);
      window.removeEventListener("focus", onFocus);
    };
  }, []);

  // Compteurs non-lus (cloche + sidebar) : refresh au montage puis toutes
  // les 30s tant que l'utilisateur est connecté.
  useEffect(() => {
    const refresh = useBadgesStore.getState().refresh;
    void refresh();
    const id = window.setInterval(() => void refresh(), 30_000);
    return () => window.clearInterval(id);
  }, []);

  return (
    <div className="reborn-app-shell flex h-full w-full overflow-hidden">
      <Sidebar />
      <main className="relative flex flex-1 flex-col overflow-hidden bg-transparent">
        <OfflineBanner />
        {/* key = pathname : chaque page repart en haut (le conteneur de
            scroll etait partage, on atterrissait a mi-page en changeant
            d'onglet) + fondu d'entree court. Opacite seule : pas de
            transform qui piegerait les position:fixed des pages. */}
        <div key={pathname} className="reborn-route-view flex-1 overflow-y-auto">
          <Outlet />
        </div>
      </main>
      <DiagnosticToast />
      <GameCrashModal />
      <CrashStub />
      <DevHelpers />
    </div>
  );
}
