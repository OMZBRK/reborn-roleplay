/**
 * ============================================================================
 *  AUDIT DISCORD — STRICTEMENT EN LECTURE SEULE
 * ============================================================================
 *
 *  Ce script NE MODIFIE RIEN sur Discord. Aucun create / edit / delete /
 *  setPosition / permissionOverwrites.edit / ban / kick / envoi de message.
 *
 *  Garde-fous :
 *   1. Le code n'appelle que des lectures (cache gateway + fetch GET).
 *   2. `installReadOnlyGuard` intercepte TOUTES les requêtes REST du client
 *      (`client.rest.queueRequest`) et rejette toute méthode autre que GET.
 *      Si une future modification de ce fichier introduisait une écriture par
 *      erreur, elle échouerait avant de partir vers Discord.
 *   3. Intent `Guilds` seul par défaut : aucun message lu. Les intents
 *      GuildMessages + MessageContent ne sont demandés QUE si `--lire` ou
 *      `--tout` est passé (lecture de l'historique des salons choisis, en GET).
 *      Jamais l'intent DirectMessages : aucun message privé n'est lu.
 *   4. Les fichiers produits (qui contiennent des IDs et des noms) sont écrits
 *      HORS du dépôt (refus explicite si le dossier cible est dans le dépôt —
 *      règle 7 de docs/REPRISE_DE_SESSION.md) et jamais écrasés.
 *
 *  Usage (depuis la racine du monorepo) :
 *    pnpm --filter @reborn/bot audit:guild                    → liste les serveurs du bot
 *    pnpm --filter @reborn/bot audit:guild --guild <id>       → audit complet d'un serveur
 *    pnpm --filter @reborn/bot audit:guild --guild <id> --out <dossier>
 *    pnpm --filter @reborn/bot audit:guild --guild <id> --lire "hiérarchie,règlement-staff" [--limite 100]
 *    pnpm --filter @reborn/bot audit:guild --guild <id> --lire "Staff Général"   → tous les salons de la catégorie
 *    pnpm --filter @reborn/bot audit:guild --guild <id> --tout [--limite 100]    → audit + tous les salons texte visibles
 *
 *  Sortie par défaut : %USERPROFILE%\Desktop\Reborn - Gestion\Discord\audit-<date>\
 *    - audit.json  : export brut (sert aussi de SAUVEGARDE pour un retour arrière)
 *    - RAPPORT.md  : rapport lisible
 *    - messages\   : (avec --lire / --tout) <salon>.md + <salon>.json + INDEX.md
 *  Dossier parent configurable par `--out` ou la variable REBORN_DISCORD_AUDIT_DIR.
 *
 *  Doc : docs/operations/DISCORD_STAFF.md
 * ============================================================================
 */
import {
  ChannelType,
  Client,
  Events,
  GatewayIntentBits,
  GuildExplicitContentFilter,
  GuildMFALevel,
  GuildVerificationLevel,
  OverwriteType,
  PermissionFlagsBits,
  PermissionsBitField,
  type Guild,
  type GuildBasedChannel,
  type AnyThreadChannel,
  type GuildMember,
  type GuildTextBasedChannel,
  type Message,
  type NonThreadGuildBasedChannel,
  type PermissionResolvable,
  type Role,
} from "discord.js";
import { config as loadDotenv } from "dotenv";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join, relative, resolve, isAbsolute } from "node:path";
import { fileURLToPath } from "node:url";

// ---------------------------------------------------------------------------
// Environnement : on ne lit QUE DISCORD_BOT_TOKEN (pas config.ts, qui exige
// des variables sans rapport avec l'audit).
// ---------------------------------------------------------------------------

const here = dirname(fileURLToPath(import.meta.url));

