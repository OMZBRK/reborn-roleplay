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
