import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useSearchParams } from "react-router";
import { motion, AnimatePresence } from "framer-motion";
import { Bell, Gamepad2, Info, Link2, Palette, ShieldCheck, User2 } from "lucide-react";
import { ProfileTab } from "./settings/ProfileTab";
import { AccountTab } from "./settings/AccountTab";
import { ConnectionsTab } from "./settings/ConnectionsTab";
import { GameTab } from "./settings/GameTab";
import { AppearanceTab } from "./settings/AppearanceTab";
import { NotificationsTab } from "./settings/NotificationsTab";
import { AboutTab } from "./settings/AboutTab";

// Page Paramètres : en-tête + navigation verticale par onglets. Chaque
// onglet vit dans routes/settings/<Onglet>.tsx, les briques communes dans
// routes/settings/ui.tsx. Lien direct vers un onglet : /settings?tab=game.

const TABS = [
  { id: "profile", label: "Profil", hint: "Identité & rôle", icon: User2, Panel: ProfileTab },
  { id: "account", label: "Compte", hint: "Session", icon: ShieldCheck, Panel: AccountTab },
  { id: "connections", label: "Connexions", hint: "Discord, Steam…", icon: Link2, Panel: ConnectionsTab },
  { id: "game", label: "Jeu", hint: "Fichiers du jeu", icon: Gamepad2, Panel: GameTab },
  { id: "appearance", label: "Apparence", hint: "Police & affichage", icon: Palette, Panel: AppearanceTab },
  { id: "notif", label: "Notifications", hint: "Push, e-mail, alertes", icon: Bell, Panel: NotificationsTab },
  { id: "about", label: "À propos", hint: "Version & support", icon: Info, Panel: AboutTab },
] as const;
type TabId = (typeof TABS)[number]["id"];

const isTabId = (v: string | null): v is TabId => TABS.some((t) => t.id === v);

export function Settings() {
  const [params] = useSearchParams();
  const requested = params.get("tab");
  const [tab, setTab] = useState<TabId>(isTabId(requested) ? requested : "profile");
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);

  // Lien interne vers /settings?tab=… alors que la page est déjà ouverte.
  useEffect(() => {
    if (isTabId(requested)) setTab(requested);
  }, [requested]);

  // Navigation clavier dans la liste verticale (motif ARIA « tabs ») :
  // flèches haut/bas, Début/Fin ; l'onglet suit le focus.
  function onTabsKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    const current = TABS.findIndex((t) => t.id === tab);
    let next = -1;
    if (e.key === "ArrowDown") next = (current + 1) % TABS.length;
    else if (e.key === "ArrowUp") next = (current - 1 + TABS.length) % TABS.length;
    else if (e.key === "Home") next = 0;
    else if (e.key === "End") next = TABS.length - 1;
    if (next < 0) return;
    e.preventDefault();
    setTab(TABS[next].id);
    tabRefs.current[next]?.focus();
  }

  const active = TABS.find((t) => t.id === tab) ?? TABS[0];
  const Panel = active.Panel;

  return (
    <div className="reborn-settings-page">
      <div className="reborn-settings-scroll">
        <div className="reborn-settings-container">
          <Header />

          <div className="reborn-settings-body">
            <div
              role="tablist"
              aria-label="Catégories des paramètres"
              aria-orientation="vertical"
              onKeyDown={onTabsKeyDown}
              className="reborn-settings-sidebar"
            >
              {TABS.map((t, i) => {
                const Icon = t.icon;
                const selected = tab === t.id;
                return (
                  <button
                    key={t.id}
                    ref={(el) => {
                      tabRefs.current[i] = el;
                    }}
                    type="button"
                    role="tab"
                    id={`settings-tab-${t.id}`}
                    aria-selected={selected}
                    aria-controls="settings-panel"
                    tabIndex={selected ? 0 : -1}
                    onClick={() => setTab(t.id)}
                    data-active={selected || undefined}
                    className="reborn-settings-cat"
                  >
                    <span className="reborn-settings-cat-icon">
                      <Icon className="h-4 w-4" />
                    </span>
                    <span className="reborn-settings-cat-meta">
                      <span className="reborn-settings-cat-label">{t.label}</span>
                      <span className="reborn-settings-cat-hint">{t.hint}</span>
                    </span>
                  </button>
                );
              })}
            </div>

            <div
              role="tabpanel"
              id="settings-panel"
              aria-labelledby={`settings-tab-${tab}`}
              className="reborn-settings-content"
            >
              <AnimatePresence mode="wait">
                <motion.div
                  key={tab}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -4 }}
                  transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
                >
                  <Panel />
                </motion.div>
              </AnimatePresence>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Header() {
  return (
    <header className="relative flex items-end justify-between gap-6 border-b border-border/60 pb-5">
      <div>
        <p className="text-2xs font-semibold uppercase tracking-[0.28em] text-foreground-muted">
          Reborn Roleplay
        </p>
        <h1
          className="mt-1.5 font-display"
          style={{
            fontSize: 32,
            lineHeight: 1.05,
            letterSpacing: "0.03em",
            color: "var(--color-foreground)",
          }}
        >
          Paramètres
        </h1>
      </div>
      <div className="hidden text-right sm:block">
        <p className="text-2xs font-semibold uppercase tracking-[0.24em] text-foreground-muted">
          Identité visuelle
        </p>
        <p className="mt-1 text-[11.5px] text-foreground-subtle">
          Direction Akatsuki <span className="mx-1 opacity-40">·</span> v3
        </p>
      </div>
    </header>
  );
}