function findUp(start: string, name: string, maxDepth = 8): string | null {
  let dir = start;
  for (let i = 0; i < maxDepth; i++) {
    const candidate = join(dir, name);
    if (existsSync(candidate)) return candidate;
    const parent = dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
  return null;
}

const envPath = findUp(here, ".env");
if (envPath) loadDotenv({ path: envPath });

const workspaceFile = findUp(here, "pnpm-workspace.yaml");
const repoRoot = workspaceFile ? dirname(workspaceFile) : null;

// ---------------------------------------------------------------------------
// Arguments
// ---------------------------------------------------------------------------

/** Valeur spéciale de --lire : tous les salons texte (texte, annonces, forums) que le bot peut lire. */
const ALL_TEXT = "tout-texte";
const DEFAULT_LIMIT = 100;
const MAX_LIMIT = 5000;

interface Args {
  guildId: string | null;
  outParent: string;
  help: boolean;
  /** Argument invalide : l'aide est affichée et le script sort en erreur. */
  error: boolean;
  /** null = pas de lecture de messages. Sinon : noms / IDs de salons ou de catégories, ou [ALL_TEXT]. */
  read: string[] | null;
  limit: number;
}

function parseArgs(argv: string[]): Args {
  const args: Args = {
    guildId: null,
    outParent:
      process.env.REBORN_DISCORD_AUDIT_DIR ??
      join(homedir(), "Desktop", "Reborn - Gestion", "Discord"),
    help: false,
    error: false,
    read: null,
    limit: DEFAULT_LIMIT,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--") continue;
    if (a === "--help" || a === "-h") args.help = true;
    else if (a === "--guild") args.guildId = argv[++i] ?? null;
    else if (a === "--out") args.outParent = argv[++i] ?? args.outParent;
    else if (a === "--tout") args.read = [ALL_TEXT];
    else if (a === "--lire") {
      const list = (argv[++i] ?? "")
        .split(",")
        .map((s) => s.trim())
        .filter((s) => s.length > 0);
      if (list.length === 0) {
        console.error("--lire attend une liste de salons séparés par des virgules (ou \"tout-texte\").");
        args.help = args.error = true;
      } else if (args.read?.[0] !== ALL_TEXT) {
        args.read = list.some((s) => s.toLowerCase() === ALL_TEXT) ? [ALL_TEXT] : list;
      }
    } else if (a === "--limite") {
      const n = Number(argv[++i]);
      if (!Number.isInteger(n) || n < 1 || n > MAX_LIMIT) {
        console.error(`--limite attend un entier entre 1 et ${MAX_LIMIT}.`);
        args.help = args.error = true;
      } else args.limit = n;
    } else {
      console.error(`Argument inconnu : ${a}`);
      args.help = args.error = true;
    }
  }
  return args;
}

const args = parseArgs(process.argv.slice(2));

if (args.help) {
  console.log(
    [
      "Audit Discord en lecture seule.",
      "  (sans argument)        liste les serveurs où le bot est présent",
      "  --guild <id>           audite ce serveur (catégories, salons, rôles, permissions)",
      "  --lire <a,b,…>         en plus de l'audit, exporte les messages de ces salons (noms, IDs ou",
      "                         noms de catégorie ; « tout-texte » = tous les salons texte lisibles)",
      "  --tout                 = --lire tout-texte",
      `  --limite <N>           messages max par salon (et par fil de forum), défaut ${DEFAULT_LIMIT}, max ${MAX_LIMIT}`,
      "  --out <dossier>        dossier parent de sortie (défaut : Desktop\\Reborn - Gestion\\Discord)",
    ].join("\n"),
  );
  process.exit(args.error ? 2 : 0);
}

if (args.guildId !== null && !/^\d{17,20}$/.test(args.guildId)) {
  console.error("--guild attend un ID numérique de serveur (mode développeur → clic droit → Copier l'identifiant).");
  process.exit(2);
}

if (args.read && !args.guildId) {
  console.error("--lire / --tout exigent --guild <id>.");
  process.exit(2);
}

const token = process.env.DISCORD_BOT_TOKEN;
if (!token) {
  console.error("DISCORD_BOT_TOKEN absent (cherché dans le .env racine et l'environnement).");
  process.exit(2);
}
/** Serveur communautaire déjà configuré pour le bot, signalé dans la liste (jamais modifié ici non plus). */
const communityGuildId = process.env.DISCORD_GUILD_ID ?? "";

// ---------------------------------------------------------------------------
// Libellés de permissions
// ---------------------------------------------------------------------------

const PERM_FR: Record<string, string> = {
  CreateInstantInvite: "Créer une invitation",
  KickMembers: "Expulser des membres",
  BanMembers: "Bannir des membres",
  Administrator: "ADMINISTRATEUR",
  ManageChannels: "Gérer les salons",
  ManageGuild: "Gérer le serveur",
  AddReactions: "Ajouter des réactions",
  ViewAuditLog: "Voir les logs du serveur",
  PrioritySpeaker: "Voix prioritaire",
  Stream: "Vidéo / partage d'écran",
  ViewChannel: "Voir les salons",
  SendMessages: "Envoyer des messages",
  SendTTSMessages: "Messages TTS",
  ManageMessages: "Gérer les messages",
  EmbedLinks: "Intégrer des liens",
  AttachFiles: "Joindre des fichiers",
  ReadMessageHistory: "Voir l'historique",
  MentionEveryone: "Mentionner @everyone / @here / tous les rôles",
  UseExternalEmojis: "Émojis externes",
  ViewGuildInsights: "Statistiques du serveur",
  Connect: "Se connecter (vocal)",
  Speak: "Parler",
  MuteMembers: "Rendre muet",
  DeafenMembers: "Mettre en sourdine",
  MoveMembers: "Déplacer des membres",
  UseVAD: "Détection de la voix",
  ChangeNickname: "Changer de pseudo",
  ManageNicknames: "Gérer les pseudos",
  ManageRoles: "Gérer les rôles",
  ManageWebhooks: "Gérer les webhooks",
  ManageEmojisAndStickers: "Gérer les expressions",
  ManageGuildExpressions: "Gérer les expressions",
  UseApplicationCommands: "Commandes d'application",
  RequestToSpeak: "Demander la parole",
  ManageEvents: "Gérer les événements",
  ManageThreads: "Gérer les fils",
  CreatePublicThreads: "Créer des fils publics",
  CreatePrivateThreads: "Créer des fils privés",
  UseExternalStickers: "Autocollants externes",
  SendMessagesInThreads: "Envoyer dans les fils",
  UseEmbeddedActivities: "Activités",
  ModerateMembers: "Exclure temporairement (timeout)",
  ViewCreatorMonetizationAnalytics: "Statistiques de monétisation",
  UseSoundboard: "Soundboard",
  CreateGuildExpressions: "Créer des expressions",
  CreateEvents: "Créer des événements",
  UseExternalSounds: "Sons externes",
  SendVoiceMessages: "Messages vocaux",
  SendPolls: "Sondages",
  UseExternalApps: "Applications externes",
  PinMessages: "Épingler des messages",
  BypassSlowmode: "Ignorer le mode lent",
};

/** Permissions qui donnent du pouvoir sur le serveur ou les autres membres. */
const SENSITIVE = new Set<string>([
  "Administrator",
  "ManageGuild",
  "ManageRoles",
  "ManageChannels",
  "ManageWebhooks",
  "BanMembers",
  "KickMembers",
  "ModerateMembers",
  "MentionEveryone",
  "ManageMessages",
  "ViewAuditLog",
  "ManageNicknames",
  "ManageThreads",
  "ManageEvents",
  "ManageGuildExpressions",
]);

function permNames(bits: PermissionsBitField | bigint): string[] {
  const field = typeof bits === "bigint" ? new PermissionsBitField(bits) : bits;
  // ManageEmojisAndStickers et ManageGuildExpressions sont le même bit : on garde le nom récent.
  return field.toArray().filter((n) => n !== "ManageEmojisAndStickers");
}

function permLabels(names: string[]): string {
  if (names.length === 0) return "—";
  return names
    .map((n) => {
      const label = PERM_FR[n] ?? n;
      return SENSITIVE.has(n) ? `**${label}**` : label;
    })
    .join(", ");
}

// ---------------------------------------------------------------------------
// Garde-fou REST : tout ce qui n'est pas GET est refusé avant l'envoi.
// ---------------------------------------------------------------------------

function installReadOnlyGuard(client: Client): void {
  const rest = client.rest;
  const original = rest.queueRequest.bind(rest);
  rest.queueRequest = (request) => {
    const method = String(request.method).toUpperCase();
    if (method !== "GET") {
      const msg = `[audit] REFUS (lecture seule) : ${method} ${String(request.fullRoute)}`;
      console.error(msg);
      return Promise.reject(new Error(msg));
    }
    return original(request);
  };
}

// ---------------------------------------------------------------------------
// Utilitaires d'export
// ---------------------------------------------------------------------------

function md(text: string | null | undefined): string {
  if (!text) return "";
  return text.replace(/\|/g, "\\|").replace(/\r?\n/g, " ");
}

function hexColor(role: Role): string {
  return role.color === 0 ? "aucune" : `#${role.color.toString(16).padStart(6, "0")}`;
}

function channelTypeName(type: ChannelType): string {
  const fr: Partial<Record<ChannelType, string>> = {
    [ChannelType.GuildCategory]: "catégorie",
    [ChannelType.GuildText]: "texte",
    [ChannelType.GuildAnnouncement]: "annonces",
    [ChannelType.GuildForum]: "forum",
    [ChannelType.GuildMedia]: "média",
    [ChannelType.GuildVoice]: "vocal",
    [ChannelType.GuildStageVoice]: "conférence",
    [ChannelType.PublicThread]: "fil public",
    [ChannelType.PrivateThread]: "fil privé",
    [ChannelType.AnnouncementThread]: "fil d'annonce",
  };
  return fr[type] ?? ChannelType[type] ?? String(type);
}

function timestamp(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}

function isInside(child: string, parent: string): boolean {
  const rel = relative(parent, child);
  return rel === "" || (!rel.startsWith("..") && !isAbsolute(rel));
}

function assertOutsideRepo(parent: string): void {
  const absParent = resolve(parent);
  if (repoRoot && isInside(absParent, repoRoot)) {
    console.error(
      `Refus : le dossier de sortie (${absParent}) est dans le dépôt. Les exports contiennent des IDs : ` +
        "choisis un dossier hors dépôt (--out).",
    );
    process.exit(2);
  }
}

function prepareOutDir(parent: string): string {
  assertOutsideRepo(parent);
  const absParent = resolve(parent);
  let dir = join(absParent, `audit-${timestamp()}`);
  let n = 2;
  while (existsSync(dir)) dir = join(absParent, `audit-${timestamp()}-${n++}`);
  mkdirSync(dir, { recursive: true });
  return dir;
}

// ---------------------------------------------------------------------------
// Collecte
// ---------------------------------------------------------------------------

interface OverwriteExport {
  targetType: "role" | "member";
  targetId: string;
  targetName: string;
  allow: string[];
  deny: string[];
  allowBits: string;
  denyBits: string;
}

interface ChannelExport {
  id: string;
  name: string;
  type: string;
  typeCode: number;
  position: number;
  rawPosition: number;
  parentId: string | null;
  parentName: string | null;
  topic: string | null;
  slowmodeSeconds: number | null;
  nsfw: boolean | null;
  bitrate: number | null;
  userLimit: number | null;
  /** true = permissions synchronisées avec la catégorie parente. */
  syncedWithParent: boolean | null;
  viewableByBot: boolean;
  visibleToEveryone: boolean;
  /** Rôles (hors Administrateur) qui voient ce salon, calcul rôle par rôle. */
  rolesThatCanView: string[];
  rolesThatCanSend: string[];
  overwrites: OverwriteExport[];
}

interface RoleExport {
  id: string;
  name: string;
  color: string;
  position: number;
  hoist: boolean;
  mentionable: boolean;
  managed: boolean;
  isEveryone: boolean;
  botTag: string | null;
  permissions: string[];
  permissionsBits: string;
}

function str(v: unknown): string | null {
  return typeof v === "string" ? v : null;
}
function num(v: unknown): number | null {
  return typeof v === "number" ? v : null;
}
function bool(v: unknown): boolean | null {
  return typeof v === "boolean" ? v : null;
}

async function resolveMemberName(guild: Guild, id: string, cache: Map<string, string>): Promise<string> {
  const known = cache.get(id);
  if (known) return known;
  let name = `membre ${id}`;
  try {
    // GET /guilds/:id/members/:user — ne demande pas l'intent privilégié GuildMembers.
    const m: GuildMember = await guild.members.fetch({ user: id, cache: false });
    name = m.user.tag === m.displayName ? m.user.tag : `${m.displayName} (${m.user.tag})`;
  } catch {
    name = `membre ${id} (introuvable ou parti)`;
  }
  cache.set(id, name);
  return name;
}

function canRole(channel: NonThreadGuildBasedChannel, role: Role, perm: PermissionResolvable): boolean {
  return channel.permissionsFor(role).has(perm, true);
}

async function exportChannel(
  guild: Guild,
  channel: NonThreadGuildBasedChannel,
  me: GuildMember,
  roles: Role[],
  memberNames: Map<string, string>,
): Promise<ChannelExport> {
  const raw = channel as unknown as Record<string, unknown>;
  const overwrites: OverwriteExport[] = [];
  for (const ow of channel.permissionOverwrites.cache.values()) {
    const isRole = ow.type === OverwriteType.Role;
    const targetName = isRole
      ? (guild.roles.cache.get(ow.id)?.name ?? `rôle supprimé ${ow.id}`)
      : await resolveMemberName(guild, ow.id, memberNames);
    overwrites.push({
      targetType: isRole ? "role" : "member",
      targetId: ow.id,
      targetName,
      allow: permNames(ow.allow),
      deny: permNames(ow.deny),
      allowBits: ow.allow.bitfield.toString(),
      denyBits: ow.deny.bitfield.toString(),
    });
  }
  const nonAdminRoles = roles.filter((r) => !r.permissions.has(PermissionFlagsBits.Administrator));
  const isVoice = channel.type === ChannelType.GuildVoice || channel.type === ChannelType.GuildStageVoice;
  const sendPerm = isVoice ? PermissionFlagsBits.Connect : PermissionFlagsBits.SendMessages;
  return {
    id: channel.id,
    name: channel.name,
    type: channelTypeName(channel.type),
    typeCode: channel.type,
    position: channel.position,
    rawPosition: channel.rawPosition,
    parentId: channel.parentId,
    parentName: channel.parent?.name ?? null,
    topic: str(raw.topic),
    slowmodeSeconds: num(raw.rateLimitPerUser),
    nsfw: bool(raw.nsfw),
    bitrate: num(raw.bitrate),
    userLimit: num(raw.userLimit),
    syncedWithParent: channel.parentId ? channel.permissionsLocked : null,
    viewableByBot: channel.permissionsFor(me).has(PermissionFlagsBits.ViewChannel, true),
    visibleToEveryone: canRole(channel, guild.roles.everyone, PermissionFlagsBits.ViewChannel),
    rolesThatCanView: nonAdminRoles
      .filter((r) => canRole(channel, r, PermissionFlagsBits.ViewChannel))
      .map((r) => r.name),
    rolesThatCanSend: nonAdminRoles
      .filter(
        (r) =>
          canRole(channel, r, PermissionFlagsBits.ViewChannel) && canRole(channel, r, sendPerm),
      )
      .map((r) => r.name),
    overwrites,
  };
}

async function auditGuild(client: Client<true>, guild: Guild, outParent: string): Promise<string> {
  console.log(`Audit de « ${guild.name} » (${guild.id}) — lecture seule…`);

  // Lectures uniquement (GET). Le cache gateway est complété par des fetch.
  const me = await guild.members.fetchMe();
  await guild.roles.fetch();
  await guild.channels.fetch();
  let owner: string | null = null;
  try {
    const o = await guild.fetchOwner();
    owner = o.user.tag;
  } catch {
    owner = null;
  }
  let activeThreads: { id: string; name: string; type: string; parentName: string | null; archived: boolean | null }[] = [];
  try {
    const fetched = await guild.channels.fetchActiveThreads(false);
    activeThreads = [...fetched.threads.values()].map((t) => ({
      id: t.id,
      name: t.name,
      type: channelTypeName(t.type),
      parentName: t.parent?.name ?? null,
      archived: t.archived,
    }));
  } catch (err) {
    console.warn(`[audit] fils actifs non lisibles : ${(err as Error).message}`);
  }

  const roles = [...guild.roles.cache.values()].sort((a, b) => b.position - a.position);
  const roleExports: RoleExport[] = roles.map((r) => ({
    id: r.id,
    name: r.name,
    color: hexColor(r),
    position: r.position,
    hoist: r.hoist,
    mentionable: r.mentionable,
    managed: r.managed,
    isEveryone: r.id === guild.id,
    botTag: r.tags?.botId ? (client.users.cache.get(r.tags.botId)?.tag ?? r.tags.botId) : null,
    permissions: permNames(r.permissions),
    permissionsBits: r.permissions.bitfield.toString(),
  }));

  const all = [...guild.channels.cache.values()].filter(
    (c): c is NonThreadGuildBasedChannel => !c.isThread(),
  );
  const categories = all
    .filter((c) => c.type === ChannelType.GuildCategory)
    .sort((a, b) => a.position - b.position);
  const sortChildren = (list: GuildBasedChannel[]) =>
    list.sort((a, b) => {
      // Discord affiche les salons texte avant les vocaux dans une catégorie.
      const va = a.type === ChannelType.GuildVoice || a.type === ChannelType.GuildStageVoice ? 1 : 0;
      const vb = b.type === ChannelType.GuildVoice || b.type === ChannelType.GuildStageVoice ? 1 : 0;
      if (va !== vb) return va - vb;
      return ("position" in a ? a.position : 0) - ("position" in b ? b.position : 0);
    });

  const memberNames = new Map<string, string>();
  const channelExports: ChannelExport[] = [];
  const orderedChannels: NonThreadGuildBasedChannel[] = [
    ...(sortChildren(all.filter((c) => c.type !== ChannelType.GuildCategory && !c.parentId)) as NonThreadGuildBasedChannel[]),
  ];
  for (const cat of categories) {
    orderedChannels.push(cat);
    orderedChannels.push(
      ...(sortChildren(all.filter((c) => c.parentId === cat.id)) as NonThreadGuildBasedChannel[]),
    );
  }
  for (const ch of orderedChannels) {
    channelExports.push(await exportChannel(guild, ch, me, roles, memberNames));
  }

  const botPerms = permNames(me.permissions);
  const hidden = channelExports.filter((c) => !c.viewableByBot);
  const adminRoles = roleExports.filter((r) => r.permissions.includes("Administrator"));
  const everyone = roleExports.find((r) => r.isEveryone);
  const everyoneSensitive = (everyone?.permissions ?? []).filter((p) => SENSITIVE.has(p));
  const publicChannels = channelExports.filter((c) => c.typeCode !== ChannelType.GuildCategory && c.visibleToEveryone);
  const unsynced = channelExports.filter((c) => c.syncedWithParent === false);
  const memberOverwrites = channelExports.filter((c) => c.overwrites.some((o) => o.targetType === "member"));
  const botHighest = me.roles.highest;
  const rolesAboveBot = roleExports.filter((r) => r.position > botHighest.position);

  const audit = {
    meta: {
      tool: "apps/bot/src/scripts/audit-guild.ts",
      mode: "lecture seule",
      generatedAt: new Date().toISOString(),
      warning:
        "Le bot ne voit que ce que ses rôles lui permettent de voir. Les salons marqués viewableByBot=false " +
        "sont listés par Discord mais le bot n'y a pas accès ; un salon peut aussi manquer entièrement de " +
        "l'export si Discord ne le transmet pas. Pour un audit complet : « Voir les salons » partout, ou " +
        "Administrateur, TEMPORAIREMENT.",
    },
    guild: {
      id: guild.id,
      name: guild.name,
      ownerId: guild.ownerId,
      ownerTag: owner,
      createdAt: guild.createdAt.toISOString(),
      memberCount: guild.memberCount,
      verificationLevel: GuildVerificationLevel[guild.verificationLevel],
      mfaLevel: GuildMFALevel[guild.mfaLevel],
      explicitContentFilter: GuildExplicitContentFilter[guild.explicitContentFilter],
      defaultMessageNotifications: guild.defaultMessageNotifications,
      preferredLocale: guild.preferredLocale,
      features: guild.features,
      systemChannelId: guild.systemChannelId,
      rulesChannelId: guild.rulesChannelId,
      publicUpdatesChannelId: guild.publicUpdatesChannelId,
      afkChannelId: guild.afkChannelId,
      afkTimeout: guild.afkTimeout,
    },
    bot: {
      id: me.id,
      tag: me.user.tag,
      highestRole: botHighest.name,
      highestRolePosition: botHighest.position,
      permissions: botPerms,
      isAdministrator: me.permissions.has(PermissionFlagsBits.Administrator),
    },
    roles: roleExports,
    channels: channelExports,
    activeThreads,
    findings: {
      hiddenFromBot: hidden.map((c) => ({ id: c.id, name: c.name, type: c.type, parentName: c.parentName })),
      administratorRoles: adminRoles.map((r) => r.name),
      everyoneSensitivePermissions: everyoneSensitive,
      channelsVisibleToEveryone: publicChannels.map((c) => c.name),
      channelsNotSyncedWithCategory: unsynced.map((c) => `${c.parentName ?? "?"} / ${c.name}`),
      channelsWithMemberOverwrites: memberOverwrites.map((c) => c.name),
      rolesAboveBot: rolesAboveBot.map((r) => r.name),
    },
  };

  // ---------------------------------------------------------------- Rapport Markdown
  const L: string[] = [];
  L.push(`# Audit Discord — ${md(guild.name)}`);
  L.push("");
  L.push(`> Généré le ${new Date().toLocaleString("fr-FR")} par \`apps/bot/src/scripts/audit-guild.ts\` (lecture seule).`);
  L.push("> Fichier privé : contient des IDs et des noms. **Ne pas le copier dans le dépôt ni sur le Trello public.**");
  L.push("");
  L.push("## 1. Résumé");
  L.push("");
  L.push(`- Catégories : ${categories.length} · salons : ${channelExports.length - categories.length} · rôles : ${roleExports.length} · fils actifs : ${activeThreads.length}`);
  L.push(`- Membres (approx.) : ${guild.memberCount}`);
  L.push(`- 2FA exigée pour la modération : **${guild.mfaLevel === GuildMFALevel.Elevated ? "oui" : "NON"}**`);
  L.push(`- Niveau de vérification : ${GuildVerificationLevel[guild.verificationLevel]}`);
  L.push(`- Salons que le bot **ne voit pas** : **${hidden.length}**`);
  L.push(`- Rôles Administrateur : ${adminRoles.length ? adminRoles.map((r) => md(r.name)).join(", ") : "aucun"}`);
  L.push("");
  L.push("## 2. Ce que le bot voit (et ne voit pas)");
  L.push("");
  L.push(
    "Le bot ne voit que ce que **ses rôles** lui permettent de voir. Les salons ci-dessous existent mais lui sont " +
      "masqués : leurs réglages ci-après viennent de ce que Discord transmet, sans garantie d'exhaustivité. " +
      "Pour un audit complet, donner TEMPORAIREMENT au rôle du bot « Voir les salons » partout (ou Administrateur), " +
      "relancer l'audit, puis retirer cette permission.",
  );
  L.push("");
  L.push(`- Bot : ${md(me.user.tag)} · rôle le plus haut : ${md(botHighest.name)} (position ${botHighest.position})`);
  L.push(`- Permissions du bot sur le serveur : ${permLabels(botPerms)}`);
  L.push("");
  if (hidden.length) {
    L.push("| Salon masqué | Type | Catégorie |");
    L.push("|---|---|---|");
    for (const c of hidden) L.push(`| ${md(c.name)} | ${c.type} | ${md(c.parentName) || "—"} |`);
  } else {
    L.push("Aucun salon masqué : le bot voit tout ce que Discord lui a transmis.");
  }
  L.push("");
  L.push("## 3. Réglages du serveur");
  L.push("");
  L.push(`- Propriétaire : ${md(owner) || "inconnu"}`);
  L.push(`- Créé le : ${guild.createdAt.toLocaleDateString("fr-FR")}`);
  L.push(`- Filtre de contenu explicite : ${GuildExplicitContentFilter[guild.explicitContentFilter]}`);
  L.push(`- Fonctionnalités : ${guild.features.length ? guild.features.join(", ") : "—"}`);
  const chName = (id: string | null) => (id ? md(guild.channels.cache.get(id)?.name ?? id) : "—");
  L.push(`- Salon système : ${chName(guild.systemChannelId)} · règlement : ${chName(guild.rulesChannelId)} · mises à jour communauté : ${chName(guild.publicUpdatesChannelId)} · AFK : ${chName(guild.afkChannelId)}`);
  L.push("");
  L.push("## 4. Rôles (du plus haut au plus bas)");
  L.push("");
  L.push("| Pos. | Rôle | Couleur | Séparé | Mentionnable | Géré | Permissions |");
  L.push("|---|---|---|---|---|---|---|");
  for (const r of roleExports) {
    const managed = r.managed ? (r.botTag ? `bot ${md(r.botTag)}` : "intégration") : "—";
    L.push(
      `| ${r.position} | ${md(r.name)} | ${r.color} | ${r.hoist ? "oui" : "—"} | ${r.mentionable ? "oui" : "—"} | ${managed} | ${permLabels(r.permissions)} |`,
    );
  }
  L.push("");
  L.push("## 5. Catégories et salons");
  L.push("");
  if (adminRoles.length) {
    L.push(`Les rôles Administrateur (${adminRoles.map((r) => md(r.name)).join(", ")}) voient et font tout : ils ne sont pas répétés ci-dessous.`);
    L.push("");
  }
  for (const c of channelExports) {
    const isCat = c.typeCode === ChannelType.GuildCategory;
    L.push(isCat ? `### Catégorie ${md(c.name)}` : `#### ${c.type === "vocal" || c.type === "conférence" ? "(vocal)" : "#"} ${md(c.name)}${c.parentName ? "" : " (hors catégorie)"}`);
    L.push("");
    const facts: string[] = [`type : ${c.type}`, `position : ${c.position}`];
    if (c.slowmodeSeconds) facts.push(`mode lent : ${c.slowmodeSeconds} s`);
    if (c.nsfw) facts.push("NSFW");
    if (c.userLimit) facts.push(`limite : ${c.userLimit}`);
    if (c.syncedWithParent !== null) facts.push(c.syncedWithParent ? "synchronisé avec la catégorie" : "**non synchronisé avec la catégorie**");
    if (!c.viewableByBot) facts.push("**masqué au bot**");
    if (c.visibleToEveryone) facts.push("**visible par @everyone**");
    L.push(`- ${facts.join(" · ")}`);
    if (c.topic) L.push(`- Sujet : ${md(c.topic)}`);
    if (!isCat) {
      L.push(`- Voient : ${c.rolesThatCanView.length ? c.rolesThatCanView.map(md).join(", ") : "aucun rôle (hors Administrateur)"}`);
      L.push(`- ${c.type === "vocal" || c.type === "conférence" ? "Se connectent" : "Écrivent"} : ${c.rolesThatCanSend.length ? c.rolesThatCanSend.map(md).join(", ") : "aucun rôle (hors Administrateur)"}`);
    }
    if (c.overwrites.length) {
      L.push("");
      L.push("| Cible | Autorisé | Refusé |");
      L.push("|---|---|---|");
      for (const o of c.overwrites) {
        L.push(`| ${o.targetType === "role" ? "rôle" : "membre"} ${md(o.targetName)} | ${permLabels(o.allow)} | ${permLabels(o.deny)} |`);
      }
    } else {
      L.push("- Aucune permission propre (hérite des rôles du serveur).");
    }
    L.push("");
  }
  L.push("## 6. Points d'attention");
  L.push("");
  const pts: string[] = [];
  if (guild.mfaLevel !== GuildMFALevel.Elevated) pts.push("La **2FA n'est pas exigée** pour les actions de modération (Paramètres du serveur → Sécurité).");
  if (adminRoles.length > 2) pts.push(`${adminRoles.length} rôles ont **Administrateur** (${adminRoles.map((r) => md(r.name)).join(", ")}) — viser 1 ou 2 personnes.`);
  if (everyoneSensitive.length) pts.push(`@everyone a des permissions sensibles : ${permLabels(everyoneSensitive)}.`);
  if (publicChannels.length) pts.push(`${publicChannels.length} salon(s) visibles par @everyone : ${publicChannels.map((c) => md(c.name)).join(", ")}.`);
  if (unsynced.length) pts.push(`${unsynced.length} salon(s) non synchronisés avec leur catégorie (permissions à vérifier une par une).`);
  if (memberOverwrites.length) pts.push(`${memberOverwrites.length} salon(s) avec des permissions données à un **membre** nommément (à remplacer par des rôles) : ${memberOverwrites.map((c) => md(c.name)).join(", ")}.`);
  if (audit.bot.isAdministrator) pts.push("Le bot a **Administrateur** : à retirer dès l'audit terminé.");
  if (rolesAboveBot.length) pts.push(`Rôles au-dessus du bot (il ne pourra pas les gérer) : ${rolesAboveBot.map((r) => md(r.name)).join(", ")}.`);
  if (hidden.length) pts.push(`${hidden.length} salon(s) masqués au bot : audit **incomplet** pour eux.`);
  L.push(pts.length ? pts.map((p) => `- ${p}`).join("\n") : "- Rien de notable.");
  L.push("");
  if (activeThreads.length) {
    L.push("## 7. Fils actifs");
    L.push("");
    L.push("| Fil | Type | Salon parent |");
    L.push("|---|---|---|");
    for (const t of activeThreads) L.push(`| ${md(t.name)} | ${t.type} | ${md(t.parentName) || "—"} |`);
    L.push("");
  }

  const outDir = prepareOutDir(outParent);
  writeFileSync(join(outDir, "audit.json"), JSON.stringify(audit, null, 2), { encoding: "utf8", flag: "wx" });
  writeFileSync(join(outDir, "RAPPORT.md"), L.join("\n"), { encoding: "utf8", flag: "wx" });
  console.log(`OK — ${channelExports.length} salons/catégories, ${roleExports.length} rôles, ${hidden.length} salon(s) masqué(s) au bot.`);
  console.log(`Écrit dans : ${outDir}`);
  return outDir;
}

// ---------------------------------------------------------------------------
// Lecture des messages (option --lire / --tout) — GET uniquement
// ---------------------------------------------------------------------------

/** Salons dont on peut lire l'historique directement. */
const READABLE_TYPES = new Set<ChannelType>([
  ChannelType.GuildText,
  ChannelType.GuildAnnouncement,
  ChannelType.GuildVoice,
  ChannelType.GuildStageVoice,
]);
/** Salons dont les messages vivent dans des fils (posts). */
const FORUM_TYPES = new Set<ChannelType>([ChannelType.GuildForum, ChannelType.GuildMedia]);
/** Types retenus par « tout-texte » (pas le chat textuel des vocaux, surtout du bruit). */
const ALL_TEXT_TYPES = new Set<ChannelType>([
  ChannelType.GuildText,
  ChannelType.GuildAnnouncement,
  ChannelType.GuildForum,
  ChannelType.GuildMedia,
]);

/** Compare des noms de salon sans emoji, séparateur, casse ni accent : « 📜・règlement-staff » ≈ « reglement-staff ». */
function normName(s: string): string {
  return s
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, "");
}

