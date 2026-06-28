import { randomUUID } from 'node:crypto';
import { join } from 'node:path';

/**
 * Configuration + helpers purs pour l'upload d'images (cf PLAN — pièces
 * jointes whitelist/tickets via `attachmentUrls`).
 *
 * Tout est isolé dans des fonctions pures et testables : validation du type
 * MIME, dérivation de l'extension, génération du nom de fichier aléatoire,
 * résolution du dossier de stockage et construction de l'URL publique.
 */

/** Seuls types d'images acceptés. Tout le reste → 400. */
export const ALLOWED_IMAGE_MIME_TYPES = [
  'image/png',
  'image/jpeg',
  'image/webp',
  'image/gif',
] as const;

export type AllowedImageMimeType = (typeof ALLOWED_IMAGE_MIME_TYPES)[number];

/**
 * MIME → extension whitelistée. On ne dérive JAMAIS l'extension du nom de
 * fichier client (path traversal / double extension). L'extension stockée
 * provient uniquement de cette table.
 */
const MIME_EXTENSION: Record<AllowedImageMimeType, string> = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/webp': 'webp',
  'image/gif': 'gif',
};

/** Plafond de taille par défaut (~8 MiB), surchargable via env. */
export const DEFAULT_MAX_UPLOAD_BYTES = 8 * 1024 * 1024;

/** Route publique (hors préfixe global `/v1`) où les fichiers sont servis. */
export const UPLOADS_ROUTE = '/uploads';

export function isAllowedImageMime(
  mime: string,
): mime is AllowedImageMimeType {
  return (ALLOWED_IMAGE_MIME_TYPES as readonly string[]).includes(mime);
}

/** Extension whitelistée pour un MIME accepté. Throw si non supporté. */
export function extensionForMime(mime: string): string {
  if (!isAllowedImageMime(mime)) {
    throw new Error(`Type de fichier non supporté: ${mime}`);
  }
  return MIME_EXTENSION[mime];
}

/**
 * Nom de fichier stocké = UUID v4 + extension whitelistée. Non devinable et
 * indépendant du nom de fichier fourni par le client.
 */
export function generateStoredFilename(mime: string): string {
  return `${randomUUID()}.${extensionForMime(mime)}`;
}

/**
 * Dossier de stockage : env `REBORN_UPLOAD_DIR`, sinon `<cwd>/uploads`
 * (l'API tourne avec cwd = apps/api → apps/api/uploads).
 */
export function resolveUploadDir(
  env: NodeJS.ProcessEnv = process.env,
): string {
  const fromEnv = env.REBORN_UPLOAD_DIR?.trim();
  return fromEnv ? fromEnv : join(process.cwd(), 'uploads');
}

/** Base publique de l'API (sans slash final), pour bâtir des URLs absolues. */
export function resolvePublicApiUrl(
  env: NodeJS.ProcessEnv = process.env,
): string {
  const base = env.REBORN_PUBLIC_API_URL?.trim() || 'http://localhost:3000';
  return base.replace(/\/+$/, '');
}

/** URL absolue publique d'un fichier stocké. */
export function buildPublicUrl(
  filename: string,
  env: NodeJS.ProcessEnv = process.env,
): string {
  return `${resolvePublicApiUrl(env)}${UPLOADS_ROUTE}/${filename}`;
}

/** Plafond de taille effectif (env `REBORN_UPLOAD_MAX_BYTES` ou défaut). */
export function maxUploadBytes(
  env: NodeJS.ProcessEnv = process.env,
): number {
  const raw = env.REBORN_UPLOAD_MAX_BYTES?.trim();
  if (!raw) return DEFAULT_MAX_UPLOAD_BYTES;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : DEFAULT_MAX_UPLOAD_BYTES;
}
