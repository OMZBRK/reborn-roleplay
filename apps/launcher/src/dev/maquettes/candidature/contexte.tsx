// MAQUETTES — état partagé entre les écrans de la candidature (dev seulement).
//
// Un seul brouillon fictif pour les six onglets : ce qu'on tape dans un
// onglet se retrouve dans la relecture de l'onglet Envoi, les coches des
// onglets suivent. L'indicateur « Enregistré » simule l'enregistrement
// automatique (1,5 s après la dernière frappe, §2.5).

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import type { Dossier, OngletId } from "./donnees";

export type EtatEnregistrement = "enregistre" | "enregistrement" | "hors-ligne" | "echec";

/** Ce que l'écran photographié doit montrer en plus du brouillon. */
export type Scenario = {
  /** Messages d'erreur visibles sous tous les champs (sinon : à la sortie du champ). */
  montrerErreurs?: boolean;
  /** Onglet Envoi : bandeau « envoi refusé » avec la réponse de l'API. */
  erreurApi?: boolean;
  /** Onglet Voix : Windows refuse le micro au launcher. */
  micRefuse?: boolean;
  /** Indicateur figé (« Hors ligne — gardé sur ce PC »). */
  enregistrement?: EtatEnregistrement;
};

type Valeur = {
  dossier: Dossier;
  maj: <K extends keyof Dossier>(champ: K, valeur: Dossier[K]) => void;
  enregistrement: EtatEnregistrement;
  onglet: OngletId;
  allerA: (onglet: OngletId) => void;
  scenario: Scenario;
};

const Ctx = createContext<Valeur | null>(null);

type ProviderProps = {
  initial: Dossier;
  onglet: OngletId;
  scenario?: Scenario;
  children: ReactNode;
};

export function DossierProvider({ initial, onglet, scenario, children }: ProviderProps) {
  const [dossier, setDossier] = useState<Dossier>(initial);
  const [enregistrement, setEnregistrement] = useState<EtatEnregistrement>("enregistre");
  const [ongletCourant, setOngletCourant] = useState<OngletId>(onglet);
  const timer = useRef<number | null>(null);

  // Autre écran demandé : on repart de son préréglage et de son onglet.
  useEffect(() => setDossier(initial), [initial]);
  useEffect(() => setOngletCourant(onglet), [onglet]);

  useEffect(
    () => () => {
      if (timer.current !== null) window.clearTimeout(timer.current);
    },
    [],
  );

  const maj = useCallback(<K extends keyof Dossier>(champ: K, valeur: Dossier[K]) => {
    setDossier((d) => ({ ...d, [champ]: valeur }));
    setEnregistrement("enregistrement");
    if (timer.current !== null) window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => {
      timer.current = null;
      setEnregistrement("enregistre");
    }, 1500);
  }, []);

  const allerA = useCallback((o: OngletId) => setOngletCourant(o), []);

  const sc = scenario ?? SANS_SCENARIO;
  const valeur = useMemo<Valeur>(
    () => ({
      dossier,
      maj,
      enregistrement: sc.enregistrement ?? enregistrement,
      onglet: ongletCourant,
      allerA,
      scenario: sc,
    }),
    [dossier, maj, enregistrement, ongletCourant, allerA, sc],
  );
  return <Ctx.Provider value={valeur}>{children}</Ctx.Provider>;
}

const SANS_SCENARIO: Scenario = {};

export function useDossier(): Valeur {
  const v = useContext(Ctx);
  if (!v) throw new Error("useDossier hors de DossierProvider");
  return v;
}

// ──────────────────────────────────────────────────────
//  Navigation entre écrans (posée par MaquettesCandidature)
// ──────────────────────────────────────────────────────

type Nav = { aller: (ecran: string) => void };
const NavCtx = createContext<Nav | null>(null);

export function NavProvider({ aller, children }: { aller: (ecran: string) => void; children: ReactNode }) {
  const v = useMemo(() => ({ aller }), [aller]);
  return <NavCtx.Provider value={v}>{children}</NavCtx.Provider>;
}

/** Navigation vers un autre écran de maquette ; sans effet hors de MaquettesCandidature. */
export function useAllerEcran(): (ecran: string) => void {
  const nav = useContext(NavCtx);
  return nav?.aller ?? noop;
}

function noop() {}
