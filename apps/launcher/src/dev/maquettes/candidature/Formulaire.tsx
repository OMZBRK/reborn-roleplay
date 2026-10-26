// MAQUETTES L1-L7 — le formulaire à six onglets dans la coque (dev seulement).
//
// Onglets mieux que Ketto (§2.2) : un onglet complet porte une coche verte ;
// un onglet incomplet garde son numéro et une pastille avec le nombre de
// manques (rouge s'il contient une erreur bloquante : texte trop long, âge
// hors bornes), les mêmes nombres que « Il reste à compléter » (onglet Envoi). A / E changent d'onglet hors d'un champ de saisie,
// Ctrl+PgPréc / Ctrl+PgSuiv partout.

import { useEffect, type ReactNode } from "react";
import { ArrowRight, Check } from "lucide-react";
import { CoqueCandidature } from "./Coque";
import { DossierProvider, useDossier, type Scenario } from "./contexte";
import {
  BORNES,
  ONGLETS,
  longueur,
  manquesDe,
  messageAge,
  messageNomFamille,
  type Dossier,
  type OngletId,
} from "./donnees";
import { EnTete, Enregistrement } from "./ui";
import { OngletVous } from "./onglets/Vous";
import { OngletPersonnage } from "./onglets/Personnage";
import { OngletVillage } from "./onglets/Village";
import { OngletHistoire } from "./onglets/Histoire";
import { OngletVoix } from "./onglets/Voix";
import { OngletEnvoi } from "./onglets/Envoi";

/** Erreur « dure » : un texte au-delà du maximum, un âge saisi hors bornes, un nom de famille refusé (nom de clan). */
function erreurDure(d: Dossier, o: OngletId): boolean {
  const trop = (v: string, max: number) => longueur(v) > max;
  switch (o) {
    case "vous":
      return (
        (d.playerAge !== "" && messageAge(d.playerAge) !== null) || trop(d.availability, BORNES.availability.max)
      );
    case "personnage":
      return d.nomFamille.trim() !== "" && messageNomFamille(d.nomFamille) !== null;
    case "histoire":
      return trop(d.history, BORNES.history.max) || trop(d.objectives, BORNES.objectives.max);
    default:
      return false;
  }
}

function OngletsFormulaire() {
  const { dossier: d, onglet, allerA } = useDossier();
  return (
    <>
      {ONGLETS.map((o) => {
        const n = o.id === "envoi" ? 0 : manquesDe(d, o.id).length;
        const complet = o.id !== "envoi" && n === 0;
        const pastille = !complet && o.id !== "envoi";
        return (
          <button
            key={o.id}
            type="button"
            className="mqc-tab"
            data-active={onglet === o.id || undefined}
            data-complet={complet || undefined}
            aria-current={onglet === o.id ? "step" : undefined}
            onClick={() => allerA(o.id)}
          >
            {complet ? (
              <Check size={15} strokeWidth={3} className="mqc-tab-check" aria-label="complet" />
            ) : (
              <span className="mqc-tab-num">{o.numero}</span>
            )}
            {o.libelle}
            {pastille && (
              <span className="mqc-tab-pill" data-ton={erreurDure(d, o.id) ? "danger" : undefined} title={`${n} à compléter`}>
                {n}
              </span>
            )}
          </button>
        );
      })}
    </>
  );
}

const CONTENU: Record<OngletId, () => ReactNode> = {
  vous: () => <OngletVous />,
  personnage: () => <OngletPersonnage />,
  village: () => <OngletVillage />,
  histoire: () => <OngletHistoire />,
  voix: () => <OngletVoix />,
  envoi: () => <OngletEnvoi />,
};

function estChampDeSaisie(t: EventTarget | null): boolean {
  if (!(t instanceof HTMLElement)) return false;
  return t.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(t.tagName);
}

export function FormulaireCandidature({ coque = true }: { coque?: boolean }) {
  const { dossier: d, onglet, allerA, enregistrement } = useDossier();
  const i = ONGLETS.findIndex((o) => o.id === onglet);
  const courant = ONGLETS[i];
  const precedent = i > 0 ? ONGLETS[i - 1] : null;
  const suivant = i < ONGLETS.length - 1 ? ONGLETS[i + 1] : null;
  const n = onglet === "envoi" ? 0 : manquesDe(d, onglet).length;

  // Changer d'onglet au clavier.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      let delta = 0;
      if (e.ctrlKey && e.key === "PageUp") delta = -1;
      else if (e.ctrlKey && e.key === "PageDown") delta = 1;
      else if (!e.ctrlKey && !e.altKey && !e.metaKey && !estChampDeSaisie(e.target)) {
        if (e.key === "a" || e.key === "A") delta = -1;
        else if (e.key === "e" || e.key === "E") delta = 1;
      }
      if (delta === 0) return;
      const j = i + delta;
      if (j < 0 || j >= ONGLETS.length) return;
      e.preventDefault();
      allerA(ONGLETS[j].id);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [i, allerA]);

  const surtitre =
    courant.partie === null ? (
      <>
        Étape 6 sur 6<span className="mqc-eyebrow-sep" aria-hidden />
        Relecture et envoi
      </>
    ) : (
      <>
        Étape {courant.numero} sur 6<span className="mqc-eyebrow-sep" aria-hidden />
        Partie {courant.partie}
      </>
    );

  return (
    <CoqueCandidature onglets={<OngletsFormulaire />} coque={coque} cle={onglet}>
      <EnTete
        surtitre={surtitre}
        titre="Candidature whitelist"
        chapo="Le passage obligé pour rejoindre Reborn. Une vingtaine de minutes ; tout s'enregistre au fil de l'eau, tu peux reprendre plus tard, même depuis un autre PC."
        droite={<Enregistrement etat={enregistrement} />}
      />

      {CONTENU[onglet]()}

      {onglet !== "envoi" && (
        <div className="mqc-actions">
          {precedent && (
            <button type="button" className="mqc-prev" onClick={() => allerA(precedent.id)}>
              Étape précédente
            </button>
          )}
          {suivant && (
            <button type="button" className="wl-btn-primary glow wl-btn-cta-big mqc-cta" onClick={() => allerA(suivant.id)}>
              Continuer <ArrowRight size={16} aria-hidden />
            </button>
          )}
          <span className="mqc-actions-note">
            {n === 0
              ? "Onglet complet."
              : `${n} champ${n > 1 ? "s" : ""} à compléter ici · tu peux y revenir plus tard.`}
          </span>
        </div>
      )}
    </CoqueCandidature>
  );
}

/** Formulaire autonome : son propre brouillon fictif, onglets cliquables. */
export function EcranFormulaire({
  initial,
  onglet,
  scenario,
  coque = true,
}: {
  initial: Dossier;
  onglet: OngletId;
  scenario?: Scenario;
  coque?: boolean;
}) {
  return (
    <DossierProvider initial={initial} onglet={onglet} scenario={scenario}>
      <FormulaireCandidature coque={coque} />
    </DossierProvider>
  );
}
