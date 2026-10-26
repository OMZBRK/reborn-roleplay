// MAQUETTES — coque du launcher pour la candidature (dev seulement).
//
// Reprend la coque réelle : rail v4 de 232 px (mêmes classes reborn-rail-*
// que components/Sidebar.tsx, joueur fictif « Kazuki » comme le mode démo,
// rôle Joueur, entrée « Personnages » comme sur L10 / L11), contrôles
// de fenêtre réels, fond .reborn-app-shell. Le vrai Sidebar n'est pas
// importé : il lit le compte connecté et la route courante, alors qu'une
// maquette doit montrer un candidat sur l'onglet Whitelist quoi qu'il arrive.
//
// La barre du haut est celle de Ketto, dans l'identité Reborn : titre de page
// « Ma candidature », onglets numérotés au centre, RBCoins et cloche à droite
// (classes de HomeHeader). Aides clavier en bas à droite.

import type { ReactNode } from "react";
import {
  Bell,
  Book,
  BookOpen,
  ChevronRight,
  Coins,
  Drama,
  FileQuestion,
  FileText,
  Home,
  LifeBuoy,
  Package,
  ShoppingBag,
  Sparkles,
  UsersRound,
  type LucideIcon,
} from "lucide-react";
import { WindowControls } from "../../../components/shell/WindowControls";
import { ROLE_META } from "../../../components/sidebar/role";
import { JOUEUR } from "./donnees";
import "./candidature.css";

type ItemRail = { id: string; label: string; icon: LucideIcon; dot?: boolean };

const GROUPES: { id: string; label: string; items: ItemRail[] }[] = [
  {
    id: "principal",
    label: "Principal",
    items: [
      { id: "home", label: "Accueil", icon: Home },
      { id: "shop", label: "Boutique", icon: ShoppingBag },
      { id: "whitelist", label: "Whitelist", icon: FileText },
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
    items: [{ id: "tickets", label: "Tickets", icon: LifeBuoy }],
  },
];

/** Rail du launcher, figé sur « Whitelist » avec le candidat fictif. */
export function RailMaquette({ dotWhitelist = false }: { dotWhitelist?: boolean }) {
  const role = ROLE_META.player;
  return (
    <aside className="reborn-rail">
      <button type="button" className="reborn-rail-brand" aria-label="Accueil">
        <span className="reborn-rail-mark">R</span>
        <span className="reborn-rail-brand-text">
          <span className="reborn-rail-wordmark">REBORN</span>
          <span className="reborn-rail-tagline">ROLEPLAY</span>
        </span>
      </button>
      <nav className="reborn-rail-nav" aria-label="Navigation principale">
        {GROUPES.map((g) => (
          <div key={g.id} className="reborn-rail-group">
            <span className="reborn-rail-group-label">{g.label}</span>
            {g.items.map((it) => {
              const Icon = it.icon;
              const active = it.id === "whitelist";
              const dot = it.dot || (it.id === "whitelist" && dotWhitelist);
              return (
                <button
                  key={it.id}
                  type="button"
                  className="reborn-rail-item"
                  data-active={active ? "true" : undefined}
                  aria-current={active ? "page" : undefined}
                >
                  <span className="reborn-rail-item-icon" aria-hidden>
                    <Icon size={18} />
                  </span>
                  <span className="reborn-rail-item-label">{it.label}</span>
                  {dot ? (
                    <span className="reborn-rail-item-dot" />
                  ) : (
                    active && <ChevronRight size={14} className="reborn-rail-item-chev" aria-hidden />
                  )}
                </button>
              );
            })}
          </div>
        ))}
      </nav>
      <div className="reborn-rail-foot">
        <button type="button" className="reborn-rail-user" aria-label={`Menu utilisateur de ${JOUEUR.pseudo}`}>
          <span className="reborn-rail-user-avatar" aria-hidden>
            {JOUEUR.pseudo.charAt(0)}
            <span className="reborn-rail-user-status" />
          </span>
          <span className="reborn-rail-user-meta">
            <span className="reborn-rail-user-name">{JOUEUR.pseudo}</span>
            <span className="reborn-rail-user-role" style={{ color: role.color }}>
              {role.label}
            </span>
          </span>
        </button>
      </div>
    </aside>
  );
}

type CoqueProps = {
  /** Onglets de la barre du haut (formulaire ou suivi). */
  onglets: ReactNode;
  children: ReactNode;
  /** false : contenu seul, sans rail ni barre (si l'hôte fournit déjà la coque). */
  coque?: boolean;
  /** Aides clavier en bas à droite. */
  aides?: boolean;
  dotWhitelist?: boolean;
  /** Défilement remis en haut quand cette clé change (changement d'onglet). */
  cle?: string;
};

export function CoqueCandidature({ onglets, children, coque = true, aides = true, dotWhitelist, cle }: CoqueProps) {
  const zone = (
    <div className="mqc-main" data-aides={aides || undefined}>
      <header className="mqc-topbar">
        <div className="mqc-topbar-title">Ma candidature</div>
        <nav className="mqc-tabs" aria-label="Onglets de la candidature">
          {onglets}
        </nav>
        <div className="mqc-topbar-actions">
          <div className="reborn-home-currency" title="RBCoins">
            <Coins className="h-3.5 w-3.5" aria-hidden />
            <span>{JOUEUR.rbcoins.toLocaleString("fr-FR")}</span>
          </div>
          <button type="button" className="reborn-home-iconbtn" aria-label="Notifications">
            <Bell className="h-4 w-4" aria-hidden />
          </button>
        </div>
      </header>
      <div className="mqc-scroll" key={cle}>
        <div className="mqc-col">{children}</div>
      </div>
      {aides && (
        <div className="mqc-keys" aria-hidden>
          <span>
            <span className="mqc-kbd">A / E</span>
            Changer d'onglet
          </span>
          <span>
            <span className="mqc-kbd">Échap</span>
            Retour
          </span>
        </div>
      )}
    </div>
  );

  if (!coque) return zone;
  return (
    <div className="reborn-app-shell mqc-coque">
      <RailMaquette dotWhitelist={dotWhitelist} />
      {zone}
      <WindowControls />
    </div>
  );
}
