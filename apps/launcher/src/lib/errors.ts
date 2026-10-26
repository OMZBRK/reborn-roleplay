// Formatage unique des erreurs remontees par l'IPC Tauri (et des erreurs JS).
//
// Contrat : toute commande Rust en echec rejette avec { kind, message }, ou
// `message` est deja une phrase francaise complete (cause reelle + quoi
// faire). Ce module la rend presentable (majuscule, ponctuation, longueur)
// et ne complete qu'au besoin :
//   - message vide ou inexploitable → phrase propre au `kind` ;
//   - message sans consigne → consigne propre au `kind` ajoutee ;
//   - ancien format « API a repondu 401 : {json} » → message NestJS extrait.
// Jamais « Erreur » seul. A utiliser dans tous les catch qui affichent :
//   catch (err) { setError(formatIpcError(err, "Impossible d'envoyer le message")); }

/** Forme d'une erreur IPC (contrat partage avec le Rust). `code` n'est
 *  renseigne que par certaines erreurs d'auth (Xbox / XSTS). */
export type IpcError = { kind: string; message: string; code?: string | null };

type KindText = { text: string; hint?: string };

const NET_HINT = "Vérifie ta connexion Internet puis réessaie.";
const DL_HINT = "Vérifie ta connexion et l'espace disque libre, puis relance.";
const STAFF_HINT = "Réessaie ; si ça persiste, exporte les logs et envoie-les au staff.";
const RELOGIN_HINT = "Reconnecte-toi pour continuer.";

