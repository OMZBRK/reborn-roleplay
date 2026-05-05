import { Fragment } from "react";
import { Check } from "lucide-react";

type Props = {
  current: 1 | 2 | 3;
};

const STEPS = [
  { n: 1, label: "HRP" },
  { n: 2, label: "RP" },
  { n: 3, label: "Validation" },
] as const;

export function Stepper({ current }: Props) {
  return (
    <div className="wl-stepper">
      {STEPS.map((s, i) => {
        const done = current > s.n;
        const active = current === s.n;
        return (
          <Fragment key={s.n}>
            <div
              className={`wl-step${active ? " active" : ""}${done ? " done" : ""}`}
            >
              <div className="wl-step-circle">
                {done ? <Check size={14} strokeWidth={3} /> : s.n}
              </div>
              <div className="wl-step-label">{s.label}</div>
            </div>
            {i < STEPS.length - 1 && (
              <div
                className={`wl-step-line${current > s.n ? " filled" : ""}`}
              />
            )}
          </Fragment>
        );
      })}
    </div>
  );
}
