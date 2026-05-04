import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { motion } from "framer-motion";
import {
  AlertTriangle,
  Check,
  Loader2,
  LogOut,
  MonitorSmartphone,
  User2,
  Link2,
  Gamepad2,
  Unlink,
} from "lucide-react";
import { useAuthStore } from "../stores/auth-store";
import { getPrefs, setPrefs, type Preferences } from "../lib/prefs";
import { logout, refreshMe, startDiscordLink, unlinkDiscord } from "../lib/auth";
import { cn } from "../lib/cn";

const TABS = [
  { id: "profile", label: "Profil", icon: User2 },
  { id: "account", label: "Compte", icon: MonitorSmartphone },
  { id: "connections", label: "Connexions", icon: Link2 },
  { id: "game", label: "Jeu", icon: Gamepad2 },
] as const;
type TabId = (typeof TABS)[number]["id"];

export function Settings() {
  const [tab, setTab] = useState<TabId>("profile");

  return (
    <div className="px-8 py-8">
      <header className="mb-6">
        <p className="text-xs uppercase tracking-widest text-foreground-subtle">Reborn Roleplay</p>
        <h1 className="mt-1 font-display text-3xl font-semibold">Paramètres</h1>
      </header>

      <nav className="mb-6 flex gap-1 border-b border-border">
        {TABS.map((t) => {
          const Icon = t.icon;
          const active = tab === t.id;
          return (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={cn(
                "relative flex items-center gap-2 px-4 py-3 text-sm font-medium transition",
                active ? "text-foreground" : "text-foreground-subtle hover:text-foreground",
              )}
            >
              <Icon className="h-4 w-4" />
              {t.label}
              {active && (
                <motion.span
                  layoutId="settings-tab-underline"
                  className="absolute inset-x-0 -bottom-px h-0.5 bg-accent"
                />
              )}
            </button>
          );
        })}
      </nav>

      <div className="max-w-3xl">
        {tab === "profile" && <ProfileTab />}
        {tab === "account" && <AccountTab />}
        {tab === "connections" && <ConnectionsTab />}
        {tab === "game" && <GameTab />}
      </div>
    </div>
  );
}

function Section({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="mb-6 rounded-[--radius-card] border border-border bg-surface p-6">
      <h2 className="font-display text-base font-semibold">{title}</h2>
      {description && <p className="mt-1 text-xs text-foreground-subtle">{description}</p>}
      <div className="mt-4 flex flex-col gap-4">{children}</div>
    </section>
  );
}

function Row({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex items-center justify-between gap-4">
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium">{label}</p>
        {hint && <p className="mt-0.5 text-xs text-foreground-subtle">{hint}</p>}
      </div>
      <div className="flex-shrink-0">{children}</div>
    </div>
  );
}

function ProfileTab() {
  const user = useAuthStore((s) => s.user);
  return (
    <Section title="Profil" description="Comment tu apparais aupres des autres joueurs Reborn.">
      <Row label="Pseudo Minecraft" hint="Recupere depuis ton compte Microsoft.">
        <span className="text-sm font-medium">{user?.minecraftUsername ?? "—"}</span>
      </Row>
      <Row label="Nom d'affichage" hint="Visible dans les DM et le panel staff.">
        <span className="text-sm">{user?.displayName ?? user?.minecraftUsername ?? "—"}</span>
      </Row>
      <Row label="Role" hint="Definit tes permissions in-game et sur le panel.">
        <span className="rounded-md bg-accent/10 px-2.5 py-1 text-xs font-medium text-accent">
          {user?.role ?? "PLAYER"}
        </span>
      </Row>
    </Section>
  );
}

function AccountTab() {
  const user = useAuthStore((s) => s.user);
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
      setSession(null);
      navigate("/login", { replace: true });
    } catch {
      // Best-effort : meme si la requete echoue, on degage la session locale.
      setSession(null);
      navigate("/login", { replace: true });
    }
  }

  return (
    <>
      <Section
        title="Compte"
        description="Informations liees a ton compte Microsoft. Modifiables uniquement via account.microsoft.com."
      >
        <Row label="UUID Minecraft" hint="Identifiant unique attribue par Mojang.">
          <code className="rounded bg-surface-elevated px-2 py-0.5 text-xs">
            {user?.minecraftUuid ?? "—"}
          </code>
        </Row>
        <Row label="Identifiant Reborn" hint="Cle interne utilisee par les tickets et l'API.">
          <code className="rounded bg-surface-elevated px-2 py-0.5 text-xs">
            {user?.id ?? "—"}
          </code>
        </Row>
        <Row label="Version du launcher">
          <span className="rounded-md border border-border px-2 py-1 text-xs">v0.1.0</span>
        </Row>
      </Section>

      <Section
        title="Session"
        description="Deconnecte le launcher. Tu devras te reauthentifier avec Microsoft pour relancer le jeu."
      >
        <Row label="Deconnexion" hint="Supprime les tokens locaux et revoque la session API.">
          <button
            type="button"
            onClick={handleLogout}
            disabled={loggingOut}
            className="flex h-9 items-center gap-2 rounded-md border border-danger/40 bg-danger/10 px-3 text-xs font-medium text-danger transition hover:bg-danger/20 disabled:opacity-60"
          >
            {loggingOut ? (
              <Loader2 className="h-3 w-3 animate-spin" />
            ) : (
              <LogOut className="h-3 w-3" />
            )}
            Se déconnecter
          </button>
        </Row>
      </Section>
    </>
  );
}

