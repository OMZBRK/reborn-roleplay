import { Minus, Square, X } from "lucide-react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { isTauri } from "../lib/tauri";

export function TitleBar() {
  async function handleMinimize() {
    if (!isTauri) return;
    await getCurrentWindow().minimize();
  }
  async function handleToggleMaximize() {
    if (!isTauri) return;
    await getCurrentWindow().toggleMaximize();
  }
  async function handleClose() {
    if (!isTauri) return;
    await getCurrentWindow().close();
  }

  return (
    <div className="drag-region flex h-9 items-center justify-end border-b border-border bg-background/40 backdrop-blur">
      <div className="no-drag flex h-full">
        <TitleBarButton onClick={handleMinimize} aria-label="Reduire">
          <Minus className="h-3.5 w-3.5" />
        </TitleBarButton>
        <TitleBarButton onClick={handleToggleMaximize} aria-label="Maximiser">
          <Square className="h-3 w-3" />
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
          : "hover:bg-surface-elevated hover:text-foreground")
      }
      {...rest}
    >
      {children}
    </button>
  );
}
