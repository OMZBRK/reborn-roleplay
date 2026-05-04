import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import {
  AlertTriangle,
  ArrowLeft,
  Bug,
  CheckCircle2,
  Clock,
  CreditCard,
  Flag,
  HelpCircle,
  Loader2,
  Lock,
  MessageSquare,
  Plus,
  Send,
  ShieldAlert,
  Trash2,
  XCircle,
} from "lucide-react";
import {
  createTicket,
  deleteTicket,
  fetchTicket,
  fetchTickets,
  postTicketMessage,
  type CreateTicketInput,
  type TicketCategory,
  type TicketDetail,
  type TicketStatus,
  type TicketSummary,
} from "../lib/content";

type View =
  | { kind: "list" }
  | { kind: "create" }
  | { kind: "detail"; id: string };

export function Tickets() {
  const [view, setView] = useState<View>({ kind: "list" });

  return (
    <div className="px-8 py-8">
      <header className="mb-8 flex items-start justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-widest text-foreground-subtle">
            Reborn Roleplay
          </p>
          <h1 className="mt-1 font-display text-3xl font-semibold">Tickets</h1>
          <p className="mt-1 text-sm text-foreground-subtle">
            Pose une question au staff, signale un joueur, conteste une decision.
          </p>
        </div>
        {view.kind === "list" && (
          <button
            type="button"
            onClick={() => setView({ kind: "create" })}
            className="flex h-10 items-center gap-2 rounded-md bg-accent px-4 text-sm font-medium text-white transition hover:bg-accent-hover"
          >
            <Plus className="h-4 w-4" />
            Nouveau ticket
          </button>
        )}
        {view.kind !== "list" && (
          <button
            type="button"
            onClick={() => setView({ kind: "list" })}
            className="flex h-10 items-center gap-2 rounded-md border border-border bg-background px-4 text-sm font-medium hover:border-accent/50"
          >
            <ArrowLeft className="h-4 w-4" />
            Retour
          </button>
        )}
      </header>

      {view.kind === "list" && (
        <TicketsList
          onOpen={(id) => setView({ kind: "detail", id })}
          onNew={() => setView({ kind: "create" })}
        />
      )}
      {view.kind === "create" && (
        <CreateTicketForm
          onCreated={(t) => setView({ kind: "detail", id: t.id })}
          onCancel={() => setView({ kind: "list" })}
        />
      )}
      {view.kind === "detail" && (
        <TicketDetailView
          ticketId={view.id}
          onDeleted={() => setView({ kind: "list" })}
        />
      )}
    </div>
  );
}

// ──────────────────────────────────────────────────────
// List
// ──────────────────────────────────────────────────────

