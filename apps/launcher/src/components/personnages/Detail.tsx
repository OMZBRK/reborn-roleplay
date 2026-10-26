// « Mes personnages » : fiche du personnage choisi, en lecture seule.
// Personnage issu de la candidature : son dossier d'entrée (histoire,
// objectifs, date de la douane). Reprise de la maquette validée L11 ; la
// rubrique « Caractère et apparence » n'existe plus en v2 (DECISIONS 24/10).

import { useState } from "react";
import { useNavigate } from "react-router";
import { FileCheck2, Info, LifeBuoy } from "lucide-react";
import { LIBELLE_SEXE, LIBELLE_TYPE, type PersonnageCompte } from "../../lib/personnages";
import { Blason } from "./Cartes";
import { dateDe, dateJour, memeJour, texteBrut } from "./format";
import { grade, nomComplet } from "./identite";

/** Ligne d'accroche sous le nom : douane, mort RP, suppression ou création. */
function accroche(perso: PersonnageCompte): { texte: string; doux: string } {
  const cree = dateDe(perso.createdAt);
  const creeLe = cree ? ` · créé le ${dateJour(cree)}` : "";
  if (perso.dossier) {
    const douane = dateDe(perso.dossier.oralDecidedAt);
    if (!douane) return { texte: "Repris de ta candidature", doux: creeLe };
    const texte = `Validé à la douane le ${dateJour(douane)}`;
    // Les séances de douane ont lieu le soir : création dans la foulée.
    if (cree && memeJour(cree, douane)) {
      return { texte, doux: cree.getHours() >= 18 ? " · créé le soir même" : " · créé le jour même" };
    }
    return { texte, doux: creeLe };
  }
  if (perso.state === "DEAD") return { texte: "Mort RP", doux: creeLe };
  if (perso.state === "DELETED") return { texte: "Supprimé", doux: creeLe };
  if (cree) return { texte: `Créé le ${dateJour(cree)}`, doux: "" };
  const suivi = dateDe(perso.playMinutesSince);
  return {
    texte: "Création en jeu non datée",
    doux: suivi ? ` · suivi depuis le ${dateJour(suivi)}` : "",
  };
}

export function DetailPersonnage({ perso }: { perso: PersonnageCompte }) {
  const navigate = useNavigate();
  const dossier = perso.dossier;
  const nom = nomComplet(perso);
  const { texte, doux } = accroche(perso);
  const histoire = dossier ? texteBrut(dossier.history) : "";
  const objectifs = dossier ? texteBrut(dossier.objectives) : "";

  return (
    <aside className="reborn-perso-detail" aria-label={`Fiche de ${nom}`}>
      <div className="reborn-perso-detail-tete">
        <Blason perso={perso} taille={96} />
        <div className="min-w-0">
          <p className="reborn-perso-kicker reborn-perso-kicker--or">
            {dossier ? (
              <>
                <FileCheck2 className="h-3 w-3" aria-hidden />
                Dossier d'entrée
              </>
            ) : (
              "Fiche du personnage"
            )}
          </p>
          <h2 className="reborn-perso-detail-nom">{nom}</h2>
          <p className="reborn-perso-detail-sous">
            {texte}
            {doux && <span className="reborn-perso-detail-sous-doux">{doux}</span>}
          </p>
        </div>
      </div>

      <dl className="reborn-perso-detail-identite">
        <Ligne terme="Village" valeur={perso.village ?? "—"} />
        <Ligne terme="Clan" valeur={perso.clan ?? "Sans clan"} />
        <Ligne terme="Type" valeur={LIBELLE_TYPE[perso.type]} />
        <Ligne terme="Grade" valeur={grade(perso) ?? "—"} />
        <Ligne
          terme="Âge · sexe"
          valeur={
            [perso.age !== null ? `${perso.age} ans` : null, perso.sex ? LIBELLE_SEXE[perso.sex] : null]
              .filter(Boolean)
              .join(" · ") || "—"
          }
        />
        <Ligne terme="N°" valeur={perso.playerNumber ?? "En attente"} mono={perso.playerNumber !== null} />
      </dl>

      {dossier ? (
        <div className="reborn-perso-detail-sections">
          {histoire && <Section titre="Histoire" texte={histoire} />}
          {objectifs && <Section titre="Objectifs" texte={objectifs} />}
          {!histoire && !objectifs && (
            <p className="reborn-perso-detail-vide">Ton dossier d'entrée ne contient pas de texte.</p>
          )}
        </div>
      ) : (
        <p className="reborn-perso-detail-vide">
          Ce personnage n'est pas issu de ta candidature : il n'a pas de dossier d'entrée.
          {perso.clan && ` Clan ${perso.clan} rejoint en jeu, lors d'une session de clan.`}
          {perso.state === "DEAD" && " Mort RP, il reste dans ton recensement mais ne se joue plus."}
          {perso.state === "DELETED" && " Supprimé, il reste dans ton recensement pour mémoire et ne se joue plus."}
        </p>
      )}

      <div className="reborn-perso-detail-pied">
        <Info className="h-3.5 w-3.5 flex-shrink-0" aria-hidden />
        <span>Lecture seule. Pour corriger un nom, un village ou un âge, ouvre un ticket avec le N°.</span>
        <button type="button" className="reborn-perso-lien" onClick={() => navigate("/tickets")}>
          <LifeBuoy className="h-3 w-3" aria-hidden />
          Ouvrir un ticket
        </button>
      </div>
    </aside>
  );
}

function Ligne({ terme, valeur, mono }: { terme: string; valeur: string; mono?: boolean }) {
  return (
    <div>
      <dt>{terme}</dt>
      <dd className={mono ? "reborn-perso-mono" : undefined}>{valeur}</dd>
    </div>
  );
}

/** Texte du dossier, replié à 3 lignes, « Lire la suite » s'il dépasse. */
function Section({ titre, texte }: { titre: string; texte: string }) {
  const [ouvert, setOuvert] = useState(false);
  const long = texte.length > 170 || texte.split("\n").length > 3;
  return (
    <section className="reborn-perso-section">
      <h3>{titre}</h3>
      <p data-replie={long && !ouvert ? "true" : undefined}>{texte}</p>
      {long && (
        <button
          type="button"
          className="reborn-perso-lien"
          onClick={() => setOuvert((v) => !v)}
          aria-expanded={ouvert}
        >
          {ouvert ? "Replier" : "Lire la suite"}
        </button>
      )}
    </section>
  );
}
