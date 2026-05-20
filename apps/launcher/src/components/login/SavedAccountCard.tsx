import { motion } from "framer-motion";
import { X } from "lucide-react";
import { MinecraftHead } from "./MinecraftHead";

type Props = {
  pseudo: string;
  lastSeen: string;
  seed: string;
  onClick: () => void;
  onRemove?: () => void;
};

const REL = new Intl.RelativeTimeFormat("fr", { numeric: "auto" });

function formatLastSeen(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const diffSec = Math.round((then - Date.now()) / 1000);
  const absSec = Math.abs(diffSec);
  if (absSec < 60) return REL.format(diffSec, "second");
  if (absSec < 3600) return REL.format(Math.round(diffSec / 60), "minute");
  if (absSec < 86_400) return REL.format(Math.round(diffSec / 3600), "hour");
  if (absSec < 604_800) return REL.format(Math.round(diffSec / 86_400), "day");
  if (absSec < 2_592_000) return REL.format(Math.round(diffSec / 604_800), "week");
  if (absSec < 31_536_000) return REL.format(Math.round(diffSec / 2_592_000), "month");
  return REL.format(Math.round(diffSec / 31_536_000), "year");
}

export function SavedAccountCard({ pseudo, lastSeen, seed, onClick, onRemove }: Props) {
  return (
    <motion.div
      whileHover={{ y: -3 }}
      whileTap={{ scale: 0.97 }}
      transition={{ type: "spring", stiffness: 320, damping: 24 }}
      className="no-drag group relative w-[148px] flex-shrink-0"
    >
      <button
        type="button"
        onClick={onClick}
        className="flex w-full flex-col items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-3 text-center transition-colors hover:border-[var(--color-accent)] hover:bg-[var(--color-surface-elevated)]"
      >
        <MinecraftHead seed={seed} size={56} />
        <span className="mt-1 max-w-full truncate font-medium text-[var(--color-foreground)]">
          {pseudo}
        </span>
        <span className="text-xs text-[var(--color-foreground-subtle)]">
          {formatLastSeen(lastSeen)}
        </span>
      </button>

      {onRemove && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onRemove();
          }}
          aria-label={`Oublier le compte ${pseudo}`}
          title="Oublier ce compte"
          className="absolute right-1.5 top-1.5 flex h-6 w-6 items-center justify-center rounded-full bg-[var(--color-surface-overlay)] text-[var(--color-foreground-subtle)] opacity-0 transition-opacity hover:bg-[var(--color-danger-soft)] hover:text-[var(--color-danger)] group-hover:opacity-100 focus:opacity-100"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      )}
    </motion.div>
  );
}
