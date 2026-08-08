import { calcAge } from "../../../lib/whitelist-validation";

type Props = {
  label: string;
  required?: boolean;
  value: string;
  onChange: (v: string) => void;
};

const MONTHS = [
  "Janvier",
  "Février",
  "Mars",
  "Avril",
  "Mai",
  "Juin",
  "Juillet",
  "Août",
  "Septembre",
  "Octobre",
  "Novembre",
  "Décembre",
];

const SELECT_CLASS =
  "flex-1 rounded-md border border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-3 py-2.5 text-sm text-[var(--color-foreground)] outline-none transition focus:border-[color-mix(in_oklab,var(--color-accent)_50%,transparent)]";

/**
 * Champ de date de naissance en 3 selects (jour / mois / année) — fiable et
 * cohérent, contrairement au picker natif `type="date"` (« tableau » buggé sur
 * la WebView2). Les années listées commencent à -16 ans (âge minimum).
 */
export function DateField({ label, required, value, onChange }: Props) {
  const age = calcAge(value);
  const tooYoung = age !== null && age < 16;

  const [y, m, d] = value ? value.split("-").map(Number) : [0, 0, 0];

  const currentYear = new Date().getFullYear();
  const years: number[] = [];
  for (let yr = currentYear - 16; yr >= currentYear - 100; yr--) years.push(yr);

  const daysInMonth = m && y ? new Date(y, m, 0).getDate() : 31;
  const days = Array.from({ length: daysInMonth }, (_, i) => i + 1);

  function set(part: "y" | "m" | "d", val: number) {
    const ny = part === "y" ? val : y;
    const nm = part === "m" ? val : m;
    const nd = part === "d" ? val : d;
    if (ny && nm && nd) {
      const clampedDay = Math.min(nd, new Date(ny, nm, 0).getDate());
      onChange(
        `${ny}-${String(nm).padStart(2, "0")}-${String(clampedDay).padStart(2, "0")}`,
      );
    }
  }

  return (
    <div className="wl-field">
      <label className="wl-field-label">
        {label}
        {required && <span className="wl-required">*</span>}
      </label>
      <div className="flex items-center gap-2">
        <select
          className={SELECT_CLASS}
          value={d || ""}
          onChange={(e) => set("d", Number(e.target.value))}
        >
          <option value="" disabled>
            Jour
          </option>
          {days.map((dd) => (
            <option key={dd} value={dd}>
              {dd}
            </option>
          ))}
        </select>
        <select
          className={`${SELECT_CLASS} flex-[1.4]`}
          value={m || ""}
          onChange={(e) => set("m", Number(e.target.value))}
        >
          <option value="" disabled>
            Mois
          </option>
          {MONTHS.map((mm, i) => (
            <option key={mm} value={i + 1}>
              {mm}
            </option>
          ))}
        </select>
        <select
          className={SELECT_CLASS}
          value={y || ""}
          onChange={(e) => set("y", Number(e.target.value))}
        >
          <option value="" disabled>
            Année
          </option>
          {years.map((yy) => (
            <option key={yy} value={yy}>
              {yy}
            </option>
          ))}
        </select>
      </div>
      <div className={`wl-field-helper${tooYoung ? " error" : ""}`}>
        {tooYoung
          ? `Vous devez avoir au moins 16 ans (actuellement ${age} ans)`
          : age !== null
            ? `Âge : ${age} ans · vous êtes éligible`
            : "Vous devez avoir au moins 16 ans"}
      </div>
    </div>
  );
}
