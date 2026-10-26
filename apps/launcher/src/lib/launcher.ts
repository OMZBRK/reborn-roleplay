import { invoke, onTauriEvent, type UnlistenFn } from "./tauri";

// Wrappers types des commandes et evenements du lancement. Les erreurs
// rejetees par ces fonctions sont des objets { kind, message } : les
// afficher avec formatIpcError (lib/errors.ts), jamais `err.message ?? ...`.
// Les logs du jeu (game:log-batch, snapshot, export) vivent dans lib/logs.ts.

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
  return onTauriEvent("manifest:progress", callback);
}

// ──────────────────────────────────────────────────────
//  Lancement du jeu (Semaine 4)
// ──────────────────────────────────────────────────────

/** Retour de launcher_launch_game / launcher_launch_second_instance, et
 *  payload de l'event `game:started` (LaunchResult cote Rust). */
export type LaunchedGame = {
  pid: number;
  javaPath: string;
  minecraftVersion: string;
  fabricVersion: string;
  /** Portee du jeton de jeu emis par l'API : "douane" = hall de la douane
   *  seulement (candidat), "play" = jeu normal ; null = API d'avant le 25/10. */
  scope: "play" | "douane" | null;
  /** Serveur vise par le jeton (claim `aud` : "prod", "dev"…), null si
   *  l'API n'en emet pas. */
  serverId: string | null;
};

/** Payload de `game:window-ready` : vide, emis une fois quand la fenetre
 *  Minecraft est reellement ouverte (detectee dans les logs). */
export type GameWindowReadyEvent = Record<string, never>;

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

/** Lance le jeu. La barriere d'acces lit la PHASE de la candidature (cf
 *  lib/candidature.ts › lireAccesJeu, meme decision) : whiteliste ou staff →
 *  jeu normal ; candidat dans la fenetre d'entree de sa seance → douane ;
 *  sinon rejet { kind, message } avec kind = candidature_required,
 *  douane_hors_jeu, douane_not_open, douane_closed, access_check (phase
 *  illisible), session_expired, ou not_whitelisted (API d'avant la
 *  candidature v2). */
export async function launchGame(): Promise<LaunchedGame> {
  return invoke<LaunchedGame>("launcher_launch_game");
}

/** Retour de launcher_prepare_game (PreparedGame cote Rust). */
export type PreparedGame = {
  minecraftVersion: string;
  fabricVersion: string;
};

/** « Préparer le jeu » : installe Java, bibliothèques, ressources et Fabric
 *  SANS jeton ni démarrage (candidat avant sa séance de douane : le premier
 *  téléchargement se fait hors de la fenêtre d'entrée). Progression sur
 *  `launch:progress`, en 5 étapes. */
export async function prepareGame(): Promise<PreparedGame> {
  return invoke<PreparedGame>("launcher_prepare_game");
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
  return onTauriEvent("launch:progress", cb);
}

/** Tue le jeu en cours (arret volontaire : pas de rapport de crash). */
export async function stopGame(): Promise<void> {
  await invoke<void>("launcher_stop_game");
}

export async function onGameStarted(
  cb: (g: LaunchedGame) => void,
): Promise<UnlistenFn> {
  return onTauriEvent("game:started", cb);
}

export async function onGameWindowReady(cb: () => void): Promise<UnlistenFn> {
  return onTauriEvent("game:window-ready", () => cb());
}

/** `code` = code de sortie de la JVM (-1 si illisible). */
export async function onGameExited(cb: (code: number) => void): Promise<UnlistenFn> {
  return onTauriEvent("game:exited", cb);
}

/** Payload de l'event `game:crashed` (JVM sortie non-zero non sollicitee). */
export type GameCrashedEvent = {
  /** Code de sortie, ou null si tue par signal / illisible. */
  exitCode: number | null;
  /** Journal complet de la partie (stdout + stderr) : logs/last-crash.log,
   *  ou last-stderr.txt si le journal etait vide. */
  stderrPath: string;
  /** ~100 dernieres lignes du journal, stdout ET stderr (le rapport de crash
   *  Minecraft sort sur stdout), pour affichage immediat. */
  stderrTail: string;
};

/** Souscrit a l'event `game:crashed`. Retourne la fonction de cleanup. */
export async function onGameCrashed(
  cb: (c: GameCrashedEvent) => void,
): Promise<UnlistenFn> {
  return onTauriEvent("game:crashed", cb);
}

/** Etat de la partie cote backend : sert a se resynchroniser si la WebView
 *  est rechargee en pleine partie (les events game:* deja emis sont perdus). */
export type GameStatus = { running: boolean; pid: number | null };

export async function getGameStatus(): Promise<GameStatus | undefined> {
  return invoke<GameStatus | undefined>("launcher_game_status");
}

export async function onTampering(cb: (t: TamperingEvent) => void): Promise<UnlistenFn> {
  return onTauriEvent("integrity:tampering", cb);
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
    | "MC_INVALID_UUID"
    | "MC_AUTH_INVALID"
    | "MC_CRASH_REPORT"
    | "AUDIO_UNAVAILABLE"
    | "GPU_DRIVER_ISSUE"
    | (string & {});
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

/** Payload de `mods:purged` (meme forme pour toutes les purges : mods
 *  incompatibles avec la version MC et orphelins du manifest). */
export type ModsPurgedEvent = {
  removed: string[];
  targetMcVersion: string;
};

export async function onGameDiagnostic(
  cb: (d: GameDiagnostic) => void,
): Promise<UnlistenFn> {
  return onTauriEvent("game:diagnostic", cb);
}

export async function onModsPurged(
  cb: (e: ModsPurgedEvent) => void,
): Promise<UnlistenFn> {
  return onTauriEvent("mods:purged", cb);
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

/** « Réparer » : efface les témoins de vérification ; le « Jouer » suivant
 *  revérifie par hash Java, bibliothèques, ressources et Fabric. */
export async function requestFullVerify(): Promise<void> {
  await invoke<void>("launcher_repair");
}

export type CleanResult = { removed: string[]; freedMb: number };

export async function cleanObsolete(): Promise<CleanResult> {
  return invoke<CleanResult>("launcher_clean_obsolete");
}
