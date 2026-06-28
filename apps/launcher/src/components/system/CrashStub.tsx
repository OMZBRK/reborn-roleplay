import { useEffect } from "react";
import { onGameCrashed, onGameDiagnostic } from "../../lib/launcher";
import { useCrashStore } from "../../stores/crash-store";
import {
  isModDiagnostic,
  toSuspectedMod,
  useDiagnosticsStore,
} from "../../stores/diagnostics-store";

// Branche le modal de crash sur le vrai event Tauri `game:crashed`, emis par
// le backend (src-tauri/src/launcher/game.rs::await_game_end) quand la JVM se
// termine avec un status non-zero qui n'a pas ete sollicite par l'utilisateur
// (un arret volontaire via `launcher_stop_game` ne declenche pas de crash).
//
// Le LogAnalyzer (src-tauri/src/launcher/diagnostics.rs) continue d'emettre
// game:diagnostic en parallele pendant le run (toasts in-app) ; ici on ne
// gere que le rapport post-mortem avec le tail de stderr.
//
// Composant invisible : il ne fait que poser le listener au montage. Le
// declenchement manuel en dev passe par window.__reborn.crash (DevHelpers).
export function CrashStub() {
  const openCrash = useCrashStore((s) => s.open);

  useEffect(() => {
    const unlistens: Array<() => void> = [];

    // Mémorise au fil de l'eau le dernier diagnostic imputable à un mod
    // (mismatch de version, échec de résolution Fabric, purge). On lit
    // l'état via getState() au moment du crash pour éviter une closure
    // périmée.
    onGameDiagnostic((d) => {
      if (isModDiagnostic(d.code)) {
        useDiagnosticsStore.getState().setModDiagnostic(d);
      }
    }).then((fn) => unlistens.push(fn));

    onGameCrashed((c) => {
      // Corrélation : si un diagnostic mod a précédé le crash, on le
      // remonte comme cause suspectée dans le rapport.
      const modDiag = useDiagnosticsStore.getState().lastModDiagnostic;
      openCrash({
        exitCode: c.exitCode,
        summary:
          "Le jeu s'est fermé de manière inattendue. Consulte le journal ci-dessous pour en identifier la cause.",
        suspectedMod: modDiag ? toSuspectedMod(modDiag) : null,
        logPath: c.stderrPath,
        stderrTail: c.stderrTail.trim().length > 0 ? c.stderrTail : null,
      });
    }).then((fn) => unlistens.push(fn));

    return () => unlistens.forEach((fn) => fn());
  }, [openCrash]);

  return null;
}
