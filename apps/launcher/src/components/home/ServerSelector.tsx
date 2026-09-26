import { useEffect } from "react";
import { Hammer, Server } from "lucide-react";
import { useAuthStore } from "../../stores/auth-store";
import { useServerStore } from "../../stores/server-store";

/** Grades « staff » — aligné avec is_staff_role() (Rust) et SecondInstanceButton. */
const STAFF_ROLES = ["HELPER", "MODELISATEUR", "DEVELOPPEUR", "MODERATOR", "WHITELIST_REVIEWER", "ADMIN", "OWNER"];

/**
 * Sélecteur de serveur (multi-version). Rangée de serveurs façon Shinoda : le
 * serveur choisi détermine la version Minecraft et le modpack.
 *
 * - "rp"    : Reborn RP (MC 26.2) — visible par tous.
 * - "build" : Serveur Build (MC 26.3) — visible uniquement pour le staff.
 *
 * Si un seul serveur est visible (joueur normal sans serveur build), on
 * n'affiche rien : pas de choix à faire.
 */
export function ServerSelector() {
  const user = useAuthStore((s) => s.user);
  const servers = useServerStore((s) => s.servers);
  const selectedId = useServerStore((s) => s.selectedId);
  const select = useServerStore((s) => s.select);
  const load = useServerStore((s) => s.load);

  useEffect(() => {
    void load();
  }, [load]);

  const isStaff = !!user && STAFF_ROLES.includes(user.role);
  const visible = servers.filter((s) => !s.staffOnly || isStaff);

  // Un seul serveur → pas de sélecteur.
  if (visible.length <= 1) return null;

  return (
    <div className="flex items-center gap-2">
      {visible.map((s) => {
        const active = s.id === selectedId;
        const isBuild = s.id === "build";
        return (
          <button
            key={s.id}
            type="button"
            onClick={() => select(s.id)}
            className="group flex items-center gap-2.5 rounded-xl border px-4 py-2.5 text-left transition-all duration-150"
            style={{
              minWidth: 170,
              background: active
                ? "linear-gradient(180deg, rgba(160,24,43,0.22) 0%, rgba(160,24,43,0.10) 100%)"
                : "var(--color-surface-elevated)",
              borderColor: active ? "var(--color-accent)" : "var(--color-border-strong)",
              boxShadow: active ? "0 0 18px rgba(160,24,43,0.28)" : "none",
            }}
          >
            <span
              className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg"
              style={{
                background: active ? "var(--color-accent)" : "rgba(255,255,255,0.05)",
                color: active ? "#fff" : "var(--color-foreground-muted)",
              }}
            >
              {isBuild ? <Hammer className="h-4 w-4" strokeWidth={2.2} /> : <Server className="h-4 w-4" strokeWidth={2.2} />}
            </span>
            <span className="flex flex-col leading-tight">
              <span
                className="font-display text-[15px] tracking-wide"
                style={{ color: active ? "#fff" : "var(--color-foreground)" }}
              >
                {s.name}
              </span>
              <span className="text-[10px] uppercase tracking-[0.16em] text-foreground-muted">
                MC {s.mcVersion}
                {s.staffOnly ? " · staff" : ""}
              </span>
            </span>
          </button>
        );
      })}
    </div>
  );
}
