import { useEffect, useState } from "react";
import { check, type Update } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import {
  isPermissionGranted,
  requestPermission,
  sendNotification,
} from "@tauri-apps/plugin-notification";
import { useUpdateStore } from "../stores/update-store";
import { useLaunchStore } from "../stores/launch-store";

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
  /** Reset state vers idle, silence la version 24 h (pulse logo conservee). */
  postpone: () => void;
  /** Silence la version proposee jusqu'a la sortie d'une plus recente. */
  ignoreVersion: () => void;
};

// Choix de l'utilisateur sur la modale, persistes pour qu'elle ne revienne
// pas a chaque demarrage ni a chaque poll de 30 min :
//   - "Plus tard"          : silence SNOOZE_MS sur cette version ;
//   - "Ignorer la version" : silence jusqu'a la sortie d'une version plus recente.
// Dans les deux cas la pulse du logo sidebar reste comme rappel doux.
const SNOOZE_KEY = "reborn:updater:snooze"; // JSON { version, until }
const IGNORED_VERSION_KEY = "reborn:updater:ignored-version";
const SNOOZE_MS = 24 * 60 * 60 * 1000;

function isSilenced(version: string): boolean {
  try {
    if (localStorage.getItem(IGNORED_VERSION_KEY) === version) return true;
    const raw = localStorage.getItem(SNOOZE_KEY);
    if (!raw) return false;
    const snooze = JSON.parse(raw) as { version?: string; until?: number };
    return snooze.version === version && (snooze.until ?? 0) > Date.now();
  } catch {
    return false;
  }
}

function silence(version: string, mode: "snooze" | "ignore"): void {
  try {
    if (mode === "ignore") {
      localStorage.setItem(IGNORED_VERSION_KEY, version);
    } else {
      localStorage.setItem(
        SNOOZE_KEY,
        JSON.stringify({ version, until: Date.now() + SNOOZE_MS }),
      );
    }
  } catch {
    // localStorage indisponible : la modale reviendra au prochain poll.
  }
}

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
  const openNonce = useUpdateStore((s) => s.openNonce);

  useEffect(() => {
    let cancelled = false;
    async function poll() {
      try {
        const update = await check();
        if (cancelled) return;
        if (!update) {
          // Deja a jour (204) : on eteint la pulse si elle trainait.
          setAvailable(false);
          return;
        }
        setAvailable(true);
        // Version mise en "plus tard"/"ignoree", ou jeu en cours : pas de
        // modale, la pulse du logo suffit. Le poll suivant reessaiera.
        const inGame = useLaunchStore.getState().phase === "running";
        if (isSilenced(update.version) || inGame) {
          setIgnored(true);
          return;
        }
        // Si l'utilisateur etait en cours d'install/download, on ne
        // reset PAS son state — le poll est un fond passif.
        setState((current) => {
          if (current.kind === "downloading" || current.kind === "installing") {
            return current;
          }
          return { kind: "available", update };
        });
        // Notification OS native (Windows toast bas-droite). Idempotente
        // grace au localStorage : une seule notif par version.
        void maybeNotifyOs(update);
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
  }, [setAvailable, setIgnored]);

  // Reouverture explicite (clic sur le logo) : on ignore le silence.
  useEffect(() => {
    if (openNonce === 0) return;
    let cancelled = false;
    void check()
      .then((update) => {
        if (cancelled) return;
        if (!update) {
          setAvailable(false);
          return;
        }
        setState((current) =>
          current.kind === "idle" ? { kind: "available", update } : current,
        );
      })
      .catch((err) => console.warn("[updater] reopen check failed:", err));
    return () => {
      cancelled = true;
    };
  }, [openNonce, setAvailable]);

  async function install(): Promise<void> {
    // Recupere l'objet Update a installer :
    //   - depuis "available"/"downloading" : on reutilise celui en main.
    //   - depuis "error" (retry apres coupure reseau) : l'objet precedent
    //     peut etre dans un etat invalide, on re-check() pour repartir propre.
    //     C'est ce qui debloque le "Reessayer" quand la connexion est tombee
    //     en plein telechargement (avant : le garde !== "available" faisait
    //     que le bouton ne relancait jamais rien).
    let update: Update | null = null;
    if (state.kind === "available" || state.kind === "downloading") {
      update = state.update;
    } else if (state.kind === "error") {
      try {
        update = await check();
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        setState({ kind: "error", message: `Vérification impossible : ${message}` });
        return;
      }
      if (!update) {
        // Plus rien a installer (deja a jour, ou l'update a ete retiree).
        setState({ kind: "idle" });
        return;
      }
    }
    if (!update) return;
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

  function currentVersion(): string | null {
    if (state.kind === "idle" || state.kind === "error") return null;
    return state.update.version;
  }

  function postpone(): void {
    const v = currentVersion();
    if (v) silence(v, "snooze");
    setState({ kind: "idle" });
    setIgnored(true);
  }

  function ignoreVersion(): void {
    // Depuis l'etat "error" l'objet Update n'est plus en main : on
    // re-interroge pour connaitre la version a ignorer.
    const v = currentVersion();
    if (v) {
      silence(v, "ignore");
    } else {
      void check()
        .then((u) => u && silence(u.version, "ignore"))
        .catch(() => {});
    }
    setState({ kind: "idle" });
    setIgnored(true);
  }

  return { state, install, postpone, ignoreVersion };
}
