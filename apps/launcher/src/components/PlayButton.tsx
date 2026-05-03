import { motion } from "framer-motion";
import { useEffect, useState } from "react";
import { AlertTriangle, Lock, Play, RefreshCw } from "lucide-react";
import { useLaunchStore } from "../stores/launch-store";
import { cn } from "../lib/cn";
import {
  applyUpdate,
  checkUpdate,
  launchGame,
  onDownloadProgress,
  onGameExited,
  onGameStarted,
  type DownloadProgress,
  type UpdatePreview,
} from "../lib/launcher";
import { DownloadModal } from "./DownloadModal";

export function PlayButton() {
  const phase = useLaunchStore((s) => s.phase);
  const setPhase = useLaunchStore((s) => s.setPhase);

  const [preview, setPreview] = useState<UpdatePreview | null>(null);
  const [progress, setProgress] = useState<DownloadProgress | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Pre-check au montage : on telecharge le manifest, on verifie la signature,
  // et on calcule le diff. C'est ce qui distingue le bouton "Jouer" de
  // "Telechargement X%".
  useEffect(() => {
    let cancelled = false;
    setPhase("checking");
    (async () => {
      try {
        const p = await checkUpdate();
        if (cancelled) return;
        setPreview(p);
        if (p.launcherOutdated) {
          setPhase("blocked");
        } else {
          setPhase(p.plan.length === 0 ? "ready" : "idle");
        }
      } catch (err) {
        if (cancelled) return;
        setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
        setPhase("blocked");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [setPhase]);

  // Subscribe to download progress events from Rust.
  useEffect(() => {
    let unlisten: (() => void) | null = null;
    onDownloadProgress((p) => setProgress(p)).then((fn) => {
      unlisten = fn;
    });
    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  // Subscribe to game lifecycle events.
  useEffect(() => {
    const unlistens: Array<() => void> = [];
    onGameStarted(() => setPhase("running")).then((fn) => unlistens.push(fn));
    onGameExited(() => setPhase("ready")).then((fn) => unlistens.push(fn));
    return () => {
      unlistens.forEach((fn) => fn());
    };
  }, [setPhase]);

  async function handleClick() {
    if (phase === "blocked" || phase === "running" || phase === "downloading" || phase === "checking") {
      return;
    }
    setError(null);

    // Si on a deja tout, on lance directement.
    if (preview && preview.plan.length === 0) {
      try {
        await launchGame();
        // setPhase("running") arrive via l'event game:started
      } catch (err) {
        setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
      }
      return;
    }

    // Sinon, on telecharge ce qui manque puis on relance le check.
    setPhase("downloading");
    setProgress(null);
    try {
      const result = await applyUpdate();
      if (result.kind === "launcher_outdated") {
        setError(`Launcher trop vieux : ${result.current} < ${result.required}.`);
        setPhase("blocked");
        return;
      }
      const fresh = await checkUpdate();
      setPreview(fresh);
      setPhase(fresh.plan.length === 0 ? "ready" : "idle");
    } catch (err) {
      setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
      setPhase("idle");
    }
  }

  const isBlocked = phase === "blocked";
  const isDownloading = phase === "downloading";
  const isChecking = phase === "checking";

  const label = (() => {
    if (isBlocked) return "Inaccessible";
    if (isChecking) return "Verification...";
    if (isDownloading) {
      const percent = progress
        ? Math.min(100, Math.round((progress.bytesDownloaded / Math.max(progress.bytesTotal, 1)) * 100))
        : 0;
      return `Telechargement ${percent}%`;
    }
    if (phase === "running") return "En jeu...";
    if (preview && preview.plan.length > 0) return "Mettre a jour";
    return "Jouer";
  })();

  return (
    <>
      <div className="flex flex-col items-center">
        <motion.button
          type="button"
          whileHover={isBlocked || isChecking ? undefined : { scale: 1.02 }}
          whileTap={isBlocked || isChecking ? undefined : { scale: 0.98 }}
          onClick={handleClick}
          disabled={isBlocked || phase === "running" || isChecking}
          className={cn(
            "relative flex h-16 w-72 items-center justify-center gap-3 overflow-hidden rounded-xl text-base font-semibold uppercase tracking-wider transition",
            isBlocked
              ? "cursor-not-allowed bg-surface-elevated text-foreground-subtle"
              : "bg-accent text-white shadow-[0_8px_30px_-10px_rgba(59,91,219,0.6)] hover:bg-accent-hover",
          )}
        >
          <span className="relative flex items-center gap-3">
            {isBlocked ? (
              <Lock className="h-5 w-5" />
            ) : isChecking ? (
              <RefreshCw className="h-5 w-5 animate-spin" />
            ) : (
              <Play className="h-5 w-5 fill-current" />
            )}
            {label}
          </span>
        </motion.button>

        {error && (
          <div className="mt-3 flex max-w-md items-center gap-2 rounded-md border border-danger/40 bg-danger/10 px-3 py-2 text-xs text-danger">
            <AlertTriangle className="h-3.5 w-3.5 flex-shrink-0" />
            <span className="truncate" title={error}>
              {error}
            </span>
          </div>
        )}
      </div>

      <DownloadModal
        open={isDownloading}
        progress={progress}
        version={preview?.version ?? null}
        bytesTotal={preview?.bytesTotal ?? 0}
      />
    </>
  );
}
