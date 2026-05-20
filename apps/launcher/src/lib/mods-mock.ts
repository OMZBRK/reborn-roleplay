// Helpers visuels pour la page Mods. La data reelle vient de
// listMods() (lib/launcher.ts) qui retourne le contenu reel du dossier
// `mods/` (parsing fabric.mod.json). Ici on fournit juste :
//   - une palette de couleurs deterministe pour les icones (hash modId)
//   - un mapping nom affichable → eventuel libelle "joli" si on veut
//     surcharger l'affichage de certains modIds techniques
//
// Pas de mock data : ce qui s'affiche dans la page = ce qui est
// reellement dans le dossier mods/ du launcher.

const ICON_PALETTE = [
  "#3b5bdb", // accent
  "#8b5cf6", // violet
  "#16a34a", // green
  "#f59e0b", // gold
  "#ef4444", // red
  "#d97757", // orange-brown
  "#06b6d4", // cyan
  "#ec4899", // pink
];

/**
 * Hash deterministe djb2 → index dans la palette ICON_PALETTE.
 * Utilise pour donner une couleur stable a chaque mod base sur son
 * modId/fileName, sans avoir besoin de la stocker.
 */
export function colorForMod(seed: string): string {
  let h = 5381;
  for (let i = 0; i < seed.length; i++) {
    h = ((h << 5) + h + seed.charCodeAt(i)) | 0;
  }
  const idx = (h >>> 0) % ICON_PALETTE.length;
  return ICON_PALETTE[idx] as string;
}

/** Formate un nombre d'octets en KB / MB humainement lisible. */
export function formatModSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Initiales 2-lettres a partir du nom affichable. */
export function modInitials(name: string): string {
  return name.slice(0, 2).toUpperCase();
}
