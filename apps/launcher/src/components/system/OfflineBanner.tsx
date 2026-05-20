import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  AlertCircle,
  ChevronDown,
  RefreshCw,
  Server,
  Wifi,
  WifiOff,
  X,
} from "lucide-react";
import { useNetworkStore } from "../../stores/network-store";

// Banner slide-down depuis le haut de la zone main. Deux variantes :
//   - danger : "Reborn est en mode hors-ligne" (status === "offline" et
//     dismissed === false)
//   - success : "Connexion retablie" (justRecovered === true, auto-clear
//     apres 4s via lib/network-status.ts)
// Affiche au-dessus du contenu, en haut du main. La drag-region du shell
// (App.tsx) reste cliquable au-dessus parce qu'elle est en z-40 et le
// banner en z-30 ; mais le bouton "Solutions" est positionne plus bas
// que la drag-region donc pas de conflit.
//
// Popover "Solutions" : 3 hints non-actionnables pour cette PR (les
// boutons sont des liens d'info uniquement, pas de retry actif —
// le ping continue tout seul toutes les 12s).
export function OfflineBanner() {
  const status = useNetworkStore((s) => s.status);
  const justRecovered = useNetworkStore((s) => s.justRecovered);
  const dismissed = useNetworkStore((s) => s.dismissed);
  const setDismissed = useNetworkStore((s) => s.setDismissed);
  const [popoverOpen, setPopoverOpen] = useState(false);

  const visible =
    (status === "offline" && !dismissed) || (status === "online" && justRecovered);
  const variant: "danger" | "success" =
    status === "online" && justRecovered ? "success" : "danger";
  const isSuccess = variant === "success";

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ y: -44, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: -44, opacity: 0 }}
          transition={{ type: "spring", stiffness: 380, damping: 30 }}
          className={[
            "no-drag relative z-30 mx-4 mt-1 flex items-center gap-3 rounded-md border px-4 py-2 text-sm",
            isSuccess
              ? "border-[var(--color-success)]/40 bg-[var(--color-success-soft)]/40 text-[var(--color-success)]"
              : "border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)]/40 text-[var(--color-danger)]",
          ].join(" ")}
          role="status"
        >
          <span className="flex-shrink-0">
            {isSuccess ? <Wifi className="h-4 w-4" /> : <WifiOff className="h-4 w-4" />}
          </span>
          <div className="flex-1 text-[var(--color-foreground)]">
            {isSuccess ? (
              <span>
                <b>Connexion rétablie.</b> Tous les services Reborn sont de nouveau disponibles.
              </span>
            ) : (
              <span>
                Reborn est en <b>mode hors-ligne.</b> Aucune connexion disponible · nouvelle tentative dans 12s.
              </span>
            )}
          </div>

          {!isSuccess && (
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setPopoverOpen((v) => !v)}
                aria-expanded={popoverOpen}
                className="flex items-center gap-1 rounded px-2 py-1 text-xs text-[var(--color-foreground-subtle)] transition-colors hover:bg-white/5 hover:text-[var(--color-foreground)]"
              >
                <AlertCircle className="h-3 w-3" />
                Solutions
                <ChevronDown
                  className={[
                    "h-3 w-3 transition-transform",
                    popoverOpen && "rotate-180",
                  ]
                    .filter(Boolean)
                    .join(" ")}
                />
              </button>
              <button
                type="button"
                onClick={() => setDismissed(true)}
                aria-label="Masquer le bandeau"
                className="flex h-6 w-6 items-center justify-center rounded text-[var(--color-foreground-subtle)] transition-colors hover:bg-white/5 hover:text-[var(--color-foreground)]"
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
                className="absolute right-4 top-[calc(100%+6px)] w-[260px] rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface-elevated)] p-2 shadow-lg"
              >
                <div className="px-2 py-1 text-[10px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
                  Solutions suggérées
                </div>
                <SolutionItem
                  icon={<Wifi className="h-3.5 w-3.5" />}
                  title="Vérifier votre connexion"
                  sub="Wi-Fi, VPN, proxy"
                />
                <SolutionItem
                  icon={<RefreshCw className="h-3.5 w-3.5" />}
                  title="Réessayer maintenant"
                  sub="Ping automatique en cours"
                />
                <SolutionItem
                  icon={<Server className="h-3.5 w-3.5" />}
                  title="Voir le statut du serveur"
                  sub="status.reborn-rp.com"
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
    <div className="flex items-center gap-2 rounded px-2 py-1.5 text-[var(--color-foreground)] hover:bg-[var(--color-surface-overlay)]">
      <span className="flex h-6 w-6 items-center justify-center rounded bg-[var(--color-surface)] text-[var(--color-foreground-subtle)]">
        {icon}
      </span>
      <div className="flex-1 text-xs">
        <div>{title}</div>
        <div className="text-[10px] text-[var(--color-foreground-muted)]">{sub}</div>
      </div>
    </div>
  );
}
