// Onglet 1 « Vous » (partie HRP), §2.3, maquette L1. Retirés le 24/10 :
// « Ton expérience du roleplay » et « Pourquoi Reborn ? » (l'oral en jeu les
// remplace).

import { useId, useState } from "react";
import { BadgeCheck } from "lucide-react";
import { DiscordGlyph } from "../../home/FooterMega";
import { useFormulaire } from "../contexte";
import { messageAge, messageTexte } from "../regles";
import { Champ, ChampNombre, ChampTexte, Facultatif, Liste, Section, Segments } from "../ui";

export function OngletVous() {
  const { f, maj, options, montrerErreurs, erreursApi, discord } = useFormulaire();
  const b = options.bornes;
  const l = options.listes;
  const idAge = useId();
  const idSource = useId();
  const [ageQuitte, setAgeQuitte] = useState(false);
  const erreurAge = messageAge(f.playerAge, b.playerAge);
  // Un âge saisi mais hors bornes se signale tout de suite ; un champ vide, à la sortie.
  const ageVisible = erreurAge !== null && (ageQuitte || montrerErreurs || f.playerAge !== "");
  const erreurChoix = (champ: "rpYears" | "narutoKnowledge") =>
    erreursApi[champ] ?? (montrerErreurs && !f[champ] ? "Choisis une réponse." : null);

  return (
    <>
      <Section
        titre="Toi, derrière l'écran"
        chapo="Cette partie parle de toi, pas de ton personnage. Elle reste entre toi et l'équipe whitelist."
      />

      <Champ
        label="Quel âge as-tu ?"
        htmlFor={idAge}
        ancre="playerAge"
        aide={`Reborn accueille les joueurs à partir de ${b.playerAge.min} ans.`}
        erreur={erreursApi.playerAge ?? (ageVisible ? erreurAge : null)}
      >
        <ChampNombre
          id={idAge}
          valeur={f.playerAge}
          onChange={(v) => maj("playerAge", v)}
          onBlur={() => setAgeQuitte(true)}
          min={b.playerAge.min}
          max={b.playerAge.max}
          invalide={ageVisible || !!erreursApi.playerAge}
          aDroite={
            <span className="cand-number-range">
              Entre {b.playerAge.min} et {b.playerAge.max}
            </span>
          }
        />
      </Champ>

      <Champ label="Depuis combien de temps fais-tu du roleplay ?" ancre="rpYears" erreur={erreurChoix("rpYears")}>
        <Segments label="Années de roleplay" options={l.rpYears} valeur={f.rpYears} onChange={(v) => maj("rpYears", v)} />
      </Champ>

      <Champ label="Ta connaissance de l'univers Naruto" ancre="narutoKnowledge" erreur={erreurChoix("narutoKnowledge")}>
        <Segments
          label="Connaissance de l'univers"
          options={l.narutoKnowledge}
          valeur={f.narutoKnowledge}
          onChange={(v) => maj("narutoKnowledge", v)}
        />
      </Champ>

      <ChampTexte
        label="Tes disponibilités"
        ancre="availability"
        valeur={f.availability}
        onChange={(v) => maj("availability", v)}
        bornes={b.availability}
        message={messageTexte(f.availability, b.availability)}
        forcer={montrerErreurs}
        erreurApi={erreursApi.availability}
        aide="Soirs, week-ends… Ça sert à placer ta douane."
        minLignes={2}
      />

      <Champ
        label="Comment as-tu découvert le serveur ?"
        htmlFor={idSource}
        ancre="discoverySource"
        droite={<Facultatif />}
        aide="Ça nous dit où parler de Reborn."
        erreur={erreursApi.discoverySource ?? null}
      >
        <Liste
          id={idSource}
          options={l.discoverySource}
          valeur={f.discoverySource}
          onChange={(v) => maj("discoverySource", v)}
          vide="Pas de réponse"
        />
      </Champ>

      <Champ
        label="Ton Discord"
        aide="Lié à l'arrivée sur le launcher, rien à saisir. Les annonces de la whitelist passent par lui."
      >
        <div className="cand-readonly">
          <span className="grid h-7 w-7 place-items-center rounded-full bg-[#5865f2] text-white">
            <DiscordGlyph className="h-3.5 w-3.5" />
          </span>
          {discord ? (
            <>
              <strong>{discord}</strong>
              <span className="ml-auto inline-flex items-center gap-1.5 text-[12px] font-semibold text-success">
                <BadgeCheck size={14} aria-hidden />
                Lié
              </span>
            </>
          ) : (
            <span>Aucun compte Discord lié à ce compte Reborn.</span>
          )}
        </div>
      </Champ>
    </>
  );
}
