import { create } from "zustand";
import type { DownloadProgress, LaunchProgress } from "../lib/launcher";

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
  /** Dernier `launch:progress` (preparation du lancement) et dernier
   *  `manifest:progress` (telechargement « Mettre a jour »). Tenus ici, et
   *  remplis par CrashStub (monte en permanence), pas dans l'etat local de
   *  PlayButton : revenir sur l'accueil pendant une preparation remettait
   *  sinon la barre a 0 % jusqu'au paquet suivant. Remis a null quand la
   *  phase revient a ready/idle, et au debut et a la fin d'une partie. */
  launchProgress: LaunchProgress | null;
  downloadProgress: DownloadProgress | null;
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
  setLaunchProgress: (p: LaunchProgress | null) => void;
  setDownloadProgress: (p: DownloadProgress | null) => void;
  requestRelaunch: () => void;
};

export const useLaunchStore = create<LaunchState>((set) => ({
  phase: "idle",
  progress: 0,
  serverOnline: 0,
  launchProgress: null,
  downloadProgress: null,
  relaunchNonce: 0,
  demoProgress: null,
  setDemoProgress: (demoProgress) => set({ demoProgress }),
  setPhase: (phase) =>
    set(
      phase === "ready" || phase === "idle"
        ? { phase, launchProgress: null, downloadProgress: null }
        : { phase },
    ),
  setProgress: (progress) => set({ progress }),
  setLaunchProgress: (launchProgress) => set({ launchProgress }),
  setDownloadProgress: (downloadProgress) => set({ downloadProgress }),
  requestRelaunch: () => set((s) => ({ relaunchNonce: s.relaunchNonce + 1 })),
}));
