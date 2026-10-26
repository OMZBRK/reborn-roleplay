// MAQUETTES : la coque du launcher (fenêtre 1280 × 800, rail de 232 px,
// boutons de fenêtre), recomposée avec les classes réelles de globals.css.
//
// Le vrai Sidebar lit les stores (compte, badges, whitelist) et le routeur :
// ici, tout vient des props, pour que la maquette s'affiche partout (galerie,
// page d'aperçu) sans session ni BrowserRouter. Seule nouveauté visible :
// l'entrée « Personnages » (CANDIDATURE_ET_DOUANE §4.6), dans Principal.

import type { ComponentType, ReactNode } from "react";
import {
  Book,
  BookOpen,
  ChevronRight,
  Drama,
  FileQuestion,
  FileText,
  Home,
  LifeBuoy,
  Package,
  ShoppingBag,
  Sparkles,
  UsersRound,
} from "lucide-react";
import { WindowControls } from "../../../components/shell/WindowControls";
import { ROLE_META } from "../../../components/sidebar/role";
import type { CompteFictif } from "./donnees";

export type EntreeRail = "accueil" | "personnages";

type Item = {
  id: string;
  label: string;
  icon: ComponentType<{ size?: number }>;
  dot?: boolean;
  badge?: string;
};

type Props = {
  actif: EntreeRail;
  compte: CompteFictif;
  /** Pastille or sur « Whitelist » (candidature en cours). */
  pastilleWhitelist?: boolean;
  /** Fenêtre de la hauteur de son contenu (capture longue) au lieu de 800 px. */
  pleineHauteur?: boolean;
  children: ReactNode;
};

export function CoqueLauncher({ actif, compte, pastilleWhitelist, pleineHauteur, children }: Props) {
  const groupes: Array<{ id: string; label: string; items: Item[] }> = [
    {
      id: "principal",
      label: "Principal",
      items: [
        { id: "accueil", label: "Accueil", icon: Home },
        { id: "shop", label: "Boutique", icon: ShoppingBag },
        { id: "whitelist", label: "Whitelist", icon: FileText, dot: pastilleWhitelist },
        { id: "personnages", label: "Personnages", icon: UsersRound },
        { id: "rp", label: "RP", icon: Drama },
      ],
    },
    {
      id: "contenu",
      label: "Contenu",
      items: [
        { id: "mods", label: "Mods", icon: Package },
        { id: "rules", label: "Règlement", icon: Book },
        { id: "lore", label: "Lore", icon: BookOpen },
        { id: "patchnotes", label: "Patch Notes", icon: Sparkles, dot: true },
        { id: "docs", label: "Documentation", icon: FileQuestion },
      ],
    },
    {
      id: "support",
      label: "Support",
      items: [{ id: "tickets", label: "Tickets", icon: LifeBuoy, badge: "1" }],
    },
  ];

  const role = ROLE_META[compte.role];

  return (
    <div
      className="mqa-fenetre reborn-app-shell"
      data-pleine={pleineHauteur ? "true" : undefined}
    >
      <WindowControls />
      <aside className="reborn-rail">
        <div className="reborn-rail-brand" aria-hidden>
          <span className="reborn-rail-mark">R</span>
          <span className="reborn-rail-brand-text">
            <span className="reborn-rail-wordmark">REBORN</span>
            <span className="reborn-rail-tagline">ROLEPLAY</span>
          </span>
        </div>

        <nav className="reborn-rail-nav" aria-label="Navigation principale (maquette)">
          {groupes.map((g) => (
            <div key={g.id} className="reborn-rail-group">
              <span className="reborn-rail-group-label">{g.label}</span>
              {g.items.map((it) => {
                const Icon = it.icon;
                const estActif = it.id === actif;
                return (
                  <button
                    key={it.id}
                    type="button"
                    className="reborn-rail-item"
                    data-active={estActif ? "true" : undefined}
                    aria-current={estActif ? "page" : undefined}
                    tabIndex={-1}
                  >
                    <span className="reborn-rail-item-icon" aria-hidden>
                      <Icon size={18} />
                    </span>
                    <span className="reborn-rail-item-label">{it.label}</span>
                    {it.badge ? (
                      <span className="reborn-rail-item-badge">{it.badge}</span>
                    ) : it.dot ? (
                      <span className="reborn-rail-item-dot" />
                    ) : estActif ? (
                      <ChevronRight size={14} className="reborn-rail-item-chev" aria-hidden />
                    ) : null}
                  </button>
                );
              })}
            </div>
          ))}
        </nav>

        <div className="reborn-rail-foot">
          <div className="reborn-rail-user">
            <span className="reborn-rail-user-avatar" aria-hidden>
              {compte.pseudo.charAt(0).toUpperCase()}
              <span className="reborn-rail-user-status" />
            </span>
            <span className="reborn-rail-user-meta">
              <span className="reborn-rail-user-name">{compte.pseudo}</span>
              <span className="reborn-rail-user-role" style={{ color: role.color }}>
                {role.label}
              </span>
            </span>
          </div>
        </div>
      </aside>

      <main className="mqa-fenetre-main">
        <div className="mqa-fenetre-vue">{children}</div>
      </main>
    </div>
  );
}
