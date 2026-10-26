// MAQUETTES — branchement dans le mode démo (développement uniquement).
//
//   http://localhost:1420/?demo=maquette&ecran=L1     (codes dans ECRANS_MAQUETTE)
//
//   …&section=actualites   défile jusqu'à la section de ce titre (accents
//              et casse ignorés), calée en haut de la fenêtre : la carte
//              Discord de L10b (section Communauté, sous les Actualités) est
//              sous la ligne de flottaison en 1280 × 800
//   …&selecteur   sélecteur flottant des écrans L1 … L9 (candidature)
//   …&fige=1   sans animations : chaque élément est d'emblée dans son état
//              final (captures reproductibles d'une fois sur l'autre)
//   …&pleine=1 capture en longueur : la fenêtre prend la hauteur de son
//              contenu (onglets entiers, sans rien couper) ; la hauteur
//              mesurée est posée sur <html data-hauteur="…"> une fois les
//              polices et les images prêtes
//
// Capture en 1280 × 800 avec Edge sans fenêtre : &fige=1 et
// --run-all-compositor-stages-before-draw, sinon les animations d'entrée
// (framer-motion) sont prises en cours de route et des blocs sortent vides.
// En longueur : 1) msedge --headless=new --window-size=1280,800 --dump-dom
// « …&fige=1&pleine=1 » et lire data-hauteur ; 2) même adresse avec
// --screenshot et --window-size=1280,<hauteur>. Les zones de texte gardent
// leur plafond d'une fenêtre de 800 px (60 %), comme dans le launcher.
//
// Chargé par src/dev/demo.ts (import dynamique, scénario « maquette »), lui-même
// chargé par main.tsx derrière import.meta.env.DEV : rien ici n'entre dans le
// build de production. L'écran remplace l'application entière (pas de
// reprise de session, pas de routeur) : chaque maquette porte sa propre coque
// du launcher, en 1280 × 800.
//
// Les deux familles sont chargées à la demande, l'une OU l'autre : leurs
// feuilles de style (mqc-, mqa-) ne cohabitent jamais sur une même capture.

import { useEffect, type ReactNode } from "react";

type Famille = "candidature" | "accueil";

type Entree = {
  code: string;
  famille: Famille;
  /** Identifiant dans la famille (ECRANS_CANDIDATURE ou ECRANS_ACCUEIL). */
  id: string;
  titre: string;
};

const CANDIDATURE: Array<[code: string, titre: string]> = [
  ["L1", "Candidature › 1 Vous"],
  ["L2", "Candidature › 2 Personnage"],
  ["L3", "Candidature › 3 Village (clans en information, aucun choix)"],
  ["L4", "Candidature › 4 Histoire"],
  ["L5", "Candidature › 5 Voix"],
  ["L6", "Candidature › 6 Envoi, il reste à compléter"],
  ["L6-complet", "Candidature › 6 Envoi, prêt à envoyer"],
  ["L7", "Erreurs › champs"],
  ["L7-nom", "Erreurs › nom de famille = nom de clan"],
  ["L7-envoi", "Erreurs › envoi refusé"],
  ["L7-micro", "Erreurs › micro refusé"],
  ["L7-voix", "Erreurs › enregistrement trop court"],
  ["L8", "Suivi › écrit en relecture"],
  ["L8-accepte", "Suivi › écrit accepté"],
  ["L8-revoir", "Suivi › écrit à revoir"],
  ["L8-refuse", "Suivi › refusé"],
  ["L9", "Douane › choisir une séance"],
  ["L9-reservee", "Douane › place réservée"],
];

const ACCUEIL: Array<[code: string, id: string, titre: string]> = [
  ["L10", "accueil-douane", "Accueil › douane à passer (= L10a)"],
  ["L10a", "accueil-douane", "Accueil › douane à passer"],
  ["L10b", "accueil-joueur", "Accueil › joueur, carte Discord"],
  ["L10-etats", "accueil-etats", "Accueil › planche des états (en longueur)"],
  ["L11", "personnages", "Mes personnages"],
  ["L11-mort", "personnages-mort", "Mes personnages › mort RP ouvert"],
  ["L11-vide", "personnages-vide", "Mes personnages › vide"],
];

export const ECRANS_MAQUETTE: ReadonlyArray<Entree> = [
  ...CANDIDATURE.map(([code, titre]): Entree => ({ code, famille: "candidature", id: code, titre })),
  ...ACCUEIL.map(([code, id, titre]): Entree => ({ code, famille: "accueil", id, titre })),
];

/** Code demandé (insensible à la casse), L1 si absent ou inconnu. */
function trouver(code: string | null): Entree {
  const voulu = (code ?? "").trim().toLowerCase();
  const entree = ECRANS_MAQUETTE.find((e) => e.code.toLowerCase() === voulu);
  if (!entree && voulu) {
    console.warn(`[maquette] écran inconnu « ${code} », L1 à la place.`);
  }
  return entree ?? ECRANS_MAQUETTE[0];
}

