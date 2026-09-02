import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";

// Preferences purement UI persistees cote frontend (localStorage).
//
// Pourquoi pas dans Rust (lib/prefs.ts → prefs_get/set) ?
//   - Les prefs de prefs.ts (ramMb, resolution, autoConnect, discordRPC,
//     language) sont CONSOMMEES par le Rust au launch du jeu — donc
//     persistance Rust est obligatoire.
//   - Les prefs ci-dessous (audio, perf, notif) n'ont pas encore de
//     consommateur cote game-launch — elles servent uniquement a faire
//     persister le choix utilisateur entre sessions du launcher.
//
// TODO(prefs-rust): quand le mod Reborn / le game-launcher consommera
// les volumes audio (passes a la JVM via -Dorg.lwjgl.openal.volume=X)
// ou les options perf (fps cap via launchwrapper), migrer ces champs
// vers prefs.ts cote Rust et supprimer ce store.

export type PerfPrefs = {
  autoRenderDistance: boolean;
  fpsMax: number; // 30..240, 240 = illimite
  vsync: boolean;
  lowSpecMode: boolean;
};

export type AudioPrefs = {
  master: number; // 0..100
  music: number;
  sfx: number;
  voice: number;
  muteOnBlur: boolean;
  // ─────────── OST Reborn (mod broadcast) ───────────
  // Volume independant du music slider vanilla : l'OST passe par OpenAL en
  // SOURCE_RELATIVE pour le control fin de l'attenuation par distance, donc
  // ne suit pas le volume "Music" de Minecraft.
  ostVolume: number; // 0..100, applique au gain global du mod
  ostEnabled: boolean; // toggle global : si false, le mod skip toutes les zones
};

export type NotifChannel = "push" | "email" | "both";

export type NotifPrefs = {
  whitelistEnabled: boolean;
  whitelistChannel: NotifChannel;
  ticketsEnabled: boolean;
  patchEnabled: boolean;
  eventsEnabled: boolean;
};

// Choix de la police d'affichage du launcher — purement preferentiel.
//   - "default" : police actuelle (Inter), inchangee.
//   - "arcade"  : ArcadePix (police du menu principal in-game). ASCII-only,
//     donc appliquee via une STACK CSS (ArcadePix -> Inter) pour que les
//     accents FR retombent proprement sur Inter (cf globals.css).
export type FontChoice = "default" | "arcade";

export type UiPrefs = {
  font: FontChoice;
};

type SettingsState = {
  perf: PerfPrefs;
  audio: AudioPrefs;
  notif: NotifPrefs;
  ui: UiPrefs;
  setPerf: <K extends keyof PerfPrefs>(key: K, value: PerfPrefs[K]) => void;
  setAudio: <K extends keyof AudioPrefs>(key: K, value: AudioPrefs[K]) => void;
  setNotif: <K extends keyof NotifPrefs>(key: K, value: NotifPrefs[K]) => void;
  setUi: <K extends keyof UiPrefs>(key: K, value: UiPrefs[K]) => void;
};

const DEFAULTS: Pick<SettingsState, "perf" | "audio" | "notif" | "ui"> = {
  perf: {
    autoRenderDistance: true,
    fpsMax: 144,
    vsync: false,
    lowSpecMode: false,
  },
  audio: {
    master: 80,
    music: 50,
    sfx: 70,
    voice: 85,
    muteOnBlur: true,
    ostVolume: 70,
    ostEnabled: true,
  },
  notif: {
    whitelistEnabled: true,
    whitelistChannel: "both",
    ticketsEnabled: true,
    patchEnabled: true,
    eventsEnabled: false,
  },
  ui: {
    font: "default",
  },
};

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      ...DEFAULTS,
      setPerf: (key, value) =>
        set((s) => ({ perf: { ...s.perf, [key]: value } })),
      setAudio: (key, value) =>
        set((s) => ({ audio: { ...s.audio, [key]: value } })),
      setNotif: (key, value) =>
        set((s) => ({ notif: { ...s.notif, [key]: value } })),
      setUi: (key, value) =>
        set((s) => ({ ui: { ...s.ui, [key]: value } })),
    }),
    {
      name: "reborn:settings",
      storage: createJSONStorage(() => localStorage),
      version: 3,
      // Quand un user vient d'une version pre-OST, ostVolume/ostEnabled
      // n'existent pas dans le state persiste -> on les remplit depuis
      // DEFAULTS pour eviter un NaN dans le slider.
      migrate: (persisted: unknown, _version: number) => {
        const p = persisted as Partial<SettingsState> | undefined;
        return {
          ...DEFAULTS,
          ...(p ?? {}),
          audio: { ...DEFAULTS.audio, ...(p?.audio ?? {}) },
          perf: { ...DEFAULTS.perf, ...(p?.perf ?? {}) },
          notif: { ...DEFAULTS.notif, ...(p?.notif ?? {}) },
          ui: { ...DEFAULTS.ui, ...(p?.ui ?? {}) },
        } as SettingsState;
      },
    },
  ),
);
