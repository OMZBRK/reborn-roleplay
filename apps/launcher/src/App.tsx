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
  const loadSavedAccounts = useAuthStore((s) => s.loadSavedAccounts);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      // Hydrate la liste des comptes connus (carousel LoginScreen) en
      // parallele du resume session. Les deux operations sont
      // independantes : plugin-store local vs refresh token MS/API.
      const loadAccounts = loadSavedAccounts().catch(() => {
        // Premier boot : fichier saved-accounts.json absent → on demarre
        // avec savedAccounts = []. Pas d'erreur a remonter.
      });
      try {
        const session = await resumeSession();
        if (!cancelled) setSession(session);
      } catch {
        // Ignore : si auto-resume echoue, on tombe sur /login proprement.
      } finally {
        await loadAccounts;
        if (!cancelled) setResuming(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [setSession, setResuming, loadSavedAccounts]);

  // TitleBar overlay : positionnée absolute par-dessus le contenu. Le
  // contenu (sidebar/main/login/resume) remonte à y=0 et ses bgs/gradients
  // s'étendent naturellement jusqu'au top de la fenêtre. La TitleBar ne
  // crée plus de bande noire séparante — seuls les boutons minimize/close
  // flottent en haut à droite. Cf TitleBar.tsx pour le détail des zones
  // (spacer / drag-region / boutons).
  return (
    <div className="relative h-screen overflow-hidden bg-background">
      <div className="relative flex h-full min-h-0 flex-col">
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
      {/* TitleBar overlay : reste après le contenu pour être au-dessus dans
          le z-order (z-50 dans le composant). Visible sur tous les écrans
          (login, resume, authenticated). */}
      <TitleBar />
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
