import { useState } from "react";
import { Loader2, RefreshCw, ShieldCheck, Trash2, Wrench } from "lucide-react";
import {
  checkUpdate,
  cleanObsolete,
  reinstallAll,
  type CleanResult,
  type DownloadProgress,
} from "../../lib/launcher";
import { formatIpcError } from "../../lib/errors";
import { cn } from "../../lib/cn";
import { useTauriEvent } from "../../hooks/use-tauri-event";
import { useGameRunning } from "../../stores/game-store";
import { useLaunchStore } from "../../stores/launch-store";
import { repairGameFiles } from "./repair";
import {
  Card,
  formatBytes,
  InlineAction,
  plural,
  RetryAction,
  StatusLine,
  WIDE_BUTTON,
} from "./ui";

// Cartes d'entretien de l'onglet Jeu : intégrité (vérifier / réparer),
// nettoyage, réinstallation. Toutes désactivées pendant un lancement ou une
// partie : les jars sont verrouillés par la JVM et le backend refuserait de
// toute façon (kind `game_running`) ; on le dit avant le clic.

/** Phrase expliquant pourquoi l'entretien est indisponible (avec quoi faire
 *  pour `action`), ou null. La verification de l'accueil (« checking »,
 *  quelques secondes au demarrage) n'est pas un lancement : on demande
 *  seulement de patienter. */
function useMaintenanceLock(action: string): string | null {
  const running = useGameRunning();
  const phase = useLaunchStore((s) => s.phase);
  if (running || phase === "running") return `Le jeu est ouvert : ferme-le pour ${action}.`;
  if (phase === "downloading" || phase === "launching") {
    return `Le jeu est en cours de lancement : ferme-le une fois ouvert pour ${action}.`;
  }
  if (phase === "checking") {
    return `Vérification des fichiers en cours : patiente quelques secondes pour ${action}.`;
  }
  return null;
}

function outdatedMessage(current: string, required: string): string {
  return `Ce launcher (v${current}) est trop ancien pour le modpack actuel (v${required} minimum) : redémarre-le pour installer la mise à jour.`;
}

// ──────────────────────────────────────────────────────
//  Intégrité : vérifier / réparer
// ──────────────────────────────────────────────────────

type VerifyState =
  | { kind: "idle" }
  | { kind: "checking" }
  | { kind: "ok" }
  | { kind: "needs"; count: number; bytes: number }
  | { kind: "outdated"; current: string; required: string }
  | { kind: "error"; message: string };

type RepairState =
  | { kind: "idle" }
  | { kind: "working"; progress: DownloadProgress | null }
  | { kind: "done"; files: number; fullVerify: boolean }
  | { kind: "outdated"; current: string; required: string }
  | { kind: "error"; message: string };

