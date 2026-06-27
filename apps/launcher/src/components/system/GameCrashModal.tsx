import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Copy, FileText, Package, RefreshCw, X } from "lucide-react";
import { Modal } from "./Modal";
import { readCrashLog } from "../../lib/launcher";
import { useCrashStore, type GameCrashReport } from "../../stores/crash-store";

type Props = {
  /** Optionnel : si on veut driver le state depuis le parent. Sinon le
   *  modal se branche tout seul sur useCrashStore. */
  report?: GameCrashReport | null;
  onClose?: () => void;
  onRelaunch?: () => void;
};

// Affiche le rapport de crash post-game. Volontairement bloquant (pas de
// dismissable sans action explicite) : le crash peut etre serieux et on
// veut que l'utilisateur acquitte. Bouton Relaunch en primary, log Copy /
// Voir le log complet en secondary. Le tail de stderr (~100 lignes) est
// affiche immediatement ; "Voir le log complet" lit le fichier via la
// commande Tauri read_crash_log (plafonne a 200 Ko).
export function GameCrashModal({ report: reportProp, onClose, onRelaunch }: Props) {
  const reportFromStore = useCrashStore((s) => s.report);
  const closeFromStore = useCrashStore((s) => s.close);

  const report = reportProp !== undefined ? reportProp : reportFromStore;
  const handleClose = onClose ?? closeFromStore;

  // Log complet charge a la demande. Reset des qu'on change de rapport.
  const [fullLog, setFullLog] = useState<string | null>(null);
  const [loadingLog, setLoadingLog] = useState(false);
  const [logError, setLogError] = useState<string | null>(null);

  const reportKey = report ? `${report.exitCode}-${report.logPath}` : null;
  useEffect(() => {
    setFullLog(null);
    setLoadingLog(false);
    setLogError(null);
  }, [reportKey]);

  if (!report) return null;

  // Ce qu'on affiche dans la zone log : le log complet s'il est charge,
  // sinon le tail stderr remonte par l'event.
  const logText = fullLog ?? report.stderrTail;

  async function loadFullLog() {
    if (!report?.logPath || loadingLog) return;
    setLoadingLog(true);
    setLogError(null);
    try {
      const content = await readCrashLog(report.logPath);
      setFullLog(content);
    } catch (err) {
      setLogError(
        typeof err === "string"
          ? err
          : (err as { message?: string }).message ?? "Lecture du log impossible",
      );
    } finally {
      setLoadingLog(false);
    }
  }

  function copyLog() {
    // Copie le contenu reellement affiche (log complet si charge, sinon le
    // tail). Fallback sur le chemin si on n'a aucun texte.
    const text = logText ?? report?.logPath;
    if (!text) return;
    void navigator.clipboard.writeText(text);
  }

  return (
    <Modal open onClose={handleClose} variant="danger">
      <div className="mb-4 flex justify-center">
        {/* Anim shake via framer-motion plutot que CSS keyframes : la motion.div
            parent du Modal applique deja un transform pour le scale-in, donc
            l'enchainer en CSS finit par composer bizarrement (le transform
            CSS est ecrase a chaque frame par framer). framer-motion gere
            proprement la composition. La cle change quand le report change
            pour re-trigger l'anim. */}
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

      <h2 className="text-center font-display text-2xl tracking-wide">
        Crash détecté
      </h2>
      <p className="mt-2 text-center text-sm text-[var(--color-foreground-subtle)]">
        {report.summary}
        <br />
        <span
          className="font-mono text-[11px]"
          style={{ color: "var(--color-danger)" }}
        >
          Exit code: {report.exitCode ?? "inconnu"}
        </span>
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
              <div className="truncate font-medium">{report.suspectedMod.filename}</div>
              <div className="truncate text-xs text-[var(--color-foreground-subtle)]">
                {report.suspectedMod.reason}
              </div>
            </div>
          </div>
        </div>
      )}

      {logText && (
        <div className="mt-5">
          <div className="flex items-center justify-between">
            <span className="text-[10px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
              {fullLog ? "Journal complet" : "Dernières lignes du journal"}
            </span>
            {logError && (
              <span className="text-[10px]" style={{ color: "var(--color-danger)" }}>
                {logError}
              </span>
            )}
          </div>
          <pre
            className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap break-all rounded-md border p-3 font-mono text-[11px] leading-relaxed text-[var(--color-foreground-subtle)]"
            style={{
              background: "var(--color-surface-overlay)",
              borderColor: "var(--color-border)",
            }}
          >
            {logText}
          </pre>
        </div>
      )}

      <div className="mt-6 flex flex-col gap-2">
        <button
          type="button"
          onClick={() => onRelaunch?.()}
          disabled={!onRelaunch}
          className="flex h-10 items-center justify-center gap-2 rounded-md bg-[var(--color-accent)] font-medium text-white transition-colors hover:bg-[var(--color-accent-hover)] disabled:opacity-50"
        >
          <RefreshCw className="h-4 w-4" />
          Relancer le jeu
        </button>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={copyLog}
            disabled={!logText && !report.logPath}
            className="flex h-9 flex-1 items-center justify-center gap-1.5 rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-3 text-xs text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-surface-elevated)] disabled:opacity-50"
          >
            <Copy className="h-3 w-3" />
            Copier le log
          </button>
          <button
            type="button"
            onClick={loadFullLog}
            disabled={!report.logPath || loadingLog || fullLog !== null}
            className="flex h-9 flex-1 items-center justify-center gap-1.5 rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-3 text-xs text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-surface-elevated)] disabled:opacity-50"
          >
            <FileText className="h-3 w-3" />
            {loadingLog
              ? "Chargement…"
              : fullLog !== null
                ? "Log complet chargé"
                : "Voir le log complet"}
          </button>
        </div>
      </div>
    </Modal>
  );
}
