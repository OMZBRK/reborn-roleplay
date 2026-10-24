import { useCallback, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { motion } from "framer-motion";
import { AlertTriangle, Loader2, Paperclip, RefreshCw, Send, WifiOff, X } from "lucide-react";
import { uploadAttachment } from "../../lib/content";
import { formatIpcError, ipcErrorKind } from "../../lib/errors";
import { usePolling } from "../../hooks/use-polling";

// Fil de discussion partagé entre les tickets (routes/Tickets.tsx) et le
// chat de candidature whitelist (components/whitelist/StatusChatPage.tsx) :
//   - useChatFeed : sondage toutes les 5 s via usePolling (pas de
//     chevauchement, pause pendant une partie, annulation au démontage).
//     Un échec n'est plus avalé : il remonte dans `sync`, le fil affiche un
//     indicateur discret « Hors ligne — nouvel essai… » avec la vraie cause,
//     et le tour suivant reprend tout seul. Sert aussi aux autres données
//     sondées de ces pages (liste des tickets, avancement whitelist).
//   - ChatThread : bulles, saisie (Ctrl+Entrée), pièces jointes (images
//     uploadées dès la sélection), bandeau d'erreur d'envoi.
// Les classes wl-* viennent de globals.css : visuel identique aux deux
// anciennes copies.

export const CHAT_POLL_MS = 5000;

/** Anneau de focus clavier (visible uniquement à la navigation au clavier). */
export const FOCUS_RING =
  "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus-ring";

export type ChatAuthor = "me" | "staff" | "system";

/** Message normalisé (TicketMessage et WhitelistMessage s'y ramènent). */
export type ChatMessage = {
  id: string;
  author: ChatAuthor;
  authorName: string | null;
  content: string;
  attachments: { url: string }[];
  createdAt: string;
};

// ──────────────────────────────────────────────────────────
// Sondage du fil
// ──────────────────────────────────────────────────────────

export type ChatSync = {
  /** Cause réelle du dernier échec de sondage, null si le dernier tour a réussi. */
  error: string | null;
  /** Vrai si l'échec est une coupure réseau (serveur injoignable, délai). */
  offline: boolean;
};

export type ChatFeed<T> = {
  data: T | null;
  /** Premier chargement en cours (ni donnée ni échec pour l'instant). */
  loading: boolean;
  sync: ChatSync;
  /** Lance un tour tout de suite (après un envoi, bouton « Réessayer »). */
  refresh: () => void;
  /** Mise à jour locale (message envoyé, ticket créé) : un tour parti avant
   *  elle est ignoré, pour ne pas faire disparaître ce qu'on vient d'ajouter. */
  mutate: (update: (prev: T | null) => T | null) => void;
};

const SYNC_OK: ChatSync = { error: null, offline: false };
const OFFLINE_KINDS = new Set(["network", "http", "timeout"]);

function jsonOf(value: unknown): string | null {
  try {
    return JSON.stringify(value);
  } catch {
    return null;
  }
}

export type ChatFeedOptions = {
  /** Faux = pas de sondage (défaut : vrai). */
  enabled?: boolean;
  /** Intervalle entre deux tours, en ms (défaut : 5 s). */
  intervalMs?: number;
  /** Action en tête du message d'échec (« Avancement non actualisé »). */
  context?: string;
};

/**
 * Charge `load` tout de suite puis toutes les `intervalMs` ms (5 s par
 * défaut), en pause pendant une partie. Une réponse identique à la
 * précédente ne provoque aucun re-rendu.
 */
export function useChatFeed<T>(
  load: (signal: AbortSignal) => Promise<T>,
  { enabled = true, intervalMs = CHAT_POLL_MS, context }: ChatFeedOptions = {},
): ChatFeed<T> {
  const [data, setData] = useState<T | null>(null);
  const [settled, setSettled] = useState(false);
  const [sync, setSync] = useState<ChatSync>(SYNC_OK);
  // Génération : incrémentée à chaque mutation locale.
  const genRef = useRef(0);
  const lastJsonRef = useRef<string | null>(null);

  const refresh = usePolling(
    async (signal) => {
      const gen = genRef.current;
      try {
        const next = await load(signal);
        if (signal.aborted || gen !== genRef.current) return;
        const json = jsonOf(next);
        if (json === null || json !== lastJsonRef.current) {
          lastJsonRef.current = json;
          setData(next);
        }
        setSync((prev) => (prev.error === null ? prev : SYNC_OK));
      } catch (err) {
        if (signal.aborted) return;
        const error = formatIpcError(err, context);
        const offline = OFFLINE_KINDS.has(ipcErrorKind(err) ?? "");
        setSync((prev) =>
          prev.error === error && prev.offline === offline ? prev : { error, offline },
        );
      } finally {
        if (!signal.aborted) setSettled(true);
      }
    },
    intervalMs,
    { enabled },
  );

  const mutate = useCallback((update: (prev: T | null) => T | null) => {
    genRef.current += 1;
    lastJsonRef.current = null;
    setData((prev) => update(prev));
  }, []);

  return { data, loading: enabled && !settled, sync, refresh, mutate };
}

// ──────────────────────────────────────────────────────────
// Pièces jointes
// ──────────────────────────────────────────────────────────

/** Types acceptés par POST /v1/upload (apps/api/src/upload/upload.config.ts). */
const IMAGE_TYPES = new Set(["image/png", "image/jpeg", "image/webp", "image/gif"]);
export const IMAGE_ACCEPT = [...IMAGE_TYPES].join(",");

export function isAllowedImage(file: File): boolean {
  return IMAGE_TYPES.has(file.type);
}

/** « Fichier ignoré : « x.pdf ». Seules les images… » (3 noms au plus). */
export function rejectedFilesMessage(files: File[]): string {
  const shown = files.slice(0, 3).map((f) => `« ${f.name} »`).join(", ");
  const more = files.length > 3 ? ` et ${files.length - 3} autre${files.length > 4 ? "s" : ""}` : "";
  const head = files.length > 1 ? "Fichiers ignorés" : "Fichier ignoré";
  return `${head} : ${shown}${more}. Seules les images PNG, JPEG, WebP ou GIF peuvent être jointes.`;
}

type LocalAttachment = {
  id: string;
  name: string;
  size: number;
  // URL publique une fois l'upload terminé (POST /v1/upload via le backend
  // Rust). Tant que `uploading` est vrai, l'envoi du message est bloqué.
  url?: string;
  uploading?: boolean;
};

// ──────────────────────────────────────────────────────────
// Bandeau d'erreur
// ──────────────────────────────────────────────────────────

/** Bandeau d'erreur rouge (message réel + « Réessayer » / fermeture au besoin). */
export function InlineError({
  message,
  onRetry,
  retrying = false,
  onDismiss,
  large = false,
  className = "",
}: {
  message: string;
  onRetry?: () => void;
  retrying?: boolean;
  onDismiss?: () => void;
  /** Texte plus grand (bandeaux de page). */
  large?: boolean;
  className?: string;
}) {
  return (
    <div
      role="alert"
      className={`flex items-start gap-2 rounded-md border border-danger/40 bg-danger/10 text-danger ${
        large ? "px-4 py-3 text-sm" : "px-3 py-2 text-xs"
      } ${className}`}
    >
      <AlertTriangle size={large ? 16 : 14} className="mt-px shrink-0" />
      <span className="min-w-0 flex-1 break-words">{message}</span>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          disabled={retrying}
          className={`inline-flex shrink-0 items-center gap-1 rounded-sm font-medium underline-offset-2 hover:underline disabled:opacity-60 ${FOCUS_RING}`}
        >
          <RefreshCw size={12} className={retrying ? "animate-spin" : undefined} />
          Réessayer
        </button>
      )}
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Masquer ce message"
          className={`shrink-0 rounded-sm opacity-70 hover:opacity-100 ${FOCUS_RING}`}
        >
          <X size={14} />
        </button>
      )}
    </div>
  );
}

