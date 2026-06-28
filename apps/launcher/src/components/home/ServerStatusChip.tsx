import { useEffect, useState } from "react";
import { Wifi, WifiOff } from "lucide-react";
import { fetchServerStatus, type ServerStatus } from "../../lib/content";

// Chip de statut serveur LIVE dans le hero. Remplace l'ancien chip statique
// "VPS connecté". Poll l'endpoint public /v1/server/status (SLP côté API)
// toutes les 20s + une fois au montage. Affiche le nombre de joueurs en
// ligne quand le serveur répond, "Serveur hors-ligne" sinon. Toute erreur
// IPC/réseau est traitée comme un état inconnu (offline) — jamais de throw
// visible à l'utilisateur.
const POLL_MS = 20_000;

export function ServerStatusChip() {
  const [status, setStatus] = useState<ServerStatus | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const s = await fetchServerStatus();
        if (cancelled) return;
        setStatus(s);
        setFailed(false);
      } catch {
        if (cancelled) return;
        setFailed(true);
      }
    }

    void poll();
    const id = setInterval(poll, POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  const online = !!status?.online && !failed;
  const label = (() => {
    if (failed || !status) return online ? "VPS connecté" : "Serveur hors-ligne";
    if (!status.online) return "Serveur hors-ligne";
    const { online: on, max } = status.players;
    const who = on <= 1 ? "joueur" : "joueurs";
    return max > 0 ? `${on}/${max} en ligne` : `${on} ${who} en ligne`;
  })();

  return (
    <span
      className="reborn-home-hero-chip"
      title={
        status?.latencyMs != null ? `Latence ${status.latencyMs} ms` : undefined
      }
      style={online ? undefined : { opacity: 0.7 }}
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
