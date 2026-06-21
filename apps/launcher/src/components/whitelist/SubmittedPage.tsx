import { motion } from "framer-motion";

function SubmittedGlyph() {
  // Cristal stylisé 4-pétales (pas d'imagerie copyrightée).
  return (
    <svg width="96" height="96" viewBox="0 0 96 96" fill="none">
      <defs>
        <linearGradient id="wl-submitted-grad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#6b8cff" stopOpacity="0.95" />
          <stop offset="100%" stopColor="#a0182b" stopOpacity="0.7" />
        </linearGradient>
      </defs>
      <g stroke="#6b8cff" strokeWidth="1.5">
        <path
          d="M48 12 L60 36 L48 48 L36 36 Z"
          fill="url(#wl-submitted-grad)"
          fillOpacity="0.5"
        />
        <path
          d="M48 84 L60 60 L48 48 L36 60 Z"
          fill="url(#wl-submitted-grad)"
          fillOpacity="0.5"
        />
        <path
          d="M12 48 L36 36 L48 48 L36 60 Z"
          fill="url(#wl-submitted-grad)"
          fillOpacity="0.5"
        />
        <path
          d="M84 48 L60 36 L48 48 L60 60 Z"
          fill="url(#wl-submitted-grad)"
          fillOpacity="0.5"
        />
        <circle cx="48" cy="48" r="5" fill="#6b8cff" fillOpacity="0.9" />
      </g>
    </svg>
  );
}

export function SubmittedPage() {
  return (
    <div className="wl-submitted reborn-radial-bg-strong reborn-pattern-overlay">
      <motion.div
        className="wl-submitted-icon"
        initial={{ scale: 0, rotate: -180, opacity: 0 }}
        animate={{ scale: 1, rotate: 0, opacity: 1 }}
        transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
      >
        <SubmittedGlyph />
      </motion.div>
      <motion.h1
        className="wl-submitted-title"
        initial={{ opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, delay: 0.2 }}
      >
        Candidature Envoyée !
      </motion.h1>
      <motion.p
        className="wl-submitted-text"
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, delay: 0.4 }}
      >
        Votre candidature sera <b>traitée sous 24 à 72 heures</b>. Vous pourrez{" "}
        <b>suivre</b> son avancement et <b>échanger</b> avec le{" "}
        <b>staff via le chat</b>.
      </motion.p>
    </div>
  );
}
