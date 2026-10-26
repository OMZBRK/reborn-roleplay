import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import type { DonneesBrouillon, PhaseCandidature } from "../lib/candidature";

// État de la candidature côté launcher (candidature v2,
// docs/gameplay/CANDIDATURE_ET_DOUANE.md §1.2 et §2.5) :
//   - `phase` : dernière phase lue dans GET /whitelist/me (l'onglet Whitelist
//     la relit à chaque ouverture) ;
//   - `status` : la même chose en quatre valeurs, pour la pastille du rail
//     (Sidebar, WhitelistBadge) et la relecture du rôle après l'acceptation
//     (AuthenticatedLayout) ;
//   - `copieLocale` : le brouillon gardé sur CE PC, en plus du brouillon
//     serveur. Il sert hors ligne et quand le serveur n'a pas pu enregistrer ;
//     à l'ouverture, la version la plus récente des deux gagne (§2.5). Il est
//     lié au compte : un autre compte sur le même PC ne le voit pas.
//
// localStorage (zustand/middleware) : ni secret ni donnée de santé, seulement
// les réponses du formulaire, effacées à l'envoi et au retrait.

// ──────────────────────────────────────────────────────────────
// Types
// ──────────────────────────────────────────────────────────────

/** Statut pour la pastille du rail. « draft » : rien d'envoyé, ou une
 *  correction à faire (à revoir) ; « pending » : en attente du staff ou de la
 *  douane ; « accepted » : whitelisté ; « rejected » : refusé. */
export type WhitelistStatus = "draft" | "pending" | "accepted" | "rejected";

/** Brouillon gardé sur ce PC (formes du brouillon serveur). */
export type CopieLocale = {
  /** Identifiant du compte Reborn propriétaire. */
  compte: string;
  data: DonneesBrouillon;
  /** Dernière modification sur ce PC (ISO). */
  majLe: string;
};

/** Phase → statut du rail. */
export function statutDePhase(phase: PhaseCandidature | null): WhitelistStatus {
  switch (phase) {
    case "ACCEPTE":
      return "accepted";
    case "REFUSE":
      return "rejected";
    case "ECRIT_EN_ATTENTE":
    case "DOUANE_A_RESERVER":
    case "DOUANE_RESERVEE":
    case "DOUANE_A_REVOIR":
      return "pending";
    default:
      return "draft";
  }
}

// ──────────────────────────────────────────────────────────────
// Store
// ──────────────────────────────────────────────────────────────

type WhitelistState = {
  status: WhitelistStatus;
  phase: PhaseCandidature | null;
  copieLocale: CopieLocale | null;

  /** Phase lue dans GET /whitelist/me (met aussi à jour `status`). */
  setPhase: (phase: PhaseCandidature | null) => void;
  /** Garde le formulaire sur ce PC (à chaque modification). */
  garderLocalement: (compte: string, data: DonneesBrouillon) => void;
  /** Efface la copie de ce PC (envoi réussi, retrait, autre version choisie). */
  oublierCopieLocale: () => void;
  reset: () => void;
};

export const useWhitelistStore = create<WhitelistState>()(
  persist(
    (set) => ({
      status: "draft",
      phase: null,
      copieLocale: null,
      setPhase: (phase) => set({ phase, status: statutDePhase(phase) }),
      garderLocalement: (compte, data) =>
        set({ copieLocale: { compte, data, majLe: new Date().toISOString() } }),
      oublierCopieLocale: () => set({ copieLocale: null }),
      reset: () => set({ status: "draft", phase: null, copieLocale: null }),
    }),
    {
      name: "reborn:whitelist",
      storage: createJSONStorage(() => localStorage),
      // v2 : candidature v2. L'ancien brouillon (date de naissance,
      // motivation…) ne se reprend pas : ses champs n'existent plus.
      version: 2,
      migrate: (persisted, version) => {
        const ancien = (persisted ?? {}) as Partial<WhitelistState>;
        if (version < 2) {
          return { status: ancien.status ?? "draft", phase: null, copieLocale: null };
        }
        return ancien as WhitelistState;
      },
      partialize: (s) => ({ status: s.status, phase: s.phase, copieLocale: s.copieLocale }),
    },
  ),
);
