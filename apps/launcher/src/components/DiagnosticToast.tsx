import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  AlertOctagon,
  AlertTriangle,
  ChevronDown,
  Package,
  ScrollText,
  X,
} from "lucide-react";
import { useRef, useState } from "react";
import { useNavigate } from "react-router";
import { useTauriEvent } from "../hooks/use-tauri-event";
import type { GameDiagnostic, ModsPurgedEvent } from "../lib/launcher";
import { openLogsPanel } from "./game-log/logs-panel-store";

// Toasts in-app du LogAnalyzer (game:diagnostic, emis pendant la partie) et
// des purges de mods (mods:purged, emis avant le lancement ou par la
// verification des fichiers). Monte une fois dans le layout authentifie.
//
// Un meme code n'apparait qu'une fois par partie (Fabric loggue chaque
// erreur 3-4 fois) ; a chaque nouvelle partie, les diagnostics de la
// precedente sont retires et peuvent reapparaitre.

type Item = {
  id: string;
  diag: GameDiagnostic;
  expanded: boolean;
};

const PURGED = "MODS_PURGED";
/** Codes reglés depuis l'onglet Mods (bouton « Purger les mods incompatibles »). */
const MODS_CODES = new Set<string>([PURGED, "MOD_MC_VERSION_MISMATCH", "FABRIC_MOD_RESOLUTION_FAILED"]);
/** Au-dela, les plus anciens sortent (la pile ne doit pas couvrir l'ecran). */
const MAX_ITEMS = 4;

const FOCUS_RING =
  "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-current";

// Titre lisible par code (le code reste affiche en petit pour le staff).
const TITLES: Record<string, string> = {
  MOD_MC_VERSION_MISMATCH: "Mod incompatible",
  FABRIC_MOD_RESOLUTION_FAILED: "Mods incompatibles",
  JVM_OUT_OF_MEMORY: "Mémoire insuffisante",
  JVM_MAIN_CLASS_NOT_FOUND: "Installation incomplète",
  SERVER_UNREACHABLE: "Serveur injoignable",
  MC_AUTH_INVALID: "Session Minecraft refusée",
  MC_INVALID_UUID: "Identifiant de joueur invalide",
  GPU_DRIVER_ISSUE: "Carte graphique",
  MC_CRASH_REPORT: "Minecraft a planté",
  AUDIO_UNAVAILABLE: "Son indisponible",
  [PURGED]: "Mods retirés",
};

let nextId = 1;

function purgedDiagnostic(e: ModsPurgedEvent): GameDiagnostic | null {
  const removed = Array.isArray(e?.removed) ? e.removed : [];
  if (removed.length === 0) return null;
  const n = removed.length;
  const s = n > 1 ? "s" : "";
  const version = e.targetMcVersion ? ` avec Minecraft ${e.targetMcVersion}` : "";
  return {
    code: PURGED,
    severity: "warning",
    message: `${n} mod${s} retiré${s} du dossier mods : absent${s} de la liste du serveur ou incompatible${s}${version}.`,
    hint: "Seuls les mods fournis par Reborn sont acceptés. Les mods optionnels s'activent dans l'onglet Mods.",
    // Le backend envoie des noms de fichiers ; on tolere d'anciens chemins.
    details: removed.map((p) => `· ${p.split(/[\\/]/).pop() ?? p}`).join("\n"),
  };
}

