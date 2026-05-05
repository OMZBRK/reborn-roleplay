import { Hourglass } from "lucide-react";
import type { WhitelistStatus } from "../../stores/whitelist-store";

// Badge contextuel rendu à côté du nav item "Whitelist" :
//  - pending  → carré sablier accent (couleur var(--color-accent))
//  - accepted → dot vert (var(--color-success))
//  - draft / rejected → rien (renvoie null)
export function WhitelistBadge({ status }: { status: WhitelistStatus }) {
  if (status === "pending") {
    return (
      <span
        className="wl-nav-hourglass-badge"
        title="Candidature en attente de review"
      >
        <Hourglass size={11} />
      </span>
    );
  }
  if (status === "accepted") {
    return <span className="wl-nav-accepted-dot" title="Whitelist validée" />;
  }
  return null;
}
