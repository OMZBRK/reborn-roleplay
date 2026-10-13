import { useEffect, useState, type ReactNode } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { useNavigate } from "react-router";
import { AlertCircle, Check, Loader2 } from "lucide-react";
import pkg from "../../package.json";
import { useAuthStore } from "../stores/auth-store";
import type { LauncherUser } from "../stores/auth-store";
import {
  asAuthError,
  devLogin,
  explainAuthError,
  forgetAccount,
  loginWithMicrosoft,
  loginWithSavedAccount,
  logout,
} from "../lib/auth";
import { MicrosoftLoginButton } from "../components/login/MicrosoftLoginButton";
import { SavedAccountsCarousel } from "../components/login/SavedAccountsCarousel";
import { MinecraftHead } from "../components/login/MinecraftHead";
import { DiscordGlyph } from "../components/home/FooterMega";
import { useDiscordLink } from "../hooks/use-discord-link";
import type { SavedAccount } from "../lib/types";
import {
  APERCU_ACCOUNTS,
  APERCU_DISCORD,
  APERCU_USER,
  readApercu,
  type ApercuPhase,
} from "../lib/login-preview";
import konohaBg from "../assets/login/konoha_vrai.jpg";

// Écran de connexion présenté en carte d'accueil à deux étapes (façon Ketto) :
//   1. Compte Microsoft → auth_login_microsoft / auth_login_with_saved_account
//   2. Compte Discord   → discord_link_start + polling auth_me (useDiscordLink,
//      même logique que Paramètres > Connexions)
// Les deux faits, suite normale : whitelist pour un PLAYER, accueil sinon.
// App.tsx renvoie ici tout user authentifié sans Discord lié.

const EASE_OUT_EXPO: [number, number, number, number] = [0.16, 1, 0.3, 1];

type Phase = "microsoft" | "discord" | "attente" | "fini";

