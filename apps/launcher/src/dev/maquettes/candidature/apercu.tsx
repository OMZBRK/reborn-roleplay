// MAQUETTES — point d'entrée de l'aperçu autonome (dev seulement).
//
//   http://localhost:1420/src/dev/maquettes/candidature/apercu.html?ecran=L4
//   …&selecteur   affiche le sélecteur flottant d'écrans
//
// Rend les maquettes sans l'application : pas de reprise de session, pas de
// faux backend. L'écran courant est reporté dans l'adresse.

import ReactDOM from "react-dom/client";
import { MotionConfig } from "framer-motion";
import "../../../styles/globals.css";
import { MaquettesCandidature } from "./index";

const params = new URLSearchParams(window.location.search);

function reporter(ecran: string) {
  const p = new URLSearchParams(window.location.search);
  p.set("ecran", ecran);
  window.history.replaceState(null, "", `?${p.toString()}`);
}

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <MotionConfig reducedMotion="user">
    <MaquettesCandidature ecran={params.get("ecran") ?? "L1"} selecteur={params.has("selecteur")} onEcran={reporter} />
  </MotionConfig>,
);
