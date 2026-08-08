import { useEffect, type ReactNode } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { X } from "lucide-react";

export type ModalVariant = "info" | "warning" | "danger";
export type ModalSize = "sm" | "md";

type Props = {
  open: boolean;
  /**
   * Si fourni : modale dismissable (croix top-right + ESC + click-outside).
   * Sinon : bloquante (l'utilisateur doit interagir avec le contenu pour
   * fermer — utile pour downloading/installing d'une update ou un crash
   * critique qui doit etre acquitte explicitement).
   */
  onClose?: () => void;
  variant?: ModalVariant;
  /** "sm" = 340px (UpdateModal style Reborn), "md" = 420px (default). */
  size?: ModalSize;
  children: ReactNode;
};

// Wrapper modal generique pour les ecrans systeme (UpdateModal,
// GameCrashModal). Backdrop fade + scale-in centre, bouton X conditionnel
// au passage de onClose. Variant determine la couleur du halo au-dessus
// du contenu (info: accent bleu, warning: orange, danger: rouge).
//
// Utilisation typique :
//   <Modal open={state.kind !== "idle"} onClose={() => setIdle()} variant="info">
//     <h2>Titre</h2>
//     <p>Body</p>
//     <button>Action</button>
//   </Modal>
export function Modal({ open, onClose, variant = "info", size = "md", children }: Props) {
  // ESC ferme uniquement si onClose est fourni (modale dismissable).
  useEffect(() => {
    if (!open || !onClose) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  const ringColor = ringForVariant(variant);

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          key="modal-backdrop"
          className="fixed inset-0 z-[80] flex items-center justify-center bg-black/55 backdrop-blur-sm"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.18 }}
          onClick={(e) => {
            // Click sur le backdrop ferme uniquement si dismissable et si le
            // click ne vient pas du contenu (target === currentTarget).
            if (onClose && e.target === e.currentTarget) onClose();
          }}
        >
          <motion.div
            key="modal-card"
            initial={{ scale: 0.94, opacity: 0, y: 8 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            exit={{ scale: 0.96, opacity: 0 }}
            transition={{ type: "spring", stiffness: 320, damping: 26 }}
            className={[
              "relative max-w-[90vw] rounded-[14px] border bg-[var(--color-surface-elevated)] shadow-2xl",
              size === "sm" ? "w-[340px] p-5" : "w-[420px] p-6",
            ].join(" ")}
            style={{
              borderColor: "var(--color-border-strong)",
              boxShadow: `0 12px 32px -4px rgba(0,0,0,.6), 0 0 0 1px ${ringColor}`,
            }}
          >
            {onClose && (
              <button
                type="button"
                onClick={onClose}
                aria-label="Fermer"
                className="absolute right-3 top-3 flex h-7 w-7 items-center justify-center rounded-full text-[var(--color-foreground-subtle)] transition-colors hover:bg-[var(--color-surface-overlay)] hover:text-[var(--color-foreground)]"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
            {children}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function ringForVariant(variant: ModalVariant): string {
  switch (variant) {
    case "info":
      return "var(--color-accent-soft)";
    case "warning":
      return "var(--color-warning-soft)";
    case "danger":
      return "var(--color-danger-soft)";
  }
}
