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
    | "internal"
    // Emis par auth_login_with_saved_account uniquement — le handler frontend
    // bascule sur loginWithMicrosoft() (OAuth interactif) quand on voit ces
    // deux kinds.
    | "no_stored_credentials"
    | "stored_credentials_expired";
  message: string;
  code?: string | null;
};

export async function loginWithMicrosoft(): Promise<AuthSession> {
  return invoke<AuthSession>("auth_login_microsoft");
}

/** Switch silencieux : tente de reconnecter le compte identifie par son UUID
 *  Mojang sans rouvrir la fenetre OAuth. Le handler appelant doit catcher les
 *  erreurs `no_stored_credentials` / `stored_credentials_expired` et
 *  basculer sur `loginWithMicrosoft()` (OAuth interactif). */
export async function loginWithSavedAccount(uuid: string): Promise<AuthSession> {
  return invoke<AuthSession>("auth_login_with_saved_account", { uuid });
}

/** Supprime les slots keyring per-uuid d'un compte (refresh MS + refresh
 *  API). N'affecte pas la session courante si le compte oublie est le user
 *  actif — le retrait du carousel cote frontend (removeSavedAccount du
 *  store) est complementaire. */
export async function forgetAccount(uuid: string): Promise<void> {
  await invoke<void>("auth_forget_account", { uuid });
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

/** Re-fetch /v1/auth/me et met a jour le user cote Rust. Utile apres
 *  une liaison Discord effectuee dans le navigateur. */
export async function refreshMe(): Promise<LauncherUser> {
  return invoke<LauncherUser>("auth_me");
}

/** Lance le flow OAuth Discord : ouvre le navigateur et retourne le
 *  state. La completion est detectee en pollant `refreshMe()`. */
export async function startDiscordLink(): Promise<{ url: string; state: string }> {
  return invoke<{ url: string; state: string }>("discord_link_start");
}

export async function unlinkDiscord(): Promise<void> {
  await invoke<void>("discord_unlink");
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
    case "no_stored_credentials":
    case "stored_credentials_expired":
      // Ces deux kinds ne devraient jamais arriver jusqu'a l'utilisateur : le
      // handler du carousel bascule sur OAuth interactif avant. Texte de
      // secours au cas ou.
      return "Reconnexion requise. Ouverture de la fenetre Microsoft…";
    default:
      return err.message;
  }
}
