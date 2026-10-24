// MODE DÉMO : développement uniquement, absent du build de production.
//
// Fait tourner le front dans un simple navigateur (vite dev sur
// http://localhost:1420) avec un faux backend Tauri, pour photographier les
// vrais écrans sans Rust ni API :
//
//   http://localhost:1420/?demo=<scénario>     (liste dans SCENARIOS)
//   http://localhost:1420/mods?demo=accueil    (n'importe quelle page)
//   http://localhost:1420/?demo=off            (retour au mode navigateur)
//
// Le scénario est mémorisé en sessionStorage : il survit aux rechargements et
// aux navigations, jusqu'à ?demo=off ou la fermeture de l'onglet.
//
// Chargé par main.tsx (import dynamique derrière import.meta.env.DEV) AVANT
// l'application : lib/tauri.ts fige isTauriRuntime au chargement du module,
// le faux backend (mockIPC + mockWindows) doit donc exister avant. Pour la
// même raison, ce fichier n'importe de l'application QUE des types : un
// import de valeur chargerait lib/tauri.ts trop tôt (hors Tauri pour de bon).
// En mode démo, main.tsx rend sans StrictMode, comme en production (ses
// effets montés deux fois relançaient par exemple le jeu tout seul depuis
// l'accueil, cf relaunchSeen dans PlayButton).

import { mockIPC, mockWindows } from "@tauri-apps/api/mocks";
import { emit } from "@tauri-apps/api/event";
import type { InvokeArgs } from "@tauri-apps/api/core";
import pkg from "../../package.json";
import type { AuthSession } from "../lib/auth";
import type { LauncherUser } from "../stores/auth-store";
import type { SavedAccount } from "../lib/types";
import type { Preferences } from "../lib/prefs";
import type { SystemSpecs } from "../lib/system";
import type { HealthPing } from "../lib/network-status";
import type { TauriEventMap, TauriEventName } from "../lib/tauri";
import type { LogLevel, LogLine, LogStream } from "../lib/logs";
import type {
  CleanResult,
  GameDiagnostic,
  GameInfo,
  GameStatus,
  LaunchedGame,
  ModEntry,
  OptionalMod,
  UpdatePreview,
  UpdateStatus,
} from "../lib/launcher";
import type {
  Badges,
  LoreDocument,
  OralSlotsResponse,
  PatchNoteDetail,
  PatchNoteListResponse,
  PatchNoteSummary,
  RulesDocument,
  ServerStatus,
  TicketCategory,
  TicketDetail,
  TicketMessage,
  TicketSummary,
  WhitelistApplication,
  WhitelistMeResponse,
  WhitelistMessage,
} from "../lib/content";
import type { PendingSharesResult, ScreenshotItem, ShotFeed } from "../lib/screenshots";

// ──────────────────────────────────────────────────────
//  Scénarios
// ──────────────────────────────────────────────────────

const STORAGE_KEY = "reborn:demo";

const SCENARIOS = {
  accueil: "accueil, prêt à jouer",
  "en-jeu": "partie en cours (bouton « Arrêter le jeu »)",
  journal: "partie en cours + panneau Journal du jeu alimenté chaque seconde",
  crash: "crash du jeu et fenêtre de rapport",
  "hors-ligne": "API injoignable (bandeau hors-ligne)",
  "erreur-jouer": "« Jouer » refusé : session Minecraft expirée",
  parametres: "Paramètres, onglet Jeu",
} as const;

type Scenario = keyof typeof SCENARIOS;

const isScenario = (v: string): v is Scenario =>
  Object.prototype.hasOwnProperty.call(SCENARIOS, v);

