import { invoke } from "./tauri";

export type Preferences = {
  ramMb: number;
  width: number;
  height: number;
  autoConnect: boolean;
  discordRichPresence: boolean;
  language: string;
};

export async function getPrefs(): Promise<Preferences> {
  return invoke<Preferences>("prefs_get");
}

export async function setPrefs(value: Preferences): Promise<Preferences> {
  return invoke<Preferences>("prefs_set", { value });
}
