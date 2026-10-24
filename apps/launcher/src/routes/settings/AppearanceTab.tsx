import { Check, Type } from "lucide-react";
import { useSettingsStore, type FontChoice } from "../../stores/settings-store";
import { cn } from "../../lib/cn";
import { Card } from "./ui";

const FONT_OPTIONS: {
  value: FontChoice;
  label: string;
  hint: string;
  // Aperçu : la stack rétro réutilise --font-sans-arcade (ArcadePix -> Inter)
  // pour que l'aperçu montre réellement la police, accents compris.
  preview: string;
}[] = [
  {
    value: "default",
    label: "Par défaut",
    hint: "Police actuelle (Inter) — lisibilité maximale.",
    preview: "var(--font-sans)",
  },
  {
    value: "arcade",
    label: "Rétro (menu du jeu)",
    hint: "ArcadePix, la police du menu principal en jeu.",
    preview: "var(--font-sans-arcade)",
  },
];

export function AppearanceTab() {
  const font = useSettingsStore((s) => s.ui.font);
  const setUi = useSettingsStore((s) => s.setUi);

  return (
    <Card
      title="Police d'affichage"
      description="Change la police de l'interface du launcher. Purement esthétique — les accents restent lisibles dans les deux cas."
      icon={<Type className="h-4 w-4" />}
    >
      <div
        role="radiogroup"
        aria-label="Police d'affichage"
        className="grid grid-cols-1 gap-3 p-6 sm:grid-cols-2"
      >
        {FONT_OPTIONS.map((o) => {
          const active = font === o.value;
          return (
            <button
              key={o.value}
              type="button"
              role="radio"
              aria-checked={active}
              onClick={() => setUi("font", o.value)}
              data-active={active || undefined}
              className={cn(
                "group relative flex flex-col items-start gap-2 rounded-[12px] border p-4 text-left transition-colors",
                active
                  ? "border-accent bg-accent/10"
                  : "border-border bg-surface/70 hover:border-border-strong",
              )}
            >
              <div className="flex w-full items-center justify-between gap-2">
                <span className="text-[13px] font-semibold text-foreground">{o.label}</span>
                {active && (
                  <span className="flex h-5 w-5 items-center justify-center rounded-full bg-accent text-white">
                    <Check className="h-3 w-3" strokeWidth={3} />
                  </span>
                )}
              </div>
              <span
                className="text-[22px] leading-none text-foreground"
                style={{ fontFamily: o.preview }}
              >
                Reborn RP
              </span>
              <span className="text-[11.5px] leading-snug text-foreground-subtle">{o.hint}</span>
            </button>
          );
        })}
      </div>
    </Card>
  );
}
