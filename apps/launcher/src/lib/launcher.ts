import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import { invoke } from "./tauri";

export type PlanReason = "missing" | "hash_mismatch";

export type PlanItem = {
  file: {
    path: string;
    sha256: string;
    size: number;
    url: string;
    required: boolean;
  };
  reason: PlanReason;
};

export type UpdatePreview = {
  version: string;
  minecraftVersion: string;
  plan: PlanItem[];
  bytesTotal: number;
  minLauncherVersion: string;
  launcherVersion: string;
  launcherOutdated: boolean;
};

export type UpdateStatus =
  | { kind: "up_to_date"; version: string }
  | { kind: "updated"; version: string; filesDownloaded: number }
  | { kind: "launcher_outdated"; current: string; required: string };

export type DownloadProgress = {
  completed: number;
  total: number;
  bytesDownloaded: number;
  bytesTotal: number;
  currentFile: string | null;
};

export async function checkUpdate(): Promise<UpdatePreview> {
  return invoke<UpdatePreview>("launcher_check_update");
}

export async function applyUpdate(): Promise<UpdateStatus> {
  return invoke<UpdateStatus>("launcher_apply_update");
}

/** Souscrit aux events de progression du download. Retourne la fonction de cleanup. */
export async function onDownloadProgress(
  callback: (p: DownloadProgress) => void,
): Promise<UnlistenFn> {
  return listen<DownloadProgress>("manifest:progress", (e) => callback(e.payload));
}

// ──────────────────────────────────────────────────────
//  Lancement du jeu (Semaine 4)
// ──────────────────────────────────────────────────────

export type LaunchedGame = {
  pid: number;
  javaPath: string;
};

export type TamperingEvent = {
  kind: "file_added" | "file_modified" | "file_removed";
  paths: string[];
};

export async function launchGame(): Promise<LaunchedGame> {
  return invoke<LaunchedGame>("launcher_launch_game");
}

export async function stopGame(): Promise<void> {
  await invoke<void>("launcher_stop_game");
}

export async function onGameStarted(
  cb: (g: LaunchedGame) => void,
): Promise<UnlistenFn> {
  return listen<LaunchedGame>("game:started", (e) => cb(e.payload));
}

export async function onGameExited(cb: (code: number) => void): Promise<UnlistenFn> {
  return listen<number>("game:exited", (e) => cb(e.payload));
}

export async function onGameStdout(cb: (line: string) => void): Promise<UnlistenFn> {
  return listen<string>("game:stdout", (e) => cb(e.payload));
}

export async function onGameStderr(cb: (line: string) => void): Promise<UnlistenFn> {
  return listen<string>("game:stderr", (e) => cb(e.payload));
}

export async function onTampering(cb: (t: TamperingEvent) => void): Promise<UnlistenFn> {
  return listen<TamperingEvent>("integrity:tampering", (e) => cb(e.payload));
}