export function Login() {
  const navigate = useNavigate();
  // Aperçu DEV-ONLY (?apercu=etape1|etape2|attente|fini) pour les captures
  // hors Tauri ; toujours null en release.
  const [pv, setPv] = useState<ApercuPhase | null>(readApercu);

  const storeUser = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isLoading = useAuthStore((s) => s.isLoading);
  const authError = useAuthStore((s) => s.error);
  const storeAccounts = useAuthStore((s) => s.savedAccounts);
  const setSession = useAuthStore((s) => s.setSession);
  const setLoading = useAuthStore((s) => s.setLoading);
  const setError = useAuthStore((s) => s.setError);
  const addSavedAccount = useAuthStore((s) => s.addSavedAccount);
  const removeSavedAccount = useAuthStore((s) => s.removeSavedAccount);
  const setDiscordGateSkipped = useAuthStore((s) => s.setDiscordGateSkipped);
  const discord = useDiscordLink();

  const user: LauncherUser | null = pv
    ? pv === "etape1"
      ? null
      : { ...APERCU_USER, discord: pv === "fini" ? APERCU_DISCORD : null }
    : storeUser;
  const authed = pv ? pv !== "etape1" : isAuthenticated && !!storeUser;
  const accounts = pv ? APERCU_ACCOUNTS : storeAccounts;
  const waiting = pv ? pv === "attente" : discord.waiting;

  const phase: Phase = !authed
    ? "microsoft"
    : user?.discord
    ? "fini"
    : waiting
    ? "attente"
    : "discord";
  const error = phase === "microsoft" ? authError : discord.error;
  const busy = !pv && (isLoading || discord.linking);

  const btnState: "idle" | "loading" | "error" = busy ? "loading" : authError ? "error" : "idle";

  // Les deux comptes faits → suite normale.
  const destination = user?.role === "PLAYER" ? "/whitelist" : "/home";
  useEffect(() => {
    if (phase !== "fini" || pv) return;
    const t = window.setTimeout(() => navigate(destination, { replace: true }), 1400);
    return () => window.clearTimeout(t);
  }, [phase, pv, destination, navigate]);

  async function runInteractiveLogin() {
    if (pv) return setPv("etape2");
    setLoading(true);
    setError(null);
    try {
      const session = await loginWithMicrosoft();
      await persistAccount(session.user, addSavedAccount);
      setSession(session);
    } catch (err) {
      const parsed = asAuthError(err);
      setError(parsed.kind === "user_canceled" ? null : explainAuthError(parsed));
    } finally {
      setLoading(false);
    }
  }

  async function handleDevLogin() {
    setLoading(true);
    setError(null);
    try {
      const session = await devLogin("OMZ");
      await persistAccount(session.user, addSavedAccount);
      setSession(session);
    } catch (err) {
      setError(explainAuthError(asAuthError(err)));
    } finally {
      setLoading(false);
    }
  }

  async function handleRemoveSaved(account: SavedAccount) {
    if (pv) return;
    // Best-effort : slots keyring per-uuid (Rust) puis entrée plugin-store.
    try {
      await forgetAccount(account.minecraftUuid);
    } catch {
      // Slots déjà vides ou keyring inaccessible : on retire quand même la puce.
    }
    await removeSavedAccount(account.pseudo);
  }

  async function handlePickSaved(account: SavedAccount) {
    if (pv) return setPv("etape2");
    setLoading(true);
    setError(null);
    try {
      const session = await loginWithSavedAccount(account.minecraftUuid);
      await persistAccount(session.user, addSavedAccount);
      setSession(session);
    } catch (err) {
      const parsed = asAuthError(err);
      if (
        parsed.kind === "no_stored_credentials" ||
        parsed.kind === "stored_credentials_expired"
      ) {
        // Pas de refresh utilisable → OAuth interactif.
        setLoading(false);
        await runInteractiveLogin();
        return;
      }
      setError(explainAuthError(parsed));
    } finally {
      setLoading(false);
    }
  }

  async function handleSwitchAccount() {
    if (pv) return setPv("etape1");
    discord.cancel();
    discord.setError(null);
    try {
      await logout();
    } finally {
      setSession(null);
    }
  }

  return (
    <div className="rb-welcome">
      <div className="rb-welcome-bg" style={{ backgroundImage: `url(${konohaBg})` }} aria-hidden />
      <div className="rb-welcome-veil" aria-hidden />

      <div className="rb-welcome-center">
        <motion.section
          className="rb-welcome-card no-drag"
          initial={{ opacity: 0, y: 16, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.5, ease: EASE_OUT_EXPO }}
        >
          <div className="rb-welcome-mark">R</div>
          <h1 className="rb-welcome-title">Bienvenue sur Reborn</h1>
          <p className="rb-welcome-sub">
            {phase === "fini" ? "Tout est prêt. Bonne aventure." : "Comptes Microsoft et Discord requis."}
          </p>

          <ol className="rb-welcome-steps">
            <StepRow
              n={1}
              state={phase === "microsoft" ? "current" : "done"}
              title="Compte Microsoft"
              hint="Minecraft Java doit être sur ce compte"
              done={
                user && (
                  <span className="rb-welcome-done">
                    <MinecraftHead seed={user.minecraftUuid} size={16} />
                    <span className="rb-welcome-done-name">{user.minecraftUsername}</span>
                  </span>
                )
              }
            />
            <StepRow
              n={2}
              state={phase === "fini" ? "done" : phase === "microsoft" ? "todo" : "current"}
              title="Compte Discord"
              hint="Nécessaire pour rejoindre le serveur"
              done={
                user?.discord && (
                  <span className="rb-welcome-done">
                    <DiscordGlyph className="h-3.5 w-3.5 text-[#8b93f8]" />
                    <span className="rb-welcome-done-name">@{user.discord.username}</span>
                  </span>
                )
              }
            />
          </ol>

          <div className="rb-welcome-action">
            <AnimatePresence mode="wait" initial={false}>
              {phase === "microsoft" && (
                <Fade key="ms">
                  <MicrosoftLoginButton state={btnState} onClick={runInteractiveLogin} />
                </Fade>
              )}
              {phase === "discord" && (
                <Fade key="dc">
                  <button
                    type="button"
                    className="rb-welcome-cta"
                    onClick={() => (pv ? setPv("attente") : discord.link())}
                    disabled={busy}
                  >
                    {busy ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <DiscordGlyph className="h-[18px] w-[18px]" />
                    )}
                    <span>Lier mon compte Discord</span>
                  </button>
                </Fade>
              )}
              {phase === "attente" && (
                <Fade key="wait">
                  <div className="rb-welcome-wait">
                    <div className="rb-welcome-wait-line">
                      <Loader2 className="h-4 w-4 animate-spin text-[var(--color-accent-bright)]" />
                      <span>En attente de Discord…</span>
                    </div>
                    <p className="rb-welcome-wait-hint">
                      Autorise Reborn dans ton navigateur, on s'occupe du reste.
                    </p>
                    <div className="rb-welcome-wait-links">
                      <button
                        type="button"
                        className="rb-welcome-link"
                        onClick={() => !pv && discord.reopen()}
                      >
                        Rouvrir la page
                      </button>
                      <span aria-hidden>·</span>
                      <button
                        type="button"
                        className="rb-welcome-link is-muted"
                        onClick={() => (pv ? setPv("etape2") : discord.cancel())}
                      >
                        Annuler
                      </button>
                    </div>
                  </div>
                </Fade>
              )}
              {phase === "fini" && (
                <Fade key="ok">
                  <button
                    type="button"
                    className="rb-welcome-cta is-done"
                    onClick={() => !pv && navigate(destination, { replace: true })}
                  >
                    <Check className="h-4 w-4" strokeWidth={3} />
                    <span>Entrer dans Reborn</span>
                  </button>
                </Fade>
              )}
            </AnimatePresence>

            {error && (
              <motion.p
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                className="rb-welcome-error"
              >
                <AlertCircle className="mt-px h-3.5 w-3.5 flex-shrink-0" />
                <span>{error}</span>
              </motion.p>
            )}
          </div>
        </motion.section>

        {/* Sous la carte, discret : comptes enregistrés (étape 1) ou
            changement de compte (étape 2). */}
        <div className="rb-welcome-below no-drag">
          {phase === "microsoft" && (
            <SavedAccountsCarousel
              accounts={accounts}
              onPick={handlePickSaved}
              onRemove={handleRemoveSaved}
            />
          )}
          {(phase === "discord" || phase === "attente") && (
            <button type="button" className="rb-welcome-link is-muted" onClick={handleSwitchAccount}>
              Ce n'est pas toi ? Changer de compte Microsoft
            </button>
          )}

          {import.meta.env.DEV && !pv && phase === "microsoft" && (
            <button type="button" className="rb-welcome-dev" onClick={handleDevLogin} disabled={busy}>
              [DEV] Connexion sans Microsoft
            </button>
          )}
          {import.meta.env.DEV && !pv && (phase === "discord" || phase === "attente") && (
            <button type="button" className="rb-welcome-dev" onClick={() => setDiscordGateSkipped(true)}>
              [DEV] Passer l'étape Discord
            </button>
          )}
        </div>
      </div>

      <p className="rb-welcome-foot">v{pkg.version} — Non affilié à Microsoft, Mojang ou Discord</p>
    </div>
  );
}

