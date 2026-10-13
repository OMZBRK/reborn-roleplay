import { useCallback, useEffect, useRef, useState } from "react";
import { refreshMe, startDiscordLink } from "../lib/auth";
import { useAuthStore } from "../stores/auth-store";

// Liaison Discord partagée (écran d'accueil + Paramètres > Connexions).
// `discord_link_start` (Rust) ouvre le navigateur ; on détecte la fin en
// pollant `auth_me` jusqu'à ce que `discord` soit renseigné côté API.

const POLL_MS = 2500;
const POLL_TIMEOUT_MS = 5 * 60_000;

function messageOf(err: unknown, fallback: string): string {
  if (typeof err === "string") return err;
  return (err as { message?: string } | null)?.message ?? fallback;
}

export function useDiscordLink() {
  const setUser = useAuthStore((s) => s.setUser);
  const [linking, setLinking] = useState(false);
  const [waiting, setWaiting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pollRef = useRef<number | null>(null);

  const stopPolling = useCallback(() => {
    if (pollRef.current) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  useEffect(() => stopPolling, [stopPolling]);

  const startPolling = useCallback(() => {
    stopPolling();
    let elapsed = 0;
    pollRef.current = window.setInterval(async () => {
      elapsed += POLL_MS;
      try {
        const fresh = await refreshMe();
        if (fresh?.discord) {
          setUser(fresh);
          stopPolling();
          setWaiting(false);
          return;
        }
      } catch {
        // Réseau en vol : on retente au tick suivant.
      }
      if (elapsed >= POLL_TIMEOUT_MS) {
        stopPolling();
        setWaiting(false);
        setError("Aucune liaison détectée après 5 min. Si tu as validé côté Discord, réessaie.");
      }
    }, POLL_MS);
  }, [setUser, stopPolling]);

  /** Ouvre la page Discord et démarre l'attente. */
  const link = useCallback(async () => {
    setError(null);
    setLinking(true);
    try {
      await startDiscordLink();
      setWaiting(true);
      startPolling();
    } catch (err) {
      setError(messageOf(err, "Impossible d'ouvrir la page Discord."));
    } finally {
      setLinking(false);
    }
  }, [startPolling]);

  /** Rouvre la page Discord sans relancer l'attente. */
  const reopen = useCallback(async () => {
    try {
      await startDiscordLink();
    } catch (err) {
      setError(messageOf(err, "Impossible d'ouvrir la page Discord."));
    }
  }, []);

  const cancel = useCallback(() => {
    stopPolling();
    setWaiting(false);
  }, [stopPolling]);

  /** Vérification immédiate (bouton « Rafraîchir »). */
  const refreshNow = useCallback(async () => {
    setError(null);
    try {
      const fresh = await refreshMe();
      setUser(fresh);
      if (fresh?.discord) cancel();
    } catch (err) {
      setError(messageOf(err, "Erreur"));
    }
  }, [cancel, setUser]);

  return { linking, waiting, error, setError, link, reopen, cancel, refreshNow };
}
