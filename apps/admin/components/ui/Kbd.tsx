import type { ReactNode } from 'react';

/** Une touche du clavier (« Ctrl K », « Entrée »), comme au Poste de garde. */
export function Kbd({ children }: { children: ReactNode }) {
  return <kbd className="kbd">{children}</kbd>;
}
