import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { fetchLore, type LoreDocument } from "../lib/content";
import { Markdown } from "../components/Markdown";

export function Lore() {
  const [doc, setDoc] = useState<LoreDocument | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchLore()
      .then((d) => {
        if (!cancelled) setDoc(d);
      })
      .catch((err) => {
        if (!cancelled)
          setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="px-8 py-8">
      <header className="mb-8">
        <p className="text-xs uppercase tracking-widest text-foreground-subtle">
          Reborn Roleplay
        </p>
        <h1 className="mt-1 font-display text-3xl font-semibold">Lore</h1>
        <p className="mt-1 text-sm text-foreground-subtle">
          {doc
            ? `Version ${doc.version} · mise a jour le ${new Date(doc.publishedAt).toLocaleDateString(
                "fr-FR",
                { day: "2-digit", month: "long", year: "numeric" },
              )}`
            : "L'univers et le canon RP du serveur."}
        </p>
      </header>

      {error && (
        <div className="rounded-md border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
          {error}
        </div>
      )}

      {!doc && !error && (
        <div className="flex items-center gap-2 text-sm text-foreground-subtle">
          <Loader2 className="h-4 w-4 animate-spin" />
          Chargement...
        </div>
      )}

      {doc && (
        <article className="max-w-3xl rounded-[--radius-card] border border-border bg-surface p-8">
          <Markdown content={doc.content} />
        </article>
      )}
    </div>
  );
}
