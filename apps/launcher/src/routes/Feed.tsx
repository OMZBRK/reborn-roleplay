import { useCallback, useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { AlertCircle, Heart, Loader2, RefreshCw, Users, X } from "lucide-react";
import {
  fetchShotFeed,
  processPendingShares,
  toggleShotLike,
  type ShotView,
} from "../lib/screenshots";
import { formatIpcError } from "../lib/errors";
import { Portal } from "../components/system/Portal";

// Feed communautaire : les screenshots partagés par les joueurs (POST /v1/shots
// depuis la galerie). Data via les commandes Tauri shots_feed / shots_toggle_like
// (le launcher détient l'auth). Pagination curseur + like optimiste.
// Toute erreur affiche la cause réelle (formatIpcError) : chargement → écran
// d'erreur avec Réessayer, page suivante / like / partages en attente →
// message dans la page, sans perdre ce qui est déjà affiché.

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

const NOTICE_TTL_MS = 6000;

export function Feed() {
  const [items, setItems] = useState<ShotView[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [moreError, setMoreError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [openShot, setOpenShot] = useState<ShotView | null>(null);
  const likeInFlight = useRef<Set<string>>(new Set());
  const noticeTimer = useRef<number | null>(null);
  const closeRef = useRef<HTMLButtonElement>(null);

  const showNotice = useCallback((msg: string) => {
    setNotice(msg);
    if (noticeTimer.current !== null) window.clearTimeout(noticeTimer.current);
    noticeTimer.current = window.setTimeout(() => setNotice(null), NOTICE_TTL_MS);
  }, []);
  useEffect(
    () => () => {
      if (noticeTimer.current !== null) window.clearTimeout(noticeTimer.current);
    },
    [],
  );

  const load = useCallback(
    async (reset: boolean) => {
      if (reset) {
        setLoading(true);
        setError(null);
        setMoreError(null);
        // Publie d'abord les partages mis en file depuis le jeu : un échec
        // n'empêche pas d'afficher le feed, mais il est signalé.
        try {
          const res = await processPendingShares();
          const n = res.failed;
          if (n > 0) {
            showNotice(
              n > 1
                ? `${n} captures partagées depuis le jeu n'ont pas pu être publiées : réessaie avec « Actualiser ».`
                : "1 capture partagée depuis le jeu n'a pas pu être publiée : réessaie avec « Actualiser ».",
            );
          }
        } catch (e) {
          showNotice(formatIpcError(e, "Les partages faits en jeu n'ont pas pu être publiés"));
        }
      } else {
        setLoadingMore(true);
        setMoreError(null);
      }
      try {
        const feed = await fetchShotFeed(reset ? undefined : (cursor ?? undefined));
        setItems((prev) => (reset ? feed.items : [...prev, ...feed.items]));
        setCursor(feed.nextCursor);
      } catch (e) {
        if (reset) setError(formatIpcError(e));
        else setMoreError(formatIpcError(e, "Impossible de charger la suite du feed"));
      } finally {
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [cursor, showNotice],
  );

  useEffect(() => {
    void load(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ESC ferme la grande vue ; le focus part sur « Fermer » à l'ouverture.
  useEffect(() => {
    if (!openShot) return;
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpenShot(null);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [openShot]);

  // Applique un état de like à la fois à la grille et au shot ouvert en grand.
  function applyLike(id: string, likedByMe: boolean, likeCount: number) {
    setItems((prev) => prev.map((s) => (s.id === id ? { ...s, likedByMe, likeCount } : s)));
    setOpenShot((cur) => (cur && cur.id === id ? { ...cur, likedByMe, likeCount } : cur));
  }

  async function onLike(shot: ShotView) {
    if (likeInFlight.current.has(shot.id)) return;
    likeInFlight.current.add(shot.id);
    // Optimiste
    applyLike(shot.id, !shot.likedByMe, shot.likeCount + (shot.likedByMe ? -1 : 1));
    try {
      const res = await toggleShotLike(shot.id);
      applyLike(shot.id, res.liked, res.likeCount);
    } catch (e) {
      applyLike(shot.id, shot.likedByMe, shot.likeCount); // rollback
      const action = shot.likedByMe ? "retirer ton like" : "enregistrer ton like";
      showNotice(formatIpcError(e, `Impossible de ${action}`));
    } finally {
      likeInFlight.current.delete(shot.id);
    }
  }

  return (
    <div className="reborn-shots-page reborn-radial-bg-strong reborn-pattern-overlay">
      <div className="reborn-shots-scroll" aria-busy={loading || undefined}>
        <header className="reborn-shots-head">
          <div>
            <h1 className="font-display text-4xl tracking-wide">Feed</h1>
            <div className="reborn-shots-subhead">
              <span>Les captures de la communauté</span>
              <span className="reborn-shots-subhead-dot" aria-hidden />
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
              <Loader2 className="h-3 w-3 animate-spin" aria-hidden />
            ) : (
              <RefreshCw className="h-3 w-3" aria-hidden />
            )}
            Actualiser
          </button>
        </header>

        {notice && (
          <div className="reborn-feed-notice" role="status">
            <AlertCircle className="h-3.5 w-3.5 flex-shrink-0" aria-hidden />
            <span className="min-w-0 flex-1">{notice}</span>
            <button
              type="button"
              className="reborn-feed-notice-close"
              onClick={() => setNotice(null)}
              aria-label="Masquer ce message"
            >
              <X className="h-3.5 w-3.5" aria-hidden />
            </button>
          </div>
        )}

        {loading && items.length === 0 ? (
          <div className="reborn-shots-loading" role="status">
            <Loader2 className="h-5 w-5 animate-spin" aria-hidden />
            <span>Chargement du feed…</span>
          </div>
        ) : error && items.length === 0 ? (
          <div className="reborn-feed-empty" role="alert">
            <Users className="h-8 w-8 opacity-40" aria-hidden />
            <p>Impossible de charger le feed.</p>
            <p className="reborn-feed-empty-sub">{error}</p>
            <button type="button" className="reborn-shots-head-btn" onClick={() => void load(true)}>
              <RefreshCw className="h-3 w-3" aria-hidden />
              Réessayer
            </button>
          </div>
        ) : items.length === 0 ? (
          <div className="reborn-feed-empty">
            <Users className="h-8 w-8 opacity-40" aria-hidden />
            <p>Aucune capture partagée pour l'instant.</p>
            <p className="reborn-feed-empty-sub">
              Sois le premier : ouvre une capture dans Screenshots et clique « Partager ».
            </p>
          </div>
        ) : (
          <>
            {error && (
              <p className="reborn-feed-notice" role="alert">
                <AlertCircle className="h-3.5 w-3.5 flex-shrink-0" aria-hidden />
                <span className="min-w-0 flex-1">
                  Actualisation impossible, le feed affiché date du dernier chargement. {error}
                </span>
              </p>
            )}
            <div className="reborn-feed-grid">
              {items.map((shot) => (
                <article key={shot.id} className="reborn-feed-card">
                  <button
                    type="button"
                    className="reborn-feed-card-img-wrap"
                    onClick={() => setOpenShot(shot)}
                    aria-label={`Agrandir la capture de ${shot.author.name}`}
                  >
                    <img
                      className="reborn-feed-card-img"
                      src={shot.url}
                      alt={shot.caption ?? `Capture de ${shot.author.name}`}
                      loading="lazy"
                      decoding="async"
                    />
                  </button>
                  <div className="reborn-feed-card-body">
                    <div className="reborn-feed-card-author">
                      <span className="reborn-feed-avatar" aria-hidden>
                        {shot.author.avatarUrl ? (
                          <img src={shot.author.avatarUrl} alt="" loading="lazy" />
                        ) : (
                          shot.author.name.charAt(0).toUpperCase()
                        )}
                      </span>
                      <span className="reborn-feed-author-name">{shot.author.name}</span>
                      <span className="reborn-feed-time">{timeLabel(shot.createdAt)}</span>
                    </div>
                    {shot.caption && <p className="reborn-feed-caption">{shot.caption}</p>}
                    <LikeButton shot={shot} onLike={onLike} />
                  </div>
                </article>
              ))}
            </div>

            {(cursor || moreError) && (
              <div className="reborn-feed-more">
                {moreError && (
                  <p className="reborn-feed-more-error" role="alert">
                    {moreError}
                  </p>
                )}
                {cursor && (
                  <button
                    type="button"
                    className="reborn-shots-head-btn"
                    onClick={() => void load(false)}
                    disabled={loadingMore}
                  >
                    {loadingMore ? (
                      <Loader2 className="h-3 w-3 animate-spin" aria-hidden />
                    ) : (
                      <RefreshCw className="h-3 w-3" aria-hidden />
                    )}
                    {moreError ? "Réessayer" : "Charger plus"}
                  </button>
                )}
              </div>
            )}
          </>
        )}
      </div>

      <Portal>
        <AnimatePresence>
          {openShot && (
            <motion.div
              className="reborn-feed-lightbox"
              role="dialog"
              aria-modal="true"
              aria-label={`Capture de ${openShot.author.name}`}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.16 }}
              onMouseDown={(e) => {
                if (e.target === e.currentTarget) setOpenShot(null);
              }}
            >
              <button
                ref={closeRef}
                type="button"
                className="reborn-feed-lightbox-close"
                onClick={() => setOpenShot(null)}
                aria-label="Fermer la grande vue"
              >
                <X className="h-4 w-4" aria-hidden />
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
                  alt={openShot.caption ?? `Capture de ${openShot.author.name}`}
                />
                <div className="reborn-feed-lightbox-bar">
                  <span className="reborn-feed-avatar" aria-hidden>
                    {openShot.author.avatarUrl ? (
                      <img src={openShot.author.avatarUrl} alt="" />
                    ) : (
                      openShot.author.name.charAt(0).toUpperCase()
                    )}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="reborn-feed-author-name">{openShot.author.name}</div>
                    {openShot.caption && (
                      <div className="reborn-feed-lightbox-caption">{openShot.caption}</div>
                    )}
                  </div>
                  <LikeButton shot={openShot} onLike={onLike} />
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>
      </Portal>
    </div>
  );
}

function LikeButton({ shot, onLike }: { shot: ShotView; onLike: (s: ShotView) => void }) {
  return (
    <button
      type="button"
      className="reborn-feed-like"
      data-liked={shot.likedByMe}
      onClick={() => onLike(shot)}
      aria-pressed={shot.likedByMe}
      aria-label={`${shot.likedByMe ? "Ne plus aimer" : "Aimer"} — ${shot.likeCount} j'aime`}
    >
      <Heart className="h-3.5 w-3.5" fill={shot.likedByMe ? "currentColor" : "none"} aria-hidden />
      <span>{shot.likeCount}</span>
    </button>
  );
}
