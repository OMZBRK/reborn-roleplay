// MODE DÉMO : développement uniquement, absent du build de production.
//
// Fait tourner le front dans un simple navigateur (vite dev sur
// http://localhost:1420) avec un faux backend Tauri, pour photographier les
// vrais écrans sans Rust ni API :
//
//   http://localhost:1420/?demo=<scénario>     (liste dans SCENARIOS)
//   http://localhost:1420/mods?demo=accueil    (n'importe quelle page)
//   http://localhost:1420/?demo=off            (retour au mode navigateur)
//   http://localhost:1420/?demo=maquette&ecran=L1   (maquettes, cf plus bas)
//   http://localhost:1420/whitelist?demo=suivi&fige=1   (animations coupées)
//
// &fige=1 : framer-motion et les animations CSS coupés, chaque bloc est
// d'emblée dans son état final. Pour Edge sans fenêtre (--headless=new
// --virtual-time-budget) : sans lui, les entrées animées (messages du fil,
// cartes) restent figées à leur première image, invisibles.
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
//
// Scénario « maquette » : pas de faux backend, pas d'application. main.tsx
// rend à la place l'écran de maquette demandé par &ecran= (L1 … L11, L10a,
// L10b et variantes, liste dans src/dev/maquettes/index.tsx), chargé à la
// demande par maquette() ci-dessous. Données fictives, 1280 × 800.

import { MotionGlobalConfig } from "framer-motion";
import { mockIPC, mockWindows } from "@tauri-apps/api/mocks";
import { emit } from "@tauri-apps/api/event";
import type { InvokeArgs } from "@tauri-apps/api/core";
import type { ReactNode } from "react";
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
  PreparedGame,
  OptionalMod,
  UpdatePreview,
  UpdateStatus,
} from "../lib/launcher";
import type {
  Badges,
  DiscordCommunity,
  LoreDocument,
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
import type {
  AccesJeu,
  BrouillonVue,
  CandidatureEnvoi,
  CandidatureMe,
  CandidatureOptions,
  CandidatureRenvoi,
  CandidatureV2,
  DernierePlace,
  DonneesBrouillon,
  EtatDouane,
  FenetreEntree,
  PhaseCandidature,
  ReservationDouane,
  SeanceDouane,
  SuiviCandidature,
  VoixDeposee,
  VoixVue,
} from "../lib/candidature";
import type { MesPersonnages, PersonnageCompte } from "../lib/personnages";

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
  candidature: "candidat : candidature en cours (brouillon serveur, voix déposée), « Jouer » refusé",
  "candidature-complete": "candidat : candidature complète, prête à partir (onglet Envoi)",
  suivi: "candidat : écrit envoyé, en cours de relecture (frise, fil avec le staff)",
  "a-revoir": "candidat : écrit à revoir, partie RP rouverte",
  refuse: "candidat : écrit refusé, nouvelle candidature possible dans 22 h",
  reserver: "candidat : écrit accepté, séance de douane à réserver",
  "douane-demain": "candidat : place réservée demain soir, entrée pas encore ouverte",
  douane: "candidat : écrit accepté, place réservée, entrée ouverte (« Passer la douane »)",
  "douane-manquee": "candidat : séance manquée il y a 20 min (« Séance manquée », réserver une autre séance)",
  "douane-a-revoir": "candidat : douane à revoir, nouvelle séance réservable dès maintenant pour demain",
  "premier-personnage": "joueur avec son seul premier personnage (Mes personnages : emplacement libre)",
  "sans-personnage": "joueur whitelisté sans personnage (Mes personnages vide, « Lancer le jeu »)",
  maquette:"maquettes candidature, douane, accueil et personnages (&ecran=L1 … L11, L10a, L10b)",
} as const;

type Scenario = keyof typeof SCENARIOS;

/** Scénarios « candidat » : compte PLAYER, candidature v2 et douane. */
const CANDIDATS = new Set<Scenario>([
  "candidature",
  "candidature-complete",
  "suivi",
  "a-revoir",
  "refuse",
  "reserver",
  "douane-demain",
  "douane",
  "douane-manquee",
  "douane-a-revoir",
]);

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

// ──────────────────────────────────────────────────────
//  Candidature v2 et douane (CANDIDATURE_ET_DOUANE §2, §3.3)
//  ?demo=candidature : candidat, brouillon serveur avec sa voix.
//  ?demo=douane : écrit accepté, place réservée, entrée ouverte.
//  Les autres scénarios : compte whitelisté (phase ACCEPTE).
// ──────────────────────────────────────────────────────

const dans = (ms: number) => new Date(NOW + ms).toISOString();

const OPTIONS_CANDIDATURE: CandidatureOptions = {
  formVersion: 2,
  villages: [
    { valeur: "Konohagakure", court: "Konoha", pays: "Pays du Feu" },
    { valeur: "Sunagakure", court: "Suna", pays: "Pays du Vent" },
    { valeur: "Kirigakure", court: "Kiri", pays: "Pays de l'Eau" },
    { valeur: "Kumogakure", court: "Kumo", pays: "Pays de la Foudre" },
    { valeur: "Iwagakure", court: "Iwa", pays: "Pays de la Terre" },
    { valeur: "Amegakure", court: "Ame", pays: "Pays de la Pluie" },
  ],
  clans: (
    [
      ["Uchiha", "Konohagakure"], ["Senju", "Konohagakure"], ["Hyuga", "Konohagakure"],
      ["Nara", "Konohagakure"], ["Sarutobi", "Konohagakure"], ["Aburame", "Konohagakure"],
      ["Akimichi", "Konohagakure"], ["Yamanaka", "Konohagakure"], ["Hatake", "Konohagakure"],
      ["Uzumaki", "Konohagakure"], ["Kurama", "Konohagakure"], ["Sabaku", "Sunagakure"],
      ["Hoki", "Sunagakure"], ["Hozuki", "Kirigakure"], ["Hoshigaki", "Kirigakure"],
      ["Kaguya", "Kirigakure"], ["Yuki", "Kirigakure"], ["Terumi", "Kirigakure"],
      ["Yotsuki", "Kumogakure"], ["Kamizuru", "Iwagakure"], ["Onoki", "Iwagakure"],
    ] as const
  ).map(([nom, village]) => ({ nom, village, surSession: true })),
  sansClan: "Autre",
  bornes: {
    playerAge: { min: 15, max: 99 },
    availability: { min: 20, max: 2000 },
    firstName: { min: 3, max: 20 },
    lastName: { min: 3, max: 20 },
    characterAge: { min: 10, max: 60 },
    history: { min: 150, max: 12000 },
    objectives: { min: 400, max: 8000 },
    support: { min: 0, max: 500 },
    voixSec: { min: 15, max: 60 },
  },
  listes: {
    rpYears: [
      { valeur: "JAMAIS", libelle: "Jamais" },
      { valeur: "MOINS_D_UN_AN", libelle: "Moins d'un an" },
      { valeur: "UN_A_DEUX_ANS", libelle: "1 à 2 ans" },
      { valeur: "TROIS_A_CINQ_ANS", libelle: "3 à 5 ans" },
      { valeur: "SIX_ANS_ET_PLUS", libelle: "6 ans et plus" },
    ],
    narutoKnowledge: [
      { valeur: "DEBUTANT", libelle: "Débutant" },
      { valeur: "QUELQUES_ARCS", libelle: "Quelques arcs" },
      { valeur: "BONNE_CONNAISSANCE", libelle: "Bonne connaissance" },
      { valeur: "INCOLLABLE", libelle: "Incollable, Boruto compris" },
    ],
    discoverySource: [
      { valeur: "DISCORD", libelle: "Discord" },
      { valeur: "TIKTOK", libelle: "TikTok" },
      { valeur: "YOUTUBE", libelle: "YouTube" },
      { valeur: "AMI", libelle: "Un ami" },
      { valeur: "AUTRE_SERVEUR", libelle: "Un autre serveur" },
      { valeur: "RECHERCHE", libelle: "Une recherche" },
      { valeur: "AUTRE", libelle: "Autre" },
    ],
    characterType: [
      { valeur: "NINJA", libelle: "Ninja" },
      { valeur: "CIVIL", libelle: "Civil" },
    ],
    characterSex: [
      { valeur: "HOMME", libelle: "Homme" },
      { valeur: "FEMME", libelle: "Femme" },
    ],
  },
  voix: { types: ["audio/webm", "audio/ogg"], octetsMax: 1536 * 1024, dureeMinSec: 15, dureeMaxSec: 60 },
  delaiNouvelleCandidatureHeures: 24,
};

