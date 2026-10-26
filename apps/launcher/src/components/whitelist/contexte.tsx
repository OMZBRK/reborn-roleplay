// État partagé entre les six onglets du formulaire : ce qu'on tape dans un
// onglet se retrouve dans la relecture de l'onglet Envoi, les coches des
// onglets suivent. Le formulaire lui-même (brouillon, enregistrement
// automatique, envoi) vit dans Formulaire.tsx.

import { createContext, useContext } from "react";
import type { CandidatureOptions, VoixVue } from "../../lib/candidature";
import type { Formulaire, Manque, OngletId, Partie } from "./regles";
import type { EtatEnregistrement } from "./ui";

/** Enregistrement vocal présent dans le launcher (fait ici, ou relu du serveur). */
export type VoixLocale = {
  blob: Blob;
  dureeSec: number;
  /** Déjà sur le serveur (brouillon ou candidature). */
  depose: boolean;
};

export type ValeurFormulaire = {
  f: Formulaire;
  maj: <K extends keyof Formulaire>(champ: K, valeur: Formulaire[K]) => void;
  options: CandidatureOptions;
  /** « nouvelle » : tout le formulaire ; « revoir » : seules les parties rouvertes. */
  mode: "nouvelle" | "revoir";
  partiesRouvertes: Partie[];
  editable: (o: OngletId) => boolean;
  onglet: OngletId;
  allerA: (o: OngletId, champ?: string) => void;
  enregistrement: EtatEnregistrement;
  manques: Manque[];
  /** Messages visibles sous tous les champs (après un envoi refusé). */
  montrerErreurs: boolean;
  /** Messages de l'API par champ (dernier envoi refusé), effacés à la modification du champ. */
  erreursApi: Record<string, string>;
  /** Enregistrement du serveur qui compte pour l'envoi (brouillon, ou candidature à revoir). */
  voixServeur: VoixVue | null;
  setVoixServeur: (v: VoixVue | null) => void;
  /** Enregistrement présent dans le launcher (réécoute, forme d'onde). */
  voixLocale: VoixLocale | null;
  setVoixLocale: (v: VoixLocale | null) => void;
  /** Un enregistrement valable compte pour l'envoi. */
  voixOk: boolean;
  /** Pseudo Discord lié au compte, ou null. */
  discord: string | null;
};

export const FormulaireCtx = createContext<ValeurFormulaire | null>(null);

export function useFormulaire(): ValeurFormulaire {
  const v = useContext(FormulaireCtx);
  if (!v) throw new Error("useFormulaire hors de FormulaireCtx");
  return v;
}
