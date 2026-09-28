import { useEffect, useRef, useState } from "react";
import { AlertTriangle, ArrowRight, Download, RefreshCw } from "lucide-react";
import { getVersion } from "@tauri-apps/api/app";
import type { Update } from "@tauri-apps/plugin-updater";
import { Modal } from "./Modal";
import type { UpdaterState } from "../../hooks/use-updater";
import { isTauri } from "../../lib/tauri";

type Props = {
  state: UpdaterState;
  onInstall: () => void;
  onPostpone: () => void;
  onIgnoreVersion: () => void;
};

// h-10 (40) + gap-1.5 (6) + h-7 (28) : bouton principal + lien secondaire.
const ACTION_ZONE_H = 74;

// Modale auto-update style Reborn : card compacte (340px), titre a gauche +
// X discret a droite, carte ACTUELLE -> NOUVELLE side-by-side.
//
// Gabarit FIXE sur tous les etats : la card ne change pas de taille au
// passage available -> downloading -> installing (ni sur erreur). Trois zones
// toujours rendues, a hauteur constante — seul leur contenu change :
//   1. bandeau de message (2 lignes reservees)
//   2. carte des versions + ligne "Notes de version"
//   3. zone d'action (ACTION_ZONE_H) : boutons / barre + octets / spinner
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
  // L'etat "error" ne porte plus l'update : on garde la derniere vue pour que
  // la carte des versions reste en place.
  const lastUpdate = useRef<Update | null>(null);
  if (state.kind !== "idle" && state.kind !== "error") {
    lastUpdate.current = state.update;
  }

  if (state.kind === "idle") return null;

  const isError = state.kind === "error";
  const isDownloading = state.kind === "downloading";
  const isInstalling = state.kind === "installing";
  const dismissable = state.kind === "available";
  const update = lastUpdate.current;

  const title =
    state.kind === "available"
      ? "Mise à jour disponible"
      : isDownloading
        ? "Téléchargement…"
        : isInstalling
          ? "Installation…"
          : "Échec de la mise à jour";

  const bannerText = isError
    ? state.message
    : isDownloading
      ? "La mise à jour s'installera dès la fin du téléchargement."
      : isInstalling
        ? "Le launcher va redémarrer automatiquement. Ne ferme pas Reborn."
        : "Une nouvelle version est prête à être installée. Quelques améliorations t'attendent.";

  const bannerStyle = isError
    ? {
        background: "var(--color-danger-soft)",
        borderColor: "var(--color-danger)",
        color: "var(--color-danger)",
      }
    : state.kind === "available"
      ? {
          background: "var(--color-accent-soft)",
          borderColor: "var(--color-accent)",
          color: "var(--color-accent)",
        }
      : {
          background: "var(--color-surface-overlay)",
          borderColor: "var(--color-border)",
          color: "var(--color-foreground-subtle)",
        };

  const percent = isDownloading
    ? Math.round(state.progress * 100)
    : isInstalling
      ? 100
      : 0;

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

      {/* 1. Bandeau — 2 lignes reservees quel que soit le texte. */}
      <div
        className="mt-3 flex h-[46px] items-start gap-2 rounded-md border-l-2 px-3 py-2 text-[11.5px] leading-snug transition-colors"
        style={bannerStyle}
      >
        {isError && <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />}
        <span className="line-clamp-2" title={bannerText}>
          {bannerText}
        </span>
      </div>

      {/* 2. Carte versions — ACTUELLE -> NOUVELLE, ratio 1:auto:1. La fleche
          en colonne centrale appuie le sens de l'upgrade. */}
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
            v{update?.version ?? "—"}
          </span>
        </div>
      </div>

      {/* Notes de version : la ligne reste en place dans tous les etats ; le
          depliage n'arrive que sur action de l'utilisateur. */}
      <div className="mt-2.5 min-h-[16px]">
        {update?.body && (
          <details>
            <summary className="cursor-pointer text-[10.5px] font-medium text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground-subtle)]">
              Notes de version
            </summary>
            <p className="mt-1.5 line-clamp-4 whitespace-pre-line text-[11px] leading-relaxed text-[var(--color-foreground-subtle)]">
              {update.body}
            </p>
          </details>
        )}
      </div>

      {/* 3. Zone d'action — hauteur fixe. */}
      <div
        className="mt-4 flex flex-col gap-1.5"
        style={{ height: ACTION_ZONE_H }}
      >
        {(state.kind === "available" || isError) && (
          <>
            <button
              type="button"
              onClick={onInstall}
              className="flex h-10 flex-shrink-0 items-center justify-center gap-2 rounded-md bg-[var(--color-accent)] text-[13px] font-semibold tracking-wide text-white shadow-sm transition-colors hover:bg-[var(--color-accent-hover)]"
            >
              {isError ? (
                <RefreshCw className="h-4 w-4" strokeWidth={2.4} />
              ) : (
                <Download className="h-4 w-4" strokeWidth={2.4} />
              )}
              {isError ? "Réessayer" : "Installer"}
            </button>
            <button
              type="button"
              onClick={isError ? onIgnoreVersion : onPostpone}
              className="h-7 flex-shrink-0 text-[11px] text-[var(--color-foreground-muted)] transition-colors hover:text-[var(--color-foreground)]"
            >
              {isError ? "Ignorer cette version" : "Plus tard"}
            </button>
          </>
        )}

        {(isDownloading || isInstalling) && (
          <>
            {/* La barre prend la place du bouton principal (h-10). */}
            <div className="flex h-10 flex-shrink-0 flex-col justify-center">
              <div className="reborn-update-progress">
                <div
                  className="reborn-update-progress-fill"
                  style={{ width: `${percent}%` }}
                />
              </div>
            </div>
            <div className="flex h-7 flex-shrink-0 items-center justify-between text-[10.5px] text-[var(--color-foreground-subtle)]">
              {isDownloading ? (
                <>
                  <span className="font-medium tabular-nums">{percent}%</span>
                  {state.total > 0 && (
                    <span className="font-mono tabular-nums">
                      {formatBytes(state.downloaded)} / {formatBytes(state.total)}
                    </span>
                  )}
                </>
              ) : (
                <span className="flex w-full items-center justify-center gap-2">
                  <RefreshCw className="h-3 w-3 animate-spin" />
                  Vérification · décompression · finalisation
                </span>
              )}
            </div>
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
