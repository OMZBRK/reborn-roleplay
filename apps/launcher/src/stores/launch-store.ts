import { create } from "zustand";

export type LaunchPhase =
  | "idle"
  | "checking"
  | "downloading"
  | "launching"
  | "ready"
  | "running"
  | "blocked";

type LaunchState = {
  phase: LaunchPhase;
  progress: number;
  serverOnline: number;
  /** Compteur incrémenté pour demander un relancement du jeu depuis
   *  l'extérieur de PlayButton (ex. bouton "Relancer" du modal de crash).
   *  PlayButton observe ce nonce et rejoue sa logique de lancement — il
   *  reste l'unique détenteur du flow (update-check + phases), pas de
   *  duplication. */
  relaunchNonce: number;
  /** Dev uniquement (window.__reborn.download.demo) : force l'affichage de
   *  la barre de telechargement avec cette valeur 0..1. null = normal. */
  demoProgress: number | null;
  setDemoProgress: (p: number | null) => void;
  setPhase: (p: LaunchPhase) => void;
  setProgress: (p: number) => void;
  requestRelaunch: () => void;
};

export const useLaunchStore = create<LaunchState>((set) => ({
  phase: "idle",
  progress: 0,
  serverOnline: 0,
  relaunchNonce: 0,
  demoProgress: null,
  setDemoProgress: (demoProgress) => set({ demoProgress }),
  setPhase: (phase) => set({ phase }),
  setProgress: (progress) => set({ progress }),
  requestRelaunch: () => set((s) => ({ relaunchNonce: s.relaunchNonce + 1 })),
}));