// Kinds connus (enums AuthError, ContentError, GameError, LauncherError
// cote Rust, plus quelques kinds generiques d'API). `text` sert quand le
// message est vide, `hint` quand le message ne dit pas quoi faire.
const KINDS: Record<string, KindText> = {
  // ── Communs ──
  not_authenticated: { text: "Tu n'es pas connecté.", hint: RELOGIN_HINT },
  session_expired: { text: "Session expirée.", hint: RELOGIN_HINT },
  unauthorized: { text: "Session expirée.", hint: RELOGIN_HINT },
  forbidden: {
    text: "Le serveur Reborn a refusé l'accès.",
    hint: "Si tu penses que c'est une erreur, contacte le staff.",
  },
  not_found: {
    text: "Élément introuvable sur le serveur Reborn.",
    hint: "Il a peut-être été supprimé : actualise la page.",
  },
  payload_too_large: {
    text: "Fichier trop volumineux pour le serveur Reborn.",
    hint: "Choisis une image plus légère ou réduis-la avant de l'envoyer.",
  },
  rate_limited: {
    text: "Trop de requêtes envoyées au serveur Reborn.",
    hint: "Patiente quelques secondes puis réessaie.",
  },
  api: {
    text: "Le serveur Reborn a renvoyé une erreur.",
    hint: "Réessaie dans un instant ; si ça persiste, contacte le staff.",
  },
  network: { text: "Impossible de joindre le serveur Reborn.", hint: NET_HINT },
  http: { text: "Impossible de joindre le serveur.", hint: NET_HINT },
  timeout: { text: "Le serveur a mis trop de temps à répondre.", hint: NET_HINT },
  io: {
    text: "Lecture ou écriture d'un fichier impossible.",
    hint: "Vérifie que le dossier n'est ni en lecture seule, ni bloqué par l'antivirus ou OneDrive, puis réessaie.",
  },
  storage: {
    text: "Le gestionnaire d'identifiants Windows est inaccessible.",
    hint: "Redémarre le launcher ; si ça persiste, reconnecte-toi.",
  },
  internal: { text: "Erreur interne du launcher.", hint: STAFF_HINT },
  config: { text: "Le launcher est mal configuré.", hint: "Contacte le staff." },

  // ── Connexion Microsoft / Xbox ──
  microsoft_response: {
    text: "Réponse inattendue de Microsoft.",
    hint: "Réessaie la connexion dans un instant.",
  },
  xbox: {
    text: "Xbox Live a refusé l'authentification.",
    hint: "Vérifie que ton profil Xbox existe (xbox.com) puis réessaie.",
  },
  xsts: {
    text: "Xbox Live a refusé l'accès à ce compte.",
    hint: "Vérifie les restrictions du compte (âge, pays) sur account.xbox.com.",
  },
  no_minecraft_license: {
    text: "Aucune licence Minecraft Java n'est associée à ce compte Microsoft.",
    hint: "Connecte-toi avec le compte qui possède le jeu.",
  },
  user_canceled: { text: "Connexion annulée.", hint: "Réessaie quand tu veux." },
  no_stored_credentials: {
    text: "Aucun identifiant enregistré pour ce compte.",
    hint: "Reconnecte-toi avec Microsoft.",
  },
  stored_credentials_expired: {
    text: "Les identifiants enregistrés pour ce compte ont expiré.",
    hint: "Reconnecte-toi avec Microsoft.",
  },

  // ── Lancement du jeu ──
  not_whitelisted: {
    text: "Ta candidature whitelist n'est pas encore acceptée.",
    hint: "Suis son avancement dans l'onglet Whitelist.",
  },
  // Barriere d'acces par la phase de la candidature (content/candidature.rs).
  candidature_required: {
    text: "Ta candidature doit être acceptée avant de rejoindre le serveur.",
    hint: "Suis son avancement dans l'onglet Whitelist.",
  },
  douane_hors_jeu: {
    text: "Ton oral se passe hors du jeu pour le moment.",
    hint: "Suis les consignes du staff dans l'onglet Whitelist.",
  },
  douane_not_open: {
    text: "L'entrée de ta séance de douane n'est pas encore ouverte.",
    hint: "Relance le jeu 15 min avant ta convocation.",
  },
  douane_closed: {
    text: "L'heure d'entrée de ta séance de douane est passée.",
    hint: "Réserve une autre séance dans l'onglet Whitelist.",
  },
  access_check: { text: "Vérification de ta candidature impossible.", hint: NET_HINT },
  not_staff: { text: "Action réservée au staff (grade Helper ou supérieur)." },
  already_running: { text: "Le jeu est déjà lancé.", hint: "Ferme Minecraft avant de le relancer." },
  not_running: { text: "Aucune partie n'est en cours." },
  runtime: { text: "Installation du Java de Minecraft impossible.", hint: DL_HINT },
  mojang: { text: "Récupération des informations de version Minecraft impossible.", hint: NET_HINT },
  libraries: { text: "Téléchargement des bibliothèques Minecraft impossible.", hint: DL_HINT },
  assets: { text: "Téléchargement des ressources Minecraft impossible.", hint: DL_HINT },
  fabric: { text: "Installation de Fabric Loader impossible.", hint: DL_HINT },
  watcher: {
    text: "La surveillance d'intégrité des fichiers n'a pas pu démarrer.",
    hint: "Relance le launcher puis le jeu.",
  },
  play_token: {
    text: "Impossible d'obtenir l'autorisation de jeu auprès du serveur Reborn.",
    hint: "Vérifie ta connexion ; si ça persiste, reconnecte-toi.",
  },
  same_account: {
    text: "Ce compte est déjà utilisé par la partie principale.",
    hint: "Choisis un autre compte.",
  },
  game_running: { text: "Le jeu est ouvert.", hint: "Ferme le jeu puis réessaie." },
  mc_session_expired: {
    text: "Ta session Minecraft a expiré.",
    hint: "Reconnecte-toi avec Microsoft.",
  },
  mc_session_unreachable: {
    text: "Impossible de rafraîchir ta session Minecraft auprès de Microsoft.",
    hint: NET_HINT,
  },
  spawn: {
    text: "Minecraft n'a pas pu démarrer.",
    hint: "Relance le launcher ; si ça persiste, lance « Réparer les fichiers du jeu » dans Paramètres › Jeu.",
  },
  kill: {
    text: "Impossible d'arrêter le jeu.",
    hint: "Ferme la fenêtre du jeu, ou termine « javaw.exe » dans le Gestionnaire des tâches.",
  },
  logs_empty: { text: "Aucun journal à exporter : lance le jeu au moins une fois." },
  alt_account: {
    text: "Préparation du compte secondaire impossible.",
    hint: "Reconnecte ce compte depuis l'écran de connexion.",
  },

  // ── Mise à jour des fichiers ──
  manifest: { text: "La liste des fichiers du jeu est indisponible.", hint: NET_HINT },
  signature: {
    text: "La signature de la liste des fichiers du jeu est invalide.",
    hint: "Ne lance pas le jeu et préviens le staff.",
  },
  download: { text: "Téléchargement des fichiers du jeu impossible.", hint: DL_HINT },
  manifest_expired: {
    text: "La liste des fichiers du jeu a expiré.",
    hint: "Relance le launcher plus tard ou préviens le staff.",
  },
  manifest_rollback: {
    text: "La liste des fichiers du jeu reçue est plus ancienne que la dernière acceptée.",
    hint: "Ne lance pas le jeu et préviens le staff.",
  },

  // ── Services Minecraft / navigateur (connexion) ──
  minecraft_services: {
    text: "Les services Minecraft ont refusé la connexion.",
    hint: "Réessaie dans un instant.",
  },
  browser: {
    text: "Impossible d'ouvrir le navigateur.",
    hint: "Vérifie qu'un navigateur par défaut est configuré dans Windows.",
  },

  // ── Réponses de l'API (contenu) ──
  conflict: {
    text: "Action impossible dans l'état actuel.",
    hint: "Actualise la page puis réessaie.",
  },
  server_error: { text: "Le serveur Reborn a un problème.", hint: "Réessaie plus tard." },
  unexpected_response: {
    text: "Réponse inattendue du serveur Reborn.",
    hint: "Mets le launcher à jour ; si ça persiste, contacte le staff.",
  },
  invalid_input: { text: "Valeur invalide.", hint: "Corrige la saisie puis réessaie." },
};

