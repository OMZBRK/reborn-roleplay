import { useEffect, useMemo, useRef, useState, type ComponentType } from "react";
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
  UsersRound,
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
  /** Texte lu par les lecteurs d'écran pour le badge (« 3 non lus »). */
  badgeLabel?: string;
  dotBadge?: boolean;
  /** Texte lu pour la pastille (« candidature en attente »). */
  dotLabel?: string;
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
  const requestUpdateOpen = useUpdateStore((s) => s.requestOpen);
  // `?? 0` : un backend absent (vite dev navigateur) ou une réponse vide ne
  // doit pas faire tomber la sidebar.
  const unreadTickets = useBadgesStore((s) => s.badges?.unreadTickets ?? 0);
  const unreadPatchnotes = useBadgesStore((s) => s.badges?.unreadPatchnotes ?? 0);
  const [menuOpen, setMenuOpen] = useState(false);
  const userBtnRef = useRef<HTMLButtonElement>(null);

  // Échap ferme le menu utilisateur et rend le focus à la carte.
  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      setMenuOpen(false);
      userBtnRef.current?.focus();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [menuOpen]);

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
            dotLabel: "candidature en attente",
          },
          // « Mes personnages » (CANDIDATURE_ET_DOUANE §4.6).
          { id: "personnages", label: "Personnages", route: "/personnages", icon: UsersRound },
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
            dotLabel: "nouveautés non lues",
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
            badge: unreadTickets > 0 ? (unreadTickets > 9 ? "9+" : String(unreadTickets)) : undefined,
            badgeLabel:
              unreadTickets > 0
                ? `${unreadTickets} réponse${unreadTickets > 1 ? "s" : ""} non lue${unreadTickets > 1 ? "s" : ""}`
                : undefined,
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
        onClick={() => {
          navigate("/home");
          if (updateAvailable) requestUpdateOpen();
        }}
        title={
          updateAvailable
            ? "Mise à jour disponible — clic pour l'installer"
            : "Accueil"
        }
        aria-label={updateAvailable ? "Accueil — mise à jour du launcher disponible" : "Accueil"}
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
                  aria-current={active ? "page" : undefined}
                  className="reborn-rail-item"
                >
                  <span className="reborn-rail-item-icon" aria-hidden>
                    <Icon size={18} />
                  </span>
                  <span className="reborn-rail-item-label">{it.label}</span>
                  {it.badge ? (
                    <span className="reborn-rail-item-badge">
                      <span aria-hidden>{it.badge}</span>
                      {it.badgeLabel && <span className="reborn-sr-only">, {it.badgeLabel}</span>}
                    </span>
                  ) : it.dotBadge ? (
                    <span className="reborn-rail-item-dot">
                      {it.dotLabel && <span className="reborn-sr-only">, {it.dotLabel}</span>}
                    </span>
                  ) : (
                    active && (
                      <ChevronRight size={14} className="reborn-rail-item-chev" aria-hidden />
                    )
                  )}
                </button>
              );
            })}
          </div>
        ))}
      </nav>

      <div className="reborn-rail-foot">
        <button
          ref={userBtnRef}
          type="button"
          // Menu ouvert : le clic sur la carte doit le FERMER. Sans ça, le
          // mousedown « hors popover » le fermait puis le clic le rouvrait.
          onMouseDown={(e) => {
            if (menuOpen) e.stopPropagation();
          }}
          onClick={() => setMenuOpen((v) => !v)}
          aria-label={`Menu utilisateur de ${pseudo}`}
          aria-haspopup="true"
          aria-expanded={menuOpen}
          data-open={menuOpen ? "true" : undefined}
          className="reborn-rail-user"
        >
          <span className="reborn-rail-user-avatar" aria-hidden>
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
