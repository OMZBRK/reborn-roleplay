import { useEffect, useState } from "react";
import { AlertTriangle, ArrowRight, Download, RefreshCw, X } from "lucide-react";
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

// 4 sous-etats : available | downloading | installing | error.
// Comportement bloquant : on autorise la fermeture (croix, ESC, click-out)
// uniquement sur "available" et "error" — pas pendant download/install.
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

  // Bloquant pendant les phases reseau/install. L'utilisateur peut
  // "Plus tard" depuis "available" et "Ignorer" depuis "error".
  const dismissable = state.kind === "available";

  return (
    <Modal
      open
      onClose={dismissable ? onPostpone : undefined}
      variant={isError ? "danger" : "info"}
    >
      {/* Icone d'etat */}
      <div className="mb-4 flex justify-center">
        {isInstalling ? (
          <div className="reborn-update-spinner" aria-hidden />
        ) : isError ? (
          <div
            className="flex h-14 w-14 items-center justify-center rounded-full"
            style={{
              background: "var(--color-danger-soft)",
              color: "var(--color-danger)",
            }}
          >
            <AlertTriangle className="h-7 w-7" strokeWidth={2} />
          </div>
        ) : (
          <div
            className="flex h-14 w-14 items-center justify-center rounded-full"
            style={{
              background: "var(--color-accent-soft)",
              color: "var(--color-accent)",
            }}
          >
            <Download className="h-7 w-7" strokeWidth={2.5} />
          </div>
        )}
      </div>

      {/* Titre */}
      <h2 className="text-center font-display text-2xl tracking-wide">
        {state.kind === "available" && "Mise à jour disponible"}
        {isDownloading && "Téléchargement de la mise à jour"}
        {isInstalling && "Installation en cours…"}
        {isError && "Échec de la mise à jour"}
      </h2>

      {/* Sous-titre */}
      <p className="mt-2 text-center text-sm text-[var(--color-foreground-subtle)]">
        {state.kind === "available" && (
          <>
            Une nouvelle version de Reborn Launcher est prête à être installée.
            <br />
            Quelques améliorations t'attendent.
          </>
        )}
        {isDownloading && "Patience : la mise à jour s'installera dès la fin du téléchargement."}
        {isInstalling && (
          <>
            Le launcher va redémarrer automatiquement dans quelques instants.
            <br />
            Ne ferme pas Reborn.
          </>
        )}
        {isError && (
          <>
            Une erreur est survenue pendant la mise à jour.
            <br />
            Vérifie ta connexion ou attends la prochaine release.
          </>
        )}
      </p>

      {/* Carte details (idle) — ACTUELLE -> NOUVELLE side-by-side
          facon Zenkai. La fleche separe les 2 colonnes pour appuyer le
          "direction" de l'upgrade. */}
      {state.kind === "available" && (
        <div className="mt-5 grid grid-cols-[1fr_auto_1fr] items-stretch gap-0 overflow-hidden rounded-lg border"
          style={{
            background: "var(--color-surface-overlay)",
            borderColor: "var(--color-border)",
          }}
        >
          <div className="flex flex-col items-center justify-center px-3 py-3">
            <span className="text-[9px] font-semibold uppercase tracking-[0.12em] text-[var(--color-foreground-muted)]">
              Actuelle
            </span>
            <span className="mt-1 font-mono text-base font-semibold tabular-nums text-[var(--color-foreground-subtle)]">
              v{currentVersion ?? "—"}
            </span>
          </div>
          <div className="flex items-center justify-center px-1 text-[var(--color-foreground-muted)]">
            <ArrowRight className="h-4 w-4" />
          </div>
          <div className="flex flex-col items-center justify-center px-3 py-3"
            style={{ background: "var(--color-accent-soft)" }}
          >
            <span className="text-[9px] font-semibold uppercase tracking-[0.12em] text-[var(--color-accent)]">
              Nouvelle
            </span>
            <span className="mt-1 font-mono text-base font-semibold tabular-nums text-[var(--color-accent)]">
              v{state.update.version}
            </span>
          </div>
        </div>
      )}

      {/* Notes de version (separe de la carte ACTUELLE/NOUVELLE pour
          garder cette derniere visuellement legere comme Zenkai). */}
      {state.kind === "available" && state.update.body && (
        <div
          className="mt-3 rounded-md border p-3 text-xs"
          style={{
            background: "var(--color-surface-overlay)",
            borderColor: "var(--color-border)",
          }}
        >
          <div className="text-[10px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
            Notes de version
          </div>
          <p className="mt-1 line-clamp-5 whitespace-pre-line leading-relaxed text-[var(--color-foreground-subtle)]">
            {state.update.body}
          </p>
        </div>
      )}

      {/* Carte erreur */}
      {isError && (
        <div
          className="mt-5 rounded-md border p-3"
          style={{
            background: "var(--color-danger-soft)",
            borderColor: "var(--color-danger)",
          }}
        >
          <div className="flex gap-2 text-xs">
            <AlertTriangle className="h-4 w-4 flex-shrink-0 text-[var(--color-danger)]" />
            <div>
              <div className="font-semibold text-[var(--color-danger)]">
                Erreur du flow auto-update
              </div>
              <div className="mt-1 text-[var(--color-foreground-subtle)]">
                {state.message}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Barre de progression (downloading) */}
      {isDownloading && (
        <div className="mt-6">
          <div className="reborn-update-progress">
            <div
              className="reborn-update-progress-fill"
              style={{ width: `${Math.round(state.progress * 100)}%` }}
            />
          </div>
          <div className="mt-2 flex items-center justify-between text-xs text-[var(--color-foreground-subtle)]">
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
      <div className="mt-6 flex flex-col gap-2">
        {state.kind === "available" && (
          <>
            <button
              type="button"
              onClick={onInstall}
              className="flex h-10 items-center justify-center gap-2 rounded-md bg-[var(--color-accent)] font-medium text-white transition-colors hover:bg-[var(--color-accent-hover)]"
            >
              <Download className="h-4 w-4" />
              Installer maintenant
            </button>
            <button
              type="button"
              onClick={onPostpone}
              className="flex h-8 items-center justify-center gap-1 text-xs text-[var(--color-foreground-subtle)] transition-colors hover:text-[var(--color-foreground)]"
            >
              <X className="h-3 w-3" />
              Plus tard
            </button>
          </>
        )}

        {isInstalling && (
          <div className="flex items-center justify-center gap-2 text-xs text-[var(--color-foreground-subtle)]">
            <RefreshCw className="h-3 w-3 animate-spin" />
            Vérification · décompression · finalisation
          </div>
        )}

        {isError && (
          <>
            <button
              type="button"
              onClick={onInstall}
              className="flex h-10 items-center justify-center gap-2 rounded-md bg-[var(--color-accent)] font-medium text-white transition-colors hover:bg-[var(--color-accent-hover)]"
            >
              <RefreshCw className="h-4 w-4" />
              Réessayer
            </button>
            <button
              type="button"
              onClick={onIgnoreVersion}
              className="flex h-8 items-center justify-center text-xs text-[var(--color-foreground-subtle)] transition-colors hover:text-[var(--color-foreground)]"
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
