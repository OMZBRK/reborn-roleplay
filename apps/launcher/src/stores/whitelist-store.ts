import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";

// ──────────────────────────────────────────────────────────────
// Types
// ──────────────────────────────────────────────────────────────

// Statut côté client. "draft" = candidature pas encore soumise (form en cours).
// Les 3 autres viennent du serveur (mappés depuis WhitelistStatus côté API).
// "needs_revision" est volontairement absent : côté UI design v2, on retombe
// sur "rejected" (l'utilisateur peut refaire une candidature).
export type WhitelistStatus = "draft" | "pending" | "accepted" | "rejected";

// Forme du brouillon stocké localement entre les 3 étapes du wizard.
// Aligné sur MOCK_DATA / EMPTY_DATA dans whitelist-app.jsx (artefact design).
export type WhitelistDraft = {
  // Étape 1 — HRP
  dob: string;            // ISO YYYY-MM-DD
  motivation: string;
  experience: string;
  availability: string;
  // Étape 2 — RP
  firstName: string;
  lastName: string;
  village: string;
  support: string;        // optionnel
  history: string;
  appearance: string;
  objectives: string;
};

export const EMPTY_DRAFT: WhitelistDraft = {
  dob: "",
  motivation: "",
  experience: "",
  availability: "",
  firstName: "",
  lastName: "",
  village: "",
  support: "",
  history: "",
  appearance: "",
  objectives: "",
};

// Mock data Reborn (Hikami Nara, Konoha) — équivalent du MOCK_DATA de l'artefact.
// Utilisé par le menu dev (Ctrl+Shift+W) pour pré-remplir le formulaire.
export const MOCK_DRAFT: WhitelistDraft = {
  dob: "2004-01-10",
  motivation:
    "Reborn m'a été recommandé par un ami croisé sur un autre serveur, et j'avoue que l'univers travaillé autour du Pays du Feu m'a immédiatement parlé. Je cherche depuis plusieurs mois un RP exigeant où l'écriture compte vraiment, pas juste un terrain de jeu PvP — et la lecture du lore m'a confirmé que c'était le cas ici. J'ai envie de m'investir sur du long terme, de tisser des relations avec d'autres joueurs et de voir mon personnage évoluer au fil des intrigues.",
  experience:
    "Je fais du RP textuel depuis environ six ans. J'ai principalement joué sur des serveurs MC (deux campagnes longues sur des univers médiévaux dérivés), un GN annuel, et plus récemment un FoundryVTT entre potes. J'ai déjà tenu un personnage sur 14 mois IRL, ce qui m'a beaucoup appris sur la patience narrative et l'arc évolutif. J'aime particulièrement les scènes politiques et les huis-clos de tension, beaucoup moins les combats à rallonge.",
  availability:
    "Soirs en semaine (19h-23h), week-ends plus larges. Disponible sur Discord la journée pour les briefings.",
  firstName: "Hikami",
  lastName: "Nara",
  village: "Konohagakure",
  support: "",
  history:
    "Hikami est né dans une branche cadette du clan Nara, à la lisière du Pays du Feu. Sa mère, archiviste au sanctuaire de Kuroyama, lui a transmis très tôt le goût des textes anciens et la prudence des stratèges. À douze ans, lors d'une expédition vers les frontières d'Ame, il a perdu son frère aîné dans une embuscade non revendiquée — un événement dont il ne parle jamais ouvertement, mais qui structure tout son rapport au combat et à la confiance.",
  appearance:
    "Silhouette mince, presque longiligne. Cheveux noirs attachés bas, mèches courtes encadrant un visage anguleux. Il porte presque toujours une veste sombre brodée du blason des Nara à l'épaule. Réservé, observateur, parle peu mais écoute beaucoup — au point que certains le trouvent froid. Sous cette retenue se cache un humour sec et une curiosité difficile à rassasier, surtout pour tout ce qui touche aux écritures perdues.",
  objectives:
    "À court terme, Hikami veut être assigné à des missions de reconnaissance pour cartographier les zones grises entre Konoha et les pays voisins. Il a besoin de comprendre comment les frontières se sont déplacées depuis la dernière guerre. À moyen terme, il cherche à reprendre l'enquête laissée inachevée par son frère sur l'embuscade — sans ressentiment, mais avec méthode. À long terme, il aimerait s'approcher des cercles d'archives ANBU non par ambition militaire mais pour accéder aux dossiers verrouillés. Il rêve aussi, plus secrètement, de voir si les théories de sa mère sur la convergence des techniques de clan tiennent debout sur le terrain. Il sait que ces objectifs peuvent le mener loin de Konoha — il l'accepte.",
};

