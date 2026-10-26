// MAQUETTE L3 — onglet 3 « Village » (partie RP), §2.3 et décision Q4 du 24/10.
//
// Le village est le seul choix de faction : six villages avec les vrais
// emblèmes du mod (Déserteur exclu, comme en jeu), verrouillé à la création
// en jeu (Q13). Aucun choix de clan : tous les clans se rejoignent en jeu,
// lors des sessions de clan annoncées sur le Discord (Q4). Le premier
// personnage arrive donc « sans clan » (clan « Autre » du mod, §4.2), avec le
// nom de famille libre de l'onglet Personnage. Les clans du village choisi
// sont montrés pour information seulement, sans sélection.

import { Check, Info, UsersRound } from "lucide-react";
import { useDossier } from "../contexte";
import { CLANS, VILLAGES, villageDe, type VillageId } from "../donnees";
import { Champ, Emblem, Section } from "../ui";

export function OngletVillage() {
  const { dossier: d, maj } = useDossier();
  const village = villageDe(d.village);
  const clans = CLANS.filter((c) => c.village === d.village);

  function choisirVillage(id: VillageId) {
    if (id !== d.village) maj("village", id);
  }

  return (
    <>
      <Section
        titre="Ton village"
        chapo="Ton personnage naît dans l'un des six villages. C'est le seul choix de faction de la candidature."
      />

      <Champ label="Village" aide="Verrouillé à la création en jeu : il ne change plus ensuite.">
        <div className="mqc-villages" role="radiogroup" aria-label="Village">
          {VILLAGES.map((v) => {
            const choisi = d.village === v.id;
            return (
              <button
                key={v.id}
                type="button"
                role="radio"
                aria-checked={choisi}
                className="mqc-village"
                title={`${v.nom} · ${v.pays}`}
                onClick={() => choisirVillage(v.id)}
              >
                <span className="mqc-village-emblem">
                  <Emblem src={v.emblem} taille={36} />
                </span>
                <span className="min-w-0">
                  <span className="mqc-village-name block">{v.court}</span>
                  <span className="mqc-village-meta block">{v.nom}</span>
                  <span className="mqc-village-desc block">{v.description}</span>
                </span>
                {choisi && (
                  <span className="mqc-village-tick" aria-hidden>
                    <Check size={11} strokeWidth={3} />
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </Champ>

      <section className="mqc-clanbox" aria-label="Les clans">
        <div className="mqc-clanbox-head">
          <span className="mqc-clanbox-icon" aria-hidden>
            <UsersRound size={15} />
          </span>
          <div className="min-w-0">
            <h3 className="mqc-clanbox-title">Et le clan ?</h3>
            <p className="mqc-clanbox-text">
              Les clans se rejoignent lors des <strong>sessions de clan</strong> annoncées sur le Discord. Ton
              personnage commence donc <strong>sans clan</strong>, avec le nom de famille choisi à l'onglet
              Personnage.
            </p>
          </div>
        </div>
        {village && (
          <div className="mqc-clanbox-list">
            <div className="mqc-clanbox-label">
              {clans.length > 0 ? `Les clans de ${village.court}, pour information` : `Pas de clan à ${village.nom} pour l'instant`}
            </div>
            {clans.length > 0 && (
              <ul className="mqc-clanchips" aria-label={`Clans de ${village.court}`}>
                {clans.map((c) => (
                  <li key={c.nom} className="mqc-clanchip" title={`${c.nom} · ${c.trait}`}>
                    <Emblem src={c.emblem} taille={18} />
                    {c.nom}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </section>

      <div className="mqc-note">
        <Info size={14} aria-hidden />
        <span>Les organisations (ANBU, Akatsuki…) se rejoignent elles aussi en jeu, jamais à la candidature.</span>
      </div>
    </>
  );
}
