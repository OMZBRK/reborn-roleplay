import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { DoorOpen, type LucideIcon } from "lucide-react";
import { HomeHeader } from "../components/home/HomeHeader";
import { HeroLaunchCard } from "../components/home/HeroLaunchCard";
import { NewsCard } from "../components/home/NewsCard";
import { PartnersRow } from "../components/home/PartnersRow";
import { FooterMega } from "../components/home/FooterMega";
import { DiscordCard } from "../components/home/DiscordCard";
import { fetchPatchnotes, type PatchNoteSummary } from "../lib/content";
import { lireCandidature, type CandidatureMe } from "../lib/candidature";
import { dateDe, heure, jourRelatif, quand } from "../components/whitelist/format";
import { formatIpcError } from "../lib/errors";
import {
  NEWS_RP_CARD,
  PATCH_CARD,
  PATCH_CARD_ERROR,
  PATCH_CARD_FALLBACK,
  WHITELIST_CARD,
  WHITELIST_CARD_STATES,
} from "../lib/mock-data";

/** Echec de chargement : message reel, affiche dans la carte. */
type Failed = { error: string };
type PatchState = PatchNoteSummary | null | Failed | "loading";
type WhitelistState = CandidatureMe | null | Failed | "loading";

// Pendant le chargement : pas « aucune note publiée », qui serait faux.
const PATCH_CARD_LOADING = {
  ...PATCH_CARD_FALLBACK,
  title: "Chargement…",
  excerpt: "Récupération du dernier patch publié.",
};

const isFailed = (s: unknown): s is Failed =>
  typeof s === "object" && s !== null && "error" in s && typeof (s as Failed).error === "string";

export function Home() {
  const navigate = useNavigate();
  const [latestPatch, setLatestPatch] = useState<PatchState>("loading");
  const [whitelist, setWhitelist] = useState<WhitelistState>("loading");
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const loadPatch = useCallback(() => {
    setLatestPatch("loading");
    fetchPatchnotes(1, 1)
      .then((res) => {
        if (mounted.current) setLatestPatch(res?.items?.[0] ?? null);
      })
      .catch((err: unknown) => {
        if (mounted.current) {
          setLatestPatch({ error: formatIpcError(err, "Dernier patch indisponible") });
        }
      });
  }, []);

  const loadWhitelist = useCallback(() => {
    setWhitelist("loading");
    lireCandidature()
      .then((res) => {
        if (mounted.current) setWhitelist(res?.suivi ? res : null);
      })
      .catch((err: unknown) => {
        if (mounted.current) {
          setWhitelist({ error: formatIpcError(err, "État de ta candidature indisponible") });
        }
      });
  }, []);

  useEffect(() => {
    loadPatch();
    loadWhitelist();
  }, [loadPatch, loadWhitelist]);

  const patchContent = resolvePatchContent(latestPatch);
  const whitelistContent = resolveWhitelistContent(whitelist);

  return (
    <div className="reborn-home-page">
      <HomeHeader />

      <HeroLaunchCard />

      <section className="reborn-home-section">
        <div className="reborn-home-section-head">
          <h2 className="reborn-home-section-title">Actualités</h2>
        </div>
        <div className="reborn-home-news-grid">
          <NewsCard
            size="large"
            kicker={patchContent.kicker}
            title={patchContent.title}
            excerpt={patchContent.excerpt}
            link={patchContent.link}
            gradient={PATCH_CARD.gradient}
            icon={PATCH_CARD.icon}
            delay={0.05}
            // En erreur, la carte relance le chargement (« Réessayer »).
            onClick={isFailed(latestPatch) ? loadPatch : () => navigate(PATCH_CARD.href)}
          />
          <NewsCard
            kicker={whitelistContent.kicker}
            title={whitelistContent.title}
            excerpt={whitelistContent.excerpt}
            link={whitelistContent.link}
            gradient={WHITELIST_CARD.gradient}
            icon={whitelistContent.icon ?? WHITELIST_CARD.icon}
            delay={0.12}
            onClick={isFailed(whitelist) ? loadWhitelist : () => navigate(whitelistContent.href ?? WHITELIST_CARD.href)}
          />
          <NewsCard
            kicker={NEWS_RP_CARD.kicker}
            title={NEWS_RP_CARD.title}
            excerpt={NEWS_RP_CARD.excerpt}
            link={NEWS_RP_CARD.link}
            gradient={NEWS_RP_CARD.gradient}
            icon={NEWS_RP_CARD.icon}
            delay={0.18}
            onClick={() => navigate(NEWS_RP_CARD.href)}
          />
        </div>
      </section>

      {/* Communauté : carte « Discord officiel », sous les actualités (§5.3). */}
      <section className="reborn-home-section">
        <div className="reborn-home-section-head">
          <h2 className="reborn-home-section-title">Communauté</h2>
        </div>
        <DiscordCard />
      </section>

      <PartnersRow />

      <FooterMega />
    </div>
  );
}