export function IntegrityCard() {
  const lock = useMaintenanceLock("vérifier ou réparer les fichiers");
  const [verify, setVerify] = useState<VerifyState>({ kind: "idle" });
  const [repair, setRepair] = useState<RepairState>({ kind: "idle" });
  const working = verify.kind === "checking" || repair.kind === "working";

  // Progression du retéléchargement (émise par download_plan), seulement
  // pendant une réparation lancée d'ici.
  useTauriEvent(
    "manifest:progress",
    (p) => setRepair((r) => (r.kind === "working" ? { kind: "working", progress: p } : r)),
    repair.kind === "working",
  );

  async function runVerify() {
    if (working || lock) return;
    setVerify({ kind: "checking" });
    setRepair({ kind: "idle" });
    try {
      const preview = await checkUpdate();
      if (preview?.launcherOutdated) {
        setVerify({
          kind: "outdated",
          current: preview.launcherVersion,
          required: preview.minLauncherVersion,
        });
      } else if (!preview || preview.plan.length === 0) {
        setVerify({ kind: "ok" });
      } else {
        setVerify({ kind: "needs", count: preview.plan.length, bytes: preview.bytesTotal });
      }
    } catch (err) {
      setVerify({ kind: "error", message: formatIpcError(err, "Vérification impossible") });
    }
  }

  async function runRepair() {
    if (working || lock) return;
    setRepair({ kind: "working", progress: null });
    setVerify({ kind: "idle" });
    try {
      const { status, fullVerify } = await repairGameFiles();
      if (status?.kind === "launcher_outdated") {
        setRepair({ kind: "outdated", current: status.current, required: status.required });
      } else {
        setRepair({
          kind: "done",
          files: status?.kind === "updated" ? status.filesDownloaded : 0,
          fullVerify,
        });
      }
    } catch (err) {
      setRepair({ kind: "error", message: formatIpcError(err, "Réparation impossible") });
    }
  }

  return (
    <Card
      title="Intégrité des fichiers"
      description="Vérifie que tous les fichiers du jeu sont présents et intacts, et retélécharge ceux qui sont abîmés."
      icon={<ShieldCheck className="h-4 w-4" />}
    >
      <div className="px-6 py-5">
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          <button
            type="button"
            onClick={() => void runVerify()}
            disabled={working || lock !== null}
            className={WIDE_BUTTON}
          >
            {verify.kind === "checking" ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <ShieldCheck className="h-4 w-4" />
            )}
            Vérifier les fichiers
          </button>
          <button
            type="button"
            onClick={() => void runRepair()}
            disabled={working || lock !== null}
            className={WIDE_BUTTON}
          >
            {repair.kind === "working" ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Wrench className="h-4 w-4" />
            )}
            Réparer les fichiers du jeu
          </button>
        </div>

        {lock && <StatusLine tone="info">{lock}</StatusLine>}

        {verify.kind === "ok" && (
          <StatusLine tone="success">Tous les fichiers du modpack sont présents et intacts.</StatusLine>
        )}
        {verify.kind === "needs" && (
          <StatusLine
            tone="warning"
            action={
              !lock && (
                <InlineAction onClick={() => void runRepair()} icon={Wrench}>
                  Réparer maintenant
                </InlineAction>
              )
            }
          >
            {plural(verify.count, "fichier")} à réparer ({formatBytes(verify.bytes)}) : répare-les
            maintenant ou relance le jeu, ils seront retéléchargés.
          </StatusLine>
        )}
        {verify.kind === "outdated" && (
          <StatusLine tone="warning">{outdatedMessage(verify.current, verify.required)}</StatusLine>
        )}
        {verify.kind === "error" && (
          <StatusLine
            tone="error"
            action={!lock && <RetryAction onClick={() => void runVerify()} />}
          >
            {verify.message}
          </StatusLine>
        )}

        {repair.kind === "working" && <RepairProgress progress={repair.progress} />}
        {repair.kind === "done" && (
          <StatusLine tone="success">
            {repair.files > 0
              ? `Réparation terminée : ${plural(repair.files, "fichier retéléchargé", "fichiers retéléchargés")}.`
              : "Réparation terminée : les fichiers du modpack étaient déjà intacts."}
            {repair.fullVerify &&
              " Java, les bibliothèques et les ressources de Minecraft seront entièrement revérifiés au prochain « Jouer » (quelques secondes de plus)."}
          </StatusLine>
        )}
        {repair.kind === "outdated" && (
          <StatusLine tone="warning">{outdatedMessage(repair.current, repair.required)}</StatusLine>
        )}
        {repair.kind === "error" && (
          <StatusLine
            tone="error"
            action={!lock && <RetryAction onClick={() => void runRepair()} />}
          >
            {repair.message}
          </StatusLine>
        )}
      </div>
    </Card>
  );
}

