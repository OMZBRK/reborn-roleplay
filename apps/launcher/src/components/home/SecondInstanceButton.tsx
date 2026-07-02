import { useEffect, useState } from "react";
import { Loader2, Users } from "lucide-react";
import { useAuthStore } from "../../stores/auth-store";
import { launchSecondInstance } from "../../lib/launcher";

/** Grades considérés « staff » (au-dessus de WHITELISTED). Doit rester aligné
 *  avec is_staff_role() côté Rust (launcher/game.rs). */
const STAFF_ROLES = ["HELPER", "MODERATOR", "WHITELIST_REVIEWER", "ADMIN", "OWNER"];

/**
 * Bouton dev « Lancer une 2e instance » — visible uniquement pour le staff.
 * Ouvre un petit menu des comptes enregistrés (hors compte courant) ; cliquer
 * un compte lance une 2e fenêtre de jeu connectée avec ce compte (fire-and-
 * forget côté backend). Sert à tester à deux (double compte) sans changer les
 * paramètres serveur. Invisible pour les joueurs normaux.
 */
export function SecondInstanceButton() {
  const user = useAuthStore((s) => s.user);
  const savedAccounts = useAuthStore((s) => s.savedAccounts);
  const loadSavedAccounts = useAuthStore((s) => s.loadSavedAccounts);

  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Assure que la liste des comptes sauvegardés est chargée (le carousel du
  // login la peuple, mais on peut arriver ici sans être passé par là).
  useEffect(() => {
    if (savedAccounts.length === 0) void loadSavedAccounts();
  }, [savedAccounts.length, loadSavedAccounts]);

  const isStaff = !!user && STAFF_ROLES.includes(user.role);
  if (!isStaff) return null;

  // Comptes alternatifs = enregistrés, hors compte courant.
  const alts = savedAccounts.filter((a) => a.minecraftUuid !== user!.minecraftUuid);

  async function launch(uuid: string, pseudo: string) {
    setBusy(uuid);
    setError(null);
    setMsg(null);
    try {
      const r = await launchSecondInstance(uuid);
      setMsg(`2e instance lancée : ${pseudo} (pid ${r.pid})`);
      setOpen(false);
    } catch (err) {
      setError(
        typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur",
      );
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="flex flex-col items-center gap-2">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="inline-flex items-center gap-2 rounded-lg border border-border-strong bg-surface-elevated px-4 py-2 text-[11px] uppercase tracking-[0.14em] text-foreground-subtle transition-colors hover:border-accent/60 hover:text-white"
      >
        <Users className="h-3.5 w-3.5" />
        2e instance (dev)
      </button>

      {open && (
        <div className="flex w-64 flex-col gap-1 rounded-lg border border-border-strong bg-surface p-2">
          {alts.length === 0 ? (
            <div className="px-2 py-2 text-[11px] leading-relaxed text-foreground-muted">
              Aucun autre compte enregistré. Ajoute un 2e compte Microsoft via
              l'écran de connexion (carousel des comptes), puis reviens ici.
            </div>
          ) : (
            alts.map((a) => (
              <button
                key={a.minecraftUuid}
                type="button"
                disabled={busy !== null}
                onClick={() => launch(a.minecraftUuid, a.pseudo)}
                className="flex items-center justify-between rounded-md px-3 py-2 text-left text-sm text-foreground-subtle transition-colors hover:bg-surface-elevated hover:text-white disabled:opacity-50"
              >
                <span className="truncate">{a.pseudo}</span>
                {busy === a.minecraftUuid && (
                  <Loader2 className="h-3.5 w-3.5 flex-shrink-0 animate-spin" />
                )}
              </button>
            ))
          )}
        </div>
      )}

      {msg && <div className="text-[11px] text-success">{msg}</div>}
      {error && (
        <div className="max-w-xs truncate text-[11px] text-danger" title={error}>
          {error}
        </div>
      )}
    </div>
  );
}
