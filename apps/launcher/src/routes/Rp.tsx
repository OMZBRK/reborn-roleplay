import { useState } from "react";
import { Image, Lock, Map as MapIcon, User, Users } from "lucide-react";
import { useNavigate } from "react-router";
import { useAuthStore } from "../stores/auth-store";
import { cn } from "../lib/cn";
import { Character } from "./Character";
import { Map } from "./Map";
import { Screenshots } from "./Screenshots";
import { Feed } from "./Feed";

const TABS = [
  { id: "character", label: "Mon personnage", icon: User, gated: false },
  { id: "map", label: "Carte", icon: MapIcon, gated: false },
  { id: "screenshots", label: "Screenshots", icon: Image, gated: true },
  { id: "feed", label: "Feed communautaire", icon: Users, gated: true },
] as const;

type RpTab = (typeof TABS)[number]["id"];

/**
 * Onglet « RP » — regroupe le personnage, la carte, les screenshots et le feed
 * communautaire. Screenshots + feed sont réservés aux joueurs ayant le statut
 * whitelist minimum ({@code role !== "PLAYER"}).
 */
export function Rp() {
  const user = useAuthStore((s) => s.user);
  const whitelisted = user ? user.role !== "PLAYER" : false;
  const [tab, setTab] = useState<RpTab>("character");

  return (
    <div className="flex h-full flex-col">
      {/* Barre de sous-onglets */}
      <div className="flex flex-shrink-0 items-center gap-1 border-b border-border/60 px-6 pt-5 pb-0">
        {TABS.map((t) => {
          const Icon = t.icon;
          const locked = t.gated && !whitelisted;
          const active = tab === t.id;
          return (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={cn(
                "relative flex items-center gap-1.5 rounded-t-md px-3.5 py-2.5 text-[13px] font-medium transition-colors",
                active
                  ? "text-foreground"
                  : "text-foreground-muted hover:text-foreground-subtle",
              )}
            >
              <Icon size={15} />
              {t.label}
              {locked && <Lock size={11} className="opacity-60" />}
              {active && (
                <span className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-accent" />
              )}
            </button>
          );
        })}
      </div>

      {/* Contenu du sous-onglet */}
      <div className="min-h-0 flex-1 overflow-hidden">
        {tab === "character" && <Character />}
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
        <Lock className="h-6 w-6 text-foreground-muted" />
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
