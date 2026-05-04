import { motion, AnimatePresence } from "framer-motion";
import { Download } from "lucide-react";
import type { DownloadProgress } from "../lib/launcher";

type Props = {
  open: boolean;
  progress: DownloadProgress | null;
  version: string | null;
  bytesTotal: number;
};

function formatBytes(n: number): string {
  if (n < 1024) return `${n} o`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} ko`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} Mo`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} Go`;
}

export function DownloadModal({ open, progress, version, bytesTotal }: Props) {
  const percent = progress
    ? Math.min(100, Math.round((progress.bytesDownloaded / Math.max(progress.bytesTotal, 1)) * 100))
    : 0;
  const completed = progress?.completed ?? 0;
  const total = progress?.total ?? 0;
  const bytesDl = progress?.bytesDownloaded ?? 0;
  const bytesAll = progress?.bytesTotal ?? bytesTotal;

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm"
        >
          <motion.div
            initial={{ scale: 0.96, y: 8 }}
            animate={{ scale: 1, y: 0 }}
            exit={{ scale: 0.96, y: 8 }}
            transition={{ duration: 0.2 }}
            className="w-[480px] rounded-[--radius-card] border border-border bg-surface-elevated p-8"
          >
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-accent/15 text-accent">
                <Download className="h-5 w-5" />
              </div>
              <div>
                <p className="text-xs uppercase tracking-widest text-foreground-subtle">
                  Mise à jour
                </p>
                <h2 className="font-display text-lg font-semibold">
                  Téléchargement {version ?? ""}
                </h2>
              </div>
            </div>

            <div className="mt-6">
              <div className="flex items-baseline justify-between text-sm">
                <span className="text-foreground-subtle">
                  Fichier {Math.min(completed + 1, total)} / {total}
                </span>
                <span className="font-medium tabular-nums">{percent}%</span>
              </div>

              <div className="mt-2 h-2 overflow-hidden rounded-full bg-background">
                <motion.div
                  className="h-full bg-accent"
                  initial={{ width: 0 }}
                  animate={{ width: `${percent}%` }}
                  transition={{ duration: 0.25 }}
                />
              </div>

              <div className="mt-2 flex items-baseline justify-between text-xs text-foreground-subtle">
                <span className="truncate" title={progress?.currentFile ?? undefined}>
                  {progress?.currentFile ?? "Initialisation..."}
                </span>
                <span className="tabular-nums">
                  {formatBytes(bytesDl)} / {formatBytes(bytesAll)}
                </span>
              </div>
            </div>

            <p className="mt-6 text-xs text-foreground-subtle">
              Garde le launcher ouvert. La connexion sera coupée si tu fermes la
              fenêtre. Les fichiers sont vérifiés par signature Ed25519 et hash
              SHA-256.
            </p>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
