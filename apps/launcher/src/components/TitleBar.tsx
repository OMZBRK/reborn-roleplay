import { Minus, X } from "lucide-react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { isTauri } from "../lib/tauri";

// TitleBar overlay — transparente, positionnée absolute par-dessus le
// contenu. Aucune barre visible : seuls les boutons minimize/close
// flottent en haut à droite. Le contenu (sidebar, main) remonte jusqu'en
// y=0 et ses couleurs/gradients s'étendent naturellement.
//
// La zone est divisée en 3 segments :
//   - Gauche (au-dessus de la sidebar, ~SIDEBAR_WIDTH) : pointer-events-none
//     → clics passent à travers, l'avatar / bell / gear de la sidebar
//     restent cliquables.
//   - Milieu (au-dessus du main) : drag-region → permet de drag la fenêtre
//     en grabbant cette zone vide en haut de la page.
//   - Droite : boutons minimize/close (no-drag).
//
// Largeur du spacer gauche : 300px pour couvrir la sidebar la plus large
// du repo (tk-sidebar des Tickets fait 300px ; la Sidebar principale fait
// 280px). Sur les routes avec sidebar 280px, les 20px supplémentaires du
// spacer recouvrent le début du main — comme le main a toujours du padding
// horizontal et aucun élément interactif dans ces 20px-là, ça ne gêne rien.
// Sur /login (pas de sidebar), c'est juste une bande sans interaction.
const SIDEBAR_WIDTH = 300;
const BAR_HEIGHT = 36;

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
      className="pointer-events-none absolute inset-x-0 top-0 z-50 flex"
      style={{ height: BAR_HEIGHT }}
    >
      {/* Spacer au-dessus de la sidebar : pointer-events-none, clicks passent
          à travers vers le UserBlock (avatar/bell/gear cliquables). */}
      <div style={{ width: SIDEBAR_WIDTH }} aria-hidden />

      {/* Drag-region : le user grabbe cette zone vide en haut du main pour
          drag la fenêtre. pointer-events-auto pour que le drag soit
          intercepté. */}
      <div className="drag-region pointer-events-auto flex-1" />

      {/* Boutons minimize/close — no-drag pour qu'ils soient cliquables.
          Subtle gradient derrière pour que les icônes restent lisibles
          même quand le radial bleu de la Home glows en haut à droite. */}
      <div
        className="no-drag pointer-events-auto flex h-full"
        style={{
          backgroundImage:
            "linear-gradient(to left, rgba(7,8,11,0.55), rgba(7,8,11,0))",
        }}
      >
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
