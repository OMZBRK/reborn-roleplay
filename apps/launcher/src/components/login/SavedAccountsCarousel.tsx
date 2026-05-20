import { Plus } from "lucide-react";
import { motion } from "framer-motion";
import type { SavedAccount } from "../../lib/types";
import { SavedAccountCard } from "./SavedAccountCard";

type Props = {
  accounts: SavedAccount[];
  onPick: (a: SavedAccount) => void;
  onAdd: () => void;
  onRemove?: (a: SavedAccount) => void;
};

export function SavedAccountsCarousel({ accounts, onPick, onAdd, onRemove }: Props) {
  if (accounts.length === 0) return null;

  return (
    <div className="no-drag flex items-stretch gap-3 overflow-x-auto overflow-y-hidden py-1">
      {accounts.map((a) => (
        <SavedAccountCard
          key={a.pseudo}
          pseudo={a.pseudo}
          lastSeen={a.lastSeen}
          seed={a.seed}
          onClick={() => onPick(a)}
          onRemove={onRemove ? () => onRemove(a) : undefined}
        />
      ))}
      <motion.button
        type="button"
        onClick={onAdd}
        whileHover={{ y: -3 }}
        whileTap={{ scale: 0.97 }}
        transition={{ type: "spring", stiffness: 320, damping: 24 }}
        className="flex w-[148px] flex-shrink-0 flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-[var(--color-border-strong)] bg-transparent p-3 text-[var(--color-foreground-subtle)] transition-colors hover:border-[var(--color-accent)] hover:text-[var(--color-accent)]"
        aria-label="Ajouter un compte"
      >
        <div className="flex h-14 w-14 items-center justify-center rounded-md border border-[var(--color-border-strong)]">
          <Plus className="h-6 w-6" />
        </div>
        <span className="mt-1 text-sm">Ajouter un compte</span>
      </motion.button>
    </div>
  );
}
