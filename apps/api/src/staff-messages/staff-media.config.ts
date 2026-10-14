import { createHmac, randomUUID, timingSafeEqual } from 'node:crypto';
import { join } from 'node:path';
import { resolvePublicApiUrl, resolveUploadDir } from '../upload/upload.config';

/**
 * Images des messages Discord rédigés dans le panel (module staff-messages).
 *
 * Elles NE sont PAS servies par le `/uploads` public : stockage dans un dossier
 * à point (`<REBORN_UPLOAD_DIR>/.staff-media` par défaut — `express.static`
 * ignore les dossiers à point, et main.ts bloque en plus ce chemin), donc
 * persisté par le même volume Docker que les uploads. Le panel les affiche via
 * des URL signées à durée limitée (`GET /v1/admin/discord/media/:id?exp=&sig=`),
 * le bot les reçoit en base64 dans le webhook signé HMAC.
 *
 * Fonctions pures, testées dans staff-media.config.spec.ts.
 */

/** Plafond par image : sous la limite Discord de 10 Mo par pièce jointe. */
export const STAFF_MEDIA_MAX_BYTES = 8 * 1024 * 1024;
/** Plafond cumulé des images d'un message envoyé au bot. */
export const STAFF_MEDIA_MAX_TOTAL_BYTES = 25 * 1024 * 1024;
/** Durée de validité d'une URL signée (secondes). */
export const STAFF_MEDIA_URL_TTL_S = 60 * 60;

const MIME_EXT: Record<string, string> = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/webp': 'webp',
  'image/gif': 'gif',
};
const EXT_MIME: Record<string, string> = {
  png: 'image/png',
  jpg: 'image/jpeg',
  webp: 'image/webp',
  gif: 'image/gif',
};

/** Identifiant d'un média = nom du fichier stocké : `<uuid v4>.<ext>`. */
export const STAFF_MEDIA_ID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(png|jpg|webp|gif)$/;

export function isStaffMediaId(id: string): boolean {
  return STAFF_MEDIA_ID_RE.test(id);
}

export function staffMediaExtension(mime: string): string | null {
  return MIME_EXT[mime] ?? null;
}

export function staffMediaMime(id: string): string {
  const ext = id.slice(id.lastIndexOf('.') + 1);
  return EXT_MIME[ext] ?? 'application/octet-stream';
}

export function generateStaffMediaId(mime: string): string {
  const ext = staffMediaExtension(mime);
  if (!ext) throw new Error(`Type non supporté : ${mime}`);
  return `${randomUUID()}.${ext}`;
}

/**
 * Vérifie la signature d'octets réelle (magic number) : le MIME du client ne
 * suffit pas. Retourne le MIME détecté ou null.
 */
export function sniffImageMime(buf: Buffer): string | null {
  if (
    buf.length >= 8 &&
    buf
      .subarray(0, 8)
      .equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))
  ) {
    return 'image/png';
  }
  if (buf.length >= 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff)
    return 'image/jpeg';
  if (
    buf.length >= 6 &&
    /^GIF8[79]a$/.test(buf.subarray(0, 6).toString('latin1'))
  )
    return 'image/gif';
  if (
    buf.length >= 12 &&
    buf.subarray(0, 4).toString('latin1') === 'RIFF' &&
    buf.subarray(8, 12).toString('latin1') === 'WEBP'
  ) {
    return 'image/webp';
  }
  return null;
}

export function resolveStaffMediaDir(
  env: NodeJS.ProcessEnv = process.env,
): string {
  const fromEnv = env.REBORN_STAFF_MEDIA_DIR?.trim();
  return fromEnv ? fromEnv : join(resolveUploadDir(env), '.staff-media');
}

/** Secret de signature : dédié s'il existe, sinon dérivé de JWT_SECRET (séparation de domaine). */
function signingKey(env: NodeJS.ProcessEnv): string {
  const key = env.REBORN_MEDIA_SIGNING_SECRET?.trim() || env.JWT_SECRET?.trim();
  if (!key)
    throw new Error(
      'Ni REBORN_MEDIA_SIGNING_SECRET ni JWT_SECRET : URLs de médias impossibles à signer.',
    );
  return key;
}

export function signStaffMedia(
  id: string,
  exp: number,
  env: NodeJS.ProcessEnv = process.env,
): string {
  return createHmac('sha256', signingKey(env))
    .update(`staff-media|${id}|${exp}`)
    .digest('hex');
}

export function verifyStaffMediaSignature(
  id: string,
  exp: number,
  sig: string,
  nowS: number = Math.floor(Date.now() / 1000),
  env: NodeJS.ProcessEnv = process.env,
): boolean {
  if (!isStaffMediaId(id) || !Number.isInteger(exp) || exp < nowS) return false;
  if (!/^[0-9a-f]{64}$/.test(sig)) return false;
  const expected = signStaffMedia(id, exp, env);
  return timingSafeEqual(Buffer.from(expected, 'hex'), Buffer.from(sig, 'hex'));
}

/** URL absolue signée, valable `ttlS` secondes. */
export function signedStaffMediaUrl(
  id: string,
  ttlS: number = STAFF_MEDIA_URL_TTL_S,
  nowS: number = Math.floor(Date.now() / 1000),
  env: NodeJS.ProcessEnv = process.env,
): string {
  const exp = nowS + ttlS;
  const sig = signStaffMedia(id, exp, env);
  return `${resolvePublicApiUrl(env)}/v1/admin/discord/media/${id}?exp=${exp}&sig=${sig}`;
}
