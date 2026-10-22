/**
 * ============================================================================
 *  APPLICATION D'UN PLAN DISCORD — PAR LOTS, SUR EMPREINTE
 * ============================================================================
 *
 *  ⚠️ Ce script MODIFIE le serveur. Il ne s'exécute que si :
 *   - `--confirm <empreinte>` est égal à l'empreinte du diff RECALCULÉ au moment
 *     de l'exécution (refus si le serveur ou le plan a changé depuis
 *     `plan:guild`) ;
 *   - le bot a « Gérer les rôles » / « Gérer les salons » selon le lot, est placé
 *     au-dessus des rôles qu'il modifie et possède chaque permission qu'il
 *     accorde (règle Discord) ;
 *   - une sauvegarde complète (audit-guild.ts) vient d'être écrite.
 *
 *  Lots, dans l'ordre (arrêt au premier échec, journal détaillé) :
 *    roles, roles-maj, roles-ordre, permissions, salons, overwrites, salons-ordre
 *  `--lot <nom>` n'applique qu'un lot. Sans `--lot`, tous les lots s'enchaînent ;
 *  chaque lot après le premier est recalculé sur l'état frais et doit avoir la
 *  même intention (signature) que dans le diff validé, sinon arrêt.
 *
 *  Jamais : suppression de rôle ou de salon (seulement listés par plan:guild),
 *  attribution de rôle à un membre (sauf `--attribuer-fondateurs`, explicite :
 *  donne « | Fondateur » aux membres qui ont aujourd'hui Direction, listés avant).
 *
 *  Administrateur n'est retiré à Direction / Head of Reborn (ex-Gérant SR) que par
 *  `--lot permissions` passé explicitement (empreinte de `plan:guild --sans-admin`),
 *  et seulement si « | Fondateur » existe, a Administrateur et au moins un membre.
 *
 *  Usage (depuis la racine du monorepo) :
 *    pnpm --filter @reborn/bot plan:guild                         → empreinte
 *    pnpm --filter @reborn/bot apply:guild --confirm <empreinte> --lot roles
 *    pnpm --filter @reborn/bot apply:guild --confirm <empreinte> --attribuer-fondateurs
 *
 *  Doc : docs/operations/DISCORD_STAFF.md
 * ============================================================================
 */
import {
  ChannelType,
  Client,
  Events,
  GatewayIntentBits,
  PermissionFlagsBits,
  PermissionsBitField,
  type Guild,
  type GuildBasedChannel,
  type NonThreadGuildBasedChannel,
} from "discord.js";
import { config as loadDotenv } from "dotenv";
import { spawnSync } from "node:child_process";
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  LOTS,
  fetchMembersRetry,
  LOT_FR,
  assertOutsideRepo,
  bitsOf,
  computeDiff,
  findUp,
  fingerprint,
  installRestGuard,
  lotSignature,
  matchPlan,
  permLabels,
  namesOf,
  takeSnapshot,
  timestamp,
  validatePlan,
  type Diff,
  type Lot,
  type Op,
  type Plan,
  type Snapshot,
} from "./guild-plan-lib.js";

const here = dirname(fileURLToPath(import.meta.url));
const botDir = resolve(here, "..", "..");
const envPath = findUp(here, ".env");
if (envPath) loadDotenv({ path: envPath });
const workspaceFile = findUp(here, "pnpm-workspace.yaml");
const repoRoot = workspaceFile ? dirname(workspaceFile) : null;

// ---------------------------------------------------------------------------
// Arguments
// ---------------------------------------------------------------------------

