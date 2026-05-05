import { useState } from "react";
import { motion } from "framer-motion";
import { CheckSquare, ExternalLink } from "lucide-react";
import { openUrl } from "@tauri-apps/plugin-opener";
import { Checkbox } from "./shared/Checkbox";

type Props = {
  onCancel: () => void;
  onStart: () => void;
};

// L'écran sous-jacent ("page d'accueil candidature en arrière-plan") est
// rendu par WhitelistRoute qui empile la modal par-dessus quand state="modal".
export function PrerequisitesModal({ onCancel, onStart }: Props) {
  const [c1, setC1] = useState(false);
  const [c2, setC2] = useState(false);
  const [c3, setC3] = useState(false);
  const [c4, setC4] = useState(false);
  const allChecked = c1 && c2 && c3 && c4;

  return (
    <motion.div
      className="wl-modal-backdrop"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
    >
      <motion.div
        className="wl-modal"
        initial={{ scale: 0.92, opacity: 0, y: 12 }}
        animate={{ scale: 1, opacity: 1, y: 0 }}
        transition={{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
      >
        <div className="wl-modal-header">
          <div className="wl-modal-icon">
            <CheckSquare size={20} />
          </div>
          <div>
            <h2 className="wl-modal-title">Avant de commencer</h2>
            <p className="wl-modal-subtitle">
              Confirme avoir pris connaissance des éléments suivants avant de
              démarrer ta candidature.
            </p>
          </div>
        </div>

        <div className="wl-modal-checks">
          <div className="wl-check-row" onClick={() => setC1((v) => !v)}>
            <Checkbox checked={c1} onChange={setC1} />
            <div className="wl-check-content">
              J'ai lu le <b>Lore</b> du serveur
            </div>
          </div>
          <div className="wl-check-row" onClick={() => setC2((v) => !v)}>
            <Checkbox checked={c2} onChange={setC2} />
            <div className="wl-check-content">
              J'ai lu le <b>Règlement</b> du serveur
            </div>
          </div>
          <div className="wl-check-row" onClick={() => setC3((v) => !v)}>
            <Checkbox checked={c3} onChange={setC3} />
            <div className="wl-check-content">
              J'ai <b>plus de 16 ans</b>
            </div>
          </div>
          <div
            className="wl-check-row with-action"
            onClick={() => setC4((v) => !v)}
          >
            <Checkbox checked={c4} onChange={setC4} />
            <div className="wl-check-content">
              J'ai consulté le salon <b>Aide-Candidature</b>
              <div className="wl-check-sub">Sur le Discord Reborn</div>
            </div>
            <button
              type="button"
              className="wl-btn-mini-ghost"
              onClick={(e) => {
                e.stopPropagation();
                // TODO: remplacer par l'URL réelle du salon Aide-Candidature
                // une fois le serveur Discord Reborn live.
                void openUrl("https://discord.com");
              }}
            >
              <ExternalLink size={13} />
              <span>Aller voir</span>
            </button>
          </div>
        </div>

        <div className="wl-modal-footer">
          <button type="button" className="wl-btn-ghost" onClick={onCancel}>
            Annuler
          </button>
          <button
            type="button"
            className="wl-btn-primary glow"
            disabled={!allChecked}
            onClick={() => allChecked && onStart()}
          >
            Commencer la candidature
          </button>
        </div>
      </motion.div>
    </motion.div>
  );
}
