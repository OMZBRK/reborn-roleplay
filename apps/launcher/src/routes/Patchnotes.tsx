import { motion, AnimatePresence } from "framer-motion";
import { Pin, X, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import {
  fetchPatchnote,
  fetchPatchnotes,
  type PatchNoteDetail,
  type PatchNoteSummary,
} from "../lib/content";
import { Markdown } from "../components/Markdown";
import { useBadgesStore } from "../stores/badges-store";

export function Patchnotes() {
  const [items, setItems] = useState<PatchNoteSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<PatchNoteDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Ouvrir la page = marquer les patch notes comme lus (efface la pastille).
  useEffect(() => {
    void useBadgesStore.getState().markRead("patchnotes");
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetchPatchnotes(1, 25)
      .then((res) => {
        if (!cancelled) setItems(res.items);
      })
      .catch((err) => {
        if (!cancelled)
          setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function openDetail(id: string) {
    setDetailLoading(true);
    try {
      const detail = await fetchPatchnote(id);
      setSelected(detail);
    } catch (err) {
      setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
    } finally {
      setDetailLoading(false);
    }
  }

  return (
    <div className="px-8 py-8">
      <header className="mb-8">
        <p className="text-xs uppercase tracking-widest text-foreground-subtle">Reborn Roleplay</p>
        <h1 className="mt-1 font-display text-3xl font-semibold">Patch Notes</h1>
        <p className="mt-1 text-sm text-foreground-subtle">
          L'historique des mises a jour du launcher et du serveur.
        </p>
      </header>

      {error && (
        <div className="mb-6 rounded-md border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
          {error}
        </div>
      )}

      {items === null && !error && (
        <div className="flex items-center gap-2 text-sm text-foreground-subtle">
          <Loader2 className="h-4 w-4 animate-spin" />
          Chargement...
        </div>
      )}

      {items && items.length === 0 && (
        <p className="text-sm text-foreground-subtle">Aucun patch note publie.</p>
      )}

      {items && items.length > 0 && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3">
          {items.map((note) => (
            <motion.button
              key={note.id}
              type="button"
              whileHover={{ y: -2 }}
              onClick={() => openDetail(note.id)}
              className="group flex flex-col items-start rounded-[--radius-card] border border-border bg-surface p-5 text-left transition hover:border-accent/40"
            >
              <div className="flex w-full items-center justify-between text-xs uppercase tracking-widest text-foreground-subtle">
                <span>v{note.version}</span>
                {note.pinned && (
                  <span className="flex items-center gap-1 text-warning">
                    <Pin className="h-3 w-3" />
                    Epingle
                  </span>
                )}
              </div>
              <h2 className="mt-3 font-display text-lg font-semibold leading-snug">
                {note.title}
              </h2>
              <p className="mt-2 line-clamp-3 text-sm text-foreground-subtle">{note.excerpt}</p>
              <p className="mt-4 text-xs text-foreground-subtle">
                Publie le{" "}
                {new Date(note.publishedAt).toLocaleDateString("fr-FR", {
                  day: "2-digit",
                  month: "long",
                  year: "numeric",
                })}
              </p>
            </motion.button>
          ))}
        </div>
      )}

      <AnimatePresence>
        {selected && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setSelected(null)}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-8"
          >
            <motion.article
              initial={{ scale: 0.96, y: 12 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.96, y: 12 }}
              transition={{ duration: 0.18 }}
              onClick={(e) => e.stopPropagation()}
              className="relative max-h-[80vh] w-full max-w-2xl overflow-y-auto rounded-[--radius-card] border border-border bg-surface-elevated p-8"
            >
              <button
                type="button"
                onClick={() => setSelected(null)}
                className="absolute right-4 top-4 flex h-8 w-8 items-center justify-center rounded-md text-foreground-subtle hover:bg-surface hover:text-foreground"
                aria-label="Fermer"
              >
                <X className="h-4 w-4" />
              </button>

              <p className="text-xs uppercase tracking-widest text-foreground-subtle">
                v{selected.version} ·{" "}
                {new Date(selected.publishedAt).toLocaleDateString("fr-FR", {
                  day: "2-digit",
                  month: "long",
                  year: "numeric",
                })}
              </p>
              <h2 className="mt-2 font-display text-2xl font-semibold">{selected.title}</h2>
              <div className="mt-6">
                <Markdown content={selected.content} />
              </div>
            </motion.article>
          </motion.div>
        )}
      </AnimatePresence>

      {detailLoading && !selected && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40">
          <Loader2 className="h-6 w-6 animate-spin text-foreground" />
        </div>
      )}
    </div>
  );
}
