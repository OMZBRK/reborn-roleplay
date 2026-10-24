import { useRef, type ComponentType, type KeyboardEvent, type ReactNode } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { AlertTriangle, Check, Info, Loader2, RotateCcw } from "lucide-react";
import { cn } from "../../lib/cn";

// Briques visuelles communes aux onglets des Paramètres (Card, Row, Toggle,
// selecteurs, lignes d'etat). Visuel inchange par rapport a l'ancien
// Settings.tsx monolithique ; ajouts : roles ARIA (switch, radiogroup),
// lignes d'erreur avec « Réessayer ». Le focus clavier est l'anneau or
// global (:focus-visible dans globals.css) : ne pas poser outline-none.

/** Gros bouton pleine largeur des cartes d'entretien (verifier, nettoyer…). */
export const WIDE_BUTTON =
  "inline-flex w-full items-center justify-center gap-2 rounded-md border border-border-strong bg-surface px-4 py-3 text-[13px] font-medium text-foreground transition hover:border-accent/50 hover:text-accent disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:border-border-strong disabled:hover:text-foreground";

/** Bouton secondaire compact (Ouvrir, Copier…). */
export const COMPACT_BUTTON =
  "inline-flex flex-shrink-0 items-center gap-1.5 rounded-md border border-border-strong bg-surface px-3 py-2.5 text-xs font-medium text-foreground transition hover:border-accent/50 hover:text-accent disabled:cursor-not-allowed disabled:opacity-60";

/** « 4 Go », « 4,5 Go » (format francais, une decimale au plus). */
export function formatGo(mb: number): string {
  return `${(mb / 1024).toLocaleString("fr-FR", { maximumFractionDigits: 1 })} Go`;
}

/** « 820 Ko », « 12,4 Mo », « 1,2 Go » (base 1000, comme les Mo du backend). */
export function formatBytes(bytes: number): string {
  const units = ["octets", "Ko", "Mo", "Go"];
  let v = Math.max(0, bytes);
  let i = 0;
  while (v >= 1000 && i < units.length - 1) {
    v /= 1000;
    i += 1;
  }
  return `${v.toLocaleString("fr-FR", { maximumFractionDigits: i >= 2 ? 1 : 0 })} ${units[i]}`;
}

/** « 1 fichier », « 3 fichiers ». */
export function plural(n: number, singular: string, pluralForm = `${singular}s`): string {
  return `${n.toLocaleString("fr-FR")} ${n > 1 ? pluralForm : singular}`;
}

export function Card({
  title,
  description,
  icon,
  accent,
  children,
}: {
  title: string;
  description?: string;
  icon?: ReactNode;
  accent?: "default" | "danger";
  children: ReactNode;
}) {
  const danger = accent === "danger";
  return (
    <section
      className={cn(
        "group relative mb-5 overflow-hidden rounded-[14px] border bg-surface/80 transition-colors",
        danger
          ? "border-danger/30 hover:border-danger/50"
          : "border-border hover:border-border-strong",
      )}
    >
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-px opacity-60"
        style={{
          background: danger
            ? "linear-gradient(90deg, transparent, rgba(239,68,68,0.5) 50%, transparent)"
            : "linear-gradient(90deg, transparent, rgba(160, 24, 43, 0.45) 50%, transparent)",
        }}
      />
      <div className="flex items-start gap-3 border-b border-border/60 px-6 pb-4 pt-5">
        {icon && (
          <div
            className={cn(
              "flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-md",
              danger ? "bg-danger/10 text-danger" : "bg-accent/12 text-accent",
            )}
          >
            {icon}
          </div>
        )}
        <div className="min-w-0 flex-1">
          <h2
            className="font-display text-lg tracking-wide text-foreground"
            style={{ letterSpacing: "0.04em" }}
          >
            {title}
          </h2>
          {description && (
            <p className="mt-1 text-xs leading-relaxed text-foreground-subtle">{description}</p>
          )}
        </div>
      </div>
      <div className="divide-y divide-border/40">{children}</div>
    </section>
  );
}

export function Row({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="flex items-center justify-between gap-6 px-6 py-4">
      <div className="min-w-0 flex-1">
        <p className="text-[13px] font-medium leading-tight text-foreground">{label}</p>
        {hint && <p className="mt-1 text-[11.5px] leading-snug text-foreground-subtle">{hint}</p>}
      </div>
      <div className="flex-shrink-0">{children}</div>
    </div>
  );
}

