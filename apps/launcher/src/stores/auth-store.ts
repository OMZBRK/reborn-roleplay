import { create } from "zustand";

export type DiscordLinkage = {
  userId: string;
  username: string;
  linkedAt: string;
};

export type LauncherUser = {
  id: string;
  minecraftUuid: string;
  minecraftUsername: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: "PLAYER" | "WHITELISTED" | "HELPER" | "MODERATOR" | "WHITELIST_REVIEWER" | "ADMIN" | "OWNER";
  discord: DiscordLinkage | null;
};

type AuthState = {
  user: LauncherUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isResuming: boolean;
  setSession: (session: { user: LauncherUser; accessToken: string } | null) => void;
  setUser: (user: LauncherUser) => void;
  setResuming: (v: boolean) => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  accessToken: null,
  isAuthenticated: false,
  isResuming: true,
  setSession: (session) =>
    set({
      user: session?.user ?? null,
      accessToken: session?.accessToken ?? null,
      isAuthenticated: !!session,
    }),
  setUser: (user) => set({ user }),
  setResuming: (isResuming) => set({ isResuming }),
}));
