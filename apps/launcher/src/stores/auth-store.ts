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
  accessToken: string | null;
  isAuthenticated: boolean;
  isResuming: boolean;
  setSession: (session: { user: LauncherUser; accessToken: string } | null) => void;
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
  setResuming: (isResuming) => set({ isResuming }),
}));