export function SpecCard({
  icon: Icon,
  label,
  value,
  sub,
}: {
  icon: ComponentType<{ className?: string }>;
  label: string;
  value: string;
  sub: string;
}) {
  return (
    <div className="relative overflow-hidden rounded-[12px] border border-border bg-surface/70 p-4">
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-px"
        style={{
          background:
            "linear-gradient(90deg, transparent, rgba(160, 24, 43, 0.4) 50%, transparent)",
        }}
      />
      <div className="flex items-center gap-2">
        <span className="flex h-7 w-7 items-center justify-center rounded-md bg-accent/12 text-accent">
          <Icon className="h-3.5 w-3.5" />
        </span>
        <span className="text-2xs font-semibold uppercase tracking-[0.18em] text-foreground-muted">
          {label}
        </span>
      </div>
      <p
        className="mt-3 truncate font-display text-lg text-foreground"
        style={{ letterSpacing: "0.02em" }}
        title={value}
      >
        {value}
      </p>
      <p className="mt-0.5 truncate text-[11px] text-foreground-subtle">{sub}</p>
    </div>
  );
}

export function PrettySelect({
  value,
  onChange,
  options,
  leadingIcon: LeadingIcon,
  ariaLabel,
  disabled,
}: {
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
  leadingIcon?: ComponentType<{ className?: string }>;
  ariaLabel: string;
  disabled?: boolean;
}) {
  return (
    <div className="relative inline-flex items-center">
      {LeadingIcon && (
        <LeadingIcon className="pointer-events-none absolute left-3 h-3.5 w-3.5 text-foreground-subtle" />
      )}
      <select
        value={value}
        aria-label={ariaLabel}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
        className={cn(
          "h-9 cursor-pointer appearance-none rounded-md border border-border bg-background pr-8 text-xs font-medium text-foreground transition hover:border-border-strong focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/40 disabled:cursor-not-allowed disabled:opacity-60",
          LeadingIcon ? "pl-9" : "pl-3",
        )}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <svg
        className="pointer-events-none absolute right-2.5 h-3 w-3 text-foreground-subtle"
        viewBox="0 0 12 12"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
      >
        <path
          d="M3 4.5L6 7.5L9 4.5"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  );
}

export function Toggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  /** Nom accessible (le libelle de la ligne). */
  label: string;
}) {
  // Sizing : pill 40×22, knob 16×16. translate-x = pill_width - knob - 2*padding
  // = 40 - 16 - 6 = 18px. Knob reste integralement dans le pill, plus de
  // depassement visuel (cf depassement.png feedback utilisateur).
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={cn(
        "relative inline-flex h-[22px] w-10 flex-shrink-0 rounded-full border transition-colors duration-200",
        checked
          ? "border-accent bg-accent"
          : "border-border-strong bg-surface-overlay hover:border-border-strong",
      )}
    >
      <span
        className={cn(
          "absolute top-[2px] h-[16px] w-[16px] rounded-full bg-white shadow-sm transition-transform duration-200",
          checked ? "translate-x-[20px]" : "translate-x-[2px]",
        )}
      />
    </button>
  );
}

