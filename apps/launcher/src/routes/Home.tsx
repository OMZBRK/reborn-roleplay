import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { HeroSection } from "../components/home/HeroSection";
import { NewsCard } from "../components/home/NewsCard";
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
    <div className="flex h-full flex-col">
      <HeroSection />

      <section
        className="flex shrink-0 gap-5 bg-background px-10 pb-6 pt-2"
        style={{ height: 288 }}
      >
        <div className="min-w-0 flex-1">
          <NewsCard
            kicker={patchContent.kicker}
            title={patchContent.title}
            excerpt={patchContent.excerpt}
            link={patchContent.link}
            gradient={PATCH_CARD.gradient}
            icon={PATCH_CARD.icon}
            delay={0.1}
            onClick={() => navigate(PATCH_CARD.href)}
          />
        </div>
        <div className="min-w-0 flex-1">
          <NewsCard
            kicker={whitelistContent.kicker}
            title={whitelistContent.title}
            excerpt={whitelistContent.excerpt}
            link={whitelistContent.link}
            gradient={WHITELIST_CARD.gradient}
            icon={WHITELIST_CARD.icon}
            delay={0.2}
            onClick={() => navigate(WHITELIST_CARD.href)}
          />
        </div>
        <div className="min-w-0 flex-1">
          <NewsCard
            kicker={NEWS_RP_CARD.kicker}
            title={NEWS_RP_CARD.title}
            excerpt={NEWS_RP_CARD.excerpt}
            link={NEWS_RP_CARD.link}
            gradient={NEWS_RP_CARD.gradient}
            icon={NEWS_RP_CARD.icon}
            delay={0.3}
            onClick={() => navigate(NEWS_RP_CARD.href)}
          />
        </div>
      </section>
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
