import { useState } from "react";
import { Copy, Download, FolderOpen, Loader2, ScrollText } from "lucide-react";
import { revealItemInDir } from "@tauri-apps/plugin-opener";
import { exportLogs } from "../../lib/logs";
import { formatIpcError } from "../../lib/errors";
import { cn } from "../../lib/cn";
import { useLogCounts, useLogsStore } from "../../stores/logs-store";
import { useGameRunning } from "../../stores/game-store";
import { openLogsPanel } from "../../components/game-log/logs-panel-store";
import { Card, COMPACT_BUTTON, InlineAction, plural, StatusLine } from "./ui";

// Carte « Journal du jeu » (onglet Jeu) : résumé du tampon de logs
// (partie en cours ou dernière partie), ouverture du panneau de logs
// (components/game-log/LogsPanel.tsx) et export dans un fichier à joindre à un
// ticket.

export function GameLogsCard() {
  const total = useLogsStore((s) => s.lines.length);
  const counts = useLogCounts();
  const running = useGameRunning();

  const [exporting, setExporting] = useState(false);
  const [exported, setExported] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function runExport() {
    if (exporting) return;
    setExporting(true);
    setError(null);
    setExported(null);
    setCopied(false);
    try {
      const path = await exportLogs();
      setExported(path || null);
    } catch (err) {
      setError(formatIpcError(err, "Export du journal impossible"));
    } finally {
      setExporting(false);
    }
  }

  async function reveal(path: string) {
    setError(null);
    try {
      await revealItemInDir(path);
    } catch (err) {
      setError(formatIpcError(err, "Impossible d'afficher le fichier dans l'explorateur"));
    }
  }

  async function copyPath(path: string) {
    try {
      await navigator.clipboard.writeText(path);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      setError(
        "Copie dans le presse-papiers impossible : sélectionne le chemin et copie-le avec Ctrl+C.",
      );
    }
  }

  const summary =
    total === 0
      ? "Journal vide : il se remplit dès que le jeu démarre."
      : `${running ? "Partie en cours" : "Dernière partie"} : ${plural(total, "ligne")} · ${plural(counts.error, "erreur")} · ${plural(counts.warn, "avertissement")}.`;

  return (
    <Card
      title="Journal du jeu"
      description="Tout ce qu'affiche Minecraft pendant la partie. Utile pour comprendre un crash : exporte-le et joins le fichier à ton ticket."
      icon={<ScrollText className="h-4 w-4" />}
    >
      <div className="px-6 py-5">
        <p
          className={cn(
            "text-xs leading-snug",
            counts.error > 0 ? "text-foreground" : "text-foreground-subtle",
          )}
        >
          {summary}
        </p>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={openLogsPanel}
            className="inline-flex items-center gap-1.5 rounded-md border border-accent/30 bg-accent/10 px-3 py-2.5 text-xs font-medium text-accent transition hover:bg-accent/20"
          >
            <ScrollText className="h-3.5 w-3.5" />
            Ouvrir le journal
          </button>
          <button
            type="button"
            onClick={() => void runExport()}
            disabled={exporting || total === 0}
            className={COMPACT_BUTTON}
          >
            {exporting ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <Download className="h-3.5 w-3.5" />
            )}
            Exporter dans un fichier
          </button>
        </div>

        {exported && (
          <StatusLine
            tone="success"
            action={
              <span className="flex flex-shrink-0 items-center gap-1.5">
                <InlineAction onClick={() => void reveal(exported)} icon={FolderOpen}>
                  Afficher
                </InlineAction>
                <InlineAction onClick={() => void copyPath(exported)} icon={Copy}>
                  {copied ? "Copié" : "Copier le chemin"}
                </InlineAction>
              </span>
            }
          >
            Journal enregistré :{" "}
            <span className="select-all break-all font-mono text-[11.5px] text-foreground-subtle">
              {exported}
            </span>
          </StatusLine>
        )}
        {error && <StatusLine tone="error">{error}</StatusLine>}
      </div>
    </Card>
  );
}
