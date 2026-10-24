// Wrappers fins autour de @tauri-apps/api pour l'IPC frontend → backend Rust.
// Le frontend ne fait JAMAIS d'appels HTTP directs (cf §3.1 du plan) :
// tout passe par des #[tauri::command] cote Rust.
//
// Les erreurs renvoyees par une commande arrivent telles quelles dans le
// catch : un objet { kind, message } (contrat IPC). Pour les afficher,
// toujours passer par formatIpcError (lib/errors.ts).

import { invoke as tauriInvoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import type {
  DownloadProgress,
  GameCrashedEvent,
  GameDiagnostic,
  GameWindowReadyEvent,
  LaunchedGame,
  LaunchProgress,
  ModsPurgedEvent,
  TamperingEvent,
} from "./launcher";
import type { LogBatchEvent } from "./logs";

const isTauriRuntime = typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;

export async function invoke<T = unknown>(
  cmd: string,
  args?: Record<string, unknown>,
): Promise<T> {
  if (!isTauriRuntime) {
    // En mode dev pur navigateur (vite dev sans tauri), on log et on no-op.
    console.warn(`[tauri] invoke('${cmd}') ignored : not running in Tauri.`);
    return undefined as T;
  }
  return tauriInvoke<T>(cmd, args);
}

export const isTauri = isTauriRuntime;

export type { UnlistenFn };

/** Evenements emis par le backend Rust et leur payload (contrat partage). */
export type TauriEventMap = {
  "game:started": LaunchedGame;
  "game:window-ready": GameWindowReadyEvent;
  /** Code de sortie de la JVM (-1 si illisible). */
  "game:exited": number;
  "game:crashed": GameCrashedEvent;
  "game:diagnostic": GameDiagnostic;
  "game:log-batch": LogBatchEvent;
  "launch:progress": LaunchProgress;
  "manifest:progress": DownloadProgress;
  "mods:purged": ModsPurgedEvent;
  "integrity:tampering": TamperingEvent;
};

export type TauriEventName = keyof TauriEventMap;

const noop: UnlistenFn = () => {};

/**
 * Abonnement a un evenement Tauri, sans risque :
 *  - hors Tauri (vite dev navigateur), ne fait rien et renvoie un no-op ;
 *  - la fonction renvoyee est idempotente et n'echoue jamais (le unlisten
 *    natif est asynchrone et peut rejeter si la fenetre est deja detruite).
 */
export function onTauriEvent<K extends TauriEventName>(
  name: K,
  cb: (payload: TauriEventMap[K]) => void,
): Promise<UnlistenFn>;
export function onTauriEvent<T>(name: string, cb: (payload: T) => void): Promise<UnlistenFn>;
export async function onTauriEvent<T>(
  name: string,
  cb: (payload: T) => void,
): Promise<UnlistenFn> {
  if (!isTauriRuntime) return noop;
  const unlisten = await listen<T>(name, (e) => cb(e.payload));
  let done = false;
  return () => {
    if (done) return;
    done = true;
    try {
      void Promise.resolve(unlisten()).catch(() => {});
    } catch {
      // Listener deja retire cote plugin : rien a faire.
    }
  };
}
