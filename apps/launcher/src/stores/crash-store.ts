import { create } from "zustand";

// Crash report frontend. Pour cette PR le declenchement est manuel (stub
// via window.__triggerCrash en dev). Le vrai branchement sur les events
// Tauri du game launcher (game:crashed via tracing diagnostics) arrive
// dans une PR ulterieure — cf TODO dans GameCrashController.tsx.
export type GameCrashReport = {
  /** Code de sortie de la JVM (par ex. 255 = crash fatal, -1 = inconnu). */
  exitCode: number;
  /** Message court rendu en sous-titre du modal. */
  summary: string;
  /** Mod incrimine par le LogAnalyzer (filename + version), ou null. */
  suspectedMod: { filename: string; reason: string } | null;
  /** Chemin du fichier log complet — pour les boutons Copy / Open. */
  logPath: string | null;
};

type CrashState = {
  report: GameCrashReport | null;
  open: (report: GameCrashReport) => void;
  close: () => void;
};

export const useCrashStore = create<CrashState>((set) => ({
  report: null,
  open: (report) => set({ report }),
  close: () => set({ report: null }),
}));
