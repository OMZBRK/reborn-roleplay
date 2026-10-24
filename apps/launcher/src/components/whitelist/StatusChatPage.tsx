import { useEffect, useMemo, useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  AlertTriangle,
  CalendarClock,
  CheckCircle2,
  Clock,
  FileText,
  Loader2,
  MessageSquare,
  Mic,
  Trash2,
} from "lucide-react";
import { useWhitelistStore } from "../../stores/whitelist-store";
import { formatDateFr } from "../../lib/whitelist-validation";
import { formatIpcError } from "../../lib/errors";
import {
  bookOralSlot,
  cancelOralSlot,
  fetchOralSlots,
  fetchWhitelistMe,
  fetchWhitelistMessages,
  postWhitelistMessage,
  reclaimWhitelist,
  type OralSlot,
  type OralSlotsResponse,
  type WhitelistAppStatus,
  type WhitelistMeResponse,
  type WhitelistMessage,
} from "../../lib/content";
import {
  ChatThread,
  FOCUS_RING,
  SyncIndicator,
  useChatFeed,
  type ChatMessage,
} from "../chat/ChatThread";
import { RecapField } from "./RecapField";

const RECLAIM_AFTER_MS = 4 * 60 * 60 * 1000;
const STATUS_POLL_MS = 30_000;

type StatusData = { slots: OralSlotsResponse; me: WhitelistMeResponse };

/** Avancement de la candidature : statuts HRP/RP + créneaux du test oral. */
async function loadStatus(): Promise<StatusData> {
  const [slots, me] = await Promise.all([fetchOralSlots(), fetchWhitelistMe()]);
  return { slots, me };
}

