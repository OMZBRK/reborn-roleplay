// Mock data pour la page Screenshots tant que le vrai scanner FS Tauri
// n'est pas branche. Chaque entree decrit une capture in-game avec ses
// metadonnees + un `art` qui est une string CSS background utilisee comme
// stand-in pour le PNG reel (l'image source ne sera disponible qu'apres
// branchement Tauri @tauri-apps/plugin-fs sur %APPDATA%/.minecraft/screenshots).
//
// TODO(screenshots-scanner): remplacer par ScreenshotsService Rust qui :
//   1. List le dossier .minecraft/screenshots (Tauri plugin-fs)
//   2. Parse les noms de fichier (pattern Minecraft : YYYY-MM-DD_HH.mm.ss.png)
//   3. Lit les EXIF/headers pour les dimensions
//   4. Expose un asset:// URL via convertFileSrc pour rendre les vignettes
//   5. Optionnellement persiste un index .reborn/screenshots-index.json pour
//      le tag "pinned" + tags utilisateur (server, RP context)

export type ScreenshotRecord = {
  id: string;
  title: string;
  server: string;
  player: string;
  date: string;   // affichable ("16 mai 2026")
  time: string;   // affichable ("23:42")
  size: string;   // affichable ("412 KB")
  resolution: string; // affichable ("1920 × 1080")
  pinned: boolean;
  /** Background CSS string utilisee comme stand-in / fallback de l'image. */
  art: string;
  // ── Champs remplis par le scanner FS réel (lib/screenshots.ts) ──
  /** Nom de fichier sur disque (id + clé favori partagée avec le mod). */
  fileName?: string;
  /** URL asset (convertFileSrc) de l'image réelle. Absent = mock. */
  src?: string;
  /** Epoch ms de dernière modif, pour le tri réel. */
  modifiedMs?: number;
  /** Taille en octets, pour le tri réel. */
  sizeBytes?: number;
};

export type ScreenshotServerOption = {
  id: string;
  name: string;
  count: number;
};

export type ScreenshotPlayerOption = {
  id: string;
  initial: string;
  /** Nom affichable du joueur, pour matcher avec ScreenshotRecord.player */
  name: string;
};

