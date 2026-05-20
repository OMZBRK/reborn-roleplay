import { useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  ChevronLeft,
  ChevronRight,
  Clock,
  Download,
  Image as ImageIcon,
  Maximize2,
  Server,
  Share2,
  User,
  X,
} from "lucide-react";
import type { ScreenshotRecord } from "../../lib/screenshots-mock";

type Props = {
  shot: ScreenshotRecord | null;
  shots: ScreenshotRecord[];
  onClose: () => void;
  onPrev: () => void;
  onNext: () => void;
};

// Lightbox plein-ecran : navigation prev/next via boutons + fleches clavier
// + ESC pour fermer. Filmstrip horizontal de jusqu'a 8 vignettes en bas.
//
// La nav clavier est interceptee uniquement quand `shot` n'est pas null
// pour eviter d'avaler les fleches sur les autres pages.
export function ScreenshotLightbox({ shot, shots, onClose, onPrev, onNext }: Props) {
  useEffect(() => {
    if (!shot) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      else if (e.key === "ArrowLeft") onPrev();
      else if (e.key === "ArrowRight") onNext();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [shot, onClose, onPrev, onNext]);

  return (
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
            <button
              type="button"
              className="reborn-shots-lightbox-btn"
              aria-label="Télécharger"
            >
              <Download className="h-3.5 w-3.5" />
              Télécharger
            </button>
            <button
              type="button"
              className="reborn-shots-lightbox-btn"
              aria-label="Partager"
            >
              <Share2 className="h-3.5 w-3.5" />
              Partager
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
            <motion.div
              key={shot.id}
              className="reborn-shots-lightbox-img"
              style={{ background: shot.art }}
              initial={{ opacity: 0, scale: 0.98 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.2 }}
            />
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
              {shots.slice(0, 8).map((s) => (
                <div
                  key={s.id}
                  className="reborn-shots-lightbox-strip-item"
                  data-active={s.id === shot.id}
                  style={{ background: s.art }}
                />
              ))}
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