/** Objectifs du personnage de démonstration (au-dessus des 400 caractères). */
const OBJECTIFS_DEMO =
  "Entrer à l'Académie de Kiri, se faire une place malgré ses origines modestes, et découvrir ce qui est vraiment arrivé au bateau de son père. " +
  "À plus long terme, elle veut rejoindre les gardes-côtes du village, apprendre à lire la brume plutôt qu'à la craindre, " +
  "et nouer des liens avec les autres genin, quitte à se heurter aux familles installées qui voient d'un mauvais œil une fille de pêcheur. " +
  "Un jour, peut-être, rendre à la marchande de filets ce qu'elle lui a appris.";

/** Formulaire du candidat de ?demo=candidature : onglets Vous, Personnage et
 *  Village remplis, histoire commencée (sous le minimum), objectifs vides. */
const BROUILLON_DEMO: DonneesBrouillon = {
  playerAge: 19,
  rpYears: "UN_A_DEUX_ANS",
  narutoKnowledge: "BONNE_CONNAISSANCE",
  discoverySource: "TIKTOK",
  availability: "Soirs en semaine après 20 h, et le samedi après-midi.",
  firstName: "Itsuki",
  lastName: "Kaneda",
  village: "Kirigakure",
  characterType: "NINJA",
  characterSex: "FEMME",
  characterAge: 15,
  history:
    "Née sur une île de pêcheurs au large de Kiri, Itsuki a appris à nager avant de marcher.",
  objectives: "",
  support: "",
};

/** ?demo=candidature-complete : tous les onglets remplis, il ne manque que
 *  les deux cases de l'onglet Envoi. */
const BROUILLON_COMPLET: DonneesBrouillon = {
  ...BROUILLON_DEMO,
  history:
    "## Une enfance dans la brume\n\n" +
    "Née sur une île de pêcheurs au large de Kiri, **Itsuki** a appris à nager avant de marcher. " +
    "Quand la brume a avalé le bateau de son père, elle a juré de devenir assez forte pour la percer.\n\n" +
    "## La marchande de filets\n\n" +
    "Recueillie par une ancienne kunoichi devenue marchande de filets, elle apprend la patience, le silence, " +
    "et le poids des secrets du village. Le soir, elle répare les mailles en écoutant les histoires de la guerre :\n\n" +
    "- les épéistes de la Brume, qu'on craignait jusqu'à Konoha ;\n" +
    "- les années sanglantes, dont personne ne parle sur le port ;\n" +
    "- et ce bateau, retrouvé vide, que les gardes-côtes ont classé trop vite.\n\n" +
    "> « La brume ne cache rien à qui sait l'écouter. »\n\n" +
    "Aujourd'hui, elle frappe à la porte de l'Académie avec des mains abîmées par les cordages et une seule question.",
  objectives: OBJECTIFS_DEMO,
};

const VOIX_DEMO: VoixVue = { mimeType: "audio/webm", durationSec: 42, uploadedAt: ago(26 * MIN) };

/** Dossier v2 du candidat de ?demo=douane : écrit accepté hier. */
const CANDIDATURE_DOUANE: CandidatureV2 = {
  id: "9c41e7d2-3b85-4a60-bf17-6e2d8a5c0f94",
  status: "PENDING",
  hrpStatus: "APPROVED",
  rpStatus: "APPROVED",
  formVersion: 2,
  phase: "DOUANE_RESERVEE",
  playerAge: 19,
  dob: "",
  rpYears: "UN_A_DEUX_ANS",
  narutoKnowledge: "BONNE_CONNAISSANCE",
  discoverySource: "TIKTOK",
  availability: "Soirs en semaine après 20 h, et le samedi après-midi.",
  motivation: "",
  experience: "",
  firstName: "Itsuki",
  lastName: "Kaneda",
  village: "Kirigakure",
  characterType: "NINJA",
  characterSex: "FEMME",
  characterAge: 15,
  support: null,
  history:
    "Née sur une île de pêcheurs au large de Kiri, Itsuki a appris à nager avant de marcher. " +
    "Quand la brume a avalé le bateau de son père, elle a juré de devenir assez forte pour la percer. " +
    "Recueillie par une ancienne kunoichi devenue marchande de filets, elle apprend la patience, le silence, et le poids des secrets du village.",
  appearance: "",
  objectives: OBJECTIFS_DEMO,
  voix: { mimeType: "audio/webm", durationSec: 42, uploadedAt: ago(2 * DAY) },
  voiceDeletedAt: null,
  rulesAcceptedAt: ago(2 * DAY),
  voiceConsentAt: ago(2 * DAY),
  submittedAt: ago(2 * DAY),
  reviewedAt: ago(1 * DAY),
  reviewNotes: "Belle histoire, ton personnage a une vraie voix. On creusera ses liens avec le village à l'oral.",
  reviewerName: "Mei",
  writtenApprovedAt: ago(1 * DAY),
  oralStatus: "PENDING",
  oralDecidedAt: null,
  oralVia: null,
  oralReason: null,
  assignee: { username: "mei.helper" },
  assignedAt: ago(2 * DAY - 3 * HOUR),
};

/** ?demo=suivi : le même dossier, envoyé ce matin, en relecture chez Mei. */
const CANDIDATURE_ENVOYEE: CandidatureV2 = {
  ...CANDIDATURE_DOUANE,
  hrpStatus: "PENDING",
  rpStatus: "PENDING",
  phase: "ECRIT_EN_ATTENTE",
  submittedAt: ago(5 * HOUR),
  rulesAcceptedAt: ago(5 * HOUR),
  voiceConsentAt: ago(5 * HOUR),
  voix: { mimeType: "audio/webm", durationSec: 42, uploadedAt: ago(5 * HOUR + 20 * MIN) },
  reviewedAt: null,
  reviewNotes: null,
  reviewerName: null,
  writtenApprovedAt: null,
  assignedAt: ago(2 * HOUR),
};

/** ?demo=a-revoir : HRP validée, RP à revoir. */
const CANDIDATURE_A_REVOIR: CandidatureV2 = {
  ...CANDIDATURE_ENVOYEE,
  status: "NEEDS_REVISION",
  hrpStatus: "APPROVED",
  rpStatus: "NEEDS_REVISION",
  phase: "ECRIT_A_REVOIR",
  submittedAt: ago(DAY + 4 * HOUR),
  reviewedAt: ago(40 * MIN),
  reviewerName: "Mei",
  reviewNotes:
    "La partie HRP est validée. Côté RP, ton histoire donne à Itsuki le Hyoton, appris auprès d'un Yuki : " +
    "ton personnage arrive sans clan, et les clans se rejoignent en jeu, lors des sessions de clan, jamais par la candidature. " +
    "Reprends ce passage, et précise ses objectifs : ils restent trop vagues.",
};

/** ?demo=refuse : écrit refusé il y a 1 h 25, nouvelle candidature dans 22 h 35. */
const CANDIDATURE_REFUSEE: CandidatureV2 = {
  ...CANDIDATURE_ENVOYEE,
  status: "REJECTED",
  hrpStatus: "APPROVED",
  rpStatus: "REJECTED",
  phase: "REFUSE",
  submittedAt: ago(DAY + 4 * HOUR),
  reviewedAt: ago(85 * MIN),
  reviewerName: "Mei",
  reviewNotes:
    "Plusieurs passages de l'histoire sont copiés d'un autre serveur, mot pour mot. Une candidature doit être personnelle. " +
    "Tu peux en écrire une nouvelle après le délai.",
};

