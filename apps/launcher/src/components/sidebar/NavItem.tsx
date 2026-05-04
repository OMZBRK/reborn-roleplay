import { NavLink } from "react-router";
import type { LucideIcon } from "lucide-react";

export type NavItemConfig = {
  to: string;
  label: string;
  icon: LucideIcon;
};

type Props = {
  item: NavItemConfig;
  mountIndex: number;
};

export function NavItem({ item, mountIndex }: Props) {
  const Icon = item.icon;
  return (
    <NavLink
      to={item.to}
      className={({ isActive }) =>
        [
          "reborn-nav-item-mount group relative flex w-full items-center gap-3 rounded-[10px] py-2.5 pl-4 pr-3 text-left text-sm transition-all duration-150 ease-out",
          isActive
            ? "is-active text-white"
            : "text-foreground-subtle hover:scale-[1.01] hover:text-foreground",
        ].join(" ")
      }
      style={{ animationDelay: `${120 + mountIndex * 35}ms` }}
    >
      {({ isActive }) => (
        <>
          {/* Background actif/hover — un seul fond, transitionné */}
          <span
            aria-hidden
            className="pointer-events-none absolute inset-0 rounded-[10px] transition-colors duration-150 ease-out"
            style={{
              background: isActive
                ? "linear-gradient(90deg, rgba(59,91,219,0.12) 0%, rgba(59,91,219,0.04) 100%)"
                : "transparent",
            }}
          />
          {/* Border-left 3px accent uniquement actif */}
          <span
            aria-hidden
            className="absolute"
            style={{
              left: 0,
              top: 8,
              bottom: 8,
              width: 3,
              borderRadius: 2,
              background: isActive ? "var(--color-accent)" : "transparent",
              boxShadow: isActive ? "0 0 8px rgba(59, 91, 219, 0.6)" : "none",
              transition: "background 150ms ease-out, box-shadow 150ms ease-out",
            }}
          />
          {/* Hover background subtle accent — via CSS group-hover sans active */}
          <span
            aria-hidden
            className={[
              "pointer-events-none absolute inset-0 rounded-[10px] transition-opacity duration-150 ease-out",
              isActive ? "opacity-0" : "opacity-0 group-hover:opacity-100",
            ].join(" ")}
            style={{ background: "rgba(59, 91, 219, 0.04)" }}
          />
          <Icon
            className="relative h-4 w-4 transition-colors duration-150 ease-out"
            style={{
              color: isActive ? "var(--color-accent-hover)" : "currentColor",
            }}
          />
          <span
            className="relative tracking-wide"
            style={{
              fontWeight: isActive ? 500 : 400,
              letterSpacing: "0.005em",
            }}
          >
            {item.label}
          </span>
        </>
      )}
    </NavLink>
  );
}
