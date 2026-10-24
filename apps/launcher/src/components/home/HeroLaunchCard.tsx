import { Cpu, Layers, ScrollText } from "lucide-react";
import { PlayButton } from "./PlayButton";
import { SecondInstanceButton } from "./SecondInstanceButton";
import { ServerStatusChip } from "./ServerStatusChip";
import { useMcVersion } from "../../hooks/use-mc-version";
import { openLogsPanel } from "../game-log/logs-panel-store";

// Bloc hero principal : texte branding a gauche + PlayButton existant a
// droite. Reutilise integralement le PlayButton v2 qui porte deja toute
// la logique check-update / launch-game / progress / pulse glow.
//
// Pas de wrapper framer-motion ici — l'`initial={opacity:0}` du
// composant precedent posait probleme dans certains contextes HMR
// (la section restait invisible). Animation d'entree desormais via CSS
// keyframes (cf .reborn-home-hero).
export function HeroLaunchCard() {
  const mcVersion = useMcVersion();
  return (
    <section className="reborn-home-hero reborn-pattern-overlay">
      <div className="reborn-home-hero-inner">
        <div className="reborn-home-hero-left">
          <div className="reborn-home-hero-kicker">
            <span>REBORN ROLEPLAY</span>
            <span className="reborn-home-hero-dot" />
            <span>NARUTO EDITION</span>
          </div>
          <h1 className="reborn-home-hero-title">REBORN</h1>
          <p className="reborn-home-hero-sub">
            Entre le silence et le fracas, chaque shinobi trace sa propre voie.
          </p>
          <div className="reborn-home-hero-meta">
            <span className="reborn-home-hero-chip">
              <Cpu className="h-2.5 w-2.5" />
              Fabric Loader
            </span>
            <span className="reborn-home-hero-chip">
              <Layers className="h-2.5 w-2.5" />
              Minecraft {mcVersion}
            </span>
            <ServerStatusChip />
          </div>
        </div>
        <div className="reborn-home-hero-right">
          <div className="flex flex-col items-center gap-4">
            <PlayButton />
            <div className="flex items-start gap-3">
              <button
                type="button"
                onClick={openLogsPanel}
                title="Journal de la partie en cours ou de la dernière partie (filtres, recherche, export)"
                className="inline-flex items-center gap-2 rounded-lg border border-border-strong bg-surface-elevated px-4 py-2 text-[11px] uppercase tracking-[0.14em] text-foreground-subtle transition-colors hover:border-accent/60 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]"
              >
                <ScrollText className="h-3.5 w-3.5" />
                Journal du jeu
              </button>
              <SecondInstanceButton />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
