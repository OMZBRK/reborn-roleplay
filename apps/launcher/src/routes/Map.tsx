import { Layers, Lock, Map as MapIcon, MapPin } from "lucide-react";

// Page Map — stub visuel pour annoncer la fonctionnalite a venir.
// Pas de scan FS ni de vrai rendu : on affiche un apercu mock + une
// roadmap des 3 etapes de developpement.
//
// TODO(map): brancher le vrai rendu sur un endpoint /v1/map/tiles ou
// integrer un viewer Leaflet/MapLibre quand l'asset tileset sera dispo.
export function Map() {
  return (
    <div className="reborn-map-page reborn-radial-bg-strong reborn-pattern-overlay">
      <div className="reborn-map-stub">
        <div className="reborn-map-stub-icon">
          <MapIcon className="h-9 w-9" />
        </div>
        <div className="reborn-map-stub-tag">v1.1 — EN COURS DE DÉVELOPPEMENT</div>
        <h1 className="reborn-map-stub-title">Carte du monde</h1>
        <p className="reborn-map-stub-sub">
          Une vue interactive de Reborn Roleplay arrive bientôt. Tu pourras explorer
          les villages cachés, les points d'intérêt RP et localiser tes alliés en
          temps réel.
        </p>

        <div className="reborn-map-preview">
          <div className="reborn-map-preview-bg" />
          <div className="reborn-map-preview-overlay">
            <div className="reborn-map-preview-controls">
              <button type="button" className="reborn-map-ctrl">+</button>
              <button type="button" className="reborn-map-ctrl">−</button>
              <button type="button" className="reborn-map-ctrl">
                <Layers className="h-3 w-3" />
              </button>
            </div>
            <div className="reborn-map-preview-pins">
              <span
                className="reborn-map-pin"
                style={{ top: "38%", left: "28%" }}
              >
                <MapPin className="h-3 w-3" />
              </span>
              <span
                className="reborn-map-pin reborn-map-pin--alt"
                style={{ top: "55%", left: "62%" }}
              >
                <MapPin className="h-3 w-3" />
              </span>
              <span
                className="reborn-map-pin reborn-map-pin--gold"
                style={{ top: "30%", left: "70%" }}
              >
                <MapPin className="h-3 w-3" />
              </span>
            </div>
            <div className="reborn-map-preview-coord">
              <span>576, 64, -1648</span>
              <span className="reborn-map-preview-coord-sep" />
              <span>VILLAGE DE KONOHA</span>
            </div>
          </div>
          <div className="reborn-map-preview-locked">
            <div className="reborn-map-preview-locked-inner">
              <Lock className="h-3 w-3" />
              <span>Aperçu uniquement</span>
            </div>
          </div>
        </div>

        <div className="reborn-map-roadmap">
          <RoadmapStep state="done" title="Cartographie initiale" sub="Capture des chunks principaux" />
          <div className="reborn-map-roadmap-line" />
          <RoadmapStep state="active" title="Pins RP" sub="Villages, clans, événements" />
          <div className="reborn-map-roadmap-line" />
          <RoadmapStep state="todo" title="Tracking live" sub="Positions des shinobis" />
        </div>
      </div>
    </div>
  );
}

function RoadmapStep({
  state,
  title,
  sub,
}: {
  state: "done" | "active" | "todo";
  title: string;
  sub: string;
}) {
  return (
    <div className={`reborn-map-roadmap-step reborn-map-roadmap-step--${state}`}>
      <span className="reborn-map-roadmap-dot" />
      <div>
        <div className="reborn-map-roadmap-title">{title}</div>
        <div className="reborn-map-roadmap-sub">{sub}</div>
      </div>
    </div>
  );
}
