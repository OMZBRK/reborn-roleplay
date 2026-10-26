import { useRef, useState } from "react";
import { CloudOff } from "lucide-react";
import { openUrl } from "@tauri-apps/plugin-opener";
import icone from "./medias/discord_icone.webp";
import volutes from "./medias/discord_volutes.webp";
import {
  DISCORD_INVITE_URL,
  fetchDiscordCommunity,
  type DiscordCommunity,
} from "../../lib/content";
import { formatIpcError } from "../../lib/errors";
import { isTauri } from "../../lib/tauri";
import { usePolling } from "../../hooks/use-polling";
import "./discord-card.css";

// Carte « Discord officiel » de l'accueil (CANDIDATURE_ET_DOUANE §5.3),
// reprise de la maquette validée L10 (dev/maquettes/accueil/CarteDiscord).
//
// - Bannière : volutes de l'identité Reborn violette (DA §7) et icône du
//   serveur embarquées, jamais le CDN de Discord.
// - Compteur : membres, puis « ● N en ligne », lu par Rust sur l'endpoint
//   public /menu/panel (l'API lit l'invitation publique, cache de quelques
//   minutes). Trois états : chargement, ok, indisponible.
// - « Rejoindre » reste actif dans les trois états : le lien a un repli
//   constant (DISCORD_INVITE_URL). Ouverture par tauri-plugin-opener.

/** Le compteur bouge lentement et l'API le met en cache 5 min. */
const POLL_MS = 120_000;
/**
 * API injoignable : dernier compteur gardé au plus 30 min, comme l'API le
 * fait de son côté quand Discord se tait (DISCORD_STALE_MAX_MS).
 */
const STALE_MAX_MS = 30 * 60_000;

type CardState =
  | { kind: "loading" }
  | { kind: "ok"; data: DiscordCommunity }
  | { kind: "unavailable"; reason: string };

const UNAVAILABLE_HINT =
  "Nouvel essai automatique dans quelques minutes ; le bouton fonctionne quand même.";

/** Ouvre une invitation Discord dans le navigateur du système. */
export async function openDiscordInvite(url: string = DISCORD_INVITE_URL): Promise<void> {
  if (!isTauri) {
    // vite dev dans un simple navigateur : pas de plugin opener.
    window.open(url, "_blank", "noopener,noreferrer");
    return;
  }
  await openUrl(url);
}

/** Logo Discord (marque officielle), une couleur : currentColor. */
export function DiscordLogo({ className = "reborn-discord-logo" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      <path d="M20.317 4.3698a19.7913 19.7913 0 0 0-4.8851-1.5152.0741.0741 0 0 0-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 0 0-.0785-.037 19.7363 19.7363 0 0 0-4.8852 1.515.0699.0699 0 0 0-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 0 0 .0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 0 0 .0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 0 0-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 0 1-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 0 1 .0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 0 1 .0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 0 1-.0066.1276 12.2986 12.2986 0 0 1-1.873.8914.0766.0766 0 0 0-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 0 0 .0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 0 0 .0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 0 0-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189z" />
    </svg>
  );
}

export function DiscordCard() {
  const [state, setState] = useState<CardState>({ kind: "loading" });
  const [openError, setOpenError] = useState<string | null>(null);
  // Heure de la dernière réponse de l'API avec un compteur.
  const lastOkAt = useRef(0);

  usePolling(async (signal) => {
    try {
      const data = await fetchDiscordCommunity();
      if (signal.aborted) return;
      if (data) lastOkAt.current = Date.now();
      // null couvre plusieurs causes côté API (invitation absente, mal formée
      // ou expirée, Discord muet) : message neutre, le détail est au journal
      // de l'API.
      setState(
        data
          ? { kind: "ok", data }
          : { kind: "unavailable", reason: "Le compteur Discord n'est pas disponible côté Reborn." },
      );
    } catch (err) {
      if (signal.aborted) return;
      // API injoignable : on garde le dernier compteur connu (au plus 30 min)
      // plutôt que de faire clignoter la carte ; sinon « indisponible » avec
      // la vraie cause.
      const recent = Date.now() - lastOkAt.current < STALE_MAX_MS;
      setState((prev) =>
        prev.kind === "ok" && recent
          ? prev
          : { kind: "unavailable", reason: formatIpcError(err, "Compteur Discord indisponible") },
      );
    }
  }, POLL_MS);

  const lien = (state.kind === "ok" && state.data.inviteUrl) || DISCORD_INVITE_URL;
  const lienCourt = lien.replace(/^https?:\/\//, "");

  const rejoindre = () => {
    setOpenError(null);
    openDiscordInvite(lien).catch(() => {
      setOpenError(`Navigateur introuvable : ouvre ${lienCourt} à la main.`);
    });
  };

  return (
    <div className="reborn-discord-cadre">
      <article className="reborn-discord" aria-label="Discord Reborn Roleplay">
        <div
          className="reborn-discord-art"
          style={{ backgroundImage: `url(${volutes})` }}
          aria-hidden
        >
          <img className="reborn-discord-icone" src={icone} alt="" width={76} height={76} />
        </div>

        <div className="reborn-discord-corps">
          <span className="reborn-discord-surtitre">
            <DiscordLogo className="reborn-discord-logo reborn-discord-logo--sm" />
            Discord officiel
          </span>
          <h3 className="reborn-discord-titre">Reborn Roleplay</h3>
          <p className="reborn-discord-desc">
            Annonces, sessions de clan, recrutement, entraide : tout se passe ici.
          </p>
          <Compteur state={state} />
        </div>

        <div className="reborn-discord-action">
          <a
            className="reborn-discord-bouton"
            href={lien}
            title={`Ouvrir ${lienCourt}`}
            onClick={(e) => {
              e.preventDefault();
              rejoindre();
            }}
          >
            <DiscordLogo />
            Rejoindre
          </a>
          {openError ? (
            <span className="reborn-discord-lien reborn-discord-lien--erreur" role="alert">
              {openError}
            </span>
          ) : (
            <span className="reborn-discord-lien">{lienCourt}</span>
          )}
        </div>
      </article>
    </div>
  );
}

/** 1238 → « 1 238 » (espace fine insécable du français). */
const nombre = (n: number) => n.toLocaleString("fr-FR");
const pluriel = (n: number, un: string, plusieurs: string) => (n > 1 ? plusieurs : un);

function Compteur({ state }: { state: CardState }) {
  if (state.kind === "loading") {
    return (
      <div className="reborn-discord-stats" aria-busy="true" aria-label="Chargement du compteur">
        <span className="reborn-discord-squelette" style={{ width: 92 }} />
        <span className="reborn-discord-squelette" style={{ width: 78 }} />
      </div>
    );
  }
  if (state.kind === "unavailable") {
    return (
      <div
        className="reborn-discord-stats reborn-discord-stats--off"
        title={`${state.reason} ${UNAVAILABLE_HINT}`}
      >
        <CloudOff className="h-3.5 w-3.5" aria-hidden />
        <span>Compteur indisponible pour le moment</span>
      </div>
    );
  }
  const { members, online } = state.data;
  return (
    <div className="reborn-discord-stats" aria-live="polite">
      <span>
        <b>{nombre(members)}</b> {pluriel(members, "membre", "membres")}
      </span>
      <span className="reborn-discord-sep" aria-hidden>
        ·
      </span>
      <span className="reborn-discord-enligne">
        <span className="reborn-discord-pastille" aria-hidden />
        <b>{nombre(online)}</b> en ligne
      </span>
    </div>
  );
}
