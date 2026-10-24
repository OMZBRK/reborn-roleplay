import { useState } from "react";
import { Wifi, WifiOff } from "lucide-react";
import { fetchServerStatus, type ServerStatus } from "../../lib/content";
import { formatIpcError } from "../../lib/errors";
import { usePolling } from "../../hooks/use-polling";

// Chip de statut serveur LIVE dans le hero. Sonde l'endpoint public
// /v1/server/status (SLP cote API) toutes les 20 s + une fois au montage,
// en pause pendant une partie (le launcher est reduit). Affiche le nombre
// de joueurs en ligne quand le serveur repond, « Serveur hors-ligne »
// sinon ; la vraie cause d'un echec est dans l'infobulle.
const POLL_MS = 20_000;

export function ServerStatusChip() {
  const [status, setStatus] = useState<ServerStatus | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  usePolling(async (signal) => {
    try {
      const s = await fetchServerStatus();
      if (signal.aborted) return;
      setStatus(s ?? null);
      setFailure(s ? null : "Statut du serveur indisponible hors du launcher.");
    } catch (err) {
      if (signal.aborted) return;
      setFailure(formatIpcError(err, "Statut du serveur indisponible"));
    }
  }, POLL_MS);

  const failed = failure !== null;
  const online = !!status?.online && !failed;
  const pending = !status && !failed;
  const label = (() => {
    // Premier sondage en cours : ne pas afficher « hors-ligne » a tort.
    if (pending) return "Connexion…";
    if (failed || !status || !status.online) return "Serveur hors-ligne";
    const { online: on, max } = status.players;
    const who = on <= 1 ? "joueur" : "joueurs";
    return max > 0 ? `${on}/${max} en ligne` : `${on} ${who} en ligne`;
  })();

  const title = failed
    ? failure
    : [
        status?.version && `Serveur ${status.version}`,
        status?.latencyMs != null && `Latence ${status.latencyMs} ms`,
      ]
        .filter(Boolean)
        .join(" · ") || undefined;

  return (
    <span
      className="reborn-home-hero-chip"
      title={title ?? undefined}
      style={online || pending ? undefined : { opacity: 0.7 }}
    >
      {online ? (
        <Wifi className="h-2.5 w-2.5" style={{ color: "var(--color-success)" }} />
      ) : (
        <WifiOff className="h-2.5 w-2.5" />
      )}
      {label}
    </span>
  );
}
