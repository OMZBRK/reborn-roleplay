import { useEffect, useRef } from "react";
import { useTauriEvent } from "../../hooks/use-tauri-event";
import type { GameDiagnostic } from "../../lib/launcher";
import { useCrashStore } from "../../stores/crash-store";
import {
  isModDiagnostic,
  toSuspectedMod,
  useDiagnosticsStore,
} from "../../stores/diagnostics-store";
import { initGameStore, useGameRunning } from "../../stores/game-store";
import { useLaunchStore } from "../../stores/launch-store";
import { LogsPanel } from "../game-log/LogsPanel";

// Hote des evenements du jeu, monte une fois dans le layout authentifie :
//   - branche l'etat « jeu en cours » et la collecte des logs (initGameStore,
//     compte de references : un second appel ailleurs n'empile rien) ;
//   - garde la phase du bouton Jouer (launch-store) synchro avec la partie,
//     meme quand l'accueil n'est pas affiche (l'updater la lit), ainsi que
//     la derniere progression de preparation / telechargement ;
//   - transforme `game:crashed` (JVM sortie non-zero non sollicitee — un
//     arret via « Arreter le jeu » n'en emet pas) en rapport de crash, avec
//     la cause tiree des diagnostics recus pendant la partie ;
//   - heberge le panneau « Journal du jeu » (rendu seulement quand ouvert).
//
// Le declenchement manuel en dev passe par window.__reborn (DevHelpers).

/** Diagnostics qui expliquent un crash, du plus parlant au moins parlant.
 *  Seuls ces codes peuvent servir de cause : ils signalent ce qui ferme
 *  vraiment le jeu. SERVER_UNREACHABLE ou MC_AUTH_INVALID (« Invalid
 *  session ») ne sont qu'une déconnexion, le client continue de tourner :
 *  les retenir attribuait leur message au premier crash venu. */
const CAUSE_PRIORITY: readonly string[] = [
  "MC_CRASH_REPORT",
  "JVM_OUT_OF_MEMORY",
  "GPU_DRIVER_ISSUE",
  "JVM_MAIN_CLASS_NOT_FOUND",
  "MC_INVALID_UUID",
];

const DEFAULT_SUMMARY =
  "Le jeu s'est fermé de manière inattendue. La fin du journal ci-dessous indique généralement la cause.";

/** Assemble message et conseil d'un diagnostic en phrases distinctes. Le
 *  message d'un rapport de crash finit souvent sur le texte brut de
 *  l'exception (« … is null »), sans point : collé tel quel au conseil, ça
 *  donnait « … is null Le rapport complet … ». Chaque morceau reçoit sa
 *  ponctuation finale, et un saut de ligne les sépare (le modal l'affiche,
 *  ailleurs il compte comme une espace). */
function joinSentences(parts: Array<string | null | undefined>): string {
  return parts
    .map((p) => (p ?? "").trim().replace(/[\s,;:]+$/, ""))
    .filter((p) => p.length > 0)
    .map((p) => (/[.!?…][»"')\]]*$/.test(p) ? p : `${p}.`))
    .join("\n");
}

export function CrashStub() {
  const openCrash = useCrashStore((s) => s.open);
  // Meilleure cause « non-mod » vue pendant la partie en cours.
  const causeRef = useRef<GameDiagnostic | null>(null);

  useEffect(() => initGameStore(), []);

  // Resynchro apres un rechargement de la fenetre en pleine partie : le
  // game-store relit l'etat du backend (launcher_game_status), la phase suit.
  const running = useGameRunning();
  useEffect(() => {
    if (!running) return;
    const launch = useLaunchStore.getState();
    if (launch.phase !== "running") launch.setPhase("running");
  }, [running]);

  // Phase du bouton Jouer suivant la partie. Sur les evenements eux-memes
  // (pas sur le game-store) : la phase peut etre « running » sans que le
  // store le sache (fenetre rechargee pendant une partie, cf. PlayButton).
  useTauriEvent("game:started", () => {
    causeRef.current = null;
    const launch = useLaunchStore.getState();
    launch.setPhase("running");
    launch.setLaunchProgress(null);
    launch.setDownloadProgress(null);
  });
  useTauriEvent("game:exited", () => {
    const launch = useLaunchStore.getState();
    if (launch.phase === "running" || launch.phase === "launching") launch.setPhase("ready");
    launch.setLaunchProgress(null);
    launch.setDownloadProgress(null);
  });

  // Progression de la preparation et du telechargement, gardee dans le
  // launch-store meme quand l'accueil (PlayButton) n'est pas affiche.
  useTauriEvent("launch:progress", (p) => useLaunchStore.getState().setLaunchProgress(p));
  useTauriEvent("manifest:progress", (p) => useLaunchStore.getState().setDownloadProgress(p));

  // Memorise au fil de l'eau le dernier diagnostic imputable a un mod
  // (mismatch de version, echec de resolution Fabric) et la cause la plus
  // parlante parmi les autres. Lus au moment du crash (pas de closure perimee).
  useTauriEvent("game:diagnostic", (d) => {
    if (!d?.code) return;
    if (isModDiagnostic(d.code)) {
      useDiagnosticsStore.getState().setModDiagnostic(d);
      return;
    }
    if (d.severity === "warning") return;
    const rank = CAUSE_PRIORITY.indexOf(d.code);
    if (rank < 0) return;
    const current = causeRef.current;
    if (!current || rank <= CAUSE_PRIORITY.indexOf(current.code)) causeRef.current = d;
  });

  useTauriEvent("game:crashed", (c) => {
    const modDiag = useDiagnosticsStore.getState().lastModDiagnostic;
    const cause = causeRef.current;
    const summary = (cause && joinSentences([cause.message, cause.hint])) || DEFAULT_SUMMARY;
    const details = cause?.details?.trim() || null;
    const tail = typeof c?.stderrTail === "string" ? c.stderrTail : "";
    openCrash({
      exitCode: typeof c?.exitCode === "number" ? c.exitCode : null,
      summary,
      // Ligne brute du journal (l'exception d'un rapport de crash), affichée
      // à part sous le résumé, sauf si le message la cite déjà (backend
      // antérieur, dont le message MC_CRASH_REPORT reprenait l'exception).
      details: details && cause && !cause.message.includes(details) ? details : null,
      suspectedMod: modDiag ? toSuspectedMod(modDiag) : null,
      logPath: c?.stderrPath || null,
      stderrTail: tail.trim().length > 0 ? tail : null,
    });
  });

  return <LogsPanel />;
}
