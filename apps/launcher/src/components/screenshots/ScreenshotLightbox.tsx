import { useEffect } from "react";
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
import type { ScreenshotRecord } from "../../lib/screenshots-mock";
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
};

// Lightbox plein-ecran : navigation prev/next via boutons + fleches clavier
// + ESC pour fermer. Filmstrip cliquable de 9 vignettes centree sur la
// capture courante (pas les 8 premieres de la liste).
//
// La nav clavier est interceptee uniquement quand `shot` n'est pas null
// pour eviter d'avaler les fleches sur les autres pages.
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
}: Props) {
  useEffect(() => {
    if (!shot) return;
    const onKey = (e: KeyboardEvent) => {
      // La modale de partage s'ouvre par-dessus : ne pas lui voler ESC ni
      // les fleches pendant la saisie de la legende.
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === "TEXTAREA" || t.tagName === "INPUT")) return;
      if (e.key === "Escape") onClose();
      else if (e.key === "ArrowLeft") onPrev();
      else if (e.key === "ArrowRight") onNext();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [shot, onClose, onPrev, onNext]);

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
                aria-label={shot.pinned ? "Retirer des favoris" : "Ajouter aux favoris"}
                onClick={() => onToggleFavorite?.(shot)}
              >
                <Star
                  className="h-3.5 w-3.5"
                  fill={shot.pinned ? "currentColor" : "none"}
                  style={shot.pinned ? { color: "var(--color-warning)" } : undefined}
                />
                {shot.pinned ? "Favori" : "Favoris"}
              </button>
              <button
                type="button"
                className="reborn-shots-lightbox-btn"
                aria-label="Partager"
                onClick={() => onShare?.(shot)}
              >
                <Share2 className="h-3.5 w-3.5" />
                Partager
              </button>
              <button
                type="button"
                className="reborn-shots-lightbox-btn"
                aria-label="Supprimer"
                onClick={() => onDelete?.(shot)}
              >
                <Trash2 className="h-3.5 w-3.5" />
                Supprimer
              </button>
              <button
                type="button"
                onClick={onClose}
                className="reborn-shots-lightbox-btn"
                aria-label="Fermer"
              >
                <X className="h-3.5 w-3.5" />
                Fermer
              </button>
            </div>

            <div className="reborn-shots-lightbox-viewport">
              <button
                type="button"
                onClick={onPrev}
                className="reborn-shots-lightbox-nav reborn-shots-lightbox-nav--prev"
                aria-label="Précédent"
              >
                <ChevronLeft className="h-5 w-5" />
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
                aria-label="Suivant"
              >
                <ChevronRight className="h-5 w-5" />
              </button>
            </div>

            <div className="reborn-shots-lightbox-panel">
              <div className="min-w-0">
                <h3 className="reborn-shots-lightbox-title">{shot.title}</h3>
                <div className="reborn-shots-lightbox-meta">
                  <span><Server className="h-3 w-3" /> {shot.server}</span>
                  <span><User className="h-3 w-3" /> {shot.player}</span>
                  <span><Clock className="h-3 w-3" /> {shot.date} · {shot.time}</span>
                  <span><Maximize2 className="h-3 w-3" /> {shot.resolution}</span>
                  <span><ImageIcon className="h-3 w-3" /> {shot.size}</span>
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
                    aria-label={s.title}
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
