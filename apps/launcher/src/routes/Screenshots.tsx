import { useMemo, useState } from "react";
import { Camera, FolderOpen, Info } from "lucide-react";
import {
  MOCK_SCREENSHOTS,
  type ScreenshotRecord,
} from "../lib/screenshots-mock";
import {
  ScreenshotsToolbar,
  type ScreenshotView,
} from "../components/screenshots/ScreenshotsToolbar";
import {
  ScreenshotsFilters,
  type ScreenshotSort,
} from "../components/screenshots/ScreenshotsFilters";
import { ScreenshotThumb } from "../components/screenshots/ScreenshotThumb";
import { ScreenshotsEmpty } from "../components/screenshots/ScreenshotsEmpty";
import { ScreenshotLightbox } from "../components/screenshots/ScreenshotLightbox";

// Page Screenshots. La data vient pour l'instant de MOCK_SCREENSHOTS (cf
// TODO en haut de lib/screenshots-mock.ts). Le composant filtre / trie /
// pagine cote frontend — quand le scanner FS Tauri arrivera, il suffira
// de remplacer le `useState` initial par les donnees du scanner.

const TOTAL_PINNED = MOCK_SCREENSHOTS.filter((s) => s.pinned).length;

function parseSizeKb(size: string): number {
  // "412 KB" -> 412
  const n = parseInt(size, 10);
  return Number.isFinite(n) ? n : 0;
}

function dateSortKey(s: ScreenshotRecord): number {
  // Le mock fournit des strings affichables ("16 mai 2026" + "23:42"). Sans
  // ISO on tri par index d'apparition dans la liste mock — qui est deja
  // ordonnee du plus recent au plus ancien.
  return MOCK_SCREENSHOTS.indexOf(s);
}

