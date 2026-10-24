import { Component, type ErrorInfo, type ReactNode } from "react";
import { AlertTriangle, Loader2, RefreshCw, RotateCcw } from "lucide-react";
import { formatIpcError } from "../../lib/errors";

// Coque des pages chargées à la demande (App.tsx + AuthenticatedLayout) :
//   - RouteFallback : indicateur sobre pendant le chargement d'un chunk de
//     page (invisible les 200 premières ms, cf .reborn-route-fallback) ;
//   - RouteErrorBoundary : une page qui plante (ou dont le chunk ne se
//     charge pas) affiche la cause réelle + quoi faire, au lieu de laisser
//     la fenêtre vide. La navigation (resetKey = pathname) la réarme.

export function RouteFallback() {
  return (
    <div className="reborn-route-fallback" role="status" aria-live="polite">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
      <span>Chargement de la page…</span>
    </div>
  );
}

// Chunk de page introuvable : fichier du launcher absent, verrouillé ou mis
// en quarantaine (antivirus), ou launcher mis à jour sous la page ouverte.
const CHUNK_ERROR_RE =
  /dynamically imported module|importing a module script failed|error loading dynamically imported module|failed to fetch dynamically/i;

/** Message affichable pour une erreur de rendu ou de chargement de page. */
export function describeRouteError(error: unknown): string {
  const raw = error instanceof Error ? error.message : String(error ?? "");
  if (CHUNK_ERROR_RE.test(raw)) {
    return "Une partie du launcher n'a pas pu être chargée (fichier manquant ou bloqué, souvent par l'antivirus). Recharge le launcher ; si ça persiste, réinstalle-le depuis le site Reborn.";
  }
  return formatIpcError(error, "Cette page a rencontré une erreur inattendue");
}

type BoundaryProps = {
  /** Change à chaque navigation : l'erreur est oubliée en changeant de page. */
  resetKey: string;
  children: ReactNode;
};

type BoundaryState = { failed: boolean; error: unknown };

export class RouteErrorBoundary extends Component<BoundaryProps, BoundaryState> {
  state: BoundaryState = { failed: false, error: null };

  static getDerivedStateFromError(error: unknown): BoundaryState {
    return { failed: true, error };
  }

  componentDidCatch(error: unknown, info: ErrorInfo) {
    console.error("[ui] page en erreur :", error, info.componentStack);
  }

  componentDidUpdate(prev: BoundaryProps) {
    if (prev.resetKey !== this.props.resetKey && this.state.failed) {
      this.setState({ failed: false, error: null });
    }
  }

  private retry = () => this.setState({ failed: false, error: null });

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="reborn-route-error">
        <div className="reborn-route-error-card" role="alert">
          <AlertTriangle className="h-6 w-6 text-[var(--color-warning)]" aria-hidden />
          <h1 className="reborn-route-error-title">Page indisponible</h1>
          <p className="reborn-route-error-message">{describeRouteError(this.state.error)}</p>
          <div className="reborn-route-error-actions">
            <button
              type="button"
              className="reborn-shots-head-btn reborn-shots-head-btn--primary"
              onClick={this.retry}
            >
              <RotateCcw className="h-3 w-3" aria-hidden />
              Réessayer
            </button>
            {/* Retour à la racine plutôt que reload() de l'URL profonde : la
                reprise de session refait tout le parcours de démarrage. */}
            <button
              type="button"
              className="reborn-shots-head-btn"
              onClick={() => window.location.replace("/")}
            >
              <RefreshCw className="h-3 w-3" aria-hidden />
              Recharger le launcher
            </button>
          </div>
        </div>
      </div>
    );
  }
}
