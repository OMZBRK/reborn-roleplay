// MAQUETTES L1-L9 — un composant nommé par écran (dev seulement).
//
// Chaque écran se rend dans la coque du launcher (rail + barre du haut) avec
// ses données fictives ; `coque={false}` ne rend que la zone de contenu.
// Les onglets restent cliquables : chaque écran est un point de départ.

import type { Scenario } from "./contexte";
import { EcranFormulaire } from "./Formulaire";
import { SuiviCandidature, type VarianteSuivi } from "./Suivi";
import { ReservationSeance, type VarianteSeance } from "./Seance";
import {
  DOSSIER_A_COMPLETER,
  DOSSIER_COMPLET,
  DOSSIER_EN_COURS,
  DOSSIER_ERREURS,
  DOSSIER_NOM_CLAN,
  DOSSIER_REFUS_API,
  type Dossier,
} from "./donnees";

type Props = { coque?: boolean };

/** L1 — onglet 1 « Vous » : âge en nombre, années de RP, univers, disponibilités (trop courtes), découverte, Discord lié. */
export function L1Vous({ coque }: Props) {
  return <EcranFormulaire initial={DOSSIER_EN_COURS} onglet="vous" coque={coque} />;
}

/** L2 — onglet 2 « Personnage » : prénom, nom de famille libre (pas de clan), « En jeu, ton personnage s'appellera … », type Ninja / Civil, sexe, âge et tranche. */
export function L2Personnage({ coque }: Props) {
  return <EcranFormulaire initial={DOSSIER_EN_COURS} onglet="personnage" coque={coque} />;
}

/** L3 — onglet 3 « Village » : six villages (emblèmes du mod), seul choix de faction ; clans du village pour information, sans sélection (Q4). */
export function L3Village({ coque }: Props) {
  return <EcranFormulaire initial={DOSSIER_EN_COURS} onglet="village" coque={coque} />;
}

/** L4 — onglet 4 « Histoire » : barre G · I · S · Titre · Liste · Citation, mots + caractères, 150 à 12 000, zone qui grandit, objectifs, support. */
export function L4Histoire({ coque }: Props) {
  return <EcranFormulaire initial={DOSSIER_EN_COURS} onglet="histoire" coque={coque} />;
}

/** L5 — onglet 5 « Voix » : test du micro (vu-mètre), enregistrement 0:42 / 1:00, Réécouter, Refaire. */
export function L5Voix({ coque }: Props) {
  return <EcranFormulaire initial={DOSSIER_EN_COURS} onglet="voix" coque={coque} />;
}

/** L6 — onglet 6 « Envoi » : « Il reste à compléter », relecture repliable, consentements, envoi. */
export function L6Envoi({ coque, complet = false }: Props & { complet?: boolean }) {
  return <EcranFormulaire initial={complet ? DOSSIER_COMPLET : DOSSIER_A_COMPLETER} onglet="envoi" coque={coque} />;
}

export type VarianteErreurs = "champs" | "nom" | "envoi" | "micro" | "voix";

const SC_CHAMPS: Scenario = { montrerErreurs: true, enregistrement: "hors-ligne" };
const SC_NOM: Scenario = { montrerErreurs: true };
const SC_ENVOI: Scenario = { erreurApi: true };
const SC_MICRO: Scenario = { micRefuse: true };
const DOSSIER_SANS_VOIX: Dossier = { ...DOSSIER_EN_COURS, micTeste: false, voixDuree: null };
const DOSSIER_VOIX_COURTE: Dossier = { ...DOSSIER_EN_COURS, voixDuree: 8 };
const SC_VOIX: Scenario = { montrerErreurs: true };

/**
 * L7 — états d'erreur.
 * - champs : âge 14, disponibilités trop longues (compteur rouge), « Hors ligne — gardé sur ce PC » ;
 * - nom : nom de famille « Uchiha » refusé, c'est un nom de clan (Q4) ;
 * - envoi : envoi refusé, message de l'API traduit champ par champ, réponse brute repliée ;
 * - micro : Windows refuse le micro au launcher ;
 * - voix : enregistrement trop court (8 s).
 */
export function L7Erreurs({ coque, variante = "champs" }: Props & { variante?: VarianteErreurs }) {
  switch (variante) {
    case "champs":
      return <EcranFormulaire initial={DOSSIER_ERREURS} onglet="vous" scenario={SC_CHAMPS} coque={coque} />;
    case "nom":
      return <EcranFormulaire initial={DOSSIER_NOM_CLAN} onglet="personnage" scenario={SC_NOM} coque={coque} />;
    case "envoi":
      return <EcranFormulaire initial={DOSSIER_REFUS_API} onglet="envoi" scenario={SC_ENVOI} coque={coque} />;
    case "micro":
      return <EcranFormulaire initial={DOSSIER_SANS_VOIX} onglet="voix" scenario={SC_MICRO} coque={coque} />;
    case "voix":
      return <EcranFormulaire initial={DOSSIER_VOIX_COURTE} onglet="voix" scenario={SC_VOIX} coque={coque} />;
  }
}

/** L8 — suivi après l'envoi : frise, état de l'écrit, fil avec le staff. */
export function L8Suivi({ coque, variante = "relecture" }: Props & { variante?: VarianteSuivi }) {
  return <SuiviCandidature variante={variante} coque={coque} />;
}

/** L9 — réservation d'une séance de douane : jours, places restantes, convocation, fenêtre H−15 / H+30, rappel. */
export function L9Seance({ coque, variante = "choix" }: Props & { variante?: VarianteSeance }) {
  return <ReservationSeance variante={variante} coque={coque} />;
}
