// Détecteur d'updates (dry-run) : pour chaque mod `auto` du mapping, interroge
// Modrinth et compare la dernière version compatible au fichier actuellement
// dans le manifeste. Ne télécharge rien, ne publie rien.
import type { ModEntry, SyncConfig, ManifestMod } from "./inputs";
import {
  listCompatibleVersions,
  primaryFile,
  searchProjects,
  SlugNotFoundError,
} from "./modrinth";

export type Status =
  | "up-to-date"
  | "update"
  | "pinned"
  | "not-in-manifest"
  | "no-compatible"
  | "slug-not-found"
  | "error";

export interface Report {
  prefix: string;
  slug: string;
  policy: string;
  current?: string;
  latest?: string;
  latestVersion?: string;
  latestType?: string;
  status: Status;
  detail?: string;
  suggestions?: string[];
}

export async function checkOne(
  mod: ModEntry,
  manifestMods: ManifestMod[],
  gv: string,
  loader: string,
): Promise<Report> {
  const current = manifestMods.find((mm) => mm.filename.startsWith(mod.prefix));
  const base: Report = {
    prefix: mod.prefix,
    slug: mod.slug,
    policy: mod.policy,
    current: current?.filename,
    status: "up-to-date",
  };
  if (!current) return { ...base, status: "not-in-manifest" };
  if (mod.policy === "pinned") return { ...base, status: "pinned", detail: mod.note };

  try {
    const versions = await listCompatibleVersions(mod.slug, gv, loader);
    if (versions.length === 0) {
      return { ...base, status: "no-compatible", detail: `aucune version ${loader}/${gv}` };
    }
    const latest = versions[0];
    const pf = primaryFile(latest);
    if (!pf) return { ...base, status: "error", detail: "version Modrinth sans fichier" };
    base.latest = pf.filename;
    base.latestVersion = latest.version_number;
    base.latestType = latest.version_type;
    base.status = pf.filename === current.filename ? "up-to-date" : "update";
    return base;
  } catch (e) {
    if (e instanceof SlugNotFoundError) {
      const query = mod.prefix.replace(/[-_].*$/, "");
      const hits = await searchProjects(query);
      return {
        ...base,
        status: "slug-not-found",
        suggestions: hits.map((h) => `${h.slug} — ${h.title}`),
      };
    }
    return { ...base, status: "error", detail: (e as Error).message };
  }
}

export async function checkAll(
  config: SyncConfig,
  manifestMods: ManifestMod[],
): Promise<Report[]> {
  // Séquentiel : reste poli avec l'API Modrinth (pas de burst rate-limit).
  const reports: Report[] = [];
  for (const mod of config.mods) {
    reports.push(await checkOne(mod, manifestMods, config.gameVersion, config.loader));
  }
  return reports;
}
