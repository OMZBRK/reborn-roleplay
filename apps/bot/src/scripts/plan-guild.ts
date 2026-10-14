/**
 * ============================================================================
 *  SIMULATION D'UN PLAN DISCORD — STRICTEMENT EN LECTURE SEULE
 * ============================================================================
 *
 *  Compare l'état réel d'un serveur au plan JSON (hors dépôt) et écrit le DIFF :
 *  renommages avant → après, créations, couleurs, permissions, positions,
 *  salons déplacés, overwrites modifiés, éléments hors plan (« à archiver /
 *  supprimer — décision »), plus une EMPREINTE SHA-256 du diff, exigée par
 *  apply-guild.ts (`--confirm <empreinte>`).
 *
 *  Garde-fous (mêmes que audit-guild.ts) :
 *   - uniquement le cache gateway et des GET ; `installRestGuard` rejette toute
 *     autre méthode AVANT l'envoi ;
 *   - intents Guilds + GuildMembers (liste des membres par rôle, pour les
 *     Fondateurs) ; jamais de lecture de messages ni de messages privés ;
 *   - plan et sortie refusés s'ils sont dans le dépôt (ils contiennent des IDs).
 *
 *  Usage (depuis la racine du monorepo) :
 *    pnpm --filter @reborn/bot plan:guild
 *    pnpm --filter @reborn/bot plan:guild --plan "<chemin>\plan-rbn-staff.json" [--out <dossier>]
 *    pnpm --filter @reborn/bot plan:guild --sans-admin   → diff du retrait d'Administrateur (« --lot permissions » explicite)
 *
 *  Sortie : <dossier>\diff-<date>\ DIFF.md + diff.json + etat.json + EMPREINTE.txt
 *  Doc : docs/operations/DISCORD_STAFF.md
 * ============================================================================
 */
import { Client, Events, GatewayIntentBits } from "discord.js";
import { config as loadDotenv } from "dotenv";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  LOTS,
  assertOutsideRepo,
  computeDiff,
  findUp,
  fingerprint,
  installRestGuard,
  renderDiffMarkdown,
  takeSnapshot,
  timestamp,
  validatePlan,
  type Plan,
} from "./guild-plan-lib.js";

const here = dirname(fileURLToPath(import.meta.url));
const envPath = findUp(here, ".env");
if (envPath) loadDotenv({ path: envPath });
const workspaceFile = findUp(here, "pnpm-workspace.yaml");
const repoRoot = workspaceFile ? dirname(workspaceFile) : null;

const defaultDir = process.env.REBORN_DISCORD_AUDIT_DIR ?? join(homedir(), "Desktop", "Reborn - Gestion", "Discord");
let planPath = join(defaultDir, "plan-rbn-staff.json");
let outParent = defaultDir;
let keepAdmin = true;
const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
  const a = argv[i];
  if (a === "--") continue;
  if (a === "--plan") planPath = argv[++i] ?? planPath;
  else if (a === "--out") outParent = argv[++i] ?? outParent;
  else if (a === "--sans-admin") keepAdmin = false;
  else if (a === "--help" || a === "-h") {
    console.log(
      [
        "Simulation (lecture seule) d'un plan Discord.",
        "  --plan <fichier>   plan JSON (défaut : Desktop\\Reborn - Gestion\\Discord\\plan-rbn-staff.json)",
        "  --out <dossier>    dossier parent de sortie (défaut : celui du plan par défaut)",
        "  --sans-admin       calcule le diff avec retrait d'Administrateur (pour « apply:guild --lot permissions »)",
      ].join("\n"),
    );
    process.exit(0);
  } else {
    console.error(`Argument inconnu : ${a}`);
    process.exit(2);
  }
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

const client = new Client({ intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMembers] });
installRestGuard(client, "plan");

const timeout = setTimeout(() => {
  console.error("Pas de connexion à Discord après 45 s — abandon.");
  void client.destroy().finally(() => process.exit(1));
}, 45_000);

client.once(Events.ClientReady, async (ready) => {
  clearTimeout(timeout);
  let code = 0;
  try {
    const guild = ready.guilds.cache.get(plan.guild.id);
    if (!guild) throw new Error(`Le bot n'est pas sur le serveur du plan (${plan.guild.name}).`);
    console.log(`Simulation sur « ${guild.name} » — lecture seule…`);
    const snap = await takeSnapshot(ready, guild, true);
    const diff = computeDiff(plan, snap, { keepAdmin });
    const fp = fingerprint(diff);
    const dir = join(resolve(outParent), `diff-${timestamp()}${keepAdmin ? "" : "-sans-admin"}`);
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, "DIFF.md"), renderDiffMarkdown(diff, plan, snap, fp, resolve(planPath)), { encoding: "utf8", flag: "wx" });
    writeFileSync(join(dir, "diff.json"), JSON.stringify({ fingerprint: fp, ...diff }, null, 2), { encoding: "utf8", flag: "wx" });
    writeFileSync(join(dir, "etat.json"), JSON.stringify(snap, null, 2), { encoding: "utf8", flag: "wx" });
    writeFileSync(join(dir, "EMPREINTE.txt"), `${fp}\n`, { encoding: "utf8", flag: "wx" });
    console.log("");
    for (const lot of LOTS) console.log(`  ${lot.padEnd(13)} ${diff.ops.filter((o) => o.lot === lot).length} opération(s)`);
    console.log(`  actions manuelles : ${diff.manual.length} · hors plan : ${diff.extras.roles.length} rôle(s), ${diff.extras.channels.length} salon(s)`);
    for (const w of diff.warnings) console.warn(`  ! ${w}`);
    console.log("");
    console.log(`Empreinte : ${fp}`);
    console.log(`Écrit dans : ${dir}`);
  } catch (err) {
    console.error("[plan] échec :", err);
    code = 1;
  } finally {
    await ready.destroy();
    process.exit(code);
  }
});

await client.login(token);
