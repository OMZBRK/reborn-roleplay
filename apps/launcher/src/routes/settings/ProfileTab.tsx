import { useEffect, useRef, useState } from "react";
import { Check, Loader2, User2 } from "lucide-react";
import { useAuthStore } from "../../stores/auth-store";
import { updateDisplayName } from "../../lib/content";
import { formatIpcError } from "../../lib/errors";
import { mapRole, ROLE_META } from "../../components/sidebar/role";
import { Card, Row } from "./ui";

const RP_NAME_MIN = 2;
const RP_NAME_MAX = 24;

export function ProfileTab() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const pseudo = user?.displayName ?? user?.minecraftUsername ?? "Joueur";
  const initial = pseudo.charAt(0).toUpperCase();
  const roleType = mapRole(user?.role);
  const roleMeta = ROLE_META[roleType];

  const [rpName, setRpName] = useState(user?.displayName ?? "");
  const [rpSave, setRpSave] = useState<"idle" | "saving" | "saved">("idle");
  const [rpError, setRpError] = useState<string | null>(null);
  const savedTimer = useRef<number | null>(null);

  useEffect(
    () => () => {
      if (savedTimer.current !== null) window.clearTimeout(savedTimer.current);
    },
    [],
  );

  const unchanged = rpName.trim() === (user?.displayName ?? "");

  async function saveRpName() {
    if (rpSave === "saving" || unchanged) return;
    const trimmed = rpName.trim();
    if (trimmed.length < RP_NAME_MIN || trimmed.length > RP_NAME_MAX) {
      setRpError(`Ton nom RP doit faire entre ${RP_NAME_MIN} et ${RP_NAME_MAX} caractères.`);
      return;
    }
    setRpSave("saving");
    setRpError(null);
    try {
      const res = await updateDisplayName(trimmed);
      // Lecture fraiche du store : l'utilisateur a pu etre rafraichi pendant l'appel.
      const current = useAuthStore.getState().user;
      if (current) setUser({ ...current, displayName: res?.displayName ?? trimmed });
      setRpName(res?.displayName ?? trimmed);
      setRpSave("saved");
      if (savedTimer.current !== null) window.clearTimeout(savedTimer.current);
      savedTimer.current = window.setTimeout(() => setRpSave("idle"), 1500);
    } catch (err) {
      setRpError(formatIpcError(err, "Impossible d'enregistrer ton nom RP"));
      setRpSave("idle");
    }
  }

  return (
    <>
      {/* Carte d'en-tête horizontale : avatar à gauche, infos à droite. */}
      <section
        className="relative mb-5 overflow-hidden rounded-[14px] border"
        style={{
          borderColor: "var(--color-border)",
          background:
            "linear-gradient(135deg, var(--color-surface) 0%, var(--color-surface-elevated) 100%)",
        }}
      >
        <div
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              "radial-gradient(circle 240px at 88% 0%, var(--color-accent-glow) 0%, transparent 70%)",
          }}
        />
        <div className="relative flex items-center gap-5 p-5">
          <div
            aria-hidden="true"
            className="flex h-[72px] w-[72px] flex-shrink-0 items-center justify-center rounded-full font-display"
            style={{
              fontSize: 30,
              color: "var(--color-foreground)",
              background:
                "linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-pressed) 100%)",
              boxShadow:
                "0 0 0 2px var(--color-surface), 0 0 0 4px var(--color-accent), 0 0 24px -4px var(--color-accent-glow-strong)",
              letterSpacing: "0.02em",
            }}
          >
            {initial}
          </div>
          <div className="min-w-0 flex-1">
            <h2
              className="truncate font-display"
              style={{
                fontSize: 22,
                lineHeight: 1.1,
                letterSpacing: "0.03em",
                color: "var(--color-foreground)",
              }}
            >
              {pseudo}
            </h2>
            <p className="mt-0.5 text-[10.5px] font-medium uppercase tracking-[0.2em] text-foreground-muted">
              @{user?.minecraftUsername ?? "—"}
            </p>
            <div className="mt-2.5 flex items-center gap-2">
              <span
                className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[10.5px] font-semibold tracking-wide"
                style={{
                  background: `color-mix(in oklab, ${roleMeta.color} 14%, transparent)`,
                  border: `1px solid color-mix(in oklab, ${roleMeta.color} 38%, transparent)`,
                  color: roleMeta.color,
                }}
              >
                <Check className="h-2.5 w-2.5" strokeWidth={3} />
                {roleMeta.label}
              </span>
            </div>
          </div>
        </div>
      </section>

      <Card
        title="Identité"
        description="Visible dans le launcher, en jeu et sur le panel staff."
        icon={<User2 className="h-4 w-4" />}
      >
        <Row
          label="Pseudo Minecraft"
          hint="Récupéré depuis ton compte Microsoft. Modifiable uniquement chez Mojang."
        >
          <span className="text-sm font-medium">{user?.minecraftUsername ?? "—"}</span>
        </Row>
        <Row
          label="Nom d'affichage RP"
          hint={`Ton nom affiché en jeu, dans les messages privés et sur le panel (${RP_NAME_MIN} à ${RP_NAME_MAX} caractères).`}
        >
          <div className="flex flex-col items-end gap-1">
            <div className="flex items-center gap-2">
              <input
                type="text"
                value={rpName}
                maxLength={RP_NAME_MAX}
                aria-label="Nom d'affichage RP"
                aria-invalid={rpError ? true : undefined}
                placeholder={user?.minecraftUsername ?? "Nom RP"}
                onChange={(e) => {
                  setRpName(e.target.value);
                  if (rpError) setRpError(null);
                }}
                onKeyDown={(e) => e.key === "Enter" && void saveRpName()}
                className="w-44 rounded-md border border-border bg-surface-elevated px-3 py-1.5 text-sm text-foreground outline-none transition focus:border-accent/50"
              />
              <button
                type="button"
                onClick={() => void saveRpName()}
                disabled={rpSave === "saving" || unchanged}
                className="inline-flex items-center gap-1.5 rounded-md border border-accent/30 bg-accent/10 px-3 py-1.5 text-xs font-medium text-accent transition hover:bg-accent/20 disabled:opacity-50"
              >
                {rpSave === "saving" ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                ) : rpSave === "saved" ? (
                  <Check className="h-3.5 w-3.5 text-success" />
                ) : null}
                {rpSave === "saved" ? "Enregistré" : "Enregistrer"}
              </button>
            </div>
            {rpError && (
              <span role="alert" className="max-w-[22rem] text-right text-[10.5px] leading-snug text-danger">
                {rpError}
              </span>
            )}
          </div>
        </Row>
        <Row label="Rôle" hint="Définit tes permissions en jeu et sur le panel.">
          <span
            className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold tracking-wide"
            style={{
              background: `color-mix(in oklab, ${roleMeta.color} 14%, transparent)`,
              border: `1px solid color-mix(in oklab, ${roleMeta.color} 40%, transparent)`,
              color: roleMeta.color,
            }}
          >
            {roleMeta.label}
          </span>
        </Row>
      </Card>
    </>
  );
}
