import { AlertTriangle, Camera, FolderOpen, RefreshCw } from "lucide-react";

type Props = {
  /** fresh = dossier vide, no-match = filtres qui excluent tout, error = lecture impossible. */
  variant?: "fresh" | "no-match" | "error";
  /** Cause réelle de l'échec (variante error). */
  message?: string;
  onReset?: () => void;
  onRetry?: () => void;
  onOpenFolder?: () => void;
};

const BTN =
  "mt-2 flex items-center gap-1.5 rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-3 py-1.5 text-xs text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-surface-elevated)]";

// États vides / d'erreur de la page Screenshots :
//   - fresh    : aucune capture (premier boot ou dossier vide)
//   - no-match : des captures, mais les filtres excluent tout
//   - error    : le dossier n'a pas pu être lu (cause réelle + Réessayer)
export function ScreenshotsEmpty({ variant = "fresh", message, onReset, onRetry, onOpenFolder }: Props) {
  if (variant === "error") {
    return (
      <div className="reborn-shots-empty" role="alert">
        <div className="reborn-shots-empty-icon">
          <AlertTriangle className="h-9 w-9" aria-hidden />
        </div>
        <div>
          <h2 className="font-display text-xl tracking-wide">Captures illisibles</h2>
          <p className="mx-auto mt-2 max-w-md text-sm text-[var(--color-foreground-subtle)]">
            {message ?? "Le dossier des captures n'a pas pu être lu."}
          </p>
        </div>
        <div className="flex gap-2">
          {onRetry && (
            <button type="button" onClick={onRetry} className={BTN}>
              <RefreshCw className="h-3 w-3" aria-hidden />
              Réessayer
            </button>
          )}
          {onOpenFolder && (
            <button type="button" onClick={onOpenFolder} className={BTN}>
              <FolderOpen className="h-3 w-3" aria-hidden />
              Ouvrir le dossier
            </button>
          )}
        </div>
      </div>
    );
  }

  if (variant === "no-match") {
    return (
      <div className="reborn-shots-empty">
        <div className="reborn-shots-empty-icon">
          <Camera className="h-9 w-9" aria-hidden />
        </div>
        <div>
          <h2 className="font-display text-xl tracking-wide">Aucun résultat</h2>
          <p className="mt-2 text-sm text-[var(--color-foreground-subtle)]">
            Aucune capture ne correspond à tes filtres actuels.
          </p>
        </div>
        {onReset && (
          <button type="button" onClick={onReset} className={BTN}>
            Réinitialiser les filtres
          </button>
        )}
      </div>
    );
  }

  return (
    <div className="reborn-shots-empty">
      <div className="reborn-shots-empty-icon">
        <Camera className="h-9 w-9" aria-hidden />
      </div>
      <div>
        <h2 className="font-display text-xl tracking-wide">Aucune capture pour le moment</h2>
        <p className="mt-2 text-sm text-[var(--color-foreground-subtle)]">
          Tes screenshots in-game apparaîtront ici, triés par date. Mets-les en favori pour les
          retrouver plus vite.
        </p>
      </div>
      <div className="reborn-shots-empty-hint">
        <span>Appuie sur</span>
        <kbd>F2</kbd>
        <span>en jeu pour capturer</span>
      </div>
      {onOpenFolder && (
        <button type="button" onClick={onOpenFolder} className={BTN}>
          <FolderOpen className="h-3 w-3" aria-hidden />
          Ouvrir le dossier
        </button>
      )}
    </div>
  );
}
