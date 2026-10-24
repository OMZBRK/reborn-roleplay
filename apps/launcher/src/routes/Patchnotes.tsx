import { motion, AnimatePresence } from "framer-motion";
import { AlertCircle, Loader2, Pin, RefreshCw, Sparkles, X } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  fetchPatchnote,
  fetchPatchnotes,
  type PatchNoteDetail,
  type PatchNoteSummary,
} from "../lib/content";
import { formatIpcError } from "../lib/errors";
import { Markdown } from "../components/Markdown";
import { useBadgesStore } from "../stores/badges-store";
import { Portal } from "../components/system/Portal";

// Historique des mises à jour (GET /v1/patchnotes via patchnotes_list /
// patchnotes_detail). Chaque erreur affiche la cause renvoyée par le
// launcher (API injoignable, session expirée…) et un bouton Réessayer.

const DATE_FMT = new Intl.DateTimeFormat("fr-FR", {
  day: "2-digit",
  month: "long",
  year: "numeric",
});

function dateLabel(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "" : DATE_FMT.format(d);
}

type DetailError = { id: string; title: string; message: string };

export function Patchnotes() {
  const [items, setItems] = useState<PatchNoteSummary[] | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [selected, setSelected] = useState<PatchNoteDetail | null>(null);
  const [openingId, setOpeningId] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<DetailError | null>(null);
  // Seule la dernière requête compte (double clic, Réessayer pendant un
  // chargement, page quittée en cours de route).
  const listReq = useRef(0);
  const detailReq = useRef(0);
  // Carte à re-focaliser quand la fenêtre de détail se ferme.
  const returnFocus = useRef<HTMLElement | null>(null);

  // Ouvrir la page = marquer les patch notes comme lus (efface la pastille).
  useEffect(() => {
    void useBadgesStore.getState().markRead("patchnotes");
  }, []);

  const loadList = useCallback(async () => {
    const req = ++listReq.current;
    setListLoading(true);
    setListError(null);
    try {
      const res = await fetchPatchnotes(1, 25);
      if (req === listReq.current) setItems(res.items);
    } catch (err) {
      if (req === listReq.current) {
        setListError(formatIpcError(err, "Impossible de charger les patch notes"));
      }
    } finally {
      if (req === listReq.current) setListLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadList();
    return () => {
      // Page quittée : les réponses en vol sont ignorées.
      listReq.current += 1;
      detailReq.current += 1;
    };
  }, [loadList]);

  async function openDetail(note: PatchNoteSummary, trigger?: HTMLElement | null) {
    const req = ++detailReq.current;
    returnFocus.current = trigger ?? null;
    setOpeningId(note.id);
    setDetailError(null);
    try {
      const detail = await fetchPatchnote(note.id);
      if (req === detailReq.current) setSelected(detail);
    } catch (err) {
      if (req === detailReq.current) {
        setDetailError({
          id: note.id,
          title: note.title,
          message: formatIpcError(err, `Impossible d'ouvrir « ${note.title} »`),
        });
      }
    } finally {
      if (req === detailReq.current) setOpeningId(null);
    }
  }

  const closeDetail = useCallback(() => {
    setSelected(null);
    returnFocus.current?.focus();
  }, []);

  return (
    <div className="px-8 py-8">
      <header className="mb-8">
        <p className="text-xs uppercase tracking-widest text-foreground-subtle">Reborn Roleplay</p>
        <h1 className="mt-1 font-display text-3xl font-semibold">Patch Notes</h1>
        <p className="mt-1 text-sm text-foreground-subtle">
          L'historique des mises à jour du launcher et du serveur.
        </p>
      </header>

      {detailError && (
        <div
          role="alert"
          className="mb-6 flex items-start gap-3 rounded-md border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" aria-hidden />
          <p className="min-w-0 flex-1 text-foreground">{detailError.message}</p>
          <button
            type="button"
            className="flex-shrink-0 font-semibold underline-offset-2 hover:underline"
            onClick={() => {
              const note = items?.find((n) => n.id === detailError.id);
              if (note) void openDetail(note);
            }}
          >
            Réessayer
          </button>
          <button
            type="button"
            className="flex-shrink-0 rounded p-0.5 text-foreground-subtle hover:text-foreground"
            onClick={() => setDetailError(null)}
            aria-label="Masquer ce message"
          >
            <X className="h-4 w-4" aria-hidden />
          </button>
        </div>
      )}

      {listError && !items ? (
        <div
          role="alert"
          className="flex flex-col items-start gap-3 rounded-card border border-danger/40 bg-danger/10 px-5 py-4"
        >
          <div className="flex items-start gap-3">
            <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0 text-danger" aria-hidden />
            <p className="text-sm leading-relaxed text-foreground">{listError}</p>
          </div>
          <button
            type="button"
            onClick={() => void loadList()}
            disabled={listLoading}
            className="reborn-shots-head-btn"
          >
            {listLoading ? (
              <Loader2 className="h-3 w-3 animate-spin" aria-hidden />
            ) : (
              <RefreshCw className="h-3 w-3" aria-hidden />
            )}
            Réessayer
          </button>
        </div>
      ) : items === null ? (
        <div className="flex items-center gap-2 text-sm text-foreground-subtle" role="status">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          Chargement des patch notes…
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-card border border-border/60 bg-surface/40 px-8 py-12 text-center">
          <Sparkles className="h-7 w-7 text-foreground-muted" aria-hidden />
          <p className="text-sm text-foreground">Aucun patch note publié pour l'instant.</p>
          <p className="text-xs text-foreground-subtle">
            Les prochaines mises à jour du launcher et du serveur apparaîtront ici.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-4">
          {items.map((note) => {
            const opening = openingId === note.id;
            return (
              <motion.button
                key={note.id}
                type="button"
                whileHover={{ y: -2 }}
                onClick={(e) => void openDetail(note, e.currentTarget)}
                aria-busy={opening || undefined}
                className="group flex flex-col items-start rounded-card border border-border bg-surface p-5 text-left transition hover:border-accent/40"
              >
                <div className="flex w-full items-center justify-between text-xs uppercase tracking-widest text-foreground-subtle">
                  <span>v{note.version}</span>
                  {opening ? (
                    <span className="flex items-center">
                      <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden />
                      <span className="reborn-sr-only">Ouverture…</span>
                    </span>
                  ) : (
                    note.pinned && (
                      <span className="flex items-center gap-1 text-warning">
                        <Pin className="h-3 w-3" aria-hidden />
                        Épinglé
                      </span>
                    )
                  )}
                </div>
                <h2 className="mt-3 font-display text-lg font-semibold leading-snug">
                  {note.title}
                </h2>
                <p className="mt-2 line-clamp-3 text-sm text-foreground-subtle">{note.excerpt}</p>
                <p className="mt-4 text-xs text-foreground-subtle">
                  Publié le {dateLabel(note.publishedAt)}
                </p>
              </motion.button>
            );
          })}
        </div>
      )}

      <PatchnoteDialog note={selected} onClose={closeDetail} />
    </div>
  );
}

function PatchnoteDialog({
  note,
  onClose,
}: {
  note: PatchNoteDetail | null;
  onClose: () => void;
}) {
  const closeRef = useRef<HTMLButtonElement>(null);

  // Échap ferme ; le focus part sur « Fermer » à l'ouverture.
  useEffect(() => {
    if (!note) return;
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [note, onClose]);

  return (
    <Portal>
      <AnimatePresence>
        {note && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-8 backdrop-blur-sm"
          >
            <motion.article
              role="dialog"
              aria-modal="true"
              aria-labelledby="patchnote-title"
              initial={{ scale: 0.96, y: 12 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.96, y: 12 }}
              transition={{ duration: 0.18 }}
              onClick={(e) => e.stopPropagation()}
              className="relative max-h-[80vh] w-full max-w-2xl overflow-y-auto rounded-card border border-border bg-surface-elevated p-8"
            >
              <button
                ref={closeRef}
                type="button"
                onClick={onClose}
                className="absolute right-4 top-4 flex h-8 w-8 items-center justify-center rounded-md text-foreground-subtle hover:bg-surface hover:text-foreground"
                aria-label="Fermer le patch note"
              >
                <X className="h-4 w-4" aria-hidden />
              </button>

              <p className="text-xs uppercase tracking-widest text-foreground-subtle">
                v{note.version} · {dateLabel(note.publishedAt)}
              </p>
              <h2 id="patchnote-title" className="mt-2 font-display text-2xl font-semibold">
                {note.title}
              </h2>
              <div className="mt-6">
                <Markdown content={note.content} />
              </div>
            </motion.article>
          </motion.div>
        )}
      </AnimatePresence>
    </Portal>
  );
}
