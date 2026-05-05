// Compteur de caractères avec coloration progressive (vide / low / mid / ok).
// Aligné sur la logique counterClass de l'artefact whitelist-components.jsx.

type Props = {
  value: string;
  min: number;
};

function counterClass(value: string, min: number): string {
  const len = value.length;
  if (len === 0) return "wl-counter-empty";
  if (len < min * 0.7) return "wl-counter-low";
  if (len < min) return "wl-counter-mid";
  return "wl-counter-ok";
}

export function CharCounter({ value, min }: Props) {
  const len = value.length;
  const cls = counterClass(value, min);
  // La key sur le span force un remount quand on change d'état → relance
  // l'animation wl-counter-pop quand on passe à "ok".
  return (
    <span key={cls} className={`wl-counter ${cls}`}>
      {len}/{min}
    </span>
  );
}
