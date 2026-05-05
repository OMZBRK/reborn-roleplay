type Props = {
  label: string;
  required?: boolean;
  placeholder?: string;
  value: string;
  onChange: (v: string) => void;
  type?: "text" | "url" | "email";
};

export function TextField({
  label,
  required,
  placeholder,
  value,
  onChange,
  type = "text",
}: Props) {
  return (
    <div className="wl-field">
      <label className="wl-field-label">
        {label}
        {required && <span className="wl-required">*</span>}
      </label>
      <input
        type={type}
        className="wl-input"
        placeholder={placeholder ?? ""}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}
