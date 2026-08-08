import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { motion, AnimatePresence } from "framer-motion";
import {
  AlertTriangle,
  Bell,
  Check,
  Cpu,
  Gamepad2,
  Globe,
  Info,
  Link2,
  Loader2,
  LogOut,
  MemoryStick,
  Monitor,
  RefreshCw,
  ShieldCheck,
  User2,
  Unlink,
  Wand2,
  Zap,
} from "lucide-react";
import pkg from "../../package.json";
import { useAuthStore } from "../stores/auth-store";
import { useSettingsStore } from "../stores/settings-store";
import { getPrefs, setPrefs, type Preferences } from "../lib/prefs";
import { logout, refreshMe, startDiscordLink, unlinkDiscord } from "../lib/auth";
import { getSystemSpecs, type SystemSpecs } from "../lib/system";
import { cn } from "../lib/cn";
import { mapRole, ROLE_META } from "../components/sidebar/role";

const TABS = [
  { id: "profile", label: "Profil", hint: "Identite & role", icon: User2 },
  { id: "account", label: "Compte", hint: "Identifiants & session", icon: ShieldCheck },
  { id: "connections", label: "Connexions", hint: "Discord, Steam, Twitch", icon: Link2 },
  { id: "game", label: "Jeu", hint: "Installation & fichiers", icon: Gamepad2 },
  { id: "notif", label: "Notifications", hint: "Push, email, alertes", icon: Bell },
  { id: "about", label: "À propos", hint: "Version & support", icon: Info },
] as const;
type TabId = (typeof TABS)[number]["id"];

export function Settings() {
  const [tab, setTab] = useState<TabId>("profile");

  return (
    <div className="reborn-settings-page">
      <div className="reborn-settings-scroll">
        <div className="reborn-settings-container">
          <Header />

          <div className="reborn-settings-body">
            <nav className="reborn-settings-sidebar" aria-label="Catégories">
              {TABS.map((t) => {
                const Icon = t.icon;
                const active = tab === t.id;
                return (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => setTab(t.id)}
                    data-active={active || undefined}
                    className="reborn-settings-cat"
                  >
                    <span className="reborn-settings-cat-icon">
                      <Icon className="h-4 w-4" />
                    </span>
                    <span className="reborn-settings-cat-meta">
                      <span className="reborn-settings-cat-label">{t.label}</span>
                      <span className="reborn-settings-cat-hint">{t.hint}</span>
                    </span>
                  </button>
                );
              })}
            </nav>

            <div className="reborn-settings-content">
              <AnimatePresence mode="wait">
                <motion.div
                  key={tab}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -4 }}
                  transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
                >
                  {tab === "profile" && <ProfileTab />}
                  {tab === "account" && <AccountTab />}
                  {tab === "connections" && <ConnectionsTab />}
                  {tab === "game" && <GameTab />}
                  {tab === "notif" && <NotificationsTab />}
                  {tab === "about" && <AboutTab />}
                </motion.div>
              </AnimatePresence>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Header() {
  return (
    <header className="relative flex items-end justify-between gap-6 border-b border-border/60 pb-5">
      <div>
        <p className="text-[10px] font-semibold uppercase tracking-[0.28em] text-foreground-muted">
          Reborn Roleplay
        </p>
        <h1
          className="mt-1.5 font-display"
          style={{
            fontSize: 32,
            lineHeight: 1.05,
            letterSpacing: "0.03em",
            color: "var(--color-foreground)",
          }}
        >
          Paramètres
        </h1>
      </div>
      <div className="hidden text-right sm:block">
        <p className="text-[10px] font-semibold uppercase tracking-[0.24em] text-foreground-muted">
          Identité visuelle
        </p>
        <p className="mt-1 text-[11.5px] text-foreground-subtle">
          Direction Akatsuki <span className="mx-1 opacity-40">·</span> v3
        </p>
      </div>
    </header>
  );
}