const defaultDir = process.env.REBORN_DISCORD_AUDIT_DIR ?? join(homedir(), "Desktop", "Reborn - Gestion", "Discord");
let planPath = join(defaultDir, "plan-rbn-staff.json");
let outParent = defaultDir;
let confirm: string | null = null;
let onlyLot: Lot | null = null;
let attribuerFondateurs = false;
const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
  const a = argv[i];
  if (a === "--") continue;
  if (a === "--confirm") confirm = (argv[++i] ?? "").trim().toLowerCase();
  else if (a === "--lot") {
    const v = argv[++i] ?? "";
    if (!(LOTS as readonly string[]).includes(v)) {
      console.error(`--lot attend l'un de : ${LOTS.join(", ")}`);
      process.exit(2);
    }
    onlyLot = v as Lot;
  } else if (a === "--attribuer-fondateurs") attribuerFondateurs = true;
  else if (a === "--plan") planPath = argv[++i] ?? planPath;
  else if (a === "--out") outParent = argv[++i] ?? outParent;
  else if (a === "--help" || a === "-h") {
    console.log(
      [
        "Application d'un plan Discord, par lots, sur empreinte.",
        "  --confirm <empreinte>     OBLIGATOIRE : empreinte affichée par plan:guild (recalculée ici)",
        `  --lot <nom>               un seul lot : ${LOTS.join(", ")}`,
        "  --attribuer-fondateurs    donne « | Fondateur » aux membres qui ont Direction (rien d'autre)",
        "  --plan <fichier>          plan JSON (défaut : Desktop\\Reborn - Gestion\\Discord\\plan-rbn-staff.json)",
        "  --out <dossier>           dossier parent (sauvegarde + journal)",
      ].join("\n"),
    );
    process.exit(0);
  } else {
    console.error(`Argument inconnu : ${a}`);
    process.exit(2);
  }
}

if (!confirm || !/^[0-9a-f]{64}$/.test(confirm)) {
  console.error("Refus : --confirm <empreinte> est obligatoire (64 caractères hexadécimaux, affichés par « plan:guild »).");
  process.exit(2);
}
if (attribuerFondateurs && onlyLot) {
  console.error("Refus : --attribuer-fondateurs s'utilise seul (sans --lot).");
  process.exit(2);
}
assertOutsideRepo(planPath, repoRoot, "le plan");
assertOutsideRepo(outParent, repoRoot, "le dossier de sortie");
if (!existsSync(planPath)) {
  console.error(`Plan introuvable : ${planPath}`);
  process.exit(2);
}
const plan = JSON.parse(readFileSync(planPath, "utf8")) as Plan;
const planErrors = validatePlan(plan);
if (planErrors.length) {
  console.error("Plan invalide :\n" + planErrors.map((e) => `  - ${e}`).join("\n"));
  process.exit(2);
}
const token = process.env.DISCORD_BOT_TOKEN;
if (!token) {
  console.error("DISCORD_BOT_TOKEN absent (cherché dans le .env racine et l'environnement).");
  process.exit(2);
}

/** Administrateur n'est retiré que par « --lot permissions » explicite. */
const keepAdmin = onlyLot !== "permissions";

// ---------------------------------------------------------------------------
// Journal
// ---------------------------------------------------------------------------

let journalPath: string | null = null;
function log(line: string): void {
  console.log(line);
  if (journalPath) appendFileSync(journalPath, `${line}\n`, "utf8");
}

class Refus extends Error {}

// ---------------------------------------------------------------------------
// Vérifications du bot
// ---------------------------------------------------------------------------

function roleIdOf(snap: Snapshot, key: string): string {
  const m = matchPlan(plan, snap);
  const r = m.roles.get(key);
  if (!r) throw new Refus(`Le rôle « ${plan.roles.find((x) => x.key === key)?.name ?? key} » n'existe pas encore : appliquer d'abord le lot « roles ».`);
  return r.id;
}

