import { useEffect, useMemo, useRef, useState } from "react";
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
  MessagesSquare,
  MessageSquare,
  Paperclip,
  Plus,
  RefreshCw,
  Send,
  ShieldAlert,
  Ticket as TicketIcon,
  Trash2,
  UploadCloud,
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
import { formatIpcError } from "../lib/errors";
import { useBadgesStore } from "../stores/badges-store";
import {
  ChatThread,
  FOCUS_RING,
  IMAGE_ACCEPT,
  InlineError,
  isAllowedImage,
  rejectedFilesMessage,
  SyncIndicator,
  useChatFeed,
  type ChatMessage,
} from "../components/chat/ChatThread";

// Flow inspiré des maquettes Reborn (accent crimson, pas bleu) :
//  - "hub"    : page d'accueil 2 cartes (Ouvrir un ticket / Mes tickets)
//  - "create" : formulaire plein écran (catégorie + titre + message + fichier)
//  - "list"   : master-detail (sidebar liste + chat), atteint via « Mes tickets »
// Le hub et le form sont pleine page (sans sidebar) ; seul le mode list garde
// le layout 2-panes historique. Liste et conversation sont sondées toutes
// les 5 s (useChatFeed : pause pendant une partie, erreurs visibles).

/** Délai d'assignation au-delà duquel le joueur peut demander une reprise. */
const RECLAIM_AFTER_MS = 4 * 60 * 60 * 1000;
const NO_TICKETS: TicketSummary[] = [];

type Mode =
  | { kind: "hub" }
  | { kind: "create" }
  | { kind: "list"; id: string | null };

function summaryOf(t: TicketDetail): TicketSummary {
  return {
    id: t.id,
    category: t.category,
    subject: t.subject,
    status: t.status,
    createdAt: t.createdAt,
    updatedAt: t.updatedAt,
    lastMessagePreview: t.messages[0]?.content.slice(0, 140) ?? null,
  };
}

