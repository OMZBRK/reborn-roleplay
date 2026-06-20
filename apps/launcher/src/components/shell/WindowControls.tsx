import { Minus, X } from "lucide-react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { isTauri } from "../../lib/tauri";

// Window controls compacts, rapproches, top-right (style Zenkai). Pas de
// "maximize" : la fenetre est resizable:false (cf tauri.conf.json), une
// bascule plein-ecran/restore n'a pas de sens.
//
// Empile au-dessus du drag-region via z-index + position absolute. Les
// boutons portent .no-drag pour rester cliquables, le reste de la bande
// haute (.drag-region rendue par AuthenticatedLayout) intercepte le drag
// de la fenetre.
//
// Dimensions deliberement petites (24x20 boutons, icones 10px) pour le
// look "app desktop" condense vs le chrome browser standard (46x32).
export function WindowControls() {
  async function handleMinimize() {
    if (!isTauri) return;
    await getCurrentWindow().minimize();
  }

  async function handleClose() {
    if (!isTauri) return;
    await getCurrentWindow().close();
  }

  return (
    <div className="no-drag absolute right-2 top-2 z-50 flex items-center gap-0.5">
      <WindowButton onClick={handleMinimize} aria-label="Reduire">
        <Minus className="h-2.5 w-2.5" strokeWidth={2.5} />
      </WindowButton>
      <WindowButton onClick={handleClose} aria-label="Fermer" variant="danger">
        <X className="h-2.5 w-2.5" strokeWidth={2.5} />
      </WindowButton>
    </div>
  );
}

type WindowButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "danger";
};

function WindowButton({ children, variant, ...rest }: WindowButtonProps) {
  return (
    <button
      type="button"
      className={[
        "flex h-5 w-6 items-center justify-center rounded-[3px] text-[var(--color-foreground-muted)] transition-colors",
        variant === "danger"
          ? "hover:bg-[var(--color-danger)] hover:text-white"
          : "hover:bg-white/8 hover:text-[var(--color-foreground)]",
      ].join(" ")}
      {...rest}
    >
      {children}
    </button>
  );
}
