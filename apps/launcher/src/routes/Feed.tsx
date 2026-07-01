import { useCallback, useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Heart, Loader2, RefreshCw, Users, X } from "lucide-react";
import {
  fetchShotFeed,
  processPendingShares,
  toggleShotLike,
  type ShotView,
} from "../lib/screenshots";

// Feed communautaire : les screenshots partagés par les joueurs (POST /v1/shots
// depuis la galerie). Data via les commandes Tauri shots_feed / shots_toggle_like
// (le launcher détient l'auth). Pagination curseur + like optimiste.

const DATE_FMT = new Intl.DateTimeFormat("fr-FR", {
  day: "2-digit",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
});

function timeLabel(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "" : DATE_FMT.format(d);
}

export function Feed() {
  const [items, setItems] = useState<ShotView[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [openShot, setOpenShot] = useState<ShotView | null>(null);
  const likeInFlight = useRef<Set<string>>(new Set());

  const load = useCallback(async (reset: boolean) => {
    if (reset) {
      setLoading(true);
      setError(null);
      // Publie d'abord les partages mis en file depuis le jeu (best-effort).
      try {
        await processPendingShares();
      } catch {
        // ignore — le feed s'affiche quand même
      }
    } else {
      setLoadingMore(true);
    }
    try {
      const feed = await fetchShotFeed(reset ? undefined : cursor ?? undefined);
      setItems((prev) => (reset ? feed.items : [...prev, ...feed.items]));
      setCursor(feed.nextCursor);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, [cursor]);

  useEffect(() => {
    void load(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ESC ferme la grande vue.
  useEffect(() => {
    if (!openShot) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpenShot(null);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [openShot]);

  // Applique un état de like à la fois à la grille et au shot ouvert en grand.
  function applyLike(id: string, likedByMe: boolean, likeCount: number) {
    setItems((prev) =>
      prev.map((s) => (s.id === id ? { ...s, likedByMe, likeCount } : s)),
    );
    setOpenShot((cur) =>
      cur && cur.id === id ? { ...cur, likedByMe, likeCount } : cur,
    );
  }

  async function onLike(shot: ShotView) {
    if (likeInFlight.current.has(shot.id)) return;
    likeInFlight.current.add(shot.id);
    // Optimiste
    applyLike(shot.id, !shot.likedByMe, shot.likeCount + (shot.likedByMe ? -1 : 1));
    try {
      const res = await toggleShotLike(shot.id);
      applyLike(shot.id, res.liked, res.likeCount);
    } catch {
      applyLike(shot.id, shot.likedByMe, shot.likeCount); // rollback
    } finally {
      likeInFlight.current.delete(shot.id);
    }
  }

  return (
    <div className="reborn-shots-page reborn-radial-bg-strong reborn-pattern-overlay">
      <div className="reborn-shots-scroll">
        <header className="reborn-shots-head">
          <div>
            <h1 className="font-display text-4xl tracking-wide">Feed</h1>
            <div className="reborn-shots-subhead">
              <span>Les captures de la communauté</span>
              <span className="reborn-shots-subhead-dot" />
              <span>Partagées depuis la galerie</span>
            </div>
          </div>
          <button
            type="button"
            className="reborn-shots-head-btn reborn-shots-head-btn--primary"
            onClick={() => void load(true)}
            disabled={loading}
          >
            {loading ? (
              <Loader2 className="h-3 w-3 animate-spin" />
            ) : (
              <RefreshCw className="h-3 w-3" />
            )}
            Actualiser
          </button>
        </header>

        {loading && items.length === 0 ? (
          <div className="reborn-shots-loading">
            <Loader2 className="h-5 w-5 animate-spin" />
            <span>Chargement du feed…</span>
          </div>
        ) : error ? (
          <div className="reborn-feed-empty">
            <Users className="h-8 w-8 opacity-40" />
            <p>Impossible de charger le feed.</p>
            <p className="reborn-feed-empty-sub">{error}</p>
            <button
              type="button"
              className="reborn-shots-head-btn"
              onClick={() => void load(true)}
            >
              <RefreshCw className="h-3 w-3" />
              Réessayer
            </button>
          </div>
        ) : items.length === 0 ? (
          <div className="reborn-feed-empty">
            <Users className="h-8 w-8 opacity-40" />
            <p>Aucune capture partagée pour l'instant.</p>
            <p className="reborn-feed-empty-sub">
              Sois le premier : ouvre une capture dans Screenshots et clique
              « Partager ».
            </p>
          </div>
        ) : (
          <>
            <div className="reborn-feed-grid">
              {items.map((shot) => (
                <article key={shot.id} className="reborn-feed-card">
                  <button
                    type="button"
                    className="reborn-feed-card-img-wrap"
                    onClick={() => setOpenShot(shot)}
                    aria-label="Agrandir"
                  >
                    <img
                      className="reborn-feed-card-img"
                      src={shot.url}
                      alt={shot.caption ?? "Capture"}
                      loading="lazy"
                    />
                  </button>
                  <div className="reborn-feed-card-body">
                    <div className="reborn-feed-card-author">
                      <span className="reborn-feed-avatar">
                        {shot.author.avatarUrl ? (
                          <img src={shot.author.avatarUrl} alt="" />
                        ) : (
                          shot.author.name.charAt(0).toUpperCase()
                        )}
                      </span>
                      <span className="reborn-feed-author-name">
                        {shot.author.name}
                      </span>
                      <span className="reborn-feed-time">
                        {timeLabel(shot.createdAt)}
                      </span>
                    </div>
                    {shot.caption && (
                      <p className="reborn-feed-caption">{shot.caption}</p>
                    )}
                    <button
                      type="button"
                      className="reborn-feed-like"
                      data-liked={shot.likedByMe}
                      onClick={() => onLike(shot)}
                      aria-label={shot.likedByMe ? "Ne plus aimer" : "Aimer"}
                    >
                      <Heart
                        className="h-3.5 w-3.5"
                        fill={shot.likedByMe ? "currentColor" : "none"}
                      />
                      <span>{shot.likeCount}</span>
                    </button>
                  </div>
                </article>
              ))}
            </div>

            {cursor && (
              <div className="reborn-feed-more">
                <button
                  type="button"
                  className="reborn-shots-head-btn"
                  onClick={() => void load(false)}
                  disabled={loadingMore}
                >
                  {loadingMore ? (
                    <Loader2 className="h-3 w-3 animate-spin" />
                  ) : (
                    <RefreshCw className="h-3 w-3" />
                  )}
                  Charger plus
                </button>
              </div>
            )}
          </>
        )}
      </div>

      <AnimatePresence>
        {openShot && (
          <motion.div
            className="reborn-feed-lightbox"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.16 }}
            onMouseDown={(e) => {
              if (e.target === e.currentTarget) setOpenShot(null);
            }}
          >
            <button
              type="button"
              className="reborn-feed-lightbox-close"
              onClick={() => setOpenShot(null)}
              aria-label="Fermer"
            >
              <X className="h-4 w-4" />
            </button>
            <motion.div
              className="reborn-feed-lightbox-inner"
              initial={{ opacity: 0, scale: 0.97 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.98 }}
              transition={{ type: "spring", stiffness: 400, damping: 32 }}
            >
              <img
                className="reborn-feed-lightbox-img"
                src={openShot.url}
                alt={openShot.caption ?? "Capture"}
              />
              <div className="reborn-feed-lightbox-bar">
                <span className="reborn-feed-avatar">
                  {openShot.author.avatarUrl ? (
                    <img src={openShot.author.avatarUrl} alt="" />
                  ) : (
                    openShot.author.name.charAt(0).toUpperCase()
                  )}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="reborn-feed-author-name">
                    {openShot.author.name}
                  </div>
                  {openShot.caption && (
                    <div className="reborn-feed-lightbox-caption">
                      {openShot.caption}
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  className="reborn-feed-like"
                  data-liked={openShot.likedByMe}
                  onClick={() => onLike(openShot)}
                  aria-label={openShot.likedByMe ? "Ne plus aimer" : "Aimer"}
                >
                  <Heart
                    className="h-3.5 w-3.5"
                    fill={openShot.likedByMe ? "currentColor" : "none"}
                  />
                  <span>{openShot.likeCount}</span>
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
