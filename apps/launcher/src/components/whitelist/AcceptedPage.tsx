import { motion } from "framer-motion";
import { ArrowRight, Check } from "lucide-react";
import { useNavigate } from "react-router";

type Props = {
  onCreate: () => void;
  staffNote?: string;
  staffName?: string;
};

const DEFAULT_NOTE =
  "« Excellente candidature, l'arc autour de ton frère est très prometteur. Bienvenue à Konoha ! » — ModoZen";

export function AcceptedPage({ onCreate, staffNote }: Props) {
  const navigate = useNavigate();

  return (
    <div className="wl-status-result">
      <motion.div
        className="wl-result-icon wl-result-icon-success"
        initial={{ scale: 0, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.5, ease: [0.34, 1.56, 0.64, 1] }}
      >
        <Check size={30} strokeWidth={3} />
      </motion.div>
      <motion.h1
        className="wl-result-title"
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.15 }}
      >
        Candidature acceptée
      </motion.h1>
      <motion.p
        className="wl-result-lead"
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.25 }}
      >
        Bienvenue dans le RP Reborn. Tu peux maintenant créer ton personnage et
        te connecter au serveur.
      </motion.p>

      <motion.div
        className="wl-staff-note"
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.4 }}
      >
        <div className="wl-staff-note-label">Note du staff</div>
        <div className="wl-staff-note-text">{staffNote ?? DEFAULT_NOTE}</div>
      </motion.div>

      <motion.div
        className="wl-result-actions"
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.55 }}
      >
        <button
          type="button"
          className="wl-btn-ghost"
          onClick={() => navigate("/rules")}
        >
          Voir le règlement
        </button>
        <button
          type="button"
          className="wl-btn-primary glow wl-btn-cta-big"
          onClick={onCreate}
        >
          Créer mon personnage <ArrowRight size={16} />
        </button>
      </motion.div>
    </div>
  );
}
