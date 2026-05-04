import type { LauncherUser } from "../../stores/auth-store";

export type RoleType = "player" | "whitelisted" | "helper" | "moderator" | "admin" | "owner";

export type RoleMeta = {
  label: string;
  color: string;
};

export const ROLE_META: Record<RoleType, RoleMeta> = {
  player: { label: "Joueur", color: "var(--color-role-player)" },
  whitelisted: { label: "Whitelisted", color: "var(--color-role-whitelisted)" },
  helper: { label: "Helper", color: "var(--color-role-helper)" },
  moderator: { label: "Modérateur", color: "var(--color-role-moderator)" },
  admin: { label: "Admin", color: "var(--color-role-admin)" },
  owner: { label: "Owner", color: "var(--color-role-owner)" },
};

export function mapRole(role: LauncherUser["role"] | undefined): RoleType {
  switch (role) {
    case "WHITELISTED":
      return "whitelisted";
    case "HELPER":
      return "helper";
    case "MODERATOR":
    case "WHITELIST_REVIEWER":
      return "moderator";
    case "ADMIN":
      return "admin";
    case "OWNER":
      return "owner";
    case "PLAYER":
    default:
      return "player";
  }
}
