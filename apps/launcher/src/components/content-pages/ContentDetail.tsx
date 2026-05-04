import { motion } from "framer-motion";
import { ChevronLeft } from "lucide-react";
import type { Category, LoreSection, RulesSection } from "../../lib/content-data";
import { Accordion } from "./Accordion";

type Variant = "rules" | "lore";

type Props = {
  variant: Variant;
  category: Category;
  intro?: string;
  sections: RulesSection[] | LoreSection[];
  onBack: () => void;
};

export function ContentDetail({ variant, category, intro, sections, onBack }: Props) {
  const isLore = variant === "lore";
  const accent = isLore ? "var(--color-lore)" : category.color;
  const glow = isLore ? "var(--color-lore-glow)" : category.glow;

  return (
    <div
      className="reborn-detail-page"
      style={{
        ["--detail-color" as string]: accent,
        ["--detail-glow" as string]: glow,
      }}
    >
      <div className="mb-7 flex items-center gap-4">
        <button type="button" onClick={onBack} className="reborn-back-btn">
          <ChevronLeft className="h-[18px] w-[18px]" />
          <span>Retour</span>
        </button>
        <div className="flex items-baseline gap-4">
          <div>
            <div className="reborn-detail-pill">
              <span className="dot" />
              {isLore ? "Lore" : "Règlement"} · Section {String(category.num).padStart(2, "0")}
            </div>
            <h1 className="reborn-detail-title">{category.name}</h1>
          </div>
        </div>
      </div>

      {intro && (
        <p
          className="reborn-detail-intro"
          dangerouslySetInnerHTML={{ __html: intro }}
        />
      )}

      {!isLore && (
        <div className="flex max-w-[920px] flex-col gap-[10px]">
          {(sections as RulesSection[]).map((s, i) => (
            <Accordion key={s.title} index={i} num={i + 1} title={s.title} defaultOpen={i === 0}>
              <div dangerouslySetInnerHTML={{ __html: s.body }} />
            </Accordion>
          ))}
        </div>
      )}

      {isLore && (
        <div className="reborn-lore-doc">
          {(sections as LoreSection[]).map((s, i) => (
            <motion.section
              key={s.title}
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: i * 0.06 }}
            >
              {s.era && <span className="reborn-timeline-pill">⌖ {s.era}</span>}
              <h2>{s.title}</h2>
              <div dangerouslySetInnerHTML={{ __html: s.body }} />
            </motion.section>
          ))}
        </div>
      )}
    </div>
  );
}
