import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  AlertCircle,
  ChevronDown,
  RefreshCw,
  Server,
  ShieldAlert,
  Wifi,
  WifiOff,
  X,
} from "lucide-react";
import { pingHealth } from "../../lib/network-status";
import { useNetworkStore } from "../../stores/network-store";

// Banner slide-down depuis le haut de la zone main. Deux variantes :
//   - danger : « Reborn est en mode hors-ligne » (status === "offline" et
//     dismissed === false)
//   - success : « Connexion rétablie » (justRecovered === true, remis a
//     faux apres 4 s)
// Affiche au-dessus du contenu, en haut du main. La drag-region du shell
// (App.tsx) reste cliquable au-dessus parce qu'elle est en z-40 et le
// banner en z-30 ; les boutons sont plus bas que la drag-region.
//
// Le sondage /health tourne tout seul (lib/network-status.ts, 12 s) ;
// « Réessayer » lance un sondage immediat. Le popover « Solutions » liste
// les causes possibles (informatif).

const PING_INTERVAL_S = 12;
const SUCCESS_BANNER_TTL_MS = 4_000;

const FOCUS_RING =
  "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-current";

export function OfflineBanner() {
  const status = useNetworkStore((s) => s.status);
  const justRecovered = useNetworkStore((s) => s.justRecovered);
  const dismissed = useNetworkStore((s) => s.dismissed);
  const cause = useNetworkStore((s) => s.cause);
  const setDismissed = useNetworkStore((s) => s.setDismissed);
  const [popoverOpen, setPopoverOpen] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [retryFailedAt, setRetryFailedAt] = useState<number | null>(null);
  const successTimer = useRef<number | null>(null);

  useEffect(
    () => () => {
      if (successTimer.current !== null) window.clearTimeout(successTimer.current);
    },
    [],
  );

  // Connexion revenue : le prochain bandeau repart du message de base.
  useEffect(() => {
    if (status === "online") setRetryFailedAt(null);
  }, [status]);

  const visible =
    (status === "offline" && !dismissed) || (status === "online" && justRecovered);
  const variant: "danger" | "success" =
    status === "online" && justRecovered ? "success" : "danger";
  const isSuccess = variant === "success";

  async function retryNow() {
    if (retrying) return;
    setRetrying(true);
    setRetryFailedAt(null);
    const { ok, cause: failure } = await pingHealth();
    const store = useNetworkStore.getState();
    if (ok) {
      store.setStatus("online", { justRecovered: store.status === "offline" });
      if (successTimer.current !== null) window.clearTimeout(successTimer.current);
      successTimer.current = window.setTimeout(() => {
        const cur = useNetworkStore.getState();
        if (cur.status === "online" && cur.justRecovered) {
          cur.setStatus("online", { justRecovered: false });
        }
        successTimer.current = null;
      }, SUCCESS_BANNER_TTL_MS);
      setPopoverOpen(false);
    } else {
      store.setStatus("offline", { cause: failure });
      setRetryFailedAt(Date.now());
    }
    setRetrying(false);
  }

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ y: -44, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: -44, opacity: 0 }}
          transition={{ type: "spring", stiffness: 380, damping: 30 }}
          className={[
            "no-drag relative z-30 ml-4 mr-[80px] mt-2 flex items-center gap-3 rounded-md border px-4 py-2 text-sm",
            isSuccess
              ? "border-[var(--color-success)]/40 bg-[var(--color-success-soft)]/40 text-[var(--color-success)]"
              : "border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)]/40 text-[var(--color-danger)]",
          ].join(" ")}
          role="status"
        >
          <span className="flex-shrink-0">
            {isSuccess ? <Wifi className="h-4 w-4" /> : <WifiOff className="h-4 w-4" />}
          </span>
          <div className="min-w-0 flex-1 text-[var(--color-foreground)]">
            {isSuccess ? (
              <span>
                <b>Connexion rétablie.</b> Tous les services Reborn sont de nouveau disponibles.
              </span>
            ) : (
              <span>
                <b>Serveur Reborn injoignable.</b>{" "}
                {cause && <span className="text-[var(--color-foreground-subtle)]">Cause : {cause}. </span>}
                {retryFailedAt
                  ? "Toujours aucune réponse : vérifie ta connexion, ou le serveur est en maintenance."
                  : `Le launcher passe en mode hors-ligne · nouvel essai toutes les ${PING_INTERVAL_S} s.`}
              </span>
            )}
          </div>

          {!isSuccess && (
            <div className="flex flex-shrink-0 items-center gap-1">
              <button
                type="button"
                onClick={() => void retryNow()}
                disabled={retrying}
                className={`flex items-center gap-1 rounded px-2 py-1 text-xs text-[var(--color-foreground)] transition-colors hover:bg-white/5 disabled:cursor-wait disabled:opacity-70 ${FOCUS_RING}`}
              >
                <RefreshCw className={`h-3 w-3 ${retrying ? "animate-spin" : ""}`} />
                {retrying ? "Test…" : "Réessayer"}
              </button>
              <button
                type="button"
                onClick={() => setPopoverOpen((v) => !v)}
                aria-expanded={popoverOpen}
                className={`flex items-center gap-1 rounded px-2 py-1 text-xs text-[var(--color-foreground-subtle)] transition-colors hover:bg-white/5 hover:text-[var(--color-foreground)] ${FOCUS_RING}`}
              >
                <AlertCircle className="h-3 w-3" />
                Solutions
                <ChevronDown
                  className={["h-3 w-3 transition-transform", popoverOpen && "rotate-180"]
                    .filter(Boolean)
                    .join(" ")}
                />
              </button>
              <button
                type="button"
                onClick={() => setDismissed(true)}
                aria-label="Masquer le bandeau"
                className={`flex h-6 w-6 items-center justify-center rounded text-[var(--color-foreground-subtle)] transition-colors hover:bg-white/5 hover:text-[var(--color-foreground)] ${FOCUS_RING}`}
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          )}

          <AnimatePresence>
            {popoverOpen && !isSuccess && (
              <motion.div
                initial={{ opacity: 0, y: -6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -6 }}
                transition={{ duration: 0.16 }}
                className="absolute right-4 top-[calc(100%_+_6px)] w-[280px] rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface-elevated)] p-2 shadow-lg"
              >
                <div className="px-2 py-1 text-[10px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
                  Causes possibles
                </div>
                <SolutionItem
                  icon={<Wifi className="h-3.5 w-3.5" />}
                  title="Ta connexion Internet"
                  sub="Wi-Fi coupé, câble débranché, partage de connexion saturé"
                />
                <SolutionItem
                  icon={<ShieldAlert className="h-3.5 w-3.5" />}
                  title="Un VPN, un proxy ou un pare-feu"
                  sub="Désactive-les un instant, ou autorise le launcher Reborn"
                />
                <SolutionItem
                  icon={<Server className="h-3.5 w-3.5" />}
                  title="Le serveur Reborn"
                  sub="Maintenance ou panne : les annonces sont sur le Discord Reborn"
                />
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function SolutionItem({
  icon,
  title,
  sub,
}: {
  icon: React.ReactNode;
  title: string;
  sub: string;
}) {
  return (
    <div className="flex items-start gap-2 rounded px-2 py-1.5 text-[var(--color-foreground)]">
      <span className="mt-0.5 flex h-6 w-6 flex-shrink-0 items-center justify-center rounded bg-[var(--color-surface)] text-[var(--color-foreground-subtle)]">
        {icon}
      </span>
      <div className="flex-1 text-xs">
        <div>{title}</div>
        <div className="text-[10px] leading-snug text-[var(--color-foreground-muted)]">{sub}</div>
      </div>
    </div>
  );
}