/** Dossier accepté (ancien formulaire) des scénarios whitelistés. */
const CANDIDATURE_ACCEPTEE: CandidatureV2 = {
  ...WHITELIST,
  formVersion: 1,
  phase: "ACCEPTE",
  playerAge: null,
  rpYears: null,
  narutoKnowledge: null,
  discoverySource: null,
  characterType: null,
  characterSex: null,
  characterAge: null,
  voix: null,
  voiceDeletedAt: null,
  rulesAcceptedAt: null,
  voiceConsentAt: null,
  reviewerName: "Mei",
  writtenApprovedAt: WHITELIST.reviewedAt,
  oralStatus: "APPROVED",
  oralDecidedAt: WHITELIST.reviewedAt,
  oralVia: "HORS_JEU",
  oralReason: null,
};

/** Séances ouvertes : celle de la place réservée (convocation dans 5 min,
 *  entrée déjà ouverte), puis deux autres, l'une complète. */
const SEANCE_EN_COURS = "1f6b9c20-84d7-4e3a-a5c1-0b7e2f9d4c68";

/** J + `jours` à `h` h `m`, heure de la machine (séances aux heures rondes). */
function aHeure(jours: number, h: number, m = 0): number {
  const d = new Date(NOW + jours * DAY);
  d.setHours(h, m, 0, 0);
  return d.getTime();
}

function seancesDemo(convocation = NOW + 5 * MIN): SeanceDouane[] {
  return [
    {
      id: SEANCE_EN_COURS,
      startAt: new Date(convocation).toISOString(),
      endAt: new Date(convocation + 2 * HOUR).toISOString(),
      placesTotal: 16,
      placesRestantes: 5,
      complet: false,
      nbExaminateurs: 2,
      vagues: [0, 30, 60, 90].map((m) => new Date(convocation + m * MIN).toISOString()),
      convocationIndicative: new Date(convocation + 90 * MIN).toISOString(),
    },
    {
      id: "6a2d4e81-5c39-4b7f-9e0a-d3c18f2b7a54",
      startAt: new Date(aHeure(2, 20)).toISOString(),
      endAt: new Date(aHeure(2, 21)).toISOString(),
      placesTotal: 8,
      placesRestantes: 0,
      complet: true,
      nbExaminateurs: 2,
      vagues: [new Date(aHeure(2, 20)).toISOString()],
      convocationIndicative: null,
    },
    {
      id: "c83f1a07-2e6b-4d95-8b40-7f9e5a1d2c36",
      startAt: new Date(aHeure(4, 21)).toISOString(),
      endAt: new Date(aHeure(4, 23)).toISOString(),
      placesTotal: 16,
      placesRestantes: 11,
      complet: false,
      nbExaminateurs: 2,
      vagues: [0, 30, 60, 90].map((m) => new Date(aHeure(4, 21) + m * MIN).toISOString()),
      convocationIndicative: new Date(aHeure(4, 21)).toISOString(),
    },
  ];
}

const REGLES_DOUANE = { avanceEntreeMin: 15, retardMaxMin: 30, douaneEnJeu: true };

function fenetreDemo(convocationAt: string): FenetreEntree {
  const h = Date.parse(convocationAt);
  const ouvre = h - REGLES_DOUANE.avanceEntreeMin * MIN;
  const ferme = h + REGLES_DOUANE.retardMaxMin * MIN;
  const t = Date.now();
  return {
    ouvreAt: new Date(ouvre).toISOString(),
    fermeAt: new Date(ferme).toISOString(),
    etat: t < ouvre ? "A_VENIR" : t > ferme ? "FERMEE" : "OUVERTE",
  };
}

function reservationDemo(seance: SeanceDouane, convocationAt: string): ReservationDouane {
  return {
    id: crypto.randomUUID(),
    seanceId: seance.id,
    statut: "BOOKED",
    convocationAt,
    fenetre: fenetreDemo(convocationAt),
    seance: { startAt: seance.startAt, endAt: seance.endAt },
    createdAt: new Date().toISOString(),
  };
}

const MESSAGES_DOUANE = (): WhitelistMessage[] => [
  {
    id: "wm-d1",
    applicationId: CANDIDATURE_DOUANE.id,
    authorType: "SYSTEM",
    authorId: null,
    authorName: null,
    content: "✅ Candidature envoyée. Elle est en cours de relecture par l'équipe whitelist.",
    attachments: [],
    createdAt: ago(2 * DAY),
  },
  {
    id: "wm-d2",
    applicationId: CANDIDATURE_DOUANE.id,
    authorType: "STAFF",
    authorId: "staff-mei",
    authorName: "Mei",
    content: "Bonjour Itsuki ! Ton écrit est validé, on se voit à la douane. Prépare-toi à parler de ton personnage.",
    attachments: [],
    createdAt: ago(1 * DAY),
  },
  {
    id: "wm-d3",
    applicationId: CANDIDATURE_DOUANE.id,
    authorType: "SYSTEM",
    authorId: null,
    authorName: null,
    content: "✅ Écrit accepté. Réserve ta séance de douane dans le launcher.",
    attachments: [],
    createdAt: ago(1 * DAY),
  },
];

/** Fil des scénarios suivi, à revoir et refusé : question du staff, réponse. */
function messagesSuivi(app: CandidatureV2, fin: string | null): WhitelistMessage[] {
  const base = Date.parse(app.submittedAt);
  const m = (
    id: string,
    authorType: WhitelistMessage["authorType"],
    content: string,
    apres: number,
  ): WhitelistMessage => ({
    id,
    applicationId: app.id,
    authorType,
    authorId: authorType === "STAFF" ? "staff-mei" : authorType === "USER" ? user.id : null,
    authorName: authorType === "STAFF" ? "Mei" : authorType === "USER" ? "Kazuki" : null,
    content,
    attachments: [],
    createdAt: new Date(base + apres).toISOString(),
  });
  const out = [
    m("wm-s1", "SYSTEM", "✅ Candidature envoyée. Elle est en cours de relecture par l'équipe whitelist.", 0),
    m(
      "wm-s2",
      "STAFF",
      "Salut ! Je prends ta candidature. Petite question en attendant : Itsuki a quel âge quand le bateau de son père disparaît ?",
      3 * HOUR,
    ),
    m("wm-s3", "USER", "Salut ! Elle a neuf ans, c'est ce qui la pousse vers l'Académie six ans plus tard.", 3 * HOUR + 21 * MIN),
    m("wm-s4", "STAFF", "Parfait, merci. La relecture continue, la réponse arrivera ici.", 3 * HOUR + 28 * MIN),
  ];
  if (fin) out.push(m("wm-s5", "SYSTEM", fin, Date.parse(app.reviewedAt ?? app.submittedAt) - base));
  return out;
}

/** Son de démonstration pour « Réécouter » : 4 s de « syllabes » (trois
 *  harmoniques sous une enveloppe hachée, intensités variées), en WAV ; la
 *  forme d'onde de l'onglet Voix a ainsi le relief d'une vraie voix. */
