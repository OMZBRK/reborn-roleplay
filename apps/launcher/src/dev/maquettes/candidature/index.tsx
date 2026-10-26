// MAQUETTES — candidature v2 et douane, côté launcher (dev seulement).
//
// Comme src/dev/demo.ts : jamais importé par l'application, donc absent du
// build de production. Données fictives (donnees.ts), aucun appel à l'API.
// Cahier des charges : docs/gameplay/CANDIDATURE_ET_DOUANE.md §2, §3.3, §6.3.
//
//   <MaquettesCandidature ecran="L4" />       un écran précis
//   <MaquettesCandidature ecran="L7-envoi" /> une variante
//   <MaquettesCandidature selecteur />        + sélecteur flottant d'écrans
//
// Aperçu autonome : http://localhost:1420/src/dev/maquettes/candidature/apercu.html?ecran=L1

import { useCallback, useEffect, useState } from "react";
import { NavProvider } from "./contexte";
import { L1Vous, L2Personnage, L3Village, L4Histoire, L5Voix, L6Envoi, L7Erreurs, L8Suivi, L9Seance } from "./ecrans";

export { L1Vous, L2Personnage, L3Village, L4Histoire, L5Voix, L6Envoi, L7Erreurs, L8Suivi, L9Seance } from "./ecrans";

export const ECRANS_CANDIDATURE = [
  { id: "L1", titre: "Candidature › 1 Vous" },
  { id: "L2", titre: "Candidature › 2 Personnage" },
  { id: "L3", titre: "Candidature › 3 Village, clans en information (aucun choix de clan)" },
  { id: "L4", titre: "Candidature › 4 Histoire" },
  { id: "L5", titre: "Candidature › 5 Voix" },
  { id: "L6", titre: "Candidature › 6 Envoi, il reste à compléter" },
  { id: "L6-complet", titre: "Candidature › 6 Envoi, prêt à envoyer" },
  { id: "L7", titre: "Erreurs › âge, trop court, trop long, hors ligne" },
  { id: "L7-nom", titre: "Erreurs › nom de famille refusé (nom de clan)" },
  { id: "L7-envoi", titre: "Erreurs › envoi refusé par l'API" },
  { id: "L7-micro", titre: "Erreurs › micro refusé par Windows" },
  { id: "L7-voix", titre: "Erreurs › enregistrement trop court" },
  { id: "L8", titre: "Suivi › écrit en relecture" },
  { id: "L8-accepte", titre: "Suivi › écrit accepté" },
  { id: "L8-revoir", titre: "Suivi › écrit à revoir" },
  { id: "L8-refuse", titre: "Suivi › refusé, compte à rebours" },
  { id: "L9", titre: "Douane › choisir une séance" },
  { id: "L9-reservee", titre: "Douane › place réservée" },
] as const;

export type EcranCandidature = (typeof ECRANS_CANDIDATURE)[number]["id"];

const IDS = new Set<string>(ECRANS_CANDIDATURE.map((e) => e.id));

export function estEcranCandidature(id: string | null | undefined): id is EcranCandidature {
  return !!id && IDS.has(id);
}

function rendre(ecran: EcranCandidature, coque: boolean) {
  switch (ecran) {
    case "L1":
      return <L1Vous coque={coque} />;
    case "L2":
      return <L2Personnage coque={coque} />;
    case "L3":
      return <L3Village coque={coque} />;
    case "L4":
      return <L4Histoire coque={coque} />;
    case "L5":
      return <L5Voix coque={coque} />;
    case "L6":
      return <L6Envoi coque={coque} />;
    case "L6-complet":
      return <L6Envoi coque={coque} complet />;
    case "L7":
      return <L7Erreurs coque={coque} variante="champs" />;
    case "L7-nom":
      return <L7Erreurs coque={coque} variante="nom" />;
    case "L7-envoi":
      return <L7Erreurs coque={coque} variante="envoi" />;
    case "L7-micro":
      return <L7Erreurs coque={coque} variante="micro" />;
    case "L7-voix":
      return <L7Erreurs coque={coque} variante="voix" />;
    case "L8":
      return <L8Suivi coque={coque} variante="relecture" />;
    case "L8-accepte":
      return <L8Suivi coque={coque} variante="accepte" />;
    case "L8-revoir":
      return <L8Suivi coque={coque} variante="revoir" />;
    case "L8-refuse":
      return <L8Suivi coque={coque} variante="refuse" />;
    case "L9":
      return <L9Seance coque={coque} variante="choix" />;
    case "L9-reservee":
      return <L9Seance coque={coque} variante="reservee" />;
  }
}

type Props = {
  /** Écran affiché (L1 … L9, variantes dans ECRANS_CANDIDATURE). Défaut : L1. */
  ecran?: EcranCandidature | string;
  /** Appelé quand un bouton de la maquette mène à un autre écran (Envoyer → L8, Choisir une séance → L9…). */
  onEcran?: (ecran: EcranCandidature) => void;
  /** Sélecteur flottant des écrans, au-dessus du rail (jamais sur une capture). */
  selecteur?: boolean;
  /** false : contenu seul, si l'hôte fournit déjà la coque du launcher. */
  coque?: boolean;
};

export function MaquettesCandidature({ ecran = "L1", onEcran, selecteur = false, coque = true }: Props) {
  const depart: EcranCandidature = estEcranCandidature(ecran) ? ecran : "L1";
  const [courant, setCourant] = useState<EcranCandidature>(depart);
  useEffect(() => setCourant(depart), [depart]);

  const aller = useCallback(
    (id: string) => {
      if (!estEcranCandidature(id)) return;
      setCourant(id);
      onEcran?.(id);
    },
    [onEcran],
  );

  return (
    <NavProvider aller={aller}>
      <div key={courant} className="contents">
        {rendre(courant, coque)}
      </div>
      {selecteur && (
        <div className="mqc-picker" role="toolbar" aria-label="Écrans de maquette">
          {ECRANS_CANDIDATURE.map((e) => (
            <button key={e.id} type="button" title={e.titre} aria-pressed={courant === e.id} onClick={() => aller(e.id)}>
              {e.id}
            </button>
          ))}
        </div>
      )}
    </NavProvider>
  );
}
