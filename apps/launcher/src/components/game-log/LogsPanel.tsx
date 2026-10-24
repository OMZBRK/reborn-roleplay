import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
} from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowDownToLine,
  Check,
  Copy,
  Download,
  FolderOpen,
  RefreshCw,
  ScrollText,
  Search,
  X,
} from "lucide-react";
import { revealItemInDir } from "@tauri-apps/plugin-opener";
import { Portal } from "../system/Portal";
import { formatIpcError } from "../../lib/errors";
import {
  exportLogs,
  formatLogLines,
  formatLogTime,
  LOG_BUFFER_MAX,
  type LogLevel,
} from "../../lib/logs";
import { isTauri } from "../../lib/tauri";
import { useGameRunning, useGameWindowReady } from "../../stores/game-store";
import {
  useFilteredLogs,
  useLogCounts,
  useLogsStore,
  type LogEntry,
  type LogStreamFilter,
} from "../../stores/logs-store";
import { closeLogsPanel, useLogsPanelStore } from "./logs-panel-store";

// Panneau « Journal du jeu » : stdout + stderr de Minecraft (tampon de
// LOG_BUFFER_MAX lignes tenu par stores/logs-store.ts, recale sur le tampon
// Rust). Ouvert via openLogsPanel() depuis le toast de diagnostic, l'ecran
// de crash ou l'accueil. Monte une fois (CrashStub) : l'hote ne lit que
// `open`, la boite de dialogue (abonnee aux lignes) n'existe qu'ouverte —
// rien ne se re-rend toutes les 250 ms quand le panneau est ferme.
//
// Liste virtualisee sans dependance : lignes a hauteur fixe (ROW_H, pas de
// retour a la ligne, defilement horizontal), seules les lignes visibles
// (+ OVERSCAN) sont rendues. Suivi automatique de la fin, coupe des que le
// joueur remonte, repris quand il revient en bas ; hors suivi, la ligne en
// haut de l'ecran reste en place quand le tampon decale (anciennes lignes
// ecartees) ou quand un filtre change.

const ROW_H = 18;
const OVERSCAN = 12;
/** Au-dela, la ligne est coupee a l'affichage (la copie garde tout). */
const RENDER_MAX = 2000;
/** Largeur fixe des colonnes heure (8ch) + niveau (6ch) + marges. */
const PREFIX_CH = 20;

const FOCUS_RING =
  "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]";

const LEVELS: { level: LogLevel; label: string; tag: string; color: string }[] = [
  { level: "error", label: "Erreurs", tag: "ERREUR", color: "var(--color-danger)" },
  { level: "warn", label: "Avertissements", tag: "AVERT", color: "var(--color-warning)" },
  { level: "info", label: "Infos", tag: "INFO", color: "var(--color-foreground-subtle)" },
  { level: "debug", label: "Debug", tag: "DEBUG", color: "var(--color-foreground-muted)" },
];
const LEVEL_META = Object.fromEntries(LEVELS.map((l) => [l.level, l])) as Record<
  LogLevel,
  (typeof LEVELS)[number]
>;

const STREAMS: { value: LogStreamFilter; label: string; title: string }[] = [
  { value: "all", label: "Tous", title: "Sortie standard et sortie d'erreur" },
  { value: "out", label: "stdout", title: "Sortie standard de Minecraft" },
  { value: "err", label: "stderr", title: "Sortie d'erreur de Minecraft" },
];

const nf = new Intl.NumberFormat("fr-FR");

/** Hote du panneau : a monter une seule fois. */
export function LogsPanel() {
  const open = useLogsPanelStore((s) => s.open);
  return (
    <Portal>
      <AnimatePresence>{open && <LogsDialog key="logs-dialog" />}</AnimatePresence>
    </Portal>
  );
}

type Notice =
  | { tone: "error"; text: string }
  | { tone: "exported"; path: string };

