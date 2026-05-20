import { motion } from "framer-motion";
import { Loader2 } from "lucide-react";

type Props = {
  state: "idle" | "loading" | "error";
  onClick: () => void;
};

// Glyph minimaliste — 4 carres bleu accent. Volontairement different du
// logo officiel Microsoft (rouge/vert/bleu/jaune) pour des raisons de
// licence : on n'embarque PAS le vrai logo.
function MicrosoftGlyph() {
  return (
    <svg viewBox="0 0 14 14" className="h-5 w-5" aria-hidden="true">
      <rect x="0" y="0" width="6" height="6" fill="var(--color-accent)" />
      <rect x="8" y="0" width="6" height="6" fill="var(--color-accent)" />
      <rect x="0" y="8" width="6" height="6" fill="var(--color-accent)" />
      <rect x="8" y="8" width="6" height="6" fill="var(--color-accent)" />
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
      className={[
        "no-drag flex h-12 w-full items-center justify-center gap-3 rounded-lg px-4 font-medium transition-colors disabled:cursor-wait",
        "border bg-[var(--color-surface)] text-[var(--color-foreground)]",
        isError
          ? "border-[var(--color-danger)]"
          : "border-[var(--color-border-strong)] hover:border-[var(--color-accent)] hover:bg-[var(--color-surface-elevated)]",
      ].join(" ")}
    >
      {isLoading ? (
        <Loader2 className="h-5 w-5 animate-spin text-[var(--color-accent)]" />
      ) : (
        <MicrosoftGlyph />
      )}
      <span>{isLoading ? "Connexion…" : "Se connecter avec Microsoft"}</span>
    </motion.button>
  );
}
