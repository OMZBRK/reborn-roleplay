import { useRef, useState, type ComponentType } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion } from "framer-motion";

type Props = {
  icon: ComponentType<{ size?: number; className?: string }>;
  label: string;
  active?: boolean;
  /** Texte (ex: "2") affiche dans un cercle danger en haut-droite. Prioritaire sur dotBadge. */
  badge?: string;
  /** Pastille bleue 8px sans texte, top-right. Ignoree si badge est defini. */
  dotBadge?: boolean;
  onClick?: () => void;
};

// Bouton de la sidebar fine 72px : icone centree, badge optionnel,
// tooltip qui apparait au hover. Le tooltip est rendu via portal dans
// `document.body` pour echapper au clipping `overflow-y-auto` du nav
// parent (sans portal, le tooltip a droite de l'icone etait coupe).
export function SidebarIconButton({
  icon: Icon,
  label,
  active,
  badge,
  dotBadge,
  onClick,
}: Props) {
  const [hover, setHover] = useState(false);
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  const btnRef = useRef<HTMLButtonElement>(null);

  function showTooltip() {
    const el = btnRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    setPos({
      top: rect.top + rect.height / 2,
      left: rect.right + 8,
    });
    setHover(true);
  }

  function hideTooltip() {
    setHover(false);
  }

  return (
    <div
      className="relative"
      onMouseEnter={showTooltip}
      onMouseLeave={hideTooltip}
    >
      <button
        ref={btnRef}
        type="button"
        onClick={onClick}
        aria-label={label}
        data-active={active ? "true" : undefined}
        className="reborn-sidebar-btn"
      >
        <Icon size={18} />
        {badge ? (
          <span className="reborn-sidebar-badge">{badge}</span>
        ) : dotBadge ? (
          <span className="reborn-sidebar-dot" />
        ) : null}
      </button>

      {createPortal(
        <AnimatePresence>
          {hover && pos && (
            <motion.div
              key="tooltip"
              initial={{ opacity: 0, x: -4 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -4 }}
              transition={{ duration: 0.15 }}
              className="reborn-sidebar-tooltip"
              style={{ top: pos.top, left: pos.left }}
            >
              {label}
            </motion.div>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </div>
  );
}
