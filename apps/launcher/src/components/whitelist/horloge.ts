import { useEffect, useState } from "react";

/**
 * Heure courante, rafraîchie toutes les `intervalMs` (comptes à rebours,
 * fenêtre d'entrée). `cibles` : instants (ms) où un rendu doit partir pile
 * (ouverture d'un bouton, fin d'un délai), sans attendre le tour suivant.
 */
export function useMaintenant(intervalMs = 30_000, cibles: Array<number | null | undefined> = []): Date {
  const [now, setNow] = useState(() => new Date());
  const cle = cibles.filter((c): c is number => typeof c === "number" && Number.isFinite(c)).join(",");

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);

  useEffect(() => {
    if (!cle) return;
    const ids = cle
      .split(",")
      .map(Number)
      .map((t) => t - Date.now() + 250)
      .filter((ms) => ms > 0 && ms < 2 ** 31 - 1)
      .map((ms) => window.setTimeout(() => setNow(new Date()), ms));
    return () => ids.forEach((id) => window.clearTimeout(id));
  }, [cle]);

  return now;
}
