import { NavLink } from "react-router";
import { Bell, Coins, Settings as SettingsIcon } from "lucide-react";
import { useAuthStore } from "../../stores/auth-store";
import { RoleBadge } from "./RoleBadge";
import { mapRole } from "./role";

export function UserBlock() {
  const user = useAuthStore((s) => s.user);
  const pseudo = user?.displayName ?? user?.minecraftUsername ?? "Joueur";
  const initial = pseudo.charAt(0).toUpperCase();
  const roleType = mapRole(user?.role);

  // TODO: brancher notifications + coins quand les endpoints existeront
  const notifications = 0;
  const coins = 0;

  return (
    <div>
      <div className="flex items-center gap-3 border-b border-border px-5 pb-4 pt-5">
        <div className="relative shrink-0">
          <div
            className="flex h-11 w-11 items-center justify-center rounded-full text-base font-semibold text-white"
            style={{
              background:
                "linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-pressed) 100%)",
              border: "2px solid var(--color-surface)",
              boxShadow:
                "0 0 0 1.5px var(--color-accent), 0 4px 12px -2px rgba(59, 91, 219, 0.4)",
            }}
          >
            {initial}
          </div>
          <span
            className="reborn-online-dot absolute"
            style={{
              right: -2,
              bottom: -2,
              width: 12,
              height: 12,
              borderRadius: "var(--radius-full)",
              background: "var(--color-success)",
              border: "2px solid var(--color-surface)",
            }}
          />
        </div>

        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold leading-tight tracking-wide text-white">
            {pseudo}
          </div>
          <div className="mt-1">
            <RoleBadge role={roleType} />
          </div>
        </div>
      </div>

      <div className="flex items-center gap-4 px-5 py-3 text-foreground-subtle">
        <button
          type="button"
          className="relative transition-colors hover:text-white"
          aria-label="Notifications"
        >
          <Bell className="h-4 w-4" />
          {notifications > 0 && (
            <span
              className="absolute -right-1.5 -top-1 inline-flex items-center justify-center rounded-full text-[9px] font-bold leading-none text-white"
              style={{
                minWidth: 14,
                height: 14,
                padding: "0 4px",
                background: "var(--color-danger)",
                border: "1.5px solid var(--color-surface)",
              }}
            >
              {notifications}
            </span>
          )}
        </button>
        <NavLink
          to="/settings"
          className="transition-colors hover:text-white"
          aria-label="Paramètres"
        >
          <SettingsIcon className="h-4 w-4" />
        </NavLink>
        <div
          className="ml-auto flex items-center gap-1.5 rounded-md px-2 py-1 text-xs"
          style={{
            background: "rgba(245, 158, 11, 0.06)",
            border: "1px solid rgba(245, 158, 11, 0.15)",
          }}
        >
          <Coins className="h-3.5 w-3.5" style={{ color: "var(--color-warning)" }} />
          <span
            className="font-mono font-medium tabular-nums"
            style={{ color: "var(--color-foreground)" }}
          >
            {coins}
          </span>
        </div>
      </div>
    </div>
  );
}