function fileSlug(s: string): string {
  const slug = s
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/[^\p{L}\p{N}_-]+/gu, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "")
    .toLowerCase();
  return slug || "salon";
}

interface SelectedChannel {
  channel: NonThreadGuildBasedChannel;
  /** Ce qui l'a fait choisir (pour l'index). */
  via: string;
}

function selectChannels(guild: Guild, wanted: string[]): { selected: SelectedChannel[]; notFound: string[] } {
  const all = [...guild.channels.cache.values()].filter(
    (c): c is NonThreadGuildBasedChannel => !c.isThread(),
  );
  // Ordre d'affichage : catégorie puis position.
  const catPos = (c: NonThreadGuildBasedChannel) =>
    c.parent ? c.parent.position : c.type === ChannelType.GuildCategory ? c.position : -1;
  all.sort((a, b) => catPos(a) - catPos(b) || a.position - b.position);

  const isReadable = (c: NonThreadGuildBasedChannel) => READABLE_TYPES.has(c.type) || FORUM_TYPES.has(c.type);
  const out = new Map<string, SelectedChannel>();
  const notFound: string[] = [];

  if (wanted.length === 1 && wanted[0] === ALL_TEXT) {
    for (const c of all) if (ALL_TEXT_TYPES.has(c.type)) out.set(c.id, { channel: c, via: ALL_TEXT });
    return { selected: [...out.values()], notFound };
  }

  for (const w of wanted) {
    let matches: NonThreadGuildBasedChannel[] = all.filter((c) => c.id === w);
    if (matches.length === 0) {
      const n = normName(w);
      matches = all.filter((c) => normName(c.name) === n);
      if (matches.length === 0 && n.length >= 3) matches = all.filter((c) => normName(c.name).includes(n));
    }
    if (matches.length === 0) {
      notFound.push(w);
      continue;
    }
    for (const m of matches) {
      if (m.type === ChannelType.GuildCategory) {
        // Une catégorie = tous ses salons texte / annonces / forums.
        for (const child of all) {
          if (child.parentId === m.id && ALL_TEXT_TYPES.has(child.type) && !out.has(child.id)) {
            out.set(child.id, { channel: child, via: `catégorie « ${m.name} »` });
          }
        }
      } else if (isReadable(m) && !out.has(m.id)) {
        out.set(m.id, { channel: m, via: `« ${w} »` });
      }
    }
  }
  return { selected: [...out.values()], notFound };
}

