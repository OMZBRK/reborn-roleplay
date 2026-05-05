import { motion } from "framer-motion";
import { ArrowLeft, Check, Loader2, Send } from "lucide-react";
import { useWhitelistStore } from "../../../stores/whitelist-store";
import { formatDateFr } from "../../../lib/whitelist-validation";
import { RecapField } from "../RecapField";
import { WizardLayout } from "../WizardLayout";

type Props = {
  onPrev: () => void;
  onSubmit: () => void;
  submitting?: boolean;
};

export function Step3Validation({ onPrev, onSubmit, submitting = false }: Props) {
  const draft = useWhitelistStore((s) => s.draft);
  // Format FR pour la date dans le récap (cohérent avec le bandeau du DateField).
  const dobDisplay = formatDateFr(draft.dob) ?? draft.dob;

  return (
    <WizardLayout
      motionKey="valid"
      step={3}
      badge={
        <div className="wl-step-badge wl-step-badge-check">
          <Check size={18} strokeWidth={3} />
        </div>
      }
      title="Validation"
      subtitle="Vérifiez vos réponses avant d'envoyer votre candidature"
      footer={
        <>
          <button
            type="button"
            className="wl-btn-ghost"
            onClick={onPrev}
            disabled={submitting}
          >
            <ArrowLeft size={16} /> Modifier
          </button>
          <button
            type="button"
            className="wl-btn-primary glow"
            onClick={onSubmit}
            disabled={submitting}
          >
            {submitting ? (
              <>
                <Loader2 size={16} className="animate-spin" /> Envoi…
              </>
            ) : (
              <>
                Envoyer la candidature <Send size={16} />
              </>
            )}
          </button>
        </>
      }
    >
      <motion.div
        className="wl-recap-card"
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.05 }}
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
        transition={{ duration: 0.4, delay: 0.12 }}
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
          <RecapField label="Apparence et personnalité" value={draft.appearance} />
          <RecapField label="Objectifs du personnage" value={draft.objectives} />
        </div>
      </motion.div>
    </WizardLayout>
  );
}
