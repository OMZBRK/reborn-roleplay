import { useMemo } from "react";
import { marked } from "marked";
import { cn } from "../lib/cn";

type Props = {
  content: string;
  className?: string;
};

marked.setOptions({ gfm: true, breaks: false });

/**
 * Rend un markdown serveur en HTML. On laisse `marked` faire le parsing —
 * il ne sanitize pas les `<script>`, mais nos sources de markdown viennent
 * toutes de notre API authentifiee (patchnotes / reglement / lore) ou
 * d'admins authentifies, donc on accepte le risque pour l'instant.
 */
export function Markdown({ content, className }: Props) {
  const html = useMemo(() => marked.parse(content) as string, [content]);
  return (
    <div
      className={cn(
        "prose prose-invert prose-sm max-w-none",
        "[&_h1]:font-display [&_h1]:text-2xl [&_h1]:font-semibold [&_h1]:mt-0 [&_h1]:mb-3",
        "[&_h2]:font-display [&_h2]:text-lg [&_h2]:font-semibold [&_h2]:mt-6 [&_h2]:mb-2",
        "[&_p]:text-foreground-subtle [&_p]:leading-relaxed [&_p]:my-2",
        "[&_ul]:list-disc [&_ul]:pl-5 [&_ul]:my-2 [&_li]:my-1 [&_li]:text-foreground-subtle",
        "[&_ol]:list-decimal [&_ol]:pl-5 [&_ol]:my-2",
        "[&_strong]:text-foreground [&_strong]:font-semibold",
        "[&_code]:rounded [&_code]:bg-surface-elevated [&_code]:px-1 [&_code]:py-0.5 [&_code]:text-xs",
        "[&_a]:text-accent [&_a]:underline-offset-2 hover:[&_a]:underline",
        className,
      )}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
