import { useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { AnimatePresence, motion } from "framer-motion";
import { SplashHeader } from "../components/content-pages/SplashHeader";
import { CategoryGrid } from "../components/content-pages/CategoryGrid";
import { RULES_CATEGORIES, type Category } from "../lib/content-data";

type LocationState = { level?: 1 | 2 } | null;

export function Rules() {
  const location = useLocation();
  const navigate = useNavigate();
  const initialLevel = ((location.state as LocationState)?.level ?? 1) as 1 | 2;
  const [level, setLevel] = useState<1 | 2>(initialLevel);

  function handlePick(cat: Category) {
    navigate(`/rules/${cat.id}`);
  }

  return (
    <div className="relative h-full">
      <AnimatePresence mode="wait" initial={false}>
        <motion.div
          key={`rules-${level}`}
          initial={{ opacity: 0, y: level === 1 ? -40 : 40 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: level === 2 ? -40 : 40 }}
          transition={{ duration: 0.55, ease: [0.16, 1, 0.3, 1] }}
          className="absolute inset-0 flex flex-col"
        >
          {level === 1 ? (
            <SplashHeader
              variant="rules"
              title="RÈGLEMENT"
              subtitle={
                <>
                  Chaque <em>action</em> a des conséquences
                </>
              }
              badgeLine1="Lecture"
              badgeLine2="OBLIGATOIRE"
              onScroll={() => setLevel(2)}
            />
          ) : (
            <CategoryGrid
              variant="rules"
              title="Tous les règlements"
              accentWord="règlements"
              categories={RULES_CATEGORIES}
              onPickCategory={handlePick}
              onCollapse={() => setLevel(1)}
            />
          )}
        </motion.div>
      </AnimatePresence>
    </div>
  );
}
