import type { SavedAccount } from "../../lib/types";
import { SavedAccountCard } from "./SavedAccountCard";

type Props = {
  accounts: SavedAccount[];
  onPick: (a: SavedAccount) => void;
  onRemove?: (a: SavedAccount) => void;
};

// Comptes Microsoft enregistrés, en ligne discrète sous la carte d'accueil.
// « Ajouter un compte » = le bouton principal « Se connecter avec Microsoft ».
export function SavedAccountsCarousel({ accounts, onPick, onRemove }: Props) {
  if (accounts.length === 0) return null;

  return (
    <div className="rb-welcome-accounts no-drag">
      <span className="rb-welcome-accounts-label">Comptes enregistrés</span>
      {accounts.map((a) => (
        <SavedAccountCard
          key={a.pseudo}
          pseudo={a.pseudo}
          lastSeen={a.lastSeen}
          seed={a.seed}
          onClick={() => onPick(a)}
          onRemove={onRemove ? () => onRemove(a) : undefined}
        />
      ))}
    </div>
  );
}
