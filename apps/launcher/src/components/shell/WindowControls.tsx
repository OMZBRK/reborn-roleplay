import type { ButtonHTMLAttributes } from "react";
import { Minus, X } from "lucide-react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { isTauri } from "../../lib/tauri";

// Contrôles fenêtre top-right (style Reborn). Pas de "maximize" : la
// fenêtre est resizable:false (cf tauri.conf.json), une bascule
// plein-écran/restore n'a pas de sens.
//
// Empilés au-dessus du drag-region via z-index + position absolute. Les
// boutons portent .no-drag pour rester cliquables, le reste de la bande
// haute (.drag-region rendue par App.tsx) intercepte le drag de la fenêtre.
//
// 32×28 par bouton (icônes 12px) : cible confortable à la souris et au
// clavier, tout en restant dans la bande de 32px. Les pages gardent leur
// contenu hors de cette zone (top 4 → 32px, 72px depuis la droite).
//
// Fermer : win.close() passe par CloseRequested côté Rust (src-tauri/src/
// tray.rs), comme Alt+F4. Préférence closeToTray active (défaut) : la
// fenêtre est cachée dans la zone de notification ; sinon le launcher quitte.
export function WindowControls() {
  async function run(action: "minimize" | "close") {
    if (!isTauri) return;
    try {
      const win = getCurrentWindow();
      await (action === "minimize" ? win.minimize() : win.close());
    } catch (e) {
      console.warn(`[fenêtre] ${action === "minimize" ? "réduction" : "fermeture"} impossible :`, e);
    }
  }

  return (
    <div className="no-drag absolute right-1.5 top-1 z-50 flex items-center gap-0.5">
      <WindowButton onClick={() => void run("minimize")} aria-label="Réduire" title="Réduire">
        <Minus className="h-3 w-3" strokeWidth={2.5} aria-hidden />
      </WindowButton>
      <WindowButton
        onClick={() => void run("close")}
        aria-label="Fermer le launcher"
        title="Fermer"
        variant="danger"
      >
        <X className="h-3 w-3" strokeWidth={2.5} aria-hidden />
      </WindowButton>
    </div>
  );
}

type WindowButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "danger";
};

function WindowButton({ children, variant, ...rest }: WindowButtonProps) {
  return (
    <button
      type="button"
      className={[
        "flex h-7 w-8 items-center justify-center rounded-[var(--radius-sm)] text-[var(--color-foreground-muted)] transition-colors focus-visible:outline-offset-[-2px]",
        variant === "danger"
          ? "hover:bg-[var(--color-danger)] hover:text-white focus-visible:bg-[var(--color-danger)] focus-visible:text-white"
          : "hover:bg-white/8 hover:text-[var(--color-foreground)] focus-visible:bg-white/8 focus-visible:text-[var(--color-foreground)]",
      ].join(" ")}
      {...rest}
    >
      {children}
    </button>
  );
}
