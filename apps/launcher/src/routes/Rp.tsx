import type { KeyboardEvent } from "react";
import { Image, Lock, Map as MapIcon, Users } from "lucide-react";
import { Navigate, useNavigate, useSearchParams } from "react-router";
import { useAuthStore } from "../stores/auth-store";
import { cn } from "../lib/cn";
import { Map } from "./Map";
import { Screenshots } from "./Screenshots";
import { Feed } from "./Feed";

// « Mon personnage » a quitté cet onglet : « Mes personnages » a sa propre
// entrée dans la barre latérale (/personnages, CANDIDATURE_ET_DOUANE §4.6).
const TABS = [
  { id: "map", label: "Carte", icon: MapIcon, gated: false },
  { id: "screenshots", label: "Screenshots", icon: Image, gated: true },
  { id: "feed", label: "Feed communautaire", icon: Users, gated: true },
] as const;

type RpTab = (typeof TABS)[number]["id"];

// Le lien du rail pointe sur /rp sans paramètre : on retombe sur le dernier
// sous-onglet ouvert pendant la session.
const LAST_TAB_KEY = "reborn.rp.tab";
function readLastTab(): string | null {
  try {
    return sessionStorage.getItem(LAST_TAB_KEY);
  } catch {
    return null;
  }
}

/**
 * Onglet « RP » — regroupe la carte, les screenshots et le feed
 * communautaire. Screenshots + feed sont réservés aux joueurs ayant le statut
 * whitelist minimum ({@code role !== "PLAYER"}).
 */
export function Rp() {
  const user = useAuthStore((s) => s.user);
  const whitelisted = user ? user.role !== "PLAYER" : false;
  // Sous-onglet porté par l'URL (?tab=feed) : revenir sur « RP » ou faire
  // précédent retombe sur le même onglet au lieu de repartir du personnage.
  const [params, setParams] = useSearchParams();
  const raw = params.get("tab") ?? readLastTab();
  const tab: RpTab = TABS.some((t) => t.id === raw) ? (raw as RpTab) : "map";
  const setTab = (id: RpTab) => {
    try {
      sessionStorage.setItem(LAST_TAB_KEY, id);
    } catch {
      // stockage indisponible : l'URL suffit
    }
    setParams({ tab: id }, { replace: true });
  };

  // Onglets ARIA : flèches gauche/droite (+ Début/Fin) changent d'onglet,
  // seul l'onglet actif est dans l'ordre de tabulation.
  const onTabsKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const i = TABS.findIndex((t) => t.id === tab);
    let next = -1;
    if (e.key === "ArrowRight") next = (i + 1) % TABS.length;
    else if (e.key === "ArrowLeft") next = (i - 1 + TABS.length) % TABS.length;
    else if (e.key === "Home") next = 0;
    else if (e.key === "End") next = TABS.length - 1;
    if (next < 0) return;
    e.preventDefault();
    setTab(TABS[next].id);
    document.getElementById(`rp-tab-${TABS[next].id}`)?.focus();
  };

  // Ancien lien vers l'onglet du personnage (/character, ?tab=character).
  if (params.get("tab") === "character") return <Navigate to="/personnages" replace />;

  return (
    <div className="flex h-full flex-col">
      {/* Barre de sous-onglets */}
      <div
        className="flex flex-shrink-0 items-center gap-1 border-b border-border/60 px-6 pt-5 pb-0"
        role="tablist"
        aria-label="Sections RP"
        onKeyDown={onTabsKeyDown}
      >
        {TABS.map((t) => {
          const Icon = t.icon;
          const locked = t.gated && !whitelisted;
          const active = tab === t.id;
          return (
            <button
              key={t.id}
              id={`rp-tab-${t.id}`}
              type="button"
              role="tab"
              aria-selected={active}
              aria-controls="rp-panel"
              tabIndex={active ? 0 : -1}
              onClick={() => setTab(t.id)}
              className={cn(
                "relative flex items-center gap-1.5 rounded-t-md px-3.5 py-2.5 text-[13px] font-medium transition-colors",
                active
                  ? "text-foreground"
                  : "text-foreground-muted hover:text-foreground-subtle",
              )}
            >
              <Icon size={15} aria-hidden />
              {t.label}
              {locked && (
                <>
                  <Lock size={11} className="opacity-60" aria-hidden />
                  <span className="reborn-sr-only"> (réservé aux joueurs whitelist)</span>
                </>
              )}
              {active && (
                <span className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-accent" aria-hidden />
              )}
            </button>
          );
        })}
      </div>

      {/* Contenu du sous-onglet */}
      <div
        className="min-h-0 flex-1 overflow-hidden"
        role="tabpanel"
        id="rp-panel"
        aria-labelledby={`rp-tab-${tab}`}
      >
        {tab === "map" && <Map />}
        {tab === "screenshots" && (whitelisted ? <Screenshots /> : <WhitelistGate feature="les screenshots" />)}
        {tab === "feed" && (whitelisted ? <Feed /> : <WhitelistGate feature="le feed communautaire" />)}
      </div>
    </div>
  );
}

function WhitelistGate({ feature }: { feature: string }) {
  const navigate = useNavigate();
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 px-8 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-full border border-border-strong bg-surface-elevated">
        <Lock className="h-6 w-6 text-foreground-muted" aria-hidden />
      </div>
      <div>
        <h2 className="font-display text-[20px] tracking-wide text-foreground">
          Réservé aux joueurs whitelist
        </h2>
        <p className="mx-auto mt-2 max-w-md text-[13px] leading-relaxed text-foreground-subtle">
          {feature.charAt(0).toUpperCase() + feature.slice(1)} n'est accessible qu'une fois ta
          candidature acceptée. Passe la whitelist pour débloquer la partie communautaire du RP.
        </p>
      </div>
      <button
        type="button"
        onClick={() => navigate("/whitelist")}
        className="inline-flex items-center gap-2 rounded-md border border-accent/40 bg-accent/10 px-4 py-2.5 text-[13px] font-medium text-accent transition hover:bg-accent/20"
      >
        Voir ma candidature
      </button>
    </div>
  );
}
