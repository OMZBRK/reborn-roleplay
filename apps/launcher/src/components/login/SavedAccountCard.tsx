import { X } from "lucide-react";
import { MinecraftHead } from "./MinecraftHead";

type Props = {
  pseudo: string;
  lastSeen: string;
  seed: string;
  onClick: () => void;
  onRemove?: () => void;
};

const REL = new Intl.RelativeTimeFormat("fr", { numeric: "auto" });

function formatLastSeen(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const diffSec = Math.round((then - Date.now()) / 1000);
  const absSec = Math.abs(diffSec);
  if (absSec < 60) return REL.format(diffSec, "second");
  if (absSec < 3600) return REL.format(Math.round(diffSec / 60), "minute");
  if (absSec < 86_400) return REL.format(Math.round(diffSec / 3600), "hour");
  if (absSec < 604_800) return REL.format(Math.round(diffSec / 86_400), "day");
  if (absSec < 2_592_000) return REL.format(Math.round(diffSec / 604_800), "week");
  if (absSec < 31_536_000) return REL.format(Math.round(diffSec / 2_592_000), "month");
  return REL.format(Math.round(diffSec / 31_536_000), "year");
}

// Puce discrète sous la carte d'accueil : tête + pseudo, croix au survol.
// La date de dernière connexion passe en infobulle.
export function SavedAccountCard({ pseudo, lastSeen, seed, onClick, onRemove }: Props) {
  const seen = formatLastSeen(lastSeen);
  return (
    <span className="rb-welcome-chip no-drag">
      <button
        type="button"
        onClick={onClick}
        className="rb-welcome-chip-main"
        title={seen ? `Se reconnecter avec ${pseudo} · vu ${seen}` : `Se reconnecter avec ${pseudo}`}
      >
        <MinecraftHead seed={seed} size={18} />
        <span>{pseudo}</span>
      </button>
      {onRemove && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onRemove();
          }}
          aria-label={`Oublier le compte ${pseudo}`}
          title="Oublier ce compte"
          className="rb-welcome-chip-x"
        >
          <X className="h-3 w-3" />
        </button>
      )}
    </span>
  );
}
