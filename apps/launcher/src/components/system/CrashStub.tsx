import { useEffect } from "react";
import { onGameCrashed } from "../../lib/launcher";
import { useCrashStore } from "../../stores/crash-store";

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
    let unlisten: (() => void) | null = null;
    onGameCrashed((c) => {
      openCrash({
        exitCode: c.exitCode,
        summary:
          "Le jeu s'est fermé de manière inattendue. Consulte le journal ci-dessous pour en identifier la cause.",
        suspectedMod: null,
        logPath: c.stderrPath,
        stderrTail: c.stderrTail.trim().length > 0 ? c.stderrTail : null,
      });
    }).then((fn) => {
      unlisten = fn;
    });
    return () => {
      if (unlisten) unlisten();
    };
  }, [openCrash]);

  return null;
}
