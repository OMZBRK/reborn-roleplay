import { useEffect } from "react";
import { Outlet } from "react-router";
import { Sidebar } from "./Sidebar";
import { DiagnosticToast } from "./DiagnosticToast";
import { UpdateController } from "./system/UpdateController";
import { OfflineBanner } from "./system/OfflineBanner";
import { GameCrashModal } from "./system/GameCrashModal";
import { CrashStub } from "./system/CrashStub";
import { DevHelpers } from "./system/DevHelpers";
import { startNetworkStatusPolling } from "../lib/network-status";

// AppShell : sidebar 72px + main 1fr. WindowControls + drag-region sont
// rendus depuis App.tsx (top-level).
//
// Modals systeme et banner offline montes ici car ils n'ont de sens que
// pour un utilisateur connecte :
//   - OfflineBanner : pilote par network-store (ping API /health 12s)
//   - UpdateController : useUpdater + UpdateModal (4 etats)
//   - GameCrashModal : pilote par crash-store (stub via window.__triggerCrash
//     en dev, vrai branchement Tauri events a venir)
//   - CrashStub : pose window.__triggerCrash en dev uniquement, no-op en
//     release. Composant invisible.
//   - DiagnosticToast : toasts in-app du LogAnalyzer pendant le launch.
export function AuthenticatedLayout() {
  // Polling /v1/health toutes les 12s pour piloter l'OfflineBanner.
  // Demarre quand l'utilisateur est connecte, stoppe au logout (cleanup).
  useEffect(() => {
    return startNetworkStatusPolling();
  }, []);

  return (
    <div className="flex h-full w-full overflow-hidden">
      <Sidebar />
      <main className="relative flex flex-1 flex-col overflow-hidden bg-background">
        <OfflineBanner />
        <div className="flex-1 overflow-y-auto">
          <Outlet />
        </div>
      </main>
      <DiagnosticToast />
      <UpdateController />
      <GameCrashModal />
      <CrashStub />
      <DevHelpers />
    </div>
  );
}
