#!/usr/bin/env node
/**
 * Construit un manifest unsigned depuis un dossier de mods.
 *
 *   tsx src/build-from-folder.ts <mods-dir> --base-url <url> --version <ver> [--mc <ver>] [--out <file>]
 *
 * Pour chaque .jar du dossier, calcule sha256 + size, et construit l'URL
 * en concatenant base-url + filename. Sortie : unsigned.json pret a etre
 * passe a `sign`.
 *
 * Exemple :
 *   tsx src/build-from-folder.ts ./jars \
 *     --base-url https://github.com/OMZBRK/reborn-roleplay/releases/download/mods-v1 \
 *     --version 1.0.0 --mc 1.21.1 --out ../../secrets/manifest-unsigned.json
 */
import { createHash } from "node:crypto";
import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { resolve, join } from "node:path";
import type { ManifestFile, UnsignedManifest } from "./manifest.js";

function arg(flag: string, args: string[]): string | undefined {
  const i = args.indexOf(flag);
  return i >= 0 ? args[i + 1] : undefined;
}

const [, , dirArg, ...rest] = process.argv;
if (!dirArg) {
  console.error(
    "Usage: tsx src/build-from-folder.ts <mods-dir> --base-url <url> --version <ver> [--mc <ver>] [--out <file>]",
  );
  process.exit(2);
}

const dir = resolve(dirArg);
const baseUrl = arg("--base-url", rest);
const version = arg("--version", rest);
const mcVersion = arg("--mc", rest) ?? "1.21.1";
const outPath = arg("--out", rest) ?? resolve(dir, "manifest-unsigned.json");

if (!baseUrl || !version) {
  console.error("Missing --base-url or --version.");
  process.exit(2);
}

const trimmedBase = baseUrl.replace(/\/$/, "");

const jars = readdirSync(dir)
  .filter((f) => f.toLowerCase().endsWith(".jar"))
  .sort();

if (jars.length === 0) {
  console.error(`Aucun .jar dans ${dir}`);
  process.exit(1);
}

const files: ManifestFile[] = jars.map((filename) => {
  const fullPath = join(dir, filename);
  const data = readFileSync(fullPath);
  const sha256 = createHash("sha256").update(data).digest("hex");
  const size = statSync(fullPath).size;
  return {
    path: `mods/${filename}`,
    sha256,
    size,
    url: `${trimmedBase}/${encodeURIComponent(filename)}`,
    required: true,
  };
});

const now = new Date();
const expires = new Date(now.getTime() + 365 * 24 * 60 * 60 * 1000);

const manifest: UnsignedManifest = {
  version,
  minecraftVersion: mcVersion,
  issuedAt: now.toISOString(),
  expiresAt: expires.toISOString(),
  minLauncherVersion: "0.1.0",
  files,
};

writeFileSync(outPath, JSON.stringify(manifest, null, 2) + "\n");
console.log(`Manifest non-signe ecrit : ${outPath}`);
console.log(`  ${files.length} fichier(s), version ${version}, MC ${mcVersion}`);
for (const f of files) {
  console.log(`  - ${f.path}  ${(f.size / 1024).toFixed(1)} KB  ${f.sha256.slice(0, 12)}…`);
}
console.log(``);
console.log(`Etape suivante :`);
console.log(
  `  pnpm exec tsx src/cli.ts sign ${outPath} --key <chemin-cle-privee> --out <out-signed.json>`,
);
