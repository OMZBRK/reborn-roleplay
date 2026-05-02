import { NavLink, useNavigate } from "react-router";
import {
  Bell,
  BookOpen,
  Coins,
  FileText,
  Gavel,
  Home,
  LifeBuoy,
  LogOut,
  Newspaper,
  Settings,
  ShoppingBag,
  Swords,
} from "lucide-react";
import { useAuthStore } from "../stores/auth-store";
import { cn } from "../lib/cn";
import { logout } from "../lib/auth";

const navItems = [
  { to: "/home", label: "Accueil", icon: Home },
  { to: "/shop", label: "Boutique", icon: ShoppingBag },
  { to: "/whitelist", label: "Whitelist", icon: Swords },
  { to: "/rules", label: "Reglement", icon: Gavel },
  { to: "/lore", label: "Lore", icon: BookOpen },
  { to: "/patchnotes", label: "Patch Notes", icon: Newspaper },
  { to: "/tickets", label: "Tickets", icon: LifeBuoy },
  { to: "/docs", label: "Documentation", icon: FileText },
];

export function Sidebar() {
  const user = useAuthStore((s) => s.user);
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();

  async function handleLogout() {
    try {
      await logout();
    } finally {
      setSession(null);
      navigate("/login", { replace: true });
    }
  }

  return (
    <aside className="flex h-full w-[260px] flex-col border-r border-border bg-surface">
      {/* Bloc utilisateur */}
      <div className="border-b border-border p-4">
        <div className="flex items-center gap-3">
          <div className="relative h-10 w-10 overflow-hidden rounded-md bg-accent">
            <span className="flex h-full items-center justify-center font-display font-semibold text-white">
              {(user?.displayName ?? user?.minecraftUsername ?? "?")
                .charAt(0)
                .toUpperCase()}
            </span>
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">
              {user?.displayName ?? user?.minecraftUsername ?? "Joueur"}
            </p>
            <p className="text-xs uppercase tracking-wider text-foreground-subtle">
              {user?.role ?? "PLAYER"}
            </p>
          </div>
        </div>

        <div className="mt-4 flex items-center justify-between">
          <button
            type="button"
            className="flex h-8 w-8 items-center justify-center rounded-md text-foreground-subtle hover:bg-surface-elevated hover:text-foreground"
            aria-label="Notifications"
          >
            <Bell className="h-4 w-4" />
          </button>
          <button
            type="button"
            className="flex h-8 w-8 items-center justify-center rounded-md text-foreground-subtle hover:bg-surface-elevated hover:text-foreground"
            aria-label="Parametres"
          >
            <Settings className="h-4 w-4" />
          </button>
          <div className="flex items-center gap-1.5 rounded-md bg-surface-elevated px-2.5 py-1 text-xs">
            <Coins className="h-3.5 w-3.5 text-warning" />
            <span className="font-medium">0</span>
          </div>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto px-2 py-4">
        <ul className="space-y-1">
          {navItems.map(({ to, label, icon: Icon }) => (
            <li key={to}>
              <NavLink
                to={to}
                className={({ isActive }) =>
                  cn(
                    "flex h-10 items-center gap-3 rounded-md px-3 text-sm font-medium transition",
                    isActive
                      ? "bg-accent/15 text-foreground"
                      : "text-foreground-subtle hover:bg-surface-elevated hover:text-foreground",
                  )
                }
              >
                <Icon className="h-4 w-4" />
                <span>{label}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      {/* Footer : statut serveur + logout */}
      <div className="border-t border-border p-4">
        <div className="flex items-center justify-between text-xs">
          <div className="flex items-center gap-2">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-success opacity-75" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-success" />
            </span>
            <span className="font-medium">Serveur en ligne</span>
          </div>
          <span className="text-foreground-subtle">23 / 200</span>
        </div>
        <div className="mt-1 flex items-center justify-between text-[11px] text-foreground-subtle">
          <span>Ping : 28 ms</span>
          <span>play.reborn-rp.fr</span>
        </div>
        <button
          type="button"
          onClick={handleLogout}
          className="mt-3 flex w-full items-center justify-center gap-2 rounded-md border border-border py-1.5 text-xs text-foreground-subtle transition hover:border-danger/50 hover:text-danger"
        >
          <LogOut className="h-3.5 w-3.5" />
          Se deconnecter
        </button>
      </div>
    </aside>
  );
}
