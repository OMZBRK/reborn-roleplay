import { useState } from "react";
import { Loader2, Hammer } from "lucide-react";
import { useAuthStore } from "../../stores/auth-store";
import { launchBuilder } from "../../lib/launcher";

/** Grades « staff » — aligné avec is_staff_role() côté Rust (launcher/game.rs). */
const STAFF_ROLES = ["HELPER", "MODELISATEUR", "DEVELOPPEUR", "MODERATOR", "WHITELIST_REVIEWER", "ADMIN", "OWNER"];

/**
 * Bouton « Builder » (staff-only) : lance MC 26.2 + Axiom / opti / shaders (sans
 * mods RP) dans un dossier de jeu séparé, connecté au serveur build. Le backend
 * gate déjà sur le rôle ; ici on masque juste le bouton aux joueurs normaux.
 */
export function BuilderButton() {
  const user = useAuthStore((s) => s.user);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isStaff = !!user && STAFF_ROLES.includes(user.role);
  if (!isStaff) return null;

  async function launch() {
    setBusy(true);
    setError(null);
    try {
      await launchBuilder();
    } catch (err) {
      setError(
        typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col items-center gap-2">
      <button
        type="button"
        disabled={busy}
        onClick={() => void launch()}
        className="inline-flex items-center gap-2 rounded-lg border border-border-strong bg-surface-elevated px-4 py-2 text-[11px] uppercase tracking-[0.14em] text-foreground-subtle transition-colors hover:border-accent/60 hover:text-white disabled:opacity-50"
      >
        {busy ? (
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
        ) : (
          <Hammer className="h-3.5 w-3.5" />
        )}
        Builder (26.2)
      </button>
      {error && (
        <div className="max-w-xs truncate text-[11px] text-danger" title={error}>
          {error}
        </div>
      )}
    </div>
  );
}
