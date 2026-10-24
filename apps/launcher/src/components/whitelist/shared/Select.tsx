import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Check, ChevronDown } from "lucide-react";

type Props = {
  value: string;
  options: readonly string[];
  placeholder: string;
  onChange: (v: string) => void;
};

export function Select({ value, options, placeholder, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);

  function choose(opt: string) {
    onChange(opt);
    setOpen(false);
  }

  return (
    <div
      ref={ref}
      style={{ position: "relative" }}
      onKeyDown={(e) => {
        // Échap referme la liste sans rien choisir.
        if (e.key === "Escape" && open) {
          e.stopPropagation();
          setOpen(false);
        }
      }}
    >
      <button
        type="button"
        className={`wl-select${open ? " open" : ""}${value ? " has-value" : ""}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <span>{value || placeholder}</span>
        <ChevronDown size={16} />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            className="wl-select-menu"
            role="listbox"
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.16 }}
          >
            {options.map((opt) => (
              <div
                key={opt}
                role="option"
                aria-selected={value === opt}
                tabIndex={0}
                className={`wl-select-option${value === opt ? " selected" : ""} focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-focus-ring`}
                onClick={() => choose(opt)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    choose(opt);
                  }
                }}
              >
                <span>{opt}</span>
                {value === opt && <Check size={14} strokeWidth={3} />}
              </div>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
