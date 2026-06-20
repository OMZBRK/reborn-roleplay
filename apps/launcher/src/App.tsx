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
import { Map } from "./routes/Map";
import { Mods } from "./routes/Mods";
import { Screenshots } from "./routes/Screenshots";
import { Tickets } from "./routes/Tickets";
import { Whitelist } from "./routes/Whitelist";
import { Character } from "./routes/Character";
import { AuthenticatedLayout } from "./components/AuthenticatedLayout";
import { ResumeSplash } from "./components/ResumeSplash";
import { WindowControls } from "./components/shell/WindowControls";
import { UpdateController } from "./components/system/UpdateController";
import { useAuthStore } from "./stores/auth-store";
import { resumeSession } from "./lib/auth";

// Largeur du rail sidebar pour positionner le spacer non-draggable au-dessus
// de la sidebar quand un user est authentifie (ses 32px du haut contiennent
// le logo R cliquable, on ne veut pas que la zone drag avale ce click).
const SIDEBAR_WIDTH = 72;
const DRAG_HEIGHT = 32;

export function App() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isResuming = useAuthStore((s) => s.isResuming);
  const setSession = useAuthStore((s) => s.setSession);
  const setResuming = useAuthStore((s) => s.setResuming);
  const loadSavedAccounts = useAuthStore((s) => s.loadSavedAccounts);

  useEffect(() => {
    let cancelled = false;
    (async () => {
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

  // Bande haute drag-region : transparente, 32px, full-width sur les ecrans
  // sans sidebar (login + resume), avec un spacer non-interactif de 72px
  // au-dessus de la sidebar sur les ecrans auth pour ne pas avaler le click
  // du logo R. WindowControls passent au-dessus en z-50 (cf composant).
  const showSidebarSpacer = isAuthenticated && !isResuming;

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
              <Route path="/mods" element={<Mods />} />
              <Route path="/map" element={<Map />} />
              <Route path="/screenshots" element={<Screenshots />} />
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

      {/* Drag-region + WindowControls : top-level, visibles sur tous les
          ecrans (login, resume, authenticated). */}
      <div
        className="pointer-events-none absolute inset-x-0 top-0 z-40 flex"
        style={{ height: DRAG_HEIGHT }}
        aria-hidden
      >
        {showSidebarSpacer && (
          <div style={{ width: SIDEBAR_WIDTH }} aria-hidden />
        )}
        <div className="drag-region pointer-events-auto flex-1" />
      </div>
      <WindowControls />

      {/* Updater monte au top-level pour poller des le boot, meme sur
          l'ecran de login / resume — sinon le user qui ne se connecte
          pas tout de suite ne voit jamais la modale de MAJ. La modale
          de UpdateController est elle-meme conditionnelle (rend null
          quand state.kind === 'idle'), donc zero overhead visuel
          tant qu'aucune MAJ n'est dispo. */}
      <UpdateController />
    </div>
  );
}

// TODO: implémenter ces routes (Boutique, Documentation, Mods, Carte) —
// actuellement montées sur ce placeholder en attendant les composants
// dédiés. /screenshots est désormais brancher sur le composant
// Screenshots (CHANTIER C).
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