function TicketsList({
  onOpen,
  onNew,
}: {
  onOpen: (id: string) => void;
  onNew: () => void;
}) {
  const [items, setItems] = useState<TicketSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchTickets()
      .then((res) => {
        if (!cancelled) setItems(res);
      })
      .catch((err) => {
        if (!cancelled)
          setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (error) {
    return (
      <div className="rounded-md border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
        {error}
      </div>
    );
  }

  if (items === null) {
    return (
      <div className="flex items-center gap-2 text-sm text-foreground-subtle">
        <Loader2 className="h-4 w-4 animate-spin" />
        Chargement...
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="rounded-[--radius-card] border border-dashed border-border bg-surface p-10 text-center">
        <MessageSquare className="mx-auto h-8 w-8 text-foreground-subtle" />
        <h2 className="mt-4 font-display text-lg font-semibold">Aucun ticket</h2>
        <p className="mt-1 text-sm text-foreground-subtle">
          Tu n'as encore ouvert aucun ticket. Le staff repond sous 48h.
        </p>
        <button
          type="button"
          onClick={onNew}
          className="mt-5 inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent-hover"
        >
          <Plus className="h-4 w-4" />
          Ouvrir un ticket
        </button>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
      {items.map((ticket) => (
        <motion.button
          key={ticket.id}
          type="button"
          whileHover={{ y: -2 }}
          onClick={() => onOpen(ticket.id)}
          className="flex flex-col items-start rounded-[--radius-card] border border-border bg-surface p-5 text-left transition hover:border-accent/40"
        >
          <div className="flex w-full items-center justify-between gap-3">
            <CategoryBadge category={ticket.category} />
            <StatusBadge status={ticket.status} />
          </div>
          <h2 className="mt-3 font-display text-base font-semibold leading-snug">
            {ticket.subject}
          </h2>
          {ticket.lastMessagePreview && (
            <p className="mt-2 line-clamp-2 text-xs text-foreground-subtle">
              {ticket.lastMessagePreview}
            </p>
          )}
          <p className="mt-3 text-[11px] text-foreground-subtle">
            Mis a jour le{" "}
            {new Date(ticket.updatedAt).toLocaleDateString("fr-FR", {
              day: "2-digit",
              month: "short",
              year: "numeric",
              hour: "2-digit",
              minute: "2-digit",
            })}
          </p>
        </motion.button>
      ))}
    </div>
  );
}

// ──────────────────────────────────────────────────────
// Create
// ──────────────────────────────────────────────────────

function CreateTicketForm({
  onCreated,
  onCancel,
}: {
  onCreated: (t: TicketDetail) => void;
  onCancel: () => void;
}) {
  const [category, setCategory] = useState<TicketCategory>("OTHER");
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    const payload: CreateTicketInput = {
      category,
      subject: subject.trim(),
      message: message.trim(),
    };
    try {
      const created = await createTicket(payload);
      onCreated(created);
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
      <Field label="Catégorie">
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value as TicketCategory)}
          className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm focus:border-accent focus:outline-none"
        >
          <option value="BUG">Bug technique</option>
          <option value="REPORT_PLAYER">Signalement de joueur</option>
          <option value="WHITELIST_APPEAL">Appel whitelist</option>
          <option value="PURCHASE_ISSUE">Probleme d'achat</option>
          <option value="OTHER">Autre</option>
        </select>
      </Field>

      <Field label="Sujet" hint="Resume en une ligne (4-120 caracteres).">
        <input
          required
          minLength={4}
          maxLength={120}
          value={subject}
          onChange={(e) => setSubject(e.target.value)}
          className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm focus:border-accent focus:outline-none"
          placeholder="ex: Mes 500 ZK Coins n'ont pas ete crediter"
        />
      </Field>

      <Field
        label="Message"
        hint="Min 10 caracteres. Donne autant de details que possible : pseudo, heure, screenshots URL."
      >
        <textarea
          required
          minLength={10}
          maxLength={4000}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          className="min-h-[160px] w-full resize-y rounded-md border border-border bg-background p-3 text-sm focus:border-accent focus:outline-none"
          placeholder="J'ai achete le pack..."
        />
      </Field>

      {error && (
        <div className="flex items-start gap-2 rounded-md border border-danger/40 bg-danger/10 p-3 text-xs text-danger">
          <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <div className="flex items-center gap-2">
        <button
          type="submit"
          disabled={submitting}
          className="flex h-11 flex-1 items-center justify-center gap-2 rounded-md bg-accent px-4 font-medium text-white transition hover:bg-accent-hover disabled:opacity-60"
        >
          {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
          Envoyer le ticket
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="h-11 rounded-md border border-border bg-background px-4 text-sm font-medium hover:border-accent/50"
        >
          Annuler
        </button>
      </div>
    </form>
  );
}

// ──────────────────────────────────────────────────────
// Detail
// ──────────────────────────────────────────────────────

function TicketDetailView({
  ticketId,
  onDeleted,
}: {
  ticketId: string;
  onDeleted: () => void;
}) {
  const [ticket, setTicket] = useState<TicketDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reply, setReply] = useState("");
  const [posting, setPosting] = useState(false);
  const [deleting, setDeleting] = useState(false);

  async function handleDelete() {
    if (
      !window.confirm(
        "Supprimer ce ticket ? Cette action est definitive.",
      )
    ) {
      return;
    }
    setError(null);
    setDeleting(true);
    try {
      await deleteTicket(ticketId);
      onDeleted();
    } catch (err) {
      setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
    } finally {
      setDeleting(false);
    }
  }

  async function reload() {
    try {
      const detail = await fetchTicket(ticketId);
      setTicket(detail);
    } catch (err) {
      setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
    }
  }

  useEffect(() => {
    let cancelled = false;
    fetchTicket(ticketId)
      .then((d) => {
        if (!cancelled) setTicket(d);
      })
      .catch((err) => {
        if (!cancelled)
          setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
      });
    return () => {
      cancelled = true;
    };
  }, [ticketId]);

  async function handlePost(e: React.FormEvent) {
    e.preventDefault();
    if (!reply.trim()) return;
    setPosting(true);
    try {
      await postTicketMessage(ticketId, reply.trim());
      setReply("");
      await reload();
    } catch (err) {
      setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
    } finally {
      setPosting(false);
    }
  }

  if (error && !ticket) {
    return (
      <div className="rounded-md border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
        {error}
      </div>
    );
  }

  if (!ticket) {
    return (
      <div className="flex items-center gap-2 text-sm text-foreground-subtle">
        <Loader2 className="h-4 w-4 animate-spin" />
        Chargement...
      </div>
    );
  }

  const isClosed = ticket.status === "CLOSED";

  return (
    <div className="grid max-w-3xl grid-cols-1 gap-4">
      <div className="flex flex-col gap-3 rounded-[--radius-card] border border-border bg-surface p-5">
        <div className="flex items-start justify-between gap-3">
          <CategoryBadge category={ticket.category} />
          <StatusBadge status={ticket.status} />
        </div>
        <h2 className="font-display text-xl font-semibold">{ticket.subject}</h2>
        <div className="flex items-center justify-between gap-2">
          <p className="text-xs text-foreground-subtle">
            Ouvert le{" "}
            {new Date(ticket.createdAt).toLocaleDateString("fr-FR", {
              day: "2-digit",
              month: "long",
              year: "numeric",
              hour: "2-digit",
              minute: "2-digit",
            })}
          </p>
          {!isClosed && ticket.status !== "RESOLVED" && (
            <button
              type="button"
              onClick={handleDelete}
              disabled={deleting}
              className="inline-flex items-center gap-1.5 rounded-md border border-danger/40 bg-danger/10 px-2.5 py-1 text-[11px] font-medium text-danger transition hover:bg-danger/20 disabled:opacity-60"
            >
              {deleting ? (
                <Loader2 className="h-3 w-3 animate-spin" />
              ) : (
                <Trash2 className="h-3 w-3" />
              )}
              Supprimer
            </button>
          )}
        </div>
      </div>

      <div className="flex flex-col gap-3">
        {ticket.messages.map((m) => (
          <div
            key={m.id}
            className={`flex ${m.isStaff ? "justify-start" : "justify-end"}`}
          >
            <div
              className={`max-w-[85%] rounded-[--radius-card] border p-4 ${
                m.isStaff
                  ? "border-accent/30 bg-accent/5"
                  : "border-border bg-surface"
              }`}
            >
              <p className="mb-2 text-[11px] font-medium uppercase tracking-wider text-foreground-subtle">
                {m.isStaff ? "Staff" : "Toi"} ·{" "}
                {new Date(m.createdAt).toLocaleString("fr-FR", {
                  day: "2-digit",
                  month: "short",
                  hour: "2-digit",
                  minute: "2-digit",
                })}
              </p>
              <p className="whitespace-pre-wrap text-sm leading-relaxed">
                {m.content}
              </p>
            </div>
          </div>
        ))}
      </div>

      {isClosed ? (
        <div className="flex items-center gap-2 rounded-md border border-border bg-background p-4 text-sm text-foreground-subtle">
          <Lock className="h-4 w-4" />
          Ce ticket est ferme. Ouvre un nouveau ticket si besoin.
        </div>
      ) : (
        <form onSubmit={handlePost} className="flex flex-col gap-3">
          <textarea
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            minLength={1}
            maxLength={4000}
            placeholder="Ecris ta reponse..."
            className="min-h-[100px] w-full resize-y rounded-md border border-border bg-surface p-3 text-sm focus:border-accent focus:outline-none"
          />
          {error && (
            <div className="flex items-start gap-2 rounded-md border border-danger/40 bg-danger/10 p-3 text-xs text-danger">
              <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}
          <button
            type="submit"
            disabled={posting || !reply.trim()}
            className="flex h-10 items-center justify-center gap-2 self-end rounded-md bg-accent px-4 text-sm font-medium text-white transition hover:bg-accent-hover disabled:opacity-60"
          >
            {posting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            Envoyer
          </button>
        </form>
      )}
    </div>
  );
}

// ──────────────────────────────────────────────────────
// Bits visuels
// ──────────────────────────────────────────────────────

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

function CategoryBadge({ category }: { category: TicketCategory }) {
  const meta = CATEGORY_META[category];
  return (
    <span className="flex items-center gap-1.5 rounded-md bg-accent/10 px-2 py-1 text-[11px] font-medium uppercase tracking-wider text-accent">
      <meta.Icon className="h-3 w-3" />
      {meta.label}
    </span>
  );
}

function StatusBadge({ status }: { status: TicketStatus }) {
  const meta = STATUS_META[status];
  return (
    <span
      className={`flex items-center gap-1.5 rounded-md border px-2 py-1 text-[11px] font-medium uppercase tracking-wider ${meta.classes}`}
    >
      <meta.Icon className="h-3 w-3" />
      {meta.label}
    </span>
  );
}

const CATEGORY_META: Record<
  TicketCategory,
  { label: string; Icon: typeof Bug }
> = {
  BUG: { label: "Bug", Icon: Bug },
  REPORT_PLAYER: { label: "Signalement", Icon: Flag },
  WHITELIST_APPEAL: { label: "Appel whitelist", Icon: ShieldAlert },
  PURCHASE_ISSUE: { label: "Achat", Icon: CreditCard },
  OTHER: { label: "Autre", Icon: HelpCircle },
};

const STATUS_META: Record<
  TicketStatus,
  { label: string; Icon: typeof Clock; classes: string }
> = {
  OPEN: {
    label: "Ouvert",
    Icon: Clock,
    classes: "border-warning/40 bg-warning/10 text-warning",
  },
  IN_PROGRESS: {
    label: "En cours",
    Icon: MessageSquare,
    classes: "border-accent/40 bg-accent/10 text-accent",
  },
  RESOLVED: {
    label: "Resolu",
    Icon: CheckCircle2,
    classes: "border-success/40 bg-success/10 text-success",
  },
  CLOSED: {
    label: "Ferme",
    Icon: XCircle,
    classes: "border-border bg-background text-foreground-subtle",
  },
};
