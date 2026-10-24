import { useEffect } from "react";
import { pingHealth } from "../../lib/network-status";
import { useNetworkStore } from "../../stores/network-store";
import { useUpdateStore } from "../../stores/update-store";
import { useCrashStore, type GameCrashReport } from "../../stores/crash-store";
import { useLaunchStore } from "../../stores/launch-store";
import { useLogsStore } from "../../stores/logs-store";
import { closeLogsPanel, openLogsPanel } from "../game-log/logs-panel-store";

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
//   window.__reborn.download.demo(10)              // faux telechargement (barre liquide)
//   window.__reborn.download.set(0.6)              // fige la barre a 60% (null = off)
//   window.__reborn.logs.open()                    // ouvre le panneau « Journal du jeu »
//   window.__reborn.logs.demo(3000)                // injecte 3000 fausses lignes
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
  download: {
    /** Rejoue un faux telechargement (paquets irreguliers) sur la barre liquide. */
    demo: (seconds?: number) => void;
    set: (progress: number | null) => void;
  };
  logs: {
    open: () => void;
    close: () => void;
    /** Ajoute `count` fausses lignes (niveaux melanges) au tampon front. */
    demo: (count?: number) => void;
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
  stderrTail:
    "java.lang.NoSuchMethodError: 'void net.minecraft.class_2680.<init>'\n" +
    "\tat me.jellysquid.mods.lithium.common.LithiumMixin (lithium.jar)\n" +
    "\t... 42 more",
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
          const { ok, cause } = await pingHealth();
          // Met a jour le store en plus de retourner — utile pour
          // tester l'enchainement complet depuis la console.
          useNetworkStore.getState().setStatus(ok ? "online" : "offline", { cause });
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
      download: {
        demo(seconds = 10) {
          const set = useLaunchStore.getState().setDemoProgress;
          let p = 0;
          set(0);
          const id = window.setInterval(() => {
            // Paquets irreguliers, comme manifest:progress : parfois un gros
            // saut (ballottement), parfois rien.
            p = Math.min(1, p + (Math.random() < 0.15 ? 0.12 : Math.random() * 0.03));
            set(p);
            if (p >= 1) {
              window.clearInterval(id);
              window.setTimeout(() => set(null), 1500);
            }
          }, (seconds * 1000) / 60);
        },
        set(progress) {
          useLaunchStore.getState().setDemoProgress(progress);
        },
      },
      logs: {
        open: openLogsPanel,
        close: closeLogsPanel,
        demo(count = 2000) {
          const now = Date.now();
          const levels = ["info", "info", "info", "warn", "error", "debug"] as const;
          useLogsStore.getState().append(
            Array.from({ length: count }, (_, i) => {
              const level = levels[i % levels.length];
              return {
                t: now - (count - i) * 40,
                stream: level === "error" ? ("err" as const) : ("out" as const),
                level,
                text:
                  i % 12 === 5
                    ? `\tat net.minecraft.client.Main.main(Main.java:${i})`
                    : `[Render thread/${level.toUpperCase()}]: Ligne de démonstration n°${i} — accents éàçù`,
              };
            }),
          );
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
