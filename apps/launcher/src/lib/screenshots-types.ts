// Modèle d'affichage d'une capture de la galerie Screenshots. Les données
// viennent du vrai dossier de jeu (commandes Tauri `screenshots_*`, mappées
// par toRecord() dans lib/screenshots.ts).

export type ScreenshotRecord = {
  id: string;
  title: string;
  /** Serveur d'origine — vide pour les captures locales (pas d'info disque). */
  server: string;
  /** Joueur d'origine — vide pour les captures locales. */
  player: string;
  date: string; // affichable ("16 mai 2026")
  time: string; // affichable ("23:42")
  size: string; // affichable ("412 KB")
  resolution: string; // affichable ("1920 × 1080")
  pinned: boolean;
  /** Background CSS de repli tant que l'image réelle n'est pas peinte. */
  art: string;
  // ── Champs remplis par le scanner FS réel (lib/screenshots.ts) ──
  /** Nom de fichier sur disque (id + clé favori partagée avec le mod). */
  fileName?: string;
  /** URL asset (convertFileSrc) de l'image réelle. */
  src?: string;
  /** Epoch ms de dernière modif, pour le tri réel. */
  modifiedMs?: number;
  /** Taille en octets, pour le tri réel. */
  sizeBytes?: number;
};
