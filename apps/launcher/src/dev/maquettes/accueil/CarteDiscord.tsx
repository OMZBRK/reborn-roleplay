// MAQUETTES : carte « Rejoindre le Discord » de l'accueil (L10,
// CANDIDATURE_ET_DOUANE §5.3).
//
// - Bannière : volutes de l'identité Reborn v3 violette (DA §7), recadrées
//   depuis la planche d'invitation ; icône du serveur embarquée (avatar
//   violet du bot), jamais le CDN de Discord.
// - Logo Discord officiel (tracé de la marque, une couleur).
// - Compteur : membres, puis « ● N en ligne » (pastille verte), lu dans
//   /menu/panel (cache API de 60 s). Trois états : chargement, ok,
//   indisponible. Le bouton reste actif dans les trois : le lien a un repli
//   constant (§5.2).
// - Dans le vrai launcher, « Rejoindre » passera par tauri-plugin-opener
//   (permission déjà accordée) ; ici, simple lien vers un nouvel onglet.

import { CloudOff } from "lucide-react";
import icone from "./medias/discord_icone.webp";
import volutes from "./medias/discord_volutes.webp";
import { DISCORD_INVITATION, type DonneesDiscord } from "./donnees";
import { nombre, pluriel } from "./format";

/** Logo Discord (marque officielle), une couleur : currentColor. */
export function DiscordLogo({ className = "mqa-discord-logo" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      <path d="M20.317 4.3698a19.7913 19.7913 0 0 0-4.8851-1.5152.0741.0741 0 0 0-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 0 0-.0785-.037 19.7363 19.7363 0 0 0-4.8852 1.515.0699.0699 0 0 0-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 0 0 .0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 0 0 .0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 0 0-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 0 1-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 0 1 .0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 0 1 .0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 0 1-.0066.1276 12.2986 12.2986 0 0 1-1.873.8914.0766.0766 0 0 0-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 0 0 .0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 0 0 .0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 0 0-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189z" />
    </svg>
  );
}

type Props = {
  donnees: DonneesDiscord;
  /** inviteUrl de l'API, sinon la constante (§5.2). */
  lien?: string;
};

export function CarteDiscord({ donnees, lien = DISCORD_INVITATION }: Props) {
  const lienCourt = lien.replace(/^https?:\/\//, "");

  return (
    <article className="mqa-discord" aria-label="Discord Reborn Roleplay">
      <div
        className="mqa-discord-art"
        style={{ backgroundImage: `url(${volutes})` }}
        aria-hidden
      >
        <img className="mqa-discord-icone" src={icone} alt="" width={76} height={76} />
      </div>

      <div className="mqa-discord-corps">
        <span className="mqa-discord-surtitre">
          <DiscordLogo className="mqa-discord-logo mqa-discord-logo--sm" />
          Discord officiel
        </span>
        <h3 className="mqa-discord-titre">Reborn Roleplay</h3>
        <p className="mqa-discord-desc">
          Annonces, sessions de clan, recrutement, entraide : tout se passe ici.
        </p>
        <Compteur donnees={donnees} />
      </div>

      <div className="mqa-discord-action">
        <a
          className="mqa-discord-bouton"
          href={lien}
          target="_blank"
          rel="noreferrer"
          title={`Ouvrir ${lienCourt}`}
        >
          <DiscordLogo />
          Rejoindre
        </a>
        <span className="mqa-discord-lien">{lienCourt}</span>
      </div>
    </article>
  );
}

function Compteur({ donnees }: { donnees: DonneesDiscord }) {
  if (donnees.etat === "chargement") {
    return (
      <div className="mqa-discord-stats" aria-busy="true" aria-label="Chargement du compteur">
        <span className="mqa-squelette" style={{ width: 92 }} />
        <span className="mqa-squelette" style={{ width: 78 }} />
      </div>
    );
  }
  if (donnees.etat === "indisponible") {
    return (
      <div
        className="mqa-discord-stats mqa-discord-stats--off"
        title="Discord n'a pas répondu. Nouvel essai automatique dans une minute ; le bouton fonctionne quand même."
      >
        <CloudOff className="h-3.5 w-3.5" aria-hidden />
        <span>Compteur indisponible pour le moment</span>
      </div>
    );
  }
  const { membres, enLigne } = donnees;
  return (
    <div className="mqa-discord-stats" aria-live="polite">
      <span>
        <b>{nombre(membres)}</b> {pluriel(membres, "membre", "membres")}
      </span>
      <span className="mqa-discord-sep" aria-hidden>
        ·
      </span>
      <span className="mqa-discord-enligne">
        <span className="mqa-pastille-verte" aria-hidden />
        <b>{nombre(enLigne)}</b> en ligne
      </span>
    </div>
  );
}
