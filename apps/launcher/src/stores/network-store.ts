import { create } from "zustand";

// Connectivite vue depuis le frontend. Pilote l'OfflineBanner. Le check
// reel est fait par lib/network-status.ts qui appelle la commande Tauri
// network_ping_health toutes les 12s.
export type NetworkStatus = "checking" | "online" | "offline";

type NetworkState = {
  status: NetworkStatus;
  lastCheckedAt: number | null;
  /** True quand on vient de basculer offline → online : le banner doit
   *  rester affiche en variante "success" pendant 4s. Reset apres timer. */
  justRecovered: boolean;
  /** Permet a l'utilisateur de masquer le banner offline sans l'aide d'un
   *  reset reseau. Reset si on rebascule offline plus tard. */
  dismissed: boolean;
  setStatus: (status: NetworkStatus, opts?: { justRecovered?: boolean }) => void;
  setDismissed: (v: boolean) => void;
};

export const useNetworkStore = create<NetworkState>((set) => ({
  status: "checking",
  lastCheckedAt: null,
  justRecovered: false,
  dismissed: false,
  setStatus: (status, opts) =>
    set((s) => ({
      status,
      lastCheckedAt: Date.now(),
      justRecovered: opts?.justRecovered ?? false,
      // Quand on retombe offline, on ressuscite la possibilite d'afficher
      // le banner meme si l'utilisateur l'avait dismiss precedemment.
      // Sinon (online ou checking), on preserve la valeur courante.
      dismissed: status === "offline" ? false : s.dismissed,
    })),
  setDismissed: (dismissed) => set({ dismissed }),
}));
