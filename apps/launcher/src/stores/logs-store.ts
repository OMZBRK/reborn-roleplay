import { useMemo } from "react";
import { create } from "zustand";
import {
  getLogsSnapshot,
  LOG_BUFFER_MAX,
  onLogBatch,
  type LogLevel,
  type LogLine,
  type LogStream,
} from "../lib/logs";
import { onTauriEvent, type UnlistenFn } from "../lib/tauri";

// Tampon front des logs du jeu (LOG_BUFFER_MAX lignes, les plus anciennes
// sortent en premier) + filtres de la vue Logs. Alimente par game:log-batch
// via initLogsFeed(), demarre par initGameStore() : les lignes sont donc
// collectees meme quand la vue Logs est fermee. Le tampon Rust reste la
// reference : au demarrage et a chaque nouvelle partie, on se recale sur
// launcher_logs_snapshot.

/** Ligne stockee : `id` unique et croissant, pour les cles React. */
export type LogEntry = LogLine & { id: number };

export type LogStreamFilter = LogStream | "all";

type LogsState = {
  lines: LogEntry[];
  /** Lignes sorties du tampon (trop anciennes) depuis le dernier vidage. */
  dropped: number;
  levels: Record<LogLevel, boolean>;
  stream: LogStreamFilter;
  query: string;
  append: (lines: LogLine[]) => void;
  /** Remplace tout le tampon (snapshot du backend). */
  replace: (lines: LogLine[]) => void;
  clear: () => void;
  /** Se recale sur le tampon du backend sans perdre les lignes recues
   *  pendant la lecture. Silencieux en cas d'echec (on garde le tampon front). */
  hydrate: () => Promise<void>;
  setLevel: (level: LogLevel, visible: boolean) => void;
  toggleLevel: (level: LogLevel) => void;
  setStream: (stream: LogStreamFilter) => void;
  setQuery: (query: string) => void;
  resetFilters: () => void;
};

const ALL_LEVELS: Record<LogLevel, boolean> = { error: true, warn: true, info: true, debug: true };

let seq = 0;
const tag = (lines: LogLine[]): LogEntry[] => lines.map((l) => ({ ...l, id: ++seq }));

// Lignes recues pendant un hydrate() en cours (null hors hydratation), et
// generation pour qu'un snapshot perime n'ecrase pas un plus recent.
let pending: LogLine[] | null = null;
let hydrateGen = 0;

const lineKey = (l: LogLine) => `${l.t}|${l.stream}|${l.text}`;

/** Snapshot + lignes recues pendant sa lecture, sans doublon a la jointure
 *  (plusieurs lignes peuvent partager le meme horodatage). */
function mergeSnapshot(snapshot: LogLine[], received: LogLine[]): LogLine[] {
  if (!snapshot.length) return received;
  const lastT = snapshot[snapshot.length - 1].t;
  const boundary = new Map<string, number>();
  for (let i = snapshot.length - 1; i >= 0 && snapshot[i].t === lastT; i--) {
    const k = lineKey(snapshot[i]);
    boundary.set(k, (boundary.get(k) ?? 0) + 1);
  }
  const extra: LogLine[] = [];
  for (const l of received) {
    if (l.t < lastT) continue;
    if (l.t === lastT) {
      const k = lineKey(l);
      const n = boundary.get(k) ?? 0;
      if (n > 0) {
        boundary.set(k, n - 1);
        continue;
      }
    }
    extra.push(l);
  }
  return extra.length ? snapshot.concat(extra) : snapshot;
}