interface EmbedExport {
  title: string | null;
  url: string | null;
  color: string | null;
  author: string | null;
  description: string | null;
  fields: { name: string; value: string; inline: boolean }[];
  footer: string | null;
  timestamp: string | null;
  image: string | null;
  thumbnail: string | null;
}

/** Composant aplati (boutons, menus, et composants v2 : texte, section, galerie, conteneur…). */
interface ComponentExport {
  depth: number;
  type: string;
  label: string | null;
  style: string | null;
  emoji: string | null;
  url: string | null;
  customId: string | null;
  text: string | null;
  options: string[];
  media: string[];
  disabled: boolean | null;
}

interface MessageExport {
  id: string;
  createdAt: string;
  editedAt: string | null;
  author: string;
  authorIsBot: boolean;
  viaWebhook: boolean;
  type: string;
  pinned: boolean;
  content: string;
  embeds: EmbedExport[];
  components: ComponentExport[];
  attachments: { name: string; url: string; contentType: string | null; size: number }[];
  stickers: string[];
  reactions: { emoji: string; count: number }[];
  poll: { question: string; answers: { text: string; votes: number }[]; finalized: boolean } | null;
  /** Messages transférés (« forward ») : contenu et embeds d'origine. */
  forwarded: { content: string; embeds: EmbedExport[]; attachments: string[] }[];
}