export const MOCK_SCREENSHOTS: ScreenshotRecord[] = [
  {
    id: "s01",
    title: "Aurora over Tokyo District",
    server: "Reborn RP",
    player: "dbrn_main",
    date: "16 mai 2026",
    time: "23:42",
    size: "412 KB",
    resolution: "1920 × 1080",
    pinned: true,
    art: `linear-gradient(180deg, rgba(15,18,40,0.4), rgba(5,8,18,0.6)),
          radial-gradient(ellipse 50% 30% at 50% 20%, oklch(0.75 0.18 280) 0%, transparent 60%),
          linear-gradient(180deg, oklch(0.32 0.12 270) 0%, oklch(0.22 0.08 240) 55%, oklch(0.12 0.04 240) 100%)`,
  },
  {
    id: "s02",
    title: "Bedwars finals · clutch",
    server: "Hypixel",
    player: "Akira_Sato",
    date: "15 mai 2026",
    time: "21:08",
    size: "388 KB",
    resolution: "1920 × 1080",
    pinned: false,
    art: `linear-gradient(160deg, rgba(0,0,0,0.2), rgba(0,0,0,0.5)),
          radial-gradient(ellipse 60% 50% at 30% 40%, oklch(0.65 0.18 50) 0%, transparent 65%),
          linear-gradient(180deg, oklch(0.5 0.16 35) 0%, oklch(0.3 0.1 25) 100%)`,
  },
  {
    id: "s03",
    title: "Forêt de bambous · matin",
    server: "Reborn RP",
    player: "kuro_neko",
    date: "15 mai 2026",
    time: "07:24",
    size: "521 KB",
    resolution: "2560 × 1440",
    pinned: false,
    art: `linear-gradient(180deg, rgba(255,255,255,0.05), rgba(0,0,0,0.4)),
          linear-gradient(180deg, oklch(0.72 0.12 130) 0%, oklch(0.45 0.14 140) 60%, oklch(0.25 0.08 145) 100%)`,
  },
  {
    id: "s04",
    title: "Donut SMP base raid",
    server: "Donut SMP",
    player: "tetsuo_2049",
    date: "14 mai 2026",
    time: "19:55",
    size: "295 KB",
    resolution: "1920 × 1080",
    pinned: false,
    art: `linear-gradient(180deg, rgba(0,0,0,0.3), rgba(0,0,0,0.6)),
          radial-gradient(ellipse 60% 40% at 50% 60%, oklch(0.6 0.22 30) 0%, transparent 60%),
          linear-gradient(180deg, oklch(0.25 0.06 280) 0%, oklch(0.12 0.04 280) 100%)`,
  },
  {
    id: "s05",
    title: "Marché de Shibuya by night",
    server: "Reborn RP",
    player: "dbrn_main",
    date: "14 mai 2026",
    time: "00:12",
    size: "604 KB",
    resolution: "2560 × 1440",
    pinned: true,
    art: `linear-gradient(180deg, rgba(0,0,0,0.2), rgba(0,0,0,0.65)),
          radial-gradient(ellipse 60% 40% at 50% 50%, oklch(0.65 0.2 350) 0%, transparent 65%),
          linear-gradient(180deg, oklch(0.3 0.14 320) 0%, oklch(0.12 0.06 280) 100%)`,
  },
  {
    id: "s06",
    title: "Mont Reborn · sommet",
    server: "Reborn RP",
    player: "yuki_dev",
    date: "13 mai 2026",
    time: "16:33",
    size: "478 KB",
    resolution: "1920 × 1080",
    pinned: false,
    art: `linear-gradient(180deg, rgba(255,255,255,0.08), rgba(0,0,0,0.4)),
          radial-gradient(ellipse 80% 50% at 50% 70%, oklch(0.55 0.08 240) 0%, transparent 65%),
          linear-gradient(180deg, oklch(0.85 0.04 230) 0%, oklch(0.55 0.06 230) 50%, oklch(0.25 0.04 240) 100%)`,
  },
  {
    id: "s07",
    title: "Hypixel lobby fountain",
    server: "Hypixel",
    player: "ravenshield",
    date: "12 mai 2026",
    time: "18:01",
    size: "356 KB",
    resolution: "1920 × 1080",
    pinned: false,
    art: `linear-gradient(180deg, rgba(0,0,0,0.2), rgba(0,0,0,0.5)),
          radial-gradient(ellipse 50% 30% at 50% 50%, oklch(0.7 0.18 220) 0%, transparent 65%),
          linear-gradient(180deg, oklch(0.4 0.1 220) 0%, oklch(0.2 0.06 220) 100%)`,
  },
  {
    id: "s08",
    title: "Combat de boss · Donjon est",
    server: "Reborn RP",
    player: "MeryneSekai",
    date: "12 mai 2026",
    time: "20:47",
    size: "421 KB",
    resolution: "1920 × 1080",
    pinned: false,
    art: `linear-gradient(180deg, rgba(0,0,0,0.2), rgba(0,0,0,0.6)),
          radial-gradient(ellipse 60% 40% at 40% 50%, oklch(0.6 0.22 15) 0%, transparent 65%),
          linear-gradient(160deg, oklch(0.3 0.14 10) 0%, oklch(0.12 0.06 0) 100%)`,
  },
  {
    id: "s09",
    title: "Sanctuaire flottant",
    server: "Reborn RP",
    player: "haruchan",
    date: "11 mai 2026",
    time: "11:29",
    size: "517 KB",
    resolution: "2560 × 1440",
    pinned: false,
    art: `linear-gradient(180deg, rgba(255,255,255,0.06), rgba(0,0,0,0.4)),
          radial-gradient(ellipse 60% 40% at 50% 35%, oklch(0.85 0.1 60) 0%, transparent 65%),
          linear-gradient(180deg, oklch(0.7 0.1 200) 0%, oklch(0.4 0.1 220) 100%)`,
  },
  {
    id: "s10",
    title: "Construction · ferme automatique",
    server: "Singleplayer",
    player: "dbrn_main",
    date: "10 mai 2026",
    time: "14:18",
    size: "289 KB",
    resolution: "1920 × 1080",
    pinned: false,
    art: `linear-gradient(180deg, rgba(0,0,0,0.2), rgba(0,0,0,0.5)),
          linear-gradient(135deg, oklch(0.45 0.1 140) 0%, oklch(0.2 0.04 140) 100%)`,
  },
  {
    id: "s11",
    title: "Reborn RP · ouverture saison 4",
    server: "Reborn RP",
    player: "dbrn_main",
    date: "08 mai 2026",
    time: "21:00",
    size: "672 KB",
    resolution: "2560 × 1440",
    pinned: false,
    art: `linear-gradient(180deg, rgba(0,0,0,0.15), rgba(0,0,0,0.6)),
          radial-gradient(ellipse 50% 30% at 50% 40%, oklch(0.7 0.2 260) 0%, transparent 65%),
          linear-gradient(180deg, oklch(0.4 0.18 260) 0%, oklch(0.15 0.08 260) 100%)`,
  },
  {
    id: "s12",
    title: "Donjon des cendres",
    server: "Reborn RP",
    player: "Akira_Sato",
    date: "06 mai 2026",
    time: "03:11",
    size: "498 KB",
    resolution: "1920 × 1080",
    pinned: false,
    art: `linear-gradient(180deg, rgba(0,0,0,0.3), rgba(0,0,0,0.7)),
          radial-gradient(ellipse 50% 30% at 30% 70%, oklch(0.65 0.2 40) 0%, transparent 60%),
          linear-gradient(180deg, oklch(0.2 0.04 30) 0%, oklch(0.08 0.02 20) 100%)`,
  },
];

// Listes derivees pour les filtres. Mises en const car les composants les
// importent directement — quand le scanner FS arrivera, ces listes seront
// derivees dynamiquement de MOCK_SCREENSHOTS et exposees via un hook.
export const MOCK_SERVERS: ScreenshotServerOption[] = [
  { id: "Reborn RP",    name: "Reborn RP",    count: 7 },
  { id: "Hypixel",      name: "Hypixel",      count: 2 },
  { id: "Donut SMP",    name: "Donut SMP",    count: 1 },
  { id: "Singleplayer", name: "Singleplayer", count: 1 },
];

export const MOCK_PLAYERS: ScreenshotPlayerOption[] = [
  { id: "dbrn_main",   initial: "D", name: "dbrn_main" },
  { id: "Akira_Sato",  initial: "A", name: "Akira_Sato" },
  { id: "kuro_neko",   initial: "K", name: "kuro_neko" },
  { id: "tetsuo_2049", initial: "T", name: "tetsuo_2049" },
  { id: "yuki_dev",    initial: "Y", name: "yuki_dev" },
  { id: "ravenshield", initial: "R", name: "ravenshield" },
  { id: "MeryneSekai", initial: "M", name: "MeryneSekai" },
  { id: "haruchan",    initial: "H", name: "haruchan" },
];
