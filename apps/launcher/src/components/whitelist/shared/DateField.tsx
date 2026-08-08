import { useEffect, useRef, useState } from "react";
import {
  Calendar,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
} from "lucide-react";
import { calcAge } from "../../../lib/whitelist-validation";
import { cn } from "../../../lib/cn";

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
const WEEKDAYS = ["L", "M", "M", "J", "V", "S", "D"];

/**
 * Date de naissance via un vrai calendrier (grille mensuelle cliquable) avec
 * navigation mois (‹ ›) et année (‹‹ ››) — pratique pour reculer de ~20 ans.
 * Remplace le picker natif `type="date"` buggé sur la WebView2.
 */
export function DateField({ label, required, value, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const age = calcAge(value);
  const tooYoung = age !== null && age < 16;

  const sel = value ? value.split("-").map(Number) : null; // [y, m(1-12), d]
  const now = new Date();
  const [viewY, setViewY] = useState(sel ? sel[0] : now.getFullYear() - 20);
  const [viewM, setViewM] = useState(sel ? sel[1] - 1 : 0); // 0-indexé

  useEffect(() => {
    if (!open) return;
    const h = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, [open]);

  function shiftMonth(delta: number) {
    let m = viewM + delta;
    let y = viewY;
    if (m < 0) {
      m = 11;
      y--;
    } else if (m > 11) {
      m = 0;
      y++;
    }
    setViewM(m);
    setViewY(y);
  }

  function pick(day: number) {
    onChange(
      `${viewY}-${String(viewM + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`,
    );
    setOpen(false);
  }

  const firstDow = (new Date(viewY, viewM, 1).getDay() + 6) % 7; // 0 = lundi
  const daysInMonth = new Date(viewY, viewM + 1, 0).getDate();
  const cells: (number | null)[] = [
    ...Array(firstDow).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];

  const displayStr = sel ? `${sel[2]} ${MONTHS[sel[1] - 1]} ${sel[0]}` : null;

  return (
    <div className="wl-field" ref={ref}>
      <label className="wl-field-label">
        {label}
        {required && <span className="wl-required">*</span>}
      </label>

      <div className="relative">
        <button
          type="button"
          onClick={() => setOpen((o) => !o)}
          className={cn("wl-date-wrap w-full", value && "has-value")}
        >
          <Calendar size={16} style={{ color: "var(--color-foreground-muted)" }} />
          <span className="wl-date-display">{displayStr ?? "Sélectionnez une date"}</span>
        </button>

        {open && (
          <div className="absolute left-0 top-full z-30 mt-2 w-[288px] rounded-lg border border-[var(--color-border-strong)] bg-[var(--color-surface-elevated)] p-3 shadow-xl">
            <div className="mb-2 flex items-center justify-between">
              <div className="flex gap-1">
                <NavBtn onClick={() => setViewY((y) => y - 1)} title="Année précédente">
                  <ChevronsLeft size={15} />
                </NavBtn>
                <NavBtn onClick={() => shiftMonth(-1)} title="Mois précédent">
                  <ChevronLeft size={15} />
                </NavBtn>
              </div>
              <span className="text-[13px] font-medium text-[var(--color-foreground)]">
                {MONTHS[viewM]} {viewY}
              </span>
              <div className="flex gap-1">
                <NavBtn onClick={() => shiftMonth(1)} title="Mois suivant">
                  <ChevronRight size={15} />
                </NavBtn>
                <NavBtn onClick={() => setViewY((y) => y + 1)} title="Année suivante">
                  <ChevronsRight size={15} />
                </NavBtn>
              </div>
            </div>

            <div className="mb-1 grid grid-cols-7 gap-1">
              {WEEKDAYS.map((w, i) => (
                <span
                  key={i}
                  className="text-center text-[10px] font-semibold uppercase text-[var(--color-foreground-muted)]"
                >
                  {w}
                </span>
              ))}
            </div>

            <div className="grid grid-cols-7 gap-1">
              {cells.map((d, i) =>
                d === null ? (
                  <span key={i} />
                ) : (
                  <button
                    key={i}
                    type="button"
                    onClick={() => pick(d)}
                    className={cn(
                      "h-8 rounded text-[13px] transition-colors",
                      sel && sel[2] === d && sel[1] === viewM + 1 && sel[0] === viewY
                        ? "bg-[var(--color-accent)] font-semibold text-white"
                        : "text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface)]",
                    )}
                  >
                    {d}
                  </button>
                ),
              )}
            </div>
          </div>
        )}
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

function NavBtn({
  onClick,
  title,
  children,
}: {
  onClick: () => void;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={title}
      className="flex h-6 w-6 items-center justify-center rounded text-[var(--color-foreground-muted)] transition-colors hover:bg-[var(--color-surface)] hover:text-[var(--color-foreground)]"
    >
      {children}
    </button>
  );
}