/** Choix exclusif en pastilles (radiogroup : fleches gauche/droite au clavier). */
export function Segmented<T extends string>({
  value,
  onChange,
  options,
  ariaLabel,
}: {
  value: T;
  onChange: (v: T) => void;
  options: { value: T; label: string; icon?: ComponentType<{ className?: string }> }[];
  ariaLabel: string;
}) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  function onKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    const delta =
      e.key === "ArrowRight" || e.key === "ArrowDown"
        ? 1
        : e.key === "ArrowLeft" || e.key === "ArrowUp"
          ? -1
          : 0;
    if (!delta) return;
    e.preventDefault();
    const current = Math.max(
      0,
      options.findIndex((o) => o.value === value),
    );
    const next = (current + delta + options.length) % options.length;
    onChange(options[next].value);
    refs.current[next]?.focus();
  }

  return (
    <div
      role="radiogroup"
      aria-label={ariaLabel}
      onKeyDown={onKeyDown}
      className="inline-flex items-center gap-1 rounded-md border border-border bg-background p-1"
    >
      {options.map((o, i) => {
        const active = o.value === value;
        const Icon = o.icon;
        return (
          <button
            key={o.value}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="radio"
            aria-checked={active}
            tabIndex={active ? 0 : -1}
            onClick={() => onChange(o.value)}
            className={cn(
              "inline-flex h-7 items-center gap-1.5 rounded-[5px] px-3 text-xs font-medium transition",
              active
                ? "bg-accent text-white shadow-[0_0_14px_-6px_var(--color-accent-glow-strong)]"
                : "text-foreground-subtle hover:bg-surface-overlay hover:text-foreground",
            )}
          >
            {Icon && <Icon className="h-3.5 w-3.5" />}
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

export type StatusTone = "success" | "warning" | "error" | "info";

const TONE_TEXT: Record<StatusTone, string> = {
  success: "text-success",
  warning: "text-warning",
  error: "text-danger",
  info: "text-foreground-subtle",
};

/** Petit bouton d'action dans une ligne d'etat (Réessayer, Afficher…). */
export function InlineAction({
  onClick,
  children,
  icon: Icon,
  disabled,
}: {
  onClick: () => void;
  children: ReactNode;
  icon?: ComponentType<{ className?: string }>;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="inline-flex flex-shrink-0 items-center gap-1 rounded-md border border-border bg-background px-2.5 py-1 text-[11px] font-medium text-foreground transition hover:border-accent/40 hover:text-accent disabled:opacity-60"
    >
      {Icon && <Icon className="h-3 w-3" />}
      {children}
    </button>
  );
}

/** Bouton « Réessayer » standard. */
export function RetryAction({ onClick, disabled }: { onClick: () => void; disabled?: boolean }) {
  return (
    <InlineAction onClick={onClick} icon={RotateCcw} disabled={disabled}>
      Réessayer
    </InlineAction>
  );
}

/** Ligne d'etat sous un bouton : icone + message (+ action eventuelle). */
export function StatusLine({
  tone,
  children,
  action,
  className,
}: {
  tone: StatusTone;
  children: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  const Icon = tone === "success" ? Check : tone === "info" ? Info : AlertTriangle;
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={cn("mt-3 flex items-start gap-2 text-xs leading-snug", TONE_TEXT[tone], className)}
    >
      <Icon className="mt-[1px] h-3.5 w-3.5 flex-shrink-0" />
      <span className="min-w-0 flex-1 break-words">{children}</span>
      {action}
    </div>
  );
}

/** Encadre d'erreur (chargement d'un onglet, action echouee hors carte). */
export function ErrorPanel({
  message,
  onRetry,
  retrying,
}: {
  message: string;
  onRetry?: () => void;
  retrying?: boolean;
}) {
  return (
    <div
      role="alert"
      className="mb-5 flex items-start gap-3 rounded-[12px] border border-danger/40 bg-danger/10 px-4 py-3 text-[13px] leading-snug text-danger"
    >
      <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" />
      <span className="min-w-0 flex-1 break-words">{message}</span>
      {onRetry && <RetryAction onClick={onRetry} disabled={retrying} />}
    </div>
  );
}

export function LoadingLine({ label = "Chargement…" }: { label?: string }) {
  return (
    <div role="status" className="flex items-center gap-2 text-sm text-foreground-subtle">
      <Loader2 className="h-4 w-4 animate-spin" />
      {label}
    </div>
  );
}

/** Pastille flottante « Enregistrement… / Enregistré ». */
export function SaveToast({ state }: { state: "idle" | "saving" | "saved" }) {
  return (
    <AnimatePresence>
      {state !== "idle" && (
        <motion.div
          role="status"
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 4 }}
          className="fixed bottom-6 right-6 z-30 flex items-center gap-2 rounded-full border border-border-strong bg-surface-elevated px-4 py-2 text-xs shadow-lg"
        >
          {state === "saving" ? (
            <>
              <Loader2 className="h-3 w-3 animate-spin text-accent" />
              <span className="font-medium">Enregistrement…</span>
            </>
          ) : (
            <>
              <Check className="h-3 w-3 text-success" />
              <span className="font-medium">Enregistré</span>
            </>
          )}
        </motion.div>
      )}
    </AnimatePresence>
  );
}
