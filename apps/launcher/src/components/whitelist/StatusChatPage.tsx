import { useCallback, useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  AlertTriangle,
  CalendarClock,
  CheckCircle2,
  FileText,
  Loader2,
  MessageSquare,
  Mic,
  Paperclip,
  Send,
  Trash2,
  X,
} from "lucide-react";
import { useWhitelistStore } from "../../stores/whitelist-store";
import { formatDateFr } from "../../lib/whitelist-validation";
import {
  bookOralSlot,
  cancelOralSlot,
  fetchOralSlots,
  fetchWhitelistMe,
  fetchWhitelistMessages,
  postWhitelistMessage,
  reclaimWhitelist,
  uploadAttachment,
  type OralSlot,
  type OralSlotsResponse,
  type WhitelistAppStatus,
  type WhitelistMessage,
} from "../../lib/content";
import { RecapField } from "./RecapField";

const RECLAIM_AFTER_MS = 4 * 60 * 60 * 1000;

/**
 * Hook qui ramene une struct prete a render pour le bandeau
 * d'assignation : phrase "Pris par @X depuis Yh" + flag canReclaim
 * (vrai si l'assignation a + de 4h). Rafraichit chaque minute pour que
 * le compteur reste vivant sans qu'on ait a forcer un re-render.
 */
function useAssignmentInfo(
  assigneeName: string | null,
  assignedAt: string | null,
): { text: string; canReclaim: boolean } | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!assignedAt) return;
    const t = window.setInterval(() => setNow(Date.now()), 60_000);
    return () => window.clearInterval(t);
  }, [assignedAt]);
  if (!assigneeName || !assignedAt) return null;
  const elapsed = now - new Date(assignedAt).getTime();
  const minutes = Math.floor(elapsed / 60_000);
  const hours = Math.floor(minutes / 60);
  const since =
    hours >= 1
      ? `${hours}h${minutes % 60 > 0 ? ` ${minutes % 60}min` : ""}`
      : `${Math.max(minutes, 1)} min`;
  return {
    text: `Pris en charge par @${assigneeName} depuis ${since}.`,
    canReclaim: elapsed >= RECLAIM_AFTER_MS,
  };
}

type Tab = "chat" | "application";

type Props = {
  onWithdraw?: () => void;
  withdrawing?: boolean;
};

type LocalAttachment = {
  id: string;
  name: string;
  size: number;
  // URL publique une fois l'upload terminé (POST /v1/upload via le backend
  // Rust). Tant que `uploading` est vrai, l'envoi du message est bloqué.
  url?: string;
  uploading?: boolean;
};

const POLL_INTERVAL_MS = 5000;