interface ThreadExport {
  id: string;
  name: string;
  archived: boolean | null;
  tags: string[];
  messages: MessageExport[];
}

interface ChannelMessagesExport {
  id: string;
  name: string;
  type: string;
  category: string | null;
  topic: string | null;
  selectedVia: string;
  limit: number;
  /** Lu jusqu'au début du salon (moins de messages que la limite). */
  complete: boolean;
  skipped: string | null;
  messages: MessageExport[];
  threads: ThreadExport[];
}

const COMPONENT_TYPE_FR: Record<number, string> = {
  1: "rangée",
  2: "bouton",
  3: "menu",
  4: "champ texte",
  5: "menu membres",
  6: "menu rôles",
  7: "menu mentions",
  8: "menu salons",
  9: "section",
  10: "texte",
  11: "vignette",
  12: "galerie",
  13: "fichier",
  14: "séparateur",
  17: "conteneur",
  18: "libellé",
};
const BUTTON_STYLE_FR: Record<number, string> = {
  1: "primaire",
  2: "secondaire",
  3: "succès",
  4: "danger",
  5: "lien",
  6: "premium",
};

function embedExport(e: { toJSON(): unknown }): EmbedExport {
  const j = e.toJSON() as {
    title?: string;
    url?: string;
    color?: number;
    author?: { name?: string };
    description?: string;
    fields?: { name: string; value: string; inline?: boolean }[];
    footer?: { text?: string };
    timestamp?: string;
    image?: { url?: string };
    thumbnail?: { url?: string };
  };
  return {
    title: j.title ?? null,
    url: j.url ?? null,
    color: typeof j.color === "number" ? `#${j.color.toString(16).padStart(6, "0")}` : null,
    author: j.author?.name ?? null,
    description: j.description ?? null,
    fields: (j.fields ?? []).map((f) => ({ name: f.name, value: f.value, inline: f.inline ?? false })),
    footer: j.footer?.text ?? null,
    timestamp: j.timestamp ?? null,
    image: j.image?.url ?? null,
    thumbnail: j.thumbnail?.url ?? null,
  };
}