function sonDemo(): ArrayBuffer {
  const rate = 16_000;
  const samples = rate * 4;
  const buf = new ArrayBuffer(44 + samples * 2);
  const v = new DataView(buf);
  const ascii = (at: number, s: string) => [...s].forEach((c, i) => v.setUint8(at + i, c.charCodeAt(0)));
  ascii(0, "RIFF");
  v.setUint32(4, 36 + samples * 2, true);
  ascii(8, "WAVEfmt ");
  v.setUint32(16, 16, true);
  v.setUint16(20, 1, true);
  v.setUint16(22, 1, true);
  v.setUint32(24, rate, true);
  v.setUint32(28, rate * 2, true);
  v.setUint16(32, 2, true);
  v.setUint16(34, 16, true);
  ascii(36, "data");
  v.setUint32(40, samples * 2, true);
  // Syllabes : [début, durée] en secondes, intensité de 0,25 à 1 (graine fixe).
  let graine = 7;
  const hasard = () => {
    graine = (graine * 16807) % 2147483647;
    return graine / 2147483647;
  };
  const syllabes: Array<{ debut: number; fin: number; force: number }> = [];
  for (let t = 0.05; t < 3.9; ) {
    const longueur = 0.09 + hasard() * 0.22;
    syllabes.push({ debut: t, fin: Math.min(3.95, t + longueur), force: 0.25 + hasard() * 0.75 });
    t += longueur + (hasard() < 0.18 ? 0.25 + hasard() * 0.2 : 0.03 + hasard() * 0.07);
  }
  for (let i = 0; i < samples; i++) {
    const t = i / rate;
    const s = syllabes.find((x) => t >= x.debut && t < x.fin);
    let val = 0;
    if (s) {
      const env = Math.sin((Math.PI * (t - s.debut)) / (s.fin - s.debut)) * s.force;
      const f = 150 + 25 * Math.sin(2 * Math.PI * 3 * t);
      val =
        env *
        (Math.sin(2 * Math.PI * f * t) + 0.5 * Math.sin(4 * Math.PI * f * t) + 0.25 * Math.sin(6 * Math.PI * f * t));
    }
    v.setInt16(44 + i * 2, Math.round(val * 6000), true);
  }
  return buf;
}

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
  // ?demo=douane : « douane » (posé par install()).
  scope: "play",
  serverId: "build",
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

