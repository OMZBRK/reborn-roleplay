import { create } from "zustand";

export type LauncherUser = {
  id: string;
  minecraftUuid: string;
  minecraftUsername: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: "PLAYER" | "WHITELISTED" | "HELPER" | "MODERATOR" | "WHITELIST_REVIEWER" | "ADMIN" | "OWNER";
};

type AuthState = {
  user: LauncherUser | null;
  isAuthenticated: boolean;
  setUser: (u: LauncherUser | null) => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  setUser: (user) => set({ user, isAuthenticated: user !== null }),
}));