function StepRow({
  n,
  state,
  title,
  hint,
  done,
}: {
  n: number;
  state: "todo" | "current" | "done";
  title: string;
  hint: string;
  done?: ReactNode;
}) {
  return (
    <li className={`rb-welcome-step is-${state}`}>
      <span className="rb-welcome-num">
        {state === "done" ? <Check className="h-3.5 w-3.5" strokeWidth={3} /> : n}
      </span>
      <span className="rb-welcome-step-text">
        <span className="rb-welcome-step-title">{title}</span>
        {state === "done" && done ? done : <span className="rb-welcome-step-hint">{hint}</span>}
      </span>
    </li>
  );
}

function Fade({ children }: { children: ReactNode }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -6 }}
      transition={{ duration: 0.22, ease: EASE_OUT_EXPO }}
      className="flex w-full flex-col items-center"
    >
      {children}
    </motion.div>
  );
}

async function persistAccount(
  user: LauncherUser,
  add: (a: SavedAccount) => Promise<void>,
): Promise<void> {
  try {
    await add({
      pseudo: user.minecraftUsername,
      lastSeen: new Date().toISOString(),
      seed: user.minecraftUuid,
      minecraftUuid: user.minecraftUuid,
    });
  } catch {
    // Écriture plugin-store en échec : on ne bloque pas la connexion.
  }
}
