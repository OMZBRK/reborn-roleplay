import { create } from "zustand";
import type { GameDiagnostic } from "../lib/launcher";

// Garde en mémoire le DERNIER diagnostic lié aux mods émis pendant le run
// courant (game:diagnostic du LogAnalyzer). Sert à corréler une cause
// probable dans le rapport de crash (GameCrashModal.suspectedMod) : un
// MOD_MC_VERSION_MISMATCH juste avant un crash est très probablement le
// coupable.
//
// Alimenté par CrashStub (listener game:diagnostic) et remis à zéro par
// PlayButton au début de chaque lancement, pour qu'un diagnostic d'un run
// précédent ne fuite pas dans le rapport du run suivant.

const MOD_DIAGNOSTIC_CODES = new Set([
  "MOD_MC_VERSION_MISMATCH",
  "FABRIC_MOD_RESOLUTION_FAILED",
  "MODS_PURGED",
]);

/** True si le code de diagnostic désigne un problème imputable à un mod. */
export function isModDiagnostic(code: string): boolean {
  return MOD_DIAGNOSTIC_CODES.has(code);
}

/**
 * Dérive un suspectedMod {filename, reason} d'un diagnostic mod. Le
 * GameDiagnostic ne porte pas de champ filename dédié ; on extrait au mieux
 * un nom de .jar du message/details, sinon un libellé générique.
 */
export function toSuspectedMod(
  d: GameDiagnostic,
): { filename: string; reason: string } {
  const haystack = `${d.details ?? ""}\n${d.message}`;
  const jar = haystack.match(/[\w.\-+]+\.jar/);
  return {
    filename: jar ? jar[0] : "Mod incompatible",
    reason: d.message,
  };
}

type DiagnosticsState = {
  lastModDiagnostic: GameDiagnostic | null;
  setModDiagnostic: (d: GameDiagnostic) => void;
  clear: () => void;
};

export const useDiagnosticsStore = create<DiagnosticsState>((set) => ({
  lastModDiagnostic: null,
  setModDiagnostic: (d) => set({ lastModDiagnostic: d }),
  clear: () => set({ lastModDiagnostic: null }),
}));
