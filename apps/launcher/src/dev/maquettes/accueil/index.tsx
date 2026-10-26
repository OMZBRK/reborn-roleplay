// MAQUETTES L10 (accueil) et L11 (Mes personnages) — développement
// uniquement, comme src/dev/demo.ts : rien ici n'est importé par
// l'application, donc rien n'entre dans le build de production. Données
// fictives (donnees.ts) ; cahier des charges :
// docs/gameplay/CANDIDATURE_ET_DOUANE.md §3.3, §4.6, §5.3, §6.3.
//
//   <MaquettesAccueil ecran="accueil-douane" />
//
// Aperçu autonome (vite dev) :
//   http://localhost:1420/src/dev/maquettes/accueil/apercu.html
//   http://localhost:1420/src/dev/maquettes/accueil/apercu.html?ecran=accueil-douane&pleine=1

import "./accueil.css";
import { AccueilMaquette } from "./AccueilMaquette";
import { MesPersonnagesMaquette } from "./MesPersonnages";
import { PlancheEtats } from "./PlancheEtats";
import { EMPLACEMENTS, PERSONNAGES } from "./donnees";

export { AccueilMaquette, PageAccueil } from "./AccueilMaquette";
export type { VarianteAccueil } from "./AccueilMaquette";
export { HeroAccueil, BoutonDouane, ColonneDouane } from "./HeroAccueil";
export { CarteDiscord, DiscordLogo } from "./CarteDiscord";
export { CoqueLauncher } from "./CoqueLauncher";
export { MesPersonnages, MesPersonnagesMaquette } from "./MesPersonnages";
export { PlancheEtats } from "./PlancheEtats";

export type EcranAccueil =
  | "accueil-douane"
  | "accueil-joueur"
  | "accueil-etats"
  | "personnages"
  | "personnages-mort"
  | "personnages-vide";

export const ECRANS_ACCUEIL: ReadonlyArray<{
  id: EcranAccueil;
  code: string;
  titre: string;
  description: string;
}> = [
  {
    id: "accueil-douane",
    code: "L10a",
    titre: "Accueil · douane à passer",
    description:
      "Écrit accepté, séance réservée : « Passer la douane » avec l'heure et la fenêtre d'entrée, carte Discord.",
  },
  {
    id: "accueil-joueur",
    code: "L10b",
    titre: "Accueil · joueur",
    description: "Joueur whitelisté : accueil actuel, avec la carte « Rejoindre le Discord ».",
  },
  {
    id: "accueil-etats",
    code: "L10",
    titre: "Accueil · états",
    description:
      "Bouton douane avant, pendant, en jeu, après la fenêtre ; carte Discord en chargement et compteur indisponible.",
  },
  {
    id: "personnages",
    code: "L11",
    titre: "Mes personnages",
    description:
      "En vie et mort RP, N° copiable, temps de jeu, dernière partie, emplacement libre, dossier d'entrée du premier.",
  },
  {
    id: "personnages-mort",
    code: "L11",
    titre: "Mes personnages · mort RP ouvert",
    description: "Fiche d'un personnage mort RP, sans dossier d'entrée (2ᵉ emplacement, hors candidature).",
  },
  {
    id: "personnages-vide",
    code: "L11",
    titre: "Mes personnages · vide",
    description: "Aucun personnage : création en jeu, pré-remplie par la candidature.",
  },
];

type Props = {
  ecran: EcranAccueil;
  /**
   * Fenêtre de la hauteur de son contenu (capture longue) au lieu de
   * 1280 × 800. Sans effet sur la planche des états, toujours en longueur.
   */
  pleineHauteur?: boolean;
};

export function MaquettesAccueil({ ecran, pleineHauteur }: Props) {
  switch (ecran) {
    case "accueil-douane":
      return <AccueilMaquette variante="douane" pleineHauteur={pleineHauteur} />;
    case "accueil-joueur":
      return <AccueilMaquette variante="joueur" pleineHauteur={pleineHauteur} />;
    case "accueil-etats":
      return <PlancheEtats />;
    case "personnages":
      return <MesPersonnagesMaquette pleineHauteur={pleineHauteur} />;
    case "personnages-mort":
      return <MesPersonnagesMaquette choixInitial={PERSONNAGES[1]?.id} pleineHauteur={pleineHauteur} />;
    case "personnages-vide":
      return (
        <MesPersonnagesMaquette personnages={[]} emplacements={EMPLACEMENTS.base} pleineHauteur={pleineHauteur} />
      );
    default:
      return <p style={{ padding: 24 }}>Écran inconnu : {String(ecran)}</p>;
  }
}
