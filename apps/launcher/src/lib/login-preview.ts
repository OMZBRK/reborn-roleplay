// Aperçu DEV-ONLY de l'écran d'accueil (captures hors Tauri).
// Usage : `pnpm --filter launcher dev` puis http://localhost:1420/login?apercu=etape1
// (etape1 | etape2 | attente | fini). Inactif en build release : la lecture
// est gardée par import.meta.env.DEV, Vite élimine le code mort.

import type { LauncherUser } from "../stores/auth-store";
import type { SavedAccount } from "./types";

export type ApercuPhase = "etape1" | "etape2" | "attente" | "fini";

const PHASES: readonly ApercuPhase[] = ["etape1", "etape2", "attente", "fini"];

export function readApercu(): ApercuPhase | null {
  if (!import.meta.env.DEV || typeof window === "undefined") return null;
  const v = new URLSearchParams(window.location.search).get("apercu");
  return PHASES.includes(v as ApercuPhase) ? (v as ApercuPhase) : null;
}

export const APERCU_USER: LauncherUser = {
  id: "00000000-0000-0000-0000-000000000000",
  minecraftUuid: "3f1c9a7e2b6d4e8fa0c5b1d29e7f4a63",
  minecraftUsername: "Kaito_Uzu",
  displayName: null,
  avatarUrl: null,
  role: "PLAYER",
  discord: null,
};

export const APERCU_DISCORD = {
  userId: "000000000000000000",
  username: "kaito.uzu",
  linkedAt: "2026-10-13T18:00:00.000Z",
};

export const APERCU_ACCOUNTS: SavedAccount[] = [
  {
    pseudo: "Kaito_Uzu",
    lastSeen: "2026-10-12T20:00:00.000Z",
    seed: "3f1c9a7e2b6d4e8fa0c5b1d29e7f4a63",
    minecraftUuid: "3f1c9a7e2b6d4e8fa0c5b1d29e7f4a63",
  },
  {
    pseudo: "Mei_Hozuki",
    lastSeen: "2026-10-01T20:00:00.000Z",
    seed: "a81d6b0c4f2e4b7d9c3a5e1f0b2d7c84",
    minecraftUuid: "a81d6b0c4f2e4b7d9c3a5e1f0b2d7c84",
  },
];