function emojiText(v: unknown): string | null {
  if (!v || typeof v !== "object") return null;
  const e = v as { name?: string | null; id?: string | null };
  if (e.id) return `:${e.name ?? "emoji"}:`;
  return e.name ?? null;
}

/** Aplatit récursivement un composant brut (JSON de l'API) — aucun type n'est supposé connu. */
function flattenComponent(raw: unknown, depth: number, out: ComponentExport[]): void {
  if (!raw || typeof raw !== "object") return;
  const c = raw as Record<string, unknown>;
  const typeCode = typeof c.type === "number" ? c.type : -1;
  const media: string[] = [];
  const pushUrl = (m: unknown) => {
    if (m && typeof m === "object" && typeof (m as { url?: unknown }).url === "string") {
      media.push((m as { url: string }).url);
    }
  };
  pushUrl(c.media);
  pushUrl(c.file);
  if (Array.isArray(c.items)) for (const it of c.items) pushUrl((it as { media?: unknown }).media);
  const options = Array.isArray(c.options)
    ? c.options
        .map((o) => (o && typeof o === "object" ? (o as { label?: unknown }).label : null))
        .filter((l): l is string => typeof l === "string")
    : [];
  out.push({
    depth,
    type: COMPONENT_TYPE_FR[typeCode] ?? `type ${typeCode}`,
    label: typeof c.label === "string" ? c.label : typeof c.placeholder === "string" ? c.placeholder : null,
    style: typeCode === 2 && typeof c.style === "number" ? (BUTTON_STYLE_FR[c.style] ?? String(c.style)) : null,
    emoji: emojiText(c.emoji),
    url: typeof c.url === "string" ? c.url : null,
    customId: typeof c.custom_id === "string" ? c.custom_id : null,
    text: typeof c.content === "string" ? c.content : null,
    options,
    media,
    disabled: typeof c.disabled === "boolean" ? c.disabled : null,
  });
  if (Array.isArray(c.components)) for (const child of c.components) flattenComponent(child, depth + 1, out);
  if (c.accessory) flattenComponent(c.accessory, depth + 1, out);
}

function messageExport(m: Message): MessageExport {
  const components: ComponentExport[] = [];
  for (const top of m.components) flattenComponent(top.toJSON(), 0, components);
  const forwarded = [...m.messageSnapshots.values()].map((s) => ({
    content: s.content ?? "",
    embeds: (s.embeds ?? []).map(embedExport),
    attachments: s.attachments ? [...s.attachments.values()].map((a) => a.url) : [],
  }));
  return {
    id: m.id,
    createdAt: m.createdAt.toISOString(),
    editedAt: m.editedAt ? m.editedAt.toISOString() : null,
    author: m.author.displayName === m.author.username ? m.author.username : `${m.author.displayName} (${m.author.username})`,
    authorIsBot: m.author.bot,
    viaWebhook: m.webhookId !== null,
    type: MessageTypeName(m.type),
    pinned: m.pinned,
    content: m.content,
    embeds: m.embeds.map(embedExport),
    components,
    attachments: [...m.attachments.values()].map((a) => ({
      name: a.name,
      url: a.url,
      contentType: a.contentType,
      size: a.size,
    })),
    stickers: [...m.stickers.values()].map((s) => s.name),
    reactions: [...m.reactions.cache.values()].map((r) => ({
      emoji: r.emoji.id ? `:${r.emoji.name ?? "emoji"}:` : (r.emoji.name ?? "?"),
      count: r.count,
    })),
    poll: m.poll
      ? {
          question: m.poll.question.text ?? "",
          answers: [...m.poll.answers.values()].map((a) => ({
            text: ("text" in a ? a.text : null) ?? "",
            votes: "voteCount" in a ? a.voteCount : 0,
          })),
          finalized: m.poll.resultsFinalized,
        }
      : null,
    forwarded,
  };
}

function MessageTypeName(t: number): string {
  const names: Record<number, string> = {
    0: "message",
    6: "épinglage",
    7: "arrivée",
    8: "boost",
    18: "fil créé",
    19: "réponse",
    20: "commande",
    21: "début de fil",
    46: "résultat de sondage",
  };
  return names[t] ?? `type ${t}`;
}

