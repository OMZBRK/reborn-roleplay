import { useEffect, useRef, useState } from "react";
import { AlertTriangle, Lock, Play, RefreshCw } from "lucide-react";
import { useLaunchStore } from "../../stores/launch-store";
import { useAuthStore } from "../../stores/auth-store";
import { useDiagnosticsStore } from "../../stores/diagnostics-store";
import {
  applyUpdate,
  checkUpdate,
  launchGame,
  onDownloadProgress,
  onGameExited,
  onGameStarted,
  onLaunchProgress,
  type DownloadProgress,
  type LaunchProgress,
  type UpdatePreview,
} from "../../lib/launcher";

function formatBytes(n: number): string {
  if (n < 1024) return `${n} o`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} Ko`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} Mo`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} Go`;
}

export function PlayButton() {
  const phase = useLaunchStore((s) => s.phase);
  const setPhase = useLaunchStore((s) => s.setPhase);
  const relaunchNonce = useLaunchStore((s) => s.relaunchNonce);
  const user = useAuthStore((s) => s.user);
  // role === PLAYER = compte cree mais whitelist non acceptee. Le backend
  // refusera launch_game (GameError::NotWhitelisted), on bloque ici aussi
  // pour eviter un clic perdu et afficher le bon tooltip.
  const isWhitelistGated = user?.role === "PLAYER";

  const [preview, setPreview] = useState<UpdatePreview | null>(null);
  const [progress, setProgress] = useState<DownloadProgress | null>(null);
  const [launch, setLaunch] = useState<LaunchProgress | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Ref vers le dernier handleClick (recréé à chaque render) pour que l'effet
  // de relaunch puisse l'appeler sans se ré-exécuter à chaque render.
  const handleClickRef = useRef<() => void>(() => {});
  const relaunchSeen = useRef(false);

  // Pre-check au montage — manifest, signature, diff. C'est ce qui distingue
  // "Jouer" de "Mettre a jour" / "Telechargement X%".
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

  useEffect(() => {
    let unlisten: (() => void) | null = null;
    onDownloadProgress((p) => setProgress(p)).then((fn) => {
      unlisten = fn;
    });
    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  useEffect(() => {
    let unlisten: (() => void) | null = null;
    onLaunchProgress((p) => setLaunch(p)).then((fn) => {
      unlisten = fn;
    });
    return () => {
      if (unlisten) unlisten();
    };
  }, []);

  useEffect(() => {
    const unlistens: Array<() => void> = [];
    // game:started => la fenetre Minecraft est lancee : on passe de
    // "launching" (preparation visible) a "running" (en jeu).
    onGameStarted(() => {
      setLaunch(null);
      setPhase("running");
    }).then((fn) => unlistens.push(fn));
    onGameExited(() => setPhase("ready")).then((fn) => unlistens.push(fn));
    return () => {
      unlistens.forEach((fn) => fn());
    };
  }, [setPhase]);

  // Relaunch demandé depuis l'extérieur (bouton "Relancer" du modal de crash).
  // On rejoue handleClick — qui early-return si la phase n'est pas lançable
  // (après un crash, game:exited a remis la phase à "ready", donc ça passe).
  // On ignore la valeur initiale du nonce au montage.
  useEffect(() => {
    if (!relaunchSeen.current) {
      relaunchSeen.current = true;
      return;
    }
    handleClickRef.current();
  }, [relaunchNonce]);

  async function handleClick() {
    if (
      phase === "blocked" ||
      phase === "running" ||
      phase === "launching" ||
      phase === "downloading" ||
      phase === "checking"
    ) {
      return;
    }
    setError(null);
    // Nouveau run : on oublie le diagnostic mod d'un run précédent pour ne pas
    // le re-corréler par erreur dans un éventuel crash de ce run.
    useDiagnosticsStore.getState().clear();

    if (preview && preview.plan.length === 0) {
      // Le jeu se prepare (JRE, libs, ~3900 assets, Fabric) AVANT de spawn.
      // On affiche "launching" avec progression au lieu de sauter direct a
      // "running"/"En jeu" qui donnait l'impression d'un freeze.
      setLaunch(null);
      setPhase("launching");
      try {
        await launchGame();
      } catch (err) {
        setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
        setLaunch(null);
        setPhase("ready");
      }
      return;
    }

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

  // Garde la ref à jour à chaque render pour l'effet de relaunch.
  handleClickRef.current = handleClick;

  const isBlocked = phase === "blocked" || isWhitelistGated;
  const isDownloading = phase === "downloading";
  const isLaunching = phase === "launching";
  const isChecking = phase === "checking";
  const isRunning = phase === "running";
  const hasUpdate = !!preview && preview.plan.length > 0;

  const percent = progress
    ? Math.min(
        100,
        Math.round((progress.bytesDownloaded / Math.max(progress.bytesTotal, 1)) * 100),
      )
    : 0;

  // % de l'etape assets (seule a sous-progression). null pour les autres
  // etapes -> l'anneau pulse sans valeur figee.
  const launchPercent =
    launch && launch.total
      ? Math.min(100, Math.round((launch.current ?? 0) / Math.max(launch.total, 1) * 100))
      : null;

  const caption = (() => {
    if (isWhitelistGated) return "Demande de whitelist requise";
    if (isBlocked) return "Launcher trop ancien, mise à jour requise";
    if (isChecking) return "Vérification en cours...";
    if (isDownloading) {
      const dl = progress ? formatBytes(progress.bytesDownloaded) : "0 Mo";
      const tot = progress
        ? formatBytes(progress.bytesTotal)
        : preview
          ? formatBytes(preview.bytesTotal)
          : "";
      return `${preview?.version ? `Patch ${preview.version} · ` : ""}${dl}${tot ? ` / ${tot}` : ""}`;
    }
    if (isLaunching) {
      if (!launch) return "Préparation du lancement...";
      const pct = launchPercent != null ? ` ${launchPercent}%` : "…";
      return `Étape ${launch.step}/${launch.totalSteps} · ${launch.label}${pct}`;
    }
    if (isRunning) return "Le jeu est en cours";
    if (hasUpdate && preview) return `Mise à jour ${preview.version} · ${formatBytes(preview.bytesTotal)}`;
    if (preview) return `v${preview.version} · prêt à lancer`;
    return " ";
  })();

  return (
    <div className="flex flex-col items-center gap-3">
      {isBlocked ? (
        <LockedButton label={isWhitelistGated ? "Whitelist requise" : "Inaccessible"} />
      ) : isDownloading ? (
        <DownloadingButton percent={percent} />
      ) : isLaunching ? (
        <LaunchingButton percent={launchPercent} />
      ) : (
        <PrimaryButton
          label={isChecking ? "Vérification" : isRunning ? "En jeu" : hasUpdate ? "METTRE À JOUR" : "JOUER"}
          disabled={isChecking || isRunning}
          checking={isChecking}
          onClick={handleClick}
        />
      )}

      <div className="h-5 text-[11px] uppercase tracking-[0.18em] text-foreground-muted">
        {caption}
      </div>

      {error && (
        <div className="mt-1 flex max-w-md items-center gap-2 rounded-md border border-danger/40 bg-danger/10 px-3 py-2 text-xs text-danger">
          <AlertTriangle className="h-3.5 w-3.5 flex-shrink-0" />
          <span className="truncate" title={error}>
            {error}
          </span>
        </div>
      )}
    </div>
  );
}

function LockedButton({ label }: { label: string }) {
  return (
    <div className="group relative inline-block">
      <button
        type="button"
        disabled
        className="inline-flex items-center gap-3 rounded-[14px] border border-border-strong bg-surface-elevated px-9 py-4 font-display text-[28px] tracking-[0.08em] text-foreground-muted"
        style={{ minWidth: 240, cursor: "not-allowed" }}
      >
        <Lock className="h-[22px] w-[22px]" strokeWidth={2.2} />
        <span>{label}</span>
      </button>
    </div>
  );
}

function PrimaryButton({
  label,
  disabled,
  checking,
  onClick,
}: {
  label: string;
  disabled: boolean;
  checking: boolean;
  onClick: () => void;
}) {
  // Icone absolute-positioned a gauche pour ne pas decaler le centrage du
  // texte. Sans ca, "Créer mon personnage" (21 chars) tirait le label
  // hors-centre vs "JOUER" (5 chars) qui paraissait centre.
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="reborn-pulse-glow group relative flex items-center justify-center rounded-[14px] px-12 py-4 font-display text-[28px] tracking-[0.08em] text-white transition-transform duration-200 hover:-translate-y-0.5 disabled:cursor-wait disabled:opacity-80"
      style={{
        background: "linear-gradient(180deg, var(--color-accent-hover) 0%, var(--color-accent) 100%)",
        border: "1px solid rgba(255,255,255,0.08)",
        minWidth: 240,
      }}
    >
      <span className="absolute left-5 flex items-center">
        {checking ? (
          <RefreshCw className="h-[26px] w-[26px] animate-spin" strokeWidth={2} />
        ) : (
          <Play className="h-[26px] w-[26px]" strokeWidth={2} fill="currentColor" />
        )}
      </span>
      <span className="text-center">{label}</span>
    </button>
  );
}

function LaunchingButton({ percent }: { percent: number | null }) {
  // Meme anneau que DownloadingButton. percent=null (etapes hors assets :
  // runtime, libs, fabric, spawn) => anneau vide + icone qui tourne pour
  // signaler l'activite indeterminee. percent!=null (assets) => anneau qui
  // se remplit.
  const SIZE = 240;
  const HEIGHT = 60;
  const R = 14;
  const indeterminate = percent == null;
  const offset = indeterminate ? 100 : 100 - percent;

  return (
    <div className="relative inline-block" style={{ padding: 4 }}>
      <svg
        className="pointer-events-none absolute inset-0"
        width="100%"
        height="100%"
        viewBox={`0 0 ${SIZE + 8} ${HEIGHT + 8}`}
        preserveAspectRatio="none"
      >
        <rect
          x="2"
          y="2"
          width={SIZE + 4}
          height={HEIGHT + 4}
          rx={R + 2}
          ry={R + 2}
          fill="none"
          stroke="rgba(160, 24, 43, 0.18)"
          strokeWidth="2"
        />
        {!indeterminate && (
          <rect
            x="2"
            y="2"
            width={SIZE + 4}
            height={HEIGHT + 4}
            rx={R + 2}
            ry={R + 2}
            fill="none"
            stroke="var(--color-accent)"
            strokeWidth="2"
            strokeLinecap="round"
            pathLength="100"
            strokeDasharray="100"
            strokeDashoffset={offset}
            style={{
              transition: "stroke-dashoffset 220ms linear",
              filter: "drop-shadow(0 0 6px rgba(160, 24, 43, 0.6))",
            }}
          />
        )}
      </svg>
      <div
        className="inline-flex items-center justify-center gap-3 rounded-[14px] px-8 font-display tracking-[0.06em] text-white"
        style={{
          width: SIZE,
          height: HEIGHT,
          background:
            "linear-gradient(180deg, rgba(160, 24, 43, 0.95) 0%, var(--color-accent-pressed) 100%)",
          border: "1px solid rgba(255,255,255,0.08)",
          boxShadow: "0 0 24px rgba(160, 24, 43, 0.4)",
        }}
      >
        <RefreshCw className="h-[22px] w-[22px] animate-spin" strokeWidth={2} />
        <span className="text-[22px] leading-none">Préparation</span>
        {!indeterminate && (
          <span className="font-mono text-[16px] tabular-nums">{percent}%</span>
        )}
      </div>
    </div>
  );
}

function DownloadingButton({ percent }: { percent: number }) {
  // Anneau de progression rectangulaire autour du bouton — perimeter normalisé via pathLength="100"
  const SIZE = 240;
  const HEIGHT = 60;
  const R = 14;

  return (
    <div className="relative inline-block" style={{ padding: 4 }}>
      <svg
        className="pointer-events-none absolute inset-0"
        width="100%"
        height="100%"
        viewBox={`0 0 ${SIZE + 8} ${HEIGHT + 8}`}
        preserveAspectRatio="none"
      >
        <rect
          x="2"
          y="2"
          width={SIZE + 4}
          height={HEIGHT + 4}
          rx={R + 2}
          ry={R + 2}
          fill="none"
          stroke="rgba(160, 24, 43, 0.18)"
          strokeWidth="2"
        />
        <rect
          x="2"
          y="2"
          width={SIZE + 4}
          height={HEIGHT + 4}
          rx={R + 2}
          ry={R + 2}
          fill="none"
          stroke="var(--color-accent)"
          strokeWidth="2"
          strokeLinecap="round"
          pathLength="100"
          strokeDasharray="100"
          strokeDashoffset={100 - percent}
          style={{
            transition: "stroke-dashoffset 220ms linear",
            filter: "drop-shadow(0 0 6px rgba(160, 24, 43, 0.6))",
          }}
        />
      </svg>
      <div
        className="inline-flex items-center justify-center gap-3 rounded-[14px] px-8 font-display tracking-[0.06em] text-white"
        style={{
          width: SIZE,
          height: HEIGHT,
          background:
            "linear-gradient(180deg, rgba(160, 24, 43, 0.95) 0%, var(--color-accent-pressed) 100%)",
          border: "1px solid rgba(255,255,255,0.08)",
          boxShadow: "0 0 24px rgba(160, 24, 43, 0.4)",
        }}
      >
        <span className="text-[22px] leading-none">Téléchargement</span>
        <span className="font-mono text-[16px] tabular-nums">{percent}%</span>
      </div>
    </div>
  );
}
