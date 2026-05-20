import { RefreshCw, WifiOff } from "lucide-react";

type Props = {
  label?: string;
  onRetry?: () => void;
};

// Bloc generique a placer dans les zones de contenu qui ne peuvent pas
// charger (ex: liste des tickets quand l'API ne repond pas). Volontairement
// sobre — l'OfflineBanner global suffit pour l'alerte globale, ce bloc-ci
// donne juste une explication contextuelle a l'endroit du contenu manquant.
export function OfflineFallback({
  label = "Impossible de charger ce contenu",
  onRetry,
}: Props) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-md border border-dashed border-[var(--color-border-strong)] bg-[var(--color-surface)] px-6 py-10 text-center">
      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--color-surface-elevated)] text-[var(--color-foreground-subtle)]">
        <WifiOff className="h-5 w-5" />
      </div>
      <p className="text-sm text-[var(--color-foreground-subtle)]">{label}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="flex items-center gap-1.5 rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-3 py-1.5 text-xs text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-surface-elevated)]"
        >
          <RefreshCw className="h-3 w-3" />
          Réessayer
        </button>
      )}
    </div>
  );
}
