import { motion } from "framer-motion";
import { PlayButton } from "./PlayButton";

export function HeroSection() {
  return (
    <section
      className="reborn-hero-bg reborn-pattern-overlay relative flex shrink-0 flex-col items-center justify-center overflow-hidden px-12"
      style={{ height: 432 }}
    >
      <motion.p
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="mb-5 text-[11px] font-medium uppercase tracking-[0.32em] text-foreground-muted"
      >
        Reborn Roleplay <span className="mx-2 opacity-60">—</span> Naruto Edition
      </motion.p>

      <motion.h1
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.05 }}
        className="mb-6 text-center font-display"
        style={{
          fontSize: 38,
          lineHeight: 1.1,
          letterSpacing: "0.015em",
          maxWidth: 920,
        }}
      >
        <span className="block text-white/95">Dans l'ombre ou la lumière,</span>
        <span className="block">
          <span className="text-white">chaque ninja écrit </span>
          <span style={{ color: "var(--color-accent-hover)" }}>sa propre destinée</span>
        </span>
      </motion.h1>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, delay: 0.15 }}
      >
        <PlayButton />
      </motion.div>
    </section>
  );
}
