import { useEffect } from "react";
import { useCrashStore, type GameCrashReport } from "../../stores/crash-store";

// Expose window.__triggerCrash en dev pour pouvoir ouvrir le modal de
// crash depuis la console DevTools sans avoir a faire crasher la JVM
// (utile pour tester le visuel avant le branchement events Tauri).
//
// Usage console :
//   window.__triggerCrash()                         // payload demo
//   window.__triggerCrash({ exitCode: 134, ... })  // payload custom
//
// TODO(crash-pipeline): brancher le vrai trigger sur les events Tauri du
// game launcher. Le LogAnalyzer (src-tauri/src/launcher/diagnostics.rs)
// emet deja game:diagnostic avec une variante JVM_OUT_OF_MEMORY / etc.
// Un event game:crashed devra remonter l'exit code de la JVM et le
// suspectedMod du dernier diagnostic MOD_MC_VERSION_MISMATCH. Ici on
// fait juste un setup useEffect, le composant ne rend rien.

declare global {
  interface Window {
    __triggerCrash?: (report?: Partial<GameCrashReport>) => void;
  }
}

const DEMO_REPORT: GameCrashReport = {
  exitCode: 255,
  summary:
    "Le serveur interne a rencontré une erreur fatale en mettant à jour un block entity.",
  suspectedMod: {
    filename: "lithium-fabric-mc1.21.1-0.12.2.jar",
    reason: "Mod patché incompatible avec Fabric Loader courant",
  },
  logPath: null,
};

export function CrashStub() {
  const openCrash = useCrashStore((s) => s.open);

  useEffect(() => {
    if (!import.meta.env.DEV) return;
    window.__triggerCrash = (override) => {
      openCrash({ ...DEMO_REPORT, ...override });
    };
    return () => {
      delete window.__triggerCrash;
    };
  }, [openCrash]);

  return null;
}
