import { AlertTriangle, ArrowRight } from "lucide-react";
import { useWhitelistStore } from "../../../stores/whitelist-store";
import { useStepValidation } from "../../../lib/whitelist-validation";
import { DateField } from "../shared/DateField";
import { Textarea } from "../shared/Textarea";
import { WizardLayout, FieldWrap } from "../WizardLayout";

type Props = {
  onCancel: () => void;
  onNext: () => void;
};

export function Step1HRP({ onCancel, onNext }: Props) {
  const draft = useWhitelistStore((s) => s.draft);
  const updateField = useWhitelistStore((s) => s.updateField);
  const { isValid } = useStepValidation(1, draft);

  return (
    <WizardLayout
      motionKey="hrp"
      step={1}
      badge={<div className="wl-step-badge">1</div>}
      title="Étape 1 — HRP"
      subtitle="Hors Roleplay · informations personnelles et motivation"
      headerRight={
        <span className="wl-callout wl-callout-warning wl-callout-pill">
          <AlertTriangle size={14} />
          Vous devez avoir au moins 16 ans
        </span>
      }
      footer={
        <>
          <button type="button" className="wl-btn-ghost" onClick={onCancel}>
            Annuler
          </button>
          <button
            type="button"
            className="wl-btn-primary glow"
            disabled={!isValid}
            onClick={() => isValid && onNext()}
          >
            Suivant <ArrowRight size={16} />
          </button>
        </>
      }
    >
      <FieldWrap>
        <DateField
          label="Date de naissance"
          required
          value={draft.dob}
          onChange={(v) => updateField("dob", v)}
        />
      </FieldWrap>
      <FieldWrap>
        <Textarea
          label="Pourquoi voulez-vous rejoindre le serveur ?"
          required
          min={150}
          rows={5}
          placeholder="Expliquez votre démarche, ce qui vous attire dans Reborn…"
          value={draft.motivation}
          onChange={(v) => updateField("motivation", v)}
          helper="Minimum 150 caractères"
        />
      </FieldWrap>
      <FieldWrap>
        <Textarea
          label="Expérience Rôle-play"
          required
          min={150}
          rows={5}
          placeholder="Vos précédents serveurs, formats, durée…"
          value={draft.experience}
          onChange={(v) => updateField("experience", v)}
          helper="Minimum 150 caractères"
        />
      </FieldWrap>
      <FieldWrap>
        <Textarea
          label="Disponibilité"
          required
          min={20}
          rows={3}
          placeholder="Jours / horaires de présence prévus"
          value={draft.availability}
          onChange={(v) => updateField("availability", v)}
          helper="Minimum 20 caractères"
        />
      </FieldWrap>
    </WizardLayout>
  );
}
