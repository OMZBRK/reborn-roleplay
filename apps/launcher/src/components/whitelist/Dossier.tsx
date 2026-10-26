// « Mon dossier » : la candidature envoyée, en lecture seule (mêmes blocs que
// la relecture de l'onglet Envoi). Jamais les notes privées du staff : l'API
// ne les envoie pas au candidat.

import { useState } from "react";
import { ecouterMaVoix, type CandidatureOptions, type CandidatureV2 } from "../../lib/candidature";
import { dateDe, quand } from "./format";
import { BlocRelecture, LecteurCompact, type DonneesRelecture } from "./Relecture";
import { depuisCandidature, ONGLETS_FORMULAIRE, type OngletId } from "./regles";
import { EnTete } from "./ui";

export function DossierCandidature({ application, options }: { application: CandidatureV2; options: CandidatureOptions }) {
  const [ouverts, setOuverts] = useState<Set<OngletId>>(() => new Set(ONGLETS_FORMULAIRE.map((o) => o.id)));
  const f = depuisCandidature(application, options);
  const envoyeLe = dateDe(application.submittedAt);
  const voixEffaceeLe = dateDe(application.voiceDeletedAt);
  const voix = application.voix;

  const donnees: DonneesRelecture = {
    f,
    options,
    voix: voix ? (
      <LecteurCompact
        blob={null}
        // La voix ENVOYÉE, pas celle d'un brouillon déposé depuis (« À revoir », après un refus).
        charger={() => ecouterMaVoix(voix.mimeType, "candidature")}
        dureeSec={voix.durationSec}
        max={options.voix.dureeMaxSec}
      />
    ) : voixEffaceeLe ? (
      `Effacé le ${quand(voixEffaceeLe)}`
    ) : null,
    ancien:
      application.formVersion < 2
        ? { motivation: application.motivation, experience: application.experience, appearance: application.appearance }
        : undefined,
  };

  const bascule = (o: OngletId) =>
    setOuverts((s) => {
      const n = new Set(s);
      if (n.has(o)) n.delete(o);
      else n.add(o);
      return n;
    });

  return (
    <>
      <EnTete
        surtitre="Candidature whitelist"
        titre="Mon dossier"
        chapo={
          envoyeLe
            ? `Ce que tu as envoyé le ${quand(envoyeLe)}, tel que l'équipe whitelist le lit.`
            : "Ce que tu as envoyé, tel que l'équipe whitelist le lit."
        }
      />
      <div className="cand-part-label">Partie HRP · toi</div>
      <BlocRelecture onglet="vous" donnees={donnees} ouvert={ouverts.has("vous")} onBascule={() => bascule("vous")} />
      <div className="cand-part-label">Partie RP · ton personnage</div>
      {(["personnage", "village", "histoire", "voix"] as const).map((o) => (
        <BlocRelecture key={o} onglet={o} donnees={donnees} ouvert={ouverts.has(o)} onBascule={() => bascule(o)} />
      ))}
    </>
  );
}
