import { motion, AnimatePresence } from "framer-motion";
import { Check } from "lucide-react";

type Props = {
  checked: boolean;
  onChange: (next: boolean) => void;
};

export function Checkbox({ checked, onChange }: Props) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      onClick={(e) => {
        e.stopPropagation();
        onChange(!checked);
      }}
      className={`wl-checkbox${checked ? " checked" : ""}`}
    >
      <AnimatePresence>
        {checked && (
          <motion.span
            key="ck"
            initial={{ scale: 0, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0, opacity: 0 }}
            transition={{ type: "spring", stiffness: 600, damping: 22 }}
            style={{ display: "grid", placeItems: "center" }}
          >
            <Check size={14} strokeWidth={3} />
          </motion.span>
        )}
      </AnimatePresence>
    </button>
  );
}
