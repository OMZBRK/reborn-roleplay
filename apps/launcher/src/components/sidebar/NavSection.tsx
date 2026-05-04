import { NavItem, type NavItemConfig } from "./NavItem";

export type NavSectionConfig = {
  label: string;
  items: NavItemConfig[];
};

type Props = {
  section: NavSectionConfig;
  isFirst: boolean;
  itemOffset: number;
};

export function NavSection({ section, isFirst, itemOffset }: Props) {
  return (
    <div className="px-3" style={{ paddingTop: isFirst ? 4 : 14 }}>
      <div
        className="px-4 pb-2 text-[10px] font-semibold uppercase tracking-[0.18em]"
        style={{ color: "var(--color-foreground-muted)" }}
      >
        {section.label}
      </div>
      <div className="flex flex-col gap-0.5">
        {section.items.map((item, i) => (
          <NavItem key={item.to} item={item} mountIndex={itemOffset + i} />
        ))}
      </div>
    </div>
  );
}
