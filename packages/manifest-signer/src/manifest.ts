/**
 * Forme du manifest signe (cf PLAN_CONCEPTION_LAUNCHER.md §4.3).
 *
 * On serialise avec une cle SHA des fichiers, signee par la cle privee Ed25519,
 * et on attache la signature au manifest sous `signature` (hex).
 */

export interface ManifestFile {
  /** Chemin relatif au game directory (`%APPDATA%\RebornRoleplay\`). */
  path: string;
  /** Hash SHA-256 attendu, en hex minuscule. */
  sha256: string;
  /** Taille en octets. */
  size: number;
  /** URL CDN absolue. */
  url: string;
  /** True = bloquant si manquant. False = optionnel (resource pack alternatif). */
  required: boolean;
}

export interface UnsignedManifest {
  version: string;
  minecraftVersion: string;
  issuedAt: string;
  expiresAt: string;
  minLauncherVersion: string;
  files: ManifestFile[];
}

export interface SignedManifest extends UnsignedManifest {
  /** "ed25519:" + signature hex (cf §4.3). */
  signature: string;
}

/**
 * Octets canoniques signes (forme RFC 8785-like) :
 * - cles triees alphabetiquement a chaque niveau (matche le BTreeMap Rust)
 * - pas d'espaces
 * - sha256 en minuscule
 * - files tries par path ascendant
 *
 * UTF-8. Doit etre bit-pour-bit identique a la sortie de
 * `apps/launcher/src-tauri/src/manifest/verify.rs::canonical_bytes`.
 */
export function canonicalize(manifest: UnsignedManifest): Buffer {
  // Normalise les timestamps via Date.toISOString() pour avoir un format
  // bit-pour-bit stable (toujours `.SSSZ`, jamais `Z` sans ms ni offset
  // numerique). C'est aussi la forme que renvoie Prisma quand on lit ces
  // valeurs depuis Postgres, donc le round-trip sign → store → fetch →
  // verify reste identique.
  const normalized: UnsignedManifest = {
    version: manifest.version,
    minecraftVersion: manifest.minecraftVersion,
    issuedAt: new Date(manifest.issuedAt).toISOString(),
    expiresAt: new Date(manifest.expiresAt).toISOString(),
    minLauncherVersion: manifest.minLauncherVersion,
    files: manifest.files
      .slice()
      .sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0))
      .map((f) => ({
        path: f.path,
        sha256: f.sha256.toLowerCase(),
        size: f.size,
        url: f.url,
        required: f.required,
      })),
  };
  return Buffer.from(canonicalStringify(normalized), "utf8");
}

/** Stringify deterministe : cles triees a chaque profondeur, pas d'espaces. */
function canonicalStringify(value: unknown): string {
  if (value === null) return "null";
  if (Array.isArray(value)) {
    return "[" + value.map(canonicalStringify).join(",") + "]";
  }
  if (typeof value === "object") {
    const keys = Object.keys(value as Record<string, unknown>).sort();
    return (
      "{" +
      keys
        .map((k) => JSON.stringify(k) + ":" + canonicalStringify((value as Record<string, unknown>)[k]))
        .join(",") +
      "}"
    );
  }
  return JSON.stringify(value);
}
