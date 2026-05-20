import { create } from "zustand";

// Store partage entre UpdateChecker (qui poll tauri-plugin-updater) et la
// Sidebar (qui rend une pulse sur le logo R quand une update est dispo).
// Le UpdateChecker reste seul responsable du flow technique (check,
// download, install, relaunch) — ce store ne fait que diffuser le flag.
//
// Le flag `ignored` est positionne quand l'utilisateur clique "Plus tard"
// dans la toast actuelle (ou Ignore-cette-version au CHANTIER B). Tant
// qu'ignored est vrai, la pulse reste visible — c'est le rappel doux
// (vs la modale bloquante).
type UpdateState = {
  available: boolean;
  ignored: boolean;
  setAvailable: (v: boolean) => void;
  setIgnored: (v: boolean) => void;
};

export const useUpdateStore = create<UpdateState>((set) => ({
  available: false,
  ignored: false,
  setAvailable: (available) => set({ available }),
  setIgnored: (ignored) => set({ ignored }),
}));
