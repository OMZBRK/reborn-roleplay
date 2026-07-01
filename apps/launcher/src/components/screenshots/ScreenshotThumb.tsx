import { motion } from "framer-motion";
import { Share2, Star, Trash2, User } from "lucide-react";
import type { ScreenshotRecord } from "../../lib/screenshots-mock";
import { shotBackground } from "../../lib/screenshots";
import type { ScreenshotView } from "./ScreenshotsToolbar";

type Props = {
  shot: ScreenshotRecord;
  layout: ScreenshotView;
  onOpen: (shot: ScreenshotRecord) => void;
  onShare?: (shot: ScreenshotRecord) => void;
  onDelete?: (shot: ScreenshotRecord) => void;
  onToggleFavorite?: (shot: ScreenshotRecord) => void;
};

// Trois layouts partagent un meme composant pour eviter la duplication du
// click handler / des actions. Le pin / les meta sont communes, seul le
// container et l'organisation visuelle changent.
export function ScreenshotThumb({
  shot,
  layout,
  onOpen,
  onShare,
  onDelete,
  onToggleFavorite,
}: Props) {
  if (layout === "list") {
    return (
      <button
        type="button"
        onClick={() => onOpen(shot)}
        className="reborn-shots-row"
      >
        <span
          className="reborn-shots-row-thumb"
          style={shotBackground(shot)}
        />
        <span className="reborn-shots-row-body">
          <span className="reborn-shots-row-title">{shot.title}</span>
          <span className="reborn-shots-row-sub">
            {shot.server} · {shot.player}
          </span>
        </span>
        <span className="reborn-shots-row-chip">{shot.resolution}</span>
        <span className="reborn-shots-row-chip">{shot.size}</span>
        <span className="reborn-shots-row-chip">{shot.date}</span>
      </button>
    );
  }

  if (layout === "detailed") {
    return (
      <div className="reborn-shots-detail-card">
        <button
          type="button"
          onClick={() => onOpen(shot)}
          className="reborn-shots-detail-art"
          style={shotBackground(shot)}
          aria-label={`Ouvrir ${shot.title}`}
        />
        <div className="reborn-shots-detail-body">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
              <h4 className="reborn-shots-detail-title">{shot.title}</h4>
              <div className="reborn-shots-detail-meta">
                <span>{shot.date} · {shot.time}</span>
                <span>{shot.resolution}</span>
                <span>{shot.size}</span>
              </div>
            </div>
            <button
              type="button"
              onClick={() => onToggleFavorite?.(shot)}
              className="reborn-shots-fav-toggle"
              aria-label={shot.pinned ? "Retirer des favoris" : "Ajouter aux favoris"}
            >
              <Star
                className="h-3.5 w-3.5 flex-shrink-0"
                style={{ color: shot.pinned ? "var(--color-warning)" : "var(--color-text-muted)" }}
                fill={shot.pinned ? "currentColor" : "none"}
              />
            </button>
          </div>
          <div className="reborn-shots-detail-tags">
            <span className="reborn-shots-chip">
              <span
                className="reborn-shots-chip-dot"
                style={{ background: "var(--color-accent)" }}
              />
              {shot.server}
            </span>
            <span className="reborn-shots-chip">
              <User className="h-2.5 w-2.5" />
              {shot.player}
            </span>
          </div>
          <div className="reborn-shots-detail-actions">
            <button
              type="button"
              className="reborn-shots-action-btn"
              onClick={() => onShare?.(shot)}
            >
              <Share2 className="h-3 w-3" />
              Partager
            </button>
            <button
              type="button"
              className="reborn-shots-action-btn reborn-shots-action-btn--danger ml-auto"
              aria-label="Supprimer"
              onClick={() => onDelete?.(shot)}
            >
              <Trash2 className="h-3 w-3" />
            </button>
          </div>
        </div>
      </div>
    );
  }

  // grid (default)
  return (
    <motion.button
      type="button"
      onClick={() => onOpen(shot)}
      whileHover={{ y: -3 }}
      transition={{ type: "spring", stiffness: 380, damping: 26 }}
      className="reborn-shots-thumb"
    >
      <span className="reborn-shots-thumb-art" style={shotBackground(shot)} />
      {shot.server && (
        <span className="reborn-shots-thumb-server">{shot.server}</span>
      )}
      <span
        className="reborn-shots-thumb-pin"
        role="button"
        tabIndex={-1}
        aria-label={shot.pinned ? "Retirer des favoris" : "Ajouter aux favoris"}
        data-active={shot.pinned}
        onClick={(e) => {
          e.stopPropagation();
          onToggleFavorite?.(shot);
        }}
        style={{ opacity: shot.pinned ? 1 : 0.55 }}
      >
        <Star className="h-3 w-3" fill={shot.pinned ? "currentColor" : "none"} />
      </span>
      <span className="reborn-shots-thumb-overlay">
        <span className="reborn-shots-thumb-meta">
          <span className="reborn-shots-thumb-title">{shot.title}</span>
          <span className="reborn-shots-thumb-sub">
            <span>{shot.date}</span>
            <span>·</span>
            <span>{shot.size}</span>
          </span>
        </span>
      </span>
    </motion.button>
  );
}