function ConnectionsTab() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const [busy, setBusy] = useState<"linking" | "unlinking" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [waitingForCallback, setWaitingForCallback] = useState(false);
  const pollRef = useRef<number | null>(null);

  // Stoppe le polling si on quitte la page (ou si l'etat user change).
  useEffect(() => {
    return () => {
      if (pollRef.current) {
        window.clearInterval(pollRef.current);
        pollRef.current = null;
      }
    };
  }, []);

  function startPolling() {
    if (pollRef.current) return;
    let elapsed = 0;
    const intervalMs = 2500;
    const timeoutMs = 5 * 60_000;
    pollRef.current = window.setInterval(async () => {
      elapsed += intervalMs;
      try {
        const fresh = await refreshMe();
        if (fresh.discord) {
          setUser(fresh);
          stopPolling();
          setWaitingForCallback(false);
        }
      } catch {
        // Reseau en vol -> on retente au prochain tick.
      }
      if (elapsed >= timeoutMs) {
        stopPolling();
        setWaitingForCallback(false);
        setError(
          "Aucune liaison détectée après 5 min. Si tu as validé côté Discord, clique sur Rafraîchir.",
        );
      }
    }, intervalMs);
  }

  function stopPolling() {
    if (pollRef.current) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }

  async function handleLink() {
    setError(null);
    setBusy("linking");
    try {
      await startDiscordLink();
      setWaitingForCallback(true);
      startPolling();
    } catch (err) {
      setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
    } finally {
      setBusy(null);
    }
  }

  async function handleUnlink() {
    setError(null);
    setBusy("unlinking");
    try {
      await unlinkDiscord();
      const fresh = await refreshMe();
      setUser(fresh);
    } catch (err) {
      setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
    } finally {
      setBusy(null);
    }
  }

  async function handleManualRefresh() {
    setError(null);
    try {
      const fresh = await refreshMe();
      setUser(fresh);
      if (fresh.discord) {
        stopPolling();
        setWaitingForCallback(false);
      }
    } catch (err) {
      setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
    }
  }

  const linked = user?.discord ?? null;

  return (
    <Section
      title="Comptes lies"
      description="Discord est obligatoire pour postuler en whitelist. Steam et Twitch arrivent en v1.0.5+."
    >
      <Row label="Microsoft" hint="Compte principal de connexion. Non deliable.">
        <span className="rounded-md bg-success/10 px-2 py-1 text-xs font-medium text-success">
          Lie
        </span>
      </Row>

      <Row
        label="Discord"
        hint={
          linked
            ? `Lie a @${linked.username} (ID ${linked.userId}) depuis le ${new Date(linked.linkedAt).toLocaleDateString(
                "fr-FR",
                { day: "2-digit", month: "long", year: "numeric" },
              )}.`
            : "A relier avant la candidature whitelist."
        }
      >
        {linked ? (
          <button
            type="button"
            onClick={handleUnlink}
            disabled={busy !== null}
            className="flex h-9 items-center gap-2 rounded-md border border-border bg-background px-3 text-xs font-medium hover:border-danger/40 hover:text-danger disabled:opacity-60"
          >
            {busy === "unlinking" ? (
              <Loader2 className="h-3 w-3 animate-spin" />
            ) : (
              <Unlink className="h-3 w-3" />
            )}
            Delier
          </button>
        ) : (
          <button
            type="button"
            onClick={handleLink}
            disabled={busy !== null || waitingForCallback}
            className="flex h-9 items-center gap-2 rounded-md bg-accent px-3 text-xs font-medium text-white transition hover:bg-accent-hover disabled:opacity-60"
          >
            {busy === "linking" ? (
              <Loader2 className="h-3 w-3 animate-spin" />
            ) : (
              <Link2 className="h-3 w-3" />
            )}
            Lier Discord
          </button>
        )}
      </Row>

      {waitingForCallback && (
        <div className="rounded-md border border-accent/40 bg-accent/5 p-3 text-xs">
          <p className="font-medium text-accent">
            En attente de la validation Discord...
          </p>
          <p className="mt-1 text-foreground-subtle">
            Une page Discord vient de s'ouvrir dans ton navigateur. Autorise
            l'application Reborn puis reviens ici. Le lien apparaitra
            automatiquement.
          </p>
          <button
            type="button"
            onClick={handleManualRefresh}
            className="mt-2 inline-flex items-center gap-1 rounded-md border border-border bg-background px-2 py-1 text-[11px] font-medium hover:border-accent/40"
          >
            Rafraichir manuellement
          </button>
        </div>
      )}

      {error && (
        <div className="flex items-start gap-2 rounded-md border border-danger/40 bg-danger/10 p-3 text-xs text-danger">
          <AlertTriangle className="mt-0.5 h-3 w-3 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <Row label="Steam" hint="Optionnel. Donne un signal de confiance pour la moderation.">
        <button
          type="button"
          disabled
          className="rounded-md border border-dashed border-border px-3 py-1 text-xs text-foreground-subtle"
        >
          v1.0.5
        </button>
      </Row>
    </Section>
  );
}

function GameTab() {
  const [prefs, setPrefsState] = useState<Preferences | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [savingState, setSavingState] = useState<"idle" | "saving" | "saved">("idle");

  useEffect(() => {
    let cancelled = false;
    getPrefs()
      .then((p) => {
        if (!cancelled) setPrefsState(p);
      })
      .catch((err) => {
        if (!cancelled)
          setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function update(patch: Partial<Preferences>) {
    if (!prefs) return;
    const next = { ...prefs, ...patch };
    setPrefsState(next);
    setSavingState("saving");
    try {
      await setPrefs(next);
      setSavingState("saved");
      setTimeout(() => setSavingState("idle"), 1500);
    } catch (err) {
      setError(typeof err === "string" ? err : (err as { message?: string }).message ?? "Erreur");
      setSavingState("idle");
    }
  }

  if (error)
    return (
      <div className="rounded-md border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
        {error}
      </div>
    );
  if (!prefs)
    return (
      <div className="flex items-center gap-2 text-sm text-foreground-subtle">
        <Loader2 className="h-4 w-4 animate-spin" />
        Chargement...
      </div>
    );

  return (
    <>
      <Section title="Performance" description="Reglages JVM et de fenetre. Pris en compte au prochain lancement.">
        <Row label="Mémoire allouée à Java" hint={`${prefs.ramMb} Mo (recommandé : 4096 Mo)`}>
          <input
            type="range"
            min={2048}
            max={16384}
            step={512}
            value={prefs.ramMb}
            onChange={(e) => update({ ramMb: Number(e.target.value) })}
            className="w-48 accent-accent"
          />
        </Row>
        <Row label="Resolution" hint="Taille de la fenetre Minecraft a l'ouverture.">
          <select
            value={`${prefs.width}x${prefs.height}`}
            onChange={(e) => {
              const [w, h] = e.target.value.split("x").map(Number);
              update({ width: w, height: h });
            }}
            className="h-9 rounded-md border border-border bg-background px-2 text-sm"
          >
            <option value="1280x720">HD 720p (1280×720)</option>
            <option value="1920x1080">FullHD 1080p (1920×1080)</option>
            <option value="2560x1440">2K (2560×1440)</option>
            <option value="3840x2160">4K (3840×2160)</option>
          </select>
        </Row>
      </Section>

      <Section title="Comportement" description="Options activees par defaut au lancement du jeu.">
        <Row label="Auto-connect au serveur" hint="Saute le menu principal et tente la connexion direct.">
          <Toggle checked={prefs.autoConnect} onChange={(v) => update({ autoConnect: v })} />
        </Row>
        <Row label="Discord Rich Presence" hint="Affiche ton statut RP dans Discord pendant le jeu.">
          <Toggle
            checked={prefs.discordRichPresence}
            onChange={(v) => update({ discordRichPresence: v })}
          />
        </Row>
        <Row label="Langue">
          <select
            value={prefs.language}
            onChange={(e) => update({ language: e.target.value })}
            className="h-9 rounded-md border border-border bg-background px-2 text-sm"
          >
            <option value="fr">Francais</option>
            <option value="en">English (a venir)</option>
          </select>
        </Row>
      </Section>

      {savingState !== "idle" && (
        <div className="flex items-center gap-2 text-xs text-foreground-subtle">
          {savingState === "saving" ? (
            <>
              <Loader2 className="h-3 w-3 animate-spin" /> Enregistrement...
            </>
          ) : (
            <>
              <Check className="h-3 w-3 text-success" /> Enregistre
            </>
          )}
        </div>
      )}
    </>
  );
}

function Toggle({
  checked,
  onChange,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className={cn(
        "relative h-6 w-11 rounded-full transition",
        checked ? "bg-accent" : "bg-border",
      )}
    >
      <span
        className={cn(
          "absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform",
          checked ? "translate-x-5" : "translate-x-0.5",
        )}
      />
    </button>
  );
}
