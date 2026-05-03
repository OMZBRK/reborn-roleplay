import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  Loader2,
  PencilLine,
  Send,
  XCircle,
} from "lucide-react";
import {
  fetchWhitelistMe,
  resubmitWhitelist,
  submitWhitelist,
  type WhitelistApplication,
  type WhitelistSubmitInput,
} from "../lib/content";

type Stage =
  | { kind: "loading" }
  | { kind: "form"; mode: "create" | "edit"; previous?: WhitelistApplication }
  | { kind: "view"; application: WhitelistApplication }
  | { kind: "error"; message: string };

export function Whitelist() {
  const [stage, setStage] = useState<Stage>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;
    fetchWhitelistMe()
      .then((res) => {
        if (cancelled) return;
        if (!res.application) {
          setStage({ kind: "form", mode: "create" });
        } else {
          setStage({ kind: "view", application: res.application });
        }
      })
      .catch((err) => {
        if (cancelled) return;
        setStage({
          kind: "error",
          message: typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur",
        });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="px-8 py-8">
      <header className="mb-8">
        <p className="text-xs uppercase tracking-widest text-foreground-subtle">Reborn Roleplay</p>
        <h1 className="mt-1 font-display text-3xl font-semibold">Whitelist</h1>
        <p className="mt-1 text-sm text-foreground-subtle">
          Pour acceder au serveur, soumets ta candidature au staff. Reponse sous 48h.
        </p>
      </header>

      {stage.kind === "loading" && (
        <div className="flex items-center gap-2 text-sm text-foreground-subtle">
          <Loader2 className="h-4 w-4 animate-spin" />
          Chargement...
        </div>
      )}

      {stage.kind === "error" && (
        <div className="rounded-md border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
          {stage.message}
        </div>
      )}

      {stage.kind === "form" && (
        <ApplicationForm
          mode={stage.mode}
          previous={stage.previous}
          onDone={(application) => setStage({ kind: "view", application })}
        />
      )}

      {stage.kind === "view" && (
        <StatusView
          application={stage.application}
          onEditRequested={() =>
            setStage({ kind: "form", mode: "edit", previous: stage.application })
          }
        />
      )}
    </div>
  );
}

function ApplicationForm({
  mode,
  previous,
  onDone,
}: {
  mode: "create" | "edit";
  previous?: WhitelistApplication;
  onDone: (app: WhitelistApplication) => void;
}) {
  const [characterName, setCharacterName] = useState(previous?.characterName ?? "");
  const [characterAge, setCharacterAge] = useState<number | "">(previous?.characterAge ?? "");
  const [background, setBackground] = useState(previous?.background ?? "");
  const [motivation, setMotivation] = useState(previous?.motivation ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (typeof characterAge !== "number") {
      setError("L'age doit etre un nombre.");
      return;
    }
    setSubmitting(true);
    const payload: WhitelistSubmitInput = {
      characterName: characterName.trim(),
      characterAge,
      background: background.trim(),
      motivation: motivation.trim(),
    };
    try {
      const result = mode === "edit" ? await resubmitWhitelist(payload) : await submitWhitelist(payload);
      onDone(result);
    } catch (err) {
      setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="grid max-w-3xl grid-cols-1 gap-4 rounded-[--radius-card] border border-border bg-surface p-6"
    >
      {previous?.reviewNotes && (
        <div className="rounded-md border border-warning/40 bg-warning/10 p-3 text-xs text-warning">
          <p className="font-semibold">Note du staff</p>
          <p className="mt-1 text-foreground-subtle">{previous.reviewNotes}</p>
        </div>
      )}

      <Field label="Pseudo RP du personnage" hint="Ton nom in-game pour le RP — different du pseudo MC.">
        <input
          required
          minLength={2}
          maxLength={32}
          value={characterName}
          onChange={(e) => setCharacterName(e.target.value)}
          className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm focus:border-accent focus:outline-none"
          placeholder="ex: Hikamatsu Nara"
        />
      </Field>

      <Field label="Age du personnage" hint="Entre 13 et 120 ans.">
        <input
          required
          type="number"
          min={13}
          max={120}
          value={characterAge}
          onChange={(e) => setCharacterAge(e.target.value === "" ? "" : Number(e.target.value))}
          className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm focus:border-accent focus:outline-none"
          placeholder="ex: 22"
        />
      </Field>

      <Field
        label="Background du personnage"
        hint="Min 50 caracteres. Raconte d'ou il vient, ses motivations profondes."
      >
        <textarea
          required
          minLength={50}
          maxLength={4000}
          value={background}
          onChange={(e) => setBackground(e.target.value)}
          className="min-h-[140px] w-full resize-y rounded-md border border-border bg-background p-3 text-sm focus:border-accent focus:outline-none"
          placeholder="Ne dans le pays du Feu, …"
        />
      </Field>

      <Field
        label="Motivation a rejoindre Reborn"
        hint="Min 20 caracteres. Pourquoi toi, pourquoi maintenant ?"
      >
        <textarea
          required
          minLength={20}
          maxLength={2000}
          value={motivation}
          onChange={(e) => setMotivation(e.target.value)}
          className="min-h-[100px] w-full resize-y rounded-md border border-border bg-background p-3 text-sm focus:border-accent focus:outline-none"
          placeholder="J'ai suivi le serveur depuis…"
        />
      </Field>

      {error && (
        <div className="flex items-start gap-2 rounded-md border border-danger/40 bg-danger/10 p-3 text-xs text-danger">
          <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <button
        type="submit"
        disabled={submitting}
        className="mt-2 flex h-11 items-center justify-center gap-2 rounded-md bg-accent px-4 font-medium text-white transition hover:bg-accent-hover disabled:opacity-60"
      >
        {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
        {mode === "edit" ? "Renvoyer la candidature" : "Soumettre la candidature"}
      </button>
    </form>
  );
}

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-xs font-medium uppercase tracking-wider text-foreground-subtle">
        {label}
      </span>
      {children}
      {hint && <span className="text-[11px] text-foreground-subtle">{hint}</span>}
    </label>
  );
}

function StatusView({
  application,
  onEditRequested,
}: {
  application: WhitelistApplication;
  onEditRequested: () => void;
}) {
  const palette = STATUS_STYLE[application.status];

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      className="max-w-3xl space-y-4"
    >
      <div
        className={`flex items-start gap-3 rounded-[--radius-card] border p-5 ${palette.border} ${palette.bg}`}
      >
        <div className={`mt-0.5 flex h-9 w-9 items-center justify-center rounded-md ${palette.iconBg}`}>
          <palette.Icon className={`h-5 w-5 ${palette.iconColor}`} />
        </div>
        <div className="flex-1">
          <p className={`text-xs uppercase tracking-widest ${palette.eyebrow}`}>
            {STATUS_LABEL[application.status]}
          </p>
          <h2 className="mt-1 font-display text-lg font-semibold">{palette.title}</h2>
          <p className="mt-1 text-sm text-foreground-subtle">{palette.description}</p>
          {application.reviewNotes && (
            <div className="mt-3 rounded-md border border-border bg-background/50 p-3 text-xs">
              <p className="font-semibold text-foreground">Note du staff</p>
              <p className="mt-1 text-foreground-subtle">{application.reviewNotes}</p>
            </div>
          )}

          {(application.status === "NEEDS_REVISION" || application.status === "REJECTED") && (
            <button
              type="button"
              onClick={onEditRequested}
              className="mt-4 inline-flex items-center gap-2 rounded-md border border-border bg-background px-3 py-1.5 text-xs font-medium hover:border-accent/50 hover:text-accent"
            >
              <PencilLine className="h-3.5 w-3.5" />
              Modifier et resoumettre
            </button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-3 rounded-[--radius-card] border border-border bg-surface p-5 md:grid-cols-2">
        <Detail label="Personnage" value={application.characterName} />
        <Detail label="Age" value={`${application.characterAge} ans`} />
        <Detail
          label="Soumise"
          value={new Date(application.submittedAt).toLocaleDateString("fr-FR", {
            day: "2-digit",
            month: "long",
            year: "numeric",
          })}
        />
        {application.reviewedAt && (
          <Detail
            label="Examinee"
            value={new Date(application.reviewedAt).toLocaleDateString("fr-FR", {
              day: "2-digit",
              month: "long",
              year: "numeric",
            })}
          />
        )}
      </div>

      <div className="rounded-[--radius-card] border border-border bg-surface p-5">
        <p className="text-xs uppercase tracking-widest text-foreground-subtle">Background</p>
        <p className="mt-2 whitespace-pre-wrap text-sm text-foreground-subtle">{application.background}</p>
      </div>

      <div className="rounded-[--radius-card] border border-border bg-surface p-5">
        <p className="text-xs uppercase tracking-widest text-foreground-subtle">Motivation</p>
        <p className="mt-2 whitespace-pre-wrap text-sm text-foreground-subtle">{application.motivation}</p>
      </div>
    </motion.div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[11px] uppercase tracking-wider text-foreground-subtle">{label}</p>
      <p className="mt-0.5 text-sm">{value}</p>
    </div>
  );
}

const STATUS_LABEL: Record<WhitelistApplication["status"], string> = {
  PENDING: "En attente",
  APPROVED: "Acceptee",
  REJECTED: "Refusee",
  NEEDS_REVISION: "Revision demandee",
};

const STATUS_STYLE: Record<
  WhitelistApplication["status"],
  {
    border: string;
    bg: string;
    iconBg: string;
    iconColor: string;
    eyebrow: string;
    Icon: typeof Clock;
    title: string;
    description: string;
  }
> = {
  PENDING: {
    border: "border-warning/40",
    bg: "bg-warning/5",
    iconBg: "bg-warning/15",
    iconColor: "text-warning",
    eyebrow: "text-warning",
    Icon: Clock,
    title: "Le staff regarde ta candidature",
    description: "Reponse sous 48h en moyenne. Tu seras notifie via Discord (si lie).",
  },
  APPROVED: {
    border: "border-success/40",
    bg: "bg-success/5",
    iconBg: "bg-success/15",
    iconColor: "text-success",
    eyebrow: "text-success",
    Icon: CheckCircle2,
    title: "Bienvenue dans le RP",
    description: "Tu peux maintenant rejoindre le serveur. Lis le reglement avant ta premiere session.",
  },
  REJECTED: {
    border: "border-danger/40",
    bg: "bg-danger/5",
    iconBg: "bg-danger/15",
    iconColor: "text-danger",
    eyebrow: "text-danger",
    Icon: XCircle,
    title: "Candidature refusee",
    description: "Tu peux corriger et resoumettre une nouvelle candidature.",
  },
  NEEDS_REVISION: {
    border: "border-accent/40",
    bg: "bg-accent/5",
    iconBg: "bg-accent/15",
    iconColor: "text-accent",
    eyebrow: "text-accent",
    Icon: PencilLine,
    title: "Le staff te demande des precisions",
    description: "Lis la note du staff ci-dessous, modifie ta candidature et renvoie-la.",
  },
};
