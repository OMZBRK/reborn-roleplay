import { lazy, Suspense, useEffect, type ComponentType } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router";
import { MotionConfig } from "framer-motion";
import { AuthenticatedLayout } from "./components/AuthenticatedLayout";
import { ResumeSplash } from "./components/ResumeSplash";
import { WindowControls } from "./components/shell/WindowControls";
import { RouteErrorBoundary, RouteFallback } from "./components/shell/RouteBoundary";
import { UpdateController } from "./components/system/UpdateController";
import { useAuthStore } from "./stores/auth-store";
import { useSettingsStore } from "./stores/settings-store";
import { resumeSession } from "./lib/auth";
import { formatIpcError, isIpcErrorKind } from "./lib/errors";

// Pages chargées à la demande : le bundle de démarrage ne contient plus que
// la coque (layout, sidebar, splash). Chaque page a son chunk ; `preload`
// lance son import sans la rendre. Login + Home sont préchargés pendant la
// reprise de session (l'un des deux s'affiche juste après), le reste quand
// le navigateur est au repos, pour que chaque navigation soit instantanée.
type PageModule<K extends string> = Record<K, ComponentType>;

function page<M extends PageModule<K>, K extends string>(load: () => Promise<M>, name: K) {
  let pending: Promise<{ default: ComponentType }> | null = null;
  // Promesse partagée entre preload() et React.lazy : un seul import.
  const factory = () => (pending ??= load().then((m) => ({ default: m[name] })));
  return Object.assign(lazy(factory), { preload: factory });
}

const Login = page(() => import("./routes/Login"), "Login");
const Home = page(() => import("./routes/Home"), "Home");
const Whitelist = page(() => import("./routes/Whitelist"), "Whitelist");
const Rp = page(() => import("./routes/Rp"), "Rp");
const Mods = page(() => import("./routes/Mods"), "Mods");
const Rules = page(() => import("./routes/Rules"), "Rules");
const RuleDetail = page(() => import("./routes/RuleDetail"), "RuleDetail");
const Lore = page(() => import("./routes/Lore"), "Lore");
const LoreDetail = page(() => import("./routes/LoreDetail"), "LoreDetail");
const Patchnotes = page(() => import("./routes/Patchnotes"), "Patchnotes");
const Tickets = page(() => import("./routes/Tickets"), "Tickets");
const Settings = page(() => import("./routes/Settings"), "Settings");

const IDLE_PAGES = [Whitelist, Rp, Mods, Rules, RuleDetail, Lore, LoreDetail, Patchnotes, Tickets, Settings];

/** Précharge les pages restantes quand le fil principal est libre. */
function preloadWhenIdle(): () => void {
  const run = () => {
    for (const p of IDLE_PAGES) {
      p.preload().catch((e: unknown) => console.warn("[ui] préchargement d'une page impossible :", e));
    }
  };
  if (typeof window.requestIdleCallback === "function") {
    const id = window.requestIdleCallback(run, { timeout: 4000 });
    return () => window.cancelIdleCallback(id);
  }
  const id = window.setTimeout(run, 1500);
  return () => window.clearTimeout(id);
}

// Largeur du rail sidebar pour positionner le spacer non-draggable au-dessus
// de la sidebar quand un user est authentifié (ses 32px du haut contiennent
// le lockup de marque cliquable, on ne veut pas que la zone drag avale ce
// clic). Doit matcher la largeur .reborn-rail dans globals.css (232px).
const SIDEBAR_WIDTH = 232;
const DRAG_HEIGHT = 32;

