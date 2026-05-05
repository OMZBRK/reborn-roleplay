import { useRef } from "react";
import { motion } from "framer-motion";
import { ChevronUp } from "lucide-react";
import type { Category } from "../../lib/content-data";
import { CategoryCard } from "./CategoryCard";

type Props = {
  variant: "rules" | "lore";
  title: string;
  accentWord: string;
  categories: Category[];
  onPickCategory: (cat: Category) => void;
  onCollapse: () => void;
};

// Seuil minimal cumulé pour replier vers le splash. Côté inverse de
// SplashHeader — on veut que l'utilisateur soit clairement en train de scroll
// vers le haut (pas un mouvement diagonal).
const WHEEL_THRESHOLD = 30;

export function CategoryGrid({
  variant,
  title,
  accentWord,
  categories,
  onPickCategory,
  onCollapse,
}: Props) {
  const isLore = variant === "lore";
  const scrollerRef = useRef<HTMLDivElement>(null);
  const lockedRef = useRef(false);

  // Quand on est tout en haut de la grille et qu'on continue à scroller vers
  // le haut, on remonte vers le splash. Symétrique de SplashHeader.handleWheel.
  function handleWheel(e: React.WheelEvent<HTMLDivElement>) {
    if (lockedRef.current) return;
    const el = scrollerRef.current;
    if (!el) return;
    if (e.deltaY < -WHEEL_THRESHOLD && el.scrollTop <= 0) {
      lockedRef.current = true;
      onCollapse();
      window.setTimeout(() => {
        lockedRef.current = false;
      }, 700);
    }
  }

  return (
    <div ref={scrollerRef} onWheel={handleWheel} className="reborn-grid-page">
      <div className="mb-8 flex items-center justify-between">
        <motion.h1
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className={`reborn-grid-title${isLore ? " is-lore" : ""}`}
        >
          {renderTitleWithAccent(title, accentWord)}
        </motion.h1>
        <button
          type="button"
          onClick={onCollapse}
          aria-label="Retour au splash"
          className="reborn-grid-collapse-btn"
        >
          <ChevronUp className="h-5 w-5" />
        </button>
      </div>
      <div className={`reborn-cards-grid${isLore ? " is-lore" : ""}`}>
        {categories.map((c, i) => (
          <CategoryCard
            key={c.id}
            index={i}
            num={c.num}
            name={c.name}
            color={c.color}
            glow={c.glow}
            silhouette={c.silhouette}
            onClick={() => onPickCategory(c)}
          />
        ))}
      </div>
    </div>
  );
}

function renderTitleWithAccent(title: string, accentWord: string) {
  if (!accentWord || !title.includes(accentWord)) return title;
  const parts = title.split(accentWord);
  return parts.flatMap((p, i) =>
    i === 0
      ? [<span key={`p${i}`}>{p}</span>]
      : [<em key={`em${i}`}>{accentWord}</em>, <span key={`p${i}`}>{p}</span>],
  );
}
