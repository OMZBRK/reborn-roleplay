import { Camera, FolderOpen } from "lucide-react";

type Props = {
  /** Variante "no-screenshots" (dossier vide) vs "no-match" (filtre exclut tout). */
  variant?: "fresh" | "no-match";
  onReset?: () => void;
};

// Etat vide de la page screenshots. Deux variantes :
//   - fresh    : pas de screenshot du tout (premier boot ou dossier vide)
//   - no-match : screenshots presents mais les filtres excluent tout
export function ScreenshotsEmpty({ variant = "fresh", onReset }: Props) {
  if (variant === "no-match") {
    return (
      <div className="reborn-shots-empty">
        <div className="reborn-shots-empty-icon">
          <Camera className="h-9 w-9" />
        </div>
        <div>
          <h2 className="font-display text-xl tracking-wide">Aucun résultat</h2>
          <p className="mt-2 text-sm text-[var(--color-foreground-subtle)]">
            Aucune capture ne correspond à tes filtres actuels.
          </p>
        </div>
        {onReset && (
          <button
            type="button"
            onClick={onReset}
            className="mt-2 flex items-center gap-1.5 rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-3 py-1.5 text-xs text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-surface-elevated)]"
          >
            Réinitialiser les filtres
          </button>
        )}
      </div>
    );
  }

  return (
    <div className="reborn-shots-empty">
      <div className="reborn-shots-empty-icon">
        <Camera className="h-9 w-9" />
      </div>
      <div>
        <h2 className="font-display text-xl tracking-wide">
          Aucune capture pour le moment
        </h2>
        <p className="mt-2 text-sm text-[var(--color-foreground-subtle)]">
          Tes screenshots in-game apparaîtront ici, organisés par serveur et par
          date. Pense à les épingler pour les retrouver plus vite.
        </p>
      </div>
      <div className="reborn-shots-empty-hint">
        <span>Appuie sur</span>
        <kbd>F2</kbd>
        <span>en jeu pour capturer</span>
      </div>
      <button
        type="button"
        className="mt-2 flex items-center gap-1.5 rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-3 py-1.5 text-xs text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-surface-elevated)]"
      >
        <FolderOpen className="h-3 w-3" />
        Ouvrir le dossier
      </button>
    </div>
  );
}
