import { CharCounter } from "./CharCounter";

type Props = {
  label: string;
  required?: boolean;
  placeholder?: string;
  value: string;
  onChange: (v: string) => void;
  min?: number;
  helper?: string;
  rows?: number;
};

export function Textarea({
  label,
  required,
  placeholder,
  value,
  onChange,
  min,
  helper,
  rows = 5,
}: Props) {
  return (
    <div className="wl-field">
      <div className="wl-field-row">
        <label className="wl-field-label">
          {label}
          {required && <span className="wl-required">*</span>}
        </label>
        {min !== undefined && <CharCounter value={value} min={min} />}
      </div>
      <textarea
        className="wl-textarea"
        rows={rows}
        placeholder={placeholder ?? ""}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {helper && <div className="wl-field-helper">{helper}</div>}
    </div>
  );
}
