import { Clock, Maximize2, RefreshCw, Sparkles, Star } from "lucide-react";

export type ScreenshotSort = "newest" | "oldest" | "size";

type Props = {
  sort: ScreenshotSort;
  setSort: (v: ScreenshotSort) => void;
  onlyFavorites: boolean;
  setOnlyFavorites: (v: boolean) => void;
  onReset: () => void;
};

const SORT_OPTIONS: { id: ScreenshotSort; label: string; icon: typeof Sparkles }[] = [
  { id: "newest", label: "Plus récents", icon: Sparkles },
  { id: "oldest", label: "Plus anciens", icon: Clock },
  { id: "size", label: "Taille", icon: Maximize2 },
];

// Filtres à droite de la grille : favoris + tri. Les filtres par joueur et
// par serveur de la maquette ont été retirés : une capture locale ne porte
// ni l'un ni l'autre (seulement un nom de fichier, une date et une taille).
export function ScreenshotsFilters({ sort, setSort, onlyFavorites, setOnlyFavorites, onReset }: Props) {
  return (
    <aside className="reborn-shots-filters" aria-label="Filtres des captures">
      <div className="reborn-shots-filters-section">
        <div className="reborn-shots-filters-title">Affichage</div>
        <button
          type="button"
          data-active={onlyFavorites}
          onClick={() => setOnlyFavorites(!onlyFavorites)}
          className="reborn-shots-sort-btn"
          aria-pressed={onlyFavorites}
        >
          <span className="reborn-shots-sort-dot" aria-hidden />
          <Star className="h-3 w-3" fill={onlyFavorites ? "currentColor" : "none"} aria-hidden />
          <span>Favoris uniquement</span>
        </button>
      </div>

      <div className="reborn-shots-filters-section">
        <div className="reborn-shots-filters-title" id="shots-sort-title">
          Tri
        </div>
        <div className="flex flex-col gap-1" role="group" aria-labelledby="shots-sort-title">
          {SORT_OPTIONS.map((opt) => {
            const Icon = opt.icon;
            const active = sort === opt.id;
            return (
              <button
                key={opt.id}
                type="button"
                data-active={active}
                aria-pressed={active}
                onClick={() => setSort(opt.id)}
                className="reborn-shots-sort-btn"
              >
                <span className="reborn-shots-sort-dot" aria-hidden />
                <Icon className="h-3 w-3" aria-hidden />
                <span>{opt.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      <button type="button" onClick={onReset} className="reborn-shots-reset-btn">
        <RefreshCw className="h-3 w-3" aria-hidden />
        Réinitialiser
      </button>
    </aside>
  );
}
