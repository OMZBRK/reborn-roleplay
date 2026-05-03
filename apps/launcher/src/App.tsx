import { useEffect } from "react";
import { Navigate, Route, Routes } from "react-router";
import { Loader2 } from "lucide-react";
import { Login } from "./routes/Login";
import { Home } from "./routes/Home";
import { Lore } from "./routes/Lore";
import { Patchnotes } from "./routes/Patchnotes";
import { Rules } from "./routes/Rules";
import { Settings } from "./routes/Settings";
import { Tickets } from "./routes/Tickets";
import { Whitelist } from "./routes/Whitelist";
import { AuthenticatedLayout } from "./components/AuthenticatedLayout";
import { useAuthStore } from "./stores/auth-store";
import { resumeSession } from "./lib/auth";

export function App() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isResuming = useAuthStore((s) => s.isResuming);
  const setSession = useAuthStore((s) => s.setSession);
  const setResuming = useAuthStore((s) => s.setResuming);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const session = await resumeSession();
        if (!cancelled) setSession(session);
      } catch {
        // Ignore : si auto-resume echoue, on tombe sur /login proprement.
      } finally {
        if (!cancelled) setResuming(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [setSession, setResuming]);

  if (isResuming) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <Loader2 className="h-6 w-6 animate-spin text-foreground-subtle" />
      </div>
    );
  }

  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route
        element={isAuthenticated ? <AuthenticatedLayout /> : <Navigate to="/login" replace />}
      >
        <Route path="/home" element={<Home />} />
        <Route path="/shop" element={<PlaceholderPage title="Boutique" />} />
        <Route path="/whitelist" element={<Whitelist />} />
        <Route path="/rules" element={<Rules />} />
        <Route path="/lore" element={<Lore />} />
        <Route path="/patchnotes" element={<Patchnotes />} />
        <Route path="/tickets" element={<Tickets />} />
        <Route path="/docs" element={<PlaceholderPage title="Documentation" />} />
        <Route path="/settings" element={<Settings />} />
      </Route>

      <Route path="*" element={<Navigate to={isAuthenticated ? "/home" : "/login"} replace />} />
    </Routes>
  );
}

function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="flex h-full items-center justify-center">
      <div className="text-center">
        <h1 className="text-3xl font-semibold">{title}</h1>
        <p className="mt-2 text-foreground-subtle">Cette page sera implementee prochainement.</p>
      </div>
    </div>
  );
}