function readStored(): string | null {
  try {
    return window.sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStored(value: string | null): void {
  try {
    if (value === null) window.sessionStorage.removeItem(STORAGE_KEY);
    else window.sessionStorage.setItem(STORAGE_KEY, value);
  } catch {
    // Stockage indisponible : le scénario ne survivra pas au rechargement.
  }
}

/** Retire ?demo= de l'adresse (captures propres) ; `target` remplace le chemin. */
function cleanUrl(params: URLSearchParams, target: string | null): void {
  params.delete("demo");
  const search = params.toString();
  const url =
    target ?? `${window.location.pathname}${search ? `?${search}` : ""}${window.location.hash}`;
  window.history.replaceState(window.history.state, "", url);
}

/** Scénario demandé (adresse) ou mémorisé (sessionStorage), null = pas de démo. */
function resolveScenario(): Scenario | null {
  const params = new URLSearchParams(window.location.search);
  const requested = params.get("demo");
  if (requested === null) {
    const stored = readStored();
    return stored !== null && isScenario(stored) ? stored : null;
  }
  const name = requested.trim().toLowerCase();
  if (name === "off") {
    writeStored(null);
    cleanUrl(params, null);
    console.info("[démo] mode démo désactivé.");
    return null;
  }
  if (name && !isScenario(name)) {
    console.warn(`[démo] scénario inconnu « ${name} », « accueil » à la place.`);
  }
  const scenario: Scenario = isScenario(name) ? name : "accueil";
  writeStored(scenario);
  // Le scénario Paramètres ouvre directement l'onglet Jeu ; les autres
  // gardent la page demandée (« / » mène à l'accueil après la reprise).
  cleanUrl(params, scenario === "parametres" ? "/settings?tab=game" : null);
  return scenario;
}

// ──────────────────────────────────────────────────────
//  Outils
// ──────────────────────────────────────────────────────

type Args = Record<string, unknown>;

const wait = (ms: number) => new Promise<void>((resolve) => window.setTimeout(resolve, ms));

/** Erreur au format du contrat IPC (cf lib/errors.ts). */
const ipcError = (kind: string, message: string) => ({ kind, message });

const str = (a: Args, key: string): string => (typeof a[key] === "string" ? (a[key] as string) : "");

const pad = (n: number, w = 2) => String(n).padStart(w, "0");

const NOW = Date.now();
const MIN = 60_000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;
const ago = (ms: number) => new Date(NOW - ms).toISOString();

const DATA_DIR = "C:\\Users\\Kazuki\\AppData\\Roaming\\RebornRoleplay";
const MANIFEST_VERSION = "3.1.149";
const MC_VERSION = "26.2";

// ──────────────────────────────────────────────────────
//  Compte, préférences, machine
// ──────────────────────────────────────────────────────

const MC_UUID = "3f8c2a1e9b7d4c6fa1e25d0b7c9e4a12";

const DISCORD: NonNullable<LauncherUser["discord"]> = {
  userId: "802463519734218752",
  username: "kazuki.rp",
  linkedAt: ago(41 * DAY),
};

let user: LauncherUser = {
  id: "a3e1c7f2-58b4-4d9e-b2a6-1f0c9d7e4b35",
  minecraftUuid: MC_UUID,
  minecraftUsername: "Kazuki",
  displayName: "Kazuki",
  avatarUrl: null,
  role: "WHITELISTED",
  discord: DISCORD,
};

let savedAccounts: SavedAccount[] = [
  { pseudo: "Kazuki", lastSeen: ago(4 * MIN), seed: MC_UUID, minecraftUuid: MC_UUID },
];

let prefs: Preferences = {
  ramMb: 4096,
  width: 1920,
  height: 1080,
  autoConnect: true,
  discordRichPresence: true,
  language: "fr",
  onGameStart: "minimize",
  closeToTray: true,
};

const SPECS: SystemSpecs = {
  totalRamMb: 16_303,
  freeRamMb: 8_912,
  cpuBrand: "AMD Ryzen 7 5800X 8-Core Processor",
  cpuCores: 16,
  osName: "Windows 11 Pro",
  recommendedRamMb: 4096,
  maxRamMb: 14_255,
};

const GAME_INFO: GameInfo = {
  installDir: DATA_DIR,
  mcVersion: MC_VERSION,
  diskFreeGb: 212.4,
  diskTotalGb: 476.3,
};

const UPDATE_PREVIEW: UpdatePreview = {
  version: MANIFEST_VERSION,
  minecraftVersion: MC_VERSION,
  plan: [],
  bytesTotal: 0,
  minLauncherVersion: "0.3.40",
  launcherVersion: pkg.version,
  launcherOutdated: false,
};

let badges: Badges = { unreadTickets: 1, unreadPatchnotes: 1, coins: 1250 };

// ──────────────────────────────────────────────────────
//  Mods (modpack joueur 26.2, cf docs/ETAT_DES_LIEUX.md)
// ──────────────────────────────────────────────────────

const MOD_FILES: Array<[file: string, id: string, version: string, mc: string, size: number]> = [
  ["fabric-api-0.156.0+26.2.jar", "fabric-api", "0.156.0+26.2", "~26.2", 2_412_331],
  ["fabric-language-kotlin-1.13.7+kotlin.2.2.21.jar", "fabric-language-kotlin", "1.13.7+kotlin.2.2.21", "*", 7_652_118],
  ["sodium-fabric-0.7.3+mc26.2.jar", "sodium", "0.7.3+mc26.2", "~26.2", 1_284_906],
  ["sodium-extra-fabric-0.7.1+mc26.2.jar", "sodiumextra", "0.7.1+mc26.2", ">=26.2", 368_412],
  ["lithium-fabric-0.20.1+mc26.2.jar", "lithium", "0.20.1+mc26.2", "26.2", 812_377],
  ["entityculling-fabric-1.9.4-mc26.2.jar", "entityculling", "1.9.4", "26.2.x", 276_530],
  ["YetAnotherConfigLib-3.8.0+26.2-fabric.jar", "yet_another_config_lib_v3", "3.8.0+26.2-fabric", "~26.2", 1_102_884],
  ["entity_texture_features_26.2-fabric-7.0.2.jar", "entity_texture_features", "7.0.2", ">=26.2", 1_418_005],
  ["firstperson-fabric-2.6.1-mc26.2.jar", "firstperson", "2.6.1", "26.2.x", 318_772],
  ["plasmovoice-fabric-26.2-2.1.17.jar", "plasmovoice", "2.1.17", ">=26.2", 9_812_440],
  ["emotecraft-for-MC26.2-3.4.0-fabric.jar", "emotecraft", "3.4.0", "26.2.x", 1_726_119],
  ["PlayerAnimationLibMerged-1.2.6+mc26.2.jar", "player-animation-lib", "1.2.6", "~26.2", 512_330],
  ["reborn-hud-0.4.192.jar", "reborn-hud", "0.4.192", "26.2", 28_734_512],
  ["reborn-integrity-0.3.1.jar", "reborn-integrity", "0.3.1", "26.2", 18_442],
  ["reborn-ost-0.2.5.jar", "reborn-ost", "0.2.5", "26.2", 214_908],
  // Optionnels activés.
  ["iris-fabric-1.9.6+mc26.2.jar", "iris", "1.9.6+mc26.2", "~26.2", 2_903_551],
  ["zoomify-2.14.6+26.2.jar", "zoomify", "2.14.6+26.2", "26.2", 1_027_640],
  ["entity_model_features-3.0.1+26.2.jar", "entity_model_features", "3.0.1", ">=26.2", 1_288_417],
];

const MODS: ModEntry[] = MOD_FILES.map(([fileName, modId, modVersion, mc, sizeBytes]) => ({
  fileName,
  absolutePath: `${DATA_DIR}\\mods\\${fileName}`,
  modId,
  modVersion,
  minecraftConstraint: mc,
  sizeBytes,
  incompatibleWithTarget: false,
}));

let optionalMods: OptionalMod[] = [
  { filename: "iris-fabric-1.9.6+mc26.2.jar", sizeBytes: 2_903_551, enabled: true, installed: true },
  { filename: "zoomify-2.14.6+26.2.jar", sizeBytes: 1_027_640, enabled: true, installed: true },
  { filename: "entity_model_features-3.0.1+26.2.jar", sizeBytes: 1_288_417, enabled: true, installed: true },
  { filename: "NoChatReports-FABRIC-26.2-v2.14.0.jar", sizeBytes: 154_212, enabled: false, installed: false },
  { filename: "DistantHorizons-2.3.6-b-26.2-fabric.jar", sizeBytes: 12_408_771, enabled: false, installed: false },
];

// ──────────────────────────────────────────────────────
//  Contenus : whitelist, tickets, patch notes, règlement, lore
// ──────────────────────────────────────────────────────

const WHITELIST: WhitelistApplication = {
  id: "5b8e2d14-7a3c-4f91-8e06-c4d2a9b7f310",
  status: "APPROVED",
  hrpStatus: "APPROVED",
  rpStatus: "APPROVED",
  dob: "2003-06-14",
  motivation:
    "Je cherche un RP Naruto exigeant, où l'écriture et les relations entre personnages comptent plus que le PvP.",
  experience: "Quatre ans de RP Minecraft, dont deux sur un serveur médiéval en whitelist.",
  availability: "Soirs en semaine (20 h - minuit), week-ends plus larges.",
  firstName: "Kazuki",
  lastName: "Uchiha",
  village: "Konohagakure",
  support: null,
  history:
    "Cadet d'une branche éloignée du clan, Kazuki a grandi loin du quartier Uchiha, élevé par un oncle forgeron.",
  appearance: "Cheveux noirs mi-longs, regard calme, veste sombre frappée de l'éventail du clan.",
  objectives: "Devenir chūnin, puis comprendre pourquoi sa branche a quitté le quartier du clan.",
  submittedAt: ago(12 * DAY),
  reviewedAt: ago(10 * DAY),
  reviewNotes: "Très belle candidature, bienvenue à Konoha !",
  assignee: { username: "mei.helper" },
  assignedAt: ago(11 * DAY),
};

let whitelistMessages: WhitelistMessage[] = [
  {
    id: "wm-1",
    applicationId: WHITELIST.id,
    authorType: "STAFF",
    authorId: "staff-mei",
    authorName: "Mei",
    content: "Bonjour Kazuki ! Je prends ta candidature en charge, on se retrouve pour le test oral ?",
    attachments: [],
    createdAt: ago(11 * DAY),
  },
  {
    id: "wm-2",
    applicationId: WHITELIST.id,
    authorType: "USER",
    authorId: user.id,
    authorName: "Kazuki",
    content: "Avec plaisir, le créneau de jeudi 21 h me va très bien.",
    attachments: [],
    createdAt: ago(11 * DAY - 2 * HOUR),
  },
  {
    id: "wm-3",
    applicationId: WHITELIST.id,
    authorType: "SYSTEM",
    authorId: null,
    authorName: null,
    content: "Candidature acceptée. Bienvenue sur Reborn Roleplay !",
    attachments: [],
    createdAt: ago(10 * DAY),
  },
];

const ORAL_SLOTS: OralSlotsResponse = {
  open: [],
  mine: {
    id: "slot-3d9a",
    startAt: ago(10 * DAY + 2 * HOUR),
    durationMin: 20,
    status: "DONE",
    notes: null,
  },
};

let tickets: TicketDetail[] = [
  {
    id: "c2f7a9e4-1b3d-4e8a-9f60-7d2b5c8e1a43",
    category: "BUG",
    subject: "Mon HUD disparaît après un KO",
    status: "IN_PROGRESS",
    createdAt: ago(26 * HOUR),
    updatedAt: ago(40 * MIN),
    assignee: { username: "mei.helper" },
    assignedAt: ago(3 * HOUR),
    messages: [
      {
        id: "tm-1",
        authorId: user.id,
        authorName: "Kazuki",
        isStaff: false,
        content:
          "Salut ! Depuis hier, quand je me relève d'un KO, les barres de vie et de chakra ne reviennent pas. Je dois me reconnecter pour les retrouver.",
        attachments: [],
        createdAt: ago(26 * HOUR),
      },
      {
        id: "tm-2",
        authorId: "staff-mei",
        authorName: "Mei",
        isStaff: true,
        content:
          "Merci pour le signalement ! On a reproduit le souci sur le serveur de dev, le correctif part avec la prochaine version du HUD. Ça t'arrive aussi après une mort RP ?",
        attachments: [],
        createdAt: ago(3 * HOUR),
      },
      {
        id: "tm-3",
        authorId: user.id,
        authorName: "Kazuki",
        isStaff: false,
        content: "Non, seulement après un KO. Merci pour la réactivité !",
        attachments: [],
        createdAt: ago(40 * MIN),
      },
    ],
  },
  {
    id: "8e41b0d2-6c5a-4f17-b3e9-2a9d7c4f0b18",
    category: "PURCHASE_ISSUE",
    subject: "RBCoins non crédités après un achat",
    status: "RESOLVED",
    createdAt: ago(6 * DAY),
    updatedAt: ago(5 * DAY),
    assignee: { username: "raiden.admin" },
    assignedAt: ago(6 * DAY - 3 * HOUR),
    messages: [
      {
        id: "tm-4",
        authorId: user.id,
        authorName: "Kazuki",
        isStaff: false,
        content: "J'ai acheté le pack de 500 RBCoins hier soir mais mon solde n'a pas bougé.",
        attachments: [],
        createdAt: ago(6 * DAY),
      },
      {
        id: "tm-5",
        authorId: "staff-raiden",
        authorName: "Raiden",
        isStaff: true,
        content: "C'est corrigé : les 500 RBCoins sont crédités sur ton compte. Désolé pour l'attente !",
        attachments: [],
        createdAt: ago(5 * DAY),
      },
    ],
  },
];

function ticketSummary(t: TicketDetail): TicketSummary {
  const last = t.messages[t.messages.length - 1];
  return {
    id: t.id,
    category: t.category,
    subject: t.subject,
    status: t.status,
    createdAt: t.createdAt,
    updatedAt: t.updatedAt,
    lastMessagePreview: last ? last.content.slice(0, 140) : null,
  };
}

function findTicket(id: string): TicketDetail {
  const t = tickets.find((x) => x.id === id);
  if (!t) throw ipcError("not_found", "Ce ticket n'existe plus : il a peut-être été supprimé.");
  return t;
}

const PATCHNOTES: PatchNoteDetail[] = [
  {
    id: "pn-0-4-192",
    version: "0.4.192",
    title: "Menu R avec accord, masques et HUD n°4",
    thumbnail: null,
    pinned: true,
    publishedAt: ago(5 * HOUR),
    excerpt:
      "Porter ou fouiller quelqu'un demande désormais son accord, un masque rend inconnu, volume vocal par joueur et nouveau HUD.",
    content: [
      "## Nouveautés",
      "- **Menu R avec accord** : porter ou fouiller une personne consciente envoie une demande (Entrée pour accepter, Retour pour refuser, 15 s).",
      "- **Masques** : un personnage masqué apparaît comme « Inconnu » sur la plaque, dans la liste des joueurs, le tchat et les /me.",
      "- **Volume vocal par joueur**, réglable depuis la liste des joueurs.",
      "- **HUD n°4** : boussole facultative, zone d'effets, faim punitive à 15 % puis 0 %.",
      "",
      "## Améliorations",
      "- Images par seconde débridées une seule fois (120 i/s, VSync, 30 i/s d'inactivité).",
      "- Sous-menu Staff réservé au staff.",
    ].join("\n"),
  },
  {
    id: "pn-0-4-191",
    version: "0.4.191",
    title: "Couleur d'aura attribuée par le staff",
    thumbnail: null,
    pinned: false,
    publishedAt: ago(5 * DAY),
    excerpt:
      "La couleur d'aura quitte les Réglages : elle est attribuée par le staff en attendant la boutique RBCoins.",
    content:
      "## Changement\n- La couleur d'aura n'est plus réglable par le joueur : le staff l'attribue, en attendant son arrivée dans la boutique RBCoins.",
  },
  {
    id: "pn-0-4-190",
    version: "0.4.190",
    title: "Méditation, bandeau teintable et coupes en direct",
    thumbnail: null,
    pinned: false,
    publishedAt: ago(6 * DAY),
    excerpt:
      "Nouvelle méditation (halo, pose), bandeau teintable, coupes de cheveux en direct, sourcils et emblèmes de clans.",
    content:
      "## Nouveautés\n- Méditation retravaillée : halo, nouvelle pose, couleur d'aura.\n- Bandeau ninja teintable.\n- Coupes de cheveux appliquées en direct par le serveur, sourcils.\n- Emblèmes de clans.",
  },
  {
    id: "pn-0-4-189",
    version: "0.4.189",
    title: "États du joueur refondus",
    thumbnail: null,
    pinned: false,
    publishedAt: ago(8 * DAY),
    excerpt: "États du joueur refondus, garde tenue et coup de garde à la parade, fouille dans la sacoche RP.",
    content:
      "## Nouveautés\n- États du joueur refondus.\n- Garde tenue et coup de garde à la parade.\n- Fouille dans la sacoche RP.",
  },
];

const toSummary = (p: PatchNoteDetail): PatchNoteSummary => ({
  id: p.id,
  version: p.version,
  title: p.title,
  thumbnail: p.thumbnail,
  pinned: p.pinned,
  publishedAt: p.publishedAt,
  excerpt: p.excerpt,
});

const RULES: RulesDocument = {
  version: "2.3",
  publishedAt: ago(20 * DAY),
  content:
    "# Règlement\n\n## Respect\nAucun propos haineux, en RP comme en HRP.\n\n## Roleplay\nPas de métagaming ni de powergaming. Les scènes se construisent à plusieurs.",
};

const LORE: LoreDocument = {
  version: "1.8",
  publishedAt: ago(30 * DAY),
  content:
    "# Le Pays du Feu\n\nKonohagakure, le village caché de la Feuille, se relève d'une guerre qui a redessiné ses frontières.",
};

// ──────────────────────────────────────────────────────
//  Journal du jeu (format réel : en-tête log4j de Fabric)
// ──────────────────────────────────────────────────────

/** = LOG_BUFFER_MAX de lib/logs.ts (pas d'import de valeur ici, cf en-tête). */
const LOG_BUFFER_MAX = 5000;

/** Ligne de journal à produire : texte brut (stdout) ou `{ err }` (stderr).
 *  « § » est remplacé par l'heure de la ligne. */
type LogSpec = string | { err: string };

const TS = "§";

function hms(t: number): string {
  const d = new Date(t);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

// Miroir de LevelClassifier (src-tauri/src/launcher/logs.rs) : mêmes niveaux
// que ceux que le vrai backend attribuerait à ces lignes. Un seul état par
// partie, stdout et stderr confondus, qui voit les lignes dans l'ordre.
const HEADER_TAGS: Array<[string, LogLevel]> = [
  ["/FATAL]", "error"],
  ["/ERROR]", "error"],
  ["/WARN]", "warn"],
  ["/INFO]", "info"],
  ["/DEBUG]", "debug"],
  ["/TRACE]", "debug"],
  ["[FATAL]", "error"],
  ["[ERROR]", "error"],
  ["[WARN]", "warn"],
  ["[WARNING]", "warn"],
  ["[INFO]", "info"],
  ["[DEBUG]", "debug"],
  ["[TRACE]", "debug"],
  ["[SEVERE]", "error"],
];

const CRASH_REPORT_MARKER = "---- Minecraft Crash Report ----";

const LEVEL_RANK: Record<LogLevel, number> = { debug: 0, info: 1, warn: 2, error: 3 };

/** État du classifieur : niveau du dernier en-tête vu (tous flux), et flux
 *  sur lequel un rapport de crash est en cours d'écriture. */
type LevelState = { header: LogLevel | null; crash: LogStream | null };

const newLevelState = (): LevelState => ({ header: null, crash: null });

/** Marqueur de niveau dans les 160 premiers caractères, le plus à gauche. */
function headerLevel(text: string): LogLevel | null {
  const head = text.slice(0, 160);
  let best: [number, LogLevel] | null = null;
  for (const [tag, level] of HEADER_TAGS) {
    const i = head.indexOf(tag);
    if (i >= 0 && (best === null || i < best[0])) best = [i, level];
  }
  return best ? best[1] : null;
}

/** Préfixes de la JVM : ils ouvrent une entrée comme un en-tête log4j. */
function jvmPrefixLevel(trimmed: string): LogLevel | null {
  if (["Exception in thread", "ERROR:", "Error:", "FATAL"].some((p) => trimmed.startsWith(p))) {
    return "error";
  }
  if (trimmed.startsWith("WARNING:") || trimmed.startsWith("Warning:")) return "warn";
  return null;
}

/** Nom d'exception, `Caused by:`, `Suppressed:`, `\tat …` ou `... N more`. */
function isExceptionLine(text: string): boolean {
  const trimmed = text.trimStart();
  const indented = trimmed.length !== text.length;
  return (
    (indented && trimmed.startsWith("at ")) ||
    trimmed.startsWith("Caused by:") ||
    trimmed.startsWith("Suppressed:") ||
    /^\.\.\. \d+ (more|common frames omitted)$/.test(trimmed) ||
    /^[\w.$]+(Exception|Error|Throwable)(: |$)/.test(trimmed)
  );
}

function classify(state: LevelState, text: string, stream: LogStream): LogLevel {
  const trimmed = text.trimStart();
  if (text.includes(CRASH_REPORT_MARKER)) {
    state.crash = stream;
    state.header = "error";
    return "error";
  }
  const header = headerLevel(text);
  if (state.crash === stream) {
    if (header === null) {
      if (trimmed.startsWith("#@") && trimmed.includes("Game crashed")) state.crash = null;
      return "error";
    }
    // Nouvelle entrée log4j sur le flux du rapport : le bloc est fini.
    state.crash = null;
  }
  const level = header ?? jvmPrefixLevel(trimmed);
  if (level) {
    state.header = level;
    return level;
  }
  const inherited: LogLevel = state.header ?? (stream === "out" ? "info" : "warn");
  return isExceptionLine(text) && LEVEL_RANK[inherited] < LEVEL_RANK.warn ? "warn" : inherited;
}

/** Horodate des lignes entre `start` et `end` ; les lignes de suite (stdout
 *  sans heure : liste des mods, pile d'appels) gardent l'heure de leur en-tête.
 *  `levels` est l'état du classifieur de la partie, mis à jour au passage. */
function materialize(
  specs: readonly LogSpec[],
  start: number,
  end: number,
  levels: LevelState,
): LogLine[] {
  const out: LogLine[] = [];
  const span = Math.max(specs.length - 1, 1);
  let t = start;
  specs.forEach((spec, i) => {
    const stream: LogStream = typeof spec === "string" ? "out" : "err";
    const raw = typeof spec === "string" ? spec : spec.err;
    if (i === 0 || stream === "err" || raw.includes(TS)) {
      t = Math.round(start + ((end - start) * i) / span);
    }
    const text = raw.replace(TS, hms(t));
    out.push({ t, stream, level: classify(levels, text, stream), text });
  });
  return out;
}

const isWindowReadyMarker = (text: string) =>
  text.includes("Reloading ResourceManager:") || text.includes("Sound engine started");

function fakeHash(s: string): string {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0).toString(16).padStart(8, "0");
}

const FABRIC_API_MODULES = [
  "fabric-api-base", "fabric-api-lookup-api-v1", "fabric-biome-api-v1", "fabric-block-api-v1",
  "fabric-block-view-api-v2", "fabric-blockrenderlayer-v1", "fabric-client-gametest-api-v1",
  "fabric-command-api-v2", "fabric-content-registries-v0", "fabric-convention-tags-v2",
  "fabric-crash-report-info-v1", "fabric-data-attachment-api-v1", "fabric-data-generation-api-v1",
  "fabric-dimensions-v1", "fabric-entity-events-v1", "fabric-events-interaction-v0",
  "fabric-game-rule-api-v1", "fabric-gametest-api-v1", "fabric-item-api-v1",
  "fabric-item-group-api-v1", "fabric-key-binding-api-v1", "fabric-lifecycle-events-v1",
  "fabric-loot-api-v3", "fabric-message-api-v1", "fabric-model-loading-api-v1",
  "fabric-networking-api-v1", "fabric-object-builder-api-v1", "fabric-particles-v1",
  "fabric-recipe-api-v1", "fabric-registry-sync-v0", "fabric-renderer-api-v1",
  "fabric-renderer-indigo", "fabric-rendering-fluids-v1", "fabric-rendering-v1",
  "fabric-resource-conditions-api-v1", "fabric-resource-loader-v1", "fabric-screen-api-v1",
  "fabric-screen-handler-api-v1", "fabric-sound-api-v1", "fabric-tag-api-v1",
  "fabric-transfer-api-v1", "fabric-transitive-access-wideners-v1",
];

const MOD_TREE: Array<[id: string, version: string, children?: string[]]> = [
  ["emotecraft", "3.4.0"],
  ["entity_model_features", "3.0.1"],
  ["entity_texture_features", "7.0.2"],
  ["entityculling", "1.9.4"],
  [
    "fabric-api",
    "0.156.0+26.2",
    FABRIC_API_MODULES.map((m, i) => `${m} ${1 + (i % 4)}.${i % 7}.${(i * 3) % 11}+${fakeHash(m)}`),
  ],
  [
    "fabric-language-kotlin",
    "1.13.7+kotlin.2.2.21",
    [
      "org_jetbrains_kotlin_kotlin-reflect 2.2.21",
      "org_jetbrains_kotlin_kotlin-stdlib 2.2.21",
      "org_jetbrains_kotlinx_kotlinx-coroutines-core-jvm 1.10.2",
      "org_jetbrains_kotlinx_kotlinx-serialization-json-jvm 1.9.0",
    ],
  ],
  ["fabricloader", "0.19.3", ["mixinextras 0.5.0"]],
  ["firstperson", "2.6.1"],
  [
    "iris",
    "1.9.6+mc26.2",
    ["io_github_douira_glsl-transformer 2.0.1", "org_anarres_jcpp 1.4.14", "org_antlr_antlr4-runtime 4.13.1"],
  ],
  ["java", "25"],
  ["lithium", "0.20.1+mc26.2"],
  ["minecraft", MC_VERSION],
  ["plasmovoice", "2.1.17"],
  ["player-animation-lib", "1.2.6"],
  ["reborn-hud", "0.4.192"],
  ["reborn-integrity", "0.3.1"],
  ["reborn-ost", "0.2.5"],
  ["sodium", "0.7.3+mc26.2"],
  ["sodiumextra", "0.7.1+mc26.2"],
  ["yet_another_config_lib_v3", "3.8.0+26.2-fabric"],
  ["zoomify", "2.14.6+26.2"],
];

function modListSpecs(): LogSpec[] {
  const lines: string[] = [];
  let count = 0;
  for (const [id, version, children = []] of MOD_TREE) {
    lines.push(`\t- ${id} ${version}`);
    count += 1 + children.length;
    children.forEach((c, i) => lines.push(`\t   ${i === children.length - 1 ? "\\--" : "|--"} ${c}`));
  }
  return [`[${TS}] [main/INFO] (FabricLoader) Loading ${count} mods:`, ...lines];
}

const LIBS = "file:/C:/Users/Kazuki/AppData/Roaming/RebornRoleplay/libraries";

const ATLASES = [
  "2048x1024x4 minecraft:textures/atlas/blocks.png-atlas",
  "512x512x4 minecraft:textures/atlas/signs.png-atlas",
  "1024x512x4 minecraft:textures/atlas/banner_patterns.png-atlas",
  "1024x1024x4 minecraft:textures/atlas/armor_trims.png-atlas",
  "256x256x4 minecraft:textures/atlas/chest.png-atlas",
  "512x256x4 minecraft:textures/atlas/shield_patterns.png-atlas",
  "256x256x0 minecraft:textures/atlas/particles.png-atlas",
  "512x512x0 minecraft:textures/atlas/paintings.png-atlas",
  "256x128x0 minecraft:textures/atlas/mob_effects.png-atlas",
  "1024x1024x0 minecraft:textures/atlas/gui.png-atlas",
  "128x128x0 minecraft:textures/atlas/map_decorations.png-atlas",
  "2048x2048x0 reborn-hud:textures/atlas/hud.png-atlas",
];

/** Démarrage : JVM, Fabric Loader, mixins, ressources, son. */
const STARTUP: LogSpec[] = [
  { err: "WARNING: A restricted method in java.lang.System has been called" },
  {
    err: `WARNING: java.lang.System::load has been called by org.lwjgl.system.Library in an unnamed module (${LIBS}/org/lwjgl/lwjgl/3.3.6/lwjgl-3.3.6.jar)`,
  },
  { err: "WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module" },
  { err: "WARNING: Restricted methods will be blocked in a future release unless native access is enabled" },
  `[${TS}] [main/INFO] (FabricLoader/GameProvider) Loading Minecraft ${MC_VERSION} with Fabric Loader 0.19.3`,
  ...modListSpecs(),
  `[${TS}] [main/INFO] (FabricLoader/Mixin) SpongePowered MIXIN Subsystem Version=0.8.7 Source=union:/C:/Users/Kazuki/AppData/Roaming/RebornRoleplay/libraries/net/fabricmc/sponge-mixin/0.16.5+mixin.0.8.7/sponge-mixin-0.16.5+mixin.0.8.7.jar Service=Knot/Fabric Env=CLIENT`,
  `[${TS}] [main/INFO] (FabricLoader/Mixin) Compatibility level set to JAVA_25`,
  `[${TS}] [main/INFO] (FabricLoader/MixinExtras|Service) Initializing MixinExtras via com.llamalad7.mixinextras.service.MixinExtrasServiceImpl(version=0.5.0).`,
  `[${TS}] [main/WARN] (FabricLoader/Mixin) Reference map 'firstperson-refmap.json' for firstperson.mixins.json could not be read. If this is a development environment you can ignore this message`,
  `[${TS}] [main/INFO] (sodium) Loaded configuration file for Sodium: 42 options available, 0 override(s) found`,
  `[${TS}] [main/INFO] (lithium) Loaded configuration file for Lithium: 147 options available, 1 override(s) found`,
  `[${TS}] [main/DEBUG] (FabricLoader/Mixin) Mixing reborn-hud.mixins.json:HudMixin from mod reborn-hud into net.minecraft.client.gui.Hud`,
  `[${TS}] [main/DEBUG] (FabricLoader/Mixin) Mixing reborn-hud.mixins.json:ChatScreenMixin from mod reborn-hud into net.minecraft.client.gui.screens.ChatScreen`,
  { err: "WARNING: A terminally deprecated method in sun.misc.Unsafe has been called" },
  {
    err: `WARNING: sun.misc.Unsafe::objectFieldOffset has been called by com.google.gson.internal.UnsafeAllocator (${LIBS}/com/google/code/gson/gson/2.13.1/gson-2.13.1.jar)`,
  },
  { err: "WARNING: Please consider reporting this to the maintainers of class com.google.gson.internal.UnsafeAllocator" },
  { err: "WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release" },
  `[${TS}] [Datafixer Bootstrap/INFO] (DataFixers) 258 Datafixer optimizations took 387 milliseconds`,
  `[${TS}] [Render thread/INFO] (Minecraft) Environment: Environment[sessionHost=https://sessionserver.mojang.com, servicesHost=https://api.minecraftservices.com, profilesHost=https://api.mojang.com, name=PROD]`,
  `[${TS}] [Render thread/INFO] (Minecraft) Setting user: Kazuki`,
  `[${TS}] [Render thread/INFO] (reborn-integrity) Reborn Integrity 0.3.1 : jeton de jeu lu, envoi prévu à la connexion`,
  `[${TS}] [Render thread/INFO] (Minecraft) Backend library: LWJGL version 3.3.6`,
  `[${TS}] [Render thread/INFO] (sodium) OpenGL Vendor: NVIDIA Corporation`,
  `[${TS}] [Render thread/INFO] (sodium) OpenGL Renderer: NVIDIA GeForce RTX 3070/PCIe/SSE2`,
  `[${TS}] [Render thread/INFO] (sodium) OpenGL Version: 4.6.0 NVIDIA 566.36`,
  `[${TS}] [Render thread/INFO] (iris) Debug functionality is disabled.`,
  `[${TS}] [Render thread/INFO] (iris) ARB_direct_state_access detected, enabling DSA.`,
  `[${TS}] [Render thread/INFO] (emotecraft) Initializing Emotecraft 3.4.0`,
  `[${TS}] [Render thread/INFO] (plasmovoice) Plasmo Voice client initialized`,
  `[${TS}] [Render thread/INFO] (reborn-ost) Reborn OST 0.2.5 : décodeur STBVorbis prêt, 48 pistes indexées`,
  `[${TS}] [Render thread/INFO] (reborn-hud) Reborn HUD 0.4.192 : menu principal, HUD, tchat RP et créateur de personnage chargés`,
  `[${TS}] [Render thread/DEBUG] (reborn-hud) Scène de chargement « nuit de pleine lune » prête`,
  `[${TS}] [Render thread/WARN] (Minecraft) Missing sound for event: minecraft:item.goat_horn.play`,
  `[${TS}] [Render thread/WARN] (Minecraft) Missing sound for event: minecraft:entity.goat.screaming.horn_break`,
  `[${TS}] [Render thread/INFO] (Minecraft) Reloading ResourceManager: vanilla, fabric, emotecraft, entity_model_features, entity_texture_features, firstperson, iris, plasmovoice, reborn-hud, reborn-ost, sodium, sodiumextra, zoomify, server`,
  `[${TS}] [Worker-Main-4/INFO] (Minecraft) Found unifont_all_no_pua-16.0.03.hex, loading`,
  `[${TS}] [Worker-Main-2/INFO] (entity_texture_features) [ETF]: reloading ETF data.`,
  `[${TS}] [Worker-Main-7/INFO] (entity_model_features) [EMF]: reloading models, 0 custom models found in resource packs`,
  `[${TS}] [Render thread/INFO] (Minecraft) OpenAL initialized on device OpenAL Soft on Haut-parleurs (Realtek(R) Audio)`,
  `[${TS}] [Render thread/INFO] (Minecraft) Sound engine started`,
  ...ATLASES.map((a) => `[${TS}] [Render thread/INFO] (Minecraft) Created: ${a}`),
  `[${TS}] [Render thread/WARN] (Minecraft) Shader rendertype_entity_translucent_emissive could not find sampler named Sampler2 in the specified shader program.`,
  `[${TS}] [Render thread/ERROR] (entity_model_features) [EMF]: Failed to load model minecraft:optifine/cem/wolf.jem: missing "models" array`,
  `[${TS}] [Render thread/INFO] (Minecraft) Loaded 1351 recipes`,
  `[${TS}] [Render thread/INFO] (Minecraft) Loaded 1478 advancements`,
];

/** Connexion au serveur (aucune adresse de serveur dans ces lignes). */
const JOIN: LogSpec[] = [
  `[${TS}] [Render thread/INFO] (reborn-hud) Connexion automatique au serveur Reborn`,
  `[${TS}] [Netty Client IO #0/INFO] (reborn-integrity) Jeton de jeu envoyé sur reborn:auth`,
  `[${TS}] [Render thread/ERROR] (plasmovoice) Failed to open input device "Microphone (USB Audio Device)", falling back to the default device`,
  `[${TS}] [Render thread/INFO] (plasmovoice) Connected to the voice server`,
  `[${TS}] [Render thread/INFO] (reborn-ost) Synchronisation des zones OST : 37 zones reçues`,
  `[${TS}] [Render thread/INFO] (reborn-hud) Personnage chargé : Kazuki Uchiha (Konohagakure)`,
  `[${TS}] [Render thread/WARN] (Minecraft) Ignoring player info update for unknown player 7c4e1a92-3b5d-4f08-a6e1-0d9b2c7f5e31 ([UPDATE_LATENCY])`,
  `[${TS}] [Render thread/INFO] (Minecraft) [System] [CHAT] Bienvenue sur Reborn Roleplay, Kazuki. Bon jeu !`,
  `[${TS}] [Render thread/INFO] (reborn-ost) Zone « Konoha, place du marché » : piste market_day.ogg`,
];

/** En jeu : groupes de lignes (une pile d'appels reste d'un seul tenant). */
const INGAME: LogSpec[][] = [
  [`[${TS}] [Render thread/INFO] (Minecraft) [System] [CHAT] [Konoha] Ren Hyūga : Rendez-vous au terrain d'entraînement n°3 dans dix minutes.`],
  [`[${TS}] [Render thread/INFO] (Minecraft) [System] [CHAT] [HRP] Itachi_77 : quelqu'un pour tester la parade ce soir ?`],
  [`[${TS}] [Render thread/INFO] (Minecraft) [System] [CHAT] * Kazuki Uchiha resserre son bandeau et observe la place du marché.`],
  [`[${TS}] [Render thread/WARN] (Minecraft) Received passengers for unknown entity`],
  [`[${TS}] [Render thread/INFO] (Minecraft) [System] [CHAT] [Radio] Escouade 4 : cible repérée près du pont Naka.`],
  [`[${TS}] [Render thread/INFO] (plasmovoice) Kenji_Aburame joined your proximity channel`],
  [`[${TS}] [Render thread/WARN] (entity_texture_features) [ETF]: emissive texture not found: minecraft:textures/entity/villager/profession/farmer_e.png`],
  [`[${TS}] [Render thread/INFO] (Minecraft) [System] [CHAT] [Annonce] Le Hokage convoque les chūnin à la tour à 21 h.`],
  [`[${TS}] [Render thread/INFO] (reborn-ost) Zone « Konoha, porte principale » : piste gate_wind.ogg (fondu 2 s)`],
  [`[${TS}] [Render thread/INFO] (Minecraft) [System] [CHAT] [Mission] Mission de rang C acceptée : escorte d'un marchand vers Tanzaku.`],
  [`[${TS}] [Netty Client IO #0/WARN] (plasmovoice) Dropped late voice packet (312 ms)`],
  [`[${TS}] [Render thread/INFO] (Minecraft) [System] [CHAT] [Konoha] Sakura_H : Je passe à l'hôpital, quelqu'un est blessé ?`],
  [
    `[${TS}] [Download-3/ERROR] (Minecraft) Couldn't download http texture`,
    "java.net.SocketTimeoutException: Read timed out",
    "\tat java.base/sun.nio.ch.NioSocketImpl.timedRead(NioSocketImpl.java:278)",
    "\tat java.base/sun.nio.ch.NioSocketImpl.implRead(NioSocketImpl.java:304)",
    "\tat java.base/java.net.HttpURLConnection.getResponseCode(HttpURLConnection.java:529)",
    "\tat net.minecraft.client.renderer.texture.HttpTexture.lambda$load$0(HttpTexture.java:118)",
    "\t... 4 more",
  ],
  [`[${TS}] [Render thread/INFO] (Minecraft) [System] [CHAT] * Ren Hyūga déplie une carte du Pays du Feu sur la table.`],
  [`[${TS}] [Render thread/WARN] (Minecraft) Ignoring player info update for unknown player 1b9e7c40-2d6f-4a83-9c15-e8f0a3d7b264 ([UPDATE_GAME_MODE])`],
  [`[${TS}] [Render thread/INFO] (Minecraft) [System] [CHAT] [HRP] Mei (staff) : rappel, event au terrain d'entraînement à 22 h !`],
  [`[${TS}] [Render thread/INFO] (Minecraft) Loaded 17 advancements`],
  [`[${TS}] [Render thread/INFO] (Minecraft) [System] [CHAT] [Konoha] Kenji Aburame : Les insectes s'agitent du côté de la forêt, restez sur vos gardes.`],
];

const SHUTDOWN: LogSpec[] = [
  `[${TS}] [Render thread/INFO] (Minecraft) Stopping!`,
  `[${TS}] [Render thread/INFO] (plasmovoice) Disconnected from the voice server`,
  `[${TS}] [Render thread/INFO] (reborn-ost) Lecture arrêtée, ressources OpenAL libérées`,
];

const RUN_LINES = 300;

/** Journal complet d'une partie (~300 lignes) qui se termine à `endAt`. */
function buildRun(endAt: number, ended: boolean, levels: LevelState = newLevelState()): LogLine[] {
  const tail = ended ? SHUTDOWN : [];
  let room = RUN_LINES - STARTUP.length - JOIN.length - tail.length;
  const ingame: LogSpec[] = [];
  for (let i = 0; room > 0 && i < INGAME.length * 8; i++) {
    const group = INGAME[i % INGAME.length];
    if (group.length > room) continue;
    ingame.push(...group);
    room -= group.length;
  }
  const startup = materialize(STARTUP, endAt - 170_000, endAt - 110_000, levels);
  const rest = materialize([...JOIN, ...ingame, ...tail], endAt - 105_000, endAt, levels);
  return startup.concat(rest);
}

function localStamp(t: number, sep: { date: string; mid: string; time: string }): string {
  const d = new Date(t);
  return (
    `${d.getFullYear()}${sep.date}${pad(d.getMonth() + 1)}${sep.date}${pad(d.getDate())}` +
    `${sep.mid}${pad(d.getHours())}${sep.time}${pad(d.getMinutes())}${sep.time}${pad(d.getSeconds())}`
  );
}

const CRASH_EXCEPTION = 'java.lang.NullPointerException: Cannot read field "pivotX" because "part" is null';
const CRASH_DESCRIPTION = "Rendering entity in world";

/** Exception de rendu + rapport de crash Minecraft (sortie standard). */
function crashSpecs(at: number): LogSpec[] {
  const stack = [
    "\tat traben.entity_model_features.models.parts.EMFModelPartRoot.render(EMFModelPartRoot.java:214)",
    "\tat net.minecraft.client.model.Model.renderToBuffer(Model.java:62)",
    "\tat net.minecraft.client.renderer.entity.LivingEntityRenderer.render(LivingEntityRenderer.java:139)",
    "\tat net.minecraft.client.renderer.entity.EntityRenderDispatcher.render(EntityRenderDispatcher.java:187)",
  ];
  const report = `${DATA_DIR}\\crash-reports\\crash-${localStamp(at, { date: "-", mid: "_", time: "." })}-client.txt`;
  return [
    `[${TS}] [Render thread/ERROR] (Minecraft) Reported exception thrown!`,
    `net.minecraft.ReportedException: ${CRASH_DESCRIPTION}`,
    "\tat net.minecraft.client.renderer.entity.EntityRenderDispatcher.render(EntityRenderDispatcher.java:195)",
    "\tat net.minecraft.client.renderer.LevelRenderer.renderEntity(LevelRenderer.java:1544)",
    "\tat net.minecraft.client.renderer.LevelRenderer.renderLevel(LevelRenderer.java:1032)",
    "\tat net.minecraft.client.renderer.GameRenderer.renderLevel(GameRenderer.java:1142)",
    "\tat net.minecraft.client.Minecraft.runTick(Minecraft.java:1356)",
    "\tat net.minecraft.client.Minecraft.run(Minecraft.java:912)",
    "\tat net.minecraft.client.main.Main.main(Main.java:266)",
    `Caused by: ${CRASH_EXCEPTION}`,
    ...stack.slice(0, 3),
    "\t... 7 more",
    "---- Minecraft Crash Report ----",
    "// Oops.",
    "",
    `Time: ${localStamp(at, { date: "-", mid: " ", time: ":" })}`,
    `Description: ${CRASH_DESCRIPTION}`,
    "",
    CRASH_EXCEPTION,
    ...stack,
    "",
    "A detailed walkthrough of the error, its code path and all known details is as follows:",
    "---------------------------------------------------------------------------------------",
    "",
    "-- Head --",
    "Thread: Render thread",
    "Stacktrace:",
    ...stack.slice(0, 2),
    "",
    "-- Entity being rendered --",
    "Details:",
    "\tEntity Type: minecraft:wolf (net.minecraft.world.entity.animal.wolf.Wolf)",
    "\tEntity's Exact location: -412.53, 71.00, 1288.21",
    "",
    "-- System Details --",
    "Details:",
    `\tMinecraft Version: ${MC_VERSION}`,
    `\tMinecraft Version ID: ${MC_VERSION}`,
    "\tOperating System: Windows 11 (amd64) version 10.0",
    "\tJava Version: 25.0.1, Microsoft",
    "\tJava VM Version: OpenJDK 64-Bit Server VM (mixed mode), Microsoft",
    "\tMemory: 1288451072 bytes (1228 MiB) / 4294967296 bytes (4096 MiB) up to 4294967296 bytes (4096 MiB)",
    "\tCPUs: 16",
    "\tProcessor Vendor: AuthenticAMD",
    "\tProcessor Name: AMD Ryzen 7 5800X 8-Core Processor",
    "\tGraphics card #0 name: NVIDIA GeForce RTX 3070",
    `\tLaunched Version: fabric-loader-0.19.3-${MC_VERSION}`,
    "\tBackend library: LWJGL version 3.3.6",
    "\tBackend API: NVIDIA GeForce RTX 3070/PCIe/SSE2 GL version 4.6.0 NVIDIA 566.36",
    "\tWindow size: 1920x1080",
    "\tRender Distance: 12/12 chunks",
    "\tResource Packs: vanilla, fabric, reborn-hud, reborn-ost, server",
    "\tCurrent Language: fr_fr",
    "\tLocale: fr_FR",
    "\tIs Modded: Definitely; Client brand changed to 'fabric'",
    "\tType: Client (map_client.txt)",
    "",
    `#@!@# Game crashed! Crash report saved to: #@!@# ${report}`,
    `[${TS}] [Render thread/INFO] (Minecraft) Stopping!`,
  ];
}

/** Ce que le LogAnalyzer Rust tire de ce rapport (MC_CRASH_REPORT, cf
 *  diagnostics.rs::crash_report : mêmes textes). */
const CRASH_DIAGNOSTIC: GameDiagnostic = {
  code: "MC_CRASH_REPORT",
  severity: "fatal",
  message: `Erreur pendant « ${CRASH_DESCRIPTION} ».`,
  hint: "Le rapport complet est dans le journal du jeu et dans le dossier crash-reports : joins-le à un ticket si le problème revient.",
  details: CRASH_EXCEPTION,
};

// ──────────────────────────────────────────────────────
//  Faux jeu : partie, flux de logs, crash
// ──────────────────────────────────────────────────────

/** Tampon « Rust » : partie en cours, ou dernière partie (il y a 2 h). */
let buffer: LogLine[] = buildRun(NOW - 2 * HOUR, true);

const game = {
  running: false,
  launching: false,
  pid: null as number | null,
  /** Classifieur des niveaux de la partie en cours (un par partie, comme
   *  le batcher de logs.rs). */
  levels: newLevelState(),
  timers: new Set<number>(),
};

const LAUNCHED: LaunchedGame = {
  pid: 18432,
  javaPath: `${DATA_DIR}\\runtime\\java-runtime-epsilon\\windows-x64\\java-runtime-epsilon\\bin\\javaw.exe`,
  minecraftVersion: MC_VERSION,
  fabricVersion: "0.19.3",
};

function later(ms: number, fn: () => void): void {
  const id = window.setTimeout(() => {
    game.timers.delete(id);
    fn();
  }, ms);
  game.timers.add(id);
}

function every(ms: number, fn: () => void): number {
  const id = window.setInterval(fn, ms);
  game.timers.add(id);
  return id;
}

function clearGameTimers(): void {
  for (const id of game.timers) {
    window.clearTimeout(id);
    window.clearInterval(id);
  }
  game.timers.clear();
}

function emitEvent<K extends TauriEventName>(name: K, payload: TauriEventMap[K]): void {
  emit(name, payload).catch((e: unknown) => console.warn(`[démo] émission de « ${name} » impossible :`, e));
}

/** Ajoute au tampon et émet un paquet `game:log-batch`, comme le backend. */
function pushLines(lines: LogLine[]): void {
  if (!lines.length) return;
  buffer = buffer.concat(lines);
  if (buffer.length > LOG_BUFFER_MAX) buffer = buffer.slice(buffer.length - LOG_BUFFER_MAX);
  emitEvent("game:log-batch", { lines });
}

/** Un paquet par seconde : 1 à 3 groupes de lignes de jeu. */
function startInGameStream(): void {
  every(1000, () => {
    const specs: LogSpec[] = [];
    const groups = 1 + Math.floor(Math.random() * 3);
    for (let i = 0; i < groups; i++) {
      specs.push(...INGAME[Math.floor(Math.random() * INGAME.length)]);
    }
    const now = Date.now();
    pushLines(materialize(specs, now, now, game.levels));
  });
}

/** Lancement réel : le démarrage défile par paquets (250 ms), la fenêtre
 *  « s'ouvre » sur le même marqueur que dans le backend. */
function streamStartup(): void {
  const specs = [...STARTUP, ...JOIN];
  let index = 0;
  let windowReady = false;
  const id = every(250, () => {
    const chunk = specs.slice(index, index + 24);
    index += chunk.length;
    const now = Date.now();
    const lines = materialize(chunk, now - 200, now, game.levels);
    pushLines(lines);
    if (!windowReady && lines.some((l) => isWindowReadyMarker(l.text))) {
      windowReady = true;
      emitEvent("game:window-ready", {});
    }
    if (index >= specs.length) {
      window.clearInterval(id);
      game.timers.delete(id);
      if (!windowReady) emitEvent("game:window-ready", {});
      startInGameStream();
    }
  });
}

/** `prefilled` : partie déjà lancée (tampon plein) ; `live` : vrai démarrage. */
function startGame(mode: "prefilled" | "live"): LaunchedGame {
  clearGameTimers();
  game.running = true;
  game.pid = LAUNCHED.pid;
  game.levels = newLevelState();
  if (mode === "prefilled") {
    buffer = buildRun(Date.now(), false, game.levels);
    emitEvent("game:started", LAUNCHED);
    later(900, () => {
      emitEvent("game:window-ready", {});
      startInGameStream();
    });
  } else {
    buffer = [];
    emitEvent("game:started", LAUNCHED);
    streamStartup();
  }
  return LAUNCHED;
}

function endGame(): void {
  clearGameTimers();
  game.running = false;
  game.pid = null;
}

/** Crash : rapport dans le journal, diagnostic, puis game:exited et
 *  game:crashed dans le même ordre que await_game_end (game.rs). Un crash
 *  Minecraft sort avec System.exit(-1) : code -1 sous Windows. */
function crashGame(): void {
  if (!game.running) return;
  clearGameTimers();
  const at = Date.now();
  pushLines(materialize(crashSpecs(at), at - 400, at, game.levels));
  emitEvent("game:diagnostic", CRASH_DIAGNOSTIC);
  window.setTimeout(() => {
    endGame();
    emitEvent("game:exited", -1);
    emitEvent("game:crashed", {
      exitCode: -1,
      stderrPath: `${DATA_DIR}\\logs\\last-crash.log`,
      stderrTail: buffer
        .slice(-100)
        .map((l) => l.text)
        .join("\n"),
    });
  }, 600);
}

async function launchGame(scenario: Scenario): Promise<LaunchedGame> {
  if (scenario === "erreur-jouer") {
    await wait(600);
    throw ipcError("mc_session_expired", "Ta session Minecraft a expiré : reconnecte-toi avec Microsoft.");
  }
  if (game.running || game.launching) {
    throw ipcError("already_running", "Le jeu est déjà lancé.");
  }
  game.launching = true;
  try {
    const step = (n: number, label: string, current: number | null = null, total: number | null = null) =>
      emitEvent("launch:progress", { step: n, totalSteps: 6, label, current, total });
    step(1, "Runtime Java");
    await wait(350);
    step(2, "Métadonnées Minecraft");
    await wait(350);
    step(3, "Bibliothèques & natives");
    await wait(400);
    const assets = 4723;
    for (const done of [0, 1180, 2361, 3542, assets]) {
      step(4, "Téléchargement des ressources", done, done === 0 ? null : assets);
      await wait(250);
    }
    step(5, "Fabric Loader");
    await wait(350);
    step(6, "Démarrage du jeu");
    await wait(400);
    return startGame("live");
  } finally {
    game.launching = false;
  }
}

/** « Arrêter le jeu » : rend la main quand le processus est fermé, puis
 *  game:exited (arrêt volontaire : pas de rapport de crash). */
async function stopGame(): Promise<null> {
  if (!game.running) throw ipcError("not_running", "Aucune partie n'est en cours.");
  clearGameTimers();
  await wait(700);
  endGame();
  emitEvent("game:exited", 1);
  return null;
}

// ──────────────────────────────────────────────────────
//  Commandes Tauri simulées (réponses typées)
// ──────────────────────────────────────────────────────

interface Commands {
  auth_resume_session: AuthSession | null;
  auth_me: LauncherUser;
  auth_refresh_access: LauncherUser | null;
  auth_login_microsoft: AuthSession;
  auth_login_with_saved_account: AuthSession;
  auth_dev_login: AuthSession;
  auth_forget_account: null;
  auth_logout: null;
  discord_link_start: { url: string; state: string };
  discord_unlink: null;
  update_display_name: { displayName: string };
  prefs_get: Preferences;
  prefs_set: Preferences;
  system_specs_get: SystemSpecs;
  network_ping_health: HealthPing;
  server_status: ServerStatus;
  me_badges: Badges;
  me_mark_read: Badges;
  launcher_game_status: GameStatus;
  launcher_game_info: GameInfo;
  launcher_check_update: UpdatePreview;
  launcher_apply_update: UpdateStatus;
  launcher_launch_game: LaunchedGame;
  launcher_launch_second_instance: LaunchedGame;
  launcher_stop_game: null;
  launcher_logs_snapshot: LogLine[];
  launcher_logs_export: string;
  launcher_mods_list: ModEntry[];
  launcher_mods_purge: string[];
  launcher_list_optional_mods: OptionalMod[];
  launcher_set_mod_pref: null;
  launcher_open_install_dir: null;
  launcher_reinstall_all: null;
  launcher_repair: null;
  launcher_clean_obsolete: CleanResult;
  whitelist_me: WhitelistMeResponse;
  whitelist_messages_list: WhitelistMessage[];
  whitelist_messages_post: WhitelistMessage;
  whitelist_oral_slots: OralSlotsResponse;
  tickets_list: TicketSummary[];
  tickets_detail: TicketDetail;
  tickets_create: TicketDetail;
  tickets_post_message: TicketMessage;
  tickets_delete: null;
  tickets_reclaim: null;
  upload_attachment: string;
  patchnotes_list: PatchNoteListResponse;
  patchnotes_detail: PatchNoteDetail;
  rules_current: RulesDocument;
  lore_current: LoreDocument;
  screenshots_list: ScreenshotItem[];
  screenshots_process_pending_shares: PendingSharesResult;
  shots_feed: ShotFeed;
}

type Handlers = {
  [K in keyof Commands]: (args: Args) => Commands[K] | Promise<Commands[K]>;
};

function createHandlers(scenario: Scenario): Handlers {
  const session = (): AuthSession => ({ user });
  return {
    // ── Compte ──
    auth_resume_session: session,
    auth_me: () => user,
    auth_refresh_access: () => user,
    auth_login_microsoft: async () => {
      await wait(900);
      return session();
    },
    auth_login_with_saved_account: async () => {
      await wait(500);
      return session();
    },
    auth_dev_login: session,
    auth_forget_account: () => null,
    auth_logout: () => null,
    discord_link_start: () => {
      // Liaison « validée dans le navigateur » au bout de 3 s (auth_me la voit).
      window.setTimeout(() => {
        user = { ...user, discord: { ...DISCORD, linkedAt: new Date().toISOString() } };
      }, 3000);
      return { url: "https://discord.com/oauth2/authorize", state: "demo-state" };
    },
    discord_unlink: () => {
      user = { ...user, discord: null };
      return null;
    },
    update_display_name: (a) => {
      const displayName = str(a, "displayName").trim() || user.minecraftUsername;
      user = { ...user, displayName };
      return { displayName };
    },

    // ── Préférences, machine, réseau ──
    prefs_get: () => ({ ...prefs }),
    prefs_set: (a) => {
      const value = a.value as Partial<Preferences> | undefined;
      prefs = { ...prefs, ...(value ?? {}) };
      return { ...prefs };
    },
    system_specs_get: () => SPECS,
    network_ping_health: async () => {
      if (scenario !== "hors-ligne") return { ok: true, cause: null };
      await wait(600);
      return { ok: false, cause: "délai dépassé" };
    },
    server_status: () => ({
      online: true,
      players: { online: 23, max: 120 },
      version: `Purpur ${MC_VERSION}`,
      motd: "Reborn Roleplay, Naruto Edition",
      latencyMs: 38,
      fetchedAt: new Date().toISOString(),
    }),
    me_badges: () => ({ ...badges }),
    me_mark_read: (a) => {
      const scope = str(a, "scope");
      if (scope === "tickets") badges = { ...badges, unreadTickets: 0 };
      if (scope === "patchnotes") badges = { ...badges, unreadPatchnotes: 0 };
      return { ...badges };
    },

    // ── Jeu ──
    launcher_game_status: () => ({ running: game.running, pid: game.pid }),
    launcher_game_info: () => GAME_INFO,
    launcher_check_update: async () => {
      await wait(200);
      return UPDATE_PREVIEW;
    },
    launcher_apply_update: async () => {
      await wait(800);
      return { kind: "up_to_date", version: MANIFEST_VERSION };
    },
    launcher_launch_game: () => launchGame(scenario),
    launcher_launch_second_instance: async () => {
      await wait(800);
      return { ...LAUNCHED, pid: 19876 };
    },
    launcher_stop_game: stopGame,
    launcher_logs_snapshot: () => buffer.slice(),
    launcher_logs_export: async () => {
      await wait(300);
      const d = new Date();
      const stamp =
        `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}_` +
        `${pad(d.getUTCHours())}-${pad(d.getUTCMinutes())}-${pad(d.getUTCSeconds())}`;
      return `${DATA_DIR}\\logs\\reborn-${stamp}.log`;
    },
    launcher_mods_list: () => MODS,
    launcher_mods_purge: () => [],
    launcher_list_optional_mods: () => optionalMods.map((m) => ({ ...m })),
    launcher_set_mod_pref: (a) => {
      const filename = str(a, "filename");
      const enabled = a.enabled === true;
      optionalMods = optionalMods.map((m) => (m.filename === filename ? { ...m, enabled } : m));
      return null;
    },
    launcher_open_install_dir: () => null,
    launcher_reinstall_all: async () => {
      await wait(800);
      return null;
    },
    launcher_repair: () => null,
    launcher_clean_obsolete: async () => {
      await wait(600);
      return {
        removed: ["sodium-fabric-0.7.2+mc26.2.jar", "reborn-hud-0.4.190.jar"],
        freedMb: 31.4,
      };
    },

    // ── Whitelist ──
    whitelist_me: () => ({ application: WHITELIST }),
    whitelist_messages_list: () => whitelistMessages.slice(),
    whitelist_messages_post: (a) => {
      const message: WhitelistMessage = {
        id: `wm-${Date.now()}`,
        applicationId: WHITELIST.id,
        authorType: "USER",
        authorId: user.id,
        authorName: user.displayName ?? user.minecraftUsername,
        content: str(a, "content"),
        attachments: Array.isArray(a.attachmentUrls)
          ? a.attachmentUrls.map((url) => ({ url: String(url) }))
          : [],
        createdAt: new Date().toISOString(),
      };
      whitelistMessages = [...whitelistMessages, message];
      return message;
    },
    whitelist_oral_slots: () => ORAL_SLOTS,

    // ── Tickets ──
    tickets_list: () =>
      tickets
        .slice()
        .sort((x, y) => y.updatedAt.localeCompare(x.updatedAt))
        .map(ticketSummary),
    tickets_detail: (a) => findTicket(str(a, "id")),
    tickets_create: async (a) => {
      await wait(400);
      const at = new Date().toISOString();
      const created: TicketDetail = {
        id: crypto.randomUUID(),
        category: (str(a, "category") || "OTHER") as TicketCategory,
        subject: str(a, "subject"),
        status: "OPEN",
        createdAt: at,
        updatedAt: at,
        assignee: null,
        assignedAt: null,
        messages: [
          {
            id: `tm-${Date.now()}`,
            authorId: user.id,
            authorName: user.displayName ?? user.minecraftUsername,
            isStaff: false,
            content: str(a, "message"),
            attachments: [],
            createdAt: at,
          },
        ],
      };
      tickets = [created, ...tickets];
      return created;
    },
    tickets_post_message: (a) => {
      const ticket = findTicket(str(a, "id"));
      const at = new Date().toISOString();
      const message: TicketMessage = {
        id: `tm-${Date.now()}`,
        authorId: user.id,
        authorName: user.displayName ?? user.minecraftUsername,
        isStaff: false,
        content: str(a, "content"),
        attachments: Array.isArray(a.attachmentUrls)
          ? a.attachmentUrls.map((url) => ({ url: String(url) }))
          : [],
        createdAt: at,
      };
      tickets = tickets.map((t) =>
        t.id === ticket.id ? { ...t, updatedAt: at, messages: [...t.messages, message] } : t,
      );
      return message;
    },
    tickets_delete: (a) => {
      const id = str(a, "id");
      tickets = tickets.filter((t) => t.id !== id);
      return null;
    },
    tickets_reclaim: () => null,
    // Pas d'hébergement en démo : l'image jointe reste dans la page.
    upload_attachment: (a) => `data:${str(a, "mimeType") || "image/png"};base64,${str(a, "dataBase64")}`,

    // ── Contenus ──
    patchnotes_list: (a) => {
      const page = typeof a.page === "number" && a.page > 0 ? a.page : 1;
      const size = typeof a.size === "number" && a.size > 0 ? a.size : 10;
      const items = PATCHNOTES.slice((page - 1) * size, page * size).map(toSummary);
      return { items, total: PATCHNOTES.length };
    },
    patchnotes_detail: (a) => {
      const note = PATCHNOTES.find((p) => p.id === str(a, "id"));
      if (!note) throw ipcError("not_found", "Cette note de mise à jour n'existe plus.");
      return note;
    },
    rules_current: () => RULES,
    lore_current: () => LORE,
    screenshots_list: () => [],
    screenshots_process_pending_shares: () => ({ shared: 0, failed: 0 }),
    shots_feed: () => ({ items: [], nextCursor: null }),
  };
}

/** Commandes des plugins Tauri (store, updater, fenêtre, opener…). */
function pluginCommand(cmd: string, a: Args): unknown {
  switch (cmd) {
    case "plugin:store|load":
    case "plugin:store|get_store":
      return 1;
    case "plugin:store|get":
      return a.key === "accounts" ? [savedAccounts, true] : [null, false];
    case "plugin:store|set":
      if (a.key === "accounts" && Array.isArray(a.value)) savedAccounts = a.value as SavedAccount[];
      return null;
    case "plugin:store|has":
      return a.key === "accounts";
    case "plugin:updater|check":
      return null; // launcher à jour
    case "plugin:app|version":
      return pkg.version;
    case "plugin:app|name":
      return "Reborn Launcher";
    case "plugin:notification|is_permission_granted":
      return false;
    case "plugin:notification|request_permission":
      return "denied";
  }
  if (/^plugin:(store|resources|window|webview|opener|process|notification)\|/.test(cmd)) {
    if (/^plugin:(window|opener|process)\|/.test(cmd)) {
      console.info(`[démo] action simulée : ${cmd}`, a);
    }
    return null;
  }
  console.info(`[démo] commande sans réponse simulée : ${cmd}`, a);
  return cmd.endsWith("_list") ? [] : null;
}

// ──────────────────────────────────────────────────────
//  Évènements : écouteurs suivis + correctif du mock
// ──────────────────────────────────────────────────────

const listeners = new Map<string, Set<number>>();

const listenerCount = (event: string) => listeners.get(event)?.size ?? 0;

type IpcInvoke = (cmd: string, args?: unknown, options?: unknown) => Promise<unknown>;

/**
 * Enveloppe l'invoke installé par mockIPC pour :
 *  - savoir quels évènements sont écoutés (les scénarios attendent que
 *    l'application soit abonnée avant d'émettre) ;
 *  - corriger le retrait des écouteurs : mockIPC 2.11 cherche `id` alors
 *    que l'API envoie `eventId`, si bien qu'aucun écouteur n'était retiré
 *    (avertissement « Couldn't find callback id » à chaque émission).
 */
function trackListeners(): void {
  const internals = (window as unknown as { __TAURI_INTERNALS__: { invoke: IpcInvoke } })
    .__TAURI_INTERNALS__;
  const mocked = internals.invoke;
  internals.invoke = async (cmd, args, options) => {
    const a = (args ?? {}) as Args;
    if (cmd === "plugin:event|unlisten" && typeof a.eventId === "number") {
      listeners.get(String(a.event))?.delete(a.eventId);
      return mocked(cmd, { ...a, id: a.eventId }, options);
    }
    const result = await mocked(cmd, args, options);
    if (cmd === "plugin:event|listen" && typeof result === "number") {
      const event = String(a.event);
      const set = listeners.get(event) ?? new Set<number>();
      set.add(result);
      listeners.set(event, set);
    }
    return result;
  };
}

/** Attend que le layout connecté soit monté, abonné aux évènements du jeu,
 *  et que le fondu d'entrée de la page soit fini : data-game="running" fige
 *  les animations CSS, une page encore en fondu resterait invisible. */
function shellReady(timeoutMs = 20_000): Promise<void> {
  const startedAt = Date.now();
  return new Promise((resolve) => {
    const tick = () => {
      const main = document.getElementById("reborn-main");
      const ready =
        listenerCount("game:started") > 0 &&
        listenerCount("game:crashed") > 0 &&
        main !== null &&
        getComputedStyle(main).opacity === "1";
      if (ready || Date.now() - startedAt > timeoutMs) {
        window.setTimeout(resolve, 500);
        return;
      }
      window.setTimeout(tick, 100);
    };
    tick();
  });
}

async function openJournal(): Promise<void> {
  const { openLogsPanel } = await import("../components/game-log/logs-panel-store");
  openLogsPanel();
}

async function runScenario(scenario: Scenario): Promise<void> {
  if (scenario !== "en-jeu" && scenario !== "journal" && scenario !== "crash") return;
  await shellReady();
  startGame("prefilled");
  if (scenario === "journal") later(1200, () => void openJournal());
  if (scenario === "crash") later(2600, crashGame);
}

// ──────────────────────────────────────────────────────
//  Installation
// ──────────────────────────────────────────────────────

function asArgs(payload: InvokeArgs | undefined): Args {
  if (!payload || Array.isArray(payload) || payload instanceof ArrayBuffer || payload instanceof Uint8Array) {
    return {};
  }
  return payload;
}

function install(scenario: Scenario): void {
  const handlers = createHandlers(scenario);
  const lookup = (cmd: string) =>
    Object.prototype.hasOwnProperty.call(handlers, cmd)
      ? (handlers[cmd as keyof Commands] as (a: Args) => unknown)
      : null;

  mockWindows("main");
  mockIPC(
    async (cmd, payload) => {
      const args = asArgs(payload);
      const handler = lookup(cmd);
      return handler ? handler(args) : pluginCommand(cmd, args);
    },
    { shouldMockEvents: true },
  );
  trackListeners();

  const others = (Object.keys(SCENARIOS) as Scenario[]).filter((s) => s !== scenario).join(", ");
  console.info(
    `[démo] scénario « ${scenario} » : ${SCENARIOS[scenario]}. Autres : ${others}. ?demo=off pour quitter.`,
  );
  void runScenario(scenario);
}

/** Jamais dans une vraie fenêtre Tauri (tauri dev) : mockIPC remplacerait le
 *  vrai backend (un « Jouer » ne lancerait plus rien). Tauri injecte
 *  __TAURI_INTERNALS__ avant les scripts de la page, d'où ce test. */
function bootScenario(): Scenario | null {
  if ("__TAURI_INTERNALS__" in window) {
    console.warn("[démo] ignoré : vraie fenêtre Tauri, le mode démo ne tourne que dans un navigateur.");
    return null;
  }
  return resolveScenario();
}

const scenario = bootScenario();
if (scenario) install(scenario);

/** Faux si ?demo=off : main.tsx démarre alors l'application normalement. */
export const demoActive = scenario !== null;
