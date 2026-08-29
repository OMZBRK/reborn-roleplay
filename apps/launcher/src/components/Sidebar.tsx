import { useMemo, useState, type ComponentType } from "react";
import { useLocation, useNavigate } from "react-router";
import { AnimatePresence } from "framer-motion";
import {
  Book,
  BookOpen,
  ChevronRight,
  Drama,
  FileQuestion,
  FileText,
  LifeBuoy,
  Home,
  Package,
  ShoppingBag,
  Sparkles,
} from "lucide-react";
import { useAuthStore } from "../stores/auth-store";
import { useWhitelistStore } from "../stores/whitelist-store";
import { useUpdateStore } from "../stores/update-store";
import { useBadgesStore } from "../stores/badges-store";
import { UserMenuPopover } from "./shell/UserMenuPopover";
import { mapRole, ROLE_META } from "./sidebar/role";

// Rail de navigation Reborn (refonte v4) : rail labellisé de 232px, groupé
// par sections (Principal / Contenu / Support), avec un lockup de marque en
// haut (mark R + wordmark) et une carte utilisateur en bas qui ouvre le
// popover. Remplace le rail icône-only 72px de la v3 — jugé illisible et mal
// hiérarchisé. Chaque item porte icône + label + badge optionnel, et un état
// actif franc (pill crimson + liseré + glow).

type SidebarItem = {
  id: string;
  label: string;
  route: string;
  icon: ComponentType<{ size?: number; className?: string }>;
  badge?: string;
  dotBadge?: boolean;
};

type SidebarGroup = {
  id: string;
  label: string;
  items: SidebarItem[];
};

export function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const whitelistStatus = useWhitelistStore((s) => s.status);
  const updateAvailable = useUpdateStore((s) => s.available);
  const unreadTickets = useBadgesStore((s) => s.badges.unreadTickets);
  const unreadPatchnotes = useBadgesStore((s) => s.badges.unreadPatchnotes);
  const [menuOpen, setMenuOpen] = useState(false);

  const groups = useMemo<SidebarGroup[]>(() => {
    return [
      {
        id: "principal",
        label: "Principal",
        items: [
          { id: "home", label: "Accueil", route: "/home", icon: Home },
          { id: "shop", label: "Boutique", route: "/shop", icon: ShoppingBag },
          {
            id: "whitelist",
            label: "Whitelist",
            route: "/whitelist",
            icon: FileText,
            dotBadge: whitelistStatus === "pending",
          },
          { id: "rp", label: "RP", route: "/rp", icon: Drama },
        ],
      },
      {
        id: "contenu",
        label: "Contenu",
        items: [
          { id: "mods", label: "Mods", route: "/mods", icon: Package },
          { id: "rules", label: "Règlement", route: "/rules", icon: Book },
          { id: "lore", label: "Lore", route: "/lore", icon: BookOpen },
          {
            id: "patchnotes",
            label: "Patch Notes",
            route: "/patchnotes",
            icon: Sparkles,
            dotBadge: unreadPatchnotes > 0,
          },
          { id: "docs", label: "Documentation", route: "/docs", icon: FileQuestion },
        ],
      },
      {
        id: "support",
        label: "Support",
        items: [
          {
            id: "tickets",
            label: "Tickets",
            route: "/tickets",
            icon: LifeBuoy,
            badge: unreadTickets > 0 ? String(Math.min(unreadTickets, 9)) : undefined,
          },
        ],
      },
    ];
  }, [whitelistStatus, unreadTickets, unreadPatchnotes]);

  // Item actif via le pathname. Match sur le prefix pour que /rules/<slug>
  // reste actif sur l'item Règlement.
  const activeId = useMemo(() => {
    const path = location.pathname;
    const all = groups.flatMap((g) => g.items);
    const match = all.find(
      (it) => path === it.route || path.startsWith(`${it.route}/`),
    );
    return match?.id ?? null;
  }, [groups, location.pathname]);

  const pseudo = user?.displayName ?? user?.minecraftUsername ?? "Joueur";
  const initial = pseudo.charAt(0).toUpperCase();
  const roleMeta = ROLE_META[mapRole(user?.role)];

  return (
    <aside className="reborn-rail">
      <button
        type="button"
        onClick={() => navigate("/home")}
        title={
          updateAvailable
            ? "Mise à jour disponible — clic pour ouvrir l'accueil"
            : "Accueil"
        }
        aria-label="Accueil"
        className="reborn-rail-brand"
      >
        <span
          className={[
            "reborn-rail-mark",
            updateAvailable && "reborn-rail-mark--pulse",
          ]
            .filter(Boolean)
            .join(" ")}
        >
          R
        </span>
        <span className="reborn-rail-brand-text">
          <span className="reborn-rail-wordmark">REBORN</span>
          <span className="reborn-rail-tagline">ROLEPLAY</span>
        </span>
      </button>

      <nav className="reborn-rail-nav" aria-label="Navigation principale">
        {groups.map((group) => (
          <div key={group.id} className="reborn-rail-group">
            <span className="reborn-rail-group-label">{group.label}</span>
            {group.items.map((it) => {
              const Icon = it.icon;
              const active = activeId === it.id;
              return (
                <button
                  key={it.id}
                  type="button"
                  onClick={() => navigate(it.route)}
                  data-active={active ? "true" : undefined}
                  className="reborn-rail-item"
                >
                  <span className="reborn-rail-item-icon">
                    <Icon size={18} />
                  </span>
                  <span className="reborn-rail-item-label">{it.label}</span>
                  {it.badge ? (
                    <span className="reborn-rail-item-badge">{it.badge}</span>
                  ) : it.dotBadge ? (
                    <span className="reborn-rail-item-dot" />
                  ) : (
                    active && <ChevronRight size={14} className="reborn-rail-item-chev" />
                  )}
                </button>
              );
            })}
          </div>
        ))}
      </nav>

      <div className="reborn-rail-foot">
        <button
          type="button"
          onClick={() => setMenuOpen((v) => !v)}
          aria-label="Menu utilisateur"
          aria-expanded={menuOpen}
          data-open={menuOpen ? "true" : undefined}
          className="reborn-rail-user"
        >
          <span className="reborn-rail-user-avatar">
            {initial}
            <span className="reborn-rail-user-status" />
          </span>
          <span className="reborn-rail-user-meta">
            <span className="reborn-rail-user-name">{pseudo}</span>
            <span className="reborn-rail-user-role" style={{ color: roleMeta.color }}>
              {roleMeta.label}
            </span>
          </span>
        </button>
        <AnimatePresence>
          {menuOpen && <UserMenuPopover onClose={() => setMenuOpen(false)} />}
        </AnimatePresence>
      </div>
    </aside>
  );
}
