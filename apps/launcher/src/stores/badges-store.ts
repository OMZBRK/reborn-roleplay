import { create } from "zustand";
import { fetchBadges, markRead as apiMarkRead, type Badges } from "../lib/content";

// Compteurs non-lus (tickets / patchnotes) + solde de monnaie, partagés
// entre la sidebar (badges numériques), le header (cloche + ZK) et les pages
// qui marquent une section comme lue à l'ouverture. Alimenté par
// /v1/me/badges (poll depuis AuthenticatedLayout). Toute erreur réseau est
// silencieuse — on garde la dernière valeur connue.

type BadgesState = {
  badges: Badges;
  refresh: () => Promise<void>;
  markRead: (scope: "tickets" | "patchnotes") => Promise<void>;
};

const EMPTY: Badges = { unreadTickets: 0, unreadPatchnotes: 0, coins: 0 };

export const useBadgesStore = create<BadgesState>((set) => ({
  badges: EMPTY,
  refresh: async () => {
    try {
      const b = await fetchBadges();
      set({ badges: b });
    } catch {
      // Hors-ligne / non authentifié : on conserve la valeur courante.
    }
  },
  markRead: async (scope) => {
    try {
      const b = await apiMarkRead(scope);
      set({ badges: b });
    } catch {
      // Best-effort : si le marquage échoue, le prochain refresh corrigera.
    }
  },
}));
