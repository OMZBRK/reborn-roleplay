import { Outlet } from "react-router";
import { Sidebar } from "./Sidebar";
import { TitleBar } from "./TitleBar";
import { DiagnosticToast } from "./DiagnosticToast";

export function AuthenticatedLayout() {
  return (
    <div className="flex h-screen flex-col">
      <TitleBar />
      <div className="flex flex-1 overflow-hidden">
        <Sidebar />
        <main className="flex-1 overflow-y-auto bg-background">
          <Outlet />
        </main>
      </div>
      <DiagnosticToast />
    </div>
  );
}