export function App() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isResuming = useAuthStore((s) => s.isResuming);
  const user = useAuthStore((s) => s.user);
  const discordGateSkipped = useAuthStore((s) => s.discordGateSkipped);
  const location = useLocation();
  const setSession = useAuthStore((s) => s.setSession);
  const setResuming = useAuthStore((s) => s.setResuming);
  const loadSavedAccounts = useAuthStore((s) => s.loadSavedAccounts);
  const font = useSettingsStore((s) => s.ui.font);

  // Applique la police d'affichage choisie (pref UI localStorage) sur <html>.
  // Un seul attribut data-font pilote le swap CSS de --font-sans (cf
  // globals.css :root[data-font="arcade"]). S'exécute au boot (restauration)
  // et à chaque changement du toggle (application live).
  useEffect(() => {
    const root = document.documentElement;
    if (font === "arcade") root.dataset.font = "arcade";
    else delete root.dataset.font;
  }, [font]);

  useEffect(() => {
    let cancelled = false;
    // Une des deux pages s'affiche dès la fin de la reprise : on charge
    // leur code pendant que le splash est à l'écran.
    void Login.preload().catch(() => {});
    void Home.preload().catch(() => {});
    (async () => {
      const loadAccounts = loadSavedAccounts().catch(() => {
        // Premier boot : fichier saved-accounts.json absent → on démarre
        // avec savedAccounts = []. Pas d'erreur à remonter.
      });
      try {
        const session = await resumeSession();
        if (!cancelled) setSession(session);
      } catch (e) {
        // Reprise impossible (session expirée, API injoignable…) : on tombe
        // sur /login, avec la vraie cause affichée à l'étape Microsoft
        // (sinon le joueur referait tout l'OAuth pour retomber dessus).
        // Silence seulement quand il n'y a simplement rien à reprendre.
        console.warn("[auth] reprise de session impossible :", e);
        if (!cancelled && !isIpcErrorKind(e, "no_stored_credentials", "stored_credentials_expired")) {
          useAuthStore.getState().setError(formatIpcError(e, "Reprise de ta session impossible"));
        }
      } finally {
        await loadAccounts;
        if (!cancelled) setResuming(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [setSession, setResuming, loadSavedAccounts]);

  // Bande haute drag-region : transparente, 32px, full-width sur les écrans
  // sans sidebar (login + resume), avec un spacer non-interactif au-dessus
  // de la sidebar sur les écrans auth pour ne pas avaler le clic du logo R.
  // WindowControls passent au-dessus en z-50 (cf composant).
  // Écran d'accueil (Player ID, cf docs/pilotage/DECISIONS.md) : comptes
  // Microsoft ET Discord requis. Tant que Discord n'est pas lié, le user
  // authentifié reste sur /login (étape 2 de la carte d'accueil).
  const needsDiscord = isAuthenticated && !user?.discord && !discordGateSkipped;
  const canEnter = isAuthenticated && !needsDiscord;
  const showSidebarSpacer = canEnter && !isResuming && location.pathname !== "/login";

  // Une fois dans le launcher, le reste des pages se charge au repos.
  useEffect(() => {
    if (!canEnter || isResuming) return;
    return preloadWhenIdle();
  }, [canEnter, isResuming]);

  return (
    // reducedMotion="user" : framer-motion coupe ses animations de
    // déplacement / échelle quand Windows demande moins d'animations.
    <MotionConfig reducedMotion="user">
      <div className="relative h-screen overflow-hidden bg-background">
        <div className="relative flex h-full min-h-0 flex-col">
          {isResuming ? (
            <ResumeSplash />
          ) : (
            <RouteErrorBoundary resetKey={location.pathname}>
              <Suspense fallback={<RouteFallback />}>
                <Routes>
                  <Route path="/login" element={<Login />} />

                  <Route
                    element={canEnter ? <AuthenticatedLayout /> : <Navigate to="/login" replace />}
                  >
                    <Route path="/home" element={<Home />} />
                    <Route path="/shop" element={<PlaceholderPage title="Boutique" />} />
                    <Route path="/whitelist" element={<Whitelist />} />
                    <Route path="/rp" element={<Rp />} />
                    {/* Anciennes adresses : le personnage, la carte, les
                        captures et le feed vivent dans l'onglet RP (même
                        barre d'onglets, même garde whitelist). */}
                    <Route path="/character" element={<Navigate to="/rp?tab=character" replace />} />
                    <Route path="/map" element={<Navigate to="/rp?tab=map" replace />} />
                    <Route path="/screenshots" element={<Navigate to="/rp?tab=screenshots" replace />} />
                    <Route path="/feed" element={<Navigate to="/rp?tab=feed" replace />} />
                    <Route path="/mods" element={<Mods />} />
                    <Route path="/rules" element={<Rules />} />
                    <Route path="/rules/:slug" element={<RuleDetail />} />
                    <Route path="/lore" element={<Lore />} />
                    <Route path="/lore/:slug" element={<LoreDetail />} />
                    <Route path="/patchnotes" element={<Patchnotes />} />
                    <Route path="/tickets" element={<Tickets />} />
                    <Route path="/docs" element={<PlaceholderPage title="Documentation" />} />
                    <Route path="/settings" element={<Settings />} />
                  </Route>

                  <Route path="*" element={<Navigate to={canEnter ? "/home" : "/login"} replace />} />
                </Routes>
              </Suspense>
            </RouteErrorBoundary>
          )}
        </div>

        {/* Drag-region + WindowControls : top-level, visibles sur tous les
            écrans (login, resume, authenticated). */}
        <div
          className="pointer-events-none absolute inset-x-0 top-0 z-40 flex"
          style={{ height: DRAG_HEIGHT }}
          aria-hidden
        >
          {showSidebarSpacer && <div style={{ width: SIDEBAR_WIDTH }} aria-hidden />}
          <div className="drag-region pointer-events-auto flex-1" />
        </div>
        <WindowControls />

        {/* Updater monté au top-level pour poller dès le boot, même sur
            l'écran de login / resume — sinon le user qui ne se connecte
            pas tout de suite ne voit jamais la modale de MAJ. La modale
            de UpdateController est elle-même conditionnelle (rend null
            quand state.kind === 'idle'), donc zéro overhead visuel
            tant qu'aucune MAJ n'est dispo. */}
        <UpdateController />
      </div>
    </MotionConfig>
  );
}

// Sections annoncées mais pas encore construites (Boutique, Documentation).
function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="reborn-radial-bg-strong reborn-pattern-overlay flex h-full items-center justify-center px-8">
      <div className="max-w-md text-center">
        <span className="inline-block rounded-full border border-accent/40 bg-accent/10 px-2.5 py-0.5 text-2xs font-bold uppercase tracking-[0.14em] text-accent">
          Bientôt
        </span>
        <h1 className="mt-4 font-display text-4xl tracking-wide text-foreground">{title}</h1>
        <p className="mt-3 text-[13px] leading-relaxed text-foreground-subtle">
          Cette section arrive dans une prochaine mise à jour du launcher.
        </p>
      </div>
    </div>
  );
}