/**
 * Hook qui ramène une struct prête à render pour le bandeau
 * d'assignation : phrase "Pris par @X depuis Yh" + flag canReclaim
 * (vrai si l'assignation a + de 4h). Rafraîchit chaque minute pour que
 * le compteur reste vivant sans qu'on ait à forcer un re-render.
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

type Tab = "chat" | "application" | "oral";

type Props = {
  onWithdraw?: () => void;
  withdrawing?: boolean;
};

function toChatMessage(m: WhitelistMessage): ChatMessage {
  return {
    id: m.id,
    author: m.authorType === "USER" ? "me" : m.authorType === "STAFF" ? "staff" : "system",
    authorName: m.authorName,
    content: m.content,
    attachments: m.attachments,
    createdAt: m.createdAt,
  };
}

// Page Statut + Chat (state 7).
//
// Le chat affiche les messages réellement persistés côté API (POST
// /v1/whitelist/me/messages côté user, /v1/staff/whitelist/:id/messages
// côté bot après messageCreate). Sondage toutes les 5 s en attendant SSE
// (components/chat/ChatThread : pause pendant une partie, coupures
// signalées dans le fil). Les pièces jointes (images) sont uploadées dès
// la sélection via POST /v1/upload.
//
// Onglets :
//  - "Chat" : conversation avec le staff
//  - "Test oral" : réservation du créneau (une fois l'HRP validé)
//  - "Ma candidature" : récap des champs HRP + RP soumis (read-only)
export function StatusChatPage({ onWithdraw, withdrawing = false }: Props) {
  const draft = useWhitelistStore((s) => s.draft);
  const applicationId = useWhitelistStore((s) => s.applicationId);
  const assigneeName = useWhitelistStore((s) => s.assigneeName);
  const assignedAt = useWhitelistStore((s) => s.assignedAt);
  const hydrateFromApplication = useWhitelistStore((s) => s.hydrateFromApplication);
  const [tab, setTab] = useState<Tab>("chat");
  const [reclaiming, setReclaiming] = useState(false);
  const [reclaimMsg, setReclaimMsg] = useState<{ ok: boolean; text: string } | null>(null);

  // Chat : sondé tant que l'onglet est affiché (au retour sur l'onglet, un
  // tour part tout de suite). Sans candidature côté serveur, rien à charger.
  const feed = useChatFeed(() => fetchWhitelistMessages(), {
    enabled: !!applicationId && tab === "chat",
  });
  const messages = useMemo(() => (feed.data ?? []).map(toChatMessage), [feed.data]);

  async function handleSend(content: string, attachmentUrls?: string[]) {
    const created = await postWhitelistMessage(content, attachmentUrls);
    feed.mutate((prev) => {
      const list = prev ?? [];
      return list.some((m) => m.id === created.id) ? list : [...list, created];
    });
    feed.refresh();
  }

  // L5 — avancement (statuts HRP/RP + créneaux du test oral), sondé toutes
  // les 30 s : le staff valide une étape ou ouvre des créneaux sans que la
  // page ait besoin d'être rouverte, et un échec se répare tout seul.
  const status = useChatFeed(loadStatus, {
    intervalMs: STATUS_POLL_MS,
    context: "Avancement de ta candidature non actualisé",
  });
  const oral = status.data?.slots ?? null;
  const hrpStatus: WhitelistAppStatus | null = status.data?.me.application?.hrpStatus ?? null;
  const rpStatus: WhitelistAppStatus | null = status.data?.me.application?.rpStatus ?? null;
  const [oralBusy, setOralBusy] = useState<string | null>(null);
  const [oralErr, setOralErr] = useState<string | null>(null);
  const autoTabDone = useRef(false);

  // Après une demande de reprise uniquement, la réponse suivante
  // resynchronise aussi le store (assignation, décision) — pas à chaque
  // tour, pour ne pas écraser l'écran choisi depuis le menu dev.
  const syncStoreRef = useRef(false);
  useEffect(() => {
    const app = status.data?.me.application;
    if (syncStoreRef.current && app) {
      syncStoreRef.current = false;
      hydrateFromApplication(app);
    }
  }, [status.data, hydrateFromApplication]);

  const mineSlot = oral?.mine ?? null;
  const openSlots = oral?.open ?? [];
  const hrpDone = hrpStatus === "APPROVED";
  const rpDone = rpStatus === "APPROVED";
  // Phase orale : l'HRP doit d'abord être VALIDÉ par le staff. L'entretien
  // oral est l'étape qui valide le RP (règlement, micro, cohérence/connaissance
  // de la candidature). Tant que le RP n'est pas accepté, la page reste ouverte.
  const oralPhase = hrpDone && !rpDone;

  // Bascule automatiquement sur l'onglet Test oral quand la phase s'ouvre (1x).
  useEffect(() => {
    if (oralPhase && !autoTabDone.current) {
      autoTabDone.current = true;
      setTab("oral");
    }
  }, [oralPhase]);

  async function oralAct(
    id: string,
    fn: (id: string) => Promise<unknown>,
    failure: string,
  ) {
    setOralBusy(id);
    setOralErr(null);
    try {
      await fn(id);
    } catch (e) {
      setOralErr(formatIpcError(e, failure));
      setOralBusy(null);
      return;
    }
    // Action réussie : on recharge l'avancement avant de rendre la main,
    // pour que la carte réservée / annulée bascule sans clignoter.
    try {
      const fresh = await loadStatus();
      status.mutate(() => fresh);
    } catch {
      status.refresh(); // l'échec s'affichera dans l'indicateur
    } finally {
      setOralBusy(null);
    }
  }

  // Calcul du délai pour le bouton "Demander une reprise".
  const assignmentInfo = useAssignmentInfo(assigneeName, assignedAt);

  async function handleReclaim() {
    setReclaiming(true);
    setReclaimMsg(null);
    try {
      await reclaimWhitelist();
      setReclaimMsg({
        ok: true,
        text: "Reprise demandée — un autre membre du staff peut maintenant prendre ta candidature.",
      });
      syncStoreRef.current = true;
      status.refresh();
    } catch (err) {
      setReclaimMsg({ ok: false, text: formatIpcError(err, "Impossible de demander une reprise") });
    } finally {
      setReclaiming(false);
    }
  }

  function openTab(next: Tab) {
    setTab(next);
    // Les créneaux bougent (le staff en ouvre, d'autres candidats en
    // prennent) : on les recharge à chaque ouverture de l'onglet.
    if (next === "oral") status.refresh();
  }

  // Sur l'onglet Chat, si le fil signale déjà la coupure, inutile de la
  // répéter dans l'en-tête.
  const showStatusSync = !!status.sync.error && !(tab === "chat" && feed.sync.error);

  // Le fil (5 s) voit le retour du réseau avant l'avancement (30 s) : on
  // relance alors l'avancement tout de suite s'il était en échec.
  const chatError = feed.sync.error;
  const statusError = status.sync.error;
  const refreshStatus = status.refresh;
  useEffect(() => {
    if (!chatError && statusError) refreshStatus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chatError]);

  return (
    <div className="wl-status-page">
      <div className="wl-status-main">
        <div className="wl-status-header">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h1 className="wl-status-title">
                {tab === "chat"
                  ? "Chat"
                  : tab === "oral"
                    ? mineSlot
                      ? "Entretien programmé"
                      : "Choisir un créneau"
                    : "Ma candidature"}
              </h1>
              <p className="wl-status-sub">
                {tab === "chat"
                  ? "Pour toute question, le chat ci-dessous vous met en relation avec le staff."
                  : tab === "oral"
                    ? mineSlot
                      ? "La date et l'heure de votre entretien oral sont indiquées ci-dessous."
                      : "Sélectionnez un créneau pour votre entretien oral avec le staff."
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
          )}
          {reclaimMsg && (
            <div
              role={reclaimMsg.ok ? "status" : "alert"}
              className="mt-2 text-xs"
              style={{
                color: reclaimMsg.ok ? "var(--color-foreground-subtle)" : "var(--color-danger)",
              }}
            >
              {reclaimMsg.text}
            </div>
          )}
          {showStatusSync && (
            <SyncIndicator
              variant="block"
              className="mt-3"
              sync={status.sync}
              onRetry={status.refresh}
            />
          )}
        </div>

        <div className="wl-status-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={tab === "chat"}
            className={`wl-tab${tab === "chat" ? " wl-tab-active wl-tab-warning" : ""} ${FOCUS_RING}`}
            onClick={() => openTab("chat")}
          >
            {tab === "chat" ? (
              <span className="wl-tab-pulse" />
            ) : (
              <MessageSquare size={14} />
            )}
            <span>
              {tab === "chat"
                ? oralPhase
                  ? "HRP validé · prochaine étape : test oral (RP)"
                  : rpDone
                    ? "Candidature acceptée"
                    : "En attente de révision · prochaine étape : HRP"
                : "Chat"}
            </span>
          </button>
          {oralPhase && (
            <button
              type="button"
              role="tab"
              aria-selected={tab === "oral"}
              className={`wl-tab${tab === "oral" ? " wl-tab-active wl-tab-oral" : ""} ${FOCUS_RING}`}
              onClick={() => openTab("oral")}
            >
              <Mic size={14} /> Test oral
            </button>
          )}
          <button
            type="button"
            role="tab"
            aria-selected={tab === "application"}
            className={`wl-tab${tab === "application" ? " wl-tab-active wl-tab-warning" : ""} ${FOCUS_RING}`}
            onClick={() => openTab("application")}
          >
            <FileText size={14} /> Ma candidature
          </button>
        </div>

        {tab === "chat" ? (
          <ChatThread
            messages={messages}
            loading={feed.loading}
            sync={feed.sync}
            onRetry={feed.refresh}
            onSend={handleSend}
            meLabel="Vous"
            placeholder="Envoyez un message (Ctrl+Entrée pour envoyer)"
            intro={
              <>
                Votre candidature a été soumise avec succès. Un membre du staff va
                l'examiner prochainement.
              </>
            }
          />
        ) : tab === "oral" ? (
          <div className="wl-status-scroll">
            <OralFullView
              mine={mineSlot}
              openSlots={openSlots}
              hrp={hrpStatus}
              rp={rpStatus}
              busy={oralBusy}
              err={oralErr}
              onBook={(id) => oralAct(id, bookOralSlot, "Réservation du créneau impossible")}
              onCancel={(id) => oralAct(id, cancelOralSlot, "Changement de créneau impossible")}
            />
          </div>
        ) : (
          <div className="wl-status-scroll">
            <ApplicationPanel
              draft={draft}
              onWithdraw={onWithdraw}
              withdrawing={withdrawing}
            />
          </div>
        )}
      </div>
    </div>
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
            Retirer ta candidature te ramène à l'écran d'accueil. Tu pourras
            en soumettre une nouvelle. Action irréversible — tes échanges
            avec le staff seront perdus.
          </p>
          <div className="mt-3">
            <button
              type="button"
              className={`wl-btn-mini-ghost ${FOCUS_RING}`}
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
// Vue plein écran du test oral : soit la sélection d'un créneau (grille type
// maquette « Choisir un Créneau »), soit l'entretien programmé (« Entretien
// Programmé »). Les statuts HRP/RP sont rappelés en tête.
function OralFullView({
  mine,
  openSlots,
  hrp,
  rp,
  busy,
  err,
  onBook,
  onCancel,
}: {
  mine: OralSlot | null;
  openSlots: OralSlot[];
  hrp: WhitelistAppStatus | null;
  rp: WhitelistAppStatus | null;
  busy: string | null;
  err: string | null;
  onBook: (id: string) => void;
  onCancel: (id: string) => void;
}) {
  return (
    <motion.div
      className="wl-oral-page"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
    >
      <div className="wl-oral-statusbar">
        <StatusPill label="HRP" status={hrp} />
        <StatusPill label="RP" status={rp} />
      </div>

      {err && (
        <div className="wl-oral-err" role="alert">
          {err}
        </div>
      )}

      {mine ? (
        <BookedView mine={mine} busy={busy} onCancel={onCancel} />
      ) : (
        <ChooseView openSlots={openSlots} busy={busy} onBook={onBook} />
      )}
    </motion.div>
  );
}

function ChooseView({
  openSlots,
  busy,
  onBook,
}: {
  openSlots: OralSlot[];
  busy: string | null;
  onBook: (id: string) => void;
}) {
  const sorted = [...openSlots].sort(
    (a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime(),
  );
  return (
    <>
      <div className="wl-oral-section-head">
        <h3 className="wl-oral-section-title">Choisissez votre créneau d'entretien</h3>
        <span className="wl-oral-count">
          {sorted.length} créneau{sorted.length > 1 ? "x" : ""} disponible
          {sorted.length > 1 ? "s" : ""}
        </span>
      </div>

      <p className="wl-oral-note">
        Ton HRP est validé. L'entretien oral est la dernière étape : le staff
        vérifie ta connaissance du règlement, ton micro et la cohérence de ta
        candidature RP. À l'issue de l'entretien, ta partie RP est validée.
      </p>

      {sorted.length === 0 ? (
        <div className="wl-oral-note">
          Aucun créneau ouvert pour l'instant. Le staff en ouvrira bientôt —
          reviens vérifier ici.
        </div>
      ) : (
        <div className="wl-oral-grid">
          {sorted.map((s) => (
            <button
              key={s.id}
              type="button"
              className={`wl-oral-cardslot ${FOCUS_RING}`}
              disabled={busy === s.id}
              onClick={() => onBook(s.id)}
            >
              <div className="wl-oral-cardslot-head">
                <CalendarClock size={17} />
                <div>
                  <div className="wl-oral-cardslot-day">{formatSlotDay(s.startAt)}</div>
                  <div className="wl-oral-cardslot-time">
                    {formatSlotRange(s.startAt, s.durationMin)}
                  </div>
                </div>
              </div>
              <div className="wl-oral-cardslot-meta">
                <Clock size={13} /> {s.durationMin} minutes
              </div>
              {busy === s.id && (
                <div className="wl-oral-cardslot-meta">
                  <Loader2 size={13} className="animate-spin" /> Réservation…
                </div>
              )}
            </button>
          ))}
        </div>
      )}
    </>
  );
}

function BookedView({
  mine,
  busy,
  onCancel,
}: {
  mine: OralSlot;
  busy: string | null;
  onCancel: (id: string) => void;
}) {
  const done = mine.status === "DONE";
  return (
    <>
      <div className="wl-oral-booked-card">
        <Mic className="wl-oral-booked-illu" size={96} strokeWidth={1} />
        <div className="wl-oral-booked-body">
          <div className="wl-oral-booked-day">{formatSlotDay(mine.startAt)}</div>
          <div className="wl-oral-booked-times">
            Début de l'entretien à <b>{formatSlotTime(mine.startAt)}</b>
            <br />
            Fin <b>{formatSlotEnd(mine.startAt, mine.durationMin)}</b>
          </div>
        </div>
        <CalendarClock className="wl-oral-booked-cal" size={26} />
      </div>

      {done ? (
        <div className="wl-oral-note wl-oral-ok">
          <CheckCircle2 size={15} /> Entretien passé — en attente de la validation
          RP par le staff.
        </div>
      ) : (
        <div className="wl-oral-warn">
          <AlertTriangle size={16} className="shrink-0" />
          <span>
            En cas d'empêchement, change ton créneau à l'avance. Toute absence non
            signalée peut entraîner un passage en file d'attente.
          </span>
        </div>
      )}

      {!done && (
        <div className="wl-oral-booked-actions">
          <button
            type="button"
            className={`wl-oral-btn-secondary ${FOCUS_RING}`}
            disabled={busy === mine.id}
            onClick={() => onCancel(mine.id)}
          >
            {busy === mine.id ? (
              <Loader2 size={14} className="animate-spin" />
            ) : null}
            Changer de créneau
          </button>
        </div>
      )}
    </>
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

function formatSlotDay(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const s = d.toLocaleDateString("fr-FR", {
    weekday: "long",
    day: "2-digit",
    month: "long",
  });
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function formatSlotTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
}

function formatSlotEnd(iso: string, durationMin: number): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return new Date(d.getTime() + durationMin * 60_000).toLocaleTimeString("fr-FR", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatSlotRange(iso: string, durationMin: number): string {
  return `${formatSlotTime(iso)} – ${formatSlotEnd(iso, durationMin)}`;
}
