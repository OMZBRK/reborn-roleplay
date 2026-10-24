import { Bell, Coins } from "lucide-react";
import { useAuthStore } from "../../stores/auth-store";
import { useBadgesStore } from "../../stores/badges-store";

// Header de la Home : salutation + currency + bell.
//
// Pas d'avatar ici : la sidebar 72px contient deja l'avatar utilisateur
// (qui ouvre le UserMenuPopover). Eviter le doublon visuel.
//
// Coins + dot-badge cloche alimentés par /v1/me/badges (badges-store,
// pollé depuis AuthenticatedLayout).
export function HomeHeader() {
  const user = useAuthStore((s) => s.user);
  const pseudo = user?.displayName ?? user?.minecraftUsername ?? "Joueur";

  // Tolerant : en mode navigateur (invoke no-op) ou sur une reponse
  // partielle, le store peut ne pas porter d'objet complet.
  const badges = useBadgesStore((s) => s.badges);
  const coins = badges?.coins ?? 0;
  const hasUnreadNotif = (badges?.unreadTickets ?? 0) + (badges?.unreadPatchnotes ?? 0) > 0;

  return (
    <header className="reborn-home-header">
      <div className="reborn-home-greeting">
        <div className="reborn-home-greeting-line">
          <span>Content de te revoir,&nbsp;</span>
          <span className="reborn-home-greeting-name">{pseudo}</span>
        </div>
        <div className="reborn-home-greeting-meta">
          <span>Naruto Edition</span>
          <span className="reborn-home-greeting-sep">•</span>
          <span>Connecté</span>
        </div>
      </div>
      <div className="reborn-home-header-actions">
        <div className="reborn-home-currency">
          <Coins className="h-3.5 w-3.5" />
          <span>{coins.toLocaleString("fr-FR")}</span>
        </div>
        <button
          type="button"
          className="reborn-home-iconbtn"
          aria-label="Notifications"
        >
          <Bell className="h-4 w-4" />
          {hasUnreadNotif && <span className="reborn-home-iconbtn-dot" />}
        </button>
      </div>
    </header>
  );
}
