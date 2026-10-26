import { useNavigate } from "react-router";
import { Check, Clock, Cpu, Layers, ScrollText } from "lucide-react";
import { PlayButton } from "./PlayButton";
import { useAccesJeu } from "./acces-jeu";
import { dateDe, heure, jourRelatif } from "../whitelist/format";
import "./douane.css";
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
//
// Candidat dont la place de douane est reservee (CANDIDATURE_ET_DOUANE
// §3.3, maquette L10) : sous-titre de la douane et mini-frise Ecrit ·
// Douane · Bienvenue ; le PlayButton devient « Passer la douane » et
// « Ma séance » rejoint « Journal du jeu ».
const BOUTON_SECONDAIRE =
  "inline-flex items-center gap-2 rounded-lg border border-border-strong bg-surface-elevated px-4 py-2 text-[11px] uppercase tracking-[0.14em] text-foreground-subtle transition-colors hover:border-accent/60 hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]";

export function HeroLaunchCard() {
  const navigate = useNavigate();
  const mcVersion = useMcVersion();
  const { acces, estCandidat } = useAccesJeu();
  // Séance manquée : la phase est revenue à « à réserver », mais la frise
  // reste (« Douane · à réserver », maquette L10).
  const manquee = acces?.refus?.kind === "douane_closed";
  const douane = estCandidat && (acces?.phase === "DOUANE_RESERVEE" || manquee);
  const convocation = dateDe(acces?.convocationAt);
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
            {douane
              ? acces?.refus?.kind === "douane_hors_jeu"
                ? "Ton écrit est accepté. Dernière étape : l'oral, face à un examinateur."
                : "Ton écrit est accepté. Dernière étape : la douane, en jeu, face à un examinateur."
              : "Entre le silence et le fracas, chaque shinobi trace sa propre voie."}
          </p>
          {douane && (
            <ol className="reborn-douane-frise" aria-label="Avancement de la candidature">
              <li data-etat="fait">
                <span className="reborn-douane-frise-pt">
                  <Check className="h-2.5 w-2.5" strokeWidth={3} />
                </span>
                Écrit accepté
              </li>
              <li data-etat="encours">
                <span className="reborn-douane-frise-pt" />
                {manquee || !convocation
                  ? "Douane · à réserver"
                  : `Douane · ${jourRelatif(convocation)} ${heure(convocation)}`}
              </li>
              <li data-etat="avenir">
                <span className="reborn-douane-frise-pt" />
                Bienvenue
              </li>
            </ol>
          )}
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
                className={BOUTON_SECONDAIRE}
              >
                <ScrollText className="h-3.5 w-3.5" />
                Journal du jeu
              </button>
              {douane && !manquee && (
                <button
                  type="button"
                  onClick={() => navigate("/whitelist?vue=douane")}
                  title="Ta séance de douane : heure, fenêtre d'entrée, changement ou annulation"
                  className={BOUTON_SECONDAIRE}
                >
                  <Clock className="h-3.5 w-3.5" />
                  Ma séance
                </button>
              )}
              <SecondInstanceButton />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
