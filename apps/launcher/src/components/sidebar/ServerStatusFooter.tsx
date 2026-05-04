import { LogOut } from "lucide-react";

type Props = {
  online: boolean;
  players: number;
  capacity: number;
  ping: number;
  ip: string;
  onLogout: () => void;
};

export function ServerStatusFooter({ online, players, capacity, ping, ip, onLogout }: Props) {
  const fillPct = Math.min(100, capacity === 0 ? 0 : (players / capacity) * 100);

  return (
    <div className="border-t border-border px-3 pb-4 pt-3">
      <div
        className="rounded-[10px] p-3"
        style={{
          background: "var(--color-surface-elevated)",
          border: "1px solid var(--color-border)",
        }}
      >
        <div className="mb-2 flex items-center justify-between gap-2">
          <div className="flex min-w-0 items-center gap-2">
            <span
              className={online ? "reborn-status-dot inline-block shrink-0" : "inline-block shrink-0"}
              style={{
                width: 8,
                height: 8,
                borderRadius: "var(--radius-full)",
                background: online ? "var(--color-success)" : "var(--color-muted)",
              }}
            />
            <span
              className="truncate text-[11px] font-medium"
              style={{ color: "var(--color-foreground)" }}
            >
              {online ? "Serveur en ligne" : "Serveur hors ligne"}
            </span>
          </div>
          <div className="flex shrink-0 items-baseline gap-0.5">
            <span
              className="font-mono text-[15px] font-semibold leading-none tabular-nums"
              style={{ color: "#fff" }}
            >
              {players}
            </span>
            <span
              className="font-mono text-[11px] leading-none tabular-nums"
              style={{ color: "var(--color-foreground-muted)" }}
            >
              /{capacity}
            </span>
          </div>
        </div>

        <div
          className="mb-2 h-1 overflow-hidden rounded-full"
          style={{ background: "var(--color-border)" }}
        >
          <div
            style={{
              width: `${fillPct}%`,
              height: "100%",
              background: "linear-gradient(90deg, var(--color-success) 0%, #5be6a0 100%)",
              boxShadow: "0 0 8px rgba(22, 163, 74, 0.5)",
              transition: "width 400ms cubic-bezier(0.16, 1, 0.3, 1)",
            }}
          />
        </div>

        <div
          className="flex items-center justify-between font-mono text-[10px]"
          style={{ color: "var(--color-foreground-muted)" }}
        >
          <span>Ping · {ping} ms</span>
          <span className="ml-2 truncate">{ip}</span>
        </div>
      </div>

      <button
        type="button"
        onClick={onLogout}
        className="mt-2 flex w-full items-center justify-center gap-2 rounded-[8px] border border-border py-2 text-xs text-foreground-subtle transition-colors hover:border-danger/35 hover:bg-danger/5 hover:text-danger"
      >
        <LogOut className="h-3 w-3" />
        Se déconnecter
      </button>
    </div>
  );
}
