import { ArrowLeft, ArrowRight } from "lucide-react";
import { useWhitelistStore } from "../../../stores/whitelist-store";
import { useStepValidation } from "../../../lib/whitelist-validation";
import { TextField } from "../shared/TextField";
import { Textarea } from "../shared/Textarea";
import { Select } from "../shared/Select";
import { WizardLayout, FieldWrap } from "../WizardLayout";

type Props = {
  onPrev: () => void;
  onNext: () => void;
};

const VILLAGES = [
  "Konohagakure",
  "Sunagakure",
  "Kirigakure",
  "Kumogakure",
  "Iwagakure",
  "Amegakure",
  "Déserteur",
] as const;

export function Step2RP({ onPrev, onNext }: Props) {
  const draft = useWhitelistStore((s) => s.draft);
  const updateField = useWhitelistStore((s) => s.updateField);
  const { isValid } = useStepValidation(2, draft);

  return (
    <WizardLayout
      motionKey="rp"
      step={2}
      badge={<div className="wl-step-badge">2</div>}
      title="Étape 2 — RP"
      subtitle="Roleplay · votre personnage"
      footer={
        <>
          <button type="button" className="wl-btn-ghost" onClick={onPrev}>
            <ArrowLeft size={16} /> Précédent
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
        <TextField
          label="Prénom du personnage"
          required
          placeholder="Entrez le prénom de votre personnage"
          value={draft.firstName}
          onChange={(v) => updateField("firstName", v)}
        />
      </FieldWrap>
      <FieldWrap>
        <TextField
          label="Nom du personnage"
          required
          placeholder="Entrez le nom de votre personnage"
          value={draft.lastName}
          onChange={(v) => updateField("lastName", v)}
        />
      </FieldWrap>
      <FieldWrap>
        <div className="wl-field">
          <label className="wl-field-label">
            Village<span className="wl-required">*</span>
          </label>
          <Select
            value={draft.village}
            placeholder="Sélectionnez un village"
            options={VILLAGES}
            onChange={(v) => updateField("village", v)}
          />
        </div>
      </FieldWrap>
      <FieldWrap>
        <TextField
          label="Support visuel (optionnel)"
          placeholder="URL YouTube ou Canva"
          value={draft.support}
          onChange={(v) => updateField("support", v)}
        />
      </FieldWrap>
      <FieldWrap>
        <Textarea
          label="Histoire du personnage"
          required
          min={150}
          rows={5}
          placeholder="Origine, passé, événements marquants…"
          value={draft.history}
          onChange={(v) => updateField("history", v)}
          helper="Minimum 150 caractères"
        />
      </FieldWrap>
      <FieldWrap>
        <Textarea
          label="Apparence et personnalité"
          required
          min={150}
          rows={5}
          placeholder="Traits physiques, caractère…"
          value={draft.appearance}
          onChange={(v) => updateField("appearance", v)}
          helper="Minimum 150 caractères"
        />
      </FieldWrap>
      <FieldWrap>
        <Textarea
          label="Objectifs du personnage"
          required
          min={400}
          rows={6}
          placeholder="Buts à court, moyen et long terme. Motivations…"
          value={draft.objectives}
          onChange={(v) => updateField("objectives", v)}
          helper="Minimum 400 caractères"
        />
      </FieldWrap>
    </WizardLayout>
  );
}
