import { Minus, X } from "lucide-react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { isTauri } from "../../lib/tauri";

// Window controls flottants, absolute en haut-droite. Pas de "maximize" :
// la fenetre est resizable:false (cf tauri.conf.json), une bascule
// plein-ecran/restore n'a pas de sens et serait visuellement bruyante.
//
// Empile au-dessus du drag-region via z-index + position absolute. Les
// boutons portent .no-drag pour rester cliquables, le reste de la bande
// haute (.drag-region rendue par AuthenticatedLayout) intercepte le drag
// de la fenetre.
export function WindowControls() {
  async function handleMinimize() {
    if (!isTauri) return;
    await getCurrentWindow().minimize();
  }

  async function handleClose() {
    if (!isTauri) return;
    await getCurrentWindow().close();
  }

  // Clean : pas de gradient bg derriere les icones (cf feedback
  // prendreencompte.png). Les boutons sont juste flottants top-right,
  // le hover fournit le contraste necessaire pour la lisibilite.
  return (
    <div className="no-drag absolute right-0 top-0 z-50 flex h-8">
      <WindowButton onClick={handleMinimize} aria-label="Reduire">
        <Minus className="h-3.5 w-3.5" />
      </WindowButton>
      <WindowButton onClick={handleClose} aria-label="Fermer" variant="danger">
        <X className="h-3.5 w-3.5" />
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
        "flex h-full w-11 items-center justify-center text-[var(--color-foreground-subtle)] transition-colors",
        variant === "danger"
          ? "hover:bg-[var(--color-danger)] hover:text-white"
          : "hover:bg-white/5 hover:text-[var(--color-foreground)]",
      ].join(" ")}
      {...rest}
    >
      {children}
    </button>
  );
}
