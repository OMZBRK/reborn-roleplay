import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { check, type Update } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import { Download, Loader2, RefreshCw, X } from "lucide-react";

/**
 * Detecte automatiquement les mises a jour du launcher au boot puis
 * toutes les 30min. Affiche une toast non-bloquante en bas a droite :
 * "Mise a jour X.Y.Z dispo" + bouton Install qui telecharge, verifie
 * la signature Ed25519 (cf tauri.conf.json plugins.updater.pubkey),
 * applique et redemarre le launcher.
 *
 * Le composant est monte une seule fois dans AuthenticatedLayout (pas
 * sur Login pour eviter de pop avant que l'utilisateur ait fini son
 * onboarding).
 *
 * Gracieux a tous les niveaux d'echec :
 *   - check() throw si endpoint down → swallow + retry plus tard
 *   - downloadAndInstall throw si signature invalide → toast erreur
 *   - relaunch peut prendre 1-2s, on garde "Redémarrage…" en attendant
 */
const CHECK_INTERVAL_MS = 30 * 60 * 1000;

type State =
  | { kind: "idle" }
  | { kind: "available"; update: Update }
  | { kind: "downloading"; update: Update; progress: number }
  | { kind: "ready"; update: Update }
  | { kind: "restarting" }
  | { kind: "error"; message: string };

export function UpdateChecker() {
  const [state, setState] = useState<State>({ kind: "idle" });
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function poll() {
      try {
        const update = await check();
        if (cancelled) return;
        if (update) {
          setState({ kind: "available", update });
          setDismissed(false);
        }
      } catch (err) {
        // En dev / endpoint down : on log silencieusement, pas de UI
        // pour pas spam.
        console.warn("[updater] check failed:", err);
      }
    }
    poll();
    const t = window.setInterval(poll, CHECK_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(t);
    };
  }, []);

  async function handleInstall() {
    if (state.kind !== "available") return;
    const update = state.update;
    setState({ kind: "downloading", update, progress: 0 });
    try {
      let downloaded = 0;
      let total = 0;
      await update.downloadAndInstall((event) => {
        if (event.event === "Started") {
          total = event.data.contentLength ?? 0;
        } else if (event.event === "Progress") {
          downloaded += event.data.chunkLength;
          const progress = total > 0 ? downloaded / total : 0;
          setState({ kind: "downloading", update, progress });
        } else if (event.event === "Finished") {
          setState({ kind: "ready", update });
        }
      });
      // L'install est faite — relaunch pour appliquer.
      setState({ kind: "restarting" });
      await relaunch();
    } catch (err) {
      const message =
        err instanceof Error ? err.message : String(err);
      setState({ kind: "error", message });
    }
  }

  const visible =
    !dismissed &&
    (state.kind === "available" ||
      state.kind === "downloading" ||
      state.kind === "ready" ||
      state.kind === "restarting" ||
      state.kind === "error");

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ opacity: 0, y: 20, scale: 0.95 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 10, scale: 0.95 }}
          transition={{ duration: 0.25 }}
          className="fixed bottom-6 right-6 z-50 w-[340px]"
        >
          <div
            className="rounded-[14px] border p-4 shadow-2xl"
            style={{
              background: "var(--color-surface-elevated)",
              borderColor: "var(--color-border-strong)",
              boxShadow:
                "0 12px 32px -4px rgba(0,0,0,.6), 0 0 0 1px var(--color-accent-soft)",
            }}
          >
            <div className="flex items-start gap-3">
              <div
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full"
                style={{
                  background: "var(--color-accent-soft)",
                  color: "var(--color-accent)",
                }}
              >
                <Download size={16} />
              </div>
              <div className="min-w-0 flex-1">
                <Title state={state} />
                <Body state={state} onInstall={handleInstall} />
              </div>
              {(state.kind === "available" || state.kind === "error") && (
                <button
                  type="button"
                  onClick={() => setDismissed(true)}
                  className="text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground)]"
                  title="Plus tard"
                >
                  <X size={14} />
                </button>
              )}
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function Title({ state }: { state: State }) {
  switch (state.kind) {
    case "available":
      return (
        <div className="text-sm font-medium">
          Mise à jour {state.update.version} disponible
        </div>
      );
    case "downloading":
      return (
        <div className="text-sm font-medium">Téléchargement en cours…</div>
      );
    case "ready":
      return <div className="text-sm font-medium">Installation prête</div>;
    case "restarting":
      return (
        <div className="text-sm font-medium">Redémarrage du launcher…</div>
      );
    case "error":
      return (
        <div
          className="text-sm font-medium"
          style={{ color: "var(--color-danger)" }}
        >
          Erreur de mise à jour
        </div>
      );
    default:
      return null;
  }
}

function Body({
  state,
  onInstall,
}: {
  state: State;
  onInstall: () => void;
}) {
  if (state.kind === "available") {
    const notes = state.update.body?.slice(0, 200);
    return (
      <>
        {notes && (
          <p
            className="mt-1 text-xs leading-relaxed"
            style={{ color: "var(--color-foreground-subtle)" }}
          >
            {notes}
          </p>
        )}
        <button
          type="button"
          onClick={onInstall}
          className="mt-3 w-full rounded-[8px] py-1.5 text-xs font-medium text-white transition-colors"
          style={{
            background: "var(--color-accent)",
          }}
        >
          Installer et redémarrer
        </button>
      </>
    );
  }
  if (state.kind === "downloading") {
    const pct = Math.round(state.progress * 100);
    return (
      <div className="mt-2">
        <div
          className="h-1.5 w-full overflow-hidden rounded-full"
          style={{ background: "var(--color-surface)" }}
        >
          <div
            className="h-full transition-[width]"
            style={{
              width: `${pct}%`,
              background: "var(--color-accent)",
            }}
          />
        </div>
        <div
          className="mt-1 text-[10px]"
          style={{ color: "var(--color-foreground-muted)" }}
        >
          {pct}%
        </div>
      </div>
    );
  }
  if (state.kind === "ready") {
    return (
      <div
        className="mt-1 flex items-center gap-2 text-xs"
        style={{ color: "var(--color-foreground-subtle)" }}
      >
        <Loader2 size={12} className="animate-spin" />
        Préparation du redémarrage…
      </div>
    );
  }
  if (state.kind === "restarting") {
    return (
      <div
        className="mt-1 flex items-center gap-2 text-xs"
        style={{ color: "var(--color-foreground-subtle)" }}
      >
        <RefreshCw size={12} className="animate-spin" />
        Le launcher va se relancer.
      </div>
    );
  }
  if (state.kind === "error") {
    return (
      <p
        className="mt-1 text-xs"
        style={{ color: "var(--color-foreground-subtle)" }}
      >
        {state.message}
      </p>
    );
  }
  return null;
}
