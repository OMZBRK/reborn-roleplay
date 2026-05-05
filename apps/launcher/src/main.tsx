import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router";
import { App } from "./App";
import "./styles/globals.css";

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);

// Une fois React mounté, on fade out le preload splash défini dans index.html.
// Délai court pour que la première frame de l'app ait le temps de peindre,
// sinon on aurait un flash entre la disparition du splash et l'apparition
// de la UI React.
const preloadEl = document.getElementById("reborn-preload");
if (preloadEl) {
  // requestAnimationFrame x2 pour s'assurer qu'on attend le premier paint
  // de React avant de retirer le splash.
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      preloadEl.classList.add("is-hidden");
      // Suppression du DOM après la transition (280ms côté CSS) pour
      // libérer les noeuds.
      window.setTimeout(() => {
        preloadEl.remove();
      }, 320);
    });
  });
}
