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

// ───────────── Mods optionnels (toggle UI dans Mods.tsx) ─────────────

export type OptionalMod = {
  filename: string;
  sizeBytes: number;
  enabled: boolean;
  installed: boolean;
};

export async function listOptionalMods(): Promise<OptionalMod[]> {
  return invoke<OptionalMod[]>("launcher_list_optional_mods");
}

export async function setModPref(filename: string, enabled: boolean): Promise<void> {
  await invoke<void>("launcher_set_mod_pref", { filename, enabled });
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

/** Progression du flux de lancement (etapes 1..6), event `launch:progress`.
 *  `current`/`total` ne sont remplis que pour l'etape assets (la plus longue). */
export type LaunchProgress = {
  step: number;
  totalSteps: number;
  label: string;
  current: number | null;
  total: number | null;
};

export async function launchGame(): Promise<LaunchedGame> {
  return invoke<LaunchedGame>("launcher_launch_game");
}

/** Lance une 2e instance de jeu (dev, staff-only) avec un autre compte
 *  Microsoft déjà enregistré. Fire-and-forget côté backend : pas d'events de
 *  cycle de vie, le staff ferme la fenêtre à la main. `altUuid` = minecraftUuid
 *  du compte alternatif (carousel). */
export async function launchSecondInstance(altUuid: string): Promise<LaunchedGame> {
  return invoke<LaunchedGame>("launcher_launch_second_instance", { altUuid });
}

export async function onLaunchProgress(
  cb: (p: LaunchProgress) => void,
): Promise<UnlistenFn> {
  return listen<LaunchProgress>("launch:progress", (e) => cb(e.payload));
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

/** Payload de l'event `game:crashed` (JVM sortie non-zero non sollicitee). */
export type GameCrashedEvent = {
  /** Code de sortie, ou null si tue par signal / illisible. */
  exitCode: number | null;
  /** Chemin du fichier last-stderr.txt complet. */
  stderrPath: string;
  /** Dernieres ~100 lignes de stderr, pour affichage immediat. */
  stderrTail: string;
};

/** Souscrit a l'event `game:crashed`. Retourne la fonction de cleanup. */
export async function onGameCrashed(
  cb: (c: GameCrashedEvent) => void,
): Promise<UnlistenFn> {
  return listen<GameCrashedEvent>("game:crashed", (e) => cb(e.payload));
}

/** Lit le contenu (plafonne a 200 Ko, fin du fichier) d'un log de crash. */
export async function readCrashLog(path: string): Promise<string> {
  return invoke<string>("read_crash_log", { path });
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

// ── Onglet Paramètres › Jeu : infos install + actions ──────────────
export type GameInfo = {
  installDir: string;
  mcVersion: string;
  diskFreeGb: number;
  diskTotalGb: number;
};

export async function getGameInfo(): Promise<GameInfo> {
  return invoke<GameInfo>("launcher_game_info");
}

export async function openInstallDir(): Promise<void> {
  await invoke<void>("launcher_open_install_dir");
}

export async function reinstallAll(): Promise<void> {
  await invoke<void>("launcher_reinstall_all");
}
