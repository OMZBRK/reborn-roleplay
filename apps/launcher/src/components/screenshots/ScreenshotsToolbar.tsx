import { LayoutDashboard, LayoutGrid, List, Search } from "lucide-react";

export type ScreenshotView = "grid" | "list" | "detailed";

type Props = {
  query: string;
  setQuery: (v: string) => void;
  view: ScreenshotView;
  setView: (v: ScreenshotView) => void;
};

const VIEW_OPTIONS: { id: ScreenshotView; label: string; icon: typeof LayoutGrid }[] = [
  { id: "grid", label: "Grid", icon: LayoutGrid },
  { id: "list", label: "Liste", icon: List },
  { id: "detailed", label: "Détaillé", icon: LayoutDashboard },
];

export function ScreenshotsToolbar({ query, setQuery, view, setView }: Props) {
  return (
    <div className="flex items-center gap-3">
      <div className="reborn-shots-search">
        <Search className="h-3.5 w-3.5" />
        <input
          type="text"
          placeholder="Rechercher par titre, lieu, joueur..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Rechercher dans les screenshots"
        />
      </div>
      <div className="reborn-shots-viewtoggle" role="tablist" aria-label="Affichage">
        {VIEW_OPTIONS.map((opt) => {
          const Icon = opt.icon;
          return (
            <button
              key={opt.id}
              type="button"
              data-active={view === opt.id}
              onClick={() => setView(opt.id)}
              role="tab"
              aria-selected={view === opt.id}
            >
              <Icon className="h-3 w-3" />
              {opt.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
