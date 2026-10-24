import { motion } from "framer-motion";
import { Share2, Star, Trash2, User } from "lucide-react";
import type { ScreenshotRecord } from "../../lib/screenshots-types";
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

const favLabel = (shot: ScreenshotRecord) =>
  shot.pinned ? `Retirer « ${shot.title} » des favoris` : `Mettre « ${shot.title} » en favori`;

// Trois layouts partagent un même composant pour éviter la duplication du
// click handler / des actions. Le favori / les meta sont communs, seul le
// container et l'organisation visuelle changent. Les captures locales n'ont
// ni serveur ni joueur : ces infos ne s'affichent que si elles existent.
export function ScreenshotThumb({ shot, layout, onOpen, onShare, onDelete, onToggleFavorite }: Props) {
  if (layout === "list") {
    return (
      <button
        type="button"
        onClick={() => onOpen(shot)}
        className="reborn-shots-row"
        aria-label={`Ouvrir ${shot.title}`}
      >
        <span className="reborn-shots-row-thumb" style={shotBackground(shot)} aria-hidden />
        <span className="reborn-shots-row-body">
          <span className="reborn-shots-row-title">
            {shot.pinned && (
              <Star className="mr-1 inline h-3 w-3 text-[var(--color-warning)]" fill="currentColor" aria-hidden />
            )}
            {shot.title}
          </span>
          <span className="reborn-shots-row-sub">
            {[shot.server, shot.player].filter(Boolean).join(" · ") || `${shot.date} · ${shot.time}`}
          </span>
        </span>
        <span className="reborn-shots-row-chip">{shot.resolution}</span>
        <span className="reborn-shots-row-chip">{shot.size}</span>
        <span className="reborn-shots-row-chip">{shot.date}</span>
      </button>
    );
  }

  if (layout === "detailed") {
    const hasTags = Boolean(shot.server || shot.player);
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
                <span>
                  {shot.date} · {shot.time}
                </span>
                <span>{shot.resolution}</span>
                <span>{shot.size}</span>
              </div>
            </div>
            <button
              type="button"
              onClick={() => onToggleFavorite?.(shot)}
              className="reborn-shots-fav-toggle"
              aria-label={favLabel(shot)}
              aria-pressed={shot.pinned}
            >
              <Star
                className="h-3.5 w-3.5 flex-shrink-0"
                style={{ color: shot.pinned ? "var(--color-warning)" : "var(--color-foreground-muted)" }}
                fill={shot.pinned ? "currentColor" : "none"}
                aria-hidden
              />
            </button>
          </div>
          {hasTags && (
            <div className="reborn-shots-detail-tags">
              {shot.server && (
                <span className="reborn-shots-chip">
                  <span className="reborn-shots-chip-dot" style={{ background: "var(--color-accent)" }} />
                  {shot.server}
                </span>
              )}
              {shot.player && (
                <span className="reborn-shots-chip">
                  <User className="h-2.5 w-2.5" aria-hidden />
                  {shot.player}
                </span>
              )}
            </div>
          )}
          <div className="reborn-shots-detail-actions">
            <button type="button" className="reborn-shots-action-btn" onClick={() => onShare?.(shot)}>
              <Share2 className="h-3 w-3" aria-hidden />
              Partager
            </button>
            <button
              type="button"
              className="reborn-shots-action-btn reborn-shots-action-btn--danger ml-auto"
              aria-label={`Supprimer ${shot.title}`}
              title="Supprimer"
              onClick={() => onDelete?.(shot)}
            >
              <Trash2 className="h-3 w-3" aria-hidden />
            </button>
          </div>
        </div>
      </div>
    );
  }

  // grid (défaut) : la vignette et l'étoile sont deux boutons frères (un
  // bouton dans un bouton est invalide et l'étoile était inatteignable au
  // clavier).
  return (
    <motion.div
      className="reborn-shots-thumb-wrap"
      whileHover={{ y: -3 }}
      transition={{ type: "spring", stiffness: 380, damping: 26 }}
    >
      <button
        type="button"
        onClick={() => onOpen(shot)}
        className="reborn-shots-thumb"
        aria-label={`Ouvrir ${shot.title}`}
      >
        <span className="reborn-shots-thumb-art" style={shotBackground(shot)} />
        {shot.server && <span className="reborn-shots-thumb-server">{shot.server}</span>}
        <span className="reborn-shots-thumb-overlay">
          <span className="reborn-shots-thumb-meta">
            <span className="reborn-shots-thumb-title">{shot.title}</span>
            <span className="reborn-shots-thumb-sub">
              <span>{shot.date}</span>
              <span aria-hidden>·</span>
              <span>{shot.size}</span>
            </span>
          </span>
        </span>
      </button>
      <button
        type="button"
        className="reborn-shots-thumb-pin"
        aria-label={favLabel(shot)}
        aria-pressed={shot.pinned}
        data-active={shot.pinned}
        onClick={() => onToggleFavorite?.(shot)}
      >
        <Star className="h-3 w-3" fill={shot.pinned ? "currentColor" : "none"} aria-hidden />
      </button>
    </motion.div>
  );
}
