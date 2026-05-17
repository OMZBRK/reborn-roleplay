import { Outlet } from "react-router";
import { Sidebar } from "./Sidebar";
import { DiagnosticToast } from "./DiagnosticToast";
import { UpdateChecker } from "./UpdateChecker";

// Layout des écrans authentifiés. La TitleBar est rendue plus haut dans
// App.tsx (visible aussi sur /login + pendant le resume), donc on n'a
// plus que sidebar + main ici.
export function AuthenticatedLayout() {
  return (
    <div className="flex h-full flex-1 overflow-hidden">
      <Sidebar />
      <main className="flex-1 overflow-y-auto bg-background">
        <Outlet />
      </main>
      <DiagnosticToast />
      <UpdateChecker />
    </div>
  );
}