// ──────────────────────────────────────────────────────────────
// Store
// ──────────────────────────────────────────────────────────────

type WhitelistState = {
  draft: WhitelistDraft;
  status: WhitelistStatus;
  // ID serveur de la candidature courante quand status !== "draft" (sert
  // notamment à savoir si on doit POST /whitelist ou PATCH /whitelist/me).
  applicationId: string | null;
  // Note du staff renvoyée par le serveur lors d'un rejet/acceptation.
  // Affichée par RejectedPage / AcceptedPage quand non-null.
  reviewNotes: string | null;
  // Cf C5 — assignation staff. Affiche dans StatusChatPage "Pris par
  // @X depuis Yh" et debloque le bouton "Demander une reprise" apres 4h.
  assigneeName: string | null;
  assignedAt: string | null;

  // Actions
  updateField: <K extends keyof WhitelistDraft>(key: K, value: WhitelistDraft[K]) => void;
  setDraft: (draft: WhitelistDraft) => void;
  applyMockData: () => void;
  setStatus: (status: WhitelistStatus) => void;
  // Hydrate intégralement le store depuis la réponse de fetchWhitelistMe.
  // Passe null pour signifier "aucune candidature côté serveur" → status="draft".
  hydrateFromServer: (data: HydrateInput | null) => void;
  reset: () => void;
};

export type HydrateInput = {
  applicationId: string;
  status: WhitelistStatus;
  reviewNotes: string | null;
  draft: WhitelistDraft;
  assigneeName: string | null;
  assignedAt: string | null;
};

// On utilise localStorage (via zustand/middleware) en attendant tauri-plugin-store —
// suffisant pour la phase actuelle, le draft survit aux reload mais pas aux migrations
// de machine. Quand le plugin Tauri sera setup, swap le storage adapter ici.
export const useWhitelistStore = create<WhitelistState>()(
  persist(
    (set) => ({
      draft: EMPTY_DRAFT,
      status: "draft",
      applicationId: null,
      reviewNotes: null,
      assigneeName: null,
      assignedAt: null,
      updateField: (key, value) =>
        set((s) => ({ draft: { ...s.draft, [key]: value } })),
      setDraft: (draft) => set({ draft }),
      applyMockData: () => set({ draft: MOCK_DRAFT }),
      setStatus: (status) => set({ status }),
      hydrateFromServer: (data) => {
        if (!data) {
          set({
            status: "draft",
            applicationId: null,
            reviewNotes: null,
            assigneeName: null,
            assignedAt: null,
          });
          return;
        }
        set({
          applicationId: data.applicationId,
          status: data.status,
          reviewNotes: data.reviewNotes,
          draft: data.draft,
          assigneeName: data.assigneeName,
          assignedAt: data.assignedAt,
        });
      },
      reset: () =>
        set({
          draft: EMPTY_DRAFT,
          status: "draft",
          applicationId: null,
          reviewNotes: null,
          assigneeName: null,
          assignedAt: null,
        }),
    }),
    {
      name: "reborn:whitelist",
      storage: createJSONStorage(() => localStorage),
      partialize: (s) => ({
        draft: s.draft,
        status: s.status,
        applicationId: s.applicationId,
        reviewNotes: s.reviewNotes,
        assigneeName: s.assigneeName,
        assignedAt: s.assignedAt,
      }),
    },
  ),
);