// ──────────────────────────────────────────────────────────
// Fil + saisie
// ──────────────────────────────────────────────────────────

type ChatThreadProps = {
  messages: ChatMessage[];
  /** Premier chargement en cours : petit indicateur dans le fil. */
  loading?: boolean;
  sync: ChatSync;
  onRetry: () => void;
  /** Envoie le message. Rejette en cas d'échec : l'erreur est affichée et
   *  le brouillon (texte + pièces jointes) conservé. */
  onSend: (content: string, attachmentUrls?: string[]) => Promise<void>;
  /** Message système affiché en tête du fil. */
  intro: ReactNode;
  /** Libellé de l'auteur pour ses propres messages (« Toi », « Vous »). */
  meLabel: string;
  placeholder: string;
  /** Remplace la zone de saisie (ex. ticket fermé). */
  closedNotice?: ReactNode;
  /** Bandeaux de la page (suppression…), affichés au-dessus de la saisie. */
  extra?: ReactNode;
};

/** Distance au bas du fil (px) sous laquelle on suit les nouveaux messages. */
const STICK_PX = 80;

export function ChatThread({
  messages,
  loading = false,
  sync,
  onRetry,
  onSend,
  intro,
  meLabel,
  placeholder,
  closedNotice,
  extra,
}: ChatThreadProps) {
  const [draft, setDraft] = useState("");
  const [attachments, setAttachments] = useState<LocalAttachment[]>([]);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  // Vrai tant que l'utilisateur est en bas du fil : les nouveaux messages
  // y font défiler ; s'il remonte lire l'historique, on ne le déplace pas.
  const stickRef = useRef(true);

  useLayoutEffect(() => {
    const el = listRef.current;
    if (el && stickRef.current) el.scrollTop = el.scrollHeight;
  }, [messages.length, loading, sync.error]);

  function handleScroll() {
    const el = listRef.current;
    if (el) stickRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < STICK_PX;
  }

  const uploading = attachments.some((a) => a.uploading);
  const canSend = !sending && !uploading && draft.trim().length > 0;

  async function uploadOne(file: File) {
    const id = `${Date.now()}-${file.name}-${Math.random().toString(36).slice(2, 8)}`;
    setAttachments((prev) => [...prev, { id, name: file.name, size: file.size, uploading: true }]);
    try {
      const url = await uploadAttachment(file);
      setAttachments((prev) => prev.map((a) => (a.id === id ? { ...a, url, uploading: false } : a)));
    } catch (err) {
      // Échec : on retire la puce et on affiche la vraie cause (cumulée
      // avec les autres refus de la même sélection).
      setAttachments((prev) => prev.filter((a) => a.id !== id));
      const message = formatIpcError(err, `Envoi de « ${file.name} » impossible`);
      setError((prev) => (prev ? `${prev} ${message}` : message));
    }
  }

  function handlePickFiles(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    e.target.value = "";
    if (files.length === 0) return;
    const rejected = files.filter((f) => !isAllowedImage(f));
    setError(rejected.length > 0 ? rejectedFilesMessage(rejected) : null);
    for (const f of files) if (isAllowedImage(f)) void uploadOne(f);
  }

  async function handleSend() {
    const content = draft.trim();
    if (!content || sending || uploading) return;
    setSending(true);
    setError(null);
    stickRef.current = true;
    try {
      const urls = attachments.flatMap((a) => (a.url ? [a.url] : []));
      await onSend(content, urls.length > 0 ? urls : undefined);
      setDraft("");
      setAttachments([]);
    } catch (err) {
      setError(formatIpcError(err, "Message non envoyé"));
    } finally {
      setSending(false);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
      e.preventDefault();
      void handleSend();
    }
  }

  return (
    <>
      <div className="wl-chat" ref={listRef} onScroll={handleScroll}>
        <motion.div
          className="wl-msg-system"
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
        >
          <em>{intro}</em>
        </motion.div>

        {loading && messages.length === 0 && (
          <div className="flex items-center justify-center gap-2 py-4 text-xs text-foreground-muted">
            <Loader2 size={14} className="animate-spin" />
            Chargement des messages…
          </div>
        )}

        {messages.map((m) => (
          <ChatBubble key={m.id} message={m} meLabel={meLabel} />
        ))}

        <SyncIndicator sync={sync} onRetry={onRetry} />
      </div>

      {error && (
        <InlineError className="mb-3" message={error} onDismiss={() => setError(null)} />
      )}
      {extra}

      {closedNotice ?? (
        <div className="wl-chat-input-wrap">
          {/* readOnly plutôt que disabled pendant l'envoi : le champ garde le
              focus clavier, on peut enchaîner sans recliquer. */}
          <textarea
            className="wl-chat-input"
            placeholder={placeholder}
            aria-label="Message"
            rows={1}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={handleKeyDown}
            readOnly={sending}
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
                    className={`wl-attachment-remove ${FOCUS_RING}`}
                    onClick={() => setAttachments((prev) => prev.filter((x) => x.id !== a.id))}
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
              accept={IMAGE_ACCEPT}
              multiple
              style={{ display: "none" }}
              onChange={handlePickFiles}
            />
            <button
              type="button"
              className={`wl-chat-icon-btn ${FOCUS_RING}`}
              onClick={() => fileInputRef.current?.click()}
              aria-label="Joindre une image"
              title="Joindre une image (PNG, JPEG, WebP ou GIF)"
              disabled={sending}
            >
              <Paperclip size={16} />
            </button>
            <button
              type="button"
              className={`wl-chat-send ${FOCUS_RING}`}
              aria-label="Envoyer"
              onClick={() => void handleSend()}
              disabled={!canSend}
            >
              {sending ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
            </button>
          </div>
        </div>
      )}
    </>
  );
}

/**
 * Indicateur discret quand le dernier sondage a échoué : « Hors ligne —
 * nouvel essai… » + la vraie cause + « Réessayer ». `pill` (défaut) tient
 * sur une ligne en bas d'un fil ; `block` passe à la ligne pour les
 * colonnes étroites (liste des tickets).
 */
export function SyncIndicator({
  sync,
  onRetry,
  variant = "pill",
  className = "",
}: {
  sync: ChatSync;
  onRetry: () => void;
  variant?: "pill" | "block";
  className?: string;
}) {
  if (!sync.error) return null;
  const Icon = sync.offline ? WifiOff : AlertTriangle;
  const label = `${sync.offline ? "Hors ligne" : "Synchronisation interrompue"} — nouvel essai…`;
  if (variant === "block") {
    return (
      <div
        role="status"
        aria-live="polite"
        className={`rounded-md border border-warning/30 bg-warning-soft/70 px-2.5 py-1.5 text-[11px] leading-snug text-warning ${className}`}
      >
        <div className="flex items-center gap-1.5">
          <Icon size={12} className="shrink-0" />
          <span className="min-w-0 flex-1 font-medium">{label}</span>
          <button
            type="button"
            onClick={onRetry}
            className={`shrink-0 rounded-sm underline-offset-2 hover:underline ${FOCUS_RING}`}
          >
            Réessayer
          </button>
        </div>
        <div
          className="mt-0.5 line-clamp-2 text-foreground-muted [overflow-wrap:anywhere]"
          title={sync.error}
        >
          {sync.error}
        </div>
      </div>
    );
  }
  return (
    <div
      role="status"
      aria-live="polite"
      className={`flex max-w-full items-center gap-2 self-center rounded-full border border-warning/30 bg-warning-soft/70 px-3 py-1 text-[11.5px] text-warning ${className}`}
    >
      <Icon size={12} className="shrink-0" />
      <span className="shrink-0 whitespace-nowrap font-medium">{label}</span>
      {/* line-clamp plutôt que truncate : un texte nowrap imposerait sa
          largeur au fil (débordement horizontal de la page). */}
      <span
        className="min-w-0 line-clamp-1 text-foreground-muted [overflow-wrap:anywhere]"
        title={sync.error}
      >
        {sync.error}
      </span>
      <button
        type="button"
        onClick={onRetry}
        className={`shrink-0 rounded-sm underline-offset-2 hover:underline ${FOCUS_RING}`}
      >
        Réessayer
      </button>
    </div>
  );
}

// Bulle individuelle : un message « me » aligné à droite, un message staff
// à gauche avec avatar + badge violet « Staff » + nom Discord, un message
// système en bandeau central.
function ChatBubble({ message, meLabel }: { message: ChatMessage; meLabel: string }) {
  if (message.author === "system") {
    return (
      <motion.div
        className="wl-msg-system"
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <em>{message.content}</em>
      </motion.div>
    );
  }

  const time = formatTime(message.createdAt);
  const timeEl = (
    <span
      style={{
        color: "var(--color-foreground-muted)",
        fontSize: 11,
        fontFamily: "var(--font-mono)",
      }}
    >
      {time}
    </span>
  );

  if (message.author === "staff") {
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
            {timeEl}
          </div>
          {message.content && (
            <div className="wl-msg-bubble wl-msg-bubble-staff">{message.content}</div>
          )}
          <AttachmentLinks attachments={message.attachments} />
        </div>
      </motion.div>
    );
  }

  return (
    <motion.div
      className="wl-msg wl-msg-me"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
    >
      <div className="wl-msg-body">
        <div className="wl-msg-meta" style={{ justifyContent: "flex-end" }}>
          {timeEl}
          <span className="wl-msg-author">{meLabel}</span>
        </div>
        {message.content && (
          <div className="wl-msg-bubble wl-msg-bubble-me">{message.content}</div>
        )}
        <AttachmentLinks attachments={message.attachments} />
      </div>
    </motion.div>
  );
}

function AttachmentLinks({ attachments }: { attachments: { url: string }[] }) {
  if (attachments.length === 0) return null;
  return (
    <div className="wl-msg-attachments">
      {attachments.map((a, i) => (
        <a
          key={`${a.url}-${i}`}
          href={a.url}
          target="_blank"
          rel="noreferrer"
          className={`wl-attachment-chip ${FOCUS_RING}`}
        >
          <Paperclip size={12} />
          <span className="truncate">{shortenUrl(a.url)}</span>
        </a>
      ))}
    </div>
  );
}

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