/** Les N derniers messages, du plus ancien au plus récent. GET /channels/:id/messages, par pages de 100. */
async function fetchHistory(
  channel: GuildTextBasedChannel,
  limit: number,
): Promise<{ messages: MessageExport[]; complete: boolean }> {
  const collected: Message[] = [];
  let before: string | undefined;
  let complete = false;
  while (collected.length < limit) {
    const requested = Math.min(100, limit - collected.length);
    const batch = await channel.messages.fetch({ limit: requested, before, cache: false });
    const sorted = [...batch.values()].sort((a, b) => b.createdTimestamp - a.createdTimestamp);
    collected.push(...sorted);
    before = sorted[sorted.length - 1]?.id;
    if (batch.size < requested || !before) {
      complete = true;
      break;
    }
  }
  collected.sort((a, b) => a.createdTimestamp - b.createdTimestamp);
  return { messages: collected.map(messageExport), complete };
}

function canRead(channel: NonThreadGuildBasedChannel, me: GuildMember): string | null {
  const p = channel.permissionsFor(me);
  if (!p.has(PermissionFlagsBits.ViewChannel, true)) return "le bot ne voit pas ce salon (Voir les salons)";
  if (!p.has(PermissionFlagsBits.ReadMessageHistory, true)) return "le bot n'a pas « Voir l'historique des messages »";
  return null;
}

async function exportForumThreads(
  forum: NonThreadGuildBasedChannel,
  limit: number,
): Promise<{ threads: ThreadExport[]; complete: boolean }> {
  if (!("threads" in forum) || !forum.threads) return { threads: [], complete: true };
  const manager = forum.threads;
  const found = new Map<string, AnyThreadChannel>();
  // Fils actifs (GET /guilds/:id/threads/active) puis archivés publics (GET …/threads/archived/public).
  const active = await manager.fetchActive(false);
  for (const t of active.threads.values()) found.set(t.id, t);
  let before: Date | undefined;
  let complete = true;
  for (;;) {
    if (found.size >= limit) {
      complete = false;
      break;
    }
    const page = await manager.fetchArchived({ type: "public", limit: 100, before }, false);
    for (const t of page.threads.values()) found.set(t.id, t);
    if (!page.hasMore || page.threads.size === 0) break;
    const oldest = [...page.threads.values()]
      .map((t) => t.archivedAt)
      .filter((d): d is Date => d instanceof Date)
      .sort((a, b) => a.getTime() - b.getTime())[0];
    if (!oldest) break;
    before = oldest;
  }
  const tagNames = new Map<string, string>();
  if ("availableTags" in forum && Array.isArray(forum.availableTags)) {
    for (const t of forum.availableTags as { id: string; name: string }[]) tagNames.set(t.id, t.name);
  }
  const threads: ThreadExport[] = [];
  for (const t of [...found.values()].slice(0, limit)) {
    let messages: MessageExport[] = [];
    try {
      messages = (await fetchHistory(t, limit)).messages;
    } catch (err) {
      console.warn(`[lecture] fil « ${t.name} » illisible : ${(err as Error).message}`);
    }
    threads.push({
      id: t.id,
      name: t.name,
      archived: t.archived,
      tags: (t.appliedTags ?? []).map((id) => tagNames.get(id) ?? id),
      messages,
    });
  }
  threads.sort((a, b) => (a.messages[0]?.createdAt ?? "").localeCompare(b.messages[0]?.createdAt ?? ""));
  return { threads, complete };
}

function quote(text: string): string {
  return text
    .split(/\r?\n/)
    .map((l) => `> ${l}`)
    .join("\n");
}

