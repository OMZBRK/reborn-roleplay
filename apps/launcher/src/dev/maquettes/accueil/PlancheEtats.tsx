// MAQUETTE L10, planche des états : le bouton principal d'un compte en
// phase DOUANE_RESERVEE au fil de la soirée (règle unique de la fenêtre
// d'entrée, §3.3), puis la carte Discord dans ses trois états (§5.3).

import { CarteDiscord } from "./CarteDiscord";
import { ColonneDouane } from "./HeroAccueil";
import { MAINTENANT, SEANCE, fermetureDe, ouvertureDe, type EtatDouane } from "./donnees";
import { heure } from "./format";

const ETATS: Array<{ etat: EtatDouane; titre: string; regle: string }> = [
  {
    etat: "avant",
    titre: "Avant la fenêtre",
    regle: `Grisé jusqu'à H − 15 (${heure(ouvertureDe(SEANCE))}). Le jeton « douane » est refusé par l'API.`,
  },
  {
    etat: "ouverte",
    titre: "Fenêtre ouverte",
    regle: `Actif de ${heure(ouvertureDe(SEANCE))} à ${heure(fermetureDe(SEANCE))}. Jeton de portée « douane ».`,
  },
  {
    etat: "en-jeu",
    titre: "Dans le hall",
    regle: "Admis : il reste jusqu'à son passage, même après H + 30.",
  },
  {
    etat: "manquee",
    titre: "Séance manquée",
    regle: "Au-delà de H + 30 : réservation MISSED, nouvelle réservation possible.",
  },
];

export function PlancheEtats() {
  return (
    <div className="mqa-planche">
      <header className="mqa-planche-tete">
        <p className="mqa-kicker">L10 · états</p>
        <h1 className="mqa-perso-titre">Accueil : bouton douane et carte Discord</h1>
        <p className="mqa-perso-sous">
          Séance du {SEANCE.jour}, convocation à {heure(SEANCE.convocation)}. Chaque état est
          figé à une heure fictive pour des captures reproductibles.
        </p>
      </header>

      <section className="mqa-planche-section">
        <h2 className="reborn-home-section-title">Bouton « Passer la douane »</h2>
        <div className="mqa-planche-grille">
          {ETATS.map(({ etat, titre, regle }) => (
            <figure key={etat} className="mqa-planche-cas">
              <figcaption>
                <span className="mqa-planche-cas-titre">{titre}</span>
                <span className="mqa-planche-cas-heure">il est {heure(MAINTENANT[etat])}</span>
              </figcaption>
              <div className="mqa-planche-hero reborn-home-hero reborn-pattern-overlay">
                <div className="mqa-planche-hero-col">
                  <ColonneDouane etat={etat} />
                </div>
              </div>
              <p className="mqa-planche-regle">{regle}</p>
            </figure>
          ))}
        </div>
      </section>

      <section className="mqa-planche-section">
        <h2 className="reborn-home-section-title">Carte « Rejoindre le Discord »</h2>
        <div className="mqa-planche-pile">
          <figure className="mqa-planche-cas">
            <figcaption>
              <span className="mqa-planche-cas-titre">Compteur à jour</span>
              <span className="mqa-planche-cas-heure">/menu/panel, cache de 60 s</span>
            </figcaption>
            <CarteDiscord donnees={{ etat: "ok", membres: 1238, enLigne: 214 }} />
          </figure>
          <figure className="mqa-planche-cas">
            <figcaption>
              <span className="mqa-planche-cas-titre">Chargement</span>
              <span className="mqa-planche-cas-heure">premier appel en cours</span>
            </figcaption>
            <CarteDiscord donnees={{ etat: "chargement" }} />
          </figure>
          <figure className="mqa-planche-cas">
            <figcaption>
              <span className="mqa-planche-cas-titre">Compteur indisponible</span>
              <span className="mqa-planche-cas-heure">API ou Discord muets : le lien de repli reste actif</span>
            </figcaption>
            <CarteDiscord donnees={{ etat: "indisponible" }} />
          </figure>
        </div>
      </section>
    </div>
  );
}
