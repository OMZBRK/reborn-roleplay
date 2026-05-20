export type ModsTab = "mods" | "shaders" | "packs";

type TabItem = {
  id: ModsTab;
  label: string;
  count: number | null;
};

type Props = {
  active: ModsTab;
  modsCount: number;
  shadersCount: number;
  packsCount: number | null;
  onChange: (tab: ModsTab) => void;
};

export function ModsTabs({
  active,
  modsCount,
  shadersCount,
  packsCount,
  onChange,
}: Props) {
  const tabs: TabItem[] = [
    { id: "mods", label: "Mods", count: modsCount },
    { id: "shaders", label: "Shaders", count: shadersCount },
    { id: "packs", label: "Resource Packs", count: packsCount },
  ];

  return (
    <aside className="reborn-mods-tabs" role="tablist" aria-label="Catégories">
      {tabs.map((t) => (
        <button
          key={t.id}
          type="button"
          role="tab"
          aria-selected={active === t.id}
          data-active={active === t.id || undefined}
          onClick={() => onChange(t.id)}
          className="reborn-mods-tab"
        >
          <span>{t.label}</span>
          {t.count != null && (
            <span className="reborn-mods-tab-count">{t.count}</span>
          )}
        </button>
      ))}
    </aside>
  );
}
