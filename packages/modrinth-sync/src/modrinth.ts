// Client minimal de l'API Modrinth v2 (lecture seule).
// Doc : https://docs.modrinth.com/api/  — User-Agent obligatoire par leur politique.

const API = "https://api.modrinth.com/v2";
const UA = "reborn-roleplay/modrinth-sync (github.com/OMZBRK/reborn-roleplay)";

export interface ModrinthFile {
  filename: string;
  url: string;
  size: number;
  primary: boolean;
  hashes: { sha1?: string; sha512?: string };
}

export interface ModrinthVersion {
  id: string;
  version_number: string;
  date_published: string;
  game_versions: string[];
  loaders: string[];
  version_type: string; // release | beta | alpha
  files: ModrinthFile[];
}

export interface SearchHit {
  slug: string;
  title: string;
  downloads: number;
}

async function get(path: string): Promise<Response> {
  return fetch(`${API}${path}`, { headers: { "User-Agent": UA, Accept: "application/json" } });
}

export class SlugNotFoundError extends Error {
  constructor(public slug: string) {
    super(`Projet Modrinth introuvable : "${slug}"`);
  }
}

/**
 * Versions d'un projet compatibles avec un game_version + loader, triées de la
 * plus récente (date_published) à la plus ancienne. Lève {@link SlugNotFoundError}
 * si le slug n'existe pas (404).
 */
export async function listCompatibleVersions(
  slug: string,
  gameVersion: string,
  loader: string,
): Promise<ModrinthVersion[]> {
  const gv = encodeURIComponent(JSON.stringify([gameVersion]));
  const ld = encodeURIComponent(JSON.stringify([loader]));
  const res = await get(`/project/${encodeURIComponent(slug)}/version?game_versions=${gv}&loaders=${ld}`);
  if (res.status === 404) throw new SlugNotFoundError(slug);
  if (!res.ok) throw new Error(`Modrinth ${res.status} pour ${slug}: ${await res.text()}`);
  const versions = (await res.json()) as ModrinthVersion[];
  return versions.sort(
    (a, b) => new Date(b.date_published).getTime() - new Date(a.date_published).getTime(),
  );
}

/** La primary file d'une version (ou la première si aucune n'est marquée primary). */
export function primaryFile(v: ModrinthVersion): ModrinthFile | undefined {
  return v.files.find((f) => f.primary) ?? v.files[0];
}

/** Recherche de projets — utilisé pour suggérer un slug quand le mapping est faux. */
export async function searchProjects(query: string, limit = 5): Promise<SearchHit[]> {
  const facets = encodeURIComponent(JSON.stringify([["project_type:mod"]]));
  const res = await get(`/search?query=${encodeURIComponent(query)}&facets=${facets}&limit=${limit}`);
  if (!res.ok) return [];
  const body = (await res.json()) as { hits: SearchHit[] };
  return body.hits ?? [];
}
