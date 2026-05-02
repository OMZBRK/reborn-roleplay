import { motion } from "framer-motion";
import { Lock, Play } from "lucide-react";
import { useLaunchStore } from "../stores/launch-store";
import { cn } from "../lib/cn";

export function PlayButton() {
  const phase = useLaunchStore((s) => s.phase);
  const progress = useLaunchStore((s) => s.progress);
  const setPhase = useLaunchStore((s) => s.setPhase);

  const isBlocked = phase === "blocked";
  const isDownloading = phase === "downloading" || phase === "checking";
  const label = isBlocked
    ? "Inaccessible"
    : isDownloading
      ? `Telechargement ${progress}%`
      : phase === "running"
        ? "En jeu..."
        : "Jouer";

  return (
    <motion.button
      type="button"
      whileHover={isBlocked ? undefined : { scale: 1.02 }}
      whileTap={isBlocked ? undefined : { scale: 0.98 }}
      onClick={() => {
        if (isBlocked) return;
        // Maquette : on simulera le flow §8 du plan en Semaines 3-4.
        setPhase("ready");
      }}
      disabled={isBlocked || phase === "running"}
      className={cn(
        "relative flex h-16 w-72 items-center justify-center gap-3 overflow-hidden rounded-xl text-base font-semibold uppercase tracking-wider transition",
        isBlocked
          ? "cursor-not-allowed bg-surface-elevated text-foreground-subtle"
          : "bg-accent text-white shadow-[0_8px_30px_-10px_rgba(59,91,219,0.6)] hover:bg-accent-hover",
      )}
    >
      {isDownloading && (
        <motion.div
          initial={{ x: "-100%" }}
          animate={{ x: `${progress - 100}%` }}
          transition={{ duration: 0.3 }}
          className="absolute inset-0 bg-accent-pressed"
        />
      )}
      <span className="relative flex items-center gap-3">
        {isBlocked ? <Lock className="h-5 w-5" /> : <Play className="h-5 w-5 fill-current" />}
        {label}
      </span>
    </motion.button>
  );
}
