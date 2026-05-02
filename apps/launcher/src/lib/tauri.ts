// Wrappers fins autour de @tauri-apps/api pour l'IPC frontend → backend Rust.
// Le frontend ne fait JAMAIS d'appels HTTP directs (cf §3.1 du plan) :
// tout passe par des #[tauri::command] cote Rust.

import { invoke as tauriInvoke } from "@tauri-apps/api/core";

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
