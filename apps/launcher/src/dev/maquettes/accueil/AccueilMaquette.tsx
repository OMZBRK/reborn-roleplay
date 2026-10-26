// MAQUETTE L10 : accueil du launcher (CANDIDATURE_ET_DOUANE §3.3, §5.3).
//
// Même page que routes/Home.tsx : en-tête, hero, Actualités, Partenaires,
// pied de page. Les cartes d'actualité, les partenaires et le pied de page
// sont les VRAIS composants (importés), avec des données fictives. L'en-tête
// et le hero sont recomposés sur leurs classes réelles, car les originaux
// lisent les stores et l'IPC.
//
// Nouveautés :
//  - variante « douane » : bouton « Passer la douane » (heure de la séance,
//    fenêtre d'entrée), carte de candidature à jour ;
//  - section « Communauté » sous les Actualités : carte « Rejoindre le
//    Discord » (§5.3, « sous les actualités »).

import { Bell, Coins, DoorOpen } from "lucide-react";
import { NewsCard } from "../../../components/home/NewsCard";
import { PartnersRow } from "../../../components/home/PartnersRow";
import { FooterMega } from "../../../components/home/FooterMega";
import {
  NEWS_RP_CARD,
  PATCH_CARD,
  WHITELIST_CARD,
  WHITELIST_CARD_STATES,
} from "../../../lib/mock-data";
import { CarteDiscord } from "./CarteDiscord";
import { CoqueLauncher } from "./CoqueLauncher";
import { HeroAccueil } from "./HeroAccueil";
import {
  ACTU_CANDIDATURE_DOUANE,
  ACTU_PATCH,
  COMPTE_CANDIDAT,
  COMPTE_JOUEUR,
  DISCORD_OK,
  PERSONNAGES,
  type CompteFictif,
  type DonneesDiscord,
  type EtatDouane,
} from "./donnees";

export type VarianteAccueil = "douane" | "joueur";

type Props = {
  variante: VarianteAccueil;
  /** État du bouton en variante douane (par défaut : fenêtre ouverte). */
  etatDouane?: EtatDouane;
  discord?: DonneesDiscord;
  pleineHauteur?: boolean;
};

/** L10 dans la coque du launcher (rail, fenêtre). */
export function AccueilMaquette({ variante, etatDouane = "ouverte", discord = DISCORD_OK, pleineHauteur }: Props) {
  const compte = variante === "douane" ? COMPTE_CANDIDAT : COMPTE_JOUEUR;
  return (
    <CoqueLauncher
      actif="accueil"
      compte={compte}
      pastilleWhitelist={variante === "douane"}
      pleineHauteur={pleineHauteur}
    >
      <PageAccueil variante={variante} etatDouane={etatDouane} discord={discord} compte={compte} />
    </CoqueLauncher>
  );
}

/** Le contenu de la page seul (sans coque), pour une intégration ailleurs. */
export function PageAccueil({
  variante,
  etatDouane = "ouverte",
  discord = DISCORD_OK,
  compte,
}: {
  variante: VarianteAccueil;
  etatDouane?: EtatDouane;
  discord?: DonneesDiscord;
  compte: CompteFictif;
}) {
  const premier = PERSONNAGES[0];
  const carteCandidature =
    variante === "douane"
      ? { ...ACTU_CANDIDATURE_DOUANE, icon: DoorOpen }
      : { ...WHITELIST_CARD_STATES.APPROVED(`${premier.prenom} ${premier.nomFamille}`), icon: WHITELIST_CARD.icon };

  return (
    <div className="reborn-home-page">
      <EnTeteAccueil compte={compte} />

      {variante === "douane" ? <HeroAccueil mode="douane" etat={etatDouane} /> : <HeroAccueil mode="joueur" />}

      <section className="reborn-home-section">
        <div className="reborn-home-section-head">
          <h2 className="reborn-home-section-title">Actualités</h2>
        </div>
        <div className="reborn-home-news-grid">
          <NewsCard
            size="large"
            kicker={ACTU_PATCH.kicker}
            title={ACTU_PATCH.title}
            excerpt={ACTU_PATCH.excerpt}
            link={ACTU_PATCH.link}
            gradient={PATCH_CARD.gradient}
            icon={PATCH_CARD.icon}
            delay={0.05}
            onClick={() => {}}
          />
          <NewsCard
            kicker={carteCandidature.kicker}
            title={carteCandidature.title}
            excerpt={carteCandidature.excerpt}
            link={carteCandidature.link}
            gradient={WHITELIST_CARD.gradient}
            icon={carteCandidature.icon}
            delay={0.12}
            onClick={() => {}}
          />
          <NewsCard
            kicker={NEWS_RP_CARD.kicker}
            title={NEWS_RP_CARD.title}
            excerpt={NEWS_RP_CARD.excerpt}
            link={NEWS_RP_CARD.link}
            gradient={NEWS_RP_CARD.gradient}
            icon={NEWS_RP_CARD.icon}
            delay={0.18}
            onClick={() => {}}
          />
        </div>
      </section>

      <section className="reborn-home-section">
        <div className="reborn-home-section-head">
          <h2 className="reborn-home-section-title">Communauté</h2>
        </div>
        <CarteDiscord donnees={discord} />
      </section>

      <PartnersRow />

      <FooterMega />
    </div>
  );
}

/** En-tête de HomeHeader (salutation, RBCoins, cloche), alimenté par props. */
function EnTeteAccueil({ compte }: { compte: CompteFictif }) {
  return (
    <header className="reborn-home-header">
      <div className="reborn-home-greeting">
        <div className="reborn-home-greeting-line">
          <span>Content de te revoir,&nbsp;</span>
          <span className="reborn-home-greeting-name">{compte.pseudo}</span>
        </div>
        <div className="reborn-home-greeting-meta">
          <span>Naruto Edition</span>
          <span className="reborn-home-greeting-sep">•</span>
          <span>Connecté</span>
        </div>
      </div>
      <div className="reborn-home-header-actions">
        <div className="reborn-home-currency">
          <Coins className="h-3.5 w-3.5" />
          <span>{compte.coins.toLocaleString("fr-FR")}</span>
        </div>
        <button type="button" className="reborn-home-iconbtn" aria-label="Notifications">
          <Bell className="h-4 w-4" />
          {compte.notificationNonLue && <span className="reborn-home-iconbtn-dot" />}
        </button>
      </div>
    </header>
  );
}
