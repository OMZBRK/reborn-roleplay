import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { HomeHeader } from "../components/home/HomeHeader";
import { HeroLaunchCard } from "../components/home/HeroLaunchCard";
import { NewsCard } from "../components/home/NewsCard";
import { PartnersRow } from "../components/home/PartnersRow";
import { FooterMega } from "../components/home/FooterMega";
import {
  fetchPatchnotes,
  fetchWhitelistMe,
  type PatchNoteSummary,
  type WhitelistApplication,
} from "../lib/content";
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
type WhitelistState = WhitelistApplication | null | Failed | "loading";

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
    fetchWhitelistMe()
      .then((res) => {
        if (mounted.current) setWhitelist(res?.application ?? null);
      })
      .catch((err: unknown) => {
        if (mounted.current) {
          setWhitelist({ error: formatIpcError(err, "Statut de whitelist indisponible") });
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
            icon={WHITELIST_CARD.icon}
            delay={0.12}
            onClick={isFailed(whitelist) ? loadWhitelist : () => navigate(WHITELIST_CARD.href)}
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

function resolveWhitelistContent(state: WhitelistState) {
  if (state === "loading") return WHITELIST_CARD_STATES.loading;
  if (isFailed(state)) return { ...WHITELIST_CARD_STATES.error, excerpt: state.error };
  if (state === null) return WHITELIST_CARD_STATES.none;
  if (state.status === "APPROVED") {
    const characterName = `${state.firstName} ${state.lastName}`.trim();
    return WHITELIST_CARD_STATES.APPROVED(characterName);
  }
  return WHITELIST_CARD_STATES[state.status];
}
