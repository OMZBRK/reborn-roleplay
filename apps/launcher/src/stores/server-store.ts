import { create } from "zustand";
import { listServers, type ServerInfo } from "../lib/launcher";

/**
 * Serveur sélectionné (multi-version). Le serveur choisi détermine la version
 * Minecraft, le modpack (manifest) et le dossier d'instance côté backend —
 * `PlayButton` passe `selectedId` à checkUpdate / applyUpdate / launchGame.
 *
 * Défaut = "rp" (serveur RP historique, MC 26.2). Le serveur "build" (MC 26.3)
 * n'apparaît que s'il est configuré ET pour le staff (`staffOnly`).
 */
type ServerStore = {
  servers: ServerInfo[];
  selectedId: string;
  loaded: boolean;
  load: () => Promise<void>;
  select: (id: string) => void;
};

export const useServerStore = create<ServerStore>((set, get) => ({
  servers: [],
  selectedId: "rp",
  loaded: false,
  load: async () => {
    try {
      const servers = await listServers();
      // Si le serveur sélectionné a disparu (config changée), retombe sur RP.
      const selectedId = servers.some((s) => s.id === get().selectedId)
        ? get().selectedId
        : "rp";
      set({ servers, selectedId, loaded: true });
    } catch {
      set({ loaded: true });
    }
  },
  select: (id) => set({ selectedId: id }),
}));
