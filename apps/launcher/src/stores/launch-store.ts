import { create } from "zustand";

export type LaunchPhase = "idle" | "checking" | "downloading" | "ready" | "running" | "blocked";

type LaunchState = {
  phase: LaunchPhase;
  progress: number;
  serverOnline: number;
  setPhase: (p: LaunchPhase) => void;
  setProgress: (p: number) => void;
};

export const useLaunchStore = create<LaunchState>((set) => ({
  phase: "idle",
  progress: 0,
  serverOnline: 0,
  setPhase: (phase) => set({ phase }),
  setProgress: (progress) => set({ progress }),
}));
