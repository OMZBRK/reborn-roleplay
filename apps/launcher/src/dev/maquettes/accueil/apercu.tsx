// MAQUETTES : point d'entrée de apercu.html (vite dev uniquement).
// Sans ?ecran=, affiche la liste des écrans ; avec, l'écran seul, collé en
// haut à gauche pour une capture 1280 × 800 (ou en longueur avec &pleine=1).

import React from "react";
import ReactDOM from "react-dom/client";
import "../../../styles/globals.css";
import { ECRANS_ACCUEIL, MaquettesAccueil, type EcranAccueil } from "./index";

document.documentElement.classList.add("mqa-apercu");

const params = new URLSearchParams(window.location.search);
const demande = params.get("ecran");
const ecran = ECRANS_ACCUEIL.find((e) => e.id === demande)?.id ?? null;
const pleine = params.get("pleine") === "1";

function Liste() {
  return (
    <div style={{ padding: "40px 48px", maxWidth: 880, fontFamily: "var(--font-sans)" }}>
      <p className="mqa-kicker">Maquettes · développement</p>
      <h1 className="mqa-perso-titre">Accueil et Mes personnages</h1>
      <ul style={{ marginTop: 24, display: "flex", flexDirection: "column", gap: 10, padding: 0, listStyle: "none" }}>
        {ECRANS_ACCUEIL.map((e) => (
          <li key={e.id}>
            <a
              href={`?ecran=${e.id}`}
              style={{ color: "var(--color-foreground)", textDecoration: "none", display: "block" }}
            >
              <span style={{ color: "var(--color-gold)", fontFamily: "var(--font-mono)", fontSize: 12 }}>
                {e.code}
              </span>{" "}
              <b>{e.titre}</b>
              <span style={{ display: "block", fontSize: 12, color: "var(--color-foreground-subtle)" }}>
                {e.description}
              </span>
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    {ecran ? <MaquettesAccueil ecran={ecran as EcranAccueil} pleineHauteur={pleine} /> : <Liste />}
  </React.StrictMode>,
);
