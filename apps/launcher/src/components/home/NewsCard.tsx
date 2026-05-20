import { motion } from "framer-motion";
import { ArrowRight, type LucideIcon } from "lucide-react";

type Props = {
  kicker: string;
  title: string;
  excerpt: string;
  link: string;
  gradient: string;
  icon: LucideIcon;
  /** Variante visuelle. "large" = card heroique 2 colonnes, "small" = standard 1 colonne. */
  size?: "large" | "small";
  delay?: number;
  onClick?: () => void;
};

// Card editoriale de la grille Home v3. Conserve la meme signature que la
// v2 (kicker/title/excerpt/link/gradient/icon) — Home.tsx peut continuer
// a passer ses slots data sans changer. Le visuel change : bandeau art
// haut avec gradient + icone, body texte bas. Variant "large" pour la
// card hero a gauche du news-grid.
export function NewsCard({
  kicker,
  title,
  excerpt,
  link,
  gradient,
  icon: Icon,
  size = "small",
  delay = 0,
  onClick,
}: Props) {
  const isInteractive = !!onClick;

  return (
    <motion.article
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, delay, ease: [0.16, 1, 0.3, 1] }}
      whileHover={{ y: -3 }}
      onClick={onClick}
      className={`reborn-news-card reborn-news-card--${size}`}
      role={isInteractive ? "button" : undefined}
      tabIndex={isInteractive ? 0 : undefined}
      onKeyDown={(e) => {
        if (!isInteractive) return;
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onClick();
        }
      }}
    >
      <div
        className="reborn-news-card-art"
        style={{ background: gradient }}
      >
        <Icon className="reborn-news-card-icon" />
      </div>
      <div className="reborn-news-card-body">
        <span className="reborn-news-card-kicker">{kicker}</span>
        <h3 className="reborn-news-card-title">{title}</h3>
        <p className="reborn-news-card-excerpt">{excerpt}</p>
        <span className="reborn-news-card-link">
          {link}
          <ArrowRight className="h-3 w-3" />
        </span>
      </div>
    </motion.article>
  );
}
