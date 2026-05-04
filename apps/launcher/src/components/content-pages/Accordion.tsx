import { useState, type ReactNode } from "react";
import { motion } from "framer-motion";
import { ChevronDown } from "lucide-react";

type Props = {
  index: number;
  num: number;
  title: string;
  defaultOpen?: boolean;
  children: ReactNode;
};

export function Accordion({ index, num, title, defaultOpen = false, children }: Props) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: index * 0.04 }}
      className={`reborn-accordion${open ? " is-open" : ""}`}
    >
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex w-full cursor-pointer select-none items-center justify-between px-5 py-4 text-left"
      >
        <div className="flex items-center gap-[14px] text-[15px] font-semibold text-foreground">
          <span className="reborn-accordion-num min-w-[28px] font-mono text-[11px] tracking-[0.1em]">
            {String(num).padStart(2, "0")}
          </span>
          <span>{title}</span>
        </div>
        <ChevronDown className="reborn-accordion-chevron h-5 w-5 transition-transform duration-[250ms]" />
      </button>
      <div className="reborn-accordion-body grid">
        <div className="overflow-hidden">
          <div className="reborn-accordion-content border-t border-border px-5 pb-5 text-[14px] leading-[1.7] text-foreground-subtle">
            {children}
          </div>
        </div>
      </div>
    </motion.div>
  );
}
