/**
 * Bloc skeleton generique — utilise Tailwind animate-pulse + bg surface
 * elevated pour rester subtil. Pour les patterns specifiques (table row,
 * card), composer plusieurs Skeleton.
 */
export function Skeleton({
  className = '',
  style,
}: {
  className?: string;
  style?: React.CSSProperties;
}) {
  return (
    <div
      className={`animate-pulse rounded bg-[var(--color-surface-elevated)] ${className}`}
      style={style}
    />
  );
}

export function SkeletonRows({ count = 5 }: { count?: number }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className="rounded-[12px] border border-[var(--color-border)] bg-[var(--color-surface)] p-4"
        >
          <Skeleton className="h-4 w-32" />
          <Skeleton className="mt-2 h-3 w-1/2" />
        </div>
      ))}
    </div>
  );
}

export function SkeletonTable({ rows = 5 }: { rows?: number }) {
  return (
    <div className="overflow-hidden rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)]">
      <div className="grid grid-cols-5 gap-4 border-b border-[var(--color-border)] bg-[var(--color-surface-elevated)] px-4 py-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-3 w-16" />
        ))}
      </div>
      {Array.from({ length: rows }).map((_, i) => (
        <div
          key={i}
          className="grid grid-cols-5 gap-4 border-t border-[var(--color-border)] px-4 py-4"
        >
          {Array.from({ length: 5 }).map((_, j) => (
            <Skeleton key={j} className="h-4" style={{ width: `${60 + ((i + j) % 4) * 10}%` }} />
          ))}
        </div>
      ))}
    </div>
  );
}
