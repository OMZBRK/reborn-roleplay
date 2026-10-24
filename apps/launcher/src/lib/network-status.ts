import { invoke } from "./tauri";
import { useNetworkStore } from "../stores/network-store";

const PING_INTERVAL_MS = 12_000;
const SUCCESS_BANNER_TTL_MS = 4_000;

let pingTimer: number | null = null;
let successTimer: number | null = null;

/** Resultat de network_ping_health : `cause` seulement en cas d'echec. */
export type HealthPing = { ok: boolean; cause: string | null };

/**
 * Un ping /health. network_ping_health ne rejette jamais (erreur encapsulee
 * cote Rust) ; le catch n'est qu'une ceinture pour le mode navigateur pur,
 * ou invoke() renvoie undefined. Tolere l'ancien retour booleen.
 */
export async function pingHealth(): Promise<HealthPing> {
  try {
    const r = await invoke<HealthPing | boolean | undefined>("network_ping_health");
    if (typeof r === "boolean") return { ok: r, cause: null };
    if (r && typeof r === "object") {
      return { ok: r.ok === true, cause: typeof r.cause === "string" && r.cause ? r.cause : null };
    }
    return { ok: false, cause: null };
  } catch (e) {
    console.warn("[network] sondage impossible :", e);
    return { ok: false, cause: null };
  }
}

async function tick(): Promise<void> {
  const prev = useNetworkStore.getState().status;
  const { ok, cause } = await pingHealth();
  const next = ok ? "online" : "offline";

  if (next === prev) {
    // Status inchange : on update juste lastCheckedAt (et la cause).
    useNetworkStore.getState().setStatus(next, { cause });
    return;
  }

  // Transition. Si on bascule offline → online, on declenche le banner
  // "Connexion retablie" pendant SUCCESS_BANNER_TTL_MS.
  const justRecovered = prev === "offline" && next === "online";
  useNetworkStore.getState().setStatus(next, { justRecovered, cause });

  if (justRecovered) {
    if (successTimer !== null) window.clearTimeout(successTimer);
    successTimer = window.setTimeout(() => {
      // Reset uniquement le flag, ne touche pas le status (peut avoir
      // change entre-temps).
      const current = useNetworkStore.getState();
      if (current.status === "online") {
        current.setStatus("online", { justRecovered: false });
      }
      successTimer = null;
    }, SUCCESS_BANNER_TTL_MS);
  }
}

export function startNetworkStatusPolling(): () => void {
  if (pingTimer !== null) return stopNetworkStatusPolling;

  void tick();
  pingTimer = window.setInterval(() => {
    void tick();
  }, PING_INTERVAL_MS);

  return stopNetworkStatusPolling;
}

export function stopNetworkStatusPolling(): void {
  if (pingTimer !== null) {
    window.clearInterval(pingTimer);
    pingTimer = null;
  }
  if (successTimer !== null) {
    // Ce minuteur devait eteindre « Connexion retablie » : sans lui, le
    // bandeau resterait affiche jusqu'a la reprise du sondage (fin de
    // partie). On eteint le drapeau tout de suite.
    window.clearTimeout(successTimer);
    successTimer = null;
    if (useNetworkStore.getState().justRecovered) {
      useNetworkStore.setState({ justRecovered: false });
    }
  }
}
