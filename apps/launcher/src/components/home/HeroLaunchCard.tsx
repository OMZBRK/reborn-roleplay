import { Cpu, Layers } from "lucide-react";
import { PlayButton } from "./PlayButton";
import { SecondInstanceButton } from "./SecondInstanceButton";
import { ServerStatusChip } from "./ServerStatusChip";

// Bloc hero principal : texte branding a gauche + PlayButton existant a
// droite. Reutilise integralement le PlayButton v2 qui porte deja toute
// la logique check-update / launch-game / progress / pulse glow.
//
// Pas de wrapper framer-motion ici — l'`initial={opacity:0}` du
// composant precedent posait probleme dans certains contextes HMR
// (la section restait invisible). Animation d'entree desormais via CSS
// keyframes (cf .reborn-home-hero).
export function HeroLaunchCard() {
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
            Dans l'ombre ou la lumière, chaque ninja écrit sa propre destinée.
          </p>
          <div className="reborn-home-hero-meta">
            <span className="reborn-home-hero-chip">
              <Cpu className="h-2.5 w-2.5" />
              Fabric Loader
            </span>
            <span className="reborn-home-hero-chip">
              <Layers className="h-2.5 w-2.5" />
              Minecraft 1.21.1
            </span>
            <ServerStatusChip />
          </div>
        </div>
        <div className="reborn-home-hero-right">
          <div className="flex flex-col items-center gap-4">
            <PlayButton />
            <SecondInstanceButton />
          </div>
        </div>
      </div>
    </section>
  );
}
