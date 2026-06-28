import { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  AlertTriangle,
  Bug,
  CheckCircle2,
  Clock,
  CreditCard,
  Flag,
  HelpCircle,
  Loader2,
  Lock,
  MessageSquare,
  Paperclip,
  Plus,
  Send,
  ShieldAlert,
  Trash2,
  X,
  XCircle,
} from "lucide-react";
import {
  createTicket,
  deleteTicket,
  fetchTicket,
  fetchTickets,
  reclaimTicket,
  postTicketMessage,
  uploadAttachment,
  type CreateTicketInput,
  type TicketCategory,
  type TicketDetail,
  type TicketMessage,
  type TicketStatus,
  type TicketSummary,
} from "../lib/content";

// Layout 2 panes : sidebar liste à gauche, chat embarqué à droite. Le user
// reste toujours sur la même page — pas de view-switching brutal. Le pattern
// suit StatusChatPage de la whitelist (bulles me/staff, polling 5s, badge
// catégorie + status, file picker pour les attachments).
//
// Modes du pane droit :
//  - "empty"  : aucun ticket sélectionné (vue par défaut quand la liste a des items)
//  - "create" : formulaire de création (catégorie + sujet + premier message)
//  - "chat"   : conversation sur le ticket sélectionné

const POLL_INTERVAL_MS = 5000;

type Mode =
  | { kind: "empty" }
  | { kind: "create" }
  | { kind: "chat"; id: string };

export function Tickets() {
  const [mode, setMode] = useState<Mode>({ kind: "empty" });
  const [list, setList] = useState<TicketSummary[]>([]);
  const [listError, setListError] = useState<string | null>(null);
  const [listLoading, setListLoading] = useState(true);

  // Polling de la liste : on rafraîchit aussi pour catcher les nouveaux
  // status (staff change OPEN → IN_PROGRESS → RESOLVED) et les nouveaux
  // last-message previews qui arrivent depuis Discord.
  useEffect(() => {
    let cancelled = false;
    let timer: number | undefined;
    async function poll() {
      try {
        const items = await fetchTickets();
        if (!cancelled) {
          setList(items);
          setListError(null);
        }
      } catch (err) {
        if (!cancelled) {
          setListError(
            typeof err === "string"
              ? err
              : (err as { message?: string }).message ?? "Erreur",
          );
        }
      } finally {
        if (!cancelled) {
          setListLoading(false);
          timer = window.setTimeout(poll, POLL_INTERVAL_MS);
        }
      }
    }
    poll();
    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, []);

  // Sélection d'un ticket par défaut quand on charge si rien n'est sélectionné
  // ET qu'on a au moins un ticket : auto-sélectionne le plus récent.
  useEffect(() => {
    if (mode.kind === "empty" && list.length > 0) {
      setMode({ kind: "chat", id: list[0].id });
    }
  }, [list, mode.kind]);

  function handleCreated(t: TicketDetail) {
    // Le nouveau ticket est ajouté en tête de liste ; on switch directement
    // sur sa conversation.
    setList((prev) => [
      {
        id: t.id,
        category: t.category,
        subject: t.subject,
        status: t.status,
        createdAt: t.createdAt,
        updatedAt: t.updatedAt,
        lastMessagePreview: t.messages[0]?.content.slice(0, 140) ?? null,
      },
      ...prev,
    ]);
    setMode({ kind: "chat", id: t.id });
  }

  function handleDeleted(id: string) {
    setList((prev) => prev.filter((t) => t.id !== id));
    setMode({ kind: "empty" });
  }

  return (
    <div className="tk-shell">
      <aside className="tk-sidebar">
        <div className="tk-sidebar-header">
          <h1 className="tk-sidebar-title">Tickets</h1>
          <p className="tk-sidebar-sub">
            Pose une question au staff, signale un joueur, conteste une décision.
          </p>
          <button
            type="button"
            className="tk-new-btn"
            onClick={() => setMode({ kind: "create" })}
          >
            <Plus size={14} />
            Nouveau ticket
          </button>
        </div>

        <div className="tk-list">
          {listError && (
            <div className="rounded-md border border-danger/40 bg-danger/10 px-3 py-2 text-xs text-danger">
              {listError}
            </div>
          )}
          {listLoading && list.length === 0 && (
            <div className="tk-list-empty">
              <Loader2
                size={18}
                className="mx-auto mb-2 animate-spin opacity-60"
              />
              Chargement…
            </div>
          )}
          {!listLoading && list.length === 0 && !listError && (
            <div className="tk-list-empty">
              <MessageSquare
                size={28}
                className="mx-auto mb-3 opacity-40"
              />
              Aucun ticket pour le moment.
              <br />
              Le staff répond sous 48h.
            </div>
          )}
          {list.map((t) => (
            <TicketListItem
              key={t.id}
              ticket={t}
              active={mode.kind === "chat" && mode.id === t.id}
              onClick={() => setMode({ kind: "chat", id: t.id })}
            />
          ))}
        </div>
      </aside>

      <section className="tk-pane">
        {mode.kind === "empty" && <EmptyPane onNew={() => setMode({ kind: "create" })} />}
        {mode.kind === "create" && (
          <CreatePane
            onCreated={handleCreated}
            onCancel={() =>
              setMode(list.length > 0 ? { kind: "chat", id: list[0].id } : { kind: "empty" })
            }
          />
        )}
        {mode.kind === "chat" && (
          <ChatPane
            ticketId={mode.id}
            onDeleted={() => handleDeleted(mode.id)}
          />
        )}
      </section>
    </div>
  );
}

// ──────────────────────────────────────────────────────────
// Sidebar item
// ──────────────────────────────────────────────────────────

function TicketListItem({
  ticket,
  active,
  onClick,
}: {
  ticket: TicketSummary;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`tk-item${active ? " is-active" : ""}`}
    >
      <div className="tk-item-row">
        <CategoryBadge category={ticket.category} />
        <StatusBadge status={ticket.status} />
      </div>
      <div className="tk-item-subject">{ticket.subject}</div>
      {ticket.lastMessagePreview && (
        <div className="tk-item-preview">{ticket.lastMessagePreview}</div>
      )}
      <div className="tk-item-meta">
        {new Date(ticket.updatedAt).toLocaleDateString("fr-FR", {
          day: "2-digit",
          month: "short",
          hour: "2-digit",
          minute: "2-digit",
        })}
      </div>
    </button>
  );
}

