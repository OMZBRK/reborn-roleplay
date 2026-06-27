import { create } from "zustand";

// Crash report frontend. Alimente par l'event Tauri `game:crashed` emis par
// le backend (apps/launcher/src-tauri/src/launcher/game.rs::await_game_end)
// quand la JVM se termine avec un status non-zero non sollicite. Le mapping
// event → report se fait dans CrashStub.tsx. En dev, window.__reborn.crash
// (DevHelpers.tsx) permet aussi de declencher le modal a la main.
export type GameCrashReport = {
  /** Code de sortie de la JVM (par ex. 255 = crash fatal). null = inconnu
   *  (process tue par signal, ou status illisible). */
  exitCode: number | null;
  /** Message court rendu en sous-titre du modal. */
  summary: string;
  /** Mod incrimine par le LogAnalyzer (filename + version), ou null. */
  suspectedMod: { filename: string; reason: string } | null;
  /** Chemin du fichier log complet (last-stderr.txt) — pour Copier / Voir. */
  logPath: string | null;
  /** Dernieres ~100 lignes de stderr, affichees immediatement dans le modal
   *  sans avoir a relire le fichier. null si indisponible. */
  stderrTail: string | null;
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