function preflight(snap: Snapshot, lot: Lot, ops: Op[]): void {
  const botPerms = new PermissionsBitField(BigInt(snap.bot.permissions));
  const isAdmin = snap.bot.isAdministrator;
  const need: bigint[] =
    lot === "salons" || lot === "salons-ordre"
      ? [PermissionFlagsBits.ManageChannels]
      : lot === "overwrites"
        ? [PermissionFlagsBits.ManageChannels, PermissionFlagsBits.ManageRoles]
        : [PermissionFlagsBits.ManageRoles];
  const missing = need.filter((b) => !botPerms.has(b));
  if (missing.length) {
    throw new Refus(
      `Le bot n'a pas ${permLabels(namesOf(missing.reduce((x, y) => x | y, 0n)))} sur le serveur. ` +
        "Paramètres du serveur → Rôles → rôle du bot → cocher Gérer les rôles et Gérer les salons (le temps de l'application).",
    );
  }
  // Hiérarchie : le bot ne modifie / déplace que des rôles situés SOUS son rôle le plus haut.
  if (lot === "roles-maj" || lot === "roles-ordre" || lot === "permissions") {
    const m = matchPlan(plan, snap);
    const ids = new Set<string>();
    if (lot === "roles-ordre") {
      for (const r of snap.roles) if (!r.isEveryone && r.id !== snap.bot.highestRoleId) ids.add(r.id);
      const botKey = [...m.roles.entries()].find(([, r]) => r.id === snap.bot.highestRoleId)?.[0];
      const top = snap.roles.find((r) => !r.isEveryone);
      if (botKey !== "bots" || top?.id !== snap.bot.highestRoleId) {
        throw new Refus(
          "Le rôle du bot doit être TOUT EN HAUT de la liste des rôles avant de réordonner. " +
            "Paramètres du serveur → Rôles → glisser le rôle du bot en première position (seul le propriétaire ou un rôle au-dessus peut le faire).",
        );
      }
    } else {
      for (const o of ops) if (o.id && o.key !== "everyone") ids.add(o.id);
    }
    const above = snap.roles.filter((r) => ids.has(r.id) && r.position >= snap.bot.highestPosition);
    if (above.length) {
      throw new Refus(
        `Le bot ne peut pas modifier ${above.length} rôle(s) placés au-dessus (ou au niveau) de son rôle « ${snap.roles.find((r) => r.id === snap.bot.highestRoleId)?.name} » : ` +
          `${above.map((r) => `« ${r.name} »`).join(", ")}. Monter le rôle du bot tout en haut (Paramètres du serveur → Rôles), puis relancer plan:guild.`,
      );
    }
  }
  // Règle Discord : on ne peut accorder (rôle ou overwrite) qu'une permission que l'on possède soi-même.
  if (!isAdmin && (lot === "permissions" || lot === "overwrites")) {
    let granted = 0n;
    for (const o of ops) {
      if (o.kind === "role.permissions") granted |= bitsOf(o.after);
      if (o.kind === "channel.overwrites") for (const x of o.after) granted |= bitsOf(x.allow) | bitsOf(x.deny);
    }
    const lacking = granted & ~BigInt(snap.bot.permissions);
    if (lacking) {
      throw new Refus(
        `Discord interdit d'accorder une permission que le bot n'a pas. Il lui manque : ${permLabels(namesOf(lacking))}. ` +
          "Soit lui laisser Administrateur le temps de ce lot, soit lui donner ces permissions, soit régler ces rôles à la main.",
      );
    }
  }
  // Retrait d'Administrateur : seulement si « | Fondateur » existe, a Administrateur (ou le reçoit avant dans ce lot) et a au moins un membre.
  if (lot === "permissions" && ops.some((o) => o.kind === "role.permissions" && o.adminRemoval)) {
    if (onlyLot !== "permissions") throw new Refus("Retrait d'Administrateur demandé hors « --lot permissions » explicite : refus.");
    const m = matchPlan(plan, snap);
    const fond = m.roles.get("fondateur");
    if (!fond) throw new Refus("Retrait d'Administrateur refusé : le rôle « | Fondateur » n'existe pas (lot « roles »).");
    const fondMembers = snap.membersByRole?.[fond.id];
    if (!snap.membersByRole) throw new Refus("Retrait d'Administrateur refusé : liste des membres illisible (intent Server Members), impossible de vérifier « | Fondateur ».");
    if (!fondMembers || fondMembers.length === 0) {
      throw new Refus("Retrait d'Administrateur refusé : « | Fondateur » n'est attribué à personne. Le donner d'abord (à la main ou --attribuer-fondateurs).");
    }
    const fondHasAdmin = (BigInt(fond.permissions) & PermissionFlagsBits.Administrator) !== 0n;
    const grantedInLot = ops.some((o) => o.kind === "role.permissions" && o.key === "fondateur" && o.after.includes("Administrator"));
    if (!fondHasAdmin && !grantedInLot) throw new Refus("Retrait d'Administrateur refusé : « | Fondateur » n'a pas Administrateur.");
    log(`  ✔ « | Fondateur » existe, ${fondHasAdmin ? "a" : "reçoit dans ce lot"} Administrateur, ${fondMembers.length} membre(s) : ${fondMembers.join(", ")}`);
  }
}

