import { invoke } from "./tauri";
import type { LauncherUser } from "../stores/auth-store";

/** Reponse Rust de auth_login_microsoft / auth_resume_session. */
export type AuthSession = {
  user: LauncherUser;
  accessToken: string;
};

/** Forme serialisee de l'erreur Rust (cf auth/error.rs). */
export type AuthErrorPayload = {
  kind:
    | "config"
    | "http"
    | "io"
    | "microsoft_response"
    | "xbox"
    | "xsts"
    | "no_minecraft_license"
    | "user_canceled"
    | "timeout"
    | "storage"
    | "internal";
  message: string;
  code?: string | null;
};

export async function loginWithMicrosoft(): Promise<AuthSession> {
  return invoke<AuthSession>("auth_login_microsoft");
}

/** Dev-only : login factice (cf docs/adr/0001-...). */
export async function devLogin(username: string): Promise<AuthSession> {
  return invoke<AuthSession>("auth_dev_login", { username });
}

export async function resumeSession(): Promise<AuthSession | null> {
  return invoke<AuthSession | null>("auth_resume_session");
}

export async function logout(): Promise<void> {
  await invoke<void>("auth_logout");
}

/** Convertit l'erreur opaque renvoyee par invoke() en payload type. */
export function asAuthError(err: unknown): AuthErrorPayload {
  if (err && typeof err === "object" && "kind" in err && "message" in err) {
    return err as AuthErrorPayload;
  }
  return {
    kind: "internal",
    message: typeof err === "string" ? err : "Erreur inconnue.",
    code: null,
  };
}

/** Texte affichable selon le type d'erreur. */
export function explainAuthError(err: AuthErrorPayload): string {
  switch (err.kind) {
    case "user_canceled":
      return "Connexion annulee. Reessaie quand tu veux.";
    case "no_minecraft_license":
      return "Aucune licence Minecraft Java n'est associee a ce compte Microsoft.";
    case "xsts":
      return err.message;
    case "xbox":
      return `Xbox Live a refuse l'authentification. ${err.message}`;
    case "config":
      return "Le launcher n'est pas configure (Client ID Microsoft manquant). Contacte le staff.";
    case "timeout":
      return "Aucune redirection Microsoft recue. Verifie que tu as bien valide la connexion.";
    case "http":
    case "io":
      return "Probleme reseau pendant la connexion. Verifie ta connexion Internet.";
    default:
      return err.message;
  }
}
