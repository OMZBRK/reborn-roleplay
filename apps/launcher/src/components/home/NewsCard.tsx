import { motion } from "framer-motion";
import { ArrowRight, type LucideIcon } from "lucide-react";

type Props = {
  kicker: string;
  title: string;
  excerpt: string;
  link: string;
  gradient: string;
  icon: LucideIcon;
  delay?: number;
  onClick?: () => void;
};

export function NewsCard({ kicker, title, excerpt, link, gradient, icon: Icon, delay = 0, onClick }: Props) {
  return (
    <motion.button
      type="button"
      onClick={onClick}
      initial={{ opacity: 0, y: 24 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay, ease: [0.16, 1, 0.3, 1] }}
      whileHover={{ y: -4, scale: 1.02 }}
      className="group relative h-full w-full cursor-pointer overflow-hidden rounded-[14px] border border-border text-left shadow-md transition-shadow duration-300 hover:shadow-lg"
    >
      {/* Gradient background — scale on hover */}
      <div
        className="absolute inset-0 transition-transform duration-[600ms]"
        style={{
          background: gradient,
          transform: "scale(var(--card-scale, 1))",
          transitionTimingFunction: "cubic-bezier(0.16, 1, 0.3, 1)",
        }}
      />

      {/* Subtle highlight + dark spot for texture */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          backgroundImage: [
            "radial-gradient(ellipse 40% 30% at 30% 25%, rgba(255,255,255,0.18), transparent 60%)",
            "radial-gradient(ellipse 30% 25% at 80% 80%, rgba(0,0,0,0.25), transparent 60%)",
          ].join(", "),
        }}
      />

      {/* Bottom dark fade for legibility */}
      <div
        className="pointer-events-none absolute inset-x-0 bottom-0 h-[70%]"
        style={{
          background:
            "linear-gradient(to top, rgba(7,8,11,0.92) 0%, rgba(7,8,11,0.7) 35%, rgba(7,8,11,0) 100%)",
        }}
      />

      {/* Inner accent glow on hover */}
      <div
        className="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-300 group-hover:opacity-100"
        style={{ boxShadow: "inset 0 0 40px rgba(59,91,219,0.18)" }}
      />

      <div className="relative flex h-full flex-col justify-end p-5">
        <div className="mb-2 flex items-center gap-2">
          <span className="inline-flex h-6 w-6 items-center justify-center rounded-md border border-white/15 bg-white/10">
            <Icon className="h-3 w-3" strokeWidth={2.2} />
          </span>
          <span className="text-[10px] font-semibold uppercase tracking-[0.18em] text-white/85">
            {kicker}
          </span>
        </div>

        <h3
          className="mb-2 font-display tracking-wide text-white"
          style={{ fontSize: 20, lineHeight: 1.15, letterSpacing: "0.02em" }}
        >
          {title}
        </h3>

        <p className="mb-3 line-clamp-2 text-[12.5px] leading-snug text-white/75">{excerpt}</p>

        <div className="flex items-center gap-1.5 text-[12px] font-medium tracking-wide text-white/90">
          <span>{link}</span>
          <ArrowRight className="h-3.5 w-3.5 transition-transform duration-200 group-hover:translate-x-0.5" />
        </div>
      </div>
    </motion.button>
  );
}
