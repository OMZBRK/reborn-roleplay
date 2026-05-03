import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { motion } from "framer-motion";
import {
  Calendar,
  CheckCircle2,
  ChevronRight,
  Clock,
  Loader2,
  PencilLine,
  Sparkles,
  XCircle,
} from "lucide-react";
import { PlayButton } from "../components/PlayButton";
import {
  fetchPatchnotes,
  fetchWhitelistMe,
  type PatchNoteSummary,
  type WhitelistApplication,
} from "../lib/content";

export function Home() {
  const navigate = useNavigate();
  const [latestPatch, setLatestPatch] = useState<PatchNoteSummary | null | "error">(null);
  const [whitelist, setWhitelist] = useState<WhitelistApplication | null | "error" | "loading">(
    "loading",
  );

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
        <PatchCard latest={latestPatch} onOpen={() => navigate("/patchnotes")} />
        <WhitelistCard state={whitelist} onOpen={() => navigate("/whitelist")} />
        <Card
          icon={<Calendar className="h-4 w-4" />}
          eyebrow="Actu RP"
          title="Calendrier RP"
          description="Les evenements RP en jeu seront annonces ici. Disponible apres la sortie."
          cta="Voir le reglement"
          onClick={() => navigate("/rules")}
        />
      </section>
    </div>
  );
}

function PatchCard({
  latest,
  onOpen,
}: {
  latest: PatchNoteSummary | null | "error";
  onOpen: () => void;
}) {
  if (latest === null) {
    return (
      <Card
        icon={<Sparkles className="h-4 w-4" />}
        eyebrow="Patch notes"
        title="En attente du premier patch"
        description="Aucune note publiee pour le moment. Reviens bientot."
        cta="Voir l'historique"
        onClick={onOpen}
      />
    );
  }
  if (latest === "error") {
    return (
      <Card
        icon={<Sparkles className="h-4 w-4" />}
        eyebrow="Patch notes"
        title="Indisponible"
        description="Impossible de charger le dernier patch — verifie ta connexion."
        cta="Reessayer"
        onClick={onOpen}
      />
    );
  }
  return (
    <Card
      icon={<Sparkles className="h-4 w-4" />}
      eyebrow={`Patch ${latest.version}`}
      title={latest.title}
      description={latest.excerpt}
      cta="Voir le patch"
      onClick={onOpen}
    />
  );
}

function WhitelistCard({
  state,
  onOpen,
}: {
  state: WhitelistApplication | null | "error" | "loading";
  onOpen: () => void;
}) {
  if (state === "loading") {
    return (
      <article className="flex flex-col items-start rounded-[--radius-card] border border-border bg-surface p-5">
        <div className="flex items-center gap-2 text-xs uppercase tracking-widest text-foreground-subtle">
          <Loader2 className="h-3 w-3 animate-spin" />
          Whitelist
        </div>
        <p className="mt-3 text-sm text-foreground-subtle">Chargement...</p>
      </article>
    );
  }
  if (state === "error") {
    return (
      <Card
        icon={<XCircle className="h-4 w-4" />}
        eyebrow="Whitelist"
        title="Indisponible"
        description="Impossible de recuperer ton statut de whitelist."
        cta="Reessayer"
        onClick={onOpen}
      />
    );
  }
  if (state === null) {
    return (
      <Card
        icon={<CheckCircle2 className="h-4 w-4" />}
        eyebrow="Whitelist"
        title="Statut : non postule"
        description="Tu n'as pas encore depose ta candidature RP. Postule pour rejoindre le serveur."
        cta="Postuler"
        onClick={onOpen}
      />
    );
  }
  const meta = WHITELIST_HOME[state.status];
  return (
    <Card
      icon={<meta.Icon className="h-4 w-4" />}
      eyebrow="Whitelist"
      title={meta.title(state.characterName)}
      description={meta.description}
      cta={meta.cta}
      onClick={onOpen}
    />
  );
}

const WHITELIST_HOME: Record<
  WhitelistApplication["status"],
  {
    Icon: typeof CheckCircle2;
    title: (name: string) => string;
    description: string;
    cta: string;
  }
> = {
  PENDING: {
    Icon: Clock,
    title: () => "Statut : en attente",
    description: "Le staff examine ta candidature. Reponse sous 48h.",
    cta: "Voir ma candidature",
  },
  APPROVED: {
    Icon: CheckCircle2,
    title: (name) => `Bienvenue, ${name}`,
    description: "Ta whitelist est validee. Tu peux te connecter au serveur.",
    cta: "Voir les details",
  },
  REJECTED: {
    Icon: XCircle,
    title: () => "Statut : refusee",
    description: "Le staff a refuse ta candidature. Tu peux la modifier et la resoumettre.",
    cta: "Modifier et resoumettre",
  },
  NEEDS_REVISION: {
    Icon: PencilLine,
    title: () => "Statut : revision demandee",
    description: "Le staff demande des modifications avant validation.",
    cta: "Modifier et resoumettre",
  },
};

type CardProps = {
  icon: React.ReactNode;
  eyebrow: string;
  title: string;
  description: string;
  cta: string;
  onClick?: () => void;
};

function Card({ icon, eyebrow, title, description, cta, onClick }: CardProps) {
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
        onClick={onClick}
        className="mt-5 inline-flex items-center gap-1 self-start text-sm font-medium text-accent hover:text-accent-hover"
      >
        {cta}
        <ChevronRight className="h-4 w-4" />
      </button>
    </motion.article>
  );
}