function renderMessageMd(m: MessageExport, guild: Guild): string[] {
  const resolveMentions = (s: string) =>
    s
      .replace(/<#(\d{17,20})>/g, (_, id: string) => `#${guild.channels.cache.get(id)?.name ?? id}`)
      .replace(/<@&(\d{17,20})>/g, (_, id: string) => `@${guild.roles.cache.get(id)?.name ?? id}`);
  const L: string[] = [];
  const flags = [
    m.authorIsBot ? (m.viaWebhook ? "webhook" : "bot") : null,
    m.pinned ? "épinglé" : null,
    m.type !== "message" && m.type !== "réponse" ? m.type : null,
    m.editedAt ? "modifié" : null,
  ].filter(Boolean);
  const date = m.createdAt.replace("T", " ").slice(0, 16);
  L.push(`### ${date} — ${m.author}${flags.length ? ` · ${flags.join(" · ")}` : ""}`);
  L.push("");
  if (m.content) {
    L.push(resolveMentions(m.content));
    L.push("");
  }
  const renderEmbed = (e: EmbedExport, label: string) => {
    L.push(`> **${label}**${e.color ? ` · couleur ${e.color}` : ""}`);
    if (e.author) L.push(`> Auteur : ${e.author}`);
    if (e.title) L.push(`> **Titre : ${e.title}**${e.url ? ` (${e.url})` : ""}`);
    if (e.description) {
      L.push(">");
      L.push(quote(resolveMentions(e.description)));
    }
    for (const f of e.fields) {
      L.push(">");
      L.push(`> **[champ${f.inline ? ", en ligne" : ""}] ${f.name}**`);
      L.push(quote(resolveMentions(f.value)));
    }
    if (e.footer) L.push(`>\n> Pied : ${e.footer}`);
    if (e.image) L.push(`> Image : ${e.image}`);
    if (e.thumbnail) L.push(`> Vignette : ${e.thumbnail}`);
    L.push("");
  };
  m.embeds.forEach((e, i) => renderEmbed(e, m.embeds.length > 1 ? `Embed ${i + 1}` : "Embed"));
  for (const f of m.forwarded) {
    L.push("> **Message transféré**");
    if (f.content) L.push(quote(resolveMentions(f.content)));
    L.push("");
    f.embeds.forEach((e) => renderEmbed(e, "Embed (transféré)"));
    for (const a of f.attachments) L.push(`- Pièce jointe (transférée) : ${a}`);
  }
  if (m.components.length) {
    L.push("Composants :");
    for (const c of m.components) {
      if (c.type === "rangée" || c.type === "séparateur") continue;
      const bits = [
        c.emoji,
        c.label ? `« ${c.label} »` : null,
        c.style ? `style ${c.style}` : null,
        c.url ? `→ ${c.url}` : null,
        c.customId ? `id ${c.customId}` : null,
        c.disabled ? "désactivé" : null,
        c.options.length ? `options : ${c.options.join(" / ")}` : null,
        c.media.length ? `médias : ${c.media.join(", ")}` : null,
      ].filter(Boolean);
      const pad = "  ".repeat(Math.max(0, c.depth - 1));
      L.push(`${pad}- ${c.type}${bits.length ? ` : ${bits.join(" · ")}` : ""}`);
      if (c.text) L.push(quote(resolveMentions(c.text)));
    }
    L.push("");
  }
  if (m.poll) {
    L.push(`Sondage : **${m.poll.question}**${m.poll.finalized ? " (terminé)" : ""}`);
    for (const a of m.poll.answers) L.push(`- ${a.text} — ${a.votes} vote(s)`);
    L.push("");
  }
  for (const a of m.attachments) L.push(`- Pièce jointe : ${a.name} — ${a.url}`);
  if (m.stickers.length) L.push(`- Autocollants : ${m.stickers.join(", ")}`);
  if (m.reactions.length) L.push(`- Réactions : ${m.reactions.map((r) => `${r.emoji} ${r.count}`).join(" · ")}`);
  if (m.attachments.length || m.stickers.length || m.reactions.length) L.push("");
  return L;
}

async function exportMessages(guild: Guild, outDir: string, wanted: string[], limit: number): Promise<void> {
  const me = await guild.members.fetchMe();
  const { selected, notFound } = selectChannels(guild, wanted);
  for (const w of notFound) console.warn(`[lecture] aucun salon ni catégorie ne correspond à « ${w} »`);
  if (selected.length === 0) {
    console.warn("[lecture] rien à lire.");
    return;
  }
  const dir = join(outDir, "messages");
  mkdirSync(dir, { recursive: true });
  const usedNames = new Set<string>();
  const index: { name: string; category: string | null; file: string | null; count: number; threads: number; complete: boolean; skipped: string | null; via: string }[] = [];

  for (const { channel, via } of selected) {
    const exp: ChannelMessagesExport = {
      id: channel.id,
      name: channel.name,
      type: channelTypeName(channel.type),
      category: channel.parent?.name ?? null,
      topic: str((channel as unknown as Record<string, unknown>).topic),
      selectedVia: via,
      limit,
      complete: false,
      skipped: canRead(channel, me),
      messages: [],
      threads: [],
    };
    if (!exp.skipped) {
      try {
        if (FORUM_TYPES.has(channel.type)) {
          const r = await exportForumThreads(channel, limit);
          exp.threads = r.threads;
          exp.complete = r.complete;
        } else if (channel.isTextBased()) {
          const r = await fetchHistory(channel, limit);
          exp.messages = r.messages;
          exp.complete = r.complete;
        } else {
          exp.skipped = "type de salon sans messages";
        }
      } catch (err) {
        exp.skipped = `lecture refusée ou impossible : ${(err as Error).message}`;
      }
    }

    let base = fileSlug(channel.name);
    if (usedNames.has(base)) base = `${base}-${channel.id.slice(-4)}`;
    usedNames.add(base);

    const count = exp.messages.length + exp.threads.reduce((n, t) => n + t.messages.length, 0);
    if (exp.skipped) {
      console.warn(`[lecture] ${channel.name} : ignoré (${exp.skipped})`);
      index.push({ name: channel.name, category: exp.category, file: null, count: 0, threads: 0, complete: false, skipped: exp.skipped, via });
      continue;
    }

    const L: string[] = [];
    L.push(`# ${exp.category ? `${md(exp.category)} / ` : ""}${md(channel.name)}`);
    L.push("");
    L.push(`> Export en lecture seule du ${new Date().toLocaleString("fr-FR")} · type : ${exp.type} · ${count} message(s)` +
      (exp.threads.length ? ` dans ${exp.threads.length} fil(s)` : "") +
      ` · limite ${limit} ${exp.complete ? "(salon lu en entier)" : "(**tronqué** : seuls les plus récents)"}.`);
    L.push("> Fichier privé : contient des pseudos. Ne pas le copier dans le dépôt ni sur le Trello public.");
    if (exp.topic) L.push(`>\n> Sujet du salon : ${md(exp.topic)}`);
    L.push("");
    if (exp.threads.length) {
      for (const t of exp.threads) {
        L.push(`## Fil : ${md(t.name)}${t.tags.length ? ` · étiquettes : ${t.tags.join(", ")}` : ""}${t.archived ? " · archivé" : ""}`);
        L.push("");
        for (const m of t.messages) L.push(...renderMessageMd(m, guild));
      }
    } else if (exp.messages.length === 0) {
      L.push("_Aucun message._");
    } else {
      for (const m of exp.messages) L.push(...renderMessageMd(m, guild));
    }
    writeFileSync(join(dir, `${base}.json`), JSON.stringify(exp, null, 2), { encoding: "utf8", flag: "wx" });
    writeFileSync(join(dir, `${base}.md`), L.join("\n"), { encoding: "utf8", flag: "wx" });
    index.push({ name: channel.name, category: exp.category, file: `${base}.md`, count, threads: exp.threads.length, complete: exp.complete, skipped: null, via });
    console.log(`[lecture] ${channel.name} : ${count} message(s)${exp.threads.length ? ` / ${exp.threads.length} fil(s)` : ""}`);
  }

  const I: string[] = [];
  I.push(`# Messages exportés — ${md(guild.name)}`);
  I.push("");
  I.push(`> ${new Date().toLocaleString("fr-FR")} · limite ${limit} par salon (et par fil de forum) · lecture seule (GET).`);
  if (notFound.length) I.push(`>\n> Introuvables : ${notFound.map(md).join(", ")}`);
  I.push("");
  I.push("| Catégorie | Salon | Messages | Fils | Complet | Fichier / raison | Choisi par |");
  I.push("|---|---|---|---|---|---|---|");
  for (const r of index) {
    I.push(`| ${md(r.category) || "—"} | ${md(r.name)} | ${r.count} | ${r.threads || "—"} | ${r.skipped ? "—" : r.complete ? "oui" : "tronqué"} | ${r.file ? `[${r.file}](./${encodeURI(r.file)})` : `ignoré : ${md(r.skipped)}`} | ${md(r.via)} |`);
  }
  writeFileSync(join(dir, "INDEX.md"), I.join("\n"), { encoding: "utf8", flag: "wx" });
  const read = index.filter((r) => !r.skipped).length;
  console.log(`Lecture : ${read} salon(s) exporté(s), ${index.length - read} ignoré(s) → ${dir}`);
}

function listGuilds(client: Client<true>): void {
  const guilds = [...client.guilds.cache.values()].sort((a, b) => a.name.localeCompare(b.name));
  console.log(`Le bot ${client.user.tag} est présent sur ${guilds.length} serveur(s) :`);
  for (const g of guilds) {
    const me = g.members.me;
    const admin = me?.permissions.has(PermissionFlagsBits.Administrator) ? " · bot ADMIN" : "";
    const tag = g.id === communityGuildId ? "  ← DISCORD_GUILD_ID (serveur communautaire)" : "";
    console.log(`  - ${g.name}  [${g.id}]  ~${g.memberCount} membres${admin}${tag}`);
  }
  console.log("\nPour auditer un serveur : pnpm --filter @reborn/bot audit:guild --guild <id>");
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

// Vérifié AVANT la connexion : on ne se connecte pas si l'export ne peut pas être écrit hors dépôt.
if (args.guildId) assertOutsideRepo(args.outParent);

// Intents : Guilds seul pour l'audit de structure. GuildMessages + MessageContent seulement si l'on
// lit des messages (--lire / --tout) ; jamais DirectMessages (aucun message privé n'est lu).
const intents = args.read
  ? [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages, GatewayIntentBits.MessageContent]
  : [GatewayIntentBits.Guilds];
const client = new Client({ intents });
installReadOnlyGuard(client);

const timeout = setTimeout(() => {
  console.error("Pas de connexion à Discord après 45 s — abandon.");
  void client.destroy().finally(() => process.exit(1));
}, 45_000);

client.once(Events.ClientReady, async (ready) => {
  // Connecté : le délai ne couvre que la connexion, pas la lecture (longue sur un gros serveur).
  clearTimeout(timeout);
  let code = 0;
  try {
    if (!args.guildId) {
      listGuilds(ready);
    } else {
      const guild = ready.guilds.cache.get(args.guildId);
      if (!guild) {
        console.error(`Le bot n'est pas sur le serveur ${args.guildId} (ou ne l'a pas encore reçu).`);
        listGuilds(ready);
        code = 1;
      } else {
        const outDir = await auditGuild(ready, guild, args.outParent);
        if (args.read) await exportMessages(guild, outDir, args.read, args.limit);
      }
    }
  } catch (err) {
    console.error("[audit] échec :", err);
    code = 1;
  } finally {
    clearTimeout(timeout);
    await ready.destroy();
    process.exit(code);
  }
});

await client.login(token);
