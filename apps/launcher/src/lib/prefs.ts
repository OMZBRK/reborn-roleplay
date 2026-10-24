import { invoke } from "./tauri";

// Preferences du jeu persistees cote Rust (storage/prefs.rs, prefs.json dans
// le dossier de donnees, serde camelCase). Les erreurs sont des objets
// { kind, message } : les afficher avec formatIpcError (lib/errors.ts).

/** Comportement de la fenetre du launcher au lancement du jeu. */
export type OnGameStart = "minimize" | "stay";

export type Preferences = {
  ramMb: number;
  width: number;
  height: number;
  autoConnect: boolean;
  discordRichPresence: boolean;
  language: string;
  /** "minimize" (defaut) : le launcher se reduit quand le jeu demarre ;
   *  "stay" : il reste affiche (pratique pour suivre les logs). */
  onGameStart: OnGameStart;
  /** true (défaut) : fermer la fenêtre (croix, Alt+F4) la cache dans la zone
   *  de notification au lieu de quitter (src-tauri/src/tray.rs) ; « Quitter »
   *  dans le menu de l'icône ferme vraiment le launcher. */
  closeToTray: boolean;
};

/** Miroir de `Preferences::default()` cote Rust. */
export const DEFAULT_PREFERENCES: Preferences = {
  ramMb: 4096,
  width: 1280,
  height: 720,
  autoConnect: true,
  discordRichPresence: true,
  language: "fr",
  onGameStart: "minimize",
  closeToTray: true,
};

/** Complete un objet partiel (prefs.json ancien, backend pas a jour, mode
 *  navigateur) avec les valeurs par defaut, champ par champ. */
function withDefaults(value: Partial<Preferences> | null | undefined): Preferences {
  const merged = { ...DEFAULT_PREFERENCES, ...(value ?? {}) };
  if (merged.onGameStart !== "minimize" && merged.onGameStart !== "stay") {
    merged.onGameStart = DEFAULT_PREFERENCES.onGameStart;
  }
  if (typeof merged.closeToTray !== "boolean") {
    merged.closeToTray = DEFAULT_PREFERENCES.closeToTray;
  }
  return merged;
}

export async function getPrefs(): Promise<Preferences> {
  return withDefaults(await invoke<Partial<Preferences> | undefined>("prefs_get"));
}

export async function setPrefs(value: Preferences): Promise<Preferences> {
  const saved = await invoke<Partial<Preferences> | undefined>("prefs_set", { value });
  return withDefaults(saved ?? value);
}
