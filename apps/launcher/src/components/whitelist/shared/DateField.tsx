import { Calendar } from "lucide-react";
import { calcAge, formatDateFr } from "../../../lib/whitelist-validation";

type Props = {
  label: string;
  required?: boolean;
  value: string;
  onChange: (v: string) => void;
};

export function DateField({ label, required, value, onChange }: Props) {
  const age = calcAge(value);
  const display = formatDateFr(value);
  const tooYoung = age !== null && age < 16;
  return (
    <div className="wl-field">
      <label className="wl-field-label">
        {label}
        {required && <span className="wl-required">*</span>}
      </label>
      <div className={`wl-date-wrap${value ? " has-value" : ""}`}>
        <Calendar size={16} style={{ color: "var(--color-foreground-muted)" }} />
        <span className="wl-date-display">
          {display ?? "Sélectionnez une date"}
        </span>
        <input
          type="date"
          className="wl-date-input"
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
      </div>
      <div className={`wl-field-helper${tooYoung ? " error" : ""}`}>
        {tooYoung
          ? `Vous devez avoir au moins 16 ans (actuellement ${age} ans)`
          : age !== null
            ? `Âge: ${age} ans · vous êtes éligible`
            : "Vous devez avoir au moins 16 ans"}
      </div>
    </div>
  );
}
