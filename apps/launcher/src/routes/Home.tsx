import { motion } from "framer-motion";
import { Calendar, CheckCircle2, ChevronRight, Sparkles } from "lucide-react";
import { PlayButton } from "../components/PlayButton";

export function Home() {
  return (
    <div className="flex min-h-full flex-col">
      {/* Header artwork */}
      <section className="relative h-[360px] overflow-hidden">
        <div
          className="absolute inset-0"
          style={{
            background:
              "linear-gradient(180deg, rgba(7, 8, 11, 0) 30%, #07080b 100%), radial-gradient(ellipse at 50% 0%, #1c2a55 0%, #0a0d18 70%)",
          }}
        />
        <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
          <motion.p
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4 }}
            className="text-xs uppercase tracking-[0.4em] text-foreground-subtle"
          >
            Reborn Roleplay — Naruto edition
          </motion.p>
          <motion.h1
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.05 }}
            className="mt-3 max-w-2xl px-6 font-display text-3xl font-semibold leading-snug"
          >
            Dans l'ombre ou la lumiere, chaque ninja ecrit sa propre destinee.
          </motion.h1>

          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.15 }}
            className="mt-10"
          >
            <PlayButton />
          </motion.div>
        </div>
      </section>

      {/* Cartes */}
      <section className="grid flex-1 grid-cols-1 gap-4 p-8 lg:grid-cols-3">
        <Card
          icon={<Sparkles className="h-4 w-4" />}
          eyebrow="Patch 1.0.0"
          title="Premiere release publique"
          description="Le launcher Reborn est officiellement en ligne. Lis le patch note pour decouvrir les nouveautes."
          cta="Voir le patch"
        />
        <Card
          icon={<CheckCircle2 className="h-4 w-4" />}
          eyebrow="Whitelist"
          title="Statut : non postule"
          description="Tu n'as pas encore depose ta candidature RP. Postule pour rejoindre le serveur."
          cta="Postuler"
        />
        <Card
          icon={<Calendar className="h-4 w-4" />}
          eyebrow="Actu RP"
          title="Tournoi des Chuunin"
          description="Sam 10/05 a 20h00 — venez supporter votre village. Notification 30 min avant."
          cta="Voir l'evenement"
        />
      </section>
    </div>
  );
}

type CardProps = {
  icon: React.ReactNode;
  eyebrow: string;
  title: string;
  description: string;
  cta: string;
};

function Card({ icon, eyebrow, title, description, cta }: CardProps) {
  return (
    <motion.article
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="flex flex-col rounded-[--radius-card] border border-border bg-surface p-5 transition hover:border-accent/40"
    >
      <div className="flex items-center gap-2 text-xs uppercase tracking-widest text-foreground-subtle">
        <span className="flex h-6 w-6 items-center justify-center rounded-md bg-accent/10 text-accent">
          {icon}
        </span>
        {eyebrow}
      </div>
      <h3 className="mt-3 font-display text-lg font-semibold leading-snug">{title}</h3>
      <p className="mt-2 flex-1 text-sm text-foreground-subtle">{description}</p>
      <button
        type="button"
        className="mt-5 inline-flex items-center gap-1 self-start text-sm font-medium text-accent hover:text-accent-hover"
      >
        {cta}
        <ChevronRight className="h-4 w-4" />
      </button>
    </motion.article>
  );
}