function RepairProgress({ progress }: { progress: DownloadProgress | null }) {
  const pct =
    progress && progress.bytesTotal > 0
      ? Math.min(100, (progress.bytesDownloaded / progress.bytesTotal) * 100)
      : null;
  return (
    <div role="status" className="mt-3">
      <p className="text-xs leading-snug text-foreground-subtle">
        {progress && progress.total > 0
          ? `Téléchargement : ${progress.completed} / ${plural(progress.total, "fichier")} · ${formatBytes(progress.bytesDownloaded)} sur ${formatBytes(progress.bytesTotal)}`
          : "Analyse des fichiers du modpack…"}
      </p>
      <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-border">
        <div
          className={cn("h-full rounded-full transition-[width] duration-200", pct === null && "animate-pulse")}
          style={{
            width: `${pct ?? 100}%`,
            opacity: pct === null ? 0.35 : 1,
            background:
              "linear-gradient(90deg, var(--color-accent) 0%, var(--color-accent-hover) 100%)",
          }}
        />
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  Nettoyage
// ──────────────────────────────────────────────────────

type CleanState =
  | { kind: "idle" }
  | { kind: "working" }
  | { kind: "done"; result: CleanResult }
  | { kind: "error"; message: string };

export function CleanCard() {
  const lock = useMaintenanceLock("lancer le nettoyage");
  const [cleaning, setCleaning] = useState<CleanState>({ kind: "idle" });

  async function runClean() {
    if (cleaning.kind === "working" || lock) return;
    setCleaning({ kind: "working" });
    try {
      const result = await cleanObsolete();
      setCleaning({ kind: "done", result: result ?? { removed: [], freedMb: 0 } });
    } catch (err) {
      setCleaning({ kind: "error", message: formatIpcError(err, "Nettoyage impossible") });
    }
  }

  const removed = cleaning.kind === "done" ? cleaning.result.removed.length : 0;

  return (
    <Card
      title="Nettoyage"
      description="Supprime les résidus obsolètes (caches MCEF, anciennes versions, rapports de crash). Se fait aussi automatiquement à chaque mise à jour."
      icon={<RefreshCw className="h-4 w-4" />}
    >
      <div className="px-6 py-5">
        <button
          type="button"
          onClick={() => void runClean()}
          disabled={cleaning.kind === "working" || lock !== null}
          className={WIDE_BUTTON}
        >
          {cleaning.kind === "working" ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <RefreshCw className="h-4 w-4" />
          )}
          Nettoyer les fichiers obsolètes
        </button>
        {lock && <StatusLine tone="info">{lock}</StatusLine>}
        {cleaning.kind === "done" && (
          <StatusLine tone="success">
            {removed === 0
              ? "Rien à nettoyer : c'est déjà propre."
              : `${plural(removed, "élément supprimé", "éléments supprimés")} · ${cleaning.result.freedMb.toLocaleString("fr-FR", { maximumFractionDigits: 0 })} Mo libérés.`}
          </StatusLine>
        )}
        {cleaning.kind === "error" && (
          <StatusLine
            tone="error"
            action={!lock && <RetryAction onClick={() => void runClean()} />}
          >
            {cleaning.message}
          </StatusLine>
        )}
      </div>
    </Card>
  );
}

// ──────────────────────────────────────────────────────
//  Réinstallation
// ──────────────────────────────────────────────────────

export function ReinstallCard() {
  const lock = useMaintenanceLock("tout réinstaller");
  const [confirm, setConfirm] = useState(false);
  const [state, setState] = useState<"idle" | "working" | "done">("idle");
  const [error, setError] = useState<string | null>(null);

  async function runReinstall() {
    if (state === "working" || lock) return;
    setState("working");
    setError(null);
    try {
      await reinstallAll();
      setState("done");
      setConfirm(false);
    } catch (err) {
      setError(formatIpcError(err, "Réinstallation impossible"));
      setState("idle");
    }
  }

  return (
    <Card
      title="Réinstallation"
      description="Supprime les fichiers du jeu (mods, bibliothèques, versions, ressources) et relance un téléchargement complet au prochain lancement. Tes options, mondes et réglages Reborn sont conservés."
      icon={<Trash2 className="h-4 w-4" />}
      accent="danger"
    >
      <div className="px-6 py-5">
        {state === "done" ? (
          <StatusLine tone="success" className="mt-0">
            Fichiers supprimés : tout sera retéléchargé au prochain lancement.
          </StatusLine>
        ) : confirm ? (
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => void runReinstall()}
              disabled={state === "working" || lock !== null}
              className="inline-flex flex-1 items-center justify-center gap-2 rounded-md border border-danger/50 bg-danger/10 px-4 py-3 text-[13px] font-semibold text-danger transition hover:bg-danger/20 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {state === "working" ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Trash2 className="h-4 w-4" />
              )}
              Confirmer la réinstallation
            </button>
            <button
              type="button"
              onClick={() => {
                setConfirm(false);
                setError(null);
              }}
              disabled={state === "working"}
              className="rounded-md border border-border-strong bg-surface px-4 py-3 text-[13px] font-medium text-foreground-subtle transition hover:text-foreground disabled:opacity-60"
            >
              Annuler
            </button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setConfirm(true)}
            disabled={lock !== null}
            className="inline-flex w-full items-center justify-center gap-2 rounded-md border border-danger/40 bg-transparent px-4 py-3 text-[13px] font-medium text-danger transition hover:bg-danger/10 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:bg-transparent"
          >
            <Trash2 className="h-4 w-4" />
            Réinstaller tout
          </button>
        )}
        {lock && state !== "done" && <StatusLine tone="info">{lock}</StatusLine>}
        {error && <StatusLine tone="error">{error}</StatusLine>}
      </div>
    </Card>
  );
}