export function Tickets() {
  const [mode, setMode] = useState<Mode>({ kind: "hub" });
  // Échec d'envoi des pièces jointes d'un ticket qu'on vient de créer :
  // affiché dans sa conversation (le ticket, lui, existe bien).
  const [createdNotice, setCreatedNotice] = useState<{ id: string; message: string } | null>(
    null,
  );

  // Ouvrir la page tickets = tout marquer comme lu (efface le badge sidebar).
  useEffect(() => {
    void useBadgesStore.getState().markRead("tickets");
  }, []);

  // Sondage de la liste : récupère aussi les changements de statut côté
  // staff (OPEN → IN_PROGRESS → RESOLVED) et les aperçus de derniers
  // messages qui arrivent depuis Discord.
  const listFeed = useChatFeed(() => fetchTickets());
  const list = listFeed.data ?? NO_TICKETS;

  // En mode list sans sélection mais avec des tickets : auto-sélectionne le
  // plus récent pour ne pas rester sur un pane vide.
  useEffect(() => {
    if (mode.kind === "list" && mode.id === null && list.length > 0) {
      setMode({ kind: "list", id: list[0].id });
    }
  }, [list, mode]);

  // Nombre de tickets « en cours » (ouverts ou en traitement) pour la carte hub.
  const openCount = list.filter(
    (t) => t.status === "OPEN" || t.status === "IN_PROGRESS",
  ).length;

  function handleCreated(t: TicketDetail, attachmentError?: string) {
    listFeed.mutate((prev) => [summaryOf(t), ...(prev ?? []).filter((x) => x.id !== t.id)]);
    listFeed.refresh();
    setCreatedNotice(attachmentError ? { id: t.id, message: attachmentError } : null);
    setMode({ kind: "list", id: t.id });
  }

  function handleDeleted(id: string) {
    listFeed.mutate((prev) => (prev ?? []).filter((t) => t.id !== id));
    listFeed.refresh();
    setMode({ kind: "list", id: null });
  }

  if (mode.kind === "hub") {
    return (
      <TicketsHub
        openCount={openCount}
        onOpen={() => setMode({ kind: "create" })}
        onList={() => setMode({ kind: "list", id: list[0]?.id ?? null })}
      />
    );
  }

  if (mode.kind === "create") {
    return (
      <CreateView
        onCreated={handleCreated}
        onCancel={() => setMode({ kind: "hub" })}
      />
    );
  }

  // mode.kind === "list" → master-detail
  const selectedId = mode.id;
  const listError = listFeed.sync.error;
  return (
    <div className="tk-shell">
      <aside className="tk-sidebar">
        <div className="tk-sidebar-header">
          <button
            type="button"
            className={`tk-back ${FOCUS_RING}`}
            onClick={() => setMode({ kind: "hub" })}
          >
            <ArrowLeft size={15} />
            Tickets
          </button>
          <button
            type="button"
            className={`tk-new-btn ${FOCUS_RING}`}
            onClick={() => setMode({ kind: "create" })}
          >
            <Plus size={14} />
            Nouveau ticket
          </button>
        </div>

        <div className="tk-list">
          {/* Échec sans aucune donnée : bandeau complet. Avec une liste déjà
              affichée : indicateur discret, elle reste utilisable. */}
          {listError && list.length === 0 && (
            <InlineError
              message={`Impossible de charger tes tickets. ${listError}`}
              onRetry={listFeed.refresh}
            />
          )}
          {listError && list.length > 0 && (
            <SyncIndicator variant="block" sync={listFeed.sync} onRetry={listFeed.refresh} />
          )}
          {listFeed.loading && list.length === 0 && (
            <div className="tk-list-empty">
              <Loader2 size={18} className="mx-auto mb-2 animate-spin opacity-60" />
              Chargement…
            </div>
          )}
          {!listFeed.loading && list.length === 0 && !listError && (
            <div className="tk-list-empty">
              <MessageSquare size={28} className="mx-auto mb-3 opacity-40" />
              Aucun ticket pour le moment.
              <br />
              Le staff répond sous 48h.
            </div>
          )}
          {list.map((t) => (
            <TicketListItem
              key={t.id}
              ticket={t}
              active={selectedId === t.id}
              onClick={() => setMode({ kind: "list", id: t.id })}
            />
          ))}
        </div>
      </aside>

      <section className="tk-pane">
        {selectedId ? (
          // key : changer de ticket remonte le pane (brouillon, pièces
          // jointes et sondage repartent de zéro, aucun mélange possible).
          <ChatPane
            key={selectedId}
            ticketId={selectedId}
            notice={createdNotice?.id === selectedId ? createdNotice.message : null}
            onDismissNotice={() => setCreatedNotice(null)}
            onChanged={listFeed.refresh}
            onDeleted={() => handleDeleted(selectedId)}
          />
        ) : (
          <EmptyPane onNew={() => setMode({ kind: "create" })} />
        )}
      </section>
    </div>
  );
}

// ──────────────────────────────────────────────────────────
// Hub — page d'accueil 2 cartes (cf. maquette ticketref)
// ──────────────────────────────────────────────────────────

function TicketsHub({
  openCount,
  onOpen,
  onList,
}: {
  openCount: number;
  onOpen: () => void;
  onList: () => void;
}) {
  return (
    <div className="tk-hub">
      <header className="tk-hub-head">
        <div className="tk-hub-icon">
          <TicketIcon size={30} />
        </div>
        <div>
          <h1 className="tk-hub-title">Ticket</h1>
          <p className="tk-hub-sub">
            Ouvre un ticket afin de contacter l'équipe.
            <br />
            Sélectionne la catégorie correspondant à ta demande pour un
            traitement plus rapide.
          </p>
        </div>
      </header>

      <div className="tk-hub-cards">
        <button type="button" className={`tk-hub-card ${FOCUS_RING}`} onClick={onOpen}>
          <div className="tk-hub-card-body">
            <h2 className="tk-hub-card-title">Ouvrir un ticket</h2>
          </div>
          <MessagesSquare className="tk-hub-card-art" size={132} strokeWidth={1.1} />
        </button>

        <button type="button" className={`tk-hub-card ${FOCUS_RING}`} onClick={onList}>
          <div className="tk-hub-card-body">
            <h2 className="tk-hub-card-title">Mes tickets</h2>
            <span className="tk-hub-card-count">
              {openCount} en cours
            </span>
          </div>
          <TicketIcon className="tk-hub-card-art" size={132} strokeWidth={1.1} />
        </button>
      </div>
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
      aria-current={active ? "true" : undefined}
      className={`tk-item${active ? " is-active" : ""} ${FOCUS_RING}`}
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
      <button type="button" className={`wl-btn-primary glow ${FOCUS_RING}`} onClick={onNew}>
        <Plus size={16} />
        Nouveau ticket
      </button>
    </div>
  );
}

