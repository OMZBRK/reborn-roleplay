/**
 * ============================================================================
 *  PLAN DISCORD — bibliothèque commune à plan-guild.ts (simulation, GET seul)
 *  et apply-guild.ts (application par lots, empreinte obligatoire).
 * ============================================================================
 *
 *  Ce module ne parle pas à Discord : il lit un plan JSON (hors dépôt, il
 *  contient des IDs), photographie l'état d'un serveur à partir du cache
 *  discord.js et calcule le DIFF (opérations rangées par lot) + son empreinte.
 *
 *  Lots, dans l'ordre d'application :
 *    roles         (a) création des nouveaux rôles et séparateurs
 *    roles-maj     (b) renommages / couleurs / séparé / mentionnable
 *    roles-ordre   (c) positions des rôles
 *    permissions   (d) permissions des rôles (+ @everyone, en dernier)
 *    salons        (e) catégories et salons : création, renommage, déplacement
 *    overwrites    (f) permissions des catégories et salons
 *    salons-ordre  (g) positions des catégories et salons
 *
 *  Jamais de suppression : ce qui n'est pas dans le plan est seulement listé.
 *  Doc : docs/operations/DISCORD_STAFF.md
 * ============================================================================
 */
import { createHash } from "node:crypto";
import { existsSync } from "node:fs";
import { dirname, isAbsolute, join, relative, resolve } from "node:path";
import {
  ChannelType,
  OverwriteType,
  PermissionFlagsBits,
  PermissionsBitField,
  type Client,
  type Guild,
  type GuildMember,
  type NonThreadGuildBasedChannel,
} from "discord.js";

// ---------------------------------------------------------------------------
// Chemins, dépôt
// ---------------------------------------------------------------------------

