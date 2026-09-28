import type { ReactNode } from "react";
import { createPortal } from "react-dom";

// Rend les overlays plein-ecran (lightbox, modales) directement sous <body>.
// Les pages posent `isolation: isolate` (.reborn-pattern-overlay) : un
// `position: fixed` rendu dans la page reste piege dans ce stacking context
// et passe SOUS le rail (z-index 2), quel que soit son propre z-index.
export function Portal({ children }: { children: ReactNode }) {
  return createPortal(children, document.body);
}
