import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import {
  BookOpen,
  FileText,
  Gavel,
  Home,
  LifeBuoy,
  Newspaper,
  ShoppingBag,
  Swords,
} from "lucide-react";
import { useAuthStore } from "../stores/auth-store";
import { useWhitelistStore } from "../stores/whitelist-store";
import { logout } from "../lib/auth";
import { fetchServerStatus, type ServerStatus } from "../lib/content";
import { UserBlock } from "./sidebar/UserBlock";
import { NavSection, type NavSectionConfig } from "./sidebar/NavSection";
import { ServerStatusFooter } from "./sidebar/ServerStatusFooter";
import { WhitelistBadge } from "./sidebar/WhitelistBadge";

// Polling toutes les 30s pour le statut serveur. Coté API on a un cache 10s
// donc 3 requêtes max par minute par client — pas de pression sur Paper.
const SERVER_POLL_MS = 30_000;

export function Sidebar() {
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();
  const whitelistStatus = useWhitelistStore((s) => s.status);

  // Sections déclarées dans le composant pour pouvoir injecter le badge
  // contextuel "Whitelist" en fonction du statut courant (pending/accepted).
  const navSections: NavSectionConfig[] = useMemo(
    () => [
      {
        label: "Principal",
        items: [
          { to: "/home", label: "Accueil", icon: Home },
          { to: "/shop", label: "Boutique", icon: ShoppingBag },
        ],
      },
      {
        label: "Communauté",
        items: [
          {
            to: "/whitelist",
            label: "Whitelist",
            icon: Swords,
            badge: <WhitelistBadge status={whitelistStatus} />,
          },
          { to: "/tickets", label: "Tickets", icon: LifeBuoy },
        ],
      },
      {
        label: "Contenu",
        items: [
          { to: "/rules", label: "Règlement", icon: Gavel },
          { to: "/lore", label: "Lore", icon: BookOpen },
          { to: "/patchnotes", label: "Patch Notes", icon: Newspaper },
          { to: "/docs", label: "Documentation", icon: FileText },
        ],
      },
    ],
    [whitelistStatus],
  );

  // État du serveur Minecraft (ping via /v1/server/status). null pendant la
  // toute première fetch — affichage "—" pour les chiffres.
  const [server, setServer] = useState<ServerStatus | null>(null);

  useEffect(() => {
    let cancelled = false;
    let timer: number | undefined;
    async function poll() {
      try {
        const data = await fetchServerStatus();
        if (!cancelled) setServer(data);
      } catch (err) {
        // L'API peut être down (dev local). On retombe sur l'état "offline"
        // visuellement plutôt que de laisser des chiffres factices.
        if (!cancelled) {
          console.warn("[sidebar] server status failed:", err);
          setServer((prev) =>
            prev ?? {
              online: false,
              players: 0,
              capacity: 0,
              ping: null,
              version: null,
              motd: null,
              ip: "—",
              measuredAt: new Date().toISOString(),
            },
          );
        }
      } finally {
        if (!cancelled) {
          timer = window.setTimeout(poll, SERVER_POLL_MS);
        }
      }
    }
    poll();
    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, []);

  // Offsets cumulés pour que la stagger animation s'enchaîne sur toute la liste
  const itemOffsets = useMemo(() => {
    const out: number[] = [];
    let acc = 0;
    navSections.forEach((s) => {
      out.push(acc);
      acc += s.items.length;
    });
    return out;
  }, [navSections]);

  async function handleLogout() {
    try {
      await logout();
    } finally {
      setSession(null);
      navigate("/login", { replace: true });
    }
  }

  return (
    <aside
      className="reborn-sidebar-mount flex h-full shrink-0 flex-col border-r border-border bg-surface"
      style={{ width: 280 }}
    >
      <UserBlock />

      <nav className="flex-1 overflow-y-auto py-2">
        {navSections.map((section, i) => (
          <NavSection
            key={section.label}
            section={section}
            isFirst={i === 0}
            itemOffset={itemOffsets[i]}
          />
        ))}
      </nav>

      <ServerStatusFooter
        online={server?.online ?? false}
        players={server?.players ?? 0}
        capacity={server?.capacity ?? 0}
        ping={server?.ping ?? null}
        ip={server?.ip ?? "—"}
        onLogout={handleLogout}
      />
    </aside>
  );
}