// ---------------------------------------------------------------------------
// Exécution des lots
// ---------------------------------------------------------------------------

const REASON = "Plan RBN - STAFF (apply-guild, lot validé)";

function chanIdOf(snap: Snapshot, key: string): string {
  const m = matchPlan(plan, snap);
  const c = m.channels.get(key);
  if (!c) throw new Refus(`Catégorie / salon « ${key} » introuvable : appliquer d'abord le lot « salons ».`);
  return c.id;
}

async function fetchChannel(guild: Guild, id: string): Promise<NonThreadGuildBasedChannel> {
  const c = (await guild.channels.fetch(id)) as GuildBasedChannel | null;
  if (!c || c.isThread()) throw new Error(`salon ${id} introuvable`);
  return c as NonThreadGuildBasedChannel;
}

async function runLot(guild: Guild, snap: Snapshot, lot: Lot, ops: Op[]): Promise<void> {
  let n = 0;
  const step = (label: string) => log(`  [${lot} ${++n}/${ops.length}] ${label}`);
  for (const o of ops) {
    switch (o.kind) {
      case "role.create": {
        step(`création du rôle « ${o.after.name} »`);
        const r = await guild.roles.create({
          name: o.after.name,
          colors: { primaryColor: o.after.color },
          hoist: o.after.hoist,
          mentionable: o.after.mentionable,
          permissions: [],
          reason: REASON,
        });
        log(`      → créé (${r.id})`);
        break;
      }
      case "role.update": {
        const role = await guild.roles.fetch(o.id);
        if (!role) throw new Error(`rôle ${o.id} introuvable`);
        step(`rôle « ${role.name} » : ${Object.entries(o.after).map(([k, v]) => `${k} → ${String(v)}`).join(", ")}`);
        await role.edit({
          ...(o.after.name !== undefined ? { name: String(o.after.name) } : {}),
          ...(o.after.color !== undefined ? { colors: { primaryColor: Number(o.after.color) } } : {}),
          ...(o.after.hoist !== undefined ? { hoist: Boolean(o.after.hoist) } : {}),
          ...(o.after.mentionable !== undefined ? { mentionable: Boolean(o.after.mentionable) } : {}),
          reason: REASON,
        });
        break;
      }
      case "role.order": {
        const m = matchPlan(plan, snap);
        const ids = o.after
          .map((k) => (k.startsWith("id:") ? k.slice(3) : m.roles.get(k)?.id))
          .filter((id): id is string => !!id && id !== snap.bot.highestRoleId && id !== guild.id);
        if (o.after.some((k) => !k.startsWith("id:") && k !== "bots" && !m.roles.has(k))) {
          throw new Refus("Des rôles du plan n'existent pas encore : appliquer d'abord le lot « roles ».");
        }
        const positions = ids.map((id, i) => ({ role: id, position: ids.length - i }));
        step(`ordre de ${positions.length} rôle(s) (sous le rôle du bot)`);
        try {
          await guild.roles.setPositions(positions);
        } catch (err) {
          // Discord refuse parfois le déplacement groupé (rôles gérés par une intégration, ex. Booster) :
          // on retombe sur un déplacement rôle par rôle, du bas vers le haut, en sautant les rôles gérés.
          step(`déplacement groupé refusé (${(err as Error).message}) — repli rôle par rôle`);
          for (const p of [...positions].reverse()) {
            const role = await guild.roles.fetch(p.role);
            if (!role || role.managed) continue;
            await role.setPosition(p.position, { reason: REASON });
          }
        }
        break;
      }
      case "role.permissions": {
        const id = o.key === "everyone" ? guild.id : (o.id ?? roleIdOf(snap, o.key));
        const role = await guild.roles.fetch(id);
        if (!role) throw new Error(`rôle ${id} introuvable`);
        step(`permissions de « ${role.name} »${o.adminRemoval ? " — RETRAIT D'ADMINISTRATEUR" : ""} : +${permLabels(o.added)} / −${permLabels(o.removed)}`);
        await role.setPermissions(bitsOf(o.after), REASON);
        break;
      }
      case "channel.create": {
        step(`création ${o.after.type === ChannelType.GuildCategory ? "de la catégorie" : "du salon"} « ${o.after.name} »`);
        const parentId = o.after.parent ? chanIdOf(await freshSnap(guild), o.after.parent) : null;
        const c = await guild.channels.create({
          name: o.after.name,
          type: o.after.type as ChannelType.GuildText,
          ...(parentId ? { parent: parentId } : {}),
          ...(o.after.topic ? { topic: o.after.topic } : {}),
          // Sûr par défaut : invisible pour @everyone tant que le lot « overwrites » n'est pas passé.
          permissionOverwrites: [{ id: guild.id, deny: [PermissionFlagsBits.ViewChannel] }],
          reason: REASON,
        });
        log(`      → créé (${c.id})`);
        break;
      }
      case "channel.update": {
        const ch = await fetchChannel(guild, o.id);
        const parentKey = o.after.parent as string | null | undefined;
        const parentId = parentKey === undefined ? undefined : parentKey === null ? null : chanIdOf(await freshSnap(guild), parentKey);
        step(`« ${ch.name} »${o.after.name !== undefined ? ` → « ${String(o.after.name)} »` : ""}${parentKey !== undefined ? ` · déplacé vers ${parentKey ?? "(hors catégorie)"}` : ""}`);
        await ch.edit({
          ...(o.after.name !== undefined ? { name: String(o.after.name) } : {}),
          ...(parentId !== undefined ? { parent: parentId, lockPermissions: false } : {}),
          ...(o.after.topic !== undefined ? { topic: String(o.after.topic) } : {}),
          reason: REASON,
        });
        break;
      }
      case "channel.overwrites": {
        const s = await freshSnap(guild);
        const id = o.id ?? chanIdOf(s, o.key);
        const ch = await fetchChannel(guild, id);
        if (o.sync) {
          const cat = plan.categories.find((c) => c.channels.some((x) => x.key === o.key));
          const wantParent = cat ? chanIdOf(s, cat.key) : null;
          if (!wantParent || ch.parentId !== wantParent) throw new Refus(`« ${ch.name} » n'est pas dans sa catégorie cible : appliquer d'abord le lot « salons ».`);
          step(`« ${ch.name} » : synchronisé avec sa catégorie`);
          await ch.lockPermissions();
        } else {
          const list = o.after.map((x) => ({
            id: x.target === "everyone" ? guild.id : roleIdOf(s, x.target),
            allow: bitsOf(x.allow),
            deny: bitsOf(x.deny),
          }));
          step(`« ${ch.name} » : ${list.length} permission(s) de rôle${ch.type === ChannelType.GuildCategory ? " (catégorie)" : ""}`);
          await ch.permissionOverwrites.set(list, REASON);
        }
        break;
      }
      case "channel.order": {
        const s = await freshSnap(guild);
        const m = matchPlan(plan, s);
        const positions: { channel: string; position: number }[] = [];
        // Salons hors catégorie (en tête) : Discord les range au-dessus des catégories, seul leur ordre compte.
        (plan.topChannels ?? []).forEach((x, j) => {
          const ch = m.channels.get(x.key);
          if (!ch) throw new Refus(`Salon « ${x.name} » introuvable : appliquer d'abord le lot « salons ».`);
          if (ch.parentId) throw new Refus(`« ${ch.name} » est encore dans une catégorie : appliquer d'abord le lot « salons ».`);
          positions.push({ channel: ch.id, position: j });
        });
        plan.categories.forEach((c, i) => {
          const cat = m.channels.get(c.key);
          if (!cat) throw new Refus(`Catégorie « ${c.name} » introuvable : appliquer d'abord le lot « salons ».`);
          positions.push({ channel: cat.id, position: i });
          c.channels.forEach((x, j) => {
            const ch = m.channels.get(x.key);
            if (!ch) throw new Refus(`Salon « ${x.name} » introuvable : appliquer d'abord le lot « salons ».`);
            if (ch.parentId !== cat.id) throw new Refus(`« ${ch.name} » n'est pas dans « ${c.name} » : appliquer d'abord le lot « salons ».`);
            positions.push({ channel: ch.id, position: j });
          });
        });
        step(`ordre de ${positions.length} catégorie(s) et salon(s)`);
        await guild.channels.setPositions(positions);
        break;
      }
    }
  }
}

