import { Check } from "lucide-react";
import { ROLE_META, type RoleType } from "./role";

export function RoleBadge({ role }: { role: RoleType }) {
  const meta = ROLE_META[role];
  return (
    <span
      className="inline-flex items-center gap-1 rounded-full text-[10px] font-semibold tracking-wide"
      style={{
        padding: "2px 7px 2px 5px",
        background: `color-mix(in oklab, ${meta.color} 14%, transparent)`,
        border: `1px solid color-mix(in oklab, ${meta.color} 40%, transparent)`,
        color: meta.color,
      }}
    >
      <Check className="h-[9px] w-[9px]" strokeWidth={3} />
      {meta.label}
    </span>
  );
}
