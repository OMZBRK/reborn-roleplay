import { useCallback, useEffect, useMemo, useState } from "react";
import { FolderOpen, Loader2, RefreshCw } from "lucide-react";
import type { ScreenshotRecord } from "../lib/screenshots-mock";
import {
  deleteScreenshot,
  listScreenshots,
  openScreenshotsFolder,
  shareScreenshot,
  toRecord,
  toggleScreenshotFavorite,
} from "../lib/screenshots";
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

// Page Screenshots — branchée sur le vrai dossier de captures via les
// commandes Tauri `screenshots_*` (cf lib/screenshots.ts). Les captures et
// les favoris sont partagés avec la galerie in-game du mod-hud (même dossier
// de jeu). Le filtre / tri / recherche restent côté frontend.

function sizeBytesOf(s: ScreenshotRecord): number {
  return s.sizeBytes ?? 0;
}

function sortKey(s: ScreenshotRecord): number {
  return s.modifiedMs ?? 0;
}

export function Screenshots() {
  const [records, setRecords] = useState<ScreenshotRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [flash, setFlash] = useState<string | null>(null);

  const [query, setQuery] = useState("");
  const [view, setView] = useState<ScreenshotView>("grid");
  const [sort, setSort] = useState<ScreenshotSort>("newest");
  const [onlyFavorites, setOnlyFavorites] = useState(false);
  const [openShot, setOpenShot] = useState<ScreenshotRecord | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const items = await listScreenshots();
      setRecords(items.map(toRecord));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  function toast(msg: string) {
    setFlash(msg);
    window.setTimeout(() => setFlash((cur) => (cur === msg ? null : cur)), 2600);
  }

  const filtered = useMemo<ScreenshotRecord[]>(() => {
    let next = records.slice();

    if (onlyFavorites) next = next.filter((s) => s.pinned);
    if (query) {
      const q = query.toLowerCase();
      next = next.filter((s) => s.title.toLowerCase().includes(q));
    }

    switch (sort) {
      case "newest":
        next.sort((a, b) => sortKey(b) - sortKey(a));
        break;
      case "oldest":
        next.sort((a, b) => sortKey(a) - sortKey(b));
        break;
      case "size":
        next.sort((a, b) => sizeBytesOf(b) - sizeBytesOf(a));
        break;
    }
    return next;
  }, [records, query, sort, onlyFavorites]);

  const totalSizeMb = useMemo(() => {
    const sum = filtered.reduce((acc, s) => acc + sizeBytesOf(s), 0);
    return (sum / 1_000_000).toFixed(1);
  }, [filtered]);

  const pinnedCount = useMemo(
    () => records.filter((s) => s.pinned).length,
    [records],
  );

  function reset() {
    setQuery("");
    setSort("newest");
    setOnlyFavorites(false);
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

  // ── Actions ────────────────────────────────────────────────────────

  async function handleToggleFavorite(shot: ScreenshotRecord) {
    if (!shot.fileName) return;
    // Optimiste : bascule localement puis persiste sur disque.
    setRecords((rs) =>
      rs.map((r) => (r.id === shot.id ? { ...r, pinned: !r.pinned } : r)),
    );
    setOpenShot((cur) =>
      cur && cur.id === shot.id ? { ...cur, pinned: !cur.pinned } : cur,
    );
    try {
      await toggleScreenshotFavorite(shot.fileName);
    } catch {
      void reload(); // rollback via la source de vérité disque
    }
  }

  async function handleDelete(shot: ScreenshotRecord) {
    if (!shot.fileName) return;
    const ok = window.confirm(`Supprimer définitivement « ${shot.title} » ?`);
    if (!ok) return;
    setBusy(shot.id);
    try {
      await deleteScreenshot(shot.fileName);
      setRecords((rs) => rs.filter((r) => r.id !== shot.id));
      setOpenShot((cur) => (cur && cur.id === shot.id ? null : cur));
      toast("Capture supprimée");
    } catch (e) {
      toast(`Échec suppression : ${String(e)}`);
    } finally {
      setBusy(null);
    }
  }

  async function handleShare(shot: ScreenshotRecord) {
    if (!shot.fileName) return;
    const caption = window.prompt(
      "Légende (optionnelle) pour le partage sur le feed :",
      "",
    );
    if (caption === null) return; // annulé
    setBusy(shot.id);
    try {
      await shareScreenshot(shot.fileName, caption.trim() || undefined);
      toast("Partagé sur le feed 🎉");
    } catch (e) {
      toast(`Échec du partage : ${String(e)}`);
    } finally {
      setBusy(null);
    }
  }

  async function handleOpenFolder() {
    try {
      await openScreenshotsFolder();
    } catch (e) {
      toast(`Impossible d'ouvrir le dossier : ${String(e)}`);
    }
  }

  const isFresh = !loading && records.length === 0;
  const isFiltered = filtered.length === 0 && !isFresh && !loading;

  return (
    <div className="reborn-shots-page reborn-radial-bg-strong reborn-pattern-overlay">
      <div className="reborn-shots-scroll" aria-busy={busy ? true : undefined}>
        <header className="reborn-shots-head">
          <div>
            <h1 className="font-display text-4xl tracking-wide">Screenshots</h1>
            <div className="reborn-shots-subhead">
              <span>Tes captures in-game</span>
              <span className="reborn-shots-subhead-dot" />
              <span>Synchronisées avec la galerie du jeu</span>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {flash && <span className="reborn-shots-flash">{flash}</span>}
            <button
              type="button"
              className="reborn-shots-head-btn"
              onClick={handleOpenFolder}
            >
              <FolderOpen className="h-3 w-3" />
              Ouvrir le dossier
            </button>
            <button
              type="button"
              className="reborn-shots-head-btn reborn-shots-head-btn--primary"
              onClick={() => void reload()}
              disabled={loading}
            >
              {loading ? (
                <Loader2 className="h-3 w-3 animate-spin" />
              ) : (
                <RefreshCw className="h-3 w-3" />
              )}
              Actualiser
            </button>
          </div>
        </header>

        {loading && records.length === 0 ? (
          <div className="reborn-shots-loading">
            <Loader2 className="h-5 w-5 animate-spin" />
            <span>Lecture des captures…</span>
          </div>
        ) : isFresh ? (
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
                          onShare={handleShare}
                          onDelete={handleDelete}
                          onToggleFavorite={handleToggleFavorite}
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
                          onShare={handleShare}
                          onDelete={handleDelete}
                          onToggleFavorite={handleToggleFavorite}
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
                          onShare={handleShare}
                          onDelete={handleDelete}
                          onToggleFavorite={handleToggleFavorite}
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
                        <b>{pinnedCount}</b> favoris
                      </span>
                    </div>
                  </div>
                </>
              )}
            </div>

            <ScreenshotsFilters
              sort={sort}
              setSort={setSort}
              players={[]}
              selectedPlayers={[]}
              togglePlayer={() => {}}
              servers={[]}
              selectedServers={[]}
              toggleServer={() => {}}
              onlyFavorites={onlyFavorites}
              setOnlyFavorites={setOnlyFavorites}
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
        onShare={handleShare}
        onDelete={handleDelete}
        onToggleFavorite={handleToggleFavorite}
      />
    </div>
  );
}
