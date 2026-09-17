// `prepare` : construit un MANIFESTE CANDIDAT (non signé) intégrant les updates
// `auto` détectées. Télécharge chaque jar à jour depuis Modrinth, vérifie son
// intégrité (sha512 annoncé par Modrinth), calcule le sha256 + la taille attendus
// par le manifeste Reborn, et remplace l'entrée correspondante.
//
// L'URL du manifeste candidat pointe directement sur le CDN Modrinth
// (content-addressed, stable) — pas de ré-hébergement GitHub. Le launcher
// télécharge depuis n'importe quelle URL puis vérifie le sha256, donc c'est sûr.
// (Un ré-hébergement GitHub reste possible en Slice 2b si tu préfères self-host.)
//
// N'écrit qu'un fichier local. Ne signe pas, ne publie pas.
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { checkAll } from "./check";
import type { SyncConfig, ManifestMod } from "./inputs";

const UA = "reborn-roleplay/modrinth-sync (github.com/OMZBRK/reborn-roleplay)";

async function downloadAndHash(url: string): Promise<{ sha256: string; sha512: string; size: number }> {
  const res = await fetch(url, { headers: { "User-Agent": UA } });
  if (!res.ok) throw new Error(`téléchargement ${res.status} : ${url}`);
  const buf = Buffer.from(await res.arrayBuffer());
  return {
    sha256: createHash("sha256").update(buf).digest("hex"),
    sha512: createHash("sha512").update(buf).digest("hex"),
    size: buf.length,
  };
}

function bumpPatch(v: string): string {
  const p = v.split(".").map((n) => parseInt(n, 10) || 0);
  while (p.length < 3) p.push(0);
  p[2] += 1;
  return p.join(".");
}

export interface PrepareChange {
  slug: string;
  from: string;
  to: string;
  type?: string;
  sizeMb: number;
}

export interface PrepareResult {
  candidatePath?: string;
  version?: string;
  changes: PrepareChange[];
}

export async function prepare(
  config: SyncConfig,
  manifestMods: ManifestMod[],
  latestManifestPath: string,
  outDir: string,
): Promise<PrepareResult> {
  const reports = await checkAll(config, manifestMods);
  const updates = reports.filter((r) => r.status === "update" && r.latestUrl);
  if (updates.length === 0) return { changes: [] };

  const manifest = JSON.parse(readFileSync(latestManifestPath, "utf-8"));
  delete manifest.signature;
  const newVersion = bumpPatch(manifest.version);
  manifest.version = newVersion;

  const changes: PrepareChange[] = [];
  for (const u of updates) {
    process.stdout.write(`  ↓ ${u.slug.padEnd(24)} ${u.latest} … `);
    const { sha256, sha512, size } = await downloadAndHash(u.latestUrl!);
    if (u.latestSha512 && sha512 !== u.latestSha512) {
      throw new Error(`sha512 mismatch pour ${u.slug} — téléchargement corrompu, on stoppe.`);
    }
    const entry = manifest.files.find((f: { path: string }) => f.path === u.currentPath);
    if (!entry) throw new Error(`entrée manifeste introuvable pour ${u.currentPath}`);
    entry.path = `mods/${u.latest}`;
    entry.sha256 = sha256;
    entry.size = size;
    entry.url = u.latestUrl;
    // `required` inchangé.
    changes.push({ slug: u.slug, from: u.current!, to: u.latest!, type: u.latestType, sizeMb: size / 1e6 });
    console.log(`ok (${(size / 1e6).toFixed(1)} Mo)`);
  }

  const candidatePath = join(outDir, `manifest-candidate-v${newVersion}.json`);
  writeFileSync(candidatePath, JSON.stringify(manifest, null, 2), "utf-8");
  return { candidatePath, version: newVersion, changes };
}