function resolvePatchContent(state: PatchState) {
  if (state === "loading") return PATCH_CARD_LOADING;
  if (state === null) return PATCH_CARD_FALLBACK;
  if (isFailed(state)) return { ...PATCH_CARD_ERROR, excerpt: state.error };
  return {
    kicker: `Patch ${state.version}`,
    title: state.title,
    excerpt: state.excerpt,
    link: PATCH_CARD.link,
  };
}

type CarteCandidature = {
  kicker: string;
  title: string;
  excerpt: string;
  link: string;
  /** Page ouverte au clic (défaut : le suivi). */
  href?: string;
  icon?: LucideIcon;
};

const VERS_DOUANE = "/whitelist?vue=douane";

/** Carte « Candidature » selon la phase calculée par l'API (CANDIDATURE_ET_DOUANE
 *  §1.2) ; aucun délai de relecture promis (Q11). */
function resolveWhitelistContent(state: WhitelistState): CarteCandidature {
  if (state === "loading") return WHITELIST_CARD_STATES.loading;
  if (isFailed(state)) return { ...WHITELIST_CARD_STATES.error, excerpt: state.error };
  const app = state?.application ?? null;
  const suivi = state?.suivi ?? null;
  const enJeu = suivi?.douaneEnJeu ?? true;
  const kicker = "Candidature";
  switch (suivi?.phase ?? "BROUILLON") {
    case "BROUILLON":
      return {
        kicker,
        title: "Ta candidature t'attend",
        excerpt: "Six onglets, une quinzaine de minutes : tout s'enregistre au fil de l'eau, tu peux reprendre plus tard.",
        link: "Ma candidature",
      };
    case "ECRIT_EN_ATTENTE":
      return {
        kicker,
        title: "Écrit en relecture",
        excerpt: "L'équipe whitelist lit ton dossier. La réponse arrive ici et sur Discord.",
        link: "Voir le suivi",
      };
    case "ECRIT_A_REVOIR":
      return {
        kicker,
        title: "Écrit à revoir",
        excerpt: "Quelques points à reprendre : corrige les onglets rouverts, le reste de ton dossier est gardé.",
        link: "Corriger ma candidature",
      };
    case "DOUANE_A_RESERVER":
      return {
        kicker,
        title: "Écrit accepté",
        excerpt: enJeu
          ? "Dernière étape : la douane, un oral à la voix, en jeu. Réserve ta séance."
          : "Dernière étape : l'oral, à la voix, face à un examinateur. Réserve ta séance.",
        link: "Choisir une séance",
        href: VERS_DOUANE,
        icon: DoorOpen,
      };
    case "DOUANE_RESERVEE": {
      const convocation = dateDe(suivi?.reservation?.convocationAt);
      const ouvre = dateDe(suivi?.reservation?.entreeOuvreAt);
      const ferme = dateDe(suivi?.reservation?.entreeFermeAt);
      if (!convocation) {
        return {
          kicker,
          title: "Place réservée",
          excerpt: "Ta séance de douane est réservée.",
          link: "Voir ma séance",
          href: VERS_DOUANE,
          icon: DoorOpen,
        };
      }
      return {
        kicker,
        title: `Écrit accepté · ${enJeu ? "douane" : "oral"} ${jourRelatif(convocation)}`,
        excerpt:
          enJeu && ouvre && ferme
            ? `Présente-toi entre ${heure(ouvre)} et ${heure(ferme)}, micro branché. Un examinateur t'appellera depuis le hall.`
            : `Convocation à ${heure(convocation)} : les consignes de l'oral arrivent dans le fil de ta candidature.`,
        link: "Voir ma séance",
        href: VERS_DOUANE,
        icon: DoorOpen,
      };
    }
    case "DOUANE_A_REVOIR":
      return {
        kicker,
        title: enJeu ? "Douane à revoir" : "Oral à revoir",
        excerpt: "Ton écrit est gardé tel quel : réserve une nouvelle séance pour repasser l'oral.",
        link: "Choisir une séance",
        href: VERS_DOUANE,
        icon: DoorOpen,
      };
    case "REFUSE": {
      const reapply = dateDe(suivi?.canReapplyAt);
      return {
        kicker,
        title: "Candidature refusée",
        excerpt:
          reapply && reapply > new Date()
            ? `Le commentaire du staff est dans le suivi. Nouvelle candidature possible le ${quand(reapply)}.`
            : "Le commentaire du staff est dans le suivi. Tu peux écrire une nouvelle candidature complète.",
        link: "Voir le suivi",
      };
    }
    default:
      return WHITELIST_CARD_STATES.APPROVED(app ? `${app.firstName} ${app.lastName}`.trim() : "");
  }
}
