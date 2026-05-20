import { motion } from "framer-motion";
import { useNavigate } from "react-router";
import { AlertCircle } from "lucide-react";
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
} from "../lib/auth";
import { MicrosoftLoginButton } from "../components/login/MicrosoftLoginButton";
import { SavedAccountsCarousel } from "../components/login/SavedAccountsCarousel";
import type { SavedAccount } from "../lib/types";

const EASE_OUT_EXPO: [number, number, number, number] = [0.16, 1, 0.3, 1];

export function Login() {
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const isLoading = useAuthStore((s) => s.isLoading);
  const authError = useAuthStore((s) => s.error);
  const savedAccounts = useAuthStore((s) => s.savedAccounts);
  const setLoading = useAuthStore((s) => s.setLoading);
  const setError = useAuthStore((s) => s.setError);
  const addSavedAccount = useAuthStore((s) => s.addSavedAccount);
  const removeSavedAccount = useAuthStore((s) => s.removeSavedAccount);

  const btnState: "idle" | "loading" | "error" = isLoading
    ? "loading"
    : authError
    ? "error"
    : "idle";

  async function runInteractiveLogin() {
    setLoading(true);
    setError(null);
    try {
      const session = await loginWithMicrosoft();
      await persistAccount(session.user, addSavedAccount);
      setSession(session);
      navigate("/home", { replace: true });
    } catch (err) {
      const parsed = asAuthError(err);
      if (parsed.kind === "user_canceled") {
        setError(null);
        return;
      }
      setError(explainAuthError(parsed));
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
      navigate("/home", { replace: true });
    } catch (err) {
      const parsed = asAuthError(err);
      setError(explainAuthError(parsed));
    } finally {
      setLoading(false);
    }
  }

  async function handleRemoveSaved(account: SavedAccount) {
    // Best-effort : on supprime d'abord les slots keyring per-uuid (Rust),
    // puis l'entree carousel (plugin-store). Si le keyring fail (rare), on
    // retire quand meme la carte du UI — sinon l'utilisateur ne peut plus
    // s'en debarrasser.
    try {
      await forgetAccount(account.minecraftUuid);
    } catch {
      // Slots deja vides ou keyring inaccessible : on continue.
    }
    await removeSavedAccount(account.pseudo);
  }

  async function handlePickSaved(account: SavedAccount) {
    setLoading(true);
    setError(null);
    try {
      const session = await loginWithSavedAccount(account.minecraftUuid);
      await persistAccount(session.user, addSavedAccount);
      setSession(session);
      navigate("/home", { replace: true });
    } catch (err) {
      const parsed = asAuthError(err);
      if (
        parsed.kind === "no_stored_credentials" ||
        parsed.kind === "stored_credentials_expired"
      ) {
        // Fallback : pas de refresh utilisable → OAuth interactif. La
        // fenetre MS s'ouvrira ; passe `setLoading(false)` avant pour que
        // l'UI affiche bien "Connexion…" mais permette au flow interactif
        // de prendre le relais sans concurrence.
        setLoading(false);
        await runInteractiveLogin();
        return;
      }
      setError(explainAuthError(parsed));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="reborn-radial-bg-strong reborn-pattern-overlay relative flex h-full w-full items-center justify-center px-8">
      <div className="flex w-full max-w-lg flex-col items-center gap-8">
        <motion.div
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.05, ease: EASE_OUT_EXPO }}
          className="flex flex-col items-center text-center"
        >
          <h1
            className="font-display text-6xl font-semibold tracking-[0.08em] text-[var(--color-foreground)]"
            style={{
              filter:
                "drop-shadow(0 0 24px rgba(59,91,219,0.4)) drop-shadow(0 0 6px rgba(255,255,255,0.12))",
            }}
          >
            REBORN
          </h1>
          <p className="mt-2 text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
            Roleplay · Naruto Edition
          </p>
        </motion.div>

        {savedAccounts.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.15, ease: EASE_OUT_EXPO }}
            className="w-full"
          >
            <SavedAccountsCarousel
              accounts={savedAccounts}
              onPick={handlePickSaved}
              onAdd={runInteractiveLogin}
              onRemove={handleRemoveSaved}
            />
          </motion.div>
        )}

        <motion.div
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.25, ease: EASE_OUT_EXPO }}
          className="flex w-full max-w-sm flex-col gap-3"
        >
          <MicrosoftLoginButton state={btnState} onClick={runInteractiveLogin} />

          {authError && (
            <motion.div
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              className="no-drag flex items-start gap-2 rounded-md border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)]/40 p-3 text-sm text-[var(--color-danger)]"
            >
              <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
              <p>{authError}</p>
            </motion.div>
          )}

          {import.meta.env.DEV && (
            <button
              type="button"
              onClick={handleDevLogin}
              disabled={isLoading}
              className="no-drag mt-1 flex h-9 w-full items-center justify-center gap-2 rounded-md border border-dashed border-[var(--color-warning)]/50 px-3 text-xs text-[var(--color-warning)] transition-colors hover:bg-[var(--color-warning-soft)] disabled:opacity-60"
              title="Bypass Microsoft — disponible uniquement en dev"
            >
              [DEV] Connexion sans Microsoft
            </button>
          )}
        </motion.div>

        <motion.p
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.35, ease: EASE_OUT_EXPO }}
          className="mt-4 text-xs text-[var(--color-foreground-muted)]"
        >
          v{pkg.version} — Non affilié à Microsoft ou Mojang
        </motion.p>
      </div>
    </div>
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
    // Si l'ecriture du plugin-store echoue (disque plein, permissions), on
    // ne bloque pas la connexion — la liste se reconstruira au prochain
    // login reussi.
  }
}
