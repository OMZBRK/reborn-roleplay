import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Send, X } from "lucide-react";
import type { ScreenshotRecord } from "../../lib/screenshots-mock";
import { shotBackground } from "../../lib/screenshots";

type Props = {
  shot: ScreenshotRecord | null;
  busy?: boolean;
  onCancel: () => void;
  onConfirm: (caption: string) => void;
};

const MAX = 280;

// Modale centrée de partage : aperçu de la capture + légende optionnelle.
// Remplace le window.prompt natif (moche, collé en haut de la fenêtre).
export function ShareCaptionModal({ shot, busy, onCancel, onConfirm }: Props) {
  const [caption, setCaption] = useState("");
  const areaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (shot) {
      setCaption("");
      // Focus après l'anim d'ouverture.
      const t = window.setTimeout(() => areaRef.current?.focus(), 60);
      return () => window.clearTimeout(t);
    }
  }, [shot]);

  useEffect(() => {
    if (!shot) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCancel();
      if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) onConfirm(caption.trim());
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [shot, caption, onCancel, onConfirm]);

  return (
    <AnimatePresence>
      {shot && (
        <motion.div
          className="reborn-share-overlay"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.16 }}
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) onCancel();
          }}
        >
          <motion.div
            className="reborn-share-modal"
            initial={{ opacity: 0, scale: 0.96, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.97, y: 6 }}
            transition={{ type: "spring", stiffness: 420, damping: 32 }}
          >
            <div className="reborn-share-head">
              <span className="reborn-share-title">Partager sur le feed</span>
              <button
                type="button"
                className="reborn-share-close"
                onClick={onCancel}
                aria-label="Fermer"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>

            <div className="reborn-share-preview" style={shotBackground(shot)} />

            <label className="reborn-share-label" htmlFor="share-caption">
              Légende <span className="reborn-share-optional">(optionnelle)</span>
            </label>
            <textarea
              id="share-caption"
              ref={areaRef}
              className="reborn-share-textarea"
              value={caption}
              maxLength={MAX}
              rows={3}
              placeholder="Raconte ce moment…"
              onChange={(e) => setCaption(e.target.value)}
            />
            <div className="reborn-share-count">
              {caption.length}/{MAX}
            </div>

            <div className="reborn-share-actions">
              <button
                type="button"
                className="reborn-share-btn"
                onClick={onCancel}
                disabled={busy}
              >
                Annuler
              </button>
              <button
                type="button"
                className="reborn-share-btn reborn-share-btn--primary"
                onClick={() => onConfirm(caption.trim())}
                disabled={busy}
              >
                <Send className="h-3.5 w-3.5" />
                {busy ? "Partage…" : "Partager"}
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
