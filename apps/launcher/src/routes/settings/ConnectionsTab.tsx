import { useState, type ReactNode } from "react";
import { AlertTriangle, Check, Link2, Loader2, Unlink } from "lucide-react";
import { useAuthStore } from "../../stores/auth-store";
import { refreshMe, unlinkDiscord } from "../../lib/auth";
import { formatIpcError } from "../../lib/errors";
import { useDiscordLink } from "../../hooks/use-discord-link";
import { Card, InlineAction } from "./ui";

export function ConnectionsTab() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const [unlinking, setUnlinking] = useState(false);
  // Liaison + polling partagés avec l'écran d'accueil (hooks/use-discord-link).
  const discordLink = useDiscordLink();
  const { error, setError } = discordLink;
  const waitingForCallback = discordLink.waiting;
  const busy: "linking" | "unlinking" | null = discordLink.linking
    ? "linking"
    : unlinking
      ? "unlinking"
      : null;

  async function handleUnlink() {
    setError(null);
    setUnlinking(true);
    try {
      await unlinkDiscord();
      const fresh = await refreshMe();
      setUser(fresh);
    } catch (err) {
      setError(formatIpcError(err, "Impossible de délier ton compte Discord"));
    } finally {
      setUnlinking(false);
    }
  }

  const linked = user?.discord ?? null;

  return (
    <>
      <Card
        title="Comptes liés"
        description="Discord est obligatoire pour postuler à la whitelist. Steam et Twitch arrivent en v1.0.5+."
        icon={<Link2 className="h-4 w-4" />}
      >
        <ConnectionRow
          label="Microsoft"
          hint="Compte principal de connexion. Ne peut pas être délié."
          status="linked"
          statusLabel="Lié"
          color="#16a34a"
        />

        {linked ? (
          <ConnectionRow
            label="Discord"
            hint={`Lié à @${linked.username} (ID ${linked.userId}) depuis le ${new Date(
              linked.linkedAt,
            ).toLocaleDateString("fr-FR", { day: "2-digit", month: "long", year: "numeric" })}.`}
            status="linked"
            statusLabel="Lié"
            color="#5865F2"
            action={
              <button
                type="button"
                onClick={() => void handleUnlink()}
                disabled={busy !== null}
                className="inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-background px-3 text-[11px] font-medium transition hover:border-danger/40 hover:text-danger disabled:opacity-60"
              >
                {busy === "unlinking" ? (
                  <Loader2 className="h-3 w-3 animate-spin" />
                ) : (
                  <Unlink className="h-3 w-3" />
                )}
                Délier
              </button>
            }
          />
        ) : (
          <ConnectionRow
            label="Discord"
            hint="À relier avant la candidature whitelist."
            status="unlinked"
            statusLabel="Non lié"
            color="#5865F2"
            action={
              <button
                type="button"
                onClick={() => void discordLink.link()}
                disabled={busy !== null || waitingForCallback}
                className="inline-flex h-8 items-center gap-1.5 rounded-md bg-accent px-3 text-[11px] font-semibold text-white transition hover:bg-accent-hover hover:shadow-[0_0_18px_-6px_var(--color-accent-glow-strong)] disabled:opacity-60"
              >
                {busy === "linking" ? (
                  <Loader2 className="h-3 w-3 animate-spin" />
                ) : (
                  <Link2 className="h-3 w-3" />
                )}
                Lier Discord
              </button>
            }
          />
        )}

        <ConnectionRow
          label="Steam"
          hint="Optionnel. Donne un signal de confiance pour la modération."
          status="soon"
          statusLabel="v1.0.5"
          color="#94a3b8"
        />
      </Card>

      {waitingForCallback && (
        <div
          role="status"
          className="mb-5 rounded-[12px] border border-accent/40 bg-accent/8 p-4 text-xs"
        >
          <p className="flex items-center gap-2 font-semibold text-accent">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            En attente de la validation Discord…
          </p>
          <p className="mt-1.5 leading-relaxed text-foreground-subtle">
            Une page Discord vient de s'ouvrir dans ton navigateur. Autorise l'application Reborn
            puis reviens ici : le lien apparaîtra automatiquement.
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <InlineAction onClick={() => void discordLink.refreshNow()}>
              Rafraîchir manuellement
            </InlineAction>
            <InlineAction onClick={() => void discordLink.reopen()}>
              Rouvrir la page Discord
            </InlineAction>
            <InlineAction onClick={discordLink.cancel}>Annuler</InlineAction>
          </div>
        </div>
      )}

      {error && (
        <div
          role="alert"
          className="mb-5 flex items-start gap-2 rounded-[12px] border border-danger/40 bg-danger/10 p-4 text-xs text-danger"
        >
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
          <span className="min-w-0 flex-1 break-words">{error}</span>
        </div>
      )}
    </>
  );
}

function ConnectionRow({
  label,
  hint,
  status,
  statusLabel,
  color,
  action,
}: {
  label: string;
  hint: string;
  status: "linked" | "unlinked" | "soon";
  statusLabel: string;
  color: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex items-center gap-4 px-6 py-4">
      <div
        aria-hidden="true"
        className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-md font-display text-sm"
        style={{
          background: `color-mix(in oklab, ${color} 10%, transparent)`,
          border: `1px solid color-mix(in oklab, ${color} 30%, transparent)`,
          color,
        }}
      >
        {label.charAt(0)}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="text-[13px] font-semibold text-foreground">{label}</p>
          <span
            className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-2xs font-semibold tracking-wide"
            style={{
              background: `color-mix(in oklab, ${color} 14%, transparent)`,
              border: `1px solid color-mix(in oklab, ${color} 35%, transparent)`,
              color,
            }}
          >
            {status === "linked" && <Check className="h-2.5 w-2.5" strokeWidth={3} />}
            {statusLabel}
          </span>
        </div>
        <p className="mt-1 text-[11.5px] leading-snug text-foreground-subtle">{hint}</p>
      </div>
      {action && <div className="flex-shrink-0">{action}</div>}
    </div>
  );
}
