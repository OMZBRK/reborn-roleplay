import { motion } from "framer-motion";
import type { ReactNode } from "react";

type Props = {
  index: number;
  num: number;
  name: string;
  color: string;
  glow: string;
  silhouette: ReactNode;
  onClick: () => void;
};

export function CategoryCard({ index, num, name, color, glow, silhouette, onClick }: Props) {
  return (
    <motion.button
      type="button"
      onClick={onClick}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: index * 0.07, ease: [0.16, 1, 0.3, 1] }}
      whileHover={{ scale: 1.01 }}
      whileTap={{ scale: 0.99 }}
      className="reborn-cat-card"
      style={{
        ["--card-color" as string]: color,
        ["--card-glow" as string]: glow,
      }}
    >
      <span className="reborn-cat-badge">{num}</span>
      <span className="reborn-cat-silhouette" aria-hidden>
        <svg viewBox="0 0 100 100" fill="currentColor">
          {silhouette}
        </svg>
      </span>
      <span className="relative z-[2] flex flex-col text-left">
        <span className="font-display text-[30px] leading-none tracking-[0.03em] text-foreground">
          {name}
        </span>
        <span className="mt-[6px] font-mono text-[10px] uppercase tracking-[0.1em] text-foreground-muted">
          Section {String(num).padStart(2, "0")}
        </span>
      </span>
    </motion.button>
  );
}
