// Accès aux captures locales + partage social, via les #[tauri::command]
// Rust (`content::screenshots_*` / `content::shots_*`). Le launcher et le
// mod-hud partagent le même dossier de jeu, donc les captures et les favoris
// (`reborn/screenshots-fav.json`) sont communs à la galerie in-game.

import type { CSSProperties } from "react";
import { convertFileSrc } from "@tauri-apps/api/core";
import { invoke } from "./tauri";
import type { ScreenshotRecord } from "./screenshots-mock";

// ── Types miroir des structs Rust ────────────────────────────────────

export type ScreenshotItem = {
  fileName: string;
  path: string;
  modifiedMs: number;
  sizeBytes: number;
  width: number | null;
  height: number | null;
  favorite: boolean;
};

export type ShotAuthor = {
  id: string;
  name: string;
  avatarUrl: string | null;
};

export type ShotView = {
  id: string;
  url: string;
  caption: string | null;
  width: number | null;
  height: number | null;
  likeCount: number;
  likedByMe: boolean;
  createdAt: string;
  author: ShotAuthor;
};

export type ShotFeed = { items: ShotView[]; nextCursor: string | null };

// ── Commandes ────────────────────────────────────────────────────────

export async function listScreenshots(): Promise<ScreenshotItem[]> {
  return (await invoke<ScreenshotItem[]>("screenshots_list")) ?? [];
}

export async function openScreenshotsFolder(): Promise<void> {
  await invoke("screenshots_open_folder");
}

export async function toggleScreenshotFavorite(
  fileName: string,
): Promise<string[]> {
  return (
    (await invoke<string[]>("screenshots_toggle_favorite", { fileName })) ?? []
  );
}

export async function deleteScreenshot(fileName: string): Promise<void> {
  await invoke("screenshots_delete", { fileName });
}

export async function shareScreenshot(
  fileName: string,
  caption?: string,
): Promise<ShotView> {
  return invoke<ShotView>("screenshots_share", { fileName, caption });
}

export async function fetchShotFeed(cursor?: string): Promise<ShotFeed> {
  return (
    (await invoke<ShotFeed>("shots_feed", { cursor })) ?? {
      items: [],
      nextCursor: null,
    }
  );
}

export async function toggleShotLike(
  shotId: string,
): Promise<{ liked: boolean; likeCount: number }> {
  return invoke("shots_toggle_like", { shotId });
}

/**
 * Style d'arrière-plan d'une vignette. Utilise `background-image` (longhand)
 * quand l'image réelle est dispo pour que le `background-size: cover` des
 * classes CSS continue de s'appliquer ; sinon le dégradé mock (`art`).
 */
export function shotBackground(shot: ScreenshotRecord): CSSProperties {
  return shot.src
    ? { backgroundImage: `url("${shot.src}")` }
    : { background: shot.art };
}

// ── Mapping vers le modèle d'affichage ───────────────────────────────

const DATE_FMT = new Intl.DateTimeFormat("fr-FR", {
  day: "2-digit",
  month: "short",
  year: "numeric",
});
const TIME_FMT = new Intl.DateTimeFormat("fr-FR", {
  hour: "2-digit",
  minute: "2-digit",
});

function humanSize(bytes: number): string {
  if (bytes >= 1_000_000) return `${(bytes / 1_000_000).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(bytes / 1000))} KB`;
}

/**
 * Transforme un `ScreenshotItem` disque en `ScreenshotRecord` d'affichage.
 * Les captures locales n'ont ni serveur ni joueur associé → champs neutres.
 * `src` est l'URL asset (convertFileSrc) rendue par les vignettes.
 */
export function toRecord(item: ScreenshotItem): ScreenshotRecord {
  const d = new Date(item.modifiedMs);
  return {
    id: item.fileName,
    fileName: item.fileName,
    src: convertFileSrc(item.path),
    title: item.fileName.replace(/\.(png|jpe?g)$/i, ""),
    server: "",
    player: "",
    date: DATE_FMT.format(d),
    time: TIME_FMT.format(d),
    size: humanSize(item.sizeBytes),
    resolution:
      item.width && item.height ? `${item.width} × ${item.height}` : "—",
    pinned: item.favorite,
    modifiedMs: item.modifiedMs,
    sizeBytes: item.sizeBytes,
    art: "linear-gradient(135deg, rgba(30,32,44,0.9), rgba(10,11,16,0.95))",
  };
}
