import { useEffect } from "react";
import { Outlet } from "react-router";
import { Sidebar } from "./Sidebar";
import { DiagnosticToast } from "./DiagnosticToast";
import { OfflineBanner } from "./system/OfflineBanner";
import { GameCrashModal } from "./system/GameCrashModal";
import { CrashStub } from "./system/CrashStub";
import { DevHelpers } from "./system/DevHelpers";
import { startNetworkStatusPolling } from "../lib/network-status";
import { useBadgesStore } from "../stores/badges-store";

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
  // Polling /v1/health toutes les 12s pour piloter l'OfflineBanner.
  // Demarre quand l'utilisateur est connecte, stoppe au logout (cleanup).
  useEffect(() => {
    return startNetworkStatusPolling();
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
        <div className="flex-1 overflow-y-auto">
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
