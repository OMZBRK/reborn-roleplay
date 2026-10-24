import { useEffect, useRef } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  ChevronLeft,
  ChevronRight,
  Clock,
  Image as ImageIcon,
  Maximize2,
  Server,
  Share2,
  Star,
  Trash2,
  User,
  X,
} from "lucide-react";
import type { ScreenshotRecord } from "../../lib/screenshots-types";
import { shotBackground } from "../../lib/screenshots";
import { Portal } from "../system/Portal";

type Props = {
  shot: ScreenshotRecord | null;
  shots: ScreenshotRecord[];
  onClose: () => void;
  onPrev: () => void;
  onNext: () => void;
  onSelect?: (shot: ScreenshotRecord) => void;
  onShare?: (shot: ScreenshotRecord) => void;
  onDelete?: (shot: ScreenshotRecord) => void;
  onToggleFavorite?: (shot: ScreenshotRecord) => void;
  /** Vrai quand une modale s'ouvre par-dessus (partage) : le clavier est à elle. */
  paused?: boolean;
};

// Lightbox plein écran : navigation prev/next via boutons + flèches clavier
// + Échap pour fermer. Filmstrip cliquable de 9 vignettes centrée sur la
// capture courante (pas les 8 premières de la liste).
//
// La nav clavier est interceptée uniquement quand `shot` n'est pas null
// (et que rien n'est ouvert par-dessus) pour ne pas avaler les flèches.
export function ScreenshotLightbox({
  shot,
  shots,
  onClose,
  onPrev,
  onNext,
  onSelect,
  onShare,
  onDelete,
  onToggleFavorite,
  paused = false,
}: Props) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const isOpen = shot !== null;

  // Focus sur « Fermer » à l'ouverture (pas à chaque capture suivante).
  useEffect(() => {
    if (isOpen) closeRef.current?.focus();
  }, [isOpen]);

  useEffect(() => {
    if (!shot || paused) return;
    const onKey = (e: KeyboardEvent) => {
      // Saisie en cours (légende, recherche) : ne pas voler Échap ni les
      // flèches au champ texte.
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === "TEXTAREA" || t.tagName === "INPUT")) return;
      if (e.key === "Escape") onClose();
      else if (e.key === "ArrowLeft") onPrev();
      else if (e.key === "ArrowRight") onNext();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [shot, paused, onClose, onPrev, onNext]);

  const index = shot ? shots.findIndex((s) => s.id === shot.id) : -1;
  const STRIP = 9;
  const stripStart = Math.max(0, Math.min(index - Math.floor(STRIP / 2), shots.length - STRIP));
  const strip = shots.slice(stripStart, stripStart + STRIP);

  return (
    <Portal>
      <AnimatePresence>
        {shot && (
          <motion.div
            className="reborn-shots-lightbox"
            role="dialog"
            aria-modal="true"
            aria-label={`Capture ${shot.title}`}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
          >
            <div className="reborn-shots-lightbox-topbar">
              {index >= 0 && (
                <span className="reborn-shots-lightbox-counter">
                  {index + 1} / {shots.length}
                </span>
              )}
              <button
                type="button"
                className="reborn-shots-lightbox-btn"
                aria-pressed={shot.pinned}
                onClick={() => onToggleFavorite?.(shot)}
              >
                <Star
                  className="h-3.5 w-3.5"
                  fill={shot.pinned ? "currentColor" : "none"}
                  style={shot.pinned ? { color: "var(--color-warning)" } : undefined}
                  aria-hidden
                />
                {shot.pinned ? "Favori" : "Mettre en favori"}
              </button>
              <button
                type="button"
                className="reborn-shots-lightbox-btn"
                onClick={() => onShare?.(shot)}
              >
                <Share2 className="h-3.5 w-3.5" aria-hidden />
                Partager
              </button>
              <button
                type="button"
                className="reborn-shots-lightbox-btn"
                onClick={() => onDelete?.(shot)}
              >
                <Trash2 className="h-3.5 w-3.5" aria-hidden />
                Supprimer
              </button>
              <button
                ref={closeRef}
                type="button"
                onClick={onClose}
                className="reborn-shots-lightbox-btn"
                aria-label="Fermer la grande vue"
              >
                <X className="h-3.5 w-3.5" aria-hidden />
                Fermer
              </button>
            </div>

            <div className="reborn-shots-lightbox-viewport">
              <button
                type="button"
                onClick={onPrev}
                className="reborn-shots-lightbox-nav reborn-shots-lightbox-nav--prev"
                aria-label="Capture précédente"
              >
                <ChevronLeft className="h-5 w-5" aria-hidden />
              </button>
              {shot.src ? (
                <motion.img
                  key={shot.id}
                  src={shot.src}
                  alt={shot.title}
                  className="reborn-shots-lightbox-img"
                  draggable={false}
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ duration: 0.2 }}
                />
              ) : (
                <motion.div
                  key={shot.id}
                  className="reborn-shots-lightbox-img reborn-shots-lightbox-img--art"
                  style={shotBackground(shot)}
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ duration: 0.2 }}
                />
              )}
              <button
                type="button"
                onClick={onNext}
                className="reborn-shots-lightbox-nav reborn-shots-lightbox-nav--next"
                aria-label="Capture suivante"
              >
                <ChevronRight className="h-5 w-5" aria-hidden />
              </button>
            </div>

            <div className="reborn-shots-lightbox-panel">
              <div className="min-w-0">
                <h3 className="reborn-shots-lightbox-title">{shot.title}</h3>
                <div className="reborn-shots-lightbox-meta">
                  {shot.server && (
                    <span>
                      <Server className="h-3 w-3" aria-hidden /> {shot.server}
                    </span>
                  )}
                  {shot.player && (
                    <span>
                      <User className="h-3 w-3" aria-hidden /> {shot.player}
                    </span>
                  )}
                  <span>
                    <Clock className="h-3 w-3" aria-hidden /> {shot.date} · {shot.time}
                  </span>
                  <span>
                    <Maximize2 className="h-3 w-3" aria-hidden /> {shot.resolution}
                  </span>
                  <span>
                    <ImageIcon className="h-3 w-3" aria-hidden /> {shot.size}
                  </span>
                </div>
              </div>
              <div className="reborn-shots-lightbox-strip">
                {strip.map((s) => (
                  <button
                    type="button"
                    key={s.id}
                    className="reborn-shots-lightbox-strip-item"
                    data-active={s.id === shot.id}
                    style={shotBackground(s)}
                    onClick={() => onSelect?.(s)}
                    aria-label={`Afficher ${s.title}`}
                    aria-current={s.id === shot.id ? "true" : undefined}
                  />
                ))}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </Portal>
  );
}