function Card({
  title,
  description,
  icon,
  accent,
  children,
}: {
  title: string;
  description?: string;
  icon?: React.ReactNode;
  accent?: "default" | "danger";
  children: React.ReactNode;
}) {
  const danger = accent === "danger";
  return (
    <section
      className={cn(
        "group relative mb-5 overflow-hidden rounded-[14px] border bg-surface/80 transition-colors",
        danger
          ? "border-danger/30 hover:border-danger/50"
          : "border-border hover:border-border-strong",
      )}
    >
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-px opacity-60"
        style={{
          background: danger
            ? "linear-gradient(90deg, transparent, rgba(239,68,68,0.5) 50%, transparent)"
            : "linear-gradient(90deg, transparent, rgba(160, 24, 43, 0.45) 50%, transparent)",
        }}
      />
      <div className="flex items-start gap-3 border-b border-border/60 px-6 pb-4 pt-5">
        {icon && (
          <div
            className={cn(
              "flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-md",
              danger ? "bg-danger/10 text-danger" : "bg-accent/12 text-accent",
            )}
          >
            {icon}
          </div>
        )}
        <div className="min-w-0 flex-1">
          <h2
            className="font-display text-[18px] tracking-wide text-foreground"
            style={{ letterSpacing: "0.04em" }}
          >
            {title}
          </h2>
          {description && (
            <p className="mt-1 text-xs leading-relaxed text-foreground-subtle">{description}</p>
          )}
        </div>
      </div>
      <div className="divide-y divide-border/40">{children}</div>
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
    <div className="flex items-center justify-between gap-6 px-6 py-4">
      <div className="min-w-0 flex-1">
        <p className="text-[13px] font-medium leading-tight text-foreground">{label}</p>
        {hint && <p className="mt-1 text-[11.5px] leading-snug text-foreground-subtle">{hint}</p>}
      </div>
      <div className="flex-shrink-0">{children}</div>
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  PROFILE
// ──────────────────────────────────────────────────────

function ProfileTab() {
  const user = useAuthStore((s) => s.user);
  const pseudo = user?.displayName ?? user?.minecraftUsername ?? "Joueur";
  const initial = pseudo.charAt(0).toUpperCase();
  const roleType = mapRole(user?.role);
  const roleMeta = ROLE_META[roleType];

  return (
    <>
      {/* Hero card horizontal — avatar a gauche, infos a droite. Plus
          compact que la version centree precedente, plus dense en info. */}
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
              className="font-display"
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
        title="Identite"
        description="Visible dans le launcher, in-game et sur le panel staff."
        icon={<User2 className="h-4 w-4" />}
      >
        <Row label="Pseudo Minecraft" hint="Recupere depuis ton compte Microsoft. Modifiable uniquement chez Mojang.">
          <span className="text-sm font-medium">{user?.minecraftUsername ?? "—"}</span>
        </Row>
        <Row label="Nom d'affichage" hint="Visible dans les DM et l'historique RP.">
          <span className="text-sm">{user?.displayName ?? user?.minecraftUsername ?? "—"}</span>
        </Row>
        <Row label="Role" hint="Definit tes permissions in-game et sur le panel.">
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

// ──────────────────────────────────────────────────────
//  ACCOUNT
// ──────────────────────────────────────────────────────

function AccountTab() {
  const user = useAuthStore((s) => s.user);
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
    } catch {
      // Best-effort : meme si la requete echoue, on degage la session locale.
    }
    setSession(null);
    navigate("/login", { replace: true });
  }

  return (
    <>
      <Card
        title="Identifiants"
        description="Informations liees a ton compte Microsoft. Modifiables uniquement via account.microsoft.com."
        icon={<ShieldCheck className="h-4 w-4" />}
      >
        <Row label="UUID Minecraft" hint="Identifiant unique attribue par Mojang a la creation du compte.">
          <code className="rounded-md bg-background px-2.5 py-1 font-mono text-[11px] text-foreground-subtle">
            {user?.minecraftUuid ?? "—"}
          </code>
        </Row>
        <Row label="Identifiant Reborn" hint="Cle interne utilisee par les tickets et l'API.">
          <code className="rounded-md bg-background px-2.5 py-1 font-mono text-[11px] text-foreground-subtle">
            {user?.id ?? "—"}
          </code>
        </Row>
        <Row label="Version du launcher" hint="Mise a jour automatique au prochain demarrage si une nouvelle est dispo.">
          <span
            className="inline-flex items-center gap-1.5 rounded-md border border-border bg-background px-2.5 py-1 font-mono text-[11px] font-medium"
            style={{ color: "var(--color-foreground-subtle)" }}
          >
            <Zap className="h-3 w-3 text-accent" />
            v{pkg.version}
          </span>
        </Row>
      </Card>

      <Card
        title="Session"
        description="Supprime les tokens locaux et revoque la session API. Tu devras te reauthentifier avec Microsoft pour relancer le jeu."
        icon={<LogOut className="h-4 w-4" />}
        accent="danger"
      >
        <Row label="Deconnexion" hint="Action irreversible : revoque le refresh token API et vide le Keyring local.">
          <button
            type="button"
            onClick={handleLogout}
            disabled={loggingOut}
            className="inline-flex h-9 items-center gap-2 rounded-md border border-danger/40 bg-danger/10 px-4 text-xs font-semibold text-danger transition hover:bg-danger/20 hover:shadow-[0_0_18px_-6px_rgba(239,68,68,0.6)] disabled:opacity-60"
          >
            {loggingOut ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <LogOut className="h-3.5 w-3.5" />
            )}
            Se déconnecter
          </button>
        </Row>
      </Card>
    </>
  );
}

// ──────────────────────────────────────────────────────
//  CONNECTIONS
// ──────────────────────────────────────────────────────

function ConnectionsTab() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const [busy, setBusy] = useState<"linking" | "unlinking" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [waitingForCallback, setWaitingForCallback] = useState(false);
  const pollRef = useRef<number | null>(null);

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
    <>
      <Card
        title="Comptes lies"
        description="Discord est obligatoire pour postuler en whitelist. Steam et Twitch arrivent en v1.0.5+."
        icon={<Link2 className="h-4 w-4" />}
      >
        <ConnectionRow
          label="Microsoft"
          hint="Compte principal de connexion. Non deliable."
          status="linked"
          statusLabel="Lié"
          color="#16a34a"
        />

        {linked ? (
          <ConnectionRow
            label="Discord"
            hint={`Lie a @${linked.username} (ID ${linked.userId}) depuis le ${new Date(linked.linkedAt).toLocaleDateString(
              "fr-FR",
              { day: "2-digit", month: "long", year: "numeric" },
            )}.`}
            status="linked"
            statusLabel="Lié"
            color="#5865F2"
            action={
              <button
                type="button"
                onClick={handleUnlink}
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
                onClick={handleLink}
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
          hint="Optionnel. Donne un signal de confiance pour la moderation."
          status="soon"
          statusLabel="v1.0.5"
          color="#94a3b8"
        />
      </Card>

      {waitingForCallback && (
        <div className="mb-5 rounded-[12px] border border-accent/40 bg-accent/8 p-4 text-xs">
          <p className="flex items-center gap-2 font-semibold text-accent">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            En attente de la validation Discord...
          </p>
          <p className="mt-1.5 leading-relaxed text-foreground-subtle">
            Une page Discord vient de s'ouvrir dans ton navigateur. Autorise
            l'application Reborn puis reviens ici. Le lien apparaîtra
            automatiquement.
          </p>
          <button
            type="button"
            onClick={handleManualRefresh}
            className="mt-2 inline-flex items-center gap-1 rounded-md border border-border bg-background px-2.5 py-1.5 text-[11px] font-medium hover:border-accent/40"
          >
            Rafraîchir manuellement
          </button>
        </div>
      )}

      {error && (
        <div className="mb-5 flex items-start gap-2 rounded-[12px] border border-danger/40 bg-danger/10 p-4 text-xs text-danger">
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
          <span>{error}</span>
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
  action?: React.ReactNode;
}) {
  return (
    <div className="flex items-center gap-4 px-6 py-4">
      <div
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
            className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold tracking-wide"
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

// ──────────────────────────────────────────────────────
//  GAME
// ──────────────────────────────────────────────────────

function GameTab() {
  const [prefs, setPrefsState] = useState<Preferences | null>(null);
  const [specs, setSpecs] = useState<SystemSpecs | null>(null);
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
    getSystemSpecs()
      .then((s) => {
        if (!cancelled) setSpecs(s);
      })
      .catch(() => {
        /* swallow */
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
      <div className="rounded-[12px] border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger">
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

  const recommended = specs?.recommendedRamMb ?? 4096;
  const ramPercent = ((prefs.ramMb - 2048) / (16384 - 2048)) * 100;

  return (
    <>
      {specs && (
        <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <SpecCard icon={MemoryStick} label="Mémoire" value={`${(specs.totalRamMb / 1024).toFixed(1)} Go`} sub={`${specs.freeRamMb.toLocaleString()} Mo libres`} />
          <SpecCard icon={Cpu} label="Processeur" value={specs.cpuBrand} sub={`${specs.cpuCores} thread${specs.cpuCores > 1 ? "s" : ""}`} />
          <SpecCard icon={Monitor} label="Système" value={specs.osName} sub="Détecté au démarrage" />
        </div>
      )}

      <Card
        title="Performance"
        description="Réglages JVM et fenêtre. Pris en compte au prochain lancement du jeu."
        icon={<Zap className="h-4 w-4" />}
      >
        <div className="px-6 py-5">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <p className="text-[13px] font-medium text-foreground">Mémoire allouée à Java</p>
                {specs && (
                  <span className="rounded-full bg-accent/10 px-2 py-0.5 text-[10px] font-semibold tracking-wide text-accent">
                    Recommandé : {(recommended / 1024).toFixed(1)} Go
                  </span>
                )}
              </div>
              <p className="mt-1 text-[11.5px] text-foreground-subtle">
                Plus de RAM = chargement de chunks plus rapide, mais ne sert à rien au-delà de la
                moitié de ta mémoire totale.
              </p>
            </div>
            <div className="flex items-baseline gap-1">
              <span className="font-display text-2xl tabular-nums text-foreground" style={{ letterSpacing: "0.02em" }}>
                {(prefs.ramMb / 1024).toFixed(1)}
              </span>
              <span className="text-xs uppercase tracking-widest text-foreground-muted">Go</span>
            </div>
          </div>

          <div className="mt-4 relative">
            <div className="absolute inset-x-0 top-1/2 h-1.5 -translate-y-1/2 overflow-hidden rounded-full bg-border">
              <div
                className="h-full rounded-full transition-all duration-200"
                style={{
                  width: `${ramPercent}%`,
                  background: "linear-gradient(90deg, var(--color-accent) 0%, var(--color-accent-hover) 100%)",
                  boxShadow: "0 0 12px var(--color-accent-glow)",
                }}
              />
            </div>
            <input
              type="range"
              min={2048}
              max={16384}
              step={512}
              value={prefs.ramMb}
              onChange={(e) => update({ ramMb: Number(e.target.value) })}
              className="reborn-ram-slider relative z-10 w-full"
            />
            <div className="mt-2 flex justify-between text-[10px] font-medium uppercase tracking-widest text-foreground-muted">
              <span>2 Go</span>
              <span>16 Go</span>
            </div>
          </div>

          {specs && prefs.ramMb !== recommended && (
            <button
              type="button"
              onClick={() => update({ ramMb: recommended })}
              className="mt-3 inline-flex items-center gap-1.5 rounded-md border border-accent/30 bg-accent/8 px-3 py-1.5 text-[11px] font-medium text-accent transition hover:bg-accent/15 hover:shadow-[0_0_16px_-6px_var(--color-accent-glow-strong)]"
            >
              <Wand2 className="h-3 w-3" />
              Utiliser la valeur recommandée ({(recommended / 1024).toFixed(1)} Go)
            </button>
          )}
        </div>

        <Row label="Résolution" hint="Taille de la fenêtre Minecraft à l'ouverture.">
          <PrettySelect
            value={`${prefs.width}x${prefs.height}`}
            onChange={(v) => {
              const [w, h] = v.split("x").map(Number);
              update({ width: w, height: h });
            }}
            options={[
              { value: "1280x720", label: "HD 720p" },
              { value: "1920x1080", label: "FullHD 1080p" },
              { value: "2560x1440", label: "2K (1440p)" },
              { value: "3840x2160", label: "4K (2160p)" },
            ]}
          />
        </Row>
      </Card>

      <Card
        title="Comportement"
        description="Options activées par défaut au lancement du jeu."
        icon={<Gamepad2 className="h-4 w-4" />}
      >
        <Row label="Auto-connect au serveur" hint="Saute le menu principal et tente la connexion direct.">
          <Toggle checked={prefs.autoConnect} onChange={(v) => update({ autoConnect: v })} />
        </Row>
        <Row label="Discord Rich Presence" hint="Affiche ton statut RP dans Discord pendant que tu joues.">
          <Toggle
            checked={prefs.discordRichPresence}
            onChange={(v) => update({ discordRichPresence: v })}
          />
        </Row>
        <Row label="Langue" hint="L'anglais arrive en v1.1 — pour l'instant tout est en français.">
          <PrettySelect
            value={prefs.language}
            onChange={(v) => update({ language: v })}
            options={[
              { value: "fr", label: "Français" },
              { value: "en", label: "English (bientôt)" },
            ]}
            leadingIcon={Globe}
          />
        </Row>
      </Card>

      <AnimatePresence>
        {savingState !== "idle" && (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 4 }}
            className="fixed bottom-6 right-6 z-30 flex items-center gap-2 rounded-full border border-border-strong bg-surface-elevated px-4 py-2 text-xs shadow-lg"
          >
            {savingState === "saving" ? (
              <>
                <Loader2 className="h-3 w-3 animate-spin text-accent" />
                <span className="font-medium">Enregistrement…</span>
              </>
            ) : (
              <>
                <Check className="h-3 w-3 text-success" />
                <span className="font-medium">Enregistré</span>
              </>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}

function SpecCard({
  icon: Icon,
  label,
  value,
  sub,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  sub: string;
}) {
  return (
    <div className="relative overflow-hidden rounded-[12px] border border-border bg-surface/70 p-4">
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-px"
        style={{
          background:
            "linear-gradient(90deg, transparent, rgba(160, 24, 43, 0.4) 50%, transparent)",
        }}
      />
      <div className="flex items-center gap-2">
        <span className="flex h-7 w-7 items-center justify-center rounded-md bg-accent/12 text-accent">
          <Icon className="h-3.5 w-3.5" />
        </span>
        <span className="text-[10px] font-semibold uppercase tracking-[0.18em] text-foreground-muted">
          {label}
        </span>
      </div>
      <p
        className="mt-3 truncate font-display text-[18px] text-foreground"
        style={{ letterSpacing: "0.02em" }}
        title={value}
      >
        {value}
      </p>
      <p className="mt-0.5 truncate text-[11px] text-foreground-subtle">{sub}</p>
    </div>
  );
}

function PrettySelect({
  value,
  onChange,
  options,
  leadingIcon: LeadingIcon,
}: {
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
  leadingIcon?: React.ComponentType<{ className?: string }>;
}) {
  return (
    <div className="relative inline-flex items-center">
      {LeadingIcon && (
        <LeadingIcon className="pointer-events-none absolute left-3 h-3.5 w-3.5 text-foreground-subtle" />
      )}
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={cn(
          "h-9 cursor-pointer appearance-none rounded-md border border-border bg-background pr-8 text-[12px] font-medium text-foreground transition hover:border-border-strong focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/40",
          LeadingIcon ? "pl-9" : "pl-3",
        )}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <svg
        className="pointer-events-none absolute right-2.5 h-3 w-3 text-foreground-subtle"
        viewBox="0 0 12 12"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M3 4.5L6 7.5L9 4.5"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  );
}

function Toggle({
  checked,
  onChange,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  // Sizing : pill 40×22, knob 16×16. translate-x = pill_width - knob - 2*padding
  // = 40 - 16 - 6 = 18px. Knob reste integralement dans le pill, plus de
  // depassement visuel (cf depassement.png feedback utilisateur).
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className={cn(
        "relative inline-flex h-[22px] w-10 flex-shrink-0 rounded-full border transition-colors duration-200",
        checked
          ? "border-accent bg-accent"
          : "border-border-strong bg-surface-overlay hover:border-border-strong",
      )}
      aria-pressed={checked}
    >
      <span
        className={cn(
          "absolute top-[2px] h-[16px] w-[16px] rounded-full bg-white shadow-sm transition-transform duration-200",
          checked ? "translate-x-[20px]" : "translate-x-[2px]",
        )}
      />
    </button>
  );
}

// Slider compact reutilise par Perf/Audio. Affiche un pourcent ou un
// suffix custom (fps, %, etc.).
function NotificationsTab() {
  const notif = useSettingsStore((s) => s.notif);
  const setNotif = useSettingsStore((s) => s.setNotif);

  return (
    <Card
      title="Notifications"
      description="Choisis ce que tu veux recevoir et par quel canal. Persisté localement — l'envoi serveur arrivera quand l'API exposera /v1/notifications."
      icon={<Bell className="h-4 w-4" />}
    >
      <Row
        label="Whitelist"
        hint="Étapes de candidature et résultats."
      >
        <div className="flex items-center gap-2">
          <PrettySelect
            value={notif.whitelistChannel}
            onChange={(v) =>
              setNotif("whitelistChannel", v as "push" | "email" | "both")
            }
            options={[
              { value: "push", label: "Push" },
              { value: "email", label: "Email" },
              { value: "both", label: "Les deux" },
            ]}
          />
          <Toggle
            checked={notif.whitelistEnabled}
            onChange={(v) => setNotif("whitelistEnabled", v)}
          />
        </div>
      </Row>
      <Row
        label="Tickets staff"
        hint="Réponses sur tes signalements et demandes."
      >
        <Toggle
          checked={notif.ticketsEnabled}
          onChange={(v) => setNotif("ticketsEnabled", v)}
        />
      </Row>
      <Row
        label="Patch notes"
        hint="Reçois un résumé à chaque nouvelle version client."
      >
        <Toggle
          checked={notif.patchEnabled}
          onChange={(v) => setNotif("patchEnabled", v)}
        />
      </Row>
      <Row
        label="Événements RP"
        hint="Tournois, arcs narratifs et annonces in-game."
      >
        <Toggle
          checked={notif.eventsEnabled}
          onChange={(v) => setNotif("eventsEnabled", v)}
        />
      </Row>
    </Card>
  );
}

// ──────────────────────────────────────────────────────
//  À PROPOS
// ──────────────────────────────────────────────────────

function AboutTab() {
  return (
    <>
      <Card
        title="Version"
        description="État de ton installation client."
        icon={<Info className="h-4 w-4" />}
      >
        <Row label="Version du launcher">
          <code className="rounded-md bg-background px-2.5 py-1 font-mono text-[11px] text-foreground-subtle">
            v{pkg.version}
          </code>
        </Row>
        <Row label="Canal de build">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-success-soft px-3 py-1 text-[11px] font-semibold tracking-wide text-success">
            <Check className="h-3 w-3" strokeWidth={3} />
            Stable
          </span>
        </Row>
        <Row label="Mise à jour automatique" hint="Vérification toutes les 30 minutes via l'endpoint /v1/launcher/update.">
          <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-foreground-subtle">
            <RefreshCw className="h-3 w-3" />
            Active
          </span>
        </Row>
      </Card>
      <Card
        title="Support"
        description="Trouve de l'aide si quelque chose ne va pas."
        icon={<Link2 className="h-4 w-4" />}
      >
        <Row label="Ouvrir un ticket" hint="Le moyen le plus rapide d'avoir une réponse du staff.">
          <a
            href="#"
            className="inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-background px-3 text-xs font-medium text-foreground transition hover:border-accent hover:bg-surface-elevated"
            onClick={(e) => e.preventDefault()}
          >
            Voir les tickets
          </a>
        </Row>
        <Row label="Documentation" hint="Règlement complet, lore, guides RP.">
          <a
            href="#"
            className="inline-flex h-8 items-center gap-1.5 rounded-md border border-border bg-background px-3 text-xs font-medium text-foreground transition hover:border-accent hover:bg-surface-elevated"
            onClick={(e) => e.preventDefault()}
          >
            Consulter
          </a>
        </Row>
      </Card>
      <Card
        title="Crédits"
        description="Construit avec amour par l'équipe Reborn."
        icon={<User2 className="h-4 w-4" />}
      >
        <div className="grid grid-cols-2 gap-3 p-6 sm:grid-cols-4">
          {[
            { name: "OMZ", role: "Founder · Backend" },
            { name: "Hikami", role: "Design · Frontend" },
            { name: "Kazama", role: "Game systems" },
            { name: "Yumi", role: "Lore · Writing" },
          ].map((c) => (
            <div
              key={c.name}
              className="flex flex-col items-center gap-2 rounded-[12px] border border-border bg-background/60 p-3 text-center"
            >
              <div
                className="flex h-10 w-10 items-center justify-center rounded-full text-sm font-semibold text-white"
                style={{
                  background:
                    "linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-pressed) 100%)",
                }}
              >
                {c.name[0]}
              </div>
              <div className="text-[12px] font-semibold text-foreground">
                {c.name}
              </div>
              <div className="text-[10px] uppercase tracking-wider text-foreground-muted">
                {c.role}
              </div>
            </div>
          ))}
        </div>
      </Card>
    </>
  );
}
