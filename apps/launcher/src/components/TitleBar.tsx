import { Minus, X } from "lucide-react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { isTauri } from "../lib/tauri";

// TitleBar — bande de 36px en haut, sans border et sans bg distinct, pour
// se fondre dans le fond du launcher (pas de "bande de page web"). Reste
// dans le flow normal du layout (pas absolute), donc les enfants ci-dessous
// prennent le reste de la hauteur sans overlap.
export function TitleBar() {
  async function handleMinimize() {
    if (!isTauri) return;
    try {
      await getCurrentWindow().minimize();
    } catch (err) {
      console.error("[titlebar] minimize failed", err);
    }
  }
  async function handleClose() {
    if (!isTauri) return;
    try {
      await getCurrentWindow().close();
    } catch (err) {
      console.error("[titlebar] close failed", err);
    }
  }

  return (
    <div
      className="drag-region flex h-9 shrink-0 items-center justify-end"
      // Bg explicite identique au body — pas de border, pas de blur, pas de
      // /40 transparent qui creait l'effet "barre de page web".
      style={{ background: "var(--color-background)" }}
    >
      <div className="no-drag flex h-full">
        <TitleBarButton onClick={handleMinimize} aria-label="Reduire">
          <Minus className="h-3.5 w-3.5" />
        </TitleBarButton>
        <TitleBarButton onClick={handleClose} aria-label="Fermer" variant="danger">
          <X className="h-3.5 w-3.5" />
        </TitleBarButton>
      </div>
    </div>
  );
}

function TitleBarButton({
  children,
  onClick,
  variant,
  ...rest
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "danger" }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        "flex h-full w-11 items-center justify-center text-foreground-subtle transition " +
        (variant === "danger"
          ? "hover:bg-danger hover:text-white"
          : "hover:bg-white/5 hover:text-foreground")
      }
      {...rest}
    >
      {children}
    </button>
  );
}
