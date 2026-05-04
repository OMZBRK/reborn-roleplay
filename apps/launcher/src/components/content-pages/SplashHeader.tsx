import { motion } from "framer-motion";
import { ChevronDown } from "lucide-react";
import type { ReactNode } from "react";

export type SplashVariant = "rules" | "lore";

type Props = {
  variant: SplashVariant;
  title: string;
  subtitle: ReactNode;
  badgeLine1: string;
  badgeLine2: string;
  onScroll: () => void;
};

export function SplashHeader({ variant, title, subtitle, badgeLine1, badgeLine2, onScroll }: Props) {
  const isLore = variant === "lore";
  const accent = isLore ? "var(--color-lore)" : "var(--color-rules)";
  const titleGlow = isLore ? "rgba(139,92,246,0.45)" : "rgba(74,222,128,0.4)";

  return (
    <div
      className={`reborn-splash ${isLore ? "is-lore" : "is-rules"}`}
      style={{
        // CSS vars consommées par les classes splash-* dans globals.css
        ["--accent-color" as string]: accent,
        ["--title-glow" as string]: titleGlow,
      }}
    >
      <motion.div
        initial={{ opacity: 0, y: -10, x: 10 }}
        animate={{ opacity: 1, y: 0, x: 0 }}
        transition={{ duration: 0.6, delay: 0.4, ease: [0.16, 1, 0.3, 1] }}
        className={`reborn-splash-badge${isLore ? " is-lore" : ""}`}
      >
        <span className="text-[13px] text-white/70">{badgeLine1}</span>
        <span className="text-[18px] text-foreground">{badgeLine2}</span>
      </motion.div>

      <div className="reborn-splash-content">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
          className="reborn-splash-title-wrap"
        >
          <motion.h1
            initial={{ letterSpacing: "0.18em", opacity: 0 }}
            animate={{ letterSpacing: "0.02em", opacity: 1 }}
            transition={{ duration: 1.1, ease: [0.16, 1, 0.3, 1] }}
            className="reborn-splash-title"
          >
            {title}
          </motion.h1>

          <motion.div
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.55 }}
            className="reborn-splash-subtitle-card"
          >
            {subtitle}
          </motion.div>
        </motion.div>
      </div>

      <motion.div
        initial={{ opacity: 0, x: 30 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.8, delay: 0.5 }}
        className="reborn-splash-mascot"
      >
        <div className="reborn-mascot-circle">
          <span className="font-mono text-[10px] tracking-[0.05em] text-foreground-muted">
            320 × 380
          </span>
        </div>
        <div className="reborn-mascot-label font-mono text-[11px] uppercase tracking-[0.08em] text-foreground-muted">
          Mascotte RP
          <br />
          placeholder
        </div>
      </motion.div>

      <motion.button
        type="button"
        onClick={onScroll}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 1.0, duration: 0.5 }}
        aria-label="Voir les catégories"
        className="reborn-splash-chevron"
      >
        <ChevronDown className="h-5 w-5" />
      </motion.button>
    </div>
  );
}
