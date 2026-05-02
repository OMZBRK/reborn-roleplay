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
 * Octets canoniques signes : on serialise les champs metadata + files dans un
 * ordre stable et sans signature, puis on hash-and-sign.
 *
 * Format : JSON canonique (cles triees, pas d'espaces) → UTF-8.
 */
export function canonicalize(manifest: UnsignedManifest): Buffer {
  const ordered = {
    version: manifest.version,
    minecraftVersion: manifest.minecraftVersion,
    issuedAt: manifest.issuedAt,
    expiresAt: manifest.expiresAt,
    minLauncherVersion: manifest.minLauncherVersion,
    files: manifest.files
      .slice()
      .sort((a, b) => a.path.localeCompare(b.path))
      .map((f) => ({
        path: f.path,
        sha256: f.sha256.toLowerCase(),
        size: f.size,
        url: f.url,
        required: f.required,
      })),
  };
  return Buffer.from(JSON.stringify(ordered), "utf8");
}
