// MAQUETTE L2 — onglet 2 « Personnage » (partie RP), §2.3 et décisions du 24/10.
//
// Q4 : la candidature ne propose aucun clan. Le nom de famille est libre,
// dans les règles du mod (3 à 20 lettres, jamais le nom d'un clan) : il
// remplace la grille des clans, et la phrase « En jeu, ton personnage
// s'appellera … » montre le nom complet tel qu'il apparaîtra.
// Q3 : type Ninja / Civil dès la beta, comme Ketto (segments, aide courte).
// Q13 : à la création en jeu, le prénom est pré-rempli et reste modifiable,
// l'âge est verrouillé (le village aussi, onglet 3), le sexe reste libre.
// Les objectifs sont dans l'onglet Histoire (§2.3, maquette L4). « Caractère
// et apparence » est retiré (complément du 24/10) : l'oral en jeu le remplace.

import { useId, useState } from "react";
import { useDossier } from "../contexte";
import {
  BORNES,
  SEXES,
  TYPES,
  messageAgePerso,
  messageNomFamille,
  messagePrenom,
  trancheAge,
  villageDe,
} from "../donnees";
import { Champ, ChampNombre, Section, Segments } from "../ui";

export function OngletPersonnage() {
  const { dossier: d, maj, scenario } = useDossier();
  const idPrenom = useId();
  const idNom = useId();
  const idAge = useId();
  const [prenomQuitte, setPrenomQuitte] = useState(false);
  const [nomQuitte, setNomQuitte] = useState(false);
  const [ageQuitte, setAgeQuitte] = useState(false);

  const erreurPrenom = messagePrenom(d.firstName);
  const prenomVisible = erreurPrenom !== null && (prenomQuitte || scenario.montrerErreurs);
  const erreurNom = messageNomFamille(d.nomFamille);
  const nomVisible = erreurNom !== null && (nomQuitte || scenario.montrerErreurs);
  const erreurAge = messageAgePerso(d.characterAge);
  const ageVisible = erreurAge !== null && (ageQuitte || scenario.montrerErreurs || d.characterAge !== "");
  const tranche = trancheAge(Number(d.characterAge), d.characterType);

  const village = villageDe(d.village);
  // Nom complet seulement avec des valeurs valides : un nom de clan refusé
  // n'apparaît pas dans la phrase.
  const prenom = erreurPrenom === null ? d.firstName.trim() : null;
  const famille = erreurNom === null ? d.nomFamille.trim() : null;

  return (
    <>
      <Section
        titre="Ton personnage"
        chapo="Qui il est, ce qu'il fait, à quoi il ressemble. Son apparence exacte se choisit en jeu, à la création."
      />

      <div className="grid grid-cols-[1fr_1fr] gap-5">
        <Champ
          label="Prénom"
          htmlFor={idPrenom}
          droite={<span className="mqc-optional">3 à 20 lettres</span>}
          aide="Pré-rempli à la création en jeu, tu pourras encore le modifier."
          erreur={prenomVisible ? erreurPrenom : null}
        >
          <input
            id={idPrenom}
            className="mqc-input"
            value={d.firstName}
            maxLength={40}
            aria-invalid={prenomVisible || undefined}
            onChange={(e) => maj("firstName", e.target.value)}
            onBlur={() => setPrenomQuitte(true)}
          />
        </Champ>

        <Champ
          label="Nom de famille"
          htmlFor={idNom}
          droite={<span className="mqc-optional">3 à 20 lettres</span>}
          aide="Libre, sauf un nom de clan : ton personnage commence sans clan."
          erreur={nomVisible ? erreurNom : null}
        >
          <input
            id={idNom}
            className="mqc-input"
            value={d.nomFamille}
            maxLength={40}
            aria-invalid={nomVisible || undefined}
            onChange={(e) => maj("nomFamille", e.target.value)}
            onBlur={() => setNomQuitte(true)}
          />
        </Champ>
      </div>

      <p className="mqc-nameplate" data-incomplet={!prenom || !famille || undefined}>
        <span className="mqc-nameplate-lead">En jeu, ton personnage s'appellera</span>
        <span className="mqc-nameplate-name">
          {prenom ?? "…"} {famille ?? "…"}
        </span>
        <span className="mqc-nameplate-meta">
          Sans clan{village ? ` · ${village.nom}` : ""}
        </span>
      </p>

      <Champ
        label="Type de personnage"
        aide="Un ninja peut aussi exercer des activités civiles (métier, boutique). Un civil ne mène pas de missions ninja."
      >
        <Segments label="Type de personnage" options={TYPES} valeur={d.characterType} onChange={(v) => maj("characterType", v)} />
      </Champ>

      <div className="grid grid-cols-[auto_1fr] gap-10">
        <Champ label="Sexe" aide="Reste libre à la création en jeu.">
          <Segments label="Sexe" options={SEXES} valeur={d.characterSex} onChange={(v) => maj("characterSex", v)} />
        </Champ>

        <Champ
          label="Âge du personnage"
          htmlFor={idAge}
          erreur={ageVisible ? erreurAge : null}
          aide="Entre 10 et 60 ans, verrouillé à la création en jeu."
        >
          <ChampNombre
            id={idAge}
            valeur={d.characterAge}
            onChange={(v) => maj("characterAge", v)}
            onBlur={() => setAgeQuitte(true)}
            min={BORNES.characterAge.min}
            max={BORNES.characterAge.max}
            invalide={ageVisible}
            aDroite={tranche && <span className="mqc-number-tag">{tranche}</span>}
          />
        </Champ>
      </div>
    </>
  );
}
