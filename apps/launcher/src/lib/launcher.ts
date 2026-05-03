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

// ──────────────────────────────────────────────────────
//  Diagnostics auto + nettoyage des mods
// ──────────────────────────────────────────────────────

export type DiagnosticSeverity = "warning" | "error" | "fatal";

export type GameDiagnostic = {
  code:
    | "MOD_MC_VERSION_MISMATCH"
    | "FABRIC_MOD_RESOLUTION_FAILED"
    | "JVM_OUT_OF_MEMORY"
    | "JVM_MAIN_CLASS_NOT_FOUND"
    | "SERVER_UNREACHABLE"
    | "MC_AUTH_INVALID"
    | "GPU_DRIVER_ISSUE"
    | string;
  severity: DiagnosticSeverity;
  message: string;
  hint: string | null;
  details: string | null;
};

export type ModEntry = {
  fileName: string;
  absolutePath: string;
  modId: string | null;
  modVersion: string | null;
  minecraftConstraint: string | null;
  sizeBytes: number;
  incompatibleWithTarget: boolean;
};

export type ModsPurgedEvent = {
  removed: string[];
  targetMcVersion: string;
};

export async function onGameDiagnostic(
  cb: (d: GameDiagnostic) => void,
): Promise<UnlistenFn> {
  return listen<GameDiagnostic>("game:diagnostic", (e) => cb(e.payload));
}

export async function onModsPurged(
  cb: (e: ModsPurgedEvent) => void,
): Promise<UnlistenFn> {
  return listen<ModsPurgedEvent>("mods:purged", (e) => cb(e.payload));
}

export async function listMods(): Promise<ModEntry[]> {
  return invoke<ModEntry[]>("launcher_mods_list");
}

export async function purgeIncompatibleMods(): Promise<string[]> {
  return invoke<string[]>("launcher_mods_purge");
}
