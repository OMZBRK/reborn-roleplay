/**
 * Types partages cote frontend du launcher.
 *
 * SavedAccount : entree persistee via tauri-plugin-store (JSON dans AppData)
 * pour alimenter le carousel "comptes Microsoft enregistres" du LoginScreen.
 * Aucune donnee sensible n'est stockee ici — les refresh tokens vivent dans
 * le Windows Credential Manager (cf src-tauri/src/storage/secrets.rs).
 */
export type SavedAccount = {
  /** Pseudo Mojang affiche sur la carte. */
  pseudo: string;
  /** ISO 8601 — derniere connexion reussie via ce compte. */
  lastSeen: string;
  /** Seed deterministe pour le pixel-avatar MinecraftHead (= minecraftUuid). */
  seed: string;
  /** UUID Mojang sans tirets — cle pour retrouver les slots keyring per-compte. */
  minecraftUuid: string;
};
