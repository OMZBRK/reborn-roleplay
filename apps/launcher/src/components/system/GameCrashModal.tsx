import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { motion } from "framer-motion";
import { Check, Copy, FolderOpen, Package, RefreshCw, ScrollText, X } from "lucide-react";
import { revealItemInDir } from "@tauri-apps/plugin-opener";
import { Modal } from "./Modal";
import { formatIpcError } from "../../lib/errors";
import { isTauri } from "../../lib/tauri";
import { openLogsPanel } from "../game-log/logs-panel-store";
import { useCrashStore, type GameCrashReport } from "../../stores/crash-store";
import { useLaunchStore } from "../../stores/launch-store";

type Props = {
  /** Optionnel : si on veut driver le state depuis le parent. Sinon le
   *  modal se branche tout seul sur useCrashStore. */
  report?: GameCrashReport | null;
  onClose?: () => void;
  onRelaunch?: () => void;
};

const FOCUS_RING =
  "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]";

const SECONDARY_BTN = `flex h-9 flex-1 items-center justify-center gap-1.5 rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-3 text-xs text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-surface-elevated)] disabled:opacity-50 ${FOCUS_RING}`;

// Codes de sortie Windows frequents (valeur non signee 32 bits) et leur
// sens. La JVM les renvoie en negatif (-1073741819 = 0xC0000005).
const WINDOWS_EXIT_CODES: Record<number, string> = {
  0xc0000005:
    "accès mémoire refusé, le plus souvent à cause du pilote de la carte graphique (mets-le à jour)",
  0xc0000017: "mémoire insuffisante (ferme d'autres programmes ou baisse la RAM allouée au jeu)",
  0xc000001d: "instruction non prise en charge par le processeur (mod ou Java incompatible)",
  0xc00000fd: "dépassement de pile (stack overflow) dans le jeu ou un mod",
  0xc0000142: "Java n'a pas pu démarrer (bibliothèque bloquée par l'antivirus ?)",
  0xc000013a: "Minecraft a été fermé de force (Ctrl+C ou fermeture de la session Windows)",
  0xc0000409: "arrêt critique de Java, souvent lié au pilote graphique ou à l'antivirus",
  0xcfffffff: "arrêt forcé, Minecraft ne répondait plus et a été fermé",
};

/** Ligne « code de sortie » du rapport de crash, en clair.
 *  -1 est le code d'un crash Minecraft ordinaire : le jeu écrit son rapport
 *  puis appelle System.exit(-1). Windows le rend tel quel (-1), en non signé
 *  (4294967295) ou, tronqué à 8 bits, 255. */
function describeExitCode(code: number | null): string {
  if (code === null) {
    return "Code de sortie inconnu : le launcher n'a pas pu lire comment Minecraft s'est fermé.";
  }
  if (code === -1 || code === 255 || code === 0xffffffff) {
    return "Code de sortie -1 : plantage signalé par Minecraft.";
  }
  if (code === 0) return "Minecraft s'est fermé normalement (code 0).";
  if (code === 1) return "Minecraft s'est arrêté sur une erreur (code 1).";
  const unsigned = code >>> 0;
  const hex = `0x${unsigned.toString(16).toUpperCase()}`;
  const known = WINDOWS_EXIT_CODES[unsigned];
  if (known) return `Code de sortie ${hex} (${code}) : ${known}.`;
  // Codes NTSTATUS (0xC…) : la forme hexadécimale est celle qu'on trouve
  // en ligne ; les petits codes restent en décimal.
  return unsigned > 0xffff ? `Code de sortie ${hex} (${code}).` : `Code de sortie ${code}.`;
}

