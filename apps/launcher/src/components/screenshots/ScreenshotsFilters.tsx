import { Check, Clock, Maximize2, RefreshCw, Sparkles, Star } from "lucide-react";
import type {
  ScreenshotPlayerOption,
  ScreenshotServerOption,
} from "../../lib/screenshots-mock";

export type ScreenshotSort = "newest" | "oldest" | "size";

type Props = {
  sort: ScreenshotSort;
  setSort: (v: ScreenshotSort) => void;
  players: ScreenshotPlayerOption[];
  selectedPlayers: string[];
  togglePlayer: (id: string) => void;
  servers: ScreenshotServerOption[];
  selectedServers: string[];
  toggleServer: (id: string) => void;
  onlyFavorites: boolean;
  setOnlyFavorites: (v: boolean) => void;
  onReset: () => void;
};

const SORT_OPTIONS: { id: ScreenshotSort; label: string; icon: typeof Sparkles }[] = [
  { id: "newest", label: "Plus récents", icon: Sparkles },
  { id: "oldest", label: "Plus anciens", icon: Clock },
  { id: "size", label: "Taille", icon: Maximize2 },
];

// Filtres a droite de la grid. Pour cette PR : pas de filtre date / mini
// calendrier — le scope MVP est sort + players + servers. Le calendrier
// arrivera quand on aura un vrai backend pour les dates (le scanner FS
// remontera de vraies dates ISO, indexables proprement).
export function ScreenshotsFilters({
  sort,
  setSort,
  players,
  selectedPlayers,
  togglePlayer,
  servers,
  selectedServers,
  toggleServer,
  onlyFavorites,
  setOnlyFavorites,
  onReset,
}: Props) {
  return (
    <aside className="reborn-shots-filters">
      <div className="reborn-shots-filters-section">
        <div className="reborn-shots-filters-title">Affichage</div>
        <button
          type="button"
          data-active={onlyFavorites}
          onClick={() => setOnlyFavorites(!onlyFavorites)}
          className="reborn-shots-sort-btn"
          aria-pressed={onlyFavorites}
        >
          <span className="reborn-shots-sort-dot" />
          <Star
            className="h-3 w-3"
            fill={onlyFavorites ? "currentColor" : "none"}
          />
          <span>Favoris uniquement</span>
        </button>
      </div>

      <div className="reborn-shots-filters-section">
        <div className="reborn-shots-filters-title">Tri</div>
        <div className="flex flex-col gap-1">
          {SORT_OPTIONS.map((opt) => {
            const Icon = opt.icon;
            return (
              <button
                key={opt.id}
                type="button"
                data-active={sort === opt.id}
                onClick={() => setSort(opt.id)}
                className="reborn-shots-sort-btn"
              >
                <span className="reborn-shots-sort-dot" />
                <Icon className="h-3 w-3" />
                <span>{opt.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {players.length > 0 && (
        <div className="reborn-shots-filters-section">
          <div className="reborn-shots-filters-title">Joueur</div>
          <div className="flex flex-wrap gap-1.5">
            {players.map((p: ScreenshotPlayerOption) => {
              const active = selectedPlayers.includes(p.id);
              return (
                <button
                  key={p.id}
                  type="button"
                  title={p.name}
                  data-active={active}
                  onClick={() => togglePlayer(p.id)}
                  className="reborn-shots-avatar-chip"
                  aria-pressed={active}
                >
                  {p.initial}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {servers.length > 0 && (
        <div className="reborn-shots-filters-section">
          <div className="reborn-shots-filters-title">Serveur</div>
          <div className="flex flex-col gap-1">
            {servers.map((s: ScreenshotServerOption) => {
              const active = selectedServers.includes(s.id);
              return (
              <button
                key={s.id}
                type="button"
                data-active={active}
                onClick={() => toggleServer(s.id)}
                className="reborn-shots-checkbox"
                aria-pressed={active}
              >
                <span className="reborn-shots-checkbox-box">
                  {active && <Check className="h-2.5 w-2.5" strokeWidth={3} />}
                </span>
                <span className="flex-1 text-left">{s.name}</span>
                <span className="reborn-shots-checkbox-count">{s.count}</span>
              </button>
            );
          })}
          </div>
        </div>
      )}

      <button type="button" onClick={onReset} className="reborn-shots-reset-btn">
        <RefreshCw className="h-3 w-3" />
        Réinitialiser
      </button>
    </aside>
  );
}
