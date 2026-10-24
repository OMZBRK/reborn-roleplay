import { useEffect, useRef, useState } from "react";
import { AnimatePresence } from "framer-motion";
import { useNavigate } from "react-router";
import {
  fetchWhitelistMe,
  submitWhitelist,
  resubmitWhitelist,
  withdrawWhitelist,
} from "../lib/content";
import { formatIpcError } from "../lib/errors";
import { useWhitelistStore, type WhitelistStatus } from "../stores/whitelist-store";
import { InlineError, SyncIndicator, useChatFeed } from "../components/chat/ChatThread";
import { PrerequisitesModal } from "../components/whitelist/PrerequisitesModal";
import { IntroStepsPage } from "../components/whitelist/IntroStepsPage";
import { Step1HRP } from "../components/whitelist/steps/Step1HRP";
import { Step2RP } from "../components/whitelist/steps/Step2RP";
import { Step3Validation } from "../components/whitelist/steps/Step3Validation";
import { SubmittedPage } from "../components/whitelist/SubmittedPage";
import { StatusChatPage } from "../components/whitelist/StatusChatPage";
import { RejectedPage } from "../components/whitelist/RejectedPage";
import { AcceptedPage } from "../components/whitelist/AcceptedPage";
import { WhitelistDevToolbar } from "../components/whitelist/WhitelistDevToolbar";

// Étapes du module whitelist :
// — intro     : "En Trois Étapes" (landing). "Let's go" ouvre la modal "Avant
//               de commencer" en surcouche (overlay au-dessus d'intro).
// — hrp/rp/valid : wizard 3 étapes
// — submitted : confirmation après envoi
// — chat      : statut + chat staff (status="pending")
// — rejected  : candidature refusée (status="rejected" / "needs_revision")
// — accepted  : candidature acceptée (status="accepted")
export type WizardStage =
  | "intro"
  | "hrp"
  | "rp"
  | "valid"
  | "submitted"
  | "chat"
  | "rejected"
  | "accepted";

// Mappe le statut → écran à afficher. Le wizard (intro/hrp/rp/valid) reste
// piloté par l'état local de la route ; ce mapping ne concerne que les
// écrans "post-soumission" et la landing par défaut.
function stageFromStatus(status: WhitelistStatus): WizardStage {
  switch (status) {
    case "pending":
      return "chat";
    case "accepted":
      return "accepted";
    case "rejected":
      return "rejected";
    case "draft":
    default:
      return "intro";
  }
}