export function DiagnosticToast() {
  const navigate = useNavigate();
  const [items, setItems] = useState<Item[]>([]);
  const seen = useRef<Set<string>>(new Set());

  function push(diag: GameDiagnostic) {
    setItems((prev) => {
      const next = [...prev, { id: `d-${nextId++}`, diag, expanded: false }];
      return next.length > MAX_ITEMS ? next.slice(next.length - MAX_ITEMS) : next;
    });
  }

  // Nouvelle partie : on oublie les diagnostics de la precedente. Les purges
  // restent (celle de ce lancement arrive juste avant game:started).
  useTauriEvent("game:started", () => {
    seen.current.clear();
    setItems((prev) => prev.filter((i) => i.diag.code === PURGED));
  });

  // Crash : le modal de rapport prend le relais du toast « Minecraft a
  // planté », qui répéterait son résumé et resterait par-dessus l'accueil
  // une fois le modal fermé.
  useTauriEvent("game:crashed", () => {
    setItems((prev) => prev.filter((i) => i.diag.code !== "MC_CRASH_REPORT"));
  });

  useTauriEvent("game:diagnostic", (d) => {
    if (!d?.code || seen.current.has(d.code)) return;
    seen.current.add(d.code);
    push(d);
  });

  useTauriEvent("mods:purged", (e) => {
    const diag = purgedDiagnostic(e);
    if (diag) push(diag);
  });

  function dismiss(id: string) {
    setItems((prev) => {
      const target = prev.find((i) => i.id === id);
      // Un diagnostic ferme peut reapparaitre si le probleme se reproduit.
      if (target) seen.current.delete(target.diag.code);
      return prev.filter((i) => i.id !== id);
    });
  }

  function dismissAll() {
    seen.current.clear();
    setItems([]);
  }

  function toggleExpand(id: string) {
    setItems((prev) => prev.map((i) => (i.id === id ? { ...i, expanded: !i.expanded } : i)));
  }

  return (
    <div
      className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-[380px] max-w-[calc(100vw_-_32px)] flex-col gap-3"
      role="region"
      aria-label="Notifications du jeu"
    >
      <AnimatePresence initial={false}>
        {items.length > 1 && (
          <motion.button
            key="dismiss-all"
            type="button"
            layout
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={dismissAll}
            className={`pointer-events-auto self-end rounded-full border border-[var(--color-border-strong)] bg-[var(--color-surface-elevated)] px-3 py-1 text-[11px] text-[var(--color-foreground-subtle)] shadow-md transition-colors hover:text-[var(--color-foreground)] ${FOCUS_RING}`}
          >
            Tout fermer ({items.length})
          </motion.button>
        )}
        {items.map((item) => {
          const { diag } = item;
          const isPurge = diag.code === PURGED;
          const showMods = MODS_CODES.has(diag.code);
          const title = TITLES[diag.code];
          return (
            <motion.div
              key={item.id}
              layout
              initial={{ opacity: 0, x: 20, scale: 0.95 }}
              animate={{ opacity: 1, x: 0, scale: 1 }}
              exit={{ opacity: 0, x: 20, scale: 0.95 }}
              transition={{ duration: 0.18 }}
              role={diag.severity === "warning" ? "status" : "alert"}
              className={`pointer-events-auto rounded-card border p-4 shadow-lg backdrop-blur-sm ${
                SEVERITY_STYLE[diag.severity] ?? SEVERITY_STYLE.warning
              }`}
            >
              <div className="flex items-start gap-3">
                <SeverityIcon severity={diag.severity} purge={isPurge} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-start justify-between gap-2">
                    <p className="min-w-0 text-[11px] uppercase tracking-widest">
                      <span className="font-semibold">{title ?? "Diagnostic du jeu"}</span>
                      <span className="ml-2 font-mono text-[9.5px] normal-case tracking-normal opacity-60">
                        {diag.code}
                      </span>
                    </p>
                    <button
                      type="button"
                      onClick={() => dismiss(item.id)}
                      className={`flex h-5 w-5 flex-shrink-0 items-center justify-center rounded opacity-60 hover:bg-white/10 hover:opacity-100 ${FOCUS_RING}`}
                      aria-label="Fermer la notification"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </div>
                  <p className="mt-1 text-sm font-medium leading-snug text-[var(--color-foreground)]">
                    {diag.message}
                  </p>
                  {diag.hint && (
                    <p className="mt-2 text-xs leading-relaxed text-[var(--color-foreground-subtle)]">
                      {diag.hint}
                    </p>
                  )}

                  <div className="mt-2.5 flex flex-wrap items-center gap-x-3 gap-y-1.5">
                    {showMods && (
                      <button
                        type="button"
                        onClick={() => {
                          navigate("/mods");
                          dismiss(item.id);
                        }}
                        className={`inline-flex items-center gap-1 rounded text-[11px] font-medium underline-offset-2 hover:underline ${FOCUS_RING}`}
                      >
                        <Package className="h-3 w-3" />
                        Voir l'onglet Mods
                      </button>
                    )}
                    {!isPurge && (
                      <button
                        type="button"
                        onClick={openLogsPanel}
                        className={`inline-flex items-center gap-1 rounded text-[11px] font-medium underline-offset-2 hover:underline ${FOCUS_RING}`}
                      >
                        <ScrollText className="h-3 w-3" />
                        Ouvrir le journal
                      </button>
                    )}
                    {diag.details && (
                      <button
                        type="button"
                        onClick={() => toggleExpand(item.id)}
                        aria-expanded={item.expanded}
                        className={`inline-flex items-center gap-1 rounded text-[11px] opacity-70 hover:opacity-100 ${FOCUS_RING}`}
                      >
                        <ChevronDown
                          className={`h-3 w-3 transition-transform ${item.expanded ? "rotate-180" : ""}`}
                        />
                        {isPurge
                          ? item.expanded
                            ? "Masquer les fichiers"
                            : "Voir les fichiers"
                          : item.expanded
                            ? "Masquer l'extrait"
                            : "Voir l'extrait du journal"}
                      </button>
                    )}
                  </div>
                  {item.expanded && diag.details && (
                    <pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-all rounded border border-white/10 bg-black/40 p-2 text-[10px] leading-relaxed text-[var(--color-foreground-subtle)]">
                      {diag.details}
                    </pre>
                  )}
                </div>
              </div>
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}

const SEVERITY_STYLE: Record<string, string> = {
  warning: "border-warning/40 bg-warning/10 text-warning",
  error: "border-danger/40 bg-danger/10 text-danger",
  fatal: "border-danger/60 bg-danger/15 text-danger",
};

function SeverityIcon({ severity, purge }: { severity: GameDiagnostic["severity"]; purge: boolean }) {
  const className = "h-5 w-5 flex-shrink-0";
  if (purge) return <Package className={className} />;
  if (severity === "fatal") return <AlertOctagon className={className} />;
  if (severity === "error") return <AlertCircle className={className} />;
  return <AlertTriangle className={className} />;
}
