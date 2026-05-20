import { useEffect, useState } from "react";
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
import {
  NEWS_RP_CARD,
  PATCH_CARD,
  PATCH_CARD_ERROR,
  PATCH_CARD_FALLBACK,
  WHITELIST_CARD,
  WHITELIST_CARD_STATES,
} from "../lib/mock-data";

type PatchState = PatchNoteSummary | null | "error";
type WhitelistState = WhitelistApplication | null | "error" | "loading";

export function Home() {
  const navigate = useNavigate();
  const [latestPatch, setLatestPatch] = useState<PatchState>(null);
  const [whitelist, setWhitelist] = useState<WhitelistState>("loading");

  useEffect(() => {
    let cancelled = false;
    fetchPatchnotes(1, 1)
      .then((res) => {
        if (cancelled) return;
        setLatestPatch(res.items[0] ?? null);
      })
      .catch(() => {
        if (!cancelled) setLatestPatch("error");
      });
    fetchWhitelistMe()
      .then((res) => {
        if (!cancelled) setWhitelist(res.application);
      })
      .catch(() => {
        if (!cancelled) setWhitelist("error");
      });
    return () => {
      cancelled = true;
    };
  }, []);

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
              onClick={() => navigate(PATCH_CARD.href)}
            />
            <NewsCard
              kicker={whitelistContent.kicker}
              title={whitelistContent.title}
              excerpt={whitelistContent.excerpt}
              link={whitelistContent.link}
              gradient={WHITELIST_CARD.gradient}
              icon={WHITELIST_CARD.icon}
              delay={0.12}
              onClick={() => navigate(WHITELIST_CARD.href)}
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
  if (state === null) return PATCH_CARD_FALLBACK;
  if (state === "error") return PATCH_CARD_ERROR;
  return {
    kicker: `Patch ${state.version}`,
    title: state.title,
    excerpt: state.excerpt,
    link: PATCH_CARD.link,
  };
}

function resolveWhitelistContent(state: WhitelistState) {
  if (state === "loading") return WHITELIST_CARD_STATES.loading;
  if (state === "error") return WHITELIST_CARD_STATES.error;
  if (state === null) return WHITELIST_CARD_STATES.none;
  if (state.status === "APPROVED") {
    const characterName = `${state.firstName} ${state.lastName}`.trim();
    return WHITELIST_CARD_STATES.APPROVED(characterName);
  }
  return WHITELIST_CARD_STATES[state.status];
}