export function Whitelist() {
  const status = useWhitelistStore((s) => s.status);
  const draft = useWhitelistStore((s) => s.draft);
  const applicationId = useWhitelistStore((s) => s.applicationId);
  const reviewNotes = useWhitelistStore((s) => s.reviewNotes);
  const setStatus = useWhitelistStore((s) => s.setStatus);
  const reset = useWhitelistStore((s) => s.reset);
  const hydrateFromApplication = useWhitelistStore((s) => s.hydrateFromApplication);
  const navigate = useNavigate();

  const [stage, setStage] = useState<WizardStage>(() => stageFromStatus(status));
  const [modalOpen, setModalOpen] = useState(false);
  // Bloque les actions concurrentes (submit/withdraw) pendant un round-trip API.
  const [busy, setBusy] = useState(false);
  // Erreur d'une action (envoi, retrait) affichée dans une bannière sur
  // l'écran courant.
  const [apiError, setApiError] = useState<string | null>(null);
  // Bascule différée « Candidature envoyée » → chat (annulée au démontage).
  const chatTimerRef = useRef<number | null>(null);
  useEffect(
    () => () => {
      if (chatTimerRef.current !== null) window.clearTimeout(chatTimerRef.current);
    },
    [],
  );

  // Au mount : on hydrate depuis le serveur. Le draft local reste valide pour
  // un user qui n'a jamais soumis (status="draft"), sinon on overwrite avec
  // ce que le serveur connaît (source of truth). Un échec ne bloque pas
  // (le brouillon reste éditable hors ligne) : il est signalé par un
  // indicateur discret et retenté toutes les 15 s jusqu'à la première
  // réussite.
  const [serverSynced, setServerSynced] = useState(false);
  // Incrémenté à chaque action locale (envoi, retrait) : une réponse partie
  // avant ne doit pas écraser son résultat.
  const actionGenRef = useRef(0);
  const server = useChatFeed(
    async () => {
      const gen = actionGenRef.current;
      const res = await fetchWhitelistMe();
      if (gen === actionGenRef.current) hydrateFromApplication(res.application);
      setServerSynced(true);
      return res;
    },
    {
      enabled: !serverSynced,
      intervalMs: 15_000,
      context: "État de ta candidature non synchronisé (ton brouillon reste modifiable)",
    },
  );

  // Quand le statut change (hydrate ou setStatus côté dev), on reflète sur le
  // stage. Le user peut être en train de remplir le wizard (stage hrp/rp/valid)
  // sans que le statut serveur soit "pending" — dans ce cas on garde le stage
  // courant. On ne force que pour les transitions "vers" un état serveur.
  useEffect(() => {
    if (status === "pending" || status === "accepted" || status === "rejected") {
      setStage(stageFromStatus(status));
    } else if (status === "draft") {
      // Si on revient à "draft" depuis pending/accepted/rejected (ex: withdraw),
      // on retombe sur intro. Mais si l'utilisateur est déjà sur un wizard step,
      // pas la peine de l'éjecter.
      if (
        stage === "submitted" ||
        stage === "chat" ||
        stage === "rejected" ||
        stage === "accepted"
      ) {
        setStage("intro");
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  // Dev menu : Ctrl+Shift+W pour le toggle (debug-only, gated import.meta.env.DEV).
  const [devOpen, setDevOpen] = useState(false);
  useEffect(() => {
    if (!import.meta.env.DEV) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.shiftKey && (e.key === "W" || e.key === "w")) {
        e.preventDefault();
        setDevOpen((v) => !v);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  async function handleSubmit() {
    if (busy) return;
    actionGenRef.current += 1;
    setApiError(null);
    setBusy(true);
    try {
      // Si une application existait (status="rejected" et l'utilisateur a
      // cliqué "Refaire une candidature"), on PATCH /whitelist/me. Sinon
      // POST /whitelist pour créer.
      const payload = {
        dob: draft.dob,
        motivation: draft.motivation,
        experience: draft.experience,
        availability: draft.availability,
        firstName: draft.firstName,
        lastName: draft.lastName,
        village: draft.village,
        support: draft.support === "" ? null : draft.support,
        history: draft.history,
        appearance: draft.appearance,
        objectives: draft.objectives,
      };
      const result = applicationId
        ? await resubmitWhitelist(payload)
        : await submitWhitelist(payload);
      hydrateFromApplication(result);
      setServerSynced(true);
      setStage("submitted");
      // Après ~2.5s on bascule sur le chat (status="pending" côté server).
      if (chatTimerRef.current !== null) window.clearTimeout(chatTimerRef.current);
      chatTimerRef.current = window.setTimeout(() => {
        chatTimerRef.current = null;
        setStage("chat");
      }, 2500);
    } catch (err) {
      setApiError(formatIpcError(err, "Impossible d'envoyer ta candidature"));
    } finally {
      setBusy(false);
    }
  }

  async function handleWithdraw() {
    if (
      !window.confirm(
        "Retirer ta candidature ? Ton brouillon sera vidé et tu pourras en soumettre une nouvelle.",
      )
    ) {
      return;
    }
    actionGenRef.current += 1;
    setApiError(null);
    setBusy(true);
    try {
      await withdrawWhitelist();
      reset();
      setStage("intro");
    } catch (err) {
      setApiError(formatIpcError(err, "Impossible de retirer ta candidature"));
    } finally {
      setBusy(false);
    }
  }

  function handleRetry() {
    // Refus → l'utilisateur peut refaire une candidature. Le draft sur
    // l'écran "rejected" est encore celui qui a été refusé (hydraté par
    // applicationToHydrate). On ne reset PAS pour qu'il puisse repartir du
    // contenu existant et corriger ; le PATCH gérera le resubmit.
    setStage("intro");
  }

  function renderStage() {
    switch (stage) {
      case "intro":
        return <IntroStepsPage onStart={() => setModalOpen(true)} />;
      case "hrp":
        return (
          <Step1HRP
            onCancel={() => setStage("intro")}
            onNext={() => setStage("rp")}
          />
        );
      case "rp":
        return (
          <Step2RP
            onPrev={() => setStage("hrp")}
            onNext={() => setStage("valid")}
          />
        );
      case "valid":
        return (
          <Step3Validation
            onPrev={() => setStage("rp")}
            onSubmit={handleSubmit}
            submitting={busy}
          />
        );
      case "submitted":
        return <SubmittedPage />;
      case "chat":
        return <StatusChatPage onWithdraw={handleWithdraw} withdrawing={busy} />;
      case "rejected":
        return (
          <RejectedPage
            onRetry={handleRetry}
            onWithdraw={handleWithdraw}
            withdrawing={busy}
            reason={reviewNotes ?? undefined}
          />
        );
      case "accepted":
        return (
          <AcceptedPage
            onCreate={() => navigate("/character")}
            staffNote={reviewNotes ?? undefined}
          />
        );
    }
  }

  // Les wizards (hrp/rp/valid) ont leur layout interne (max-width 980px,
  // centrage), il leur faut un wrapper avec padding adapté. Les autres
  // écrans gèrent leur propre layout — on les laisse fill le main.
  const isWizardScreen = stage === "hrp" || stage === "rp" || stage === "valid";
  const showModal = modalOpen && stage === "intro";

  return (
    <div className="relative flex h-full w-full flex-col">
      {/* Sur l'écran Chat, StatusChatPage signale déjà la coupure. */}
      {stage !== "chat" && (
        <SyncIndicator
          variant="block"
          className="mx-9 mt-4"
          sync={server.sync}
          onRetry={server.refresh}
        />
      )}
      {apiError && (
        <InlineError
          large
          className="mx-9 mt-4"
          message={apiError}
          onDismiss={() => setApiError(null)}
        />
      )}

      <div
        className={
          isWizardScreen
            ? "flex-1 overflow-y-auto px-9 py-7"
            : "flex min-h-0 flex-1 flex-col"
        }
      >
        <AnimatePresence mode="wait">{renderStage()}</AnimatePresence>
      </div>

      <AnimatePresence>
        {showModal && (
          <PrerequisitesModal
            onCancel={() => setModalOpen(false)}
            onStart={() => {
              setModalOpen(false);
              setStage("hrp");
            }}
          />
        )}
      </AnimatePresence>

      {import.meta.env.DEV && devOpen && (
        <WhitelistDevToolbar
          stage={stage}
          modalOpen={modalOpen}
          onStageChange={(s) => {
            setStage(s);
            setModalOpen(false);
            // Synchroniser le statut store avec l'écran sélectionné côté dev,
            // pour que la sidebar / les transitions reflètent l'état choisi.
            if (s === "chat" || s === "submitted") setStatus("pending");
            else if (s === "accepted") setStatus("accepted");
            else if (s === "rejected") setStatus("rejected");
            else setStatus("draft");
          }}
          onModalToggle={(open) => {
            if (open) setStage("intro");
            setModalOpen(open);
          }}
          onClose={() => setDevOpen(false)}
        />
      )}
    </div>
  );
}
