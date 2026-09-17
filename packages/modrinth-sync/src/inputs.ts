// Chargement du mapping (mods.config.json) et lecture de la liste de mods
// actuellement live (dernier manifeste signé local sous secrets/).
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

export type ModPolicy = "auto" | "pinned";

export interface ModEntry {
  prefix: string;
  slug: string;
  policy: ModPolicy;
  note?: string;
}

export interface SyncConfig {
  gameVersion: string;
  loader: string;
  mods: ModEntry[];
}

export interface ManifestMod {
  filename: string; // ex: "sodium-fabric-0.9.1+mc26.2.jar"
  path: string; // ex: "mods/sodium-fabric-0.9.1+mc26.2.jar"
  required: boolean;
}

export function loadConfig(path: string): SyncConfig {
  const raw = JSON.parse(readFileSync(path, "utf-8"));
  if (!Array.isArray(raw.mods)) throw new Error(`${path} : champ "mods" manquant`);
  return { gameVersion: raw.gameVersion, loader: raw.loader, mods: raw.mods };
}

/** Compare des versions "3.1.85" numériquement segment par segment. */
function cmpVersion(a: string, b: string): number {
  const pa = a.split(".").map((n) => parseInt(n, 10) || 0);
  const pb = b.split(".").map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) return d;
  }
  return 0;
}

/** Trouve le dernier `secrets/manifest-signed-v<X>.json` (version la plus haute). */
export function findLatestManifest(secretsDir: string): string {
  const re = /^manifest-signed-v(\d+\.\d+\.\d+)\.json$/;
  const cands = readdirSync(secretsDir)
    .map((f) => ({ f, m: f.match(re) }))
    .filter((x) => x.m)
    .map((x) => ({ file: join(secretsDir, x.f), ver: x.m![1] }))
    .sort((a, b) => cmpVersion(b.ver, a.ver));
  if (cands.length === 0) throw new Error(`Aucun manifest-signed-v*.json dans ${secretsDir}`);
  return cands[0].file;
}

/** Extrait les entrées `mods/*.jar` (hors reborn-*) d'un manifeste signé. */
export function extractMods(manifestPath: string): ManifestMod[] {
  const m = JSON.parse(readFileSync(manifestPath, "utf-8"));
  const out: ManifestMod[] = [];
  for (const fe of m.files as Array<{ path: string; required: boolean }>) {
    if (fe.path.startsWith("mods/") && !fe.path.startsWith("mods/reborn")) {
      out.push({ filename: fe.path.slice("mods/".length), path: fe.path, required: fe.required });
    }
  }
  return out;
}
