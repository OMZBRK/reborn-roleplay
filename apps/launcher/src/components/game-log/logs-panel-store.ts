import { create } from "zustand";

// Ouverture du panneau « Journal du jeu » (LogsPanel). Un seul panneau pour
// tout le launcher, monte une fois dans le layout authentifie ; n'importe
// quel composant l'ouvre avec openLogsPanel() (toast de diagnostic, ecran
// de crash, accueil, console DevTools).

type LogsPanelState = {
  open: boolean;
  /** Incremente a chaque ouverture : le panneau se recale (fin du journal,
   *  relecture du tampon Rust) meme s'il etait deja ouvert. */
  openNonce: number;
};

export const useLogsPanelStore = create<LogsPanelState>(() => ({
  open: false,
  openNonce: 0,
}));

export function openLogsPanel(): void {
  useLogsPanelStore.setState((s) => ({ open: true, openNonce: s.openNonce + 1 }));
}

export function closeLogsPanel(): void {
  useLogsPanelStore.setState({ open: false });
}
