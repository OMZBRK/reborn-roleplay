import { motion } from "framer-motion";
import { Loader2 } from "lucide-react";

type Props = {
  state: "idle" | "loading" | "error";
  onClick: () => void;
};

// Glyph minimaliste — 4 carres monochromes (couleur du texte). Volontairement
// different du logo officiel Microsoft pour des raisons de licence : on
// n'embarque PAS le vrai logo.
function MicrosoftGlyph() {
  return (
    <svg viewBox="0 0 14 14" className="h-4 w-4" aria-hidden="true" fill="currentColor">
      <rect x="0" y="0" width="6" height="6" />
      <rect x="8" y="0" width="6" height="6" />
      <rect x="0" y="8" width="6" height="6" />
      <rect x="8" y="8" width="6" height="6" />
    </svg>
  );
}

const SHAKE_X = [-6, 6, -4, 4, -2, 2, 0];

export function MicrosoftLoginButton({ state, onClick }: Props) {
  const isLoading = state === "loading";
  const isError = state === "error";

  return (
    <motion.button
      type="button"
      onClick={onClick}
      disabled={isLoading}
      animate={isError ? { x: SHAKE_X } : { x: 0 }}
      transition={isError ? { duration: 0.5, ease: "easeInOut" } : { duration: 0.15 }}
      // Bouton principal de la carte d'accueil (styles .rb-welcome-cta,
      // globals.css). Le contour danger signale l'echec en plus du shake.
      className={["no-drag rb-welcome-cta", isError && "is-error"].filter(Boolean).join(" ")}
    >
      {isLoading ? (
        <Loader2 className="h-4 w-4 animate-spin" />
      ) : (
        <MicrosoftGlyph />
      )}
      <span>{isLoading ? "Connexion…" : "Se connecter avec Microsoft"}</span>
    </motion.button>
  );
}
