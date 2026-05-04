import { useMemo } from "react";
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
import { logout } from "../lib/auth";
import { UserBlock } from "./sidebar/UserBlock";
import { NavSection, type NavSectionConfig } from "./sidebar/NavSection";
import { ServerStatusFooter } from "./sidebar/ServerStatusFooter";

const NAV_SECTIONS: NavSectionConfig[] = [
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
      { to: "/whitelist", label: "Whitelist", icon: Swords },
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
];

export function Sidebar() {
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();

  // TODO: brancher sur un endpoint /v1/server/status quand il existera
  const server = {
    online: true,
    players: 23,
    capacity: 200,
    ping: 28,
    ip: "play.reborn-rp.fr",
  };

  // Offsets cumulés pour que la stagger animation s'enchaîne sur toute la liste
  const itemOffsets = useMemo(() => {
    const out: number[] = [];
    let acc = 0;
    NAV_SECTIONS.forEach((s) => {
      out.push(acc);
      acc += s.items.length;
    });
    return out;
  }, []);

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
        {NAV_SECTIONS.map((section, i) => (
          <NavSection
            key={section.label}
            section={section}
            isFirst={i === 0}
            itemOffset={itemOffsets[i]}
          />
        ))}
      </nav>

      <ServerStatusFooter
        online={server.online}
        players={server.players}
        capacity={server.capacity}
        ping={server.ping}
        ip={server.ip}
        onLogout={handleLogout}
      />
    </aside>
  );
}
