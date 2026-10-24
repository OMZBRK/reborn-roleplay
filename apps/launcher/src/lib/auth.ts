import { invoke } from "./tauri";
import type { LauncherUser } from "../stores/auth-store";

/** Reponse Rust de auth_login_microsoft / auth_resume_session. Le JWT
 *  Reborn ne traverse pas l'IPC : il reste dans le coffre Windows, cote Rust. */
export type AuthSession = {
  user: LauncherUser;
};

/** Forme serialisee de l'erreur Rust (cf auth/error.rs). `message` est une
 *  phrase complete : l'afficher via formatIpcError (lib/errors.ts). `code` =
 *  code Xbox/XSTS, erreur OAuth (ex. invalid_grant) ou statut HTTP. */
export type AuthErrorPayload = {
  kind:
    | "config"
    | "http"
    | "io"
    | "microsoft_response"
    | "minecraft_services"
    | "xbox"
    | "xsts"
    | "no_minecraft_license"
    | "user_canceled"
    | "timeout"
    | "storage"
    | "api"
    | "browser"
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

/** Keepalive : fait tourner l'access token API (TTL 15 min) cote Rust.
 *  Appele periodiquement par AuthenticatedLayout. Renvoie le compte a jour
 *  (son grade a pu changer : whitelist acceptee pendant la session). */
export async function refreshAccess(): Promise<LauncherUser | null> {
  return (await invoke<LauncherUser | null | undefined>("auth_refresh_access")) ?? null;
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
