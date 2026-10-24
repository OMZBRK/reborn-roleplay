import { useState } from "react";
import { useNavigate } from "react-router";
import { Loader2, LogOut, ShieldCheck, Zap } from "lucide-react";
import pkg from "../../../package.json";
import { useAuthStore } from "../../stores/auth-store";
import { logout } from "../../lib/auth";
import { Card, Row } from "./ui";

export function AccountTab() {
  const user = useAuthStore((s) => s.user);
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
    } catch (err) {
      // Best-effort : même si la révocation côté API échoue (hors ligne…),
      // on vide la session locale ; le refresh token expirera seul.
      console.warn("[settings] déconnexion côté API impossible :", err);
    }
    setSession(null);
    navigate("/login", { replace: true });
  }

  return (
    <>
      <Card
        title="Identifiants"
        description="Informations liées à ton compte Microsoft. Modifiables uniquement sur account.microsoft.com."
        icon={<ShieldCheck className="h-4 w-4" />}
      >
        <Row
          label="UUID Minecraft"
          hint="Identifiant unique attribué par Mojang à la création du compte."
        >
          <code className="select-all rounded-md bg-background px-2.5 py-1 font-mono text-[11px] text-foreground-subtle">
            {user?.minecraftUuid ?? "—"}
          </code>
        </Row>
        <Row label="Identifiant Reborn" hint="Clé interne utilisée par les tickets et l'API.">
          <code className="select-all rounded-md bg-background px-2.5 py-1 font-mono text-[11px] text-foreground-subtle">
            {user?.id ?? "—"}
          </code>
        </Row>
        <Row
          label="Version du launcher"
          hint="Mise à jour automatique au prochain démarrage quand une nouvelle version est disponible."
        >
          <span
            className="inline-flex items-center gap-1.5 rounded-md border border-border bg-background px-2.5 py-1 font-mono text-[11px] font-medium"
            style={{ color: "var(--color-foreground-subtle)" }}
          >
            <Zap className="h-3 w-3 text-accent" />v{pkg.version}
          </span>
        </Row>
      </Card>

      <Card
        title="Session"
        description="Supprime les jetons locaux et révoque la session côté serveur. Tu devras te reconnecter avec Microsoft pour relancer le jeu."
        icon={<LogOut className="h-4 w-4" />}
        accent="danger"
      >
        <Row
          label="Déconnexion"
          hint="Révoque ta session Reborn et vide les identifiants enregistrés dans Windows."
        >
          <button
            type="button"
            onClick={() => void handleLogout()}
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
