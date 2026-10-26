// Onglet 2 « Personnage » (partie RP), §2.3 et décisions du 24/10, maquette L2.
//
// Q4 : aucun clan à la candidature. Le nom de famille est libre, dans les
// règles du mod (3 à 20 lettres, jamais le nom d'un clan) ; la phrase « En
// jeu, ton personnage s'appellera … » montre le nom complet.
// Q3 : type Ninja / Civil. Q13 : à la création en jeu, prénom et nom de
// famille pré-remplis et modifiables, village et âge verrouillés, sexe libre.
// Retiré le 24/10 : « Caractère et apparence ».

import { useId, useState } from "react";
import { useFormulaire } from "../contexte";
import { messageAgePerso, messageNomFamille, messagePrenom, trancheAge } from "../regles";
import { Champ, ChampNombre, Section, Segments } from "../ui";

export function OngletPersonnage() {
  const { f, maj, options, montrerErreurs, erreursApi } = useFormulaire();
  const b = options.bornes;
  const l = options.listes;
  const idPrenom = useId();
  const idNom = useId();
  const idAge = useId();
  const [prenomQuitte, setPrenomQuitte] = useState(false);
  const [nomQuitte, setNomQuitte] = useState(false);
  const [ageQuitte, setAgeQuitte] = useState(false);

  const erreurPrenom = messagePrenom(f.firstName, b.firstName);
  const prenomVisible = erreurPrenom !== null && (prenomQuitte || montrerErreurs);
  const erreurNom = messageNomFamille(f.lastName, b.lastName, options);
  // Un nom de clan se signale tout de suite (pas seulement à la sortie du champ).
  const nomClan = erreurNom !== null && erreurNom.startsWith("«");
  const nomVisible = erreurNom !== null && (nomQuitte || montrerErreurs || nomClan);
  const erreurAge = messageAgePerso(f.characterAge, b.characterAge);
  const ageVisible = erreurAge !== null && (ageQuitte || montrerErreurs || f.characterAge !== "");
  const tranche = trancheAge(f.characterAge, f.characterType);
  const erreurChoix = (champ: "characterType" | "characterSex") =>
    erreursApi[champ] ?? (montrerErreurs && !f[champ] ? "Choisis une réponse." : null);

  const village = options.villages.find((v) => v.valeur === f.village) ?? null;
  // Nom complet seulement avec des valeurs valides : un nom de clan refusé
  // n'apparaît pas dans la phrase.
  const prenom = erreurPrenom === null ? f.firstName.trim() : null;
  const famille = erreurNom === null ? f.lastName.trim() : null;
  const regleNom = `${b.firstName.min} à ${b.firstName.max} lettres`;

  return (
    <>
      <Section
        titre="Ton personnage"
        chapo="Qui il est et ce qu'il fait. Son apparence se choisit en jeu, à la création."
      />

      <div className="cand-grille-2 grid grid-cols-[1fr_1fr] gap-5">
        <Champ
          label="Prénom"
          htmlFor={idPrenom}
          ancre="firstName"
          droite={<span className="cand-optional">{regleNom}</span>}
          aide="Pré-rempli à la création en jeu, tu pourras encore le modifier."
          erreur={erreursApi.firstName ?? (prenomVisible ? erreurPrenom : null)}
        >
          <input
            id={idPrenom}
            className="cand-input"
            value={f.firstName}
            maxLength={40}
            autoComplete="off"
            spellCheck={false}
            aria-invalid={prenomVisible || !!erreursApi.firstName || undefined}
            onChange={(e) => maj("firstName", e.target.value)}
            onBlur={() => setPrenomQuitte(true)}
          />
        </Champ>

        <Champ
          label="Nom de famille"
          htmlFor={idNom}
          ancre="lastName"
          droite={<span className="cand-optional">{`${b.lastName.min} à ${b.lastName.max} lettres`}</span>}
          aide="Libre, sauf un nom de clan : ton personnage commence sans clan. Modifiable à la création en jeu."
          erreur={erreursApi.lastName ?? (nomVisible ? erreurNom : null)}
        >
          <input
            id={idNom}
            className="cand-input"
            value={f.lastName}
            maxLength={40}
            autoComplete="off"
            spellCheck={false}
            aria-invalid={nomVisible || !!erreursApi.lastName || undefined}
            onChange={(e) => maj("lastName", e.target.value)}
            onBlur={() => setNomQuitte(true)}
          />
        </Champ>
      </div>

      <p className="cand-nameplate" data-incomplet={!prenom || !famille || undefined}>
        <span className="cand-nameplate-lead">En jeu, ton personnage s'appellera</span>
        <span className="cand-nameplate-name">
          {prenom || "…"} {famille || "…"}
        </span>
        <span className="cand-nameplate-meta">Sans clan{village ? ` · ${village.valeur}` : ""}</span>
      </p>

      <Champ
        label="Type de personnage"
        ancre="characterType"
        aide="Un ninja peut aussi exercer des activités civiles (métier, boutique). Un civil ne mène pas de missions ninja."
        erreur={erreurChoix("characterType")}
      >
        <Segments
          label="Type de personnage"
          options={l.characterType}
          valeur={f.characterType}
          onChange={(v) => maj("characterType", v)}
        />
      </Champ>

      <div className="cand-grille-2 grid grid-cols-[auto_1fr] gap-10">
        <Champ label="Sexe" ancre="characterSex" aide="Reste libre à la création en jeu." erreur={erreurChoix("characterSex")}>
          <Segments label="Sexe" options={l.characterSex} valeur={f.characterSex} onChange={(v) => maj("characterSex", v)} />
        </Champ>

        <Champ
          label="Âge du personnage"
          htmlFor={idAge}
          ancre="characterAge"
          erreur={erreursApi.characterAge ?? (ageVisible ? erreurAge : null)}
          aide={`Entre ${b.characterAge.min} et ${b.characterAge.max} ans, verrouillé à la création en jeu.`}
        >
          <ChampNombre
            id={idAge}
            valeur={f.characterAge}
            onChange={(v) => maj("characterAge", v)}
            onBlur={() => setAgeQuitte(true)}
            min={b.characterAge.min}
            max={b.characterAge.max}
            invalide={ageVisible || !!erreursApi.characterAge}
            aDroite={tranche && <span className="cand-number-tag">{tranche}</span>}
          />
        </Champ>
      </div>
    </>
  );
}
