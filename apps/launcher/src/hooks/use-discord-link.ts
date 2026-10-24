import { useCallback, useEffect, useRef, useState } from "react";
import { refreshMe, startDiscordLink } from "../lib/auth";
import { formatIpcError } from "../lib/errors";
import { useAuthStore } from "../stores/auth-store";
import { startPolling, type PollingHandle } from "./use-polling";

// Liaison Discord partagée (écran d'accueil + Paramètres > Connexions).
// `discord_link_start` (Rust) ouvre le navigateur ; on détecte la fin en
// sondant `auth_me` jusqu'à ce que `discord` soit renseigné côté API
// (startPolling : pas de chevauchement ; pas de pause en jeu, l'attente est
// lancee par le joueur et bornee).

const POLL_MS = 2500;
const POLL_TIMEOUT_MS = 5 * 60_000;

export function useDiscordLink() {
  const setUser = useAuthStore((s) => s.setUser);
  const [linking, setLinking] = useState(false);
  const [waiting, setWaiting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pollRef = useRef<PollingHandle | null>(null);

  const stopWaiting = useCallback(() => {
    pollRef.current?.stop();
    pollRef.current = null;
  }, []);

  useEffect(() => stopWaiting, [stopWaiting]);

  const startWaiting = useCallback(() => {
    stopWaiting();
    const deadline = Date.now() + POLL_TIMEOUT_MS;
    const handle = startPolling(
      async () => {
        try {
          const fresh = await refreshMe();
          if (handle !== pollRef.current) return;
          if (fresh?.discord) {
            setUser(fresh);
            stopWaiting();
            setWaiting(false);
            return;
          }
        } catch {
          // Réseau en vol : on retente au tour suivant.
        }
        if (handle === pollRef.current && Date.now() >= deadline) {
          stopWaiting();
          setWaiting(false);
          setError("Aucune liaison détectée après 5 min. Si tu as validé côté Discord, réessaie.");
        }
      },
      POLL_MS,
      // Attente lancee par le joueur et bornee a 5 min : elle continue meme
      // si une partie tourne (sinon la liaison n'aboutirait jamais en jeu).
      { immediate: false, pauseWhileGame: false },
    );
    pollRef.current = handle;
  }, [setUser, stopWaiting]);

  /** Ouvre la page Discord et démarre l'attente. */
  const link = useCallback(async () => {
    setError(null);
    setLinking(true);
    try {
      await startDiscordLink();
      setWaiting(true);
      startWaiting();
    } catch (err) {
      setError(formatIpcError(err, "Impossible d'ouvrir la page Discord"));
    } finally {
      setLinking(false);
    }
  }, [startWaiting]);

  /** Rouvre la page Discord sans relancer l'attente. */
  const reopen = useCallback(async () => {
    try {
      await startDiscordLink();
    } catch (err) {
      setError(formatIpcError(err, "Impossible d'ouvrir la page Discord"));
    }
  }, []);

  const cancel = useCallback(() => {
    stopWaiting();
    setWaiting(false);
  }, [stopWaiting]);

  /** Vérification immédiate (bouton « Rafraîchir »). */
  const refreshNow = useCallback(async () => {
    setError(null);
    try {
      const fresh = await refreshMe();
      setUser(fresh);
      if (fresh?.discord) cancel();
    } catch (err) {
      setError(formatIpcError(err, "Vérification de la liaison Discord impossible"));
    }
  }, [cancel, setUser]);

  return { linking, waiting, error, setError, link, reopen, cancel, refreshNow };
}
