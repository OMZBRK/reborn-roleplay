import { X } from "lucide-react";
import { useWhitelistStore, EMPTY_DRAFT } from "../../stores/whitelist-store";
import type { WizardStage } from "../../routes/Whitelist";

// Toolbar dev caché : permet de switcher entre les écrans du module whitelist
// sans avoir à remplir le wizard. Visible uniquement en import.meta.env.DEV
// et activable via Ctrl+Shift+W. Inspiré du toolbar de l'artefact
// whitelist-app.jsx (équivalent du segment "État").

const STAGES: { id: WizardStage; label: string }[] = [
  { id: "intro", label: "Intro" },
  { id: "hrp", label: "HRP" },
  { id: "rp", label: "RP" },
  { id: "valid", label: "Valid" },
  { id: "submitted", label: "Envoyée" },
  { id: "chat", label: "Chat" },
  { id: "rejected", label: "Refusée" },
  { id: "accepted", label: "Acceptée" },
];

type Props = {
  stage: WizardStage;
  modalOpen: boolean;
  onStageChange: (s: WizardStage) => void;
  onModalToggle: (open: boolean) => void;
  onClose: () => void;
};

export function WhitelistDevToolbar({
  stage,
  modalOpen,
  onStageChange,
  onModalToggle,
  onClose,
}: Props) {
  const applyMockData = useWhitelistStore((s) => s.applyMockData);
  const setDraft = useWhitelistStore((s) => s.setDraft);

  return (
    <div className="wl-dev-toolbar">
      <span className="wl-dev-toolbar-label">DEV</span>
      <div className="wl-dev-seg">
        {STAGES.map((s) => (
          <button
            key={s.id}
            className={stage === s.id && !modalOpen ? "active" : ""}
            onClick={() => onStageChange(s.id)}
          >
            {s.label}
          </button>
        ))}
        {/* La modal "Avant de commencer" est un overlay sur l'intro, pas une
            étape — on l'expose comme bouton dédié pour debug. */}
        <button
          className={modalOpen ? "active" : ""}
          onClick={() => onModalToggle(!modalOpen)}
        >
          Modal
        </button>
      </div>
      <span className="wl-dev-toolbar-label">DATA</span>
      <div className="wl-dev-seg">
        <button onClick={() => applyMockData()}>Mock</button>
        <button onClick={() => setDraft(EMPTY_DRAFT)}>Vide</button>
      </div>
      <button
        type="button"
        className="wl-dev-toolbar-close"
        onClick={onClose}
        aria-label="Fermer le menu dev"
      >
        <X size={14} />
      </button>
    </div>
  );
}
