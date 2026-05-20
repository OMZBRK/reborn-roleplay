import { useEffect } from "react";
import { invoke } from "../../lib/tauri";
import { useNetworkStore } from "../../stores/network-store";
import { useUpdateStore } from "../../stores/update-store";
import { useCrashStore, type GameCrashReport } from "../../stores/crash-store";

// Helpers DevTools exposes sous window.__reborn en dev uniquement.
// Permet d'inspecter / forcer les states systeme depuis la console sans
// avoir a relancer le launcher ou simuler une coupure reseau reelle.
//
// Usage console DevTools (en dev) :
//   window.__reborn.network.status                 // 'checking' | 'online' | 'offline'
//   window.__reborn.network.set('offline')         // force le state du store
//   await window.__reborn.network.pingNow()        // appelle le ping Rust directement
//   window.__reborn.update.set(true)               // simule update dispo (pulse logo)
//   window.__reborn.crash.trigger({ exitCode: 134 })
//   window.__reborn.crash.close()
//
// Tout est gated par import.meta.env.DEV → totalement removed en release.

type CrashTriggerInput = Partial<GameCrashReport>;

type RebornDevApi = {
  network: {
    readonly status: string;
    set: (status: "checking" | "online" | "offline") => void;
    pingNow: () => Promise<boolean>;
  };
  update: {
    readonly available: boolean;
    set: (available: boolean) => void;
  };
  crash: {
    trigger: (override?: CrashTriggerInput) => void;
    close: () => void;
  };
};

declare global {
  interface Window {
    __reborn?: RebornDevApi;
  }
}

const DEMO_CRASH: GameCrashReport = {
  exitCode: 255,
  summary: "Le serveur interne a rencontré une erreur fatale.",
  suspectedMod: {
    filename: "lithium-fabric-mc1.21.1-0.12.2.jar",
    reason: "Mod incompatible avec Fabric Loader courant",
  },
  logPath: null,
};

export function DevHelpers() {
  useEffect(() => {
    if (!import.meta.env.DEV) return;

    const api: RebornDevApi = {
      network: {
        get status() {
          return useNetworkStore.getState().status;
        },
        set(status) {
          useNetworkStore.getState().setStatus(status);
        },
        async pingNow() {
          const ok = await invoke<boolean>("network_ping_health");
          // Met a jour le store en plus de retourner — utile pour
          // tester l'enchainement complet depuis la console.
          useNetworkStore.getState().setStatus(ok ? "online" : "offline");
          return ok;
        },
      },
      update: {
        get available() {
          return useUpdateStore.getState().available;
        },
        set(available) {
          useUpdateStore.getState().setAvailable(available);
        },
      },
      crash: {
        trigger(override) {
          useCrashStore.getState().open({ ...DEMO_CRASH, ...override });
        },
        close() {
          useCrashStore.getState().close();
        },
      },
    };

    window.__reborn = api;
    return () => {
      delete window.__reborn;
    };
  }, []);

  return null;
}
