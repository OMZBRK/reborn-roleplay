import { useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  FileText,
  Loader2,
  MessageSquare,
  Paperclip,
  Send,
  Trash2,
  X,
} from "lucide-react";
import { useWhitelistStore } from "../../stores/whitelist-store";
import { formatDateFr } from "../../lib/whitelist-validation";
import { RecapField } from "./RecapField";

type Tab = "chat" | "application";

type Props = {
  onWithdraw?: () => void;
  withdrawing?: boolean;
};

type AttachmentDraft = {
  id: string;
  name: string;
  size: number;
};

type ChatMessage = {
  id: string;
  author: "me";
  content: string;
  attachments: AttachmentDraft[];
  sentAt: number;
};

// Page Statut + Chat (state 7).
//
// Conception du chat : seul le message système initial est affiché par défaut
// ("Votre candidature a été soumise, le staff va l'examiner"). Le staff répond
// depuis Discord (via le bridge bot ↔ API), pas depuis le launcher — donc
// aucun pré-remplissage de bulles staff côté UI. Les messages du joueur
// envoyés ici se répercuteront sur Discord via webhook (TODO côté API).
//
// Onglets :
//  - "Chat" : conversation avec le staff
//  - "Ma candidature" : récap des champs HRP + RP soumis (read-only)
//
// Pièces jointes : géréees localement comme drafts (objet AttachmentDraft).
// Quand l'envoi sera branché à l'API, on POST multipart avec ces fichiers.
//
// Bouton "Retirer ma candidature" : visible en haut, propose à l'utilisateur
// de supprimer sa candidature côté serveur (DELETE /whitelist/me) et de
// repartir d'un brouillon vide. Géré par le parent (Whitelist.tsx) qui
// appelle withdrawWhitelist + reset le store.
export function StatusChatPage({ onWithdraw, withdrawing = false }: Props) {
  const draft = useWhitelistStore((s) => s.draft);
  const [tab, setTab] = useState<Tab>("chat");
  const [draftMsg, setDraftMsg] = useState("");
  const [attachments, setAttachments] = useState<AttachmentDraft[]>([]);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);

  function handlePickFiles(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    if (files.length === 0) return;
    const next: AttachmentDraft[] = files.map((f) => ({
      id: `${Date.now()}-${f.name}-${Math.random().toString(36).slice(2, 8)}`,
      name: f.name,
      size: f.size,
    }));
    setAttachments((prev) => [...prev, ...next]);
    // Permet de re-sélectionner le même fichier consécutivement.
    e.target.value = "";
  }

  function removeAttachment(id: string) {
    setAttachments((prev) => prev.filter((a) => a.id !== id));
  }

  function handleSend() {
    const trimmed = draftMsg.trim();
    if (!trimmed && attachments.length === 0) return;
    // TODO: brancher sur POST /v1/whitelist/me/messages côté API quand le
    // canal staff↔candidat sera implémenté (équivalent du flow Tickets).
    const msg: ChatMessage = {
      id: `${Date.now()}`,
      author: "me",
      content: trimmed,
      attachments,
      sentAt: Date.now(),
    };
    setMessages((prev) => [...prev, msg]);
    setDraftMsg("");
    setAttachments([]);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
      e.preventDefault();
      handleSend();
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
            {onWithdraw && (
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
            )}
          </div>
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
          <ApplicationPanel draft={draft} />
        )}
      </div>
    </div>
  );
}

function ChatPanel({
  messages,
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
  messages: ChatMessage[];
  draftMsg: string;
  attachments: AttachmentDraft[];
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

        {messages.map((m) => (
          <motion.div
            key={m.id}
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
                  {formatTime(m.sentAt)}
                </span>
                <span className="wl-msg-author">Vous</span>
              </div>
              {m.content && (
                <div className="wl-msg-bubble wl-msg-bubble-me">{m.content}</div>
              )}
              {m.attachments.length > 0 && (
                <div className="wl-msg-attachments">
                  {m.attachments.map((a) => (
                    <span key={a.id} className="wl-attachment-chip">
                      <Paperclip size={12} />
                      <span className="truncate">{a.name}</span>
                      <span className="wl-attachment-size">
                        {formatBytes(a.size)}
                      </span>
                    </span>
                  ))}
                </div>
              )}
            </div>
          </motion.div>
        ))}
      </div>

      <div className="wl-chat-input-wrap">
        <textarea
          className="wl-chat-input"
          placeholder="Envoyez un message (Ctrl+Entrée pour envoyer)"
          rows={1}
          value={draftMsg}
          onChange={(e) => onDraftChange(e.target.value)}
          onKeyDown={onKeyDown}
        />
        {attachments.length > 0 && (
          <div className="wl-chat-pending-attachments">
            {attachments.map((a) => (
              <span key={a.id} className="wl-attachment-chip">
                <Paperclip size={12} />
                <span className="truncate">{a.name}</span>
                <span className="wl-attachment-size">{formatBytes(a.size)}</span>
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
            multiple
            style={{ display: "none" }}
            onChange={onFiles}
          />
          <button
            type="button"
            className="wl-chat-icon-btn"
            onClick={onPickClick}
            aria-label="Joindre un fichier"
          >
            <Paperclip size={16} />
          </button>
          <button
            type="button"
            className="wl-chat-send"
            aria-label="Envoyer"
            onClick={onSend}
          >
            <Send size={16} />
          </button>
        </div>
      </div>
    </>
  );
}

function ApplicationPanel({
  draft,
}: {
  draft: ReturnType<typeof useWhitelistStore.getState>["draft"];
}) {
  const dobDisplay = formatDateFr(draft.dob) ?? draft.dob;
  return (
    <div className="wl-application-panel">
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
          <RecapField
            label="Pourquoi voulez-vous rejoindre le serveur ?"
            value={draft.motivation}
          />
          <RecapField label="Expérience Rôle-play" value={draft.experience} />
          <RecapField label="Disponibilité" value={draft.availability} />
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
          <RecapField label="Histoire du personnage" value={draft.history} />
          <RecapField
            label="Apparence et personnalité"
            value={draft.appearance}
          />
          <RecapField label="Objectifs du personnage" value={draft.objectives} />
        </div>
      </motion.div>
    </div>
  );
}

function formatBytes(n: number): string {
  if (n < 1024) return `${n} o`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} ko`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} Mo`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} Go`;
}

function formatTime(ts: number): string {
  const d = new Date(ts);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}
