// CLI modrinth-sync. Slice 1 : `check` (dry-run) — détecte les mods `auto` qui
// ont une version compatible plus récente sur Modrinth que celle du manifeste live.
//
//   pnpm exec tsx src/cli.ts check [--manifest <chemin-manifest-signé>]
//
// Prochaines slices : `prepare` (télécharge + ré-héberge + manifeste candidat +
// notif Discord) puis publication 1-clic locale (signe hors-ligne + POST).
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { loadConfig, findLatestManifest, extractMods } from "./inputs";
import { checkAll, type Report } from "./check";
import { prepare } from "./prepare";
import { publish } from "./publish";

const HERE = dirname(fileURLToPath(import.meta.url));
const PKG = join(HERE, "..");
const REPO = join(PKG, "..", "..");

const ICON: Record<Report["status"], string> = {
  update: "🟢 UPDATE",
  "up-to-date": "✓  à jour",
  pinned: "📌 épinglé",
  "not-in-manifest": "—  absent manifeste",
  "no-compatible": "⚠  aucune compat",
  "slug-not-found": "❌ slug introuvable",
  error: "❌ erreur",
};

async function cmdCheck(argv: string[]) {
  const config = loadConfig(join(PKG, "mods.config.json"));
  const mIdx = argv.indexOf("--manifest");
  const manifestPath =
    mIdx >= 0 ? argv[mIdx + 1] : findLatestManifest(join(REPO, "secrets"));
  const mods = extractMods(manifestPath);

  console.log(`Manifeste live : ${manifestPath}`);
  console.log(`Cible          : MC ${config.gameVersion} / ${config.loader}`);
  console.log(`Mods mappés     : ${config.mods.length} (manifeste : ${mods.length} mods tiers)\n`);

  const reports = await checkAll(config, mods);

  for (const r of reports) {
    const label = ICON[r.status];
    console.log(`${label.padEnd(20)} ${r.slug}`);
    if (r.status === "update") {
      console.log(`   ${r.current}`);
      console.log(`   →  ${r.latest}   (v${r.latestVersion}, ${r.latestType})`);
    } else if (r.status === "pinned") {
      console.log(`   ${r.current}${r.detail ? `  — ${r.detail}` : ""}`);
    } else if (r.status === "slug-not-found") {
      console.log(`   slug "${r.slug}" absent. Suggestions :`);
      (r.suggestions ?? []).forEach((s) => console.log(`     · ${s}`));
    } else if (r.status === "no-compatible" || r.status === "error" || r.status === "not-in-manifest") {
      console.log(`   ${r.current ?? "(non trouvé dans le manifeste)"}${r.detail ? `  — ${r.detail}` : ""}`);
    }
  }

  const n = (s: Report["status"]) => reports.filter((r) => r.status === s).length;
  console.log(
    `\nRésumé : ${n("update")} update(s), ${n("up-to-date")} à jour, ${n("pinned")} épinglé(s), ` +
      `${n("slug-not-found")} slug KO, ${n("no-compatible")} sans compat, ${n("error")} erreur(s).`,
  );
  if (n("update") > 0) {
    console.log("Prochaine étape (à venir) : `prepare` téléchargera + ré-hébergera + préparera un manifeste candidat pour validation 1-clic.");
  }
}

async function cmdPrepare(argv: string[]) {
  const config = loadConfig(join(PKG, "mods.config.json"));
  const mIdx = argv.indexOf("--manifest");
  const manifestPath =
    mIdx >= 0 ? argv[mIdx + 1] : findLatestManifest(join(REPO, "secrets"));
  const mods = extractMods(manifestPath);

  console.log(`Base           : ${manifestPath}`);
  console.log(`Cible          : MC ${config.gameVersion} / ${config.loader}\n`);
  console.log("Téléchargement + vérif des mods à jour…");

  const res = await prepare(config, mods, manifestPath, join(REPO, "secrets"));
  if (res.changes.length === 0) {
    console.log("\nAucune update `auto` — rien à préparer.");
    return;
  }
  console.log(`\nManifeste candidat : ${res.candidatePath}  (v${res.version})`);
  console.log("Changements :");
  for (const c of res.changes) {
    console.log(`  🟢 ${c.slug}`);
    console.log(`     ${c.from}`);
    console.log(`     →  ${c.to}  (${c.type}, ${c.sizeMb.toFixed(1)} Mo)`);
  }
  console.log(
    "\nURLs = CDN Modrinth (vérifié sha256 côté launcher). Prochaine étape : `publish` (Slice 3) signera hors-ligne + POST /v1/admin/manifest, avec notif bot Discord.",
  );
}

async function cmdPublish(argv: string[]) {
  const cIdx = argv.indexOf("--candidate");
  const candidate = cIdx >= 0 ? argv[cIdx + 1] : undefined;
  const confirm = argv.includes("--confirm");
  await publish(join(REPO, "secrets"), REPO, candidate, confirm);
}

async function main() {
  const cmd = process.argv[2] ?? "check";
  if (cmd === "check") {
    await cmdCheck(process.argv.slice(3));
  } else if (cmd === "prepare") {
    await cmdPrepare(process.argv.slice(3));
  } else if (cmd === "publish") {
    await cmdPublish(process.argv.slice(3));
  } else {
    console.error(`Commande inconnue : ${cmd}. Disponibles : check, prepare, publish`);
    process.exit(1);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