export function findUp(start: string, name: string, maxDepth = 8): string | null {
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

export function isInside(child: string, parent: string): boolean {
  const rel = relative(parent, child);
  return rel === "" || (!rel.startsWith("..") && !isAbsolute(rel));
}

/** Refuse tout chemin (plan, sortie) situé dans le dépôt : ces fichiers contiennent des IDs (règle 7). */
export function assertOutsideRepo(path: string, repoRoot: string | null, what: string): void {
  const abs = resolve(path);
  if (repoRoot && isInside(abs, repoRoot)) {
    console.error(`Refus : ${what} (${abs}) est dans le dépôt. Il contient des IDs : garde-le hors dépôt.`);
    process.exit(2);
  }
}

export function timestamp(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}

// ---------------------------------------------------------------------------
// Plan (JSON hors dépôt)
// ---------------------------------------------------------------------------

export type PermName = keyof typeof PermissionFlagsBits;

export interface PlanOrigin {
  id: string;
  name: string;
}

export interface PlanRole {
  key: string;
  name: string;
  origin: PlanOrigin | null;
  /** "#RRGGBB", null = aucune couleur, "keep" = ne pas toucher. */
  color: string | null;
  hoist: boolean;
  mentionable: boolean;
  /** Liste de permissions, ou "keep" = ne pas toucher. */
  permissions: PermName[] | "keep";
  separator?: boolean;
  specialty?: boolean;
  /** Rôle que le bot ne peut pas modifier (son propre rôle) : actions listées pour l'utilisateur. */
  manual?: boolean;
  /** Rôle géré par Discord (Server Booster) : seul l'ordre est réglé. */
  managed?: boolean;
  /** Administrateur retiré seulement par « --lot permissions » explicite, Fondateur attribué. */
  adminRemoval?: boolean;
  note?: string;
}

export interface PlanOverwrite {
  /** Clé de rôle du plan, ou "everyone". */
  role: string;
  allow: PermName[];
  deny: PermName[];
}

export type PlanChannelType = "text" | "voice" | "announcement" | "forum" | "stage";

export interface PlanChannel {
  key: string;
  name: string;
  type: PlanChannelType;
  origin: PlanOrigin | null;
  /** true = permissions synchronisées avec la catégorie. */
  sync: boolean;
  overwrites?: PlanOverwrite[];
  topic?: string;
  note?: string;
}

export interface PlanCategory {
  key: string;
  name: string;
  origin: PlanOrigin | null;
  overwrites: PlanOverwrite[];
  channels: PlanChannel[];
  note?: string;
}

export interface Plan {
  version: number;
  title: string;
  guild: { id: string; name: string };
  source?: string;
  everyone: { key: "everyone"; permissions: PermName[]; note?: string };
  roles: PlanRole[];
  categories: PlanCategory[];
}

export function validatePlan(plan: Plan): string[] {
  const errors: string[] = [];
  const keys = new Set<string>();
  const roleKeys = new Set<string>(["everyone"]);
  const checkPerm = (p: string, where: string) => {
    if (!(p in PermissionFlagsBits)) errors.push(`${where} : permission inconnue « ${p} »`);
  };
  if (!/^\d{17,20}$/.test(plan.guild?.id ?? "")) errors.push("guild.id manquant ou invalide");
  for (const r of plan.roles) {
    if (keys.has(r.key)) errors.push(`clé en double : ${r.key}`);
    keys.add(r.key);
    roleKeys.add(r.key);
    if (r.name.length < 1 || r.name.length > 100) errors.push(`rôle ${r.key} : nom de 1 à 100 caractères`);
    if (r.color !== null && r.color !== "keep" && !/^#[0-9A-Fa-f]{6}$/.test(r.color)) errors.push(`rôle ${r.key} : couleur invalide`);
    if (r.permissions !== "keep") for (const p of r.permissions) checkPerm(p, `rôle ${r.key}`);
  }
  for (const p of plan.everyone.permissions) checkPerm(p, "@everyone");
  const checkOw = (list: PlanOverwrite[], where: string) => {
    for (const o of list) {
      if (!roleKeys.has(o.role)) errors.push(`${where} : rôle inconnu « ${o.role} »`);
      for (const p of [...o.allow, ...o.deny]) checkPerm(p, where);
    }
  };
  for (const c of plan.categories) {
    if (keys.has(c.key)) errors.push(`clé en double : ${c.key}`);
    keys.add(c.key);
    checkOw(c.overwrites, `catégorie ${c.key}`);
    for (const ch of c.channels) {
      if (keys.has(ch.key)) errors.push(`clé en double : ${ch.key}`);
      keys.add(ch.key);
      if (!ch.sync) checkOw(ch.overwrites ?? [], `salon ${ch.key}`);
      if (ch.name.length < 1 || ch.name.length > 100) errors.push(`salon ${ch.key} : nom de 1 à 100 caractères`);
    }
  }
  return errors;
}

// ---------------------------------------------------------------------------
// Permissions : noms <-> bits, libellés français
// ---------------------------------------------------------------------------

export const PERM_FR: Record<string, string> = {
  CreateInstantInvite: "Créer une invitation",
  KickMembers: "Expulser",
  BanMembers: "Bannir",
  Administrator: "ADMINISTRATEUR",
  ManageChannels: "Gérer les salons",
  ManageGuild: "Gérer le serveur",
  AddReactions: "Réactions",
  ViewAuditLog: "Voir les logs",
  PrioritySpeaker: "Voix prioritaire",
  Stream: "Vidéo",
  ViewChannel: "Voir",
  SendMessages: "Écrire",
  SendTTSMessages: "TTS",
  ManageMessages: "Gérer les messages",
  EmbedLinks: "Intégrer des liens",
  AttachFiles: "Joindre",
  ReadMessageHistory: "Historique",
  MentionEveryone: "Mentionner @everyone",
  UseExternalEmojis: "Émojis externes",
  ViewGuildInsights: "Statistiques",
  Connect: "Se connecter",
  Speak: "Parler",
  MuteMembers: "Rendre muet",
  DeafenMembers: "Sourdine",
  MoveMembers: "Déplacer",
  UseVAD: "Détection de voix",
  ChangeNickname: "Changer de pseudo",
  ManageNicknames: "Gérer les pseudos",
  ManageRoles: "Gérer les rôles",
  ManageWebhooks: "Gérer les webhooks",
  ManageGuildExpressions: "Gérer les expressions",
  UseApplicationCommands: "Commandes",
  RequestToSpeak: "Demander la parole",
  ManageEvents: "Gérer les événements",
  ManageThreads: "Gérer les fils",
  CreatePublicThreads: "Fils publics",
  CreatePrivateThreads: "Fils privés",
  UseExternalStickers: "Autocollants externes",
  SendMessagesInThreads: "Écrire dans les fils",
  UseEmbeddedActivities: "Activités",
  ModerateMembers: "Exclure temporairement",
  ViewCreatorMonetizationAnalytics: "Monétisation",
  UseSoundboard: "Soundboard",
  CreateGuildExpressions: "Créer des expressions",
  CreateEvents: "Créer des événements",
  UseExternalSounds: "Sons externes",
  SendVoiceMessages: "Messages vocaux",
  SendPolls: "Sondages",
  UseExternalApps: "Applications externes",
  PinMessages: "Épingler",
  BypassSlowmode: "Ignorer le mode lent",
};

export function bitsOf(names: readonly string[]): bigint {
  let b = 0n;
  for (const n of names) {
    const v = (PermissionFlagsBits as Record<string, bigint>)[n];
    if (v === undefined) throw new Error(`permission inconnue : ${n}`);
    b |= v;
  }
  return b;
}

export function namesOf(bits: bigint): string[] {
  // ManageEmojisAndStickers et ManageGuildExpressions sont le même bit : on garde le nom récent.
  return new PermissionsBitField(bits).toArray().filter((n) => n !== "ManageEmojisAndStickers");
}

export function permLabels(names: readonly string[]): string {
  if (names.length === 0) return "—";
  return names.map((n) => PERM_FR[n] ?? n).join(", ");
}

// ---------------------------------------------------------------------------
// Photographie de l'état réel (à partir du cache, après fetch)
// ---------------------------------------------------------------------------

export interface SnapRole {
  id: string;
  name: string;
  color: number;
  hoist: boolean;
  mentionable: boolean;
  managed: boolean;
  position: number;
  permissions: string; // bitfield décimal
  isEveryone: boolean;
  memberCount: number | null;
}

export interface SnapOverwrite {
  id: string;
  type: "role" | "member";
  allow: string;
  deny: string;
}

export interface SnapChannel {
  id: string;
  name: string;
  type: number;
  parentId: string | null;
  rawPosition: number;
  topic: string | null;
  overwrites: SnapOverwrite[];
}

export interface Snapshot {
  takenAt: string;
  guild: { id: string; name: string; ownerId: string; systemChannelId: string | null; rulesChannelId: string | null; publicUpdatesChannelId: string | null };
  bot: { id: string; tag: string; highestRoleId: string; highestPosition: number; permissions: string; isAdministrator: boolean; managedRoleId: string | null };
  roles: SnapRole[];
  channels: SnapChannel[];
  /** Pseudos des membres par ID de rôle (null si la liste des membres n'a pas pu être lue). */
  membersByRole: Record<string, string[]> | null;
}

/**
 * Liste complète des membres, en patientant si Discord limite la requête (opcode 8 limité à une fois toutes
 * les ~30 s : plan:guild suivi aussitôt d'apply:guild la déclenche).
 */
export async function fetchMembersRetry(guild: Guild): Promise<Awaited<ReturnType<Guild["members"]["fetch"]>>> {
  for (let essai = 0; ; essai++) {
    try {
      return await guild.members.fetch();
    } catch (err) {
      const m = /Retry after ([\d.]+) seconds/.exec((err as Error).message);
      if (!m || essai >= 2) throw err;
      const ms = Math.ceil(Number(m[1]) * 1000) + 1500;
      console.warn(`[membres] requête limitée par Discord — nouvel essai dans ${Math.round(ms / 1000)} s`);
      await new Promise((r) => setTimeout(r, ms));
    }
  }
}

export async function takeSnapshot(client: Client<true>, guild: Guild, withMembers: boolean): Promise<Snapshot> {
  const me = await guild.members.fetchMe();
  await guild.roles.fetch();
  await guild.channels.fetch();
  let membersByRole: Record<string, string[]> | null = null;
  if (withMembers) {
    try {
      const members = await fetchMembersRetry(guild);
      membersByRole = {};
      for (const m of members.values()) {
        for (const rid of m.roles.cache.keys()) {
          if (rid === guild.id) continue;
          (membersByRole[rid] ??= []).push(memberLabel(m));
        }
      }
      for (const list of Object.values(membersByRole)) list.sort((a, b) => a.localeCompare(b));
    } catch (err) {
      console.warn(`[plan] liste des membres illisible (intent « Server Members » ?) : ${(err as Error).message}`);
      membersByRole = null;
    }
  }
  const roles: SnapRole[] = [...guild.roles.cache.values()]
    .sort((a, b) => b.position - a.position)
    .map((r) => ({
      id: r.id,
      name: r.name,
      color: r.color,
      hoist: r.hoist,
      mentionable: r.mentionable,
      managed: r.managed,
      position: r.position,
      permissions: r.permissions.bitfield.toString(),
      isEveryone: r.id === guild.id,
      memberCount: membersByRole ? (r.id === guild.id ? null : (membersByRole[r.id]?.length ?? 0)) : null,
    }));
  const channels: SnapChannel[] = [...guild.channels.cache.values()]
    .filter((c): c is NonThreadGuildBasedChannel => !c.isThread())
    .map((c) => ({
      id: c.id,
      name: c.name,
      type: c.type,
      parentId: c.parentId,
      rawPosition: c.rawPosition,
      topic: "topic" in c && typeof c.topic === "string" ? c.topic : null,
      overwrites: [...c.permissionOverwrites.cache.values()].map((o) => ({
        id: o.id,
        type: o.type === OverwriteType.Role ? ("role" as const) : ("member" as const),
        allow: o.allow.bitfield.toString(),
        deny: o.deny.bitfield.toString(),
      })).sort((x, y) => x.id.localeCompare(y.id)),
    }))
    .sort((a, b) => a.id.localeCompare(b.id));
  const managedRole = me.roles.botRole;
  return {
    takenAt: new Date().toISOString(),
    guild: {
      id: guild.id,
      name: guild.name,
      ownerId: guild.ownerId,
      systemChannelId: guild.systemChannelId,
      rulesChannelId: guild.rulesChannelId,
      publicUpdatesChannelId: guild.publicUpdatesChannelId,
    },
    bot: {
      id: me.id,
      tag: client.user.tag,
      highestRoleId: me.roles.highest.id,
      highestPosition: me.roles.highest.position,
      permissions: me.permissions.bitfield.toString(),
      isAdministrator: me.permissions.has(PermissionFlagsBits.Administrator),
      managedRoleId: managedRole?.id ?? null,
    },
    roles,
    channels,
    membersByRole,
  };
}

function memberLabel(m: GuildMember): string {
  return m.displayName === m.user.username ? m.user.username : `${m.displayName} (${m.user.username})`;
}

// ---------------------------------------------------------------------------
// Correspondance plan <-> serveur
// ---------------------------------------------------------------------------

const TYPE_CODE: Record<PlanChannelType, number> = {
  text: ChannelType.GuildText,
  voice: ChannelType.GuildVoice,
  announcement: ChannelType.GuildAnnouncement,
  forum: ChannelType.GuildForum,
  stage: ChannelType.GuildStageVoice,
};

export function channelTypeCode(t: PlanChannelType): number {
  return TYPE_CODE[t];
}

const TYPE_FR: Record<number, string> = {
  [ChannelType.GuildCategory]: "catégorie",
  [ChannelType.GuildText]: "texte",
  [ChannelType.GuildAnnouncement]: "annonces",
  [ChannelType.GuildForum]: "forum",
  [ChannelType.GuildMedia]: "média",
  [ChannelType.GuildVoice]: "vocal",
  [ChannelType.GuildStageVoice]: "conférence",
};
export function typeFr(code: number): string {
  return TYPE_FR[code] ?? String(code);
}

function isVoiceLike(code: number): boolean {
  return code === ChannelType.GuildVoice || code === ChannelType.GuildStageVoice;
}

export interface Matching {
  /** clé de rôle du plan -> rôle réel (ou absent = à créer). "everyone" -> @everyone. */
  roles: Map<string, SnapRole>;
  /** clé de catégorie / salon du plan -> salon réel. */
  channels: Map<string, SnapChannel>;
  /** Rôles réels hors plan (hors @everyone). */
  extraRoles: SnapRole[];
  /** Salons réels hors plan. */
  extraChannels: SnapChannel[];
  /** Origines du plan introuvables sur le serveur (supprimées depuis l'audit ?). */
  missingOrigins: string[];
}

export function matchPlan(plan: Plan, snap: Snapshot): Matching {
  const roles = new Map<string, SnapRole>();
  const used = new Set<string>();
  const missingOrigins: string[] = [];
  const everyone = snap.roles.find((r) => r.isEveryone);
  if (everyone) {
    roles.set("everyone", everyone);
    used.add(everyone.id);
  }
  // 1) par ID d'origine, 2) par nom exact (rôle déjà créé par un lot précédent).
  for (const r of plan.roles) {
    if (r.origin) {
      const found = snap.roles.find((s) => s.id === r.origin!.id);
      if (found) {
        roles.set(r.key, found);
        used.add(found.id);
      } else missingOrigins.push(`rôle « ${r.origin.name} » (${r.key})`);
    }
  }
  for (const r of plan.roles) {
    if (roles.has(r.key)) continue;
    const found = snap.roles.find((s) => !used.has(s.id) && s.name === r.name);
    if (found) {
      roles.set(r.key, found);
      used.add(found.id);
    }
  }
  const extraRoles = snap.roles.filter((s) => !used.has(s.id));

  const channels = new Map<string, SnapChannel>();
  const usedCh = new Set<string>();
  const all: { key: string; name: string; origin: PlanOrigin | null; type: number }[] = [];
  for (const c of plan.categories) {
    all.push({ key: c.key, name: c.name, origin: c.origin, type: ChannelType.GuildCategory });
    for (const ch of c.channels) all.push({ key: ch.key, name: ch.name, origin: ch.origin, type: TYPE_CODE[ch.type] });
  }
  for (const p of all) {
    if (!p.origin) continue;
    const found = snap.channels.find((s) => s.id === p.origin!.id);
    if (found) {
      channels.set(p.key, found);
      usedCh.add(found.id);
    } else missingOrigins.push(`salon « ${p.origin.name} » (${p.key})`);
  }
  for (const p of all) {
    if (channels.has(p.key)) continue;
    const found = snap.channels.find((s) => !usedCh.has(s.id) && s.name === p.name && s.type === p.type);
    if (found) {
      channels.set(p.key, found);
      usedCh.add(found.id);
    }
  }
  const extraChannels = snap.channels.filter((s) => !usedCh.has(s.id));
  return { roles, channels, extraRoles, extraChannels, missingOrigins };
}

// ---------------------------------------------------------------------------
// Diff
// ---------------------------------------------------------------------------

export const LOTS = ["roles", "roles-maj", "roles-ordre", "permissions", "salons", "overwrites", "salons-ordre"] as const;
export type Lot = (typeof LOTS)[number];

export const LOT_FR: Record<Lot, string> = {
  roles: "(a) Création des rôles et séparateurs",
  "roles-maj": "(b) Renommages, couleurs, séparé, mentionnable",
  "roles-ordre": "(c) Positions des rôles",
  permissions: "(d) Permissions des rôles",
  salons: "(e) Catégories et salons : création, renommage, déplacement",
  overwrites: "(f) Permissions des catégories et salons (overwrites)",
  "salons-ordre": "(g) Positions des catégories et salons",
};

export interface OwSpec {
  /** Clé du plan ("everyone" compris) ou, pour un état actuel hors plan, "id:<snowflake>". */
  target: string;
  label: string;
  allow: string[];
  deny: string[];
}

export type Op =
  | { lot: "roles"; kind: "role.create"; key: string; id: null; label: string; after: { name: string; color: number; hoist: boolean; mentionable: boolean } }
  | { lot: "roles-maj"; kind: "role.update"; key: string; id: string; label: string; before: Record<string, unknown>; after: Record<string, unknown> }
  | { lot: "roles-ordre"; kind: "role.order"; key: "*"; id: null; label: string; before: string[]; after: string[]; moved: string[] }
  | { lot: "permissions"; kind: "role.permissions"; key: string; id: string | null; label: string; before: string[]; after: string[]; added: string[]; removed: string[]; adminRemoval: boolean }
  | { lot: "salons"; kind: "channel.create"; key: string; id: null; label: string; after: { name: string; type: number; parent: string | null; topic: string | null } }
  | { lot: "salons"; kind: "channel.update"; key: string; id: string; label: string; before: Record<string, unknown>; after: Record<string, unknown> }
  | { lot: "overwrites"; kind: "channel.overwrites"; key: string; id: string | null; label: string; sync: boolean; before: OwSpec[]; after: OwSpec[] }
  | { lot: "salons-ordre"; kind: "channel.order"; key: "*"; id: null; label: string; before: string[]; after: string[]; moved: string[] };

export interface ManualAction {
  what: string;
  why: string;
}

export interface Diff {
  planTitle: string;
  guild: { id: string; name: string };
  ops: Op[];
  manual: ManualAction[];
  extras: { roles: { id: string; name: string; members: number | null }[]; channels: { id: string; name: string; type: string; parent: string | null }[] };
  warnings: string[];
  /** Options de calcul (elles changent les opérations, donc l'empreinte). */
  options: { keepAdmin: boolean };
}

export interface DiffOptions {
  /** true = Administrateur gardé sur les rôles « adminRemoval » (tout sauf « --lot permissions » explicite). */
  keepAdmin: boolean;
}

function colorInt(c: string | null): number {
  return c === null ? 0 : parseInt(c.slice(1), 16);
}
export function colorHex(n: number): string {
  return n === 0 ? "aucune" : `#${n.toString(16).padStart(6, "0").toUpperCase()}`;
}

/** Overwrites normalisés (sans les entrées vides), triés par cible. */
function normOw(list: OwSpec[]): string {
  return JSON.stringify(
    list
      .filter((o) => o.allow.length || o.deny.length)
      .map((o) => ({ t: o.target, a: [...o.allow].sort(), d: [...o.deny].sort() }))
      .sort((x, y) => x.t.localeCompare(y.t)),
  );
}

export function computeDiff(plan: Plan, snap: Snapshot, opts: DiffOptions): Diff {
  const m = matchPlan(plan, snap);
  const ops: Op[] = [];
  const manual: ManualAction[] = [];
  const warnings: string[] = [];
  const roleByKey = new Map(plan.roles.map((r) => [r.key, r]));
  const roleKeyById = new Map<string, string>();
  for (const [k, r] of m.roles) roleKeyById.set(r.id, k);
  const roleName = (key: string) => (key === "everyone" ? "@everyone" : (roleByKey.get(key)?.name ?? key));

  if (snap.guild.id !== plan.guild.id) warnings.push(`Le plan vise ${plan.guild.name} (${plan.guild.id}), le serveur lu est ${snap.guild.name} (${snap.guild.id}).`);
  for (const o of m.missingOrigins) warnings.push(`Origine introuvable sur le serveur (supprimée depuis l'audit ?) : ${o} — sera recréé.`);

  // ------------------------------------------------------------ (a) + (b) rôles
  for (const r of plan.roles) {
    const cur = m.roles.get(r.key);
    if (r.manual) {
      if (!cur) {
        warnings.push(`Rôle « ${r.name} » (${r.key}) introuvable.`);
        continue;
      }
      if (cur.name !== r.name) manual.push({ what: `Renommer le rôle « ${cur.name} » en « ${r.name} »`, why: "le bot ne peut pas modifier son propre rôle" });
      if (r.hoist !== cur.hoist) manual.push({ what: `Rôle « ${r.name} » : ${r.hoist ? "cocher" : "décocher"} « Afficher séparément »`, why: "rôle du bot" });
      if (r.note) manual.push({ what: `Rôle « ${r.name} » : ${r.note}`, why: "rôle du bot" });
      continue;
    }
    if (!cur) {
      ops.push({
        lot: "roles",
        kind: "role.create",
        key: r.key,
        id: null,
        label: r.name,
        after: { name: r.name, color: r.color === "keep" ? 0 : colorInt(r.color), hoist: r.hoist, mentionable: r.mentionable },
      });
      continue;
    }
    if (r.managed) continue;
    const before: Record<string, unknown> = {};
    const after: Record<string, unknown> = {};
    if (cur.name !== r.name) {
      before.name = cur.name;
      after.name = r.name;
    }
    if (r.color !== "keep" && cur.color !== colorInt(r.color)) {
      before.color = cur.color;
      after.color = colorInt(r.color);
    }
    if (cur.hoist !== r.hoist) {
      before.hoist = cur.hoist;
      after.hoist = r.hoist;
    }
    if (cur.mentionable !== r.mentionable) {
      before.mentionable = cur.mentionable;
      after.mentionable = r.mentionable;
    }
    if (Object.keys(after).length) ops.push({ lot: "roles-maj", kind: "role.update", key: r.key, id: cur.id, label: r.name, before, after });
  }

  // ------------------------------------------------------------ (c) ordre des rôles
  {
    const target: string[] = plan.roles.map((r) => r.key);
    // Rôles hors plan : gardés, rangés tout en bas (juste au-dessus de @everyone), dans leur ordre actuel.
    for (const x of m.extraRoles) if (!x.isEveryone) target.push(`id:${x.id}`);
    const current: string[] = snap.roles
      .filter((r) => !r.isEveryone)
      .map((r) => roleKeyById.get(r.id) ?? `id:${r.id}`);
    const allExist = target.every((k) => k.startsWith("id:") || m.roles.has(k));
    const same = allExist && target.length === current.length && target.every((k, i) => k === current[i]);
    if (!same) {
      const curIdx = new Map(current.map((k, i) => [k, i]));
      const moved = target.filter((k, i) => curIdx.get(k) !== i);
      ops.push({ lot: "roles-ordre", kind: "role.order", key: "*", id: null, label: "Ordre des rôles", before: current, after: target, moved });
    }
    const botKey = roleKeyById.get(snap.bot.highestRoleId);
    if (current[0] !== "bots" || botKey !== "bots") {
      manual.push({
        what: "Monter le rôle du bot TOUT EN HAUT de la liste des rôles (Paramètres du serveur → Rôles → glisser)",
        why: "le bot ne peut ni modifier ni déplacer un rôle placé au-dessus du sien ; aujourd'hui il est en bas",
      });
    }
  }

  // ------------------------------------------------------------ (d) permissions des rôles
  const permOps: Op[] = [];
  for (const r of plan.roles) {
    if (r.permissions === "keep" || r.manual || r.managed) continue;
    const cur = m.roles.get(r.key);
    let want = bitsOf(r.permissions);
    const curBits = cur ? BigInt(cur.permissions) : 0n;
    const curHasAdmin = (curBits & PermissionFlagsBits.Administrator) !== 0n;
    const adminRemoval = !!r.adminRemoval && curHasAdmin && (want & PermissionFlagsBits.Administrator) === 0n;
    if (adminRemoval && opts.keepAdmin) want |= PermissionFlagsBits.Administrator;
    if (cur && curBits === want) continue;
    const before = namesOf(curBits);
    const after = namesOf(want);
    permOps.push({
      lot: "permissions",
      kind: "role.permissions",
      key: r.key,
      id: cur?.id ?? null,
      label: r.name,
      before,
      after,
      added: after.filter((n) => !before.includes(n)),
      removed: before.filter((n) => !after.includes(n)),
      adminRemoval: adminRemoval && !opts.keepAdmin,
    });
  }
  {
    const cur = m.roles.get("everyone");
    const want = bitsOf(plan.everyone.permissions);
    if (cur && BigInt(cur.permissions) !== want) {
      const before = namesOf(BigInt(cur.permissions));
      const after = namesOf(want);
      permOps.push({
        lot: "permissions",
        kind: "role.permissions",
        key: "everyone",
        id: cur.id,
        label: "@everyone",
        before,
        after,
        added: after.filter((n) => !before.includes(n)),
        removed: before.filter((n) => !after.includes(n)),
        adminRemoval: false,
      });
    }
  }
  // Ordre d'exécution : ceux qui DONNENT Administrateur, puis les autres, ceux qui le RETIRENT, @everyone en dernier.
  const rank = (o: Op) => {
    if (o.kind !== "role.permissions") return 9;
    if (o.key === "everyone") return 4;
    if (o.added.includes("Administrator")) return 0;
    if (o.adminRemoval) return 2;
    return 1;
  };
  permOps.sort((a, b) => rank(a) - rank(b));
  ops.push(...permOps);
  if (opts.keepAdmin) {
    const kept = plan.roles.filter((r) => {
      const cur = m.roles.get(r.key);
      return r.adminRemoval && cur && (BigInt(cur.permissions) & PermissionFlagsBits.Administrator) !== 0n;
    });
    if (kept.length) {
      warnings.push(
        `Administrateur GARDÉ dans ce calcul sur : ${kept.map((r) => `« ${m.roles.get(r.key)!.name} »`).join(", ")}. ` +
          "Il n'est retiré que par « --lot permissions » explicite, une fois « | Fondateur » attribué (voir le diff de ce lot).",
      );
    }
  }

  // ------------------------------------------------------------ (e) catégories et salons
  const parentKeyOf = new Map<string, string | null>(); // clé salon -> clé catégorie
  for (const c of plan.categories) {
    parentKeyOf.set(c.key, null);
    for (const ch of c.channels) parentKeyOf.set(ch.key, c.key);
  }
  const chanByKey = new Map<string, { name: string; type: number; topic: string | null }>();
  for (const c of plan.categories) {
    chanByKey.set(c.key, { name: c.name, type: ChannelType.GuildCategory, topic: null });
    for (const ch of c.channels) chanByKey.set(ch.key, { name: ch.name, type: TYPE_CODE[ch.type], topic: ch.topic ?? null });
  }
  const snapChanById = new Map(snap.channels.map((c) => [c.id, c]));
  const chanKeyById = new Map<string, string>();
  for (const [k, c] of m.channels) chanKeyById.set(c.id, k);
  const chanLabel = (key: string) => chanByKey.get(key)?.name ?? key;
  const curParentLabel = (id: string | null) => (id ? (snapChanById.get(id)?.name ?? id) : "(hors catégorie)");

  const salonOps: Op[] = [];
  const orderedKeys: string[] = [];
  for (const c of plan.categories) {
    orderedKeys.push(c.key);
    for (const ch of c.channels) orderedKeys.push(ch.key);
  }
  // Catégories d'abord, puis salons (la catégorie doit exister avant d'y créer / déplacer un salon).
  const catKeys = plan.categories.map((c) => c.key);
  const childKeys = orderedKeys.filter((k) => !catKeys.includes(k));
  for (const key of [...catKeys, ...childKeys]) {
    const want = chanByKey.get(key)!;
    const parentKey = parentKeyOf.get(key) ?? null;
    const cur = m.channels.get(key);
    if (!cur) {
      salonOps.push({ lot: "salons", kind: "channel.create", key, id: null, label: want.name, after: { name: want.name, type: want.type, parent: parentKey, topic: want.topic } });
      continue;
    }
    if (cur.type !== want.type) warnings.push(`Salon « ${cur.name} » : type ${typeFr(cur.type)} ≠ ${typeFr(want.type)} dans le plan (Discord ne convertit pas : à faire à la main ou à corriger dans le plan).`);
    const before: Record<string, unknown> = {};
    const after: Record<string, unknown> = {};
    if (cur.name !== want.name) {
      before.name = cur.name;
      after.name = want.name;
    }
    const curParentKey = cur.parentId ? (chanKeyById.get(cur.parentId) ?? `id:${cur.parentId}`) : null;
    if (curParentKey !== parentKey) {
      before.parent = curParentLabel(cur.parentId);
      after.parent = parentKey;
    }
    if (want.topic !== null && cur.topic !== want.topic) {
      before.topic = cur.topic;
      after.topic = want.topic;
    }
    if (Object.keys(after).length) salonOps.push({ lot: "salons", kind: "channel.update", key, id: cur.id, label: want.name, before, after });
  }
  ops.push(...salonOps);

  // ------------------------------------------------------------ (f) overwrites
  const desiredOw = (list: PlanOverwrite[]): OwSpec[] =>
    list
      .map((o) => ({ target: o.role, label: roleName(o.role), allow: [...o.allow].sort(), deny: [...o.deny].sort() }))
      .sort((x, y) => x.target.localeCompare(y.target));
  const currentOw = (ch: SnapChannel): OwSpec[] =>
    ch.overwrites.map((o) => {
      const key = o.type === "role" ? (roleKeyById.get(o.id) ?? `id:${o.id}`) : `membre:${o.id}`;
      const name = o.type === "role" ? (snap.roles.find((r) => r.id === o.id)?.name ?? o.id) : `membre ${o.id}`;
      return {
        target: key,
        label: key === "everyone" ? "@everyone" : name,
        allow: namesOf(BigInt(o.allow)).sort(),
        deny: namesOf(BigInt(o.deny)).sort(),
      };
    }).sort((x, y) => x.target.localeCompare(y.target));
  const owOps: Op[] = [];
  for (const c of plan.categories) {
    const catWant = desiredOw(c.overwrites);
    const catCur = m.channels.get(c.key);
    const catBefore = catCur ? currentOw(catCur) : [];
    if (!catCur || normOw(catBefore) !== normOw(catWant)) {
      owOps.push({ lot: "overwrites", kind: "channel.overwrites", key: c.key, id: catCur?.id ?? null, label: c.name, sync: false, before: catBefore, after: catWant });
    }
  }
  for (const c of plan.categories) {
    const catWant = desiredOw(c.overwrites);
    for (const ch of c.channels) {
      const want = ch.sync ? catWant : desiredOw(ch.overwrites ?? []);
      const cur = m.channels.get(ch.key);
      const before = cur ? currentOw(cur) : [];
      if (!cur || normOw(before) !== normOw(want)) {
        owOps.push({ lot: "overwrites", kind: "channel.overwrites", key: ch.key, id: cur?.id ?? null, label: ch.name, sync: ch.sync, before, after: want });
      }
    }
  }
  ops.push(...owOps);

  // ------------------------------------------------------------ (g) ordre des salons
  {
    const target: string[] = [];
    for (const c of plan.categories) {
      target.push(c.key);
      const texts = c.channels.filter((ch) => !isVoiceLike(TYPE_CODE[ch.type])).map((ch) => ch.key);
      const voices = c.channels.filter((ch) => isVoiceLike(TYPE_CODE[ch.type])).map((ch) => ch.key);
      target.push(...texts, ...voices);
    }
    for (const x of m.extraChannels) target.push(`id:${x.id}`);
    // Ordre actuel, tel que Discord l'affiche : hors catégorie, puis catégories et leurs salons.
    const sortSiblings = (list: SnapChannel[]) =>
      [...list].sort((a, b) => Number(isVoiceLike(a.type)) - Number(isVoiceLike(b.type)) || a.rawPosition - b.rawPosition || a.id.localeCompare(b.id));
    const cats = [...snap.channels.filter((c) => c.type === ChannelType.GuildCategory)].sort((a, b) => a.rawPosition - b.rawPosition || a.id.localeCompare(b.id));
    const current: string[] = [];
    const keyOf = (c: SnapChannel) => chanKeyById.get(c.id) ?? `id:${c.id}`;
    for (const c of sortSiblings(snap.channels.filter((c) => c.type !== ChannelType.GuildCategory && !c.parentId))) current.push(keyOf(c));
    for (const cat of cats) {
      current.push(keyOf(cat));
      for (const c of sortSiblings(snap.channels.filter((x) => x.parentId === cat.id))) current.push(keyOf(c));
    }
    const allExist = target.every((k) => k.startsWith("id:") || m.channels.has(k));
    const same = allExist && target.length === current.length && target.every((k, i) => k === current[i]);
    if (!same) {
      const curIdx = new Map(current.map((k, i) => [k, i]));
      const moved = target.filter((k, i) => curIdx.get(k) !== i);
      ops.push({ lot: "salons-ordre", kind: "channel.order", key: "*", id: null, label: "Ordre des catégories et salons", before: current, after: target, moved });
    }
  }

  // ------------------------------------------------------------ en trop
  const extras = {
    roles: m.extraRoles
      .filter((r) => !r.isEveryone)
      .map((r) => ({ id: r.id, name: r.name, members: r.memberCount })),
    channels: m.extraChannels.map((c) => ({ id: c.id, name: c.name, type: typeFr(c.type), parent: c.parentId ? curParentLabel(c.parentId) : null })),
  };
  return { planTitle: plan.title, guild: { id: snap.guild.id, name: snap.guild.name }, ops, manual, extras, warnings, options: { keepAdmin: opts.keepAdmin } };
}

// ---------------------------------------------------------------------------
// Empreinte et signature de lot
// ---------------------------------------------------------------------------

export function canonicalStringify(v: unknown): string {
  if (v === null || typeof v !== "object") return JSON.stringify(v);
  if (Array.isArray(v)) return `[${v.map(canonicalStringify).join(",")}]`;
  const o = v as Record<string, unknown>;
  return `{${Object.keys(o)
    .filter((k) => o[k] !== undefined)
    .sort()
    .map((k) => `${JSON.stringify(k)}:${canonicalStringify(o[k])}`)
    .join(",")}}`;
}

/** Empreinte du diff complet (avant / après, IDs, options) : change dès que le serveur ou le plan change. */
export function fingerprint(diff: Diff): string {
  return createHash("sha256")
    .update(canonicalStringify({ guild: diff.guild.id, ops: diff.ops, extras: diff.extras, options: diff.options }))
    .digest("hex");
}

/**
 * Signature d'un lot : l'INTENTION seule (clés + état visé), sans les IDs ni l'état « avant ».
 * Sert, pendant une application en plusieurs lots, à vérifier que le lot recalculé après les lots
 * précédents est bien celui qui a été validé.
 */
export function lotSignature(diff: Diff, lot: Lot): string {
  const intent = diff.ops
    .filter((o) => o.lot === lot)
    .map((o) => {
      switch (o.kind) {
        case "role.order":
        case "channel.order":
          return { k: o.kind, after: o.after.filter((x) => !x.startsWith("id:")) };
        case "role.permissions":
          return { k: o.kind, key: o.key, after: o.after, adminRemoval: o.adminRemoval };
        case "channel.overwrites":
          return { k: o.kind, key: o.key, sync: o.sync, after: o.after.map((x) => ({ t: x.target, a: x.allow, d: x.deny })) };
        default:
          return { k: o.kind, key: o.key, after: o.after };
      }
    });
  return createHash("sha256").update(canonicalStringify(intent)).digest("hex");
}

// ---------------------------------------------------------------------------
// Rendu Markdown
// ---------------------------------------------------------------------------

function md(s: string | null | undefined): string {
  return (s ?? "").replace(/\|/g, "\\|").replace(/\r?\n/g, " ");
}

// ---------------------------------------------------------------------------
// Simulation des accès par membre (avant / après le plan complet)
// ---------------------------------------------------------------------------

export interface AccessChange {
  member: string;
  roles: string[];
  lost: string[];
  gained: string[];
  newVisible: number;
  /** Salons visibles avant → après (salons du plan existant aujourd'hui + nouveaux). */
  countBefore: number;
  countAfter: number;
}

/**
 * Calcule, pour chaque membre, les salons du plan qu'il voit aujourd'hui et ceux qu'il verra une fois
 * TOUS les lots appliqués (sans attribution de rôle). Algorithme Discord : permissions de @everyone +
 * des rôles, Administrateur = tout, puis overwrite @everyone (refus puis autorisation), puis overwrites
 * des rôles du membre (refus cumulés puis autorisations cumulées).
 */
export function simulateAccess(plan: Plan, snap: Snapshot, opts: DiffOptions): AccessChange[] | null {
  if (!snap.membersByRole) return null;
  const m = matchPlan(plan, snap);
  const everyone = m.roles.get("everyone");
  if (!everyone) return null;
  const ADMIN = PermissionFlagsBits.Administrator;
  const VIEW = PermissionFlagsBits.ViewChannel;
  const memberRoles = new Map<string, string[]>();
  for (const [rid, names] of Object.entries(snap.membersByRole)) {
    for (const n of names) {
      const list = memberRoles.get(n) ?? [];
      list.push(rid);
      memberRoles.set(n, list);
    }
  }
  const permsBefore = new Map(snap.roles.map((r) => [r.id, BigInt(r.permissions)]));
  const permsAfter = new Map(permsBefore);
  for (const r of plan.roles) {
    const cur = m.roles.get(r.key);
    if (!cur || r.permissions === "keep" || r.manual || r.managed) continue;
    let b = bitsOf(r.permissions);
    if (r.adminRemoval && opts.keepAdmin && (BigInt(cur.permissions) & ADMIN) !== 0n) b |= ADMIN;
    permsAfter.set(cur.id, b);
  }
  permsAfter.set(everyone.id, bitsOf(plan.everyone.permissions));
  type Ow = { id: string; allow: bigint; deny: bigint };
  const resolveOw = (list: PlanOverwrite[]): Ow[] =>
    list.flatMap((o) => {
      const r = m.roles.get(o.role);
      return r ? [{ id: r.id, allow: bitsOf(o.allow), deny: bitsOf(o.deny) }] : [];
    });
  const chans: { name: string; before: Ow[] | null; after: Ow[] }[] = [];
  for (const c of plan.categories) {
    const catOw = resolveOw(c.overwrites);
    for (const ch of c.channels) {
      const cur = m.channels.get(ch.key);
      chans.push({
        name: ch.name,
        before: cur ? cur.overwrites.filter((o) => o.type === "role").map((o) => ({ id: o.id, allow: BigInt(o.allow), deny: BigInt(o.deny) })) : null,
        after: ch.sync ? catOw : resolveOw(ch.overwrites ?? []),
      });
    }
  }
  const canView = (roleIds: string[], perms: Map<string, bigint>, ows: Ow[]): boolean => {
    let p = perms.get(everyone.id) ?? 0n;
    for (const id of roleIds) p |= perms.get(id) ?? 0n;
    if ((p & ADMIN) !== 0n) return true;
    const e = ows.find((o) => o.id === everyone.id);
    if (e) p = (p & ~e.deny) | e.allow;
    let a = 0n;
    let d = 0n;
    for (const o of ows) {
      if (o.id !== everyone.id && roleIds.includes(o.id)) {
        a |= o.allow;
        d |= o.deny;
      }
    }
    p = (p & ~d) | a;
    return (p & VIEW) !== 0n;
  };
  const roleName = new Map(snap.roles.map((r) => [r.id, r.name]));
  const out: AccessChange[] = [];
  for (const [member, roleIds] of [...memberRoles.entries()].sort((x, y) => x[0].localeCompare(y[0]))) {
    const lost: string[] = [];
    const gained: string[] = [];
    let newVisible = 0;
    let countBefore = 0;
    let countAfter = 0;
    for (const c of chans) {
      const b = c.before ? canView(roleIds, permsBefore, c.before) : false;
      const a = canView(roleIds, permsAfter, c.after);
      if (b) countBefore++;
      if (a) countAfter++;
      if (c.before === null) {
        if (a) newVisible++;
      } else if (b && !a) lost.push(c.name);
      else if (!b && a) gained.push(c.name);
    }
    out.push({ member, roles: roleIds.map((id) => roleName.get(id) ?? id), lost, gained, newVisible, countBefore, countAfter });
  }
  return out;
}

export function renderDiffMarkdown(diff: Diff, plan: Plan, snap: Snapshot, fp: string, planPath: string): string {
  const L: string[] = [];
  const m = matchPlan(plan, snap);
  const roleByKey = new Map(plan.roles.map((r) => [r.key, r]));
  const chanName = new Map<string, string>();
  for (const c of plan.categories) {
    chanName.set(c.key, c.name);
    for (const ch of c.channels) chanName.set(ch.key, ch.name);
  }
  const roleKeyLabel = (k: string) => {
    if (k === "everyone") return "@everyone";
    if (k.startsWith("id:")) return `${snap.roles.find((r) => r.id === k.slice(3))?.name ?? k} *(hors plan)*`;
    return roleByKey.get(k)?.name ?? k;
  };
  const roleKeyLabelBefore = (k: string) => {
    if (k.startsWith("id:")) return snap.roles.find((r) => r.id === k.slice(3))?.name ?? k;
    const cur = m.roles.get(k);
    return cur?.name ?? roleByKey.get(k)?.name ?? k;
  };
  const chanKeyLabel = (k: string) => {
    if (k.startsWith("id:")) return `${snap.channels.find((c) => c.id === k.slice(3))?.name ?? k} *(hors plan)*`;
    return chanName.get(k) ?? k;
  };
  const chanKeyLabelBefore = (k: string) => {
    if (k.startsWith("id:")) return snap.channels.find((c) => c.id === k.slice(3))?.name ?? k;
    return m.channels.get(k)?.name ?? chanName.get(k) ?? k;
  };
  const isCatKey = new Set(plan.categories.map((c) => c.key));
  const count = (lot: Lot) => diff.ops.filter((o) => o.lot === lot).length;
  const members = (key: string) => {
    const r = m.roles.get(key);
    return r && r.memberCount !== null ? String(r.memberCount) : "?";
  };

  L.push(`# Diff — ${md(diff.planTitle)}`);
  L.push("");
  L.push(`> Calculé le ${new Date().toLocaleString("fr-FR")} par \`apps/bot/src/scripts/plan-guild.ts\` (**lecture seule** : aucune requête autre que GET).`);
  L.push(`> Plan : \`${planPath}\`. Fichier privé (IDs, pseudos) : **jamais dans le dépôt ni sur le Trello public**.`);
  L.push("");
  L.push(`**Empreinte SHA-256 du diff** : \`${fp}\``);
  L.push("");
  L.push(
    "Elle change dès que le serveur (ou le plan) change. `apply:guild` recalcule le diff au moment de l'exécution et " +
      "refuse si l'empreinte passée par `--confirm` ne correspond plus.",
  );
  L.push("");
  L.push(`Calcul : ${diff.options.keepAdmin ? "Administrateur **gardé** sur Direction / Head of Reborn (diff par défaut, application normale)" : "Administrateur **retiré** à Direction / Head of Reborn (diff de « --lot permissions » explicite)"}.`);
  L.push("");
  L.push("## 1. Résumé chiffré");
  L.push("");
  const creates = diff.ops.filter((o) => o.kind === "role.create");
  const seps = creates.filter((o) => roleByKey.get(o.key)?.separator).length;
  const specs = creates.filter((o) => roleByKey.get(o.key)?.specialty).length;
  const updates = diff.ops.filter((o): o is Extract<Op, { kind: "role.update" }> => o.kind === "role.update");
  const renames = updates.filter((o) => "name" in o.after).length;
  const recolors = updates.filter((o) => "color" in o.after).length;
  const chCreates = diff.ops.filter((o): o is Extract<Op, { kind: "channel.create" }> => o.kind === "channel.create");
  const chUpdates = diff.ops.filter((o): o is Extract<Op, { kind: "channel.update" }> => o.kind === "channel.update");
  const roleOrder = diff.ops.find((o): o is Extract<Op, { kind: "role.order" }> => o.kind === "role.order");
  const chOrder = diff.ops.find((o): o is Extract<Op, { kind: "channel.order" }> => o.kind === "channel.order");
  L.push("| Lot | Opérations | Détail |");
  L.push("|---|---|---|");
  L.push(`| \`roles\` ${LOT_FR.roles} | ${count("roles")} | ${creates.length} rôle(s) créé(s) dont ${seps} séparateur(s) et ${specs} spécialité(s) |`);
  L.push(`| \`roles-maj\` ${LOT_FR["roles-maj"]} | ${count("roles-maj")} | ${renames} renommage(s), ${recolors} couleur(s) |`);
  L.push(`| \`roles-ordre\` ${LOT_FR["roles-ordre"]} | ${count("roles-ordre")} | ${roleOrder ? `${roleOrder.moved.length} rôle(s) changent de place` : "déjà en ordre"} |`);
  const pOps = diff.ops.filter((o): o is Extract<Op, { kind: "role.permissions" }> => o.kind === "role.permissions");
  L.push(`| \`permissions\` ${LOT_FR.permissions} | ${count("permissions")} | ${pOps.filter((o) => o.adminRemoval).length} retrait(s) d'Administrateur, @everyone ${pOps.some((o) => o.key === "everyone") ? "modifié" : "inchangé"} |`);
  L.push(`| \`salons\` ${LOT_FR.salons} | ${count("salons")} | ${chCreates.filter((o) => o.after.type === ChannelType.GuildCategory).length} catégorie(s) et ${chCreates.filter((o) => o.after.type !== ChannelType.GuildCategory).length} salon(s) créés, ${chUpdates.filter((o) => "name" in o.after).length} renommage(s), ${chUpdates.filter((o) => "parent" in o.after).length} déplacement(s) |`);
  L.push(`| \`overwrites\` ${LOT_FR.overwrites} | ${count("overwrites")} | catégories et salons dont les permissions changent |`);
  L.push(`| \`salons-ordre\` ${LOT_FR["salons-ordre"]} | ${count("salons-ordre")} | ${chOrder ? `${chOrder.moved.length} élément(s) changent de place` : "déjà en ordre"} |`);
  L.push(`| **Total** | **${diff.ops.length}** | + ${diff.manual.length} action(s) manuelle(s), ${diff.extras.roles.length + diff.extras.channels.length} élément(s) hors plan |`);
  L.push("");

  L.push("## 2. Préalables et actions manuelles (l'utilisateur, dans Discord)");
  L.push("");
  const bot = snap.bot;
  L.push(`- Bot : ${md(bot.tag)} · rôle le plus haut : « ${md(snap.roles.find((r) => r.id === bot.highestRoleId)?.name)} » (position ${bot.highestPosition} sur ${snap.roles.length - 1}) · ${bot.isAdministrator ? "**Administrateur**" : "sans Administrateur"} · Gérer les rôles : ${new PermissionsBitField(BigInt(bot.permissions)).has(PermissionFlagsBits.ManageRoles) ? "oui" : "**NON**"} · Gérer les salons : ${new PermissionsBitField(BigInt(bot.permissions)).has(PermissionFlagsBits.ManageChannels) ? "oui" : "**NON**"}`);
  for (const a of diff.manual) L.push(`- [ ] ${md(a.what)} — *${md(a.why)}*`);
  L.push("- [ ] Exiger la 2FA pour la modération (Paramètres du serveur → Sécurité) — *recommandé, hors script*");
  L.push("");
  if (diff.warnings.length) {
    L.push("### Avertissements");
    L.push("");
    for (const w of diff.warnings) L.push(`- ${md(w)}`);
    L.push("");
  }

  L.push(`## 3. Lot \`roles\` — ${LOT_FR.roles}`);
  L.push("");
  if (!creates.length) L.push("Rien à créer.");
  else {
    L.push("| Rôle créé | Couleur | Séparé | Mentionnable | Note |");
    L.push("|---|---|---|---|---|");
    for (const o of creates) {
      if (o.kind !== "role.create") continue;
      const r = roleByKey.get(o.key);
      L.push(`| ${md(o.after.name)} | ${colorHex(o.after.color)} | ${o.after.hoist ? "oui" : "—"} | ${o.after.mentionable ? "oui" : "—"} | ${r?.separator ? "séparateur" : r?.specialty ? "spécialité" : md(r?.note)} |`);
    }
    L.push("");
    L.push("Créés **sans permission** (les permissions arrivent au lot `permissions`), en bas de la liste (l'ordre arrive au lot `roles-ordre`).");
  }
  L.push("");

  L.push(`## 4. Lot \`roles-maj\` — ${LOT_FR["roles-maj"]}`);
  L.push("");
  if (!updates.length) L.push("Rien à modifier.");
  else {
    L.push("| Avant | Après | Couleur | Séparé | Mentionnable | Membres |");
    L.push("|---|---|---|---|---|---|");
    for (const o of updates) {
      const curName = m.roles.get(o.key)?.name ?? o.label;
      const col = "color" in o.after ? `${colorHex(o.before.color as number)} → ${colorHex(o.after.color as number)}` : "=";
      const ho = "hoist" in o.after ? `${o.before.hoist ? "oui" : "non"} → ${o.after.hoist ? "oui" : "non"}` : "=";
      const me = "mentionable" in o.after ? `${o.before.mentionable ? "oui" : "non"} → ${o.after.mentionable ? "oui" : "non"}` : "=";
      L.push(`| ${md(curName)} | ${"name" in o.after ? `**${md(String(o.after.name))}**` : "="} | ${col} | ${ho} | ${me} | ${members(o.key)} |`);
    }
  }
  L.push("");

  L.push(`## 5. Lot \`roles-ordre\` — ${LOT_FR["roles-ordre"]}`);
  L.push("");
  if (!roleOrder) L.push("Déjà dans l'ordre du plan.");
  else {
    L.push("| # | Actuel (haut → bas) | Cible (haut → bas) |");
    L.push("|---|---|---|");
    const n = Math.max(roleOrder.before.length, roleOrder.after.length);
    for (let i = 0; i < n; i++) {
      const b = roleOrder.before[i];
      const a = roleOrder.after[i];
      const moved = a !== undefined && roleOrder.moved.includes(a);
      L.push(`| ${i + 1} | ${b ? md(roleKeyLabelBefore(b)) : ""} | ${a ? `${moved ? "**" : ""}${md(roleKeyLabel(a))}${moved ? "**" : ""}${a !== "everyone" && !a.startsWith("id:") && !m.roles.has(a) ? " *(nouveau)*" : ""}` : ""} |`);
    }
    L.push("");
    L.push("En gras : rôles qui changent de place. Le rôle du bot doit déjà être en haut (action manuelle) ; les rôles hors plan descendent tout en bas.");
  }
  L.push("");

  L.push(`## 6. Lot \`permissions\` — ${LOT_FR.permissions}`);
  L.push("");
  if (!pOps.length) L.push("Rien à modifier.");
  else {
    L.push("| Rôle | Membres | Ajoutées | Retirées |");
    L.push("|---|---|---|---|");
    for (const o of pOps) {
      const curName = o.key === "everyone" ? "@everyone" : (m.roles.get(o.key)?.name ?? o.label);
      const name = curName === o.label ? md(o.label) : `${md(curName)} → ${md(o.label)}`;
      L.push(`| ${name}${o.adminRemoval ? " ⚠️ **retrait d'Administrateur**" : ""} | ${o.key === "everyone" ? "tous" : members(o.key)} | ${permLabels(o.added)} | ${permLabels(o.removed)} |`);
    }
    L.push("");
    L.push("Ordre d'exécution : rôles qui reçoivent Administrateur, autres rôles, retraits d'Administrateur, **@everyone en dernier** " +
      "(il perd « Voir les salons » : enchaîner avec les lots `salons` et `overwrites`, sinon les salons sans permission propre disparaissent pour le staff non-admin).");
  }
  L.push("");

  L.push(`## 7. Lot \`salons\` — ${LOT_FR.salons}`);
  L.push("");
  if (chCreates.length) {
    L.push("### Créations");
    L.push("");
    L.push("| Nom | Type | Catégorie | Sujet |");
    L.push("|---|---|---|---|");
    for (const o of chCreates) {
      L.push(`| ${md(o.after.name)} | ${typeFr(o.after.type)} | ${o.after.parent ? md(chanName.get(o.after.parent)) : "—"} | ${md(o.after.topic) || "—"} |`);
    }
    L.push("");
    L.push("Créés avec **@everyone : Voir refusé** (sûr par défaut), permissions finales au lot `overwrites`.");
    L.push("");
  }
  if (chUpdates.length) {
    L.push("### Renommages et déplacements");
    L.push("");
    L.push("| Avant | Après | Catégorie avant → après |");
    L.push("|---|---|---|");
    for (const o of chUpdates) {
      const cur = m.channels.get(o.key);
      const parent = "parent" in o.after ? `${md(String(o.before.parent))} → **${md(o.after.parent ? chanName.get(String(o.after.parent)) : "(hors catégorie)")}**` : "=";
      L.push(`| ${md(cur?.name ?? o.label)}${isCatKey.has(o.key) ? " *(catégorie)*" : ""} | ${"name" in o.after ? `**${md(String(o.after.name))}**` : "="} | ${parent} |`);
    }
    L.push("");
    L.push("Les déplacements gardent les permissions du salon (`lockPermissions: false`) : elles changent au lot `overwrites`.");
  }
  if (!chCreates.length && !chUpdates.length) L.push("Rien à modifier.");
  L.push("");

  L.push(`## 8. Lot \`overwrites\` — ${LOT_FR.overwrites}`);
  L.push("");
  const owOps = diff.ops.filter((o): o is Extract<Op, { kind: "channel.overwrites" }> => o.kind === "channel.overwrites");
  if (!owOps.length) L.push("Rien à modifier.");
  else {
    L.push("Format : `cible : +autorisé / −refusé`. Une catégorie reçoit sa liste complète (ce qui n'y est pas est retiré) ; un salon « synchronisé » recopie celle de sa catégorie.");
    L.push("");
    const fmt = (list: OwSpec[]) =>
      list.filter((o) => o.allow.length || o.deny.length).length
        ? list
            .filter((o) => o.allow.length || o.deny.length)
            .map((o) => `${md(o.label)} : ${o.allow.length ? `+${permLabels(o.allow)}` : ""}${o.allow.length && o.deny.length ? " / " : ""}${o.deny.length ? `−${permLabels(o.deny)}` : ""}`)
            .join("<br>")
        : "—";
    L.push("| Catégorie / salon | Avant | Après |");
    L.push("|---|---|---|");
    for (const o of owOps) {
      const label = isCatKey.has(o.key) ? `**${md(o.label)}** *(catégorie)*` : `${md(o.label)}${o.sync ? " *(synchronisé)*" : " *(permissions propres)*"}`;
      L.push(`| ${label} | ${o.id ? fmt(o.before) : "*(nouveau)*"} | ${o.sync ? "= catégorie" : fmt(o.after)} |`);
    }
  }
  L.push("");

  L.push(`## 9. Lot \`salons-ordre\` — ${LOT_FR["salons-ordre"]}`);
  L.push("");
  if (!chOrder) L.push("Déjà dans l'ordre du plan.");
  else {
    L.push("Arborescence cible (en gras : ce qui change de place) :");
    L.push("");
    L.push("```text");
    for (const k of chOrder.after) {
      const moved = chOrder.moved.includes(k) ? "  ← déplacé" : "";
      if (isCatKey.has(k)) L.push(`${chanKeyLabel(k)}${moved}`);
      else L.push(`   ${chanKeyLabel(k)}${moved}`);
    }
    L.push("```");
    L.push("");
    L.push("<details><summary>Arborescence actuelle</summary>");
    L.push("");
    L.push("```text");
    for (const k of chOrder.before) {
      const isCat = isCatKey.has(k) || snap.channels.find((c) => `id:${c.id}` === k)?.type === ChannelType.GuildCategory;
      L.push(`${isCat ? "" : "   "}${chanKeyLabelBefore(k)}`);
    }
    L.push("```");
    L.push("");
    L.push("</details>");
  }
  L.push("");

  L.push("## 10. Hors plan — à archiver / supprimer : **décision de l'utilisateur**");
  L.push("");
  L.push("Le script ne supprime **jamais** rien. Ces éléments restent tels quels (les rôles hors plan descendent en bas de la liste).");
  L.push("");
  if (!diff.extras.roles.length && !diff.extras.channels.length) L.push("Aucun.");
  for (const r of diff.extras.roles) L.push(`- Rôle « ${md(r.name)} » — ${r.members === null ? "membres : ?" : `${r.members} membre(s)`} — proposition : **supprimer** (vide, sans permission) — à la main, après accord`);
  for (const c of diff.extras.channels) L.push(`- Salon « ${md(c.name)} » (${c.type}${c.parent ? `, dans ${md(c.parent)}` : ""}) — proposition : déplacer dans Archives`);
  L.push("");

  if (snap.membersByRole) {
    const dir = m.roles.get("direction");
    const fond = m.roles.get("fondateur");
    L.push("## 11. Fondateurs");
    L.push("");
    L.push(`- Membres qui ont **${md(dir?.name ?? "Direction")}** aujourd'hui (candidats à « | Fondateur ») : ${dir ? (snap.membersByRole[dir.id] ?? []).map(md).join(", ") || "aucun" : "?"}`);
    L.push(`- « | Fondateur » : ${fond ? `existe, ${(snap.membersByRole[fond.id] ?? []).length} membre(s)${(snap.membersByRole[fond.id] ?? []).length ? ` (${(snap.membersByRole[fond.id] ?? []).map(md).join(", ")})` : ""}` : "pas encore créé"}`);
    L.push("- Le retrait d'Administrateur (lot `permissions` explicite) est **refusé** tant que « | Fondateur » n'a aucun membre.");
    L.push("");
  }

  const access = simulateAccess(plan, snap, diff.options);
  if (access) {
    const changed = access.filter((a) => a.lost.length || a.gained.length);
    L.push("## 12. Accès simulés par membre (tous les lots appliqués, aucun rôle attribué)");
    L.push("");
    L.push(
      `Salons du plan visibles avant → après, pour chacun des ${access.length} membres (Administrateur = tout). ` +
        `${changed.length} membre(s) perdent ou gagnent l'accès à des salons existants ; « nouveaux » = salons créés par le plan qu'il verra.`,
    );
    L.push("");
    L.push("| Membre | Rôles actuels | Salons vus | Perd | Gagne | Nouveaux |");
    L.push("|---|---|---|---|---|---|");
    for (const a of access) {
      L.push(
        `| ${md(a.member)} | ${a.roles.map(md).join(", ")} | ${a.countBefore} → ${a.countAfter} | ${a.lost.length ? a.lost.map(md).join(", ") : "—"} | ${a.gained.length ? a.gained.map(md).join(", ") : "—"} | ${a.newVisible} |`,
      );
    }
    L.push("");
  }

  L.push("## 13. Commandes");
  L.push("");
  L.push("Depuis la racine du monorepo. **Chaque lot change le serveur, donc l'empreinte** : relancer `plan:guild` avant chaque lot et reprendre l'empreinte affichée (E1, E2…).");
  L.push("`--lot permissions` passé explicitement **retire** Administrateur à Direction / Head of Reborn : son empreinte vient de `plan:guild --sans-admin`.");
  L.push("");
  L.push("```pwsh");
  L.push("# 0. Discord, à la main : monter le rôle du bot tout en haut de la liste des rôles");
  const steps: [string, string][] = [
    ["", "--lot roles"],
    ["", "--lot roles-maj"],
    ["", "--lot roles-ordre"],
    ["", "--attribuer-fondateurs        # ou à la main"],
    [" --sans-admin", "--lot permissions            # retire Administrateur à Direction / Head of Reborn"],
    ["", "--lot salons                  # enchaîner avec overwrites"],
    ["", "--lot overwrites"],
    ["", "--lot salons-ordre"],
  ];
  steps.forEach(([planArg, applyArg], i) => {
    L.push(`pnpm --filter @reborn/bot plan:guild${planArg}`.padEnd(64) + `# → E${i + 1}`);
    L.push(`pnpm --filter @reborn/bot apply:guild --confirm E${i + 1} ${applyArg}`);
  });
  L.push("pnpm --filter @reborn/bot plan:guild".padEnd(64) + "# attendu : diff vide (hors « nouveau rôle »)");
  L.push("```");
  L.push("");
  return L.join("\n");
}

// ---------------------------------------------------------------------------
// Garde-fou REST
// ---------------------------------------------------------------------------

export interface RestGuard {
  /** Ouvre une fenêtre d'écriture (apply uniquement). */
  allowWrites(on: boolean): void;
  /** Journal des requêtes d'écriture parties vers Discord. */
  writes: string[];
}

/**
 * Intercepte TOUTES les requêtes REST du client. Hors fenêtre d'écriture explicitement ouverte,
 * toute méthode autre que GET est rejetée AVANT l'envoi.
 */
export function installRestGuard(client: Client, tag: string): RestGuard {
  const rest = client.rest;
  const original = rest.queueRequest.bind(rest);
  let open = false;
  const writes: string[] = [];
  rest.queueRequest = (request) => {
    const method = String(request.method).toUpperCase();
    if (method !== "GET") {
      if (!open) {
        const msg = `[${tag}] REFUS (lecture seule) : ${method} ${String(request.fullRoute)}`;
        console.error(msg);
        return Promise.reject(new Error(msg));
      }
      writes.push(`${new Date().toISOString()} ${method} ${String(request.fullRoute)}`);
    }
    return original(request);
  };
  return {
    allowWrites(on: boolean) {
      open = on;
    },
    writes,
  };
}