async function launchGame(scenario: Scenario, acces: () => AccesJeu): Promise<LaunchedGame> {
  if (scenario === "erreur-jouer") {
    await wait(600);
    throw ipcError("mc_session_expired", "Ta session Minecraft a expiré : reconnecte-toi avec Microsoft.");
  }
  // Barrière par la phase, avant l'installation (comme launcher_launch_game).
  const decision = acces();
  if (decision.refus) {
    await wait(500);
    throw ipcError(decision.refus.kind, decision.refus.message);
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

/** « Préparer le jeu » : étapes 1 à 5, sans barrière ni démarrage. */
async function prepareGame(): Promise<PreparedGame> {
  if (game.running || game.launching) {
    throw ipcError("already_running", "Le jeu est déjà lancé.");
  }
  game.launching = true;
  try {
    const step = (n: number, label: string, current: number | null = null, total: number | null = null) =>
      emitEvent("launch:progress", { step: n, totalSteps: 5, label, current, total });
    step(1, "Runtime Java");
    await wait(350);
    step(2, "Métadonnées Minecraft");
    await wait(350);
    step(3, "Bibliothèques & natives");
    await wait(400);
    const assets = 4723;
    for (const done of [0, 1180, 2361, 3542, assets]) {
      step(4, "Téléchargement des ressources", done, done === 0 ? null : assets);
      await wait(300);
    }
    step(5, "Fabric Loader");
    await wait(350);
    return { minecraftVersion: MC_VERSION, fabricVersion: "0.19.3" };
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
//  Candidature v2 et douane : faux backend à états
// ──────────────────────────────────────────────────────

/** Dernier corps brut reçu (invokeRaw : Uint8Array + en-têtes). mockIPC ne
 *  transmet pas les options d'invoke au gestionnaire : trackListeners les
 *  relève ici pour candidature_voix_deposer. */
let dernierCorpsBrut: { octets: Uint8Array; enTetes: Headers } | null = null;

/** Mêmes textes que decider_acces (src-tauri/src/content/candidature.rs). */
const REFUS_PHASE: Partial<Record<PhaseCandidature, string>> = {
  BROUILLON:
    "Ta candidature n'est pas encore envoyée : termine-la dans l'onglet Whitelist pour rejoindre le serveur.",
  ECRIT_EN_ATTENTE:
    "Ta candidature est en cours de relecture : suis son avancement dans l'onglet Whitelist. Tu passeras la douane une fois ton écrit accepté.",
  ECRIT_A_REVOIR:
    "Ta candidature est à revoir : corrige les parties demandées dans l'onglet Whitelist, puis renvoie-la.",
  DOUANE_A_RESERVER: "Ton écrit est accepté : réserve ta séance de douane dans l'onglet Whitelist.",
  DOUANE_A_REVOIR: "Ta douane est à revoir : réserve une nouvelle séance dans l'onglet Whitelist.",
  REFUSE:
    "Ta candidature a été refusée : le commentaire du staff et la date d'une nouvelle candidature sont dans l'onglet Whitelist.",
};

/** Mêmes textes que raisonPhase (apps/api/src/douane/douane.regles.ts). */
const RAISON_DOUANE: Partial<Record<PhaseCandidature, string>> = {
  BROUILLON: "Envoie d'abord ta candidature écrite.",
  ECRIT_EN_ATTENTE: "Ton écrit est en cours de relecture : la douane se réserve une fois l'écrit accepté.",
  ECRIT_A_REVOIR: "Ton écrit est à revoir : corrige-le et renvoie-le avant de réserver la douane.",
  REFUSE: "Ta candidature a été refusée : une nouvelle candidature complète sera possible après le délai.",
  ACCEPTE: "Tu es déjà accepté : pas de douane à passer.",
};

const PARIS_JOUR = new Intl.DateTimeFormat("fr-FR", {
  timeZone: "Europe/Paris",
  weekday: "long",
  day: "numeric",
  month: "long",
});
const PARIS_HEURE = new Intl.DateTimeFormat("fr-FR", {
  timeZone: "Europe/Paris",
  hour: "numeric",
  minute: "2-digit",
});

/** « 21 h » ou « 21 h 30 », heure de Paris (format des messages de l'API). */
function heureParis(iso: string): string {
  const [h, m] = PARIS_HEURE.format(new Date(iso)).split(":");
  return m && m !== "00" ? `${Number(h)} h ${m}` : `${Number(h)} h`;
}

/** « 12 min », « 2 h 05 », « 3 jours » (comme delai_lisible côté Rust). */
function delaiLisible(ms: number): string | null {
  if (ms <= 0) return null;
  const minutes = Math.ceil(ms / MIN);
  if (minutes < 60) return `${minutes} min`;
  if (minutes < 24 * 60) {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m === 0 ? `${h} h` : `${h} h ${pad(m)}`;
  }
  const jours = Math.floor(minutes / (24 * 60));
  return `${jours} jour${jours > 1 ? "s" : ""}`;
}

const nombreFr = (n: number) => n.toLocaleString("fr-FR");

/** Contrôle de l'envoi, version courte de whitelist.rules.ts › verifierChamp. */
function controlerEnvoi(c: Partial<CandidatureEnvoi>, voix: VoixVue | null): string[] {
  const b = OPTIONS_CANDIDATURE.bornes;
  const out: string[] = [];
  const texte = (champ: "availability" | "history" | "objectives", borne: { min: number; max: number }) => {
    const n = typeof c[champ] === "string" ? (c[champ] as string).length : 0;
    if (n < borne.min) {
      const manque = borne.min - n;
      out.push(`${champ}: Encore ${nombreFr(manque)} caractère${manque > 1 ? "s" : ""} (${nombreFr(borne.min)} minimum)`);
    } else if (n > borne.max) {
      out.push(`${champ}: ${nombreFr(n - borne.max)} caractères de trop (${nombreFr(borne.max)} maximum)`);
    }
  };
  const nom = (champ: "firstName" | "lastName") => {
    const v = (c[champ] ?? "").trim();
    if (!/^[\p{L}][\p{L} '-]{2,19}$/u.test(v)) {
      out.push(`${champ}: Lettres, espaces, apostrophes et tirets, de 3 à 20 caractères`);
    } else if (champ === "lastName" && OPTIONS_CANDIDATURE.clans.some((k) => k.nom.toLowerCase() === v.toLowerCase())) {
      out.push(
        `lastName: « ${v} » est un nom de clan. Les clans se rejoignent en jeu, lors des sessions de clan : choisis un autre nom`,
      );
    }
  };
  if (typeof c.playerAge !== "number") out.push("playerAge: Indique ton âge");
  else if (c.playerAge < b.playerAge.min) {
    out.push(`playerAge: Reborn accueille les joueurs à partir de ${b.playerAge.min} ans`);
  }
  texte("availability", b.availability);
  nom("firstName");
  nom("lastName");
  if (!OPTIONS_CANDIDATURE.villages.some((v) => v.valeur === c.village)) {
    out.push("village: Choisis un des six villages");
  }
  texte("history", b.history);
  texte("objectives", b.objectives);
  if (c.rulesAccepted !== true) out.push("rulesAccepted: Coche « J’ai lu le règlement et ces réponses sont de moi »");
  if (c.voiceConsent !== true) {
    out.push("voiceConsent: Coche « J’accepte que ma voix soit enregistrée pour ma candidature »");
  }
  if (!voix) out.push("voix: Ajoute ton enregistrement vocal (onglet Voix) avant d'envoyer ta candidature");
  return out;
}

type EtatCandidat = {
  phase: PhaseCandidature;
  application: CandidatureV2 | null;
  brouillon: BrouillonVue | null;
  seances: SeanceDouane[];
  reservation: ReservationDouane | null;
  partiesRouvertes: Array<"HRP" | "RP">;
  canReapplyAt: string | null;
  refusPar: "ECRIT" | "ORAL" | null;
  /** Dernière place perdue (séance manquée), comme derniereReservation. */
  derniere: DernierePlace | null;
  /** DOUANE_A_REVOIR : première date de séance permise (lendemain). */
  prochaineSeanceDes: string | null;
};

function candidatInitial(scenario: Scenario): EtatCandidat {
  const vide = {
    application: null,
    brouillon: null,
    seances: [] as SeanceDouane[],
    reservation: null,
    partiesRouvertes: [] as Array<"HRP" | "RP">,
    canReapplyAt: null,
    refusPar: null,
    derniere: null,
    prochaineSeanceDes: null,
  };
  switch (scenario) {
    case "candidature":
    case "candidature-complete":
      return {
        ...vide,
        phase: "BROUILLON",
        brouillon: {
          formVersion: 2,
          data: { ...(scenario === "candidature" ? BROUILLON_DEMO : BROUILLON_COMPLET) },
          updatedAt: ago(3 * MIN),
          voix: VOIX_DEMO,
        },
      };
    case "suivi":
      return { ...vide, phase: "ECRIT_EN_ATTENTE", application: { ...CANDIDATURE_ENVOYEE } };
    case "a-revoir":
      return { ...vide, phase: "ECRIT_A_REVOIR", application: { ...CANDIDATURE_A_REVOIR }, partiesRouvertes: ["RP"] };
    case "refuse":
      return {
        ...vide,
        phase: "REFUSE",
        application: { ...CANDIDATURE_REFUSEE },
        canReapplyAt: dans(22 * HOUR + 35 * MIN),
        refusPar: "ECRIT",
      };
    case "reserver":
      return {
        ...vide,
        phase: "DOUANE_A_RESERVER",
        application: { ...CANDIDATURE_DOUANE, phase: "DOUANE_A_RESERVER" },
        seances: seancesDemo(aHeure(1, 21)),
      };
    case "douane-demain": {
      const seances = seancesDemo(aHeure(1, 21));
      seances[0].placesRestantes -= 1;
      return {
        ...vide,
        phase: "DOUANE_RESERVEE",
        application: { ...CANDIDATURE_DOUANE },
        seances,
        reservation: reservationDemo(seances[0], seances[0].vagues[1]),
      };
    }
    case "douane": {
      const seances = seancesDemo();
      return {
        ...vide,
        phase: "DOUANE_RESERVEE",
        application: { ...CANDIDATURE_DOUANE },
        seances,
        reservation: reservationDemo(seances[0], seances[0].startAt),
      };
    }
    case "douane-manquee": {
      // Convocation il y a 50 min : entrée fermée il y a 20 min, sans arrivée.
      const convocation = NOW - 50 * MIN;
      return {
        ...vide,
        phase: "DOUANE_A_RESERVER",
        application: { ...CANDIDATURE_DOUANE, phase: "DOUANE_A_RESERVER" },
        seances: seancesDemo(aHeure(1, 21)),
        derniere: {
          statut: "MISSED",
          convocationAt: new Date(convocation).toISOString(),
          fermeAt: new Date(convocation + REGLES_DOUANE.retardMaxMin * MIN).toISOString(),
          motifAnnulation: null,
        },
      };
    }
    case "douane-a-revoir": {
      // Oral « à revoir » il y a 2 h : séances proposées à partir de demain.
      const demain = aHeure(1, 0);
      return {
        ...vide,
        phase: "DOUANE_A_REVOIR",
        application: {
          ...CANDIDATURE_DOUANE,
          phase: "DOUANE_A_REVOIR",
          oralStatus: "NEEDS_REVISION",
          oralDecidedAt: ago(2 * HOUR),
          oralVia: "DOUANE",
          oralReason:
            "Ton personnage est clair, mais tu as hésité sur les règles du village. Relis-les, puis reviens nous voir.",
        },
        seances: seancesDemo(aHeure(1, 21)),
        prochaineSeanceDes: new Date(demain).toISOString(),
      };
    }
    default:
      return { ...vide, phase: "ACCEPTE", application: CANDIDATURE_ACCEPTEE };
  }
}

function pushSystem(content: string): void {
  whitelistMessages = [
    ...whitelistMessages,
    {
      id: `wm-${Date.now()}`,
      applicationId: whitelistMessages[0]?.applicationId ?? "demo",
      authorType: "SYSTEM",
      authorId: null,
      authorName: null,
      content,
      attachments: [],
      createdAt: new Date().toISOString(),
    },
  ];
}

/** Faux backend de la candidature et des séances : phase, brouillon, voix,
 *  places. Après un envoi (?demo=candidature), l'écrit est « accepté » au bout
 *  de 10 s : la réservation et le refus « douane_not_open » se montrent. */
function candidatureBackend(scenario: Scenario) {
  const etat = candidatInitial(scenario);

  const application = (): CandidatureV2 | null =>
    etat.application ? { ...etat.application, phase: etat.phase } : null;

  const suivi = (): SuiviCandidature => {
    const r = etat.phase === "DOUANE_RESERVEE" ? etat.reservation : null;
    const f = r ? fenetreDemo(r.convocationAt) : null;
    return {
      phase: etat.phase,
      canReapplyAt: etat.phase === "REFUSE" ? etat.canReapplyAt : null,
      refusPar: etat.phase === "REFUSE" ? etat.refusPar : null,
      partiesRouvertes: etat.phase === "ECRIT_A_REVOIR" ? etat.partiesRouvertes : [],
      oralRequis: etat.phase !== "ACCEPTE",
      reservation:
        r && f
          ? {
              id: r.id,
              seanceId: r.seanceId,
              convocationAt: r.convocationAt,
              entreeOuvreAt: f.ouvreAt,
              entreeFermeAt: f.fermeAt,
            }
          : null,
      reservableDes: etat.phase === "DOUANE_A_REVOIR" ? etat.prochaineSeanceDes : null,
      douaneEnJeu: REGLES_DOUANE.douaneEnJeu,
    };
  };

  const douane = (): EtatDouane => {
    const peutReserver = etat.phase === "DOUANE_A_RESERVER" || etat.phase === "DOUANE_A_REVOIR";
    const peutChanger = etat.phase === "DOUANE_RESERVEE";
    const r = etat.reservation;
    return {
      phase: etat.phase,
      peutReserver,
      peutChanger,
      raison: peutReserver || peutChanger ? null : (RAISON_DOUANE[etat.phase] ?? null),
      prochaineSeanceDes: etat.phase === "DOUANE_A_REVOIR" ? etat.prochaineSeanceDes : null,
      reservation: peutChanger && r ? { ...r, fenetre: fenetreDemo(r.convocationAt) } : null,
      derniereReservation: peutReserver ? etat.derniere : null,
      seances: peutReserver || peutChanger ? etat.seances.map((s) => ({ ...s })) : [],
      regles: { ...REGLES_DOUANE },
    };
  };

  const acces = (): AccesJeu => {
    type Lieu = Pick<AccesJeu, "convocationAt" | "ouvreAt" | "fermeAt">;
    const sans: Lieu = { convocationAt: null, ouvreAt: null, fermeAt: null };
    const refus = (kind: NonNullable<AccesJeu["refus"]>["kind"], message: string, lieu: Lieu = sans): AccesJeu => ({
      autorise: false,
      portee: null,
      phase: etat.phase,
      refus: { kind, message },
      ...lieu,
    });
    if (etat.phase === "ACCEPTE") return { autorise: true, portee: "play", phase: "ACCEPTE", refus: null, ...sans };
    const d = etat.derniere;
    const depuis = d ? Date.now() - Date.parse(d.fermeAt) : -1;
    if (
      (etat.phase === "DOUANE_A_RESERVER" || etat.phase === "DOUANE_A_REVOIR") &&
      d?.statut === "MISSED" &&
      REGLES_DOUANE.douaneEnJeu &&
      depuis >= 0 &&
      depuis < 12 * HOUR
    ) {
      return refus(
        "douane_closed",
        "Tu n'es pas entré à la douane pendant la fenêtre de ta séance : réserve une autre séance dans l'onglet Whitelist.",
        { convocationAt: d.convocationAt, ouvreAt: null, fermeAt: d.fermeAt },
      );
    }
    if (etat.phase !== "DOUANE_RESERVEE" || !etat.reservation) {
      return refus(
        "candidature_required",
        REFUS_PHASE[etat.phase] ??
          "Ta candidature doit être acceptée avant de rejoindre le serveur : suis son avancement dans l'onglet Whitelist.",
      );
    }
    const f = fenetreDemo(etat.reservation.convocationAt);
    const lieu = { convocationAt: etat.reservation.convocationAt, ouvreAt: f.ouvreAt, fermeAt: f.fermeAt };
    if (f.etat === "OUVERTE") return { autorise: true, portee: "douane", phase: etat.phase, refus: null, ...lieu };
    if (f.etat === "FERMEE") {
      // Place encore réservée, entrée fermée : candidat arrivé (decider_acces).
      return refus(
        "candidature_required",
        "L'entrée de ta séance de douane est fermée et ton arrivée est enregistrée : la décision de l'examinateur arrivera dans l'onglet Whitelist. Déconnecté pendant l'examen ? Préviens le staff dans le fil.",
        lieu,
      );
    }
    const delai = delaiLisible(Date.parse(f.ouvreAt) - Date.now());
    return refus(
      "douane_not_open",
      `L'entrée de ta séance de douane ${delai ? `ouvre dans ${delai}` : "ouvre dans un instant"} (${REGLES_DOUANE.avanceEntreeMin} min avant ta convocation) : relance le jeu à ce moment-là.`,
      lieu,
    );
  };

  const rendrePlace = () => {
    const ancienne = etat.reservation && etat.seances.find((s) => s.id === etat.reservation?.seanceId);
    if (ancienne) {
      ancienne.placesRestantes += 1;
      ancienne.complet = false;
    }
  };

  const envoyer = async (a: Args): Promise<CandidatureV2> => {
    await wait(700);
    if (etat.phase !== "BROUILLON" && etat.phase !== "REFUSE") {
      throw ipcError(
        "conflict",
        "Tu as déjà une candidature en cours. Pour la modifier après une demande de révision, utilise PATCH /whitelist/me (HTTP 409).",
      );
    }
    const c = (a.candidature ?? {}) as Partial<CandidatureEnvoi>;
    const refus = controlerEnvoi(c, etat.brouillon?.voix ?? null);
    if (refus.length) throw ipcError("invalid_input", `${refus.join(" ; ")} (HTTP 400).`);
    const now = new Date().toISOString();
    etat.application = {
      id: crypto.randomUUID(),
      status: "PENDING",
      hrpStatus: "PENDING",
      rpStatus: "PENDING",
      formVersion: 2,
      phase: "ECRIT_EN_ATTENTE",
      playerAge: c.playerAge ?? null,
      dob: "",
      rpYears: c.rpYears ?? null,
      narutoKnowledge: c.narutoKnowledge ?? null,
      discoverySource: c.discoverySource ?? null,
      availability: c.availability ?? "",
      motivation: "",
      experience: "",
      firstName: (c.firstName ?? "").trim(),
      lastName: (c.lastName ?? "").trim(),
      village: c.village ?? "",
      characterType: c.characterType ?? null,
      characterSex: c.characterSex ?? null,
      characterAge: c.characterAge ?? null,
      support: c.support?.trim() || null,
      history: c.history ?? "",
      appearance: "",
      objectives: c.objectives ?? "",
      voix: etat.brouillon?.voix ?? null,
      voiceDeletedAt: null,
      rulesAcceptedAt: now,
      voiceConsentAt: now,
      submittedAt: now,
      reviewedAt: null,
      reviewNotes: null,
      reviewerName: null,
      writtenApprovedAt: null,
      oralStatus: "PENDING",
      oralDecidedAt: null,
      oralVia: null,
      oralReason: null,
      assignee: null,
      assignedAt: null,
    };
    etat.phase = "ECRIT_EN_ATTENTE";
    etat.brouillon = null;
    whitelistMessages = [];
    pushSystem("✅ Candidature envoyée. Elle est en cours de relecture par l'équipe whitelist.");
    // Le staff « accepte l'écrit » 10 s plus tard.
    window.setTimeout(() => {
      if (etat.phase !== "ECRIT_EN_ATTENTE" || !etat.application) return;
      const at = new Date().toISOString();
      etat.application = {
        ...etat.application,
        hrpStatus: "APPROVED",
        rpStatus: "APPROVED",
        reviewedAt: at,
        writtenApprovedAt: at,
        reviewerName: "Mei",
        reviewNotes: "Écrit validé. On se retrouve à la douane !",
      };
      etat.phase = "DOUANE_A_RESERVER";
      etat.seances = seancesDemo();
      pushSystem("✅ Écrit accepté. Réserve ta séance de douane dans le launcher.");
    }, 10_000);
    return { ...etat.application };
  };

  return {
    acces,
    options: () => OPTIONS_CANDIDATURE,
    me: (): CandidatureMe => ({ application: application(), suivi: suivi() }),
    application,
    envoyer,
    renvoyer: async (a: Args): Promise<CandidatureV2> => {
      await wait(600);
      if (etat.phase !== "ECRIT_A_REVOIR" || !etat.application) {
        throw ipcError(
          "conflict",
          "Ta candidature n’est pas à modifier pour l’instant : attends la réponse du staff (HTTP 409).",
        );
      }
      const { rulesAccepted, voiceConsent, ...champs } = (a.candidature ?? {}) as CandidatureRenvoi;
      if (rulesAccepted !== true || voiceConsent !== true) {
        throw ipcError(
          "invalid_input",
          "rulesAccepted: Coche « J’ai lu le règlement et ces réponses sont de moi » ; " +
            "voiceConsent: Coche « J’accepte que ma voix soit enregistrée pour ma candidature » (HTTP 400).",
        );
      }
      const at = new Date().toISOString();
      etat.application = {
        ...etat.application,
        ...champs,
        status: "PENDING",
        submittedAt: at,
        rulesAcceptedAt: at,
        voiceConsentAt: at,
      };
      etat.phase = "ECRIT_EN_ATTENTE";
      return { ...etat.application };
    },
    brouillonLire: () => ({ brouillon: etat.brouillon }),
    brouillonEcrire: async (a: Args): Promise<{ brouillon: BrouillonVue }> => {
      await wait(250);
      if (etat.phase === "ACCEPTE") {
        throw ipcError("conflict", "Ta candidature est acceptée : plus de brouillon à enregistrer (HTTP 409).");
      }
      const data = a.data && typeof a.data === "object" && !Array.isArray(a.data) ? (a.data as DonneesBrouillon) : {};
      // Contrôle souple de l'API (verifierBrouillon) : un texte au-delà de
      // deux fois son maximum est refusé (400).
      const bornes = OPTIONS_CANDIDATURE.bornes as Record<string, { min: number; max: number }>;
      for (const [cle, v] of Object.entries(data)) {
        if (typeof v !== "string") continue;
        const max = bornes[cle] ? bornes[cle].max * 2 : 24_000;
        if (v.length > max) {
          throw ipcError(
            "invalid_input",
            `${cle}: ${nombreFr(v.length - max)} caractères de trop pour un brouillon (${nombreFr(max)} au plus) (HTTP 400).`,
          );
        }
      }
      etat.brouillon = {
        formVersion: 2,
        data: { ...data },
        updatedAt: new Date().toISOString(),
        voix: etat.brouillon?.voix ?? null,
      };
      return { brouillon: etat.brouillon };
    },
    /** Démo : le brouillon modifié depuis un autre PC (window.__rebornDemo). */
    brouillonAilleurs: () => {
      const data = { ...(etat.brouillon?.data ?? {}) };
      data.history = `${typeof data.history === "string" ? data.history : ""}

Ajouté depuis un autre PC.`;
      etat.brouillon = {
        formVersion: 2,
        data,
        updatedAt: new Date().toISOString(),
        voix: etat.brouillon?.voix ?? null,
      };
    },
    brouillonEffacer: () => {
      etat.brouillon = null;
      return null;
    },
    voixDeposer: async (): Promise<{ voix: VoixDeposee }> => {
      await wait(600);
      const corps = dernierCorpsBrut;
      dernierCorpsBrut = null;
      if (etat.phase !== "BROUILLON" && etat.phase !== "ECRIT_A_REVOIR" && etat.phase !== "REFUSE") {
        throw ipcError(
          "conflict",
          etat.phase === "ACCEPTE"
            ? "Ta candidature est acceptée : plus d’enregistrement à envoyer (HTTP 409)."
            : "Ta candidature est en cours d’examen : l’enregistrement ne peut plus changer (HTTP 409).",
        );
      }
      const type = corps?.enTetes.get("x-reborn-voix-type") ?? "";
      const duree = Number(corps?.enTetes.get("x-reborn-voix-duree"));
      if (type !== "audio/webm" && type !== "audio/ogg") {
        throw ipcError("invalid_input", "Format d'enregistrement non accepté. Formats acceptés : audio/webm, audio/ogg.");
      }
      if (!Number.isInteger(duree) || duree < 15) throw ipcError("api", "15 secondes au moins (HTTP 400).");
      if (duree > 60) throw ipcError("api", "Une minute au plus (60 secondes) (HTTP 400).");
      const voix: VoixDeposee = {
        mimeType: type,
        durationSec: duree,
        uploadedAt: new Date().toISOString(),
        octets: corps?.octets.byteLength ?? 0,
      };
      const base: BrouillonVue = etat.brouillon ?? {
        formVersion: 2,
        data: {},
        updatedAt: voix.uploadedAt,
        voix: null,
      };
      etat.brouillon = { ...base, voix: { mimeType: voix.mimeType, durationSec: voix.durationSec, uploadedAt: voix.uploadedAt } };
      return { voix };
    },
    voixEcouter: async (): Promise<ArrayBuffer> => {
      await wait(200);
      return sonDemo();
    },
    douane,
    reserver: async (a: Args): Promise<ReservationDouane> => {
      await wait(450);
      const d = douane();
      const remplacer = a.remplacer === true;
      if (!d.peutReserver && !(d.peutChanger && remplacer)) {
        throw ipcError("conflict", `${d.raison ?? "Tu as déjà une place réservée."} (HTTP 409).`);
      }
      const seance = etat.seances.find((s) => s.id === str(a, "seanceId"));
      if (!seance) throw ipcError("not_found", "Séance introuvable : elle a peut-être été annulée (HTTP 404).");
      if (seance.id === etat.reservation?.seanceId) {
        throw ipcError("conflict", "Tu as déjà une place dans cette séance (HTTP 409).");
      }
      if (seance.complet) throw ipcError("conflict", "Cette séance est complète : choisis-en une autre (HTTP 409).");
      rendrePlace();
      const convocation = seance.convocationIndicative ?? seance.startAt;
      seance.placesRestantes -= 1;
      seance.complet = seance.placesRestantes === 0;
      etat.reservation = reservationDemo(seance, convocation);
      etat.phase = "DOUANE_RESERVEE";
      const f = etat.reservation.fenetre;
      pushSystem(
        `📅 Place réservée à la douane : ${PARIS_JOUR.format(new Date(convocation))}, convocation à ${heureParis(convocation)}. ` +
          `Entrée possible de ${heureParis(f.ouvreAt)} à ${heureParis(f.fermeAt)}.`,
      );
      return { ...etat.reservation };
    },
    annuler: async (a: Args): Promise<{ ok: true }> => {
      await wait(350);
      if (!etat.reservation || etat.reservation.id !== str(a, "reservationId")) {
        throw ipcError("not_found", "Réservation introuvable (HTTP 404).");
      }
      rendrePlace();
      etat.reservation = null;
      etat.phase = "DOUANE_A_RESERVER";
      pushSystem("Réservation de douane annulée. Tu peux choisir une autre séance dans le launcher.");
      return { ok: true };
    },
  };
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
  community_discord: DiscordCommunity | null;
  me_badges: Badges;
  me_mark_read: Badges;
  launcher_game_status: GameStatus;
  launcher_game_info: GameInfo;
  launcher_check_update: UpdatePreview;
  launcher_apply_update: UpdateStatus;
  launcher_launch_game: LaunchedGame;
  launcher_prepare_game: PreparedGame;
  launcher_launch_second_instance: LaunchedGame;
  launcher_stop_game: null;
  launcher_logs_snapshot: LogLine[];
  launcher_logs_export: string;
  candidature_options: CandidatureOptions;
  candidature_me: CandidatureMe;
  candidature_envoyer: CandidatureV2;
  candidature_renvoyer: CandidatureV2;
  candidature_brouillon_lire: { brouillon: BrouillonVue | null };
  candidature_brouillon_ecrire: { brouillon: BrouillonVue };
  candidature_brouillon_effacer: null;
  candidature_voix_deposer: { voix: VoixDeposee };
  candidature_voix_ecouter: ArrayBuffer;
  douane_etat: EtatDouane;
  douane_reserver: ReservationDouane;
  douane_annuler: { ok: true };
  douane_acces: AccesJeu;
  me_characters: MesPersonnages;
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

// ──────────────────────────────────────────────────────
//  Mes personnages (CANDIDATURE_ET_DOUANE §4.6, lib/personnages.ts)
//  Kaito Arashi : premier personnage, repris de la candidature (dossier
//  d'entrée, douane passée il y a 10 jours, créé le soir même). Ren Hozuki :
//  2ᵉ emplacement, clan Hozuki rejoint en session, mort RP.
// ──────────────────────────────────────────────────────

/** Il y a `jours` jours, à h:m (heure locale). */
function ilYA(jours: number, h: number, m = 0): string {
  const d = new Date(NOW);
  d.setDate(d.getDate() - jours);
  d.setHours(h, m, 0, 0);
  return d.toISOString();
}

const HISTOIRE_KAITO =
  "## Les registres des Arashi\n\n" +
  "Kaito est né à Konoha, chez les **Arashi**, une famille sans clan qui tient les registres du village depuis trois générations. " +
  "Son père, archiviste, lui a appris à lire avant de savoir lancer un kunai, et à noter tout ce qu'il voyait dans un carnet qu'il ne quitte jamais.\n\n" +
  "## L'Académie\n\n" +
  "À l'Académie, il n'était ni le plus fort ni le plus rapide, mais celui qui remarquait ce que les autres oubliaient :\n\n" +
  "- la cicatrice d'un instructeur qui changeait de main selon les jours ;\n" +
  "- les rouleaux rangés à l'envers dans la réserve ;\n" +
  "- les absences répétées d'un chūnin pendant la nuit.\n\n" +
  "> « Un registre ne ment pas. Ceux qui l'écrivent, parfois. »\n\n" +
  "Genin depuis peu, il a raté son premier examen chūnin pour avoir voulu tout comprendre avant d'agir.";

const OBJECTIFS_KAITO =
  "À court terme : réussir l'examen chūnin qu'il a raté une première fois, et gagner la confiance de son chef d'équipe. " +
  "À moyen terme : intégrer la cellule de renseignement du village, là où ses carnets serviraient enfin à quelque chose. " +
  "À long terme : comprendre pourquoi une page entière des registres de son grand-père a été arrachée l'année de l'attaque de Kyūbi, " +
  "et décider, une fois la vérité connue, s'il vaut mieux la garder pour lui.";

const KAITO: PersonnageCompte = {
  characterId: "7c1d9e42-3b6a-4f08-9d25-e8a41b0c6f73",
  playerId: 42,
  playerNumber: "00042",
  firstName: "Kaito",
  lastName: "Arashi",
  clan: null,
  village: "Konohagakure",
  type: "NINJA",
  rank: "GENIN",
  sex: "HOMME",
  age: 16,
  state: "ALIVE",
  playMinutes: 34 * 60 + 12,
  playMinutesSince: ilYA(10, 21, 52),
  createdAt: ilYA(10, 21, 52),
  lastSeenAt: ilYA(1, 23, 14),
  fromApplication: true,
  dossier: { history: HISTOIRE_KAITO, objectives: OBJECTIFS_KAITO, oralDecidedAt: ilYA(10, 21, 38) },
};

const REN: PersonnageCompte = {
  characterId: "e05b7a1c-92d4-4c3e-b8f1-4a6d2c9e7b10",
  playerId: 117,
  playerNumber: "00117",
  firstName: "Ren",
  lastName: null,
  clan: "Hozuki",
  village: "Kirigakure",
  type: "NINJA",
  rank: "GENIN_CONFIRME",
  sex: "HOMME",
  age: 19,
  state: "DEAD",
  playMinutes: 12 * 60 + 40,
  playMinutesSince: ilYA(7, 20, 5),
  createdAt: ilYA(7, 20, 5),
  lastSeenAt: ilYA(2, 22, 47),
  fromApplication: false,
  dossier: null,
};

function personnagesDemo(scenario: Scenario): MesPersonnages {
  if (CANDIDATS.has(scenario) || scenario === "sans-personnage") return { characters: [] };
  if (scenario === "premier-personnage") return { characters: [KAITO] };
  return { characters: [KAITO, REN] };
}

function createHandlers(scenario: Scenario): Handlers {
  const session = (): AuthSession => ({ user });
  const cand = candidatureBackend(scenario);
  const candidat = CANDIDATS.has(scenario);
  // Console : window.__rebornDemo.brouillonAilleurs() simule une écriture
  // depuis un autre PC (relecture au retour sur la fenêtre, « Deux versions »).
  Object.assign(window, { __rebornDemo: { brouillonAilleurs: cand.brouillonAilleurs } });
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
    // Carte « Discord officiel » de l'accueil ; « hors-ligne » montre l'état
    // « compteur indisponible » (le bouton garde son lien de repli).
    community_discord: async () => {
      await wait(350);
      if (scenario === "hors-ligne") return null;
      return {
        members: 1238,
        online: 214,
        inviteUrl: "https://discord.gg/Nw7jpjJ6dg",
        name: "Reborn Roleplay",
      };
    },
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
    launcher_launch_game: () => launchGame(scenario, cand.acces),
    launcher_prepare_game: prepareGame,
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

    // ── Whitelist (anciennes commandes, gardées par l'écran actuel) ──
    whitelist_me: () => ({ application: candidat ? cand.application() : WHITELIST }),
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

    // ── Candidature v2 et douane (lib/candidature.ts) ──
    candidature_options: cand.options,
    candidature_me: cand.me,
    candidature_envoyer: cand.envoyer,
    candidature_renvoyer: cand.renvoyer,
    candidature_brouillon_lire: cand.brouillonLire,
    candidature_brouillon_ecrire: cand.brouillonEcrire,
    candidature_brouillon_effacer: cand.brouillonEffacer,
    candidature_voix_deposer: cand.voixDeposer,
    candidature_voix_ecouter: cand.voixEcouter,
    douane_etat: cand.douane,
    douane_reserver: cand.reserver,
    douane_annuler: cand.annuler,
    douane_acces: cand.acces,

    // ── Mes personnages (lib/personnages.ts) ──
    // Candidat : aucun personnage ; « hors-ligne » : API injoignable.
    me_characters: async () => {
      await wait(350);
      if (scenario === "hors-ligne") {
        throw ipcError("network", "Impossible de joindre le serveur Reborn (délai dépassé) — vérifie ta connexion.");
      }
      return personnagesDemo(scenario);
    },

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
    if (args instanceof Uint8Array) {
      // invokeRaw (voix de la candidature) : octets et en-têtes relevés ici.
      const enTetes = (options as { headers?: HeadersInit } | undefined)?.headers;
      dernierCorpsBrut = { octets: args, enTetes: new Headers(enTetes ?? {}) };
    }
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
  // Scénarios candidat : compte PLAYER, fil de la candidature, jeton « douane ».
  if (CANDIDATS.has(scenario)) {
    user = { ...user, role: "PLAYER" };
    badges = { ...badges, coins: 0 };
    whitelistMessages =
      scenario === "douane" ||
      scenario === "douane-demain" ||
      scenario === "reserver" ||
      scenario === "douane-manquee" ||
      scenario === "douane-a-revoir"
        ? MESSAGES_DOUANE()
        : scenario === "suivi"
          ? messagesSuivi(CANDIDATURE_ENVOYEE, null)
          : scenario === "a-revoir"
            ? messagesSuivi(CANDIDATURE_A_REVOIR, "Candidature à revoir : partie RP. Les onglets concernés sont rouverts.")
            : scenario === "refuse"
              ? messagesSuivi(CANDIDATURE_REFUSEE, "Candidature refusée.")
              : [];
  }
  if (scenario === "douane" || scenario === "douane-demain") LAUNCHED.scope = "douane";

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

/** &fige=1 : animations coupées pour cette page (lu au chargement). */
function figerSiDemande(): void {
  const params = new URLSearchParams(window.location.search);
  if (!params.has("fige")) return;
  params.delete("fige");
  const search = params.toString();
  window.history.replaceState(
    window.history.state,
    "",
    `${window.location.pathname}${search ? `?${search}` : ""}${window.location.hash}`,
  );
  MotionGlobalConfig.skipAnimations = true;
  const style = document.createElement("style");
  style.dataset.demo = "fige";
  style.textContent = `*, *::before, *::after {
    animation-duration: 0s !important; animation-delay: 0s !important;
    transition-duration: 0s !important; transition-delay: 0s !important;
  }`;
  document.head.appendChild(style);
}

const scenario = bootScenario();
if (scenario && scenario !== "maquette") figerSiDemande();
// Les maquettes se rendent seules, sans backend : comme leurs pages d'aperçu.
// Leur splash (index.html) part tout de suite : une capture prise pendant son
// fondu de sortie sortait voilée.
if (scenario === "maquette") document.getElementById("reborn-preload")?.remove();
else if (scenario) install(scenario);

/** Faux si ?demo=off : main.tsx démarre alors l'application normalement. */
export const demoActive = scenario !== null;

/** ?demo=maquette : l'écran à rendre à la place de l'application ; null sinon. */
export async function maquette(): Promise<ReactNode | null> {
  if (scenario !== "maquette") return null;
  const { arbreMaquette } = await import("./maquettes");
  return arbreMaquette();
}