function LogsDialog() {
  const openNonce = useLogsPanelStore((s) => s.openNonce);
  const filtered = useFilteredLogs();
  const counts = useLogCounts();
  const total = useLogsStore((s) => s.lines.length);
  const dropped = useLogsStore((s) => s.dropped);
  const levels = useLogsStore((s) => s.levels);
  const stream = useLogsStore((s) => s.stream);
  const query = useLogsStore((s) => s.query);
  const toggleLevel = useLogsStore((s) => s.toggleLevel);
  const setStream = useLogsStore((s) => s.setStream);
  const setQuery = useLogsStore((s) => s.setQuery);
  const resetFilters = useLogsStore((s) => s.resetFilters);
  const running = useGameRunning();
  const windowReady = useGameWindowReady();

  const dialogRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  const [follow, setFollow] = useState(true);
  const [scrollTop, setScrollTop] = useState(0);
  const [viewH, setViewH] = useState(400);
  const [copied, setCopied] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);

  // Ligne en haut de l'ecran (id + decalage) : sert a la garder en place
  // quand la liste change hors suivi.
  const anchorRef = useRef<{ id: number; offset: number } | null>(null);
  const filteredRef = useRef(filtered);
  filteredRef.current = filtered;
  const rafRef = useRef(0);

  const q = query.trim().toLowerCase();
  const filtersActive =
    !!q || stream !== "all" || LEVELS.some(({ level }) => levels[level] === false);

  // Ouverture (ou re-ouverture) : on suit la fin et on se recale sur le
  // tampon Rust (lignes recues avant le montage du launcher, rechargement).
  // Le saut explicite couvre la re-ouverture pendant l'animation de
  // fermeture (meme instance reutilisee : follow peut deja valoir true).
  useEffect(() => {
    setFollow(true);
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
    void useLogsStore.getState().hydrate();
  }, [openNonce]);

  // Focus : recherche a l'ouverture, element d'origine a la fermeture.
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    searchRef.current?.focus({ preventScroll: true });
    return () => {
      if (previous && document.contains(previous)) previous.focus({ preventScroll: true });
    };
  }, []);

  // Clavier global (phase de capture : passe avant le Modal de crash qui
  // ecoute Echap sur document, pour ne fermer que ce panneau).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopPropagation();
        e.preventDefault();
        if (document.activeElement === searchRef.current && useLogsStore.getState().query) {
          useLogsStore.getState().setQuery("");
          return;
        }
        closeLogsPanel();
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "f") {
        e.preventDefault();
        searchRef.current?.focus();
        searchRef.current?.select();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, []);

  // Hauteur visible de la liste.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    setViewH(el.clientHeight);
    const ro = new ResizeObserver(() => setViewH(el.clientHeight));
    ro.observe(el);
    return () => {
      ro.disconnect();
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, []);

  // Positionnement apres chaque changement de liste : fin du journal en
  // suivi, sinon ligne d'ancrage conservee. setScrollTop synchrone (avant
  // peinture) pour ne jamais afficher une fenetre de lignes perimee.
  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    if (follow) {
      el.scrollTop = el.scrollHeight;
    } else {
      const anchor = anchorRef.current;
      if (anchor) {
        const target = lowerBoundById(filtered, anchor.id) * ROW_H + anchor.offset;
        if (Math.abs(el.scrollTop - target) >= 1) el.scrollTop = target;
      }
    }
    setScrollTop(el.scrollTop);
    updateAnchor(el.scrollTop); // ne lit que des refs
  }, [filtered, follow]);

  function updateAnchor(top: number) {
    const list = filteredRef.current;
    const idx = Math.floor(top / ROW_H);
    const line = list[Math.min(idx, list.length - 1)];
    anchorRef.current = line ? { id: line.id, offset: top - idx * ROW_H } : null;
  }

  function onScroll() {
    const el = scrollRef.current;
    if (!el) return;
    updateAnchor(el.scrollTop);
    // Suivi coupe des que le joueur remonte, repris quand il revient en bas.
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight <= ROW_H * 1.5;
    setFollow(atBottom);
    if (!rafRef.current) {
      rafRef.current = requestAnimationFrame(() => {
        rafRef.current = 0;
        if (scrollRef.current) setScrollTop(scrollRef.current.scrollTop);
      });
    }
  }

  // Fenetre de lignes rendues.
  const start = Math.max(0, Math.floor(scrollTop / ROW_H) - OVERSCAN);
  const end = Math.min(filtered.length, Math.ceil((scrollTop + viewH) / ROW_H) + OVERSCAN);
  const windowLines = filtered.slice(start, end);

  // Largeur du contenu (en ch, police mono) : stable pendant le defilement
  // vertical, sinon la barre horizontale sauterait d'une fenetre a l'autre.
  const widthCh = useMemo(() => {
    let max = 0;
    for (const l of filtered) {
      const len = Math.min(l.text.length, RENDER_MAX) + tabCount(l.text) * 3;
      if (len > max) max = len;
    }
    return max + PREFIX_CH;
  }, [filtered]);

  async function copyVisible() {
    if (!filtered.length) return;
    try {
      await navigator.clipboard.writeText(formatLogLines(filtered));
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch (err) {
      setNotice({ tone: "error", text: formatIpcError(err, "Copie dans le presse-papiers impossible") });
    }
  }

  async function exportAll() {
    if (!isTauri) {
      setNotice({
        tone: "error",
        text: "L'export du journal n'est disponible que dans le launcher (pas dans un navigateur).",
      });
      return;
    }
    setExporting(true);
    setNotice(null);
    try {
      const path = await exportLogs();
      if (!path) throw new Error("le launcher n'a renvoyé aucun chemin de fichier");
      setNotice({ tone: "exported", path });
    } catch (err) {
      setNotice({ tone: "error", text: formatIpcError(err, "Export du journal impossible") });
    } finally {
      setExporting(false);
    }
  }

  async function revealExport(path: string) {
    try {
      await revealItemInDir(path);
    } catch (err) {
      setNotice({
        tone: "error",
        text: formatIpcError(err, `Ouverture du dossier de ${path} impossible`),
      });
    }
  }

  function jumpToEnd() {
    setFollow(true);
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }

  // Piege a focus minimal : Tab boucle dans le panneau.
  function onDialogKeyDown(e: ReactKeyboardEvent<HTMLDivElement>) {
    if (e.key !== "Tab" || !dialogRef.current) return;
    const items = Array.from(
      dialogRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), [tabindex="0"]',
      ),
    );
    if (!items.length) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  }

  const status = running
    ? windowReady
      ? "Partie en cours"
      : "Partie en cours · Minecraft démarre…"
    : total > 0
      ? "Dernière partie"
      : "Aucune partie pour l'instant";
  const countLabel =
    total === 0
      ? ""
      : filtersActive
        ? `${nf.format(filtered.length)} ligne${filtered.length > 1 ? "s" : ""} affichée${filtered.length > 1 ? "s" : ""} sur ${nf.format(total)}`
        : `${nf.format(total)} ligne${total > 1 ? "s" : ""}`;

  return (
    <motion.div
      className="fixed inset-0 z-[90] flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.16 }}
      onClick={(e) => {
        if (e.target === e.currentTarget) closeLogsPanel();
      }}
    >
      <motion.div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="reborn-logs-title"
        onKeyDown={onDialogKeyDown}
        initial={{ scale: 0.97, opacity: 0, y: 8 }}
        animate={{ scale: 1, opacity: 1, y: 0 }}
        exit={{ scale: 0.98, opacity: 0 }}
        transition={{ type: "spring", stiffness: 340, damping: 28 }}
        className="flex flex-col overflow-hidden rounded-[14px] border bg-[var(--color-surface-elevated)]"
        style={{
          // Tient dans la fenetre (y compris zoomee sur petit ecran).
          height: "min(700px, calc(100vh - 32px))",
          width: "min(1080px, calc(100vw - 32px))",
          borderColor: "var(--color-border-strong)",
          boxShadow: "0 18px 48px -8px rgba(0,0,0,.7), 0 0 0 1px var(--color-accent-soft)",
        }}
      >
        {/* En-tete */}
        <div className="flex items-center gap-3 border-b border-[var(--color-border)] px-5 py-3">
          <span className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-md bg-[var(--color-accent-soft)] text-[var(--color-accent-bright)]">
            <ScrollText className="h-4 w-4" />
          </span>
          <div className="min-w-0 flex-1">
            <h2
              id="reborn-logs-title"
              className="font-display text-[17px] leading-tight tracking-wide text-[var(--color-foreground)]"
            >
              Journal du jeu
            </h2>
            <p className="truncate text-[11px] text-[var(--color-foreground-muted)]">
              <span className="inline-flex items-center gap-1.5">
                {running && (
                  <span
                    className="h-1.5 w-1.5 rounded-full"
                    style={{ background: "var(--color-success)" }}
                    aria-hidden
                  />
                )}
                {status}
              </span>
              {countLabel && <span> · {countLabel}</span>}
            </p>
          </div>
          <button
            type="button"
            onClick={() => void copyVisible()}
            disabled={!filtered.length}
            title={
              filtersActive
                ? "Copie les lignes affichées (filtres appliqués)"
                : "Copie tout le journal dans le presse-papiers"
            }
            className={`${TOOL_BTN} ${FOCUS_RING}`}
          >
            {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
            {copied ? "Copié" : "Copier"}
          </button>
          <button
            type="button"
            onClick={() => void exportAll()}
            disabled={exporting}
            title="Enregistre tout le journal (sans filtre) dans un fichier .log du dossier logs du launcher"
            className={`${TOOL_BTN} ${FOCUS_RING}`}
          >
            {exporting ? (
              <RefreshCw className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <Download className="h-3.5 w-3.5" />
            )}
            {exporting ? "Export…" : "Exporter"}
          </button>
          <button
            type="button"
            onClick={closeLogsPanel}
            aria-label="Fermer le journal"
            title="Fermer (Échap)"
            className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-[var(--color-foreground-subtle)] transition-colors hover:bg-[var(--color-surface-overlay)] hover:text-[var(--color-foreground)] ${FOCUS_RING}`}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Filtres */}
        <div className="flex flex-wrap items-center gap-2 border-b border-[var(--color-border)] px-5 py-2.5">
          <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="Niveaux affichés">
            {LEVELS.filter((l) => l.level !== "debug" || counts.debug > 0).map((l) => {
              const on = levels[l.level] !== false;
              return (
                <button
                  key={l.level}
                  type="button"
                  aria-pressed={on}
                  onClick={() => toggleLevel(l.level)}
                  className={`inline-flex h-7 items-center gap-1.5 rounded-full border px-2.5 text-[11px] transition-colors ${FOCUS_RING} ${
                    on
                      ? "border-[var(--color-border-strong)] bg-[var(--color-surface-overlay)] text-[var(--color-foreground)]"
                      : "border-[var(--color-border)] bg-transparent text-[var(--color-foreground-muted)] line-through decoration-1"
                  }`}
                >
                  <span
                    className="h-2 w-2 rounded-full"
                    style={{ background: on ? l.color : "var(--color-border-strong)" }}
                    aria-hidden
                  />
                  {l.label}
                  <span className="tabular-nums text-[var(--color-foreground-muted)]">
                    {nf.format(counts[l.level] ?? 0)}
                  </span>
                </button>
              );
            })}
          </div>

          <div
            className="flex h-7 items-center overflow-hidden rounded-full border border-[var(--color-border)]"
            role="group"
            aria-label="Flux affiché"
          >
            {STREAMS.map((s) => (
              <button
                key={s.value}
                type="button"
                aria-pressed={stream === s.value}
                title={s.title}
                onClick={() => setStream(s.value)}
                className={`h-full px-2.5 font-mono text-[10.5px] transition-colors ${FOCUS_RING} ${
                  stream === s.value
                    ? "bg-[var(--color-surface-overlay)] text-[var(--color-foreground)]"
                    : "text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground-subtle)]"
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>

          <label className="ml-auto flex h-7 min-w-[180px] flex-1 items-center gap-2 rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] px-2.5 text-[var(--color-foreground-muted)] focus-within:border-[var(--color-border-strong)] sm:max-w-[300px]">
            <Search className="h-3.5 w-3.5 flex-shrink-0" />
            <input
              ref={searchRef}
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Rechercher (Ctrl+F)…"
              aria-label="Rechercher dans le journal"
              spellCheck={false}
              className="min-w-0 flex-1 bg-transparent text-[12px] text-[var(--color-foreground)] outline-none placeholder:text-[var(--color-foreground-muted)]"
            />
            {query && (
              <button
                type="button"
                onClick={() => {
                  setQuery("");
                  searchRef.current?.focus();
                }}
                aria-label="Effacer la recherche"
                className={`flex h-4 w-4 items-center justify-center rounded hover:text-[var(--color-foreground)] ${FOCUS_RING}`}
              >
                <X className="h-3 w-3" />
              </button>
            )}
          </label>

          <button
            type="button"
            aria-pressed={follow}
            onClick={() => (follow ? setFollow(false) : jumpToEnd())}
            title={
              follow
                ? "Le journal défile tout seul vers les nouvelles lignes. Cliquer pour figer."
                : "Reprendre le défilement automatique (aller à la fin)"
            }
            className={`inline-flex h-7 items-center gap-1.5 rounded-md border px-2.5 text-[11px] transition-colors ${FOCUS_RING} ${
              follow
                ? "border-[var(--color-accent)] bg-[var(--color-accent-soft)] text-[var(--color-foreground)]"
                : "border-[var(--color-border)] text-[var(--color-foreground-subtle)] hover:text-[var(--color-foreground)]"
            }`}
          >
            <ArrowDownToLine className="h-3.5 w-3.5" />
            {follow ? "Défilement auto" : "Aller à la fin"}
          </button>
        </div>

        {/* Lignes */}
        <div className="relative flex min-h-0 flex-1 flex-col bg-[var(--color-background)]">
          <div
            ref={scrollRef}
            onScroll={onScroll}
            tabIndex={0}
            role="log"
            aria-live="off"
            aria-label="Lignes du journal du jeu"
            className={`min-h-0 flex-1 overflow-auto font-mono text-[11.5px] ${FOCUS_RING} focus-visible:outline-offset-[-2px]`}
            style={{ tabSize: 4, lineHeight: `${ROW_H}px` }}
          >
            <div
              className="relative"
              style={{ height: filtered.length * ROW_H, minWidth: `${widthCh}ch` }}
            >
              <div style={{ transform: `translateY(${start * ROW_H}px)` }}>
                {windowLines.map((l) => (
                  <LogRow key={l.id} line={l} query={q} />
                ))}
              </div>
            </div>
          </div>

          {filtered.length === 0 && (
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center p-6">
              <div className="pointer-events-auto max-w-sm text-center">
                <ScrollText className="mx-auto h-7 w-7 text-[var(--color-foreground-muted)]" />
                {total === 0 ? (
                  <>
                    <p className="mt-3 text-sm text-[var(--color-foreground)]">
                      Le journal est vide.
                    </p>
                    <p className="mt-1 text-xs leading-relaxed text-[var(--color-foreground-muted)]">
                      Il se remplit dès que tu lances le jeu et garde la dernière partie
                      jusqu'au lancement suivant.
                    </p>
                  </>
                ) : (
                  <>
                    <p className="mt-3 text-sm text-[var(--color-foreground)]">
                      Aucune ligne ne correspond aux filtres.
                    </p>
                    <button
                      type="button"
                      onClick={resetFilters}
                      className={`mt-3 ${TOOL_BTN} ${FOCUS_RING}`}
                    >
                      Réinitialiser les filtres
                    </button>
                  </>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Pied : export, erreurs, lignes ecartees */}
        {(notice || dropped > 0) && (
          <div className="flex flex-col gap-1.5 border-t border-[var(--color-border)] px-5 py-2.5 text-[11.5px]">
            {notice?.tone === "exported" && (
              <div className="flex flex-wrap items-center gap-2 text-[var(--color-foreground-subtle)]">
                <Check className="h-3.5 w-3.5 flex-shrink-0 text-[var(--color-success)]" />
                <span className="min-w-0 flex-1 break-all">
                  Journal exporté : <span className="font-mono text-[var(--color-foreground)]">{notice.path}</span>
                </span>
                <button
                  type="button"
                  onClick={() => void revealExport(notice.path)}
                  className={`${TOOL_BTN} ${FOCUS_RING}`}
                >
                  <FolderOpen className="h-3.5 w-3.5" />
                  Ouvrir le dossier
                </button>
                <button
                  type="button"
                  onClick={() => setNotice(null)}
                  aria-label="Masquer"
                  className={`flex h-6 w-6 items-center justify-center rounded text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground)] ${FOCUS_RING}`}
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </div>
            )}
            {notice?.tone === "error" && (
              <div role="alert" className="flex items-start gap-2 text-[var(--color-danger)]">
                <span className="min-w-0 flex-1 leading-relaxed">{notice.text}</span>
                <button
                  type="button"
                  onClick={() => setNotice(null)}
                  aria-label="Masquer l'erreur"
                  className={`flex h-5 w-5 flex-shrink-0 items-center justify-center rounded opacity-70 hover:opacity-100 ${FOCUS_RING}`}
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </div>
            )}
            {dropped > 0 && (
              <p className="text-[var(--color-foreground-muted)]">
                Seules les {nf.format(LOG_BUFFER_MAX)} dernières lignes sont gardées :{" "}
                {nf.format(dropped)} ligne{dropped > 1 ? "s" : ""} plus ancienne
                {dropped > 1 ? "s ont" : " a"} été écartée{dropped > 1 ? "s" : ""}.
              </p>
            )}
          </div>
        )}
      </motion.div>
    </motion.div>
  );
}

const TOOL_BTN =
  "inline-flex h-8 flex-shrink-0 items-center gap-1.5 rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-3 text-[11.5px] text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-surface-overlay)] disabled:cursor-not-allowed disabled:opacity-50";

function LogRow({ line, query }: { line: LogEntry; query: string }) {
  const meta = LEVEL_META[line.level] ?? LEVEL_META.info;
  const raw = line.text.replace(/\t/g, "    ");
  const text =
    raw.length > RENDER_MAX
      ? `${raw.slice(0, RENDER_MAX)} … (+${nf.format(raw.length - RENDER_MAX)} caractères, visibles dans la copie)`
      : raw;
  const strong = line.level === "error" || line.level === "warn";
  return (
    <div
      className="flex items-center gap-2 whitespace-pre pl-3 pr-4 hover:bg-white/[0.035]"
      style={{
        height: ROW_H,
        boxShadow: strong ? `inset 2px 0 0 ${meta.color}` : undefined,
        background:
          line.level === "error" ? "color-mix(in srgb, var(--color-danger) 7%, transparent)" : undefined,
      }}
    >
      <span className="w-[8ch] flex-shrink-0 text-[var(--color-foreground-muted)]">
        {formatLogTime(line.t)}
      </span>
      <span className="w-[6ch] flex-shrink-0 font-semibold" style={{ color: meta.color }}>
        {meta.tag}
      </span>
      <span
        style={{
          color: strong ? meta.color : line.level === "debug" ? meta.color : "var(--color-foreground)",
        }}
      >
        {highlight(text, query)}
      </span>
    </div>
  );
}

/** Surligne les occurrences de `q` (deja en minuscules) dans `text`. */
function highlight(text: string, q: string): ReactNode {
  if (!q) return text;
  const lower = text.toLowerCase();
  // Casse a longueur variable (rare) : pas de surlignage plutot qu'un decalage.
  if (lower.length !== text.length) return text;
  const parts: ReactNode[] = [];
  let i = 0;
  let k = 0;
  for (let j = lower.indexOf(q); j >= 0; j = lower.indexOf(q, i)) {
    if (j > i) parts.push(text.slice(i, j));
    parts.push(
      <mark
        key={k++}
        className="rounded-[2px] bg-[var(--color-gold-soft)] text-[var(--color-gold-bright)]"
      >
        {text.slice(j, j + q.length)}
      </mark>,
    );
    i = j + q.length;
  }
  if (!parts.length) return text;
  if (i < text.length) parts.push(text.slice(i));
  return parts;
}

function tabCount(s: string): number {
  let n = 0;
  for (let i = s.indexOf("\t"); i >= 0; i = s.indexOf("\t", i + 1)) n++;
  return n;
}

/** Premier index dont l'id est >= `id` (les ids sont croissants). */
function lowerBoundById(list: readonly LogEntry[], id: number): number {
  let lo = 0;
  let hi = list.length;
  while (lo < hi) {
    const mid = (lo + hi) >>> 1;
    if (list[mid].id < id) lo = mid + 1;
    else hi = mid;
  }
  return lo;
}
