import { useEffect, useState } from "react";
import { check, type Update } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import {
  isPermissionGranted,
  requestPermission,
  sendNotification,
} from "@tauri-apps/plugin-notification";
import { useUpdateStore } from "../stores/update-store";

// Poll toutes les 30min — meme cadence que la version v1 de UpdateChecker
// (qui etait une toast bottom-right). Le check echoue silencieusement si
// l'endpoint est down (dev sans VPS, coupure reseau) ; le polling
// continuera et ramassera l'update quand l'API repond a nouveau.
const CHECK_INTERVAL_MS = 30 * 60 * 1000;

export type UpdaterState =
  | { kind: "idle" }
  | { kind: "available"; update: Update }
  | { kind: "downloading"; update: Update; progress: number; downloaded: number; total: number }
  | { kind: "installing"; update: Update }
  | { kind: "error"; message: string };

export type UseUpdater = {
  state: UpdaterState;
  install: () => Promise<void>;
  /** Reset state vers idle ET marque l'update comme ignoree (pulse logo). */
  postpone: () => void;
  /** Identique a postpone pour cette PR — on differenciera "ignore cette
   *  version" vs "plus tard" en B+ quand on persistera le flag par
   *  version. Cf TODO ci-dessous. */
  ignoreVersion: () => void;
};

// TODO(persist): differencier "plus tard" (re-prompt au prochain poll) vs
// "ignore cette version" (silencieux jusqu'a la prochaine version dispo).
// Necessite de persister une string version dans le store + comparer au
// `update.version` du prochain check. Pas dans le scope du chantier B.

// Persiste la version qu'on a deja annoncee via notification OS, pour ne pas
// re-spam le user a chaque poll de 30min sur la meme release.
const NOTIFIED_VERSION_KEY = "reborn:updater:notified-version";

async function maybeNotifyOs(update: Update): Promise<void> {
  try {
    const lastNotified = localStorage.getItem(NOTIFIED_VERSION_KEY);
    if (lastNotified === update.version) return; // deja signalee

    let granted = await isPermissionGranted();
    if (!granted) {
      const perm = await requestPermission();
      granted = perm === "granted";
    }
    if (!granted) return;

    sendNotification({
      title: "Reborn Launcher",
      body: `Mise a jour disponible ${update.version}\nUne nouvelle version est prete a etre telechargee.`,
    });
    localStorage.setItem(NOTIFIED_VERSION_KEY, update.version);
  } catch (err) {
    // Notif OS non bloquante. Le UpdateModal in-app fait deja le job
    // visuel, la notif est juste un plus pour les users qui ont le
    // launcher en arriere-plan.
    console.warn("[updater] notif OS skipped:", err);
  }
}

export function useUpdater(): UseUpdater {
  const [state, setState] = useState<UpdaterState>({ kind: "idle" });
  const setAvailable = useUpdateStore((s) => s.setAvailable);
  const setIgnored = useUpdateStore((s) => s.setIgnored);

  useEffect(() => {
    let cancelled = false;
    async function poll() {
      try {
        const update = await check();
        if (cancelled) return;
        if (update) {
          // Si l'utilisateur etait en cours d'install/download, on ne
          // reset PAS son state — le poll est un fond passif.
          setState((current) => {
            if (current.kind === "downloading" || current.kind === "installing") {
              return current;
            }
            return { kind: "available", update };
          });
          setAvailable(true);
          // Notification OS native (Windows toast bas-droite). Idempotente
          // grace au localStorage : une seule notif par version.
          void maybeNotifyOs(update);
          // Le polling re-detecte la meme version : on ne reset pas
          // l'ignored, l'utilisateur a deja choisi. Reset uniquement
          // quand une NOUVELLE version sort (cf TODO ci-dessus).
        }
      } catch (err) {
        // Pas de toast d'erreur — silent retry au prochain interval.
        console.warn("[updater] check failed:", err);
      }
    }
    void poll();
    const t = window.setInterval(poll, CHECK_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(t);
    };
  }, [setAvailable]);

  async function install(): Promise<void> {
    if (state.kind !== "available") return;
    const update = state.update;
    setState({ kind: "downloading", update, progress: 0, downloaded: 0, total: 0 });
    try {
      let downloaded = 0;
      let total = 0;
      await update.downloadAndInstall((event) => {
        if (event.event === "Started") {
          total = event.data.contentLength ?? 0;
          setState({ kind: "downloading", update, progress: 0, downloaded: 0, total });
        } else if (event.event === "Progress") {
          downloaded += event.data.chunkLength;
          const progress = total > 0 ? downloaded / total : 0;
          setState({ kind: "downloading", update, progress, downloaded, total });
        } else if (event.event === "Finished") {
          setState({ kind: "installing", update });
        }
      });
      // L'install termine — on relance le launcher. Cette ligne ne revient
      // jamais (relaunch ferme le process actuel).
      await relaunch();
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setState({ kind: "error", message });
    }
  }

  function postpone(): void {
    setState({ kind: "idle" });
    setIgnored(true);
  }

  function ignoreVersion(): void {
    // Meme effet pour cette PR (cf TODO).
    setState({ kind: "idle" });
    setIgnored(true);
  }

  return { state, install, postpone, ignoreVersion };
}
