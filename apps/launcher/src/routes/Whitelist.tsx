import { useEffect, useState } from "react";
import { AnimatePresence } from "framer-motion";
import { useNavigate } from "react-router";
import {
  fetchWhitelistMe,
  submitWhitelist,
  resubmitWhitelist,
  withdrawWhitelist,
  type WhitelistApplication,
  type WhitelistAppStatus,
} from "../lib/content";
import {
  useWhitelistStore,
  type WhitelistDraft,
  type WhitelistStatus,
} from "../stores/whitelist-store";
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

// Mappe le statut serveur (PENDING/APPROVED/...) → status local (4 valeurs)
// utilisé par la sidebar et le mapping écran. NEEDS_REVISION retombe sur
// "rejected" : design v2 ne distingue pas les deux côté UI (l'utilisateur
// peut refaire dans les deux cas), seul le message diffère via reviewNotes.
function mapServerStatus(s: WhitelistAppStatus): WhitelistStatus {
  switch (s) {
    case "PENDING":
      return "pending";
    case "APPROVED":
      return "accepted";
    case "REJECTED":
    case "NEEDS_REVISION":
      return "rejected";
  }
}

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

function applicationToHydrate(app: WhitelistApplication) {
  const draft: WhitelistDraft = {
    dob: app.dob,
    motivation: app.motivation,
    experience: app.experience,
    availability: app.availability,
    firstName: app.firstName,
    lastName: app.lastName,
    village: app.village,
    support: app.support ?? "",
    history: app.history,
    appearance: app.appearance,
    objectives: app.objectives,
  };
  return {
    applicationId: app.id,
    status: mapServerStatus(app.status),
    reviewNotes: app.reviewNotes,
    draft,
  };
}

export function Whitelist() {
  const status = useWhitelistStore((s) => s.status);
  const draft = useWhitelistStore((s) => s.draft);
  const applicationId = useWhitelistStore((s) => s.applicationId);
  const reviewNotes = useWhitelistStore((s) => s.reviewNotes);
  const setStatus = useWhitelistStore((s) => s.setStatus);
  const reset = useWhitelistStore((s) => s.reset);
  const hydrateFromServer = useWhitelistStore((s) => s.hydrateFromServer);
  const navigate = useNavigate();

  const [stage, setStage] = useState<WizardStage>(() => stageFromStatus(status));
  const [modalOpen, setModalOpen] = useState(false);
  // Bloque les actions concurrentes (submit/withdraw) pendant un round-trip API.
  const [busy, setBusy] = useState(false);
  // Erreur API affichée dans une bannière sur l'écran courant.
  const [apiError, setApiError] = useState<string | null>(null);

  // Au mount : on hydrate depuis le serveur. Le draft local reste valide pour
  // un user qui n'a jamais soumis (status="draft"), sinon on overwrite avec
  // ce que le serveur connaît (source of truth).
  useEffect(() => {
    let cancelled = false;
    fetchWhitelistMe()
      .then((res) => {
        if (cancelled) return;
        if (res.application) {
          hydrateFromServer(applicationToHydrate(res.application));
        } else {
          hydrateFromServer(null);
        }
      })
      .catch((err) => {
        // L'API peut être down (dev local). On log mais on ne bloque pas le
        // user — il peut continuer à éditer son brouillon offline.
        console.warn("[whitelist] fetchWhitelistMe failed:", err);
      });
    return () => {
      cancelled = true;
    };
  }, [hydrateFromServer]);

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

  // Centralise la gestion d'erreur API : extrait un message lisible.
  function errMessage(err: unknown): string {
    if (typeof err === "string") return err;
    if (err && typeof err === "object" && "message" in err) {
      return String((err as { message?: unknown }).message ?? "Erreur API");
    }
    return "Erreur API inconnue";
  }

  async function handleSubmit() {
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
      hydrateFromServer(applicationToHydrate(result));
      setStage("submitted");
      // Après ~2.5s on bascule sur le chat (status="pending" côté server).
      window.setTimeout(() => {
        setStage("chat");
      }, 2500);
    } catch (err) {
      setApiError(errMessage(err));
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
    setApiError(null);
    setBusy(true);
    try {
      await withdrawWhitelist();
      reset();
      setStage("intro");
    } catch (err) {
      setApiError(errMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleRetry() {
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
      {apiError && (
        <div className="mx-9 mt-4 rounded-md border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
          {apiError}
        </div>
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