// ──────────────────────────────────────────────────────────
// Empty pane (rien sélectionné)
// ──────────────────────────────────────────────────────────

function EmptyPane({ onNew }: { onNew: () => void }) {
  return (
    <div className="tk-pane-empty">
      <div className="tk-pane-empty-icon">
        <MessageSquare size={26} />
      </div>
      <h2 className="tk-pane-empty-title">Aucun ticket sélectionné</h2>
      <p className="text-sm text-foreground-subtle">
        Choisis une conversation à gauche ou ouvre-en une nouvelle.
      </p>
      <button type="button" className="wl-btn-primary glow" onClick={onNew}>
        <Plus size={16} />
        Nouveau ticket
      </button>
    </div>
  );
}

// ──────────────────────────────────────────────────────────
// Create pane
// ──────────────────────────────────────────────────────────

function CreatePane({
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
      setError(
        typeof err === "string"
          ? err
          : (err as { message?: string }).message ?? "Erreur",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <div className="tk-pane-header">
        <div className="tk-pane-header-left">
          <h2 className="tk-pane-subject">Nouveau ticket</h2>
          <p className="tk-pane-meta-row">
            Le staff répond sous 48h en moyenne.
          </p>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="tk-pane-form">
        <div className="tk-create-card">
          <div className="wl-field">
            <label className="wl-field-label">Catégorie</label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value as TicketCategory)}
              className="wl-input"
            >
              <option value="BUG">Bug technique</option>
              <option value="REPORT_PLAYER">Signalement de joueur</option>
              <option value="WHITELIST_APPEAL">Appel whitelist</option>
              <option value="PURCHASE_ISSUE">Problème d'achat</option>
              <option value="OTHER">Autre</option>
            </select>
          </div>

          <div className="wl-field">
            <label className="wl-field-label">
              Sujet<span className="wl-required">*</span>
            </label>
            <input
              required
              minLength={4}
              maxLength={120}
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              className="wl-input"
              placeholder="ex: Mes 500 ZK Coins n'ont pas été crédités"
            />
            <div className="wl-field-helper">Résume en une ligne (4–120 caractères).</div>
          </div>

          <div className="wl-field">
            <label className="wl-field-label">
              Message<span className="wl-required">*</span>
            </label>
            <textarea
              required
              minLength={10}
              maxLength={4000}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              className="wl-textarea"
              rows={6}
              placeholder="J'ai acheté le pack…"
            />
            <div className="wl-field-helper">
              Min 10 caractères. Donne autant de détails que possible : pseudo,
              heure, screenshots URL.
            </div>
          </div>

          {error && (
            <div className="flex items-start gap-2 rounded-md border border-danger/40 bg-danger/10 p-3 text-xs text-danger">
              <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="flex items-center justify-end gap-2">
            <button
              type="button"
              className="wl-btn-ghost"
              onClick={onCancel}
              disabled={submitting}
            >
              Annuler
            </button>
            <button
              type="submit"
              className="wl-btn-primary glow"
              disabled={submitting || !subject.trim() || message.length < 10}
            >
              {submitting ? (
                <>
                  <Loader2 size={14} className="animate-spin" /> Envoi…
                </>
              ) : (
                <>
                  Envoyer le ticket <Send size={14} />
                </>
              )}
            </button>
          </div>
        </div>
      </form>
    </>
  );
}

// ──────────────────────────────────────────────────────────
// Chat pane
// ──────────────────────────────────────────────────────────

type LocalAttachment = {
  id: string;
  name: string;
  size: number;
  url?: string;
  uploading?: boolean;
};

function ChatPane({
  ticketId,
  onDeleted,
}: {
  ticketId: string;
  onDeleted: () => void;
}) {
  const [ticket, setTicket] = useState<TicketDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [attachments, setAttachments] = useState<LocalAttachment[]>([]);
  const [sending, setSending] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [reclaiming, setReclaiming] = useState(false);
  const [reclaimMsg, setReclaimMsg] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  // Refresh chaque minute pour que la duree "depuis Xh" reste fraiche
  // sans dependre du polling 5s qui peut ne pas changer la ref ticket.
  const [, setTick] = useState(0);
  useEffect(() => {
    if (!ticket?.assignedAt) return;
    const t = window.setInterval(() => setTick((n) => n + 1), 60_000);
    return () => window.clearInterval(t);
  }, [ticket?.assignedAt]);

  async function handleReclaim() {
    setReclaiming(true);
    setReclaimMsg(null);
    try {
      await reclaimTicket(ticketId);
      const detail = await fetchTicket(ticketId);
      setTicket(detail);
      setReclaimMsg("Reprise demandee — un autre staff peut reprendre.");
    } catch (err) {
      setReclaimMsg(
        typeof err === "string"
          ? err
          : (err as { message?: string }).message ?? "Echec",
      );
    } finally {
      setReclaiming(false);
    }
  }

  // Reset le draft + attachments quand on change de ticket sélectionné.
  useEffect(() => {
    setDraft("");
    setAttachments([]);
    setLoading(true);
    setTicket(null);
    setError(null);
  }, [ticketId]);

  // Polling 5s sur le détail (récupère les nouveaux messages staff).
  useEffect(() => {
    let cancelled = false;
    let timer: number | undefined;
    async function poll() {
      try {
        const detail = await fetchTicket(ticketId);
        if (!cancelled) {
          setTicket(detail);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            typeof err === "string"
              ? err
              : (err as { message?: string }).message ?? "Erreur",
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
          timer = window.setTimeout(poll, POLL_INTERVAL_MS);
        }
      }
    }
    poll();
    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [ticketId]);

  async function handlePickFiles(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    e.target.value = "";
    if (files.length === 0) return;

    const images = files.filter((f) => f.type.startsWith("image/"));
    if (images.length < files.length) {
      setError("Seules les images peuvent être jointes.");
    }

    for (const f of images) {
      const id = `${Date.now()}-${f.name}-${Math.random().toString(36).slice(2, 8)}`;
      setAttachments((prev) => [
        ...prev,
        { id, name: f.name, size: f.size, uploading: true },
      ]);
      try {
        const url = await uploadAttachment(f);
        setAttachments((prev) =>
          prev.map((a) => (a.id === id ? { ...a, url, uploading: false } : a)),
        );
      } catch (err) {
        setAttachments((prev) => prev.filter((a) => a.id !== id));
        setError(
          typeof err === "string"
            ? err
            : (err as { message?: string }).message ?? "Upload échoué",
        );
      }
    }
  }

  function removeAttachment(id: string) {
    setAttachments((prev) => prev.filter((a) => a.id !== id));
  }

  async function handleSend() {
    const trimmed = draft.trim();
    if (!trimmed || attachments.some((a) => a.uploading)) return;
    setSending(true);
    setError(null);
    try {
      const urls = attachments
        .map((a) => a.url)
        .filter((u): u is string => !!u);
      await postTicketMessage(ticketId, trimmed, urls.length > 0 ? urls : undefined);
      setDraft("");
      setAttachments([]);
      // Refetch immédiatement plutôt que d'attendre le prochain tick.
      const detail = await fetchTicket(ticketId);
      setTicket(detail);
    } catch (err) {
      setError(
        typeof err === "string"
          ? err
          : (err as { message?: string }).message ?? "Erreur",
      );
    } finally {
      setSending(false);
    }
  }

  async function handleDelete() {
    if (
      !window.confirm("Supprimer ce ticket ? Cette action est définitive.")
    ) {
      return;
    }
    setDeleting(true);
    setError(null);
    try {
      await deleteTicket(ticketId);
      onDeleted();
    } catch (err) {
      setError(
        typeof err === "string"
          ? err
          : (err as { message?: string }).message ?? "Erreur",
      );
    } finally {
      setDeleting(false);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
      e.preventDefault();
      void handleSend();
    }
  }

  if (loading && !ticket) {
    return (
      <div className="tk-pane-empty">
        <Loader2 className="animate-spin" />
        <p className="text-sm text-foreground-subtle">Chargement du ticket…</p>
      </div>
    );
  }

  if (!ticket) {
    return (
      <div className="tk-pane-empty">
        <p className="text-sm text-danger">{error ?? "Ticket introuvable."}</p>
      </div>
    );
  }

  const isClosed = ticket.status === "CLOSED";
  const canDelete = !isClosed && ticket.status !== "RESOLVED";

  return (
    <>
      <div className="tk-pane-header">
        <div className="tk-pane-header-left">
          <h2 className="tk-pane-subject">{ticket.subject}</h2>
          <div className="tk-pane-meta-row">
            <CategoryBadge category={ticket.category} />
            <StatusBadge status={ticket.status} />
            <span>
              Ouvert le{" "}
              {new Date(ticket.createdAt).toLocaleDateString("fr-FR", {
                day: "2-digit",
                month: "long",
                year: "numeric",
                hour: "2-digit",
                minute: "2-digit",
              })}
            </span>
          </div>
        </div>
        {canDelete && (
          <button
            type="button"
            className="wl-btn-mini-ghost"
            onClick={handleDelete}
            disabled={deleting}
            style={{
              borderColor: "rgba(239, 68, 68, 0.4)",
              color: "var(--color-danger)",
            }}
          >
            {deleting ? (
              <Loader2 size={13} className="animate-spin" />
            ) : (
              <Trash2 size={13} />
            )}
            Supprimer
          </button>
        )}
      </div>

      {ticket.assignee && ticket.assignedAt && (() => {
        const elapsed = Date.now() - new Date(ticket.assignedAt).getTime();
        const RECLAIM_AFTER_MS = 4 * 60 * 60 * 1000;
        const canReclaim = elapsed >= RECLAIM_AFTER_MS && !isClosed && ticket.status !== "RESOLVED";
        const minutes = Math.max(1, Math.floor(elapsed / 60_000));
        const hours = Math.floor(minutes / 60);
        const since =
          hours >= 1
            ? `${hours}h${minutes % 60 > 0 ? ` ${minutes % 60}min` : ""}`
            : `${minutes} min`;
        return (
          <div
            className="mx-3 mb-2 flex items-center justify-between gap-3 rounded-[10px] border px-3 py-2 text-xs"
            style={{
              background: canReclaim
                ? "var(--color-warning-soft)"
                : "var(--color-accent-soft)",
              borderColor: canReclaim
                ? "rgba(245, 158, 11, 0.4)"
                : "rgba(160, 24, 43, 0.4)",
              color: canReclaim
                ? "var(--color-warning)"
                : "var(--color-accent)",
            }}
          >
            <span>
              Pris en charge par @{ticket.assignee.username ?? "?"} depuis {since}.
            </span>
            {canReclaim && (
              <button
                type="button"
                className="wl-btn-mini-ghost"
                onClick={handleReclaim}
                disabled={reclaiming}
                style={{
                  borderColor: "rgba(245, 158, 11, 0.5)",
                  color: "var(--color-warning)",
                }}
              >
                {reclaiming ? (
                  <Loader2 size={12} className="animate-spin" />
                ) : null}
                Demander une reprise
              </button>
            )}
          </div>
        );
      })()}
      {reclaimMsg && (
        <div
          className="mx-3 mb-2 text-xs"
          style={{ color: "var(--color-foreground-subtle)" }}
        >
          {reclaimMsg}
        </div>
      )}

      <div className="tk-chat-area">
        <div className="wl-chat">
          <motion.div
            className="wl-msg-system"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
          >
            <em>
              Conversation reliée à un thread Discord. Les réponses du staff
              arrivent ici automatiquement.
            </em>
          </motion.div>

          {ticket.messages.map((m) => (
            <ChatBubble key={m.id} message={m} />
          ))}
        </div>

        {error && (
          <div className="mb-3 flex items-center gap-2 rounded-md border border-danger/40 bg-danger/10 px-3 py-2 text-xs text-danger">
            <AlertTriangle size={14} />
            {error}
          </div>
        )}

        {isClosed ? (
          <div className="mb-5 flex items-center gap-2 rounded-md border border-border bg-surface-elevated p-4 text-sm text-foreground-subtle">
            <Lock className="h-4 w-4" />
            Ce ticket est fermé. Ouvre un nouveau ticket si besoin.
          </div>
        ) : (
          <div className="wl-chat-input-wrap">
            <textarea
              className="wl-chat-input"
              placeholder="Écris ta réponse (Ctrl+Entrée pour envoyer)"
              rows={1}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={sending}
            />
            {attachments.length > 0 && (
              <div className="wl-chat-pending-attachments">
                {attachments.map((a) => (
                  <span key={a.id} className="wl-attachment-chip">
                    {a.uploading ? (
                      <Loader2 size={12} className="animate-spin" />
                    ) : (
                      <Paperclip size={12} />
                    )}
                    <span className="truncate">{a.name}</span>
                    <span className="wl-attachment-size">
                      {a.uploading ? "envoi…" : formatBytes(a.size)}
                    </span>
                    <button
                      type="button"
                      className="wl-attachment-remove"
                      onClick={() => removeAttachment(a.id)}
                      aria-label={`Retirer ${a.name}`}
                    >
                      <X size={11} />
                    </button>
                  </span>
                ))}
              </div>
            )}
            <div className="wl-chat-actions">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                multiple
                style={{ display: "none" }}
                onChange={handlePickFiles}
              />
              <button
                type="button"
                className="wl-chat-icon-btn"
                onClick={() => fileInputRef.current?.click()}
                aria-label="Joindre un fichier"
                disabled={sending}
              >
                <Paperclip size={16} />
              </button>
              <button
                type="button"
                className="wl-chat-send"
                aria-label="Envoyer"
                onClick={handleSend}
                disabled={
                  sending ||
                  draft.trim().length === 0 ||
                  attachments.some((a) => a.uploading)
                }
              >
                {sending ? (
                  <Loader2 size={16} className="animate-spin" />
                ) : (
                  <Send size={16} />
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}

// ──────────────────────────────────────────────────────────
// Bulle de message (réutilise wl-msg-* pour rester cohérent
// visuellement avec StatusChatPage).
// ──────────────────────────────────────────────────────────

function ChatBubble({ message }: { message: TicketMessage }) {
  const time = formatTime(message.createdAt);

  if (message.isStaff) {
    return (
      <motion.div
        className="wl-msg wl-msg-staff"
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <div
          className="wl-msg-avatar"
          style={{ background: "linear-gradient(135deg,#8b5cf6,#a78bfa)" }}
        >
          {(message.authorName ?? "S").charAt(0).toUpperCase()}
        </div>
        <div className="wl-msg-body">
          <div className="wl-msg-meta">
            <span className="wl-msg-author">{message.authorName ?? "Staff"}</span>
            <span
              className="wl-role-badge"
              style={{ ["--role-color" as string]: "var(--color-role-staff)" }}
            >
              Staff
            </span>
            <span
              style={{
                color: "var(--color-foreground-muted)",
                fontSize: 11,
                fontFamily: "var(--font-mono)",
              }}
            >
              {time}
            </span>
          </div>
          {message.content && (
            <div className="wl-msg-bubble wl-msg-bubble-staff">
              {message.content}
            </div>
          )}
          {message.attachments.length > 0 && (
            <div className="wl-msg-attachments">
              {message.attachments.map((a, i) => (
                <a
                  key={i}
                  href={a.url}
                  target="_blank"
                  rel="noreferrer"
                  className="wl-attachment-chip"
                >
                  <Paperclip size={12} />
                  <span className="truncate">{shortenUrl(a.url)}</span>
                </a>
              ))}
            </div>
          )}
        </div>
      </motion.div>
    );
  }

  // USER (toi)
  return (
    <motion.div
      className="wl-msg wl-msg-me"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
    >
      <div className="wl-msg-body">
        <div className="wl-msg-meta" style={{ justifyContent: "flex-end" }}>
          <span
            style={{
              color: "var(--color-foreground-muted)",
              fontSize: 11,
              fontFamily: "var(--font-mono)",
            }}
          >
            {time}
          </span>
          <span className="wl-msg-author">Toi</span>
        </div>
        {message.content && (
          <div className="wl-msg-bubble wl-msg-bubble-me">{message.content}</div>
        )}
        {message.attachments.length > 0 && (
          <div className="wl-msg-attachments">
            {message.attachments.map((a, i) => (
              <a
                key={i}
                href={a.url}
                target="_blank"
                rel="noreferrer"
                className="wl-attachment-chip"
              >
                <Paperclip size={12} />
                <span className="truncate">{shortenUrl(a.url)}</span>
              </a>
            ))}
          </div>
        )}
      </div>
    </motion.div>
  );
}

// ──────────────────────────────────────────────────────────
// Bits visuels
// ──────────────────────────────────────────────────────────

function CategoryBadge({ category }: { category: TicketCategory }) {
  const meta = CATEGORY_META[category];
  return (
    <span className="tk-badge tk-badge-cat">
      <meta.Icon size={11} />
      {meta.label}
    </span>
  );
}

function StatusBadge({ status }: { status: TicketStatus }) {
  const meta = STATUS_META[status];
  return (
    <span className={`tk-badge tk-badge-status-${status}`}>
      <meta.Icon size={11} />
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
  WHITELIST_APPEAL: { label: "Appel WL", Icon: ShieldAlert },
  PURCHASE_ISSUE: { label: "Achat", Icon: CreditCard },
  OTHER: { label: "Autre", Icon: HelpCircle },
};

const STATUS_META: Record<
  TicketStatus,
  { label: string; Icon: typeof Clock }
> = {
  OPEN: { label: "Ouvert", Icon: Clock },
  IN_PROGRESS: { label: "En cours", Icon: MessageSquare },
  RESOLVED: { label: "Résolu", Icon: CheckCircle2 },
  CLOSED: { label: "Fermé", Icon: XCircle },
};

function formatBytes(n: number): string {
  if (n < 1024) return `${n} o`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} ko`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} Mo`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} Go`;
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function shortenUrl(url: string): string {
  try {
    const u = new URL(url);
    const parts = u.pathname.split("/").filter(Boolean);
    return parts[parts.length - 1] ?? u.host;
  } catch {
    return url.slice(0, 32);
  }
}
