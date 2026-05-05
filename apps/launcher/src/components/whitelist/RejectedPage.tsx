import { motion } from "framer-motion";
import { Loader2, Trash2, X } from "lucide-react";

type Props = {
  onRetry: () => void;
  onWithdraw?: () => void;
  withdrawing?: boolean;
  reason?: string;
};

const DEFAULT_REASON =
  "Objectif très peu présent et il manque de jeu proposé aux autres joueurs. Réfléchis aux interactions concrètes que ton personnage cherche à provoquer, pas seulement à son histoire personnelle.";

export function RejectedPage({
  onRetry,
  onWithdraw,
  withdrawing = false,
  reason,
}: Props) {
  return (
    <div className="wl-status-result">
      <motion.div
        className="wl-result-icon wl-result-icon-danger"
        initial={{ scale: 0, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
      >
        <X size={28} strokeWidth={2.5} />
      </motion.div>
      <motion.h1
        className="wl-result-title"
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.15 }}
      >
        Candidature refusée
      </motion.h1>
      <motion.div
        className="wl-result-section"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.4, delay: 0.3 }}
      >
        <div className="wl-result-section-label">Raison du refus</div>
        <div className="wl-result-card">{reason ?? DEFAULT_REASON}</div>
      </motion.div>
      <motion.div
        className="wl-result-actions"
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.45 }}
      >
        {onWithdraw && (
          <button
            type="button"
            className="wl-btn-ghost"
            onClick={onWithdraw}
            disabled={withdrawing}
            style={{
              borderColor: "rgba(239, 68, 68, 0.4)",
              color: "var(--color-danger)",
            }}
          >
            {withdrawing ? (
              <Loader2 size={14} className="animate-spin" />
            ) : (
              <Trash2 size={14} />
            )}
            Repartir de zéro
          </button>
        )}
        <button
          type="button"
          className="wl-btn-primary glow wl-result-cta"
          onClick={onRetry}
        >
          Refaire une candidature
        </button>
      </motion.div>
    </div>
  );
}