let clientRef: Client<true> | null = null;
async function freshSnap(guild: Guild): Promise<Snapshot> {
  return takeSnapshot(clientRef!, guild, true);
}

function backup(guildId: string): void {
  log("Sauvegarde complète (audit-guild.ts, lecture seule) avant d'appliquer…");
  const r = spawnSync(process.execPath, ["--import", "tsx", join(here, "audit-guild.ts"), "--guild", guildId, "--out", resolve(outParent)], {
    cwd: botDir,
    stdio: "inherit",
    env: process.env,
  });
  if (r.status !== 0) throw new Refus(`Sauvegarde impossible (audit-guild a échoué, code ${r.status}) : rien n'est appliqué.`);
  log("Sauvegarde OK (dossier audit-… ci-dessus).");
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

const client = new Client({ intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMembers] });
const guard = installRestGuard(client, "apply");

const timeout = setTimeout(() => {
  console.error("Pas de connexion à Discord après 45 s — abandon.");
  void client.destroy().finally(() => process.exit(1));
}, 45_000);

client.once(Events.ClientReady, async (ready) => {
  clearTimeout(timeout);
  clientRef = ready;
  let code = 0;
  try {
    const guild = ready.guilds.cache.get(plan.guild.id);
    if (!guild) throw new Refus(`Le bot n'est pas sur le serveur du plan (${plan.guild.name}).`);
    const snap0 = await takeSnapshot(ready, guild, true);
    const diff0: Diff = computeDiff(plan, snap0, { keepAdmin });
    const fp0 = fingerprint(diff0);
    if (fp0 !== confirm) {
      throw new Refus(
        `Empreinte différente : le serveur (ou le plan) a changé depuis la simulation.\n` +
          `  attendue (--confirm) : ${confirm}\n  actuelle             : ${fp0}\n` +
          `Relancer « pnpm --filter @reborn/bot plan:guild${keepAdmin ? "" : " --sans-admin"} », relire le DIFF, puis reprendre l'empreinte affichée.`,
      );
    }
    const dir = join(resolve(outParent), `apply-${timestamp()}`);
    mkdirSync(dir, { recursive: true });
    journalPath = join(dir, "JOURNAL.md");
    writeFileSync(journalPath, `# Journal d'application — ${guild.name}\n\n`, "utf8");
    writeFileSync(join(dir, "etat-avant.json"), JSON.stringify(snap0, null, 2), "utf8");
    writeFileSync(join(dir, "diff.json"), JSON.stringify({ fingerprint: fp0, ...diff0 }, null, 2), "utf8");
    log(`Empreinte vérifiée : ${fp0}`);
    log(`Mode : ${attribuerFondateurs ? "--attribuer-fondateurs" : onlyLot ? `--lot ${onlyLot}` : "tous les lots"} · Administrateur ${keepAdmin ? "gardé" : "RETIRÉ"} sur Direction / Head of Reborn`);

    // ------------------------------------------------ attribution des Fondateurs (option explicite)
    if (attribuerFondateurs) {
      const m = matchPlan(plan, snap0);
      const fond = m.roles.get("fondateur");
      const dir_ = m.roles.get("direction");
      if (!fond) throw new Refus("« | Fondateur » n'existe pas : appliquer d'abord le lot « roles ».");
      if (!dir_) throw new Refus("Rôle Direction introuvable.");
      if (fond.position >= snap0.bot.highestPosition) throw new Refus("« | Fondateur » est au-dessus du rôle du bot : monter le rôle du bot tout en haut.");
      const members = await fetchMembersRetry(guild);
      const targets = [...members.values()].filter((mm) => mm.roles.cache.has(dir_.id));
      log(`Membres qui ont « ${dir_.name} » (${targets.length}) : ${targets.map((t) => t.displayName).join(", ") || "aucun"}`);
      if (!targets.length) throw new Refus("Personne n'a Direction : rien à attribuer.");
      backup(guild.id);
      guard.allowWrites(true);
      for (const t of targets) {
        if (t.roles.cache.has(fond.id)) {
          log(`  = ${t.displayName} a déjà « | Fondateur »`);
          continue;
        }
        log(`  + « | Fondateur » → ${t.displayName}`);
        await t.roles.add(fond.id, REASON);
      }
      guard.allowWrites(false);
      log("Fait. Relancer « plan:guild » pour la suite.");
      return;
    }

    // ------------------------------------------------ lots
    const lots: Lot[] = onlyLot ? [onlyLot] : LOTS.filter((l) => diff0.ops.some((o) => o.lot === l));
    const lotOps = (d: Diff, l: Lot) => d.ops.filter((o) => o.lot === l);
    if (lots.every((l) => lotOps(diff0, l).length === 0)) {
      log("Rien à appliquer pour ce(s) lot(s).");
      return;
    }
    // Vérification préalable du premier lot avant toute écriture (y compris la sauvegarde).
    preflight(snap0, lots[0]!, lotOps(diff0, lots[0]!));
    backup(guild.id);

    for (let i = 0; i < lots.length; i++) {
      const lot = lots[i]!;
      let snap = snap0;
      let ops = lotOps(diff0, lot);
      if (i > 0) {
        snap = await freshSnap(guild);
        const d = computeDiff(plan, snap, { keepAdmin });
        if (lotSignature(d, lot) !== lotSignature(diff0, lot)) {
          throw new Refus(`Le lot « ${lot} » recalculé après les lots précédents ne correspond plus au diff validé : arrêt. Relancer plan:guild.`);
        }
        ops = lotOps(d, lot);
      }
      log("");
      log(`## Lot ${lot} — ${LOT_FR[lot]} : ${ops.length} opération(s)`);
      if (!ops.length) continue;
      preflight(snap, lot, ops);
      guard.allowWrites(true);
      try {
        await runLot(guild, snap, lot, ops);
      } finally {
        guard.allowWrites(false);
      }
      log(`Lot ${lot} : OK`);
    }
    log("");
    log("Terminé. Relancer « pnpm --filter @reborn/bot plan:guild » : le diff des lots appliqués doit être vide.");
  } catch (err) {
    code = 1;
    if (err instanceof Refus) log(`\nREFUS : ${err.message}`);
    else log(`\nÉCHEC (arrêt au premier échec) : ${(err as Error).stack ?? String(err)}`);
  } finally {
    guard.allowWrites(false);
    if (journalPath) {
      appendFileSync(journalPath, `\n## Requêtes d'écriture envoyées (${guard.writes.length})\n\n${guard.writes.map((w) => `- ${w}`).join("\n")}\n`, "utf8");
      console.log(`Journal : ${journalPath}`);
    }
    await ready.destroy();
    process.exit(code);
  }
});

await client.login(token);