// Rapport de crash post-partie. Le modal reste ouvert jusqu'a une action
// (Relancer, croix, Echap) : le crash peut etre serieux, on veut qu'il soit
// vu. La fin du journal (~100 lignes, stdout + stderr) s'affiche tout de
// suite ; « Ouvrir le journal » ouvre le panneau complet (filtres,
// recherche, export) au-dessus du modal, « Afficher le fichier » montre le
// journal ecrit sur disque (logs/last-crash.log) dans l'Explorateur.
export function GameCrashModal({ report: reportProp, onClose, onRelaunch }: Props) {
  const reportFromStore = useCrashStore((s) => s.report);
  const closeFromStore = useCrashStore((s) => s.close);

  const requestRelaunch = useLaunchStore((s) => s.requestRelaunch);

  const report = reportProp !== undefined ? reportProp : reportFromStore;
  const handleClose = onClose ?? closeFromStore;

  // Relaunch : si le parent fournit onRelaunch on l'utilise, sinon ferme le
  // modal puis demande a PlayButton de rejouer son lancement (verification
  // des fichiers + phases) via le nonce ; PlayButton attend la fin de la
  // verification en cours si besoin et consomme la demande une seule fois.
  // Le bouton Jouer ne vit que sur l'accueil : on y ramene le joueur si le
  // crash l'a surpris sur une autre page, sinon la demande attendrait sa
  // prochaine visite de l'accueil et le jeu partirait alors tout seul.
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const handleRelaunch =
    onRelaunch ??
    (() => {
      handleClose();
      if (pathname !== "/home") navigate("/home");
      requestRelaunch();
    });

  const [copied, setCopied] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const reportKey = report ? `${report.exitCode}-${report.logPath}-${report.summary}` : null;
  useEffect(() => {
    setCopied(false);
    setActionError(null);
  }, [reportKey]);

  const logText = report?.stderrTail ?? null;
  const logPath = report?.logPath ?? null;

  // L'extrait s'ouvre sur sa fin : la pile, le rapport et « Game crashed! »
  // sont en bas, le haut n'est que du tchat et des avertissements anodins.
  const preRef = useRef<HTMLPreElement>(null);
  useLayoutEffect(() => {
    if (preRef.current) preRef.current.scrollTop = preRef.current.scrollHeight;
  }, [logText]);

  if (!report) return null;

  async function copyLog() {
    const text = logText ?? logPath;
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch (err) {
      setActionError(formatIpcError(err, "Copie dans le presse-papiers impossible"));
    }
  }

  async function revealLog() {
    if (!logPath) return;
    setActionError(null);
    try {
      await revealItemInDir(logPath);
    } catch (err) {
      setActionError(formatIpcError(err, `Ouverture du dossier de ${logPath} impossible`));
    }
  }

  return (
    <Modal open onClose={handleClose} variant="danger">
      <div className="mb-4 flex justify-center">
        {/* Anim shake via framer-motion plutot que CSS keyframes : la motion.div
            parent du Modal applique deja un transform pour le scale-in, donc
            l'enchainer en CSS finit par composer bizarrement (le transform
            CSS est ecrase a chaque frame par framer). La cle change quand le
            report change pour re-trigger l'anim. */}
        <motion.div
          key={`${report.exitCode}-${report.summary}`}
          initial={{ x: 0 }}
          animate={{ x: [0, -5, 5, -4, 4, -2, 2, 0] }}
          transition={{ duration: 0.6, ease: "easeInOut", delay: 0.15 }}
          className="flex h-14 w-14 items-center justify-center rounded-full"
          style={{
            background: "var(--color-danger-soft)",
            color: "var(--color-danger)",
          }}
        >
          <X className="h-8 w-8" strokeWidth={2.5} />
        </motion.div>
      </div>

      <h2 className="text-center font-display text-2xl tracking-wide">Le jeu a planté</h2>
      {/* pre-line : message et conseil arrivent sur deux lignes (CrashStub) ;
          break-words : un nom de classe Java sans espace ne déborde pas. */}
      <p className="mt-2 whitespace-pre-line break-words text-center text-sm leading-relaxed text-[var(--color-foreground-subtle)]">
        {report.summary}
      </p>
      {report.details && (
        <p className="mt-2 break-words text-center font-mono text-[11px] leading-snug text-[var(--color-foreground-muted)]">
          {report.details}
        </p>
      )}
      <p className="mt-1.5 text-center font-mono text-[11px] leading-snug text-[var(--color-danger)]">
        {describeExitCode(report.exitCode)}
      </p>

      {report.suspectedMod && (
        <div className="mt-5">
          <div className="text-[10px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
            Cause suspectée
          </div>
          <div
            className="mt-2 flex items-center gap-3 rounded-md border p-3"
            style={{
              background: "var(--color-surface-overlay)",
              borderColor: "var(--color-border)",
            }}
          >
            <span
              className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-md"
              style={{
                background: "var(--color-warning-soft)",
                color: "var(--color-warning)",
              }}
            >
              <Package className="h-4 w-4" />
            </span>
            <div className="min-w-0 flex-1 text-sm">
              <div className="truncate font-medium" title={report.suspectedMod.filename}>
                {report.suspectedMod.filename}
              </div>
              <div className="line-clamp-2 text-xs text-[var(--color-foreground-subtle)]">
                {report.suspectedMod.reason}
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="mt-5">
        <div className="text-[10px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
          Dernières lignes du journal
        </div>
        {/* overflow-wrap:anywhere : coupe d'abord aux espaces, et ne casse un
            mot (chemin, nom de classe) que s'il ne tient pas sur la ligne. */}
        {logText ? (
          <pre
            ref={preRef}
            className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap [overflow-wrap:anywhere] rounded-md border p-3 font-mono text-[11px] leading-relaxed text-[var(--color-foreground-subtle)]"
            style={{
              background: "var(--color-surface-overlay)",
              borderColor: "var(--color-border)",
            }}
            tabIndex={0}
          >
            {logText}
          </pre>
        ) : (
          <p className="mt-2 rounded-md border border-dashed border-[var(--color-border-strong)] p-3 text-xs leading-relaxed text-[var(--color-foreground-muted)]">
            Le jeu n'a rien écrit avant de se fermer. Si ça se reproduit, vérifie que
            l'antivirus ne bloque pas Java, puis ouvre un ticket.
          </p>
        )}
        {logPath && (
          <div className="mt-1.5 flex items-start gap-2">
            <p className="min-w-0 flex-1 break-all font-mono text-[10px] leading-snug text-[var(--color-foreground-muted)]">
              {logPath}
            </p>
            {isTauri && (
              <button
                type="button"
                onClick={() => void revealLog()}
                title="Afficher le fichier du journal dans l'Explorateur, pour le joindre à un ticket"
                className={`inline-flex flex-shrink-0 items-center gap-1 rounded px-1 text-[10.5px] text-[var(--color-foreground-subtle)] underline-offset-2 hover:text-[var(--color-foreground)] hover:underline ${FOCUS_RING}`}
              >
                <FolderOpen className="h-3 w-3" />
                Afficher le fichier
              </button>
            )}
          </div>
        )}
      </div>

      {actionError && (
        <p role="alert" className="mt-3 text-xs leading-relaxed text-[var(--color-danger)]">
          {actionError}
        </p>
      )}

      <div className="mt-5 flex flex-col gap-2">
        <button
          type="button"
          onClick={handleRelaunch}
          className={`flex h-10 items-center justify-center gap-2 rounded-md bg-[var(--color-accent)] font-medium text-white transition-colors hover:bg-[var(--color-accent-hover)] disabled:opacity-50 ${FOCUS_RING}`}
        >
          <RefreshCw className="h-4 w-4" />
          Relancer le jeu
        </button>
        <div className="flex gap-2">
          <button type="button" onClick={openLogsPanel} className={SECONDARY_BTN}>
            <ScrollText className="h-3 w-3" />
            Ouvrir le journal
          </button>
          <button
            type="button"
            onClick={() => void copyLog()}
            disabled={!logText && !logPath}
            className={SECONDARY_BTN}
          >
            {copied ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
            {copied ? "Copié" : "Copier la fin du journal"}
          </button>
        </div>
      </div>
    </Modal>
  );
}
