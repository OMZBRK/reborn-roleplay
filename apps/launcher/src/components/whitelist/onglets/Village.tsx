// Onglet 3 « Village » (partie RP), §2.3 et décision Q4 du 24/10, maquette L3.
//
// Le village est le seul choix de faction : liste fermée de l'API (six
// villages, Déserteur exclu, comme en jeu), avec les vrais emblèmes du mod ;
// verrouillé à la création en jeu (Q13). Aucun choix de clan : tous se
// rejoignent en jeu, lors des sessions de clan annoncées sur le Discord (Q4).
// Les clans du village choisi sont montrés pour information seulement.

import { Check, Info, UsersRound } from "lucide-react";
import { useFormulaire } from "../contexte";
import { HABILLAGE_CLANS, HABILLAGE_VILLAGES } from "../villages";
import { Champ, Emblem, Section } from "../ui";

export function OngletVillage() {
  const { f, maj, options, montrerErreurs, erreursApi } = useFormulaire();
  const village = options.villages.find((v) => v.valeur === f.village) ?? null;
  const clans = options.clans.filter((c) => c.village === f.village);
  const erreur = erreursApi.village ?? (montrerErreurs && !f.village ? "Choisis ton village." : null);

  return (
    <>
      <Section
        titre="Ton village"
        chapo="Ton personnage naît dans l'un des six villages. C'est le seul choix de faction de la candidature."
      />

      <Champ label="Village" ancre="village" aide="Verrouillé à la création en jeu : il ne change plus ensuite." erreur={erreur}>
        <div className="cand-villages" role="radiogroup" aria-label="Village">
          {options.villages.map((v) => {
            const choisi = f.village === v.valeur;
            const habillage = HABILLAGE_VILLAGES[v.valeur];
            return (
              <button
                key={v.valeur}
                type="button"
                role="radio"
                aria-checked={choisi}
                className="cand-village"
                title={`${v.valeur} · ${v.pays}`}
                onClick={() => {
                  if (!choisi) maj("village", v.valeur);
                }}
              >
                <span className="cand-village-emblem">
                  <Emblem src={habillage?.emblem} taille={36} />
                </span>
                <span className="min-w-0">
                  <span className="cand-village-name block">{v.court}</span>
                  <span className="cand-village-meta block">{v.valeur}</span>
                  {habillage && <span className="cand-village-desc block">{habillage.description}</span>}
                </span>
                {choisi && (
                  <span className="cand-village-tick" aria-hidden>
                    <Check size={11} strokeWidth={3} />
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </Champ>

      <section className="cand-clanbox" aria-label="Les clans">
        <div className="cand-clanbox-head">
          <span className="cand-clanbox-icon" aria-hidden>
            <UsersRound size={15} />
          </span>
          <div className="min-w-0">
            <h3 className="cand-clanbox-title">Et le clan ?</h3>
            <p className="cand-clanbox-text">
              Les clans se rejoignent lors des <strong>sessions de clan</strong> annoncées sur le Discord. Ton
              personnage commence donc <strong>sans clan</strong>, avec le nom de famille choisi à l'onglet
              Personnage.
            </p>
          </div>
        </div>
        {village && (
          <div className="cand-clanbox-list">
            <div className="cand-clanbox-label">
              {clans.length > 0
                ? `Les clans de ${village.court}, pour information`
                : `Pas de clan à ${village.valeur} pour l'instant`}
            </div>
            {clans.length > 0 && (
              <ul className="cand-clanchips" aria-label={`Clans de ${village.court}`}>
                {clans.map((c) => {
                  const habillage = HABILLAGE_CLANS[c.nom];
                  return (
                    <li key={c.nom} className="cand-clanchip" title={habillage ? `${c.nom} · ${habillage.trait}` : c.nom}>
                      <Emblem src={habillage?.emblem} taille={18} />
                      {c.nom}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        )}
      </section>

      <div className="cand-note">
        <Info size={14} aria-hidden />
        <span>Les organisations (ANBU, Akatsuki…) se rejoignent elles aussi en jeu, jamais à la candidature.</span>
      </div>
    </>
  );
}
