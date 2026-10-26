// Zone de la page Candidature : barre du haut de Ketto dans l'identité
// Reborn (titre « Ma candidature », onglets numérotés au centre, RBCoins et
// cloche à droite, classes de HomeHeader), contenu en colonne de 760 px qui
// défile, aides clavier en pied. Reprise de la coque des maquettes
// (src/dev/maquettes/candidature/Coque.tsx), sans le rail : celui de
// l'application est déjà là (AuthenticatedLayout).

import type { ReactNode } from "react";
import { Bell, Coins } from "lucide-react";
import { useBadgesStore } from "../../stores/badges-store";
import "./candidature.css";

type Props = {
  /** Onglets de la barre du haut (formulaire ou suivi). */
  onglets: ReactNode;
  children: ReactNode;
  /** Aides clavier en pied (formulaire seulement). */
  aides?: boolean;
  /** « Échap Retour » dans les aides (une page où revenir existe). */
  retour?: boolean;
  /** Défilement remis en haut quand cette clé change (changement d'onglet). */
  cle?: string;
};

export function BarreCandidature({ onglets, children, aides = false, retour = false, cle }: Props) {
  const badges = useBadgesStore((s) => s.badges);
  const coins = badges?.coins ?? 0;
  const nonLu = (badges?.unreadTickets ?? 0) + (badges?.unreadPatchnotes ?? 0) > 0;
  return (
    <div className="cand-main">
      <header className="cand-topbar">
        <div className="cand-topbar-title">Ma candidature</div>
        <nav className="cand-tabs" aria-label="Onglets de la candidature">
          {onglets}
        </nav>
        <div className="cand-topbar-actions">
          <div className="reborn-home-currency" title="RBCoins">
            <Coins className="h-3.5 w-3.5" aria-hidden />
            <span>{coins.toLocaleString("fr-FR")}</span>
          </div>
          <button type="button" className="reborn-home-iconbtn" aria-label="Notifications">
            <Bell className="h-4 w-4" aria-hidden />
            {nonLu && <span className="reborn-home-iconbtn-dot" />}
          </button>
        </div>
      </header>
      <div className="cand-scroll" key={cle} id="cand-scroll">
        <div className="cand-col">{children}</div>
      </div>
      {aides && (
        <div className="cand-keys" aria-hidden>
          <span>
            <span className="cand-kbd">A / E</span>
            Changer d'onglet
          </span>
          {retour && (
            <span>
              <span className="cand-kbd">Échap</span>
              Retour
            </span>
          )}
        </div>
      )}
    </div>
  );
}
