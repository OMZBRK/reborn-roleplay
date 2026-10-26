import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router";
import "./styles/globals.css";

// L'application est importée dynamiquement : en développement, le mode de
// présentation pour captures d'écran (src/dev/, activé par un paramètre
// d'adresse puis mémorisé pour l'onglet) installe un faux backend Tauri
// AVANT que lib/tauri.ts ne soit chargé, car ce module détecte Tauri une
// seule fois, à son chargement. En production, import.meta.env.DEV vaut
// false : le bloc et son module sont retirés du bundle.
async function start(): Promise<void> {
  // Ce mode rend sans StrictMode : ses effets montés deux fois n'existent
  // pas en production et fausseraient les écrans photographiés.
  let strict = true;
  // ?demo=maquette : écran de maquette rendu à la place de l'application.
  let tree: React.ReactNode = null;
  if (import.meta.env.DEV) {
    let wanted = new URLSearchParams(window.location.search).has("demo");
    try {
      wanted ||= window.sessionStorage.getItem("reborn:demo") !== null;
    } catch {
      // Stockage indisponible : seul le paramètre d'adresse compte.
    }
    if (wanted) {
      const demo = await import("./dev/demo");
      strict = !demo.demoActive;
      tree = await demo.maquette();
    }
  }

  if (tree === null) {
    const { App } = await import("./App");
    tree = (
      <BrowserRouter>
        <App />
      </BrowserRouter>
    );
  }
  ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
    strict ? <React.StrictMode>{tree}</React.StrictMode> : tree,
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
}

start().catch((e: unknown) => console.error("[boot] démarrage de l'interface impossible :", e));
