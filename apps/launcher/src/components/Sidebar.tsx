import { useMemo, useState, type ComponentType } from "react";
import { useLocation, useNavigate } from "react-router";
import { AnimatePresence } from "framer-motion";
import {
  Book,
  BookOpen,
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
import { SidebarIconButton } from "./shell/SidebarIconButton";
import { UserMenuPopover } from "./shell/UserMenuPopover";

// Sidebar fine 72px : logo R (cliquable → /home, pulse si update dispo),
// nav verticale, spacer, avatar utilisateur en bas qui ouvre un popover.
// La structure des items vient des consignes de la maquette — un seul
// regroupement contigu (plus de sections Principal / Communaute / Contenu
// de la v1, l'iconographie + tooltip suffisent a la navigation).

type SidebarItem = {
  id: string;
  label: string;
  route: string;
  icon: ComponentType<{ size?: number; className?: string }>;
  badge?: string;
  dotBadge?: boolean;
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

  const items = useMemo<SidebarItem[]>(() => {
    // Whitelist : dot badge bleu uniquement sur "pending" (review en cours).
    // Icone FileText constante. Sur "accepted", l'item "Mon personnage"
    // s'ajoute juste apres pour materialiser le statut.
    const base: SidebarItem[] = [
      { id: "home", label: "Accueil", route: "/home", icon: Home },
      { id: "shop", label: "Boutique", route: "/shop", icon: ShoppingBag },
      {
        id: "whitelist",
        label: "Whitelist",
        route: "/whitelist",
        icon: FileText,
        dotBadge: whitelistStatus === "pending",
      },
    ];

    // Onglet RP : regroupe Mon personnage + Carte + Screenshots + Feed
    // (screenshots/feed gated whitelist côté page /rp).
    base.push({ id: "rp", label: "RP", route: "/rp", icon: Drama });

    base.push(
      { id: "mods", label: "Mods", route: "/mods", icon: Package },
      {
        id: "tickets",
        label: "Tickets",
        route: "/tickets",
        icon: LifeBuoy,
        // Compteur de messages staff non-lus (cf /v1/me/badges).
        badge: unreadTickets > 0 ? String(Math.min(unreadTickets, 9)) : undefined,
      },
      { id: "rules", label: "Règlement", route: "/rules", icon: Book },
      { id: "lore", label: "Lore", route: "/lore", icon: BookOpen },
      {
        id: "patchnotes",
        label: "Patch Notes",
        route: "/patchnotes",
        icon: Sparkles,
        // Pastille si des patch notes ont été publiés depuis la dernière visite.
        dotBadge: unreadPatchnotes > 0,
      },
      { id: "docs", label: "Documentation", route: "/docs", icon: FileQuestion },
    );

    return base;
  }, [whitelistStatus, unreadTickets, unreadPatchnotes]);

  // Determine l'item actif via le pathname. On match sur le prefix pour que
  // /rules/<slug> reste actif sur l'item Reglement.
  const activeId = useMemo(() => {
    const path = location.pathname;
    const match = items.find(
      (it) => path === it.route || path.startsWith(`${it.route}/`),
    );
    return match?.id ?? null;
  }, [items, location.pathname]);

  const pseudo = user?.displayName ?? user?.minecraftUsername ?? "Joueur";
  const initial = pseudo.charAt(0).toUpperCase();

  return (
    <aside className="reborn-sidebar-mount flex h-full w-[72px] shrink-0 flex-col items-center border-r border-[var(--color-border)] bg-[var(--color-surface)] py-3">
      <button
        type="button"
        onClick={() => navigate("/home")}
        title={
          updateAvailable
            ? "Mise à jour disponible — clic pour ouvrir l'accueil"
            : "Accueil"
        }
        aria-label="Accueil"
        className={[
          "reborn-sidebar-logo",
          updateAvailable && "reborn-sidebar-logo--pulse",
        ]
          .filter(Boolean)
          .join(" ")}
      >
        R
      </button>

      <div className="reborn-sidebar-divider" />

      <nav className="reborn-sidebar-nav flex flex-1 flex-col items-center gap-[2px] overflow-y-auto py-1">
        {items.map((it) => (
          <SidebarIconButton
            key={it.id}
            icon={it.icon}
            label={it.label}
            active={activeId === it.id}
            badge={it.badge}
            dotBadge={it.dotBadge}
            onClick={() => navigate(it.route)}
          />
        ))}
      </nav>

      <div className="relative mt-2">
        <button
          type="button"
          onClick={() => setMenuOpen((v) => !v)}
          aria-label="Menu utilisateur"
          aria-expanded={menuOpen}
          className="reborn-sidebar-user"
        >
          <span className="reborn-sidebar-user-initial">{initial}</span>
          <span className="reborn-sidebar-user-status" />
        </button>
        <AnimatePresence>
          {menuOpen && <UserMenuPopover onClose={() => setMenuOpen(false)} />}
        </AnimatePresence>
      </div>
    </aside>
  );
}