const GENERIC = `Une erreur inattendue est survenue, sans détail renvoyé. ${STAFF_HINT}`;
const MAX_LEN = 500;

/** Retire les accents et met en minuscules (comparaisons tolerantes). */
function fold(s: string): string {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

// Le message dit deja quoi faire : on n'ajoute pas de consigne par-dessus.
// Teste sur le texte en minuscules AVEC accents : sur un texte replie,
// « ce ticket est fermé » passait pour la consigne « ferme ».
const ACTION_RE =
  /(?:^|[^\p{L}])(v[eé]rifie|r[eé]essaie|reconnecte|connecte-toi|relance|red[eé]marre|contacte|pr[eé]viens|signale|ouvre|ferme|lib[eè]re|patiente|attends|installe|d[eé]sinstalle|choisis|suis son|suis les|mets [aà] jour|d[eé]sactive|autorise|envoie|exporte|actualise|corrige|termine|lance|utilise|clique|supprime|ajoute|r[eé]serve|refais|ne lance pas)(?![\p{L}])/u;

const USELESS = new Set(["", "erreur", "error", "echec", "undefined", "null", "[object object]", "unknown error", "erreur inconnue"]);

function isUseless(text: string, kind: string | null): boolean {
  const f = fold(text).replace(/[\s.!…]+$/, "").trim();
  return USELESS.has(f) || (kind !== null && f === kind);
}

/** Les chaines JSON d'erreur (rare : erreur re-serialisee) sont relues. */
function parseMaybeJson(err: unknown): unknown {
  if (typeof err !== "string") return err;
  const t = err.trim();
  if (!t.startsWith("{")) return err;
  try {
    return JSON.parse(t) as unknown;
  } catch {
    return err;
  }
}

export function isIpcError(err: unknown): err is IpcError {
  return (
    typeof err === "object" &&
    err !== null &&
    typeof (err as { kind?: unknown }).kind === "string" &&
    "message" in err
  );
}

/** `kind` de l'erreur IPC, ou null si ce n'en est pas une. */
export function ipcErrorKind(err: unknown): string | null {
  const e = parseMaybeJson(err);
  return isIpcError(e) ? e.kind : null;
}

/** True si l'erreur est une erreur IPC d'un des kinds donnes. */
export function isIpcErrorKind(err: unknown, ...kinds: string[]): boolean {
  const k = ipcErrorKind(err);
  return k !== null && kinds.includes(k);
}

function truncate(s: string, max: number): string {
  return s.length > max ? `${s.slice(0, max - 1).trimEnd()}…` : s;
}

/** Extrait le champ `message` (string ou tableau) d'un corps NestJS. */
function nestBodyMessage(body: string): string | null {
  const t = body.trim();
  if (!t) return null;
  try {
    const j = JSON.parse(t) as { message?: unknown; error?: unknown };
    if (typeof j.message === "string" && j.message.trim()) return j.message.trim();
    if (Array.isArray(j.message)) {
      const parts = j.message.filter((m): m is string => typeof m === "string" && !!m.trim());
      if (parts.length) return parts.join(" ; ");
    }
    if (typeof j.error === "string" && j.error.trim()) return j.error.trim();
  } catch {
    // Pas du JSON : texte brut ou page HTML d'un proxy.
  }
  if (t.startsWith("<")) return "réponse HTML inattendue (serveur indisponible ou en maintenance ?)";
  return truncate(t, 200);
}

// Ancien format d'ApiError cote Rust (« API a repondu 401 : {corps} »),
// conserve le temps que tous les backends emettent le nouveau message.
const LEGACY_STATUS_RE = /API a r[eé]pondu (\d{3})\s*:\s*([\s\S]*)$/i;

function rewriteLegacy(text: string): string {
  if (/^command \S+ not found$/i.test(text.trim())) {
    return "Cette fonction n'existe pas dans cette version du launcher. Mets le launcher à jour.";
  }
  const m = LEGACY_STATUS_RE.exec(text);
  if (!m) return text;
  const status = Number(m[1]);
  if (status === 401) return "Session expirée : reconnecte-toi.";
  const detail = nestBodyMessage(m[2]);
  const prefix = text.slice(0, m.index).replace(/[\s:]+$/, "");
  const core = `le serveur Reborn a répondu ${status}${detail ? ` : ${detail}` : ""}`;
  return prefix ? `${prefix} : ${core}` : core;
}

/** Chemin, URL ou nom de fichier : ni majuscule forcee, ni point colle. */
const isPathLike = (token: string) => /[\\/]|:\/\/|\.[a-z0-9]{1,5}$/i.test(token);

/** Majuscule initiale, espaces normalises, longueur bornee, point final
 *  (sauf si la phrase commence/finit par un chemin, une URL ou un fichier). */
function asSentence(raw: string): string {
  let s = raw.replace(/\s+/g, " ").trim();
  const stripped = s.replace(/^(uncaught\s+)?(error|erreur)\s*:\s*/i, "");
  if (stripped) s = stripped;
  s = truncate(s, MAX_LEN);
  if (!isPathLike(s.split(" ", 1)[0])) s = s.charAt(0).toLocaleUpperCase("fr-FR") + s.slice(1);
  if (/[\p{L}\p{N})\]»"']$/u.test(s) && !isPathLike(s.split(" ").pop() ?? "")) s += ".";
  return s;
}

/** Ajoute la consigne ; « — » si la phrase finit par un chemin (pas de point). */
function withHint(text: string, hint: string): string {
  if (/[.!?…]$/.test(text)) return `${text} ${hint}`;
  return `${text} — ${hint.charAt(0).toLocaleLowerCase("fr-FR")}${hint.slice(1)}`;
}

/** Message brut + kind extraits de n'importe quelle valeur rejetee. */
function extract(err: unknown): { kind: string | null; message: string } {
  const e = parseMaybeJson(err);
  if (e === null || e === undefined) return { kind: null, message: "" };
  if (typeof e === "string") return { kind: null, message: e };
  if (typeof e === "number" || typeof e === "boolean") return { kind: null, message: String(e) };
  if (e instanceof Error) return { kind: null, message: e.message };
  if (isIpcError(e)) {
    const m = e.message;
    return { kind: e.kind, message: typeof m === "string" ? m : m == null ? "" : JSON.stringify(m) };
  }
  if (typeof e === "object") {
    const o = e as Record<string, unknown>;
    for (const key of ["message", "error", "detail", "reason", "description"]) {
      const v = o[key];
      if (typeof v === "string" && v.trim()) return { kind: null, message: v };
    }
    try {
      const json = truncate(JSON.stringify(e), 300);
      return { kind: "internal", message: `Réponse d'erreur inattendue : ${json}` };
    } catch {
      return { kind: "internal", message: "" };
    }
  }
  return { kind: null, message: String(e) };
}

/**
 * Transforme n'importe quelle erreur (rejet d'invoke, Error JS, chaine,
 * objet inconnu) en phrase francaise lisible : la cause reelle renvoyee par
 * le backend, completee au besoin d'une consigne propre au `kind`.
 *
 * @param context action qui a echoue, ajoutee en tete (« Impossible
 *   d'envoyer le message ») — facultatif.
 */
export function formatIpcError(err: unknown, context?: string): string {
  const { kind, message } = extract(err);
  const known = kind ? KINDS[kind] : undefined;

  let text: string;
  if (isUseless(message, kind)) {
    if (known) {
      text = known.hint ? `${known.text} ${known.hint}` : known.text;
    } else if (kind) {
      text = `Une erreur inattendue est survenue (${kind}). ${STAFF_HINT}`;
    } else {
      text = GENERIC;
    }
  } else {
    text = asSentence(rewriteLegacy(message));
    if (known?.hint && !ACTION_RE.test(text.toLocaleLowerCase("fr-FR"))) text = withHint(text, known.hint);
  }

  const ctx = context?.trim().replace(/[\s.:;,]+$/, "");
  return ctx ? `${ctx}. ${text}` : text;
}
