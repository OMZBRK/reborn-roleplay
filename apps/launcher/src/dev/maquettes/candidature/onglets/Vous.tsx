// MAQUETTE L1 — onglet 1 « Vous » (partie HRP), §2.3. Retirés le 24/10 (l'oral
// en jeu les remplace) : « Ton expérience du roleplay » et « Pourquoi Reborn ? ».

import { useId, useState } from "react";
import { BadgeCheck } from "lucide-react";
import { DiscordGlyph } from "../../../../components/home/FooterMega";
import { useDossier } from "../contexte";
import {
  BORNES,
  CONNAISSANCES,
  DECOUVERTE,
  JOUEUR,
  RP_YEARS,
  messageAge,
  messageTexte,
} from "../donnees";
import { Champ, ChampNombre, ChampTexte, Facultatif, Liste, Section, Segments } from "../ui";

export function OngletVous() {
  const { dossier: d, maj, scenario } = useDossier();
  const idAge = useId();
  const idSource = useId();
  const [ageQuitte, setAgeQuitte] = useState(false);
  const erreurAge = messageAge(d.playerAge);
  // Un âge saisi mais hors bornes se signale tout de suite ; un champ vide, à la sortie.
  const ageVisible = erreurAge !== null && (ageQuitte || scenario.montrerErreurs || d.playerAge !== "");

  return (
    <>
      <Section
        titre="Toi, derrière l'écran"
        chapo="Cette partie parle de toi, pas de ton personnage. Elle reste entre toi et l'équipe whitelist."
      />

      <Champ
        label="Quel âge as-tu ?"
        htmlFor={idAge}
        aide="Reborn accueille les joueurs à partir de 15 ans."
        erreur={ageVisible ? erreurAge : null}
      >
        <ChampNombre
          id={idAge}
          valeur={d.playerAge}
          onChange={(v) => maj("playerAge", v)}
          onBlur={() => setAgeQuitte(true)}
          min={BORNES.playerAge.min}
          max={BORNES.playerAge.max}
          invalide={ageVisible}
          aDroite={<span className="mqc-number-range">Entre 15 et 99</span>}
        />
      </Champ>

      <Champ label="Depuis combien de temps fais-tu du roleplay ?">
        <Segments label="Années de roleplay" options={RP_YEARS} valeur={d.rpYears} onChange={(v) => maj("rpYears", v)} />
      </Champ>

      <Champ label="Ta connaissance de l'univers Naruto">
        <Segments
          label="Connaissance de l'univers"
          options={CONNAISSANCES}
          valeur={d.narutoKnowledge}
          onChange={(v) => maj("narutoKnowledge", v)}
        />
      </Champ>

      <ChampTexte
        label="Tes disponibilités"
        valeur={d.availability}
        onChange={(v) => maj("availability", v)}
        bornes={BORNES.availability}
        message={messageTexte(d.availability, BORNES.availability)}
        aide="Soirs, week-ends… Ça sert à placer ta douane."
        minLignes={2}
      />

      <Champ
        label="Comment as-tu découvert le serveur ?"
        htmlFor={idSource}
        droite={<Facultatif />}
        aide="Ça nous dit où parler de Reborn."
      >
        <Liste
          id={idSource}
          options={DECOUVERTE}
          valeur={d.discoverySource}
          onChange={(v) => maj("discoverySource", v)}
        />
      </Champ>

      <Champ label="Ton Discord" aide="Lié à l'arrivée sur le launcher, rien à saisir. Les annonces de la whitelist passent par lui.">
        <div className="mqc-readonly">
          <span className="grid h-7 w-7 place-items-center rounded-full bg-[#5865f2] text-white">
            <DiscordGlyph className="h-3.5 w-3.5" />
          </span>
          <strong>{JOUEUR.discord}</strong>
          <span className="ml-auto inline-flex items-center gap-1.5 text-[12px] font-semibold text-success">
            <BadgeCheck size={14} aria-hidden />
            Lié
          </span>
        </div>
      </Champ>
    </>
  );
}