/** Reporte l'écran courant dans l'adresse (rechargement = même écran). */
function reporter(code: string): void {
  const params = new URLSearchParams(window.location.search);
  params.set("ecran", code);
  window.history.replaceState(window.history.state, "", `${window.location.pathname}?${params.toString()}`);
}

/** Coupe les animations (framer-motion et CSS) : état final dès la 1re image. */
async function figer(): Promise<void> {
  const { MotionGlobalConfig } = await import("framer-motion");
  MotionGlobalConfig.skipAnimations = true;
  const style = document.createElement("style");
  style.dataset.maquette = "fige";
  style.textContent = `*, *::before, *::after {
    animation-duration: 0s !important; animation-delay: 0s !important;
    transition-duration: 0s !important; transition-delay: 0s !important;
  }`;
  document.head.appendChild(style);
}

const normaliser = (t: string) =>
  t
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase();

/** Fait défiler jusqu'à la section dont le titre (h2/h3) vaut `section`. */
function Defile({ section, children }: { section: string | null; children: ReactNode }) {
  useEffect(() => {
    if (!section) return;
    const voulu = normaliser(section);
    const id = window.requestAnimationFrame(() => {
      const titre = [...document.querySelectorAll("h2, h3")].find((h) => normaliser(h.textContent ?? "") === voulu);
      if (!titre) {
        console.warn(`[maquette] section « ${section} » introuvable.`);
        return;
      }
      const cible = (titre.closest("section") ?? titre) as HTMLElement;
      cible.style.scrollMarginTop = "28px";
      cible.scrollIntoView({ block: "start", behavior: "instant" });
    });
    return () => window.cancelAnimationFrame(id);
  }, [section]);
  return <>{children}</>;
}

/** Mode « capture en longueur » : mesure la coque rendue et pose sa hauteur sur <html>. */
function Mesure({ children }: { children: ReactNode }) {
  useEffect(() => {
    if (!document.documentElement.dataset.maquettePleine) return;
    let fini = false;
    const poser = () => {
      if (fini) return;
      const coque = document.querySelector<HTMLElement>(".mqc-coque, .mqa-fenetre, .mqa-planche");
      const cible = coque ?? document.getElementById("root");
      if (!cible) return;
      const h = Math.max(800, Math.ceil(Math.max(cible.scrollHeight, cible.getBoundingClientRect().height)));
      document.documentElement.dataset.hauteur = String(h);
    };
    // Tout de suite (Edge sans fenêtre ne fait pas toujours tourner
    // requestAnimationFrame), puis à nouveau une fois polices et images prêtes.
    poser();
    const minuteur = window.setTimeout(poser, 300);
    void Promise.all([
      document.fonts.ready,
      ...[...document.images].map((img) =>
        img.complete ? null : new Promise((ok) => img.addEventListener("load", ok, { once: true })),
      ),
    ]).then(() => window.setTimeout(poser, 50));
    return () => {
      fini = true;
      window.clearTimeout(minuteur);
    };
  }, []);
  return <>{children}</>;
}

/** Arbre React de l'écran demandé par ?ecran=, à rendre à la place de l'application. */
export async function arbreMaquette(): Promise<ReactNode> {
  return (
    <Mesure>
      <Defile section={new URLSearchParams(window.location.search).get("section")}>{await ecran()}</Defile>
    </Mesure>
  );
}

async function ecran(): Promise<ReactNode> {
  const params = new URLSearchParams(window.location.search);
  const entree = trouver(params.get("ecran"));
  reporter(entree.code);
  if (params.has("fige")) await figer();
  const pleine = params.has("pleine");
  if (pleine) document.documentElement.dataset.maquettePleine = "1";
  console.info(
    `[maquette] ${entree.code} : ${entree.titre}. Écrans : ${ECRANS_MAQUETTE.map((e) => e.code).join(", ")}.`,
  );
  document.title = `Maquette ${entree.code} · ${entree.titre}`;

  if (entree.famille === "candidature") {
    const [{ MotionConfig }, { MaquettesCandidature }] = await Promise.all([
      import("framer-motion"),
      import("./candidature"),
    ]);
    return (
      <MotionConfig reducedMotion="user">
        <MaquettesCandidature ecran={entree.id} onEcran={reporter} selecteur={params.has("selecteur")} />
      </MotionConfig>
    );
  }

  const { MaquettesAccueil } = await import("./accueil");
  const id = entree.id as Parameters<typeof MaquettesAccueil>[0]["ecran"];
  // La planche des états est en longueur : elle défile dans la fenêtre
  // (globals.css bloque le défilement de body, fenêtre fixe du launcher).
  // En capture longue, rien ne défile : la fenêtre a la hauteur du contenu.
  return (
    <div
      style={{
        height: pleine ? "auto" : "100%",
        overflow: pleine ? "visible" : id === "accueil-etats" ? "auto" : "hidden",
      }}
    >
      <MaquettesAccueil ecran={id} pleineHauteur={pleine} />
    </div>
  );
}
