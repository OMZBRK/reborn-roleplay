import { Cpu, Layers } from "lucide-react";
import { PlayButton } from "./PlayButton";
import { SecondInstanceButton } from "./SecondInstanceButton";
import { ServerSelector } from "./ServerSelector";
import { ServerStatusChip } from "./ServerStatusChip";
import { useServerStore } from "../../stores/server-store";

// Bloc hero principal : texte branding a gauche + PlayButton existant a
// droite. Reutilise integralement le PlayButton v2 qui porte deja toute
// la logique check-update / launch-game / progress / pulse glow.
//
// Pas de wrapper framer-motion ici — l'`initial={opacity:0}` du
// composant precedent posait probleme dans certains contextes HMR
// (la section restait invisible). Animation d'entree desormais via CSS
// keyframes (cf .reborn-home-hero).
export function HeroLaunchCard() {
  // Version MC affichée = celle du serveur sélectionné (multi-version). Fallback
  // 26.2 (RP) tant que la liste des serveurs n'est pas chargée.
  const servers = useServerStore((s) => s.servers);
  const selectedId = useServerStore((s) => s.selectedId);
  const mcVersion =
    servers.find((s) => s.id === selectedId)?.mcVersion ?? "26.2";
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
            <ServerSelector />
            <PlayButton />
            <div className="flex items-center gap-3">
              <SecondInstanceButton />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