export const useLogsStore = create<LogsState>((set, get) => ({
  lines: [],
  dropped: 0,
  levels: { ...ALL_LEVELS },
  stream: "all",
  query: "",

  append: (incoming) => {
    if (!incoming.length) return;
    if (pending) pending.push(...incoming);
    set((s) => {
      const next = s.lines.concat(tag(incoming));
      const over = next.length - LOG_BUFFER_MAX;
      return over > 0
        ? { lines: next.slice(over), dropped: s.dropped + over }
        : { lines: next };
    });
  },

  replace: (all) => {
    const over = Math.max(0, all.length - LOG_BUFFER_MAX);
    set({ lines: tag(over ? all.slice(over) : all), dropped: 0 });
  },

  clear: () => set({ lines: [], dropped: 0 }),

  hydrate: async () => {
    const gen = ++hydrateGen;
    pending = [];
    try {
      const snapshot = await getLogsSnapshot();
      if (gen !== hydrateGen) return;
      const received = pending ?? [];
      // Backend sans tampon (commande absente ou partie pas encore lancee) :
      // on garde ce que le front a deja recu.
      if (snapshot.length || !get().lines.length) get().replace(mergeSnapshot(snapshot, received));
    } catch (e) {
      console.warn("[logs] lecture du tampon du launcher impossible :", e);
    } finally {
      if (gen === hydrateGen) pending = null;
    }
  },

  setLevel: (level, visible) => set((s) => ({ levels: { ...s.levels, [level]: visible } })),
  toggleLevel: (level) => set((s) => ({ levels: { ...s.levels, [level]: !s.levels[level] } })),
  setStream: (stream) => set({ stream }),
  setQuery: (query) => set({ query }),
  resetFilters: () => set({ levels: { ...ALL_LEVELS }, stream: "all", query: "" }),
}));

/** Lignes visibles selon les filtres (niveau, flux, texte insensible a la casse). */
export function filterLogs(
  lines: readonly LogEntry[],
  levels: Record<LogLevel, boolean>,
  stream: LogStreamFilter,
  query: string,
): LogEntry[] {
  const q = query.trim().toLowerCase();
  const allLevels = levels.error && levels.warn && levels.info && levels.debug;
  if (allLevels && stream === "all" && !q) return lines as LogEntry[];
  return lines.filter(
    (l) =>
      levels[l.level] !== false &&
      (stream === "all" || l.stream === stream) &&
      (!q || l.text.toLowerCase().includes(q)),
  );
}

/** Lignes filtrees, recalculees seulement quand le tampon ou un filtre change. */
export function useFilteredLogs(): LogEntry[] {
  const lines = useLogsStore((s) => s.lines);
  const levels = useLogsStore((s) => s.levels);
  const stream = useLogsStore((s) => s.stream);
  const query = useLogsStore((s) => s.query);
  return useMemo(() => filterLogs(lines, levels, stream, query), [lines, levels, stream, query]);
}

/** Nombre de lignes par niveau dans le tampon (badges des filtres). */
export function useLogCounts(): Record<LogLevel, number> {
  const lines = useLogsStore((s) => s.lines);
  return useMemo(() => {
    const c: Record<LogLevel, number> = { error: 0, warn: 0, info: 0, debug: 0 };
    for (const l of lines) c[l.level] = (c[l.level] ?? 0) + 1;
    return c;
  }, [lines]);
}

/**
 * Branche le tampon sur game:log-batch et le recale sur le backend (au
 * demarrage puis a chaque nouvelle partie). Appele par initGameStore() ;
 * renvoie la fonction d'arret.
 */
export function initLogsFeed(): () => void {
  let cancelled = false;
  const unlistens: UnlistenFn[] = [];
  const keep = (p: Promise<UnlistenFn>) =>
    p
      .then((fn) => (cancelled ? fn() : unlistens.push(fn)))
      .catch((e: unknown) => console.warn("[logs] écoute impossible :", e));

  keep(onLogBatch((lines) => useLogsStore.getState().append(lines)));
  keep(
    onTauriEvent("game:started", () => {
      // Nouvelle partie : tampon vide. game.rs vide le sien au spawn et
      // emet game:started avant le premier paquet de la partie, donc tout
      // ce que le front tient encore vient de la precedente (une marge de
      // temps ne retenait que ses dernieres lignes, crash compris). Puis on
      // se recale sur le backend : un tampon front vide laisse hydrate()
      // prendre le snapshot, ou les lignes recues pendant sa lecture.
      const st = useLogsStore.getState();
      st.clear();
      void st.hydrate();
    }),
  );
  void useLogsStore.getState().hydrate();

  return () => {
    cancelled = true;
    for (const fn of unlistens.splice(0)) fn();
  };
}
