import { motion } from "framer-motion";
import { useState } from "react";
import { useNavigate } from "react-router";
import { Loader2 } from "lucide-react";
import { useAuthStore } from "../stores/auth-store";

const LAUNCHER_VERSION = "v0.1.0";

export function Login() {
  const navigate = useNavigate();
  const setUser = useAuthStore((s) => s.setUser);
  const [authState, setAuthState] = useState<"idle" | "authenticating" | "error">("idle");

  // Maquette : pas de logique OAuth reelle, on simule.
  // Le flow Microsoft sera branche en Semaine 2 (cf §7 du plan).
  async function handleLogin() {
    setAuthState("authenticating");
    await new Promise((resolve) => setTimeout(resolve, 1200));
    setUser({
      id: "demo-user",
      minecraftUuid: "00000000-0000-0000-0000-000000000000",
      minecraftUsername: "OMZ",
      displayName: "OMZ",
      avatarUrl: null,
      role: "PLAYER",
    });
    navigate("/home", { replace: true });
  }

  return (
    <div className="grid h-screen grid-cols-1 lg:grid-cols-2">
      {/* Panneau gauche : formulaire */}
      <motion.div
        initial={{ opacity: 0, x: -20 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.5 }}
        className="relative flex flex-col items-center justify-center bg-background px-12"
      >
        <div className="absolute inset-x-0 top-0 h-10 drag-region" />

        <div className="w-full max-w-sm">
          <div className="mb-12 flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-accent">
              <span className="font-display text-xl font-bold text-white">R</span>
            </div>
            <div>
              <p className="text-xs uppercase tracking-widest text-foreground-subtle">
                Reborn
              </p>
              <p className="font-display text-lg font-semibold">Roleplay</p>
            </div>
          </div>

          <h1 className="font-display text-4xl font-semibold leading-tight">
            Bienvenue.
          </h1>
          <p className="mt-3 text-foreground-subtle">
            Connecte-toi avec ton compte Microsoft pour acceder au serveur.
          </p>

          <button
            type="button"
            onClick={handleLogin}
            disabled={authState === "authenticating"}
            className="no-drag mt-10 flex h-12 w-full items-center justify-center gap-3 rounded-lg bg-white px-4 font-medium text-black transition hover:bg-neutral-200 disabled:opacity-60"
          >
            {authState === "authenticating" ? (
              <Loader2 className="h-5 w-5 animate-spin" />
            ) : (
              <MicrosoftLogo />
            )}
            <span>
              {authState === "authenticating"
                ? "Connexion en cours..."
                : "Se connecter avec Microsoft"}
            </span>
          </button>

          <label className="no-drag mt-6 flex cursor-pointer items-center gap-2 text-sm text-foreground-subtle">
            <input type="checkbox" defaultChecked className="h-4 w-4 accent-accent" />
            Se souvenir de moi
          </label>

          <p className="mt-10 text-xs text-foreground-subtle">
            Pas de compte Microsoft ?{" "}
            <a
              href="https://signup.live.com"
              target="_blank"
              rel="noreferrer"
              className="text-accent hover:underline"
            >
              En creer un
            </a>
          </p>
        </div>

        <p className="absolute bottom-4 right-6 text-xs text-foreground-subtle">
          {LAUNCHER_VERSION}
        </p>
      </motion.div>

      {/* Panneau droit : artwork RP plein ecran */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.7 }}
        className="relative hidden overflow-hidden lg:block"
        style={{
          background:
            "radial-gradient(ellipse at 30% 20%, #1f2a4a 0%, #0a0d18 55%, #07080b 100%)",
        }}
      >
        <div
          className="absolute inset-0 opacity-50 mix-blend-screen"
          style={{
            background:
              "radial-gradient(circle at 80% 80%, rgba(59, 91, 219, 0.4), transparent 60%)",
          }}
        />
        <div className="absolute inset-0 flex flex-col justify-end p-12">
          <p className="font-display text-2xl text-white drop-shadow-lg">
            « Dans l'ombre ou la lumiere,
            <br />
            chaque ninja ecrit sa propre destinee. »
          </p>
          <p className="mt-3 text-sm uppercase tracking-widest text-foreground-subtle">
            — Reborn Roleplay
          </p>
        </div>
      </motion.div>
    </div>
  );
}

function MicrosoftLogo() {
  return (
    <svg viewBox="0 0 21 21" className="h-5 w-5" aria-hidden="true">
      <rect x="1" y="1" width="9" height="9" fill="#F25022" />
      <rect x="11" y="1" width="9" height="9" fill="#7FBA00" />
      <rect x="1" y="11" width="9" height="9" fill="#00A4EF" />
      <rect x="11" y="11" width="9" height="9" fill="#FFB900" />
    </svg>
  );
}
