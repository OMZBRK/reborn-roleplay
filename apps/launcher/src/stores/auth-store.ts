import { create } from "zustand";
import { Store } from "@tauri-apps/plugin-store";
import type { SavedAccount } from "../lib/types";

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

// Persistance JSON de la liste des comptes (carousel du LoginScreen).
// Aucun secret stocke ici — uniquement pseudo + uuid + lastSeen + seed.
// Les refresh tokens vivent dans le keyring OS, kxyes par UUID via les
// fonctions {get,set,delete}_*_for() de storage/secrets.rs (Rust).
const SAVED_ACCOUNTS_FILE = "saved-accounts.json";
const STORE_KEY = "accounts";

async function getStore(): Promise<Store> {
  return await Store.load(SAVED_ACCOUNTS_FILE);
}

type AuthState = {
  user: LauncherUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isResuming: boolean;
  isLoading: boolean;
  error: string | null;
  savedAccounts: SavedAccount[];
  setSession: (session: { user: LauncherUser; accessToken: string } | null) => void;
  setUser: (user: LauncherUser) => void;
  setResuming: (v: boolean) => void;
  setLoading: (v: boolean) => void;
  setError: (message: string | null) => void;
  loadSavedAccounts: () => Promise<void>;
  addSavedAccount: (account: SavedAccount) => Promise<void>;
  removeSavedAccount: (pseudo: string) => Promise<void>;
};

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  accessToken: null,
  isAuthenticated: false,
  isResuming: true,
  isLoading: false,
  error: null,
  savedAccounts: [],
  setSession: (session) =>
    set({
      user: session?.user ?? null,
      accessToken: session?.accessToken ?? null,
      isAuthenticated: !!session,
    }),
  setUser: (user) => set({ user }),
  setResuming: (isResuming) => set({ isResuming }),
  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
  loadSavedAccounts: async () => {
    const store = await getStore();
    const raw = await store.get<SavedAccount[]>(STORE_KEY);
    set({ savedAccounts: raw ?? [] });
  },
  addSavedAccount: async (account) => {
    // Dedoublonnage par pseudo. Le compte fraichement utilise remonte en tete
    // de liste (most-recent-first) pour orienter l'œil du staff.
    const next = [
      account,
      ...get().savedAccounts.filter((a) => a.pseudo !== account.pseudo),
    ];
    const store = await getStore();
    await store.set(STORE_KEY, next);
    await store.save();
    set({ savedAccounts: next });
  },
  removeSavedAccount: async (pseudo) => {
    const next = get().savedAccounts.filter((a) => a.pseudo !== pseudo);
    const store = await getStore();
    await store.set(STORE_KEY, next);
    await store.save();
    set({ savedAccounts: next });
  },
}));
