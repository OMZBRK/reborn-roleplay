import { useEffect, useState } from "react";
import { AlertTriangle, ArrowRight, Download, RefreshCw } from "lucide-react";
import { getVersion } from "@tauri-apps/api/app";
import { Modal } from "./Modal";
import type { UpdaterState } from "../../hooks/use-updater";
import { isTauri } from "../../lib/tauri";

type Props = {
  state: UpdaterState;
  onInstall: () => void;
  onPostpone: () => void;
  onIgnoreVersion: () => void;
};

// Modale auto-update style Reborn : card compacte (340px), titre a gauche +
// X discret a droite, carte ACTUELLE -> NOUVELLE side-by-side, CTA
// "Installer" full-width avec icone download. Comportement :
//   - available  : dismissable (Plus tard) + Installer
//   - downloading: bloquante, barre de progression + bytes
//   - installing : bloquante, message rassurant + spinner
//   - error      : Ignorer + Reessayer
//
// La pulse du logo sidebar reste alimentee par setIgnored(true) cote hook
// quand l'utilisateur clique "Plus tard".
export function UpdateModal({
  state,
  onInstall,
  onPostpone,
  onIgnoreVersion,
}: Props) {
  const currentVersion = useCurrentLauncherVersion();

  if (state.kind === "idle") return null;

  const isError = state.kind === "error";
  const isDownloading = state.kind === "downloading";
  const isInstalling = state.kind === "installing";
  const dismissable = state.kind === "available";

  const title =
    state.kind === "available"
      ? "Mise à jour disponible"
      : isDownloading
        ? "Téléchargement…"
        : isInstalling
          ? "Installation…"
          : "Échec de la mise à jour";

  return (
    <Modal
      open
      onClose={dismissable ? onPostpone : undefined}
      variant={isError ? "danger" : "info"}
      size="sm"
    >
      {/* Header : titre a gauche, X via Modal a droite (pt-1 pour aligner
          avec le bouton fermer). */}
      <h2 className="pr-7 pt-1 font-display text-[17px] font-semibold leading-tight tracking-wide">
        {title}
      </h2>

      {/* Banner sous le titre (variant selon etat) */}
      {state.kind === "available" && (
        <div
          className="mt-3 rounded-md border-l-2 px-3 py-2 text-[11.5px] leading-snug"
          style={{
            background: "var(--color-accent-soft)",
            borderColor: "var(--color-accent)",
            color: "var(--color-accent)",
          }}
        >
          Une nouvelle version est prête à être installée. Quelques
          améliorations t'attendent.
        </div>
      )}

      {isDownloading && (
        <p className="mt-3 text-[11.5px] leading-snug text-[var(--color-foreground-subtle)]">
          La mise à jour s'installera dès la fin du téléchargement.
        </p>
      )}

      {isInstalling && (
        <p className="mt-3 text-[11.5px] leading-snug text-[var(--color-foreground-subtle)]">
          Le launcher va redémarrer automatiquement. Ne ferme pas Reborn.
        </p>
      )}

      {isError && (
        <div
          className="mt-3 rounded-md border-l-2 px-3 py-2 text-[11.5px] leading-snug"
          style={{
            background: "var(--color-danger-soft)",
            borderColor: "var(--color-danger)",
            color: "var(--color-danger)",
          }}
        >
          <div className="flex items-start gap-2">
            <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
            <span>{state.message}</span>
          </div>
        </div>
      )}

      {/* Carte details (available) — ACTUELLE -> NOUVELLE side-by-side
          facon Reborn, ratio 1:auto:1. La fleche en colonne centrale
          appuie le sens de l'upgrade sans empieter sur les versions. */}
      {state.kind === "available" && (
        <div
          className="mt-4 grid grid-cols-[1fr_auto_1fr] items-stretch overflow-hidden rounded-md border"
          style={{
            background: "var(--color-surface-overlay)",
            borderColor: "var(--color-border)",
          }}
        >
          <div className="flex flex-col items-center px-3 py-2.5">
            <span className="text-[9px] font-semibold uppercase tracking-[0.14em] text-[var(--color-foreground-muted)]">
              Actuelle
            </span>
            <span className="mt-0.5 font-mono text-[15px] font-semibold tabular-nums text-[var(--color-foreground-subtle)]">
              v{currentVersion ?? "—"}
            </span>
          </div>
          <div className="flex items-center justify-center px-1 text-[var(--color-foreground-muted)]">
            <ArrowRight className="h-3.5 w-3.5" />
          </div>
          <div
            className="flex flex-col items-center px-3 py-2.5"
            style={{ background: "var(--color-accent-soft)" }}
          >
            <span className="text-[9px] font-semibold uppercase tracking-[0.14em] text-[var(--color-accent)]">
              Nouvelle
            </span>
            <span className="mt-0.5 font-mono text-[15px] font-semibold tabular-nums text-[var(--color-accent)]">
              v{state.update.version}
            </span>
          </div>
        </div>
      )}

      {/* Notes de version (collapse a 3 lignes pour ne pas faire deborder
          la card 340px). */}
      {state.kind === "available" && state.update.body && (
        <details className="mt-2.5">
          <summary className="cursor-pointer text-[10.5px] font-medium text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground-subtle)]">
            Notes de version
          </summary>
          <p className="mt-1.5 line-clamp-4 whitespace-pre-line text-[11px] leading-relaxed text-[var(--color-foreground-subtle)]">
            {state.update.body}
          </p>
        </details>
      )}

      {/* Barre de progression (downloading) */}
      {isDownloading && (
        <div className="mt-4">
          <div className="reborn-update-progress">
            <div
              className="reborn-update-progress-fill"
              style={{ width: `${Math.round(state.progress * 100)}%` }}
            />
          </div>
          <div className="mt-1.5 flex items-center justify-between text-[10.5px] text-[var(--color-foreground-subtle)]">
            <span className="font-medium">
              {Math.round(state.progress * 100)}%
            </span>
            {state.total > 0 && (
              <span className="font-mono tabular-nums">
                {formatBytes(state.downloaded)} / {formatBytes(state.total)}
              </span>
            )}
          </div>
        </div>
      )}

      {/* Actions */}
      <div className="mt-4 flex flex-col gap-1.5">
        {state.kind === "available" && (
          <>
            <button
              type="button"
              onClick={onInstall}
              className="flex h-10 items-center justify-center gap-2 rounded-md bg-[var(--color-accent)] text-[13px] font-semibold tracking-wide text-white shadow-sm transition-colors hover:bg-[var(--color-accent-hover)]"
            >
              <Download className="h-4 w-4" strokeWidth={2.4} />
              Installer
            </button>
            <button
              type="button"
              onClick={onPostpone}
              className="h-7 text-[11px] text-[var(--color-foreground-muted)] transition-colors hover:text-[var(--color-foreground)]"
            >
              Plus tard
            </button>
          </>
        )}

        {isInstalling && (
          <div className="flex items-center justify-center gap-2 py-2 text-[11px] text-[var(--color-foreground-subtle)]">
            <RefreshCw className="h-3 w-3 animate-spin" />
            Vérification · décompression · finalisation
          </div>
        )}

        {isError && (
          <>
            <button
              type="button"
              onClick={onInstall}
              className="flex h-10 items-center justify-center gap-2 rounded-md bg-[var(--color-accent)] text-[13px] font-semibold tracking-wide text-white shadow-sm transition-colors hover:bg-[var(--color-accent-hover)]"
            >
              <RefreshCw className="h-4 w-4" strokeWidth={2.4} />
              Réessayer
            </button>
            <button
              type="button"
              onClick={onIgnoreVersion}
              className="h-7 text-[11px] text-[var(--color-foreground-muted)] transition-colors hover:text-[var(--color-foreground)]"
            >
              Ignorer cette version
            </button>
          </>
        )}
      </div>
    </Modal>
  );
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// Hook utilitaire : recupere la version du launcher (Cargo.toml ->
// tauri.conf.json -> package.json, synchronisees au release). En mode
// browser (vite dev hors Tauri) renvoie null pour eviter le crash sur
// getVersion(). Le composant gere alors un placeholder "—".
function useCurrentLauncherVersion(): string | null {
  const [version, setVersion] = useState<string | null>(null);
  useEffect(() => {
    if (!isTauri) return;
    let cancelled = false;
    void getVersion().then((v) => {
      if (!cancelled) setVersion(v);
    });
    return () => {
      cancelled = true;
    };
  }, []);
  return version;
}
