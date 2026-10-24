import { LayoutDashboard, LayoutGrid, List, Search } from "lucide-react";

export type ScreenshotView = "grid" | "list" | "detailed";

type Props = {
  query: string;
  setQuery: (v: string) => void;
  view: ScreenshotView;
  setView: (v: ScreenshotView) => void;
};

const VIEW_OPTIONS: { id: ScreenshotView; label: string; icon: typeof LayoutGrid }[] = [
  { id: "grid", label: "Grille", icon: LayoutGrid },
  { id: "list", label: "Liste", icon: List },
  { id: "detailed", label: "Détaillé", icon: LayoutDashboard },
];

export function ScreenshotsToolbar({ query, setQuery, view, setView }: Props) {
  return (
    <div className="flex items-center gap-3">
      <div className="reborn-shots-search">
        <Search className="h-3.5 w-3.5" aria-hidden />
        <input
          type="search"
          placeholder="Rechercher une capture par son nom…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Rechercher dans les captures"
        />
      </div>
      {/* Bascule de vue : boutons à bascule (aria-pressed), pas des onglets
          (aucun panneau d'onglet associé). */}
      <div className="reborn-shots-viewtoggle" role="group" aria-label="Disposition des captures">
        {VIEW_OPTIONS.map((opt) => {
          const Icon = opt.icon;
          const active = view === opt.id;
          return (
            <button
              key={opt.id}
              type="button"
              data-active={active}
              aria-pressed={active}
              onClick={() => setView(opt.id)}
            >
              <Icon className="h-3 w-3" aria-hidden />
              {opt.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
