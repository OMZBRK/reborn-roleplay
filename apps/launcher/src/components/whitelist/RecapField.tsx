import { useState } from "react";

type Props = {
  label: string;
  value: string;
  truncate?: number;
};

export function RecapField({ label, value, truncate = 200 }: Props) {
  const [expanded, setExpanded] = useState(false);
  const isEmpty = !value;
  const isLong = !isEmpty && value.length > truncate;
  const display = expanded || !isLong ? value : value.slice(0, truncate) + "…";
  return (
    <div className="wl-recap-field">
      <div className="wl-recap-label">{label}</div>
      <div className={`wl-recap-value${isEmpty ? " wl-recap-empty" : ""}`}>
        {isEmpty ? "— non renseigné —" : display}
      </div>
      {isLong && (
        <button
          type="button"
          className="wl-recap-more"
          onClick={() => setExpanded((e) => !e)}
        >
          {expanded ? "Réduire ↑" : "Lire la suite →"}
        </button>
      )}
    </div>
  );
}
