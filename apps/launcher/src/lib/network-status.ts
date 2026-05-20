import { invoke } from "./tauri";
import { useNetworkStore } from "../stores/network-store";

const PING_INTERVAL_MS = 12_000;
const SUCCESS_BANNER_TTL_MS = 4_000;

let pingTimer: number | null = null;
let successTimer: number | null = null;

async function pingOnce(): Promise<boolean> {
  // network_ping_health renvoie un bool meme en cas d'erreur (encapsule
  // cote Rust). Le catch ici n'est qu'une ceinture pour le mode dev pur
  // navigateur ou invoke() retourne undefined.
  try {
    const ok = await invoke<boolean>("network_ping_health");
    return ok === true;
  } catch {
    return false;
  }
}

async function tick(): Promise<void> {
  const prev = useNetworkStore.getState().status;
  const ok = await pingOnce();
  const next = ok ? "online" : "offline";

  if (next === prev) {
    // Status inchange : on update juste lastCheckedAt.
    useNetworkStore.getState().setStatus(next);
    return;
  }

  // Transition. Si on bascule offline → online, on declenche le banner
  // "Connexion retablie" pendant SUCCESS_BANNER_TTL_MS.
  const justRecovered = prev === "offline" && next === "online";
  useNetworkStore.getState().setStatus(next, { justRecovered });

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
    window.clearTimeout(successTimer);
    successTimer = null;
  }
}
