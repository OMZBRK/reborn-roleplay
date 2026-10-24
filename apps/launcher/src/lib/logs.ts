import { invoke, onTauriEvent, type UnlistenFn } from "./tauri";
import { useTauriEvent } from "../hooks/use-tauri-event";

// Logs du jeu (stdout + stderr de la JVM). Le backend les regroupe et les
// emet par paquets sur `game:log-batch` (au plus toutes les 250 ms), et
// garde un tampon circulaire de LOG_BUFFER_MAX lignes (partie en cours ou
// derniere partie) lisible a tout moment par launcher_logs_snapshot.
// Le tampon cote front vit dans stores/logs-store.ts.

export type LogStream = "out" | "err";
export type LogLevel = "info" | "warn" | "error" | "debug";

export type LogLine = {
  /** Horodatage de reception (ms depuis epoch). */
  t: number;
  stream: LogStream;
  level: LogLevel;
  text: string;
};

/** Payload de `game:log-batch`. */
export type LogBatchEvent = { lines: LogLine[] };

/** Taille du tampon circulaire (identique cote Rust et cote front). */
export const LOG_BUFFER_MAX = 5000;

export const LOG_LEVELS: readonly LogLevel[] = ["error", "warn", "info", "debug"];

/** Tampon complet du backend (partie en cours ou derniere partie). */
export async function getLogsSnapshot(): Promise<LogLine[]> {
  const lines = await invoke<LogLine[] | undefined>("launcher_logs_snapshot");
  return Array.isArray(lines) ? lines : [];
}

/** Ecrit le tampon dans <donnees>/logs/reborn-AAAA-MM-JJ_HH-MM-SS.log et
 *  renvoie le chemin du fichier. */
export async function exportLogs(): Promise<string> {
  return invoke<string>("launcher_logs_export");
}

/** Abonnement hors React aux paquets de logs. */
export async function onLogBatch(cb: (lines: LogLine[]) => void): Promise<UnlistenFn> {
  return onTauriEvent("game:log-batch", (p) => cb(Array.isArray(p?.lines) ? p.lines : []));
}

/** Abonnement React aux paquets de logs (desabonnement automatique). */
export function useLogBatch(cb: (lines: LogLine[]) => void, enabled = true): void {
  useTauriEvent(
    "game:log-batch",
    (p) => cb(Array.isArray(p?.lines) ? p.lines : []),
    enabled,
  );
}

const pad = (n: number, w = 2) => String(n).padStart(w, "0");

/** « 14:03:27 » (heure locale). */
export function formatLogTime(t: number): string {
  const d = new Date(t);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** Ligne prete a copier : « [14:03:27] [WARN] texte ». */
export function formatLogLine(line: LogLine): string {
  return `[${formatLogTime(line.t)}] [${line.level.toUpperCase()}] ${line.text}`;
}

/** Bloc de texte pret a copier dans le presse-papiers. */
export function formatLogLines(lines: readonly LogLine[]): string {
  return lines.map(formatLogLine).join("\n");
}
