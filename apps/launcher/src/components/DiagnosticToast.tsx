import { AnimatePresence, motion } from "framer-motion";
import { AlertOctagon, AlertTriangle, Info, X, ChevronDown } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import {
  onGameDiagnostic,
  onModsPurged,
  type GameDiagnostic,
  type ModsPurgedEvent,
} from "../lib/launcher";

type Item = {
  id: string;
  diag: GameDiagnostic;
  expanded: boolean;
};

let nextId = 1;

export function DiagnosticToast() {
  const [items, setItems] = useState<Item[]>([]);
  const seen = useRef<Set<string>>(new Set());

  useEffect(() => {
    const unlistens: Array<() => void> = [];

    onGameDiagnostic((d) => {
      // Deduplique : un meme code n'apparait qu'une seule fois pendant
      // un meme run. Sinon Fabric loggue chaque erreur 3-4 fois et on
      // se retrouve avec une pile de toasts.
      if (seen.current.has(d.code)) return;
      seen.current.add(d.code);
      setItems((prev) => [
        ...prev,
        { id: `d-${nextId++}`, diag: d, expanded: false },
      ]);
    }).then((fn) => unlistens.push(fn));

    onModsPurged((e: ModsPurgedEvent) => {
      if (e.removed.length === 0) return;
      const lines = e.removed
        .map((p) => p.split(/[\\/]/).pop() ?? p)
        .map((name) => `· ${name}`)
        .join("\n");
      setItems((prev) => [
        ...prev,
        {
          id: `d-${nextId++}`,
          diag: {
            code: "MODS_PURGED",
            severity: "warning",
            message: `${e.removed.length} mod(s) incompatible(s) avec Minecraft ${e.targetMcVersion} ont ete supprime(s).`,
            hint: "Si tu as besoin de ces mods, telecharge la version compatible et remets-les dans le dossier mods.",
            details: lines,
          },
          expanded: false,
        },
      ]);
    }).then((fn) => unlistens.push(fn));

    return () => unlistens.forEach((fn) => fn());
  }, []);

  function dismiss(id: string) {
    setItems((prev) => {
      const target = prev.find((i) => i.id === id);
      if (target) {
        // Retire de seen pour qu'un meme diagnostic puisse re-apparaitre
        // si l'utilisateur a clique fermer et que le probleme se reproduit.
        seen.current.delete(target.diag.code);
      }
      return prev.filter((i) => i.id !== id);
    });
  }

  function toggleExpand(id: string) {
    setItems((prev) =>
      prev.map((i) => (i.id === id ? { ...i, expanded: !i.expanded } : i)),
    );
  }

  return (
    <div className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-[380px] flex-col gap-3">
      <AnimatePresence initial={false}>
        {items.map((item) => (
          <motion.div
            key={item.id}
            layout
            initial={{ opacity: 0, x: 20, scale: 0.95 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: 20, scale: 0.95 }}
            transition={{ duration: 0.18 }}
            className={`pointer-events-auto rounded-[--radius-card] border p-4 shadow-lg backdrop-blur-sm ${
              SEVERITY_STYLE[item.diag.severity]
            }`}
          >
            <div className="flex items-start gap-3">
              <SeverityIcon severity={item.diag.severity} />
              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-xs font-mono uppercase tracking-widest opacity-70">
                    {item.diag.code}
                  </p>
                  <button
                    type="button"
                    onClick={() => dismiss(item.id)}
                    className="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded opacity-60 hover:bg-white/10 hover:opacity-100"
                    aria-label="Fermer"
                  >
                    <X className="h-3 w-3" />
                  </button>
                </div>
                <p className="mt-1 text-sm font-medium leading-snug">
                  {item.diag.message}
                </p>
                {item.diag.hint && (
                  <p className="mt-2 text-xs opacity-80 leading-relaxed">
                    {item.diag.hint}
                  </p>
                )}
                {item.diag.details && (
                  <button
                    type="button"
                    onClick={() => toggleExpand(item.id)}
                    className="mt-2 inline-flex items-center gap-1 text-[11px] opacity-60 hover:opacity-100"
                  >
                    <ChevronDown
                      className={`h-3 w-3 transition-transform ${
                        item.expanded ? "rotate-180" : ""
                      }`}
                    />
                    {item.expanded ? "Masquer le log" : "Voir le log brut"}
                  </button>
                )}
                {item.expanded && item.diag.details && (
                  <pre className="mt-2 max-h-40 overflow-auto rounded border border-white/10 bg-black/40 p-2 text-[10px] leading-relaxed whitespace-pre-wrap break-all opacity-80">
                    {item.diag.details}
                  </pre>
                )}
              </div>
            </div>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}

const SEVERITY_STYLE: Record<GameDiagnostic["severity"], string> = {
  warning: "border-warning/40 bg-warning/10 text-warning",
  error: "border-danger/40 bg-danger/10 text-danger",
  fatal: "border-danger/60 bg-danger/15 text-danger",
};

function SeverityIcon({ severity }: { severity: GameDiagnostic["severity"] }) {
  const className = "h-5 w-5 flex-shrink-0";
  if (severity === "warning") return <AlertTriangle className={className} />;
  if (severity === "fatal") return <AlertOctagon className={className} />;
  return <Info className={className} />;
}