// ──────────────────────────────────────────────────────────
// Create pane
// ──────────────────────────────────────────────────────────

function CreateView({
  onCreated,
  onCancel,
}: {
  onCreated: (t: TicketDetail, attachmentError?: string) => void;
  onCancel: () => void;
}) {
  const [category, setCategory] = useState<TicketCategory>("OTHER");
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (submitting) return;
    setError(null);
    setSubmitting(true);
    const payload: CreateTicketInput = {
      category,
      subject: subject.trim(),
      message: message.trim(),
    };
    try {
      let created: TicketDetail;
      try {
        created = await createTicket(payload);
      } catch (err) {
        setError(formatIpcError(err, "Impossible de créer le ticket"));
        return;
      }
      // Le create n'accepte pas de pièces jointes : on les uploade puis on
      // les poste en message de suivi. Le ticket existe déjà : un échec ici
      // ne doit pas laisser le formulaire ouvert (un nouvel envoi créerait
      // un doublon), on ouvre la conversation avec l'erreur.
      let attachmentError: string | undefined;
      if (files.length > 0) {
        const urls: string[] = [];
        try {
          for (const f of files) {
            try {
              urls.push(await uploadAttachment(f));
            } catch (err) {
              throw new Error(formatIpcError(err, `Envoi de « ${f.name} » impossible`));
            }
          }
          await postTicketMessage(
            created.id,
            `${files.length} pièce${files.length > 1 ? "s" : ""} jointe${files.length > 1 ? "s" : ""}`,
            urls,
          );
        } catch (err) {
          attachmentError = `Ticket créé, mais les pièces jointes n'ont pas été envoyées. ${formatIpcError(err)} Tu peux les joindre depuis la conversation.`;
        }
      }
      onCreated(created, attachmentError);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="tk-create-view">
      <header className="tk-create-head">
        <button
          type="button"
          className={`tk-back-round ${FOCUS_RING}`}
          onClick={onCancel}
          disabled={submitting}
          aria-label="Retour"
        >
          <ArrowLeft size={18} />
        </button>
        <div className="tk-hub-icon sm">
          <TicketIcon size={22} />
        </div>
        <h1 className="tk-create-title">
          Nouveau <span className="accent">Ticket</span>
        </h1>
      </header>

      <form onSubmit={handleSubmit} className="tk-create-form">
        <div className="tk-create-grid">
          <div className="wl-field">
            <label className="wl-field-label" htmlFor="tk-category">
              Choisir votre catégorie
            </label>
            <select
              id="tk-category"
              value={category}
              onChange={(e) => setCategory(e.target.value as TicketCategory)}
              className="wl-input tk-create-select"
            >
              <option value="BUG">Bug technique</option>
              <option value="REPORT_PLAYER">Signalement de joueur</option>
              <option value="WHITELIST_APPEAL">Appel whitelist</option>
              <option value="PURCHASE_ISSUE">Problème d'achat</option>
              <option value="OTHER">Autre</option>
            </select>
          </div>

          <div className="wl-field">
            <label className="wl-field-label" htmlFor="tk-subject">
              Titre du ticket<span className="wl-required">*</span>
            </label>
            <input
              id="tk-subject"
              required
              minLength={4}
              maxLength={120}
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              className="wl-input"
              placeholder="Ex : Crash du jeu, problème de connexion…"
            />
          </div>
        </div>

        <div className="wl-field">
          <label className="wl-field-label" htmlFor="tk-message">
            Message<span className="wl-required">*</span>
          </label>
          <textarea
            id="tk-message"
            required
            minLength={10}
            maxLength={4000}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            className="wl-textarea"
            rows={6}
            placeholder="Décrivez en détail votre problème"
          />
        </div>

        <FileDrop
          files={files}
          setFiles={setFiles}
          onRejected={setError}
          disabled={submitting}
        />

        {error && (
          <InlineError message={error} onDismiss={() => setError(null)} />
        )}

        <div className="tk-create-actions">
          <button
            type="button"
            className={`tk-btn-cancel ${FOCUS_RING}`}
            onClick={onCancel}
            disabled={submitting}
          >
            Annuler
          </button>
          <button
            type="submit"
            className={`tk-btn-submit ${FOCUS_RING}`}
            disabled={submitting || !subject.trim() || message.length < 10}
          >
            {submitting ? (
              <>
                <Loader2 size={15} className="animate-spin" /> Envoi…
              </>
            ) : (
              <>
                Envoyez le ticket <Send size={15} />
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}

// ──────────────────────────────────────────────────────────
// Dropzone fichier (cf. maquette newticketref)
// ──────────────────────────────────────────────────────────

function FileDrop({
  files,
  setFiles,
  onRejected,
  disabled,
}: {
  files: File[];
  setFiles: React.Dispatch<React.SetStateAction<File[]>>;
  /** Fichiers refusés (pas des images acceptées par l'API) : message à afficher. */
  onRejected: (message: string) => void;
  disabled?: boolean;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [drag, setDrag] = useState(false);

  function add(fl: FileList | null) {
    if (!fl || fl.length === 0) return;
    const all = Array.from(fl);
    const rejected = all.filter((f) => !isAllowedImage(f));
    if (rejected.length > 0) onRejected(rejectedFilesMessage(rejected));
    const images = all.filter(isAllowedImage);
    if (images.length > 0) setFiles((prev) => [...prev, ...images]);
  }

  function browse() {
    if (!disabled) inputRef.current?.click();
  }

  return (
    <div
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled || undefined}
      aria-label="Importer des images (PNG, JPEG, WebP ou GIF)"
      className={`tk-drop${drag ? " is-drag" : ""} ${FOCUS_RING}`}
      onClick={browse}
      onKeyDown={(e) => {
        // Seulement sur la zone elle-même (pas sur les boutons « Retirer »).
        if (e.target !== e.currentTarget) return;
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          browse();
        }
      }}
      onDragOver={(e) => {
        e.preventDefault();
        if (!disabled) setDrag(true);
      }}
      onDragLeave={() => setDrag(false)}
      onDrop={(e) => {
        e.preventDefault();
        setDrag(false);
        if (!disabled) add(e.dataTransfer.files);
      }}
    >
      <input
        ref={inputRef}
        type="file"
        accept={IMAGE_ACCEPT}
        multiple
        hidden
        disabled={disabled}
        onChange={(e) => {
          add(e.target.files);
          e.target.value = "";
        }}
      />
      <div className="tk-drop-text">
        <div className="tk-drop-title">Importer un fichier</div>
        <div className="tk-drop-sub">Glisser déposer ou cliquer pour parcourir</div>
      </div>
      <UploadCloud className="tk-drop-icon" size={22} />

      {files.length > 0 && (
        <div
          className="tk-drop-files"
          onClick={(e) => e.stopPropagation()}
        >
          {files.map((f, i) => (
            <span key={`${f.name}-${i}`} className="tk-drop-chip">
              <Paperclip size={11} />
              <span className="tk-drop-chip-name">{f.name}</span>
              <button
                type="button"
                aria-label={`Retirer ${f.name}`}
                className={FOCUS_RING}
                disabled={disabled}
                onClick={() => setFiles((prev) => prev.filter((_, j) => j !== i))}
              >
                <X size={11} />
              </button>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

// ──────────────────────────────────────────────────────────
// Chat pane
// ──────────────────────────────────────────────────────────

function toChatMessage(m: TicketMessage): ChatMessage {
  return {
    id: m.id,
    author: m.isStaff ? "staff" : "me",
    authorName: m.authorName,
    content: m.content,
    attachments: m.attachments,
    createdAt: m.createdAt,
  };
}

function ChatPane({
  ticketId,
  notice,
  onDismissNotice,
  onChanged,
  onDeleted,
}: {
  ticketId: string;
  /** Erreur venue de la création (pièces jointes) à afficher dans le fil. */
  notice: string | null;
  onDismissNotice: () => void;
  /** Un message a été envoyé : la liste peut se rafraîchir (aperçu). */
  onChanged: () => void;
  onDeleted: () => void;
}) {
  // Sondage 5 s du détail (récupère les nouveaux messages staff).
  const feed = useChatFeed(() => fetchTicket(ticketId));
  const ticket = feed.data;
  const messages = useMemo(() => (ticket?.messages ?? []).map(toChatMessage), [ticket?.messages]);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [reclaiming, setReclaiming] = useState(false);
  const [reclaimMsg, setReclaimMsg] = useState<{ ok: boolean; text: string } | null>(null);
  // Refresh chaque minute pour que la durée « depuis Xh » reste fraîche
  // sans dépendre du sondage (qui ne re-rend que si le ticket change).
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
      feed.refresh();
      setReclaimMsg({
        ok: true,
        text: "Reprise demandée — un autre membre du staff peut maintenant prendre ton ticket.",
      });
    } catch (err) {
      setReclaimMsg({ ok: false, text: formatIpcError(err, "Impossible de demander une reprise") });
    } finally {
      setReclaiming(false);
    }
  }

  async function handleSend(content: string, attachmentUrls?: string[]) {
    const created = await postTicketMessage(ticketId, content, attachmentUrls);
    // Le message envoyé apparaît tout de suite ; le tour relancé juste
    // après apporte la version serveur (et les éventuelles réponses).
    feed.mutate((prev) =>
      prev && !prev.messages.some((m) => m.id === created.id)
        ? { ...prev, messages: [...prev.messages, created] }
        : prev,
    );
    feed.refresh();
    onChanged();
  }

  async function handleDelete() {
    if (
      !window.confirm("Supprimer ce ticket ? Cette action est définitive.")
    ) {
      return;
    }
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteTicket(ticketId);
      onDeleted();
    } catch (err) {
      setDeleteError(formatIpcError(err, "Impossible de supprimer le ticket"));
    } finally {
      setDeleting(false);
    }
  }

  if (feed.loading && !ticket) {
    return (
      <div className="tk-pane-empty">
        <Loader2 className="animate-spin" />
        <p className="text-sm text-foreground-subtle">Chargement du ticket…</p>
      </div>
    );
  }

  if (!ticket) {
    return (
      <div className="tk-pane-empty" role="alert">
        <AlertTriangle className="text-danger" />
        <p className="max-w-md text-center text-sm text-danger">
          {feed.sync.error
            ? `Impossible d'ouvrir ce ticket. ${feed.sync.error}`
            : "Ticket introuvable."}
        </p>
        <p className="text-xs text-foreground-muted">
          Nouvel essai automatique toutes les 5 secondes.
        </p>
        <button
          type="button"
          className={`wl-btn-mini-ghost ${FOCUS_RING}`}
          onClick={feed.refresh}
        >
          <RefreshCw size={13} />
          Réessayer
        </button>
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
            className={`wl-btn-mini-ghost ${FOCUS_RING}`}
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
                className={`wl-btn-mini-ghost ${FOCUS_RING}`}
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
          role={reclaimMsg.ok ? "status" : "alert"}
          className="mx-3 mb-2 text-xs"
          style={{
            color: reclaimMsg.ok ? "var(--color-foreground-subtle)" : "var(--color-danger)",
          }}
        >
          {reclaimMsg.text}
        </div>
      )}

      <div className="tk-chat-area">
        <ChatThread
          messages={messages}
          sync={feed.sync}
          onRetry={feed.refresh}
          onSend={handleSend}
          meLabel="Toi"
          placeholder="Écris ta réponse (Ctrl+Entrée pour envoyer)"
          intro={
            <>
              Conversation reliée à un thread Discord. Les réponses du staff
              arrivent ici automatiquement.
            </>
          }
          extra={
            <>
              {notice && (
                <InlineError className="mb-3" message={notice} onDismiss={onDismissNotice} />
              )}
              {deleteError && (
                <InlineError
                  className="mb-3"
                  message={deleteError}
                  onDismiss={() => setDeleteError(null)}
                />
              )}
            </>
          }
          closedNotice={
            isClosed ? (
              <div className="mb-5 flex items-center gap-2 rounded-md border border-border bg-surface-elevated p-4 text-sm text-foreground-subtle">
                <Lock className="h-4 w-4" />
                Ce ticket est fermé. Ouvre un nouveau ticket si besoin.
              </div>
            ) : undefined
          }
        />
      </div>
    </>
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