export function Screenshots() {
  const [query, setQuery] = useState("");
  const [view, setView] = useState<ScreenshotView>("grid");
  const [sort, setSort] = useState<ScreenshotSort>("newest");
  const [selectedPlayers, setSelectedPlayers] = useState<string[]>([]);
  const [selectedServers, setSelectedServers] = useState<string[]>([]);
  const [openShot, setOpenShot] = useState<ScreenshotRecord | null>(null);

  const filtered = useMemo<ScreenshotRecord[]>(() => {
    let next = MOCK_SCREENSHOTS.slice();

    if (query) {
      const q = query.toLowerCase();
      next = next.filter(
        (s) =>
          s.title.toLowerCase().includes(q) ||
          s.player.toLowerCase().includes(q) ||
          s.server.toLowerCase().includes(q),
      );
    }
    if (selectedPlayers.length > 0) {
      next = next.filter((s) => selectedPlayers.includes(s.player));
    }
    if (selectedServers.length > 0) {
      next = next.filter((s) => selectedServers.includes(s.server));
    }

    switch (sort) {
      case "newest":
        next.sort((a, b) => dateSortKey(a) - dateSortKey(b));
        break;
      case "oldest":
        next.sort((a, b) => dateSortKey(b) - dateSortKey(a));
        break;
      case "size":
        next.sort((a, b) => parseSizeKb(b.size) - parseSizeKb(a.size));
        break;
    }
    return next;
  }, [query, sort, selectedPlayers, selectedServers]);

  const totalSizeMb = useMemo(() => {
    const sum = filtered.reduce((acc, s) => acc + parseSizeKb(s.size), 0);
    return (sum / 1000).toFixed(1);
  }, [filtered]);

  function togglePlayer(id: string) {
    setSelectedPlayers((p) =>
      p.includes(id) ? p.filter((x) => x !== id) : [...p, id],
    );
  }
  function toggleServer(id: string) {
    setSelectedServers((s) =>
      s.includes(id) ? s.filter((x) => x !== id) : [...s, id],
    );
  }
  function reset() {
    setQuery("");
    setSort("newest");
    setSelectedPlayers([]);
    setSelectedServers([]);
  }

  function openAt(shot: ScreenshotRecord) {
    setOpenShot(shot);
  }
  function navOffset(delta: number) {
    if (!openShot) return;
    const idx = filtered.findIndex((s) => s.id === openShot.id);
    if (idx === -1) return;
    const nextIdx = (idx + delta + filtered.length) % filtered.length;
    const next = filtered[nextIdx];
    if (next) setOpenShot(next);
  }

  const isFresh = MOCK_SCREENSHOTS.length === 0;
  const isFiltered = filtered.length === 0 && !isFresh;

  return (
    <div className="reborn-shots-page reborn-radial-bg-strong reborn-pattern-overlay">
      <div className="reborn-shots-scroll">
        <header className="reborn-shots-head">
          <div>
            <h1 className="font-display text-4xl tracking-wide">Screenshots</h1>
            <div className="reborn-shots-subhead">
              <span>Tes captures in-game</span>
              <span className="reborn-shots-subhead-dot" />
              <span>Synchronisées localement</span>
            </div>
          </div>
          <div className="flex gap-2">
            <button type="button" className="reborn-shots-head-btn">
              <FolderOpen className="h-3 w-3" />
              Ouvrir le dossier
            </button>
            <button
              type="button"
              className="reborn-shots-head-btn reborn-shots-head-btn--primary"
            >
              <Camera className="h-3 w-3" />
              Nouvelle capture
            </button>
          </div>
        </header>

        {isFresh ? (
          <ScreenshotsEmpty variant="fresh" />
        ) : (
          <div className="reborn-shots-layout">
            <div className="min-w-0">
              <ScreenshotsToolbar
                query={query}
                setQuery={setQuery}
                view={view}
                setView={setView}
              />

              {isFiltered ? (
                <ScreenshotsEmpty variant="no-match" onReset={reset} />
              ) : (
                <>
                  {view === "grid" && (
                    <div className="reborn-shots-grid">
                      {filtered.map((s) => (
                        <ScreenshotThumb
                          key={s.id}
                          shot={s}
                          layout="grid"
                          onOpen={openAt}
                        />
                      ))}
                    </div>
                  )}
                  {view === "list" && (
                    <div className="reborn-shots-list">
                      {filtered.map((s) => (
                        <ScreenshotThumb
                          key={s.id}
                          shot={s}
                          layout="list"
                          onOpen={openAt}
                        />
                      ))}
                    </div>
                  )}
                  {view === "detailed" && (
                    <div className="reborn-shots-detailed">
                      {filtered.map((s) => (
                        <ScreenshotThumb
                          key={s.id}
                          shot={s}
                          layout="detailed"
                          onOpen={openAt}
                        />
                      ))}
                    </div>
                  )}

                  <div className="reborn-shots-footer">
                    <div className="reborn-shots-stats">
                      <span>
                        <b>{filtered.length}</b> captures
                      </span>
                      <span className="opacity-40">·</span>
                      <span>
                        <b>{totalSizeMb} MB</b> au total
                      </span>
                      <span className="opacity-40">·</span>
                      <span>
                        <b>{TOTAL_PINNED}</b> épinglées
                      </span>
                    </div>
                    <button type="button" className="reborn-shots-head-btn">
                      <Info className="h-3 w-3" />
                      Stockage : — / 50 GB
                    </button>
                  </div>
                </>
              )}
            </div>

            <ScreenshotsFilters
              sort={sort}
              setSort={setSort}
              selectedPlayers={selectedPlayers}
              togglePlayer={togglePlayer}
              selectedServers={selectedServers}
              toggleServer={toggleServer}
              onReset={reset}
            />
          </div>
        )}
      </div>

      <ScreenshotLightbox
        shot={openShot}
        shots={filtered}
        onClose={() => setOpenShot(null)}
        onPrev={() => navOffset(-1)}
        onNext={() => navOffset(1)}
      />
    </div>
  );
}
