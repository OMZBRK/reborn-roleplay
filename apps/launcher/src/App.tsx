import { useEffect } from "react";
import { Navigate, Route, Routes } from "react-router";
import { Login } from "./routes/Login";
import { Home } from "./routes/Home";
import { Lore } from "./routes/Lore";
import { LoreDetail } from "./routes/LoreDetail";
import { Patchnotes } from "./routes/Patchnotes";
import { Rules } from "./routes/Rules";
import { RuleDetail } from "./routes/RuleDetail";
import { Settings } from "./routes/Settings";
import { Tickets } from "./routes/Tickets";
import { Whitelist } from "./routes/Whitelist";
import { Character } from "./routes/Character";
import { AuthenticatedLayout } from "./components/AuthenticatedLayout";
import { TitleBar } from "./components/TitleBar";
import { ResumeSplash } from "./components/ResumeSplash";
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

  // TitleBar liftée ici pour qu'elle soit toujours visible (login, resume,
  // authenticated). Les écrans en-dessous prennent le reste de la hauteur
  // via flex-1 + min-height: 0.
  return (
    <div className="flex h-screen flex-col bg-background">
      <TitleBar />
      <div className="relative flex min-h-0 flex-1 flex-col">
        {isResuming ? (
          <ResumeSplash />
        ) : (
          <Routes>
            <Route path="/login" element={<Login />} />

            <Route
              element={isAuthenticated ? <AuthenticatedLayout /> : <Navigate to="/login" replace />}
            >
              <Route path="/home" element={<Home />} />
              <Route path="/shop" element={<PlaceholderPage title="Boutique" />} />
              <Route path="/whitelist" element={<Whitelist />} />
              <Route path="/character" element={<Character />} />
              <Route path="/rules" element={<Rules />} />
              <Route path="/rules/:slug" element={<RuleDetail />} />
              <Route path="/lore" element={<Lore />} />
              <Route path="/lore/:slug" element={<LoreDetail />} />
              <Route path="/patchnotes" element={<Patchnotes />} />
              <Route path="/tickets" element={<Tickets />} />
              <Route path="/docs" element={<PlaceholderPage title="Documentation" />} />
              <Route path="/settings" element={<Settings />} />
            </Route>

            <Route path="*" element={<Navigate to={isAuthenticated ? "/home" : "/login"} replace />} />
          </Routes>
        )}
      </div>
    </div>
  );
}

// TODO: implémenter ces routes (Boutique, Documentation) — actuellement
// montées sur ce placeholder en attendant les composants dédiés.
function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="flex h-full items-center justify-center">
      <div className="text-center">
        <h1 className="text-3xl font-semibold">{title}</h1>
        <p className="mt-2 text-foreground-subtle">Cette page sera implémentée prochainement.</p>
      </div>
    </div>
  );
}