// Page Statut + Chat (state 7).
//
// Le chat affiche les messages réellement persistés côté API (POST
// /v1/whitelist/me/messages côté user, /v1/staff/whitelist/:id/messages
// côté bot après messageCreate). Polling toutes les 5s en attendant SSE.
//
// Onglets :
//  - "Chat" : conversation avec le staff
//  - "Ma candidature" : récap des champs HRP + RP soumis (read-only)
//
// Limitation actuelle : les pièces jointes ne sont pas uploadées (pas
// d'endpoint d'upload côté API). On les affiche localement dans le draft
// mais elles ne sont pas envoyées avec le message tant que l'URL n'est
// pas resolvable. À implémenter en phase suivante.
export function StatusChatPage({ onWithdraw, withdrawing = false }: Props) {
  const draft = useWhitelistStore((s) => s.draft);
  const applicationId = useWhitelistStore((s) => s.applicationId);
  const assigneeName = useWhitelistStore((s) => s.assigneeName);
  const assignedAt = useWhitelistStore((s) => s.assignedAt);
  const hydrateFromServer = useWhitelistStore((s) => s.hydrateFromServer);
  const [tab, setTab] = useState<Tab>("chat");
  const [draftMsg, setDraftMsg] = useState("");
  const [attachments, setAttachments] = useState<LocalAttachment[]>([]);
  const [messages, setMessages] = useState<WhitelistMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reclaiming, setReclaiming] = useState(false);
  const [reclaimMsg, setReclaimMsg] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Calcul du delai pour le bouton "Demander une reprise".
  const assignmentInfo = useAssignmentInfo(assigneeName, assignedAt);

  async function handleReclaim() {
    setReclaiming(true);
    setReclaimMsg(null);
    try {
      await reclaimWhitelist();
      const res = await fetchWhitelistMe();
      if (res.application) {
        hydrateFromServer({
          applicationId: res.application.id,
          status: res.application.status === "PENDING"
            ? "pending"
            : res.application.status === "APPROVED"
              ? "accepted"
              : res.application.status === "REJECTED"
                ? "rejected"
                : "pending",
          reviewNotes: res.application.reviewNotes,
          draft: {
            dob: res.application.dob,
            motivation: res.application.motivation,
            experience: res.application.experience,
            availability: res.application.availability,
            firstName: res.application.firstName,
            lastName: res.application.lastName,
            village: res.application.village,
            support: res.application.support ?? "",
            history: res.application.history,
            appearance: res.application.appearance,
            objectives: res.application.objectives,
          },
          assigneeName: res.application.assignee?.username ?? null,
          assignedAt: res.application.assignedAt,
        });
      }
      setReclaimMsg("Reprise demandee — un autre staff peut reprendre.");
    } catch (err) {
      const msg = typeof err === "string" ? err : (err as { message?: string }).message ?? "Echec";
      setReclaimMsg(msg);
    } finally {
      setReclaiming(false);
    }
  }

  // Polling : on fetch au mount + toutes les 5s tant que l'onglet est ouvert.
  // On n'autorise le poll que si on a un applicationId — sinon ce serait
  // un appel inutile (le serveur retournerait toujours []).
  useEffect(() => {
    if (!applicationId) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    let timer: number | undefined;

    async function poll() {
      try {
        const list = await fetchWhitelistMessages();
        if (!cancelled) {
          setMessages(list);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) {
          // En dev local sans API, on log mais on n'affiche pas pour ne pas
          // polluer la UI à chaque tick.
          console.warn("[whitelist-chat] fetch failed:", err);
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
  }, [applicationId]);

  async function handlePickFiles(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    e.target.value = "";
    if (files.length === 0) return;

    // L'API n'accepte que des images. On filtre + signale le reste.
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
        // Échec : on retire la chip et on affiche l'erreur.
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
    const trimmed = draftMsg.trim();
    // Contenu textuel requis ; on n'envoie pas tant qu'une pièce jointe est
    // encore en cours d'upload (son URL ne serait pas prête).
    if (!trimmed || attachments.some((a) => a.uploading)) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      const urls = attachments
        .map((a) => a.url)
        .filter((u): u is string => !!u);
      const created = await postWhitelistMessage(trimmed, urls.length > 0 ? urls : undefined);
      setMessages((prev) => [...prev, created]);
      setDraftMsg("");
      setAttachments([]);
    } catch (err) {
      setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Envoi échoué");
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
    <div className="wl-status-page">
      <div className="wl-status-main">
        <div className="wl-status-header">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h1 className="wl-status-title">
                {tab === "chat" ? "Chat" : "Ma candidature"}
              </h1>
              <p className="wl-status-sub">
                {tab === "chat"
                  ? "Pour toute question, le chat ci-dessous vous met en relation avec le staff."
                  : "Récapitulatif de la candidature soumise — visible par le staff côté Discord."}
              </p>
            </div>
          </div>

          {assignmentInfo && (
            <div
              className="mt-3 flex items-center justify-between gap-3 rounded-[10px] border px-3 py-2 text-xs"
              style={{
                background: assignmentInfo.canReclaim
                  ? "var(--color-warning-soft)"
                  : "var(--color-accent-soft)",
                borderColor: assignmentInfo.canReclaim
                  ? "rgba(245, 158, 11, 0.4)"
                  : "rgba(160, 24, 43, 0.4)",
                color: assignmentInfo.canReclaim
                  ? "var(--color-warning)"
                  : "var(--color-accent)",
              }}
            >
              <span>{assignmentInfo.text}</span>
              {assignmentInfo.canReclaim && (
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
          )}
          {reclaimMsg && (
            <div className="mt-2 text-xs" style={{ color: "var(--color-foreground-subtle)" }}>
              {reclaimMsg}
            </div>
          )}
        </div>

        <div className="wl-status-tabs">
          <button
            type="button"
            className={`wl-tab${tab === "chat" ? " wl-tab-active wl-tab-warning" : ""}`}
            onClick={() => setTab("chat")}
          >
            {tab === "chat" ? (
              <span className="wl-tab-pulse" />
            ) : (
              <MessageSquare size={14} />
            )}
            <span>
              {tab === "chat"
                ? "En attente de révision · prochaine étape : HRP"
                : "Chat"}
            </span>
          </button>
          <button
            type="button"
            className={`wl-tab${tab === "application" ? " wl-tab-active wl-tab-warning" : ""}`}
            onClick={() => setTab("application")}
          >
            <FileText size={14} /> Ma candidature
          </button>
        </div>

        {tab === "chat" ? (
          <ChatPanel
            messages={messages}
            loading={loading}
            error={error}
            sending={sending}
            draftMsg={draftMsg}
            attachments={attachments}
            onDraftChange={setDraftMsg}
            onKeyDown={handleKeyDown}
            onSend={handleSend}
            onPickClick={() => fileInputRef.current?.click()}
            onRemoveAttachment={removeAttachment}
            fileInputRef={fileInputRef}
            onFiles={handlePickFiles}
          />
        ) : (
          <ApplicationPanel
            draft={draft}
            onWithdraw={onWithdraw}
            withdrawing={withdrawing}
          />
        )}
      </div>
    </div>
  );
}

function ChatPanel({
  messages,
  loading,
  error,
  sending,
  draftMsg,
  attachments,
  onDraftChange,
  onKeyDown,
  onSend,
  onPickClick,
  onRemoveAttachment,
  fileInputRef,
  onFiles,
}: {
  messages: WhitelistMessage[];
  loading: boolean;
  error: string | null;
  sending: boolean;
  draftMsg: string;
  attachments: LocalAttachment[];
  onDraftChange: (v: string) => void;
  onKeyDown: (e: React.KeyboardEvent<HTMLTextAreaElement>) => void;
  onSend: () => void;
  onPickClick: () => void;
  onRemoveAttachment: (id: string) => void;
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  onFiles: (e: React.ChangeEvent<HTMLInputElement>) => void;
}) {
  return (
    <>
      <div className="wl-chat">
        <motion.div
          className="wl-msg-system"
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
        >
          <em>
            Votre candidature a été soumise avec succès. Un membre du staff va
            l'examiner prochainement.
          </em>
        </motion.div>

        {loading && messages.length === 0 && (
          <div className="flex items-center justify-center gap-2 py-4 text-xs text-foreground-muted">
            <Loader2 size={14} className="animate-spin" />
            Chargement des messages…
          </div>
        )}

        {messages.map((m) => (
          <ChatBubble key={m.id} message={m} />
        ))}
      </div>

      {error && (
        <div className="mb-3 flex items-center gap-2 rounded-md border border-danger/40 bg-danger/10 px-3 py-2 text-xs text-danger">
          <AlertTriangle size={14} />
          {error}
        </div>
      )}

      <div className="wl-chat-input-wrap">
        <textarea
          className="wl-chat-input"
          placeholder="Envoyez un message (Ctrl+Entrée pour envoyer)"
          rows={1}
          value={draftMsg}
          onChange={(e) => onDraftChange(e.target.value)}
          onKeyDown={onKeyDown}
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
                  onClick={() => onRemoveAttachment(a.id)}
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
            onChange={onFiles}
          />
          <button
            type="button"
            className="wl-chat-icon-btn"
            onClick={onPickClick}
            aria-label="Joindre un fichier"
            disabled={sending}
          >
            <Paperclip size={16} />
          </button>
          <button
            type="button"
            className="wl-chat-send"
            aria-label="Envoyer"
            onClick={onSend}
            disabled={
              sending ||
              draftMsg.trim().length === 0 ||
              attachments.some((a) => a.uploading)
            }
          >
            {sending ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
          </button>
        </div>
      </div>
    </>
  );
}

// Bulle individuelle. Un message USER s'affiche aligné à droite avec
// l'auteur "Vous" ; un message STAFF aligné à gauche avec un avatar +
// badge violet "Staff" + nom Discord ; un message SYSTEM en bandeau central.
function ChatBubble({ message }: { message: WhitelistMessage }) {
  const time = formatTime(message.createdAt);

  if (message.authorType === "SYSTEM") {
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

  if (message.authorType === "STAFF") {
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
          <div className="wl-msg-bubble wl-msg-bubble-staff">{message.content}</div>
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

  // USER
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
          <span className="wl-msg-author">Vous</span>
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

function ApplicationPanel({
  draft,
  onWithdraw,
  withdrawing,
}: {
  draft: ReturnType<typeof useWhitelistStore.getState>["draft"];
  onWithdraw?: () => void;
  withdrawing?: boolean;
}) {
  const dobDisplay = formatDateFr(draft.dob) ?? draft.dob;
  return (
    <div className="wl-application-panel">
      <OralSection />

      <motion.div
        className="wl-recap-card"
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <div className="wl-recap-header">
          <div className="wl-step-badge">1</div>
          <h3 className="wl-recap-title">HRP (Hors Roleplay)</h3>
        </div>
        <div className="wl-recap-fields">
          <RecapField label="Date de naissance" value={dobDisplay} />
          <RecapField label="Disponibilité" value={draft.availability} />
          <RecapField
            label="Pourquoi voulez-vous rejoindre le serveur ?"
            value={draft.motivation}
            wide
          />
          <RecapField label="Expérience Rôle-play" value={draft.experience} wide />
        </div>
      </motion.div>

      <motion.div
        className="wl-recap-card"
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.05 }}
      >
        <div className="wl-recap-header">
          <div className="wl-step-badge">2</div>
          <h3 className="wl-recap-title">RP (Roleplay)</h3>
        </div>
        <div className="wl-recap-fields">
          <RecapField label="Prénom du personnage" value={draft.firstName} />
          <RecapField label="Nom du personnage" value={draft.lastName} />
          <RecapField label="Village" value={draft.village} />
          <RecapField label="Support visuel" value={draft.support} />
          <RecapField label="Histoire du personnage" value={draft.history} wide />
          <RecapField
            label="Apparence et personnalité"
            value={draft.appearance}
            wide
          />
          <RecapField label="Objectifs du personnage" value={draft.objectives} wide />
        </div>

      </motion.div>

      {onWithdraw && (
        <motion.div
          className="wl-recap-card"
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.1 }}
          style={{
            borderColor: "rgba(239, 68, 68, 0.25)",
          }}
        >
          <div className="wl-recap-header">
            <div
              className="wl-step-badge"
              style={{
                background: "rgba(239, 68, 68, 0.18)",
                color: "var(--color-danger)",
              }}
            >
              !
            </div>
            <h3 className="wl-recap-title">Zone sensible</h3>
          </div>
          <p
            className="text-xs leading-relaxed mt-2"
            style={{ color: "var(--color-foreground-subtle)" }}
          >
            Retirer ta candidature te ramene a l'ecran d'accueil. Tu pourras
            en soumettre une nouvelle. Action irreversible — tes echanges
            avec le staff seront perdus.
          </p>
          <div className="mt-3">
            <button
              type="button"
              className="wl-btn-mini-ghost"
              onClick={onWithdraw}
              disabled={withdrawing}
              style={{
                borderColor: "rgba(239, 68, 68, 0.4)",
                color: "var(--color-danger)",
              }}
            >
              {withdrawing ? (
                <Loader2 size={13} className="animate-spin" />
              ) : (
                <Trash2 size={13} />
              )}
              Retirer ma candidature
            </button>
          </div>
        </motion.div>
      )}
    </div>
  );
}

// ── L5 : réservation du test oral (côté candidat) ─────────────────
function OralSection() {
  const [data, setData] = useState<OralSlotsResponse | null>(null);
  const [hrp, setHrp] = useState<WhitelistAppStatus | null>(null);
  const [rp, setRp] = useState<WhitelistAppStatus | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [slots, me] = await Promise.all([fetchOralSlots(), fetchWhitelistMe()]);
      setData(slots);
      setHrp(me.application?.hrpStatus ?? null);
      setRp(me.application?.rpStatus ?? null);
    } catch {
      // silencieux (dev sans API)
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function act(id: string, fn: (id: string) => Promise<unknown>) {
    setBusy(id);
    setErr(null);
    try {
      await fn(id);
      await refresh();
    } catch (e) {
      setErr(typeof e === "string" ? e : (e as { message?: string }).message ?? "Échec");
    } finally {
      setBusy(null);
    }
  }

  const hrpDone = hrp === "APPROVED";
  const mine = data?.mine ?? null;
  const open = data?.open ?? [];

  return (
    <motion.div
      className="wl-recap-card wl-oral-card"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
    >
      <div className="wl-recap-header">
        <div className="wl-step-badge wl-oral-badge">
          <Mic size={14} />
        </div>
        <h3 className="wl-recap-title">Test oral</h3>
        <div className="wl-oral-pills">
          <StatusPill label="HRP" status={hrp} />
          <StatusPill label="RP" status={rp} />
        </div>
      </div>

      {err && <div className="wl-oral-err">{err}</div>}

      {hrpDone ? (
        <div className="wl-oral-note wl-oral-ok">
          <CheckCircle2 size={15} /> Ton test oral est validé. La partie RP est en
          cours de validation par le staff.
        </div>
      ) : mine ? (
        <div className="wl-oral-booked">
          <div className="wl-oral-booked-info">
            <CalendarClock size={17} />
            <div>
              <div className="wl-oral-booked-when">{formatSlotFull(mine.startAt)}</div>
              <div className="wl-oral-booked-sub">
                Créneau réservé · {mine.durationMin} min. Le staff te rejoindra à
                l'heure dite.
              </div>
            </div>
          </div>
          <button
            type="button"
            className="wl-btn-mini-ghost"
            disabled={busy === mine.id}
            onClick={() => act(mine.id, cancelOralSlot)}
          >
            {busy === mine.id ? (
              <Loader2 size={13} className="animate-spin" />
            ) : (
              <X size={13} />
            )}
            Annuler
          </button>
        </div>
      ) : open.length > 0 ? (
        <div className="wl-oral-slots">
          <p className="wl-oral-lead">Réserve ton test oral avec le staff :</p>
          {groupSlotsByDay(open).map(({ day, slots }) => (
            <div key={day} className="wl-oral-day">
              <div className="wl-oral-day-label">{day}</div>
              <div className="wl-oral-slot-row">
                {slots.map((s) => (
                  <button
                    key={s.id}
                    type="button"
                    className="wl-oral-slot-btn"
                    disabled={busy === s.id}
                    onClick={() => act(s.id, bookOralSlot)}
                  >
                    {busy === s.id && <Loader2 size={12} className="animate-spin" />}
                    {formatSlotTime(s.startAt)}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="wl-oral-note">
          Aucun créneau ouvert pour l'instant. Le staff en ouvrira bientôt —
          reviens vérifier ici.
        </div>
      )}
    </motion.div>
  );
}

function StatusPill({
  label,
  status,
}: {
  label: string;
  status: WhitelistAppStatus | null;
}) {
  const map: Record<string, { txt: string; cls: string }> = {
    PENDING: { txt: "En attente", cls: "is-pending" },
    APPROVED: { txt: "Validé", cls: "is-ok" },
    REJECTED: { txt: "Refusé", cls: "is-bad" },
    NEEDS_REVISION: { txt: "À revoir", cls: "is-warn" },
  };
  const s = status ? map[status] : { txt: "—", cls: "is-pending" };
  return (
    <span className={`wl-oral-pill ${s.cls}`}>
      <span className="wl-oral-pill-label">{label}</span> {s.txt}
    </span>
  );
}

function formatSlotFull(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("fr-FR", {
    weekday: "long",
    day: "2-digit",
    month: "long",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatSlotTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
}

function groupSlotsByDay(slots: OralSlot[]): { day: string; slots: OralSlot[] }[] {
  const sorted = [...slots].sort(
    (a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime(),
  );
  const out: { day: string; slots: OralSlot[] }[] = [];
  for (const s of sorted) {
    const day = new Date(s.startAt).toLocaleDateString("fr-FR", {
      weekday: "long",
      day: "2-digit",
      month: "long",
    });
    const last = out[out.length - 1];
    if (last && last.day === day) last.slots.push(s);
    else out.push({ day, slots: [s] });
  }
  return out;
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
