import { randomUUID } from 'node:crypto';
import { isAbsolute, join, relative, resolve, sep } from 'node:path';
import { Logger, PayloadTooLargeException } from '@nestjs/common';
import { resolveUploadDir } from '../upload/upload.config';

/**
 * Enregistrement vocal de la candidature v2 (onglet 5, partie RP) —
 * CANDIDATURE_ET_DOUANE.md §2.3, §2.9, Q2.
 *
 * Fichier PRIVÉ : rangé dans `<REBORN_UPLOAD_DIR>/.voix` par défaut (même volume
 * Docker persistant que les uploads, comme `.staff-media`), jamais servi par le
 * `/uploads` public. Seuls l'écoutent le staff, par
 * GET /v1/admin/whitelist/:id/voix (JWT, WHITELIST_REVIEWER), et le candidat
 * lui-même, par GET /v1/whitelist/me/voix. Le fichier est effacé au retrait et
 * 30 jours après la décision finale.
 *
 * Fonctions pures, testées dans voix.config.spec.ts.
 */

/** Types acceptés : ceux de MediaRecorder en Opus. */
export const VOIX_TYPES = ['audio/webm', 'audio/ogg'] as const;
export type VoixType = (typeof VOIX_TYPES)[number];

const EXTENSION: Record<VoixType, string> = {
  'audio/webm': 'webm',
  'audio/ogg': 'ogg',
};
const TYPE_DE_EXTENSION: Record<string, VoixType> = {
  webm: 'audio/webm',
  ogg: 'audio/ogg',
};

/** Plafond par défaut : 1,5 Mo (Opus, une minute, avec de la marge). Réglable par `WHITELIST_VOICE_MAX_BYTES`. */
export const VOIX_OCTETS_MAX_DEFAUT = 1536 * 1024;
/** En dessous, l'enregistrement est vide ou coupé. */
export const VOIX_OCTETS_MIN = 1024;

/** Nom du fichier stocké : `<uuid v4>.<webm|ogg>`. Jamais dérivé d'une donnée du client. */
export const VOIX_CLE_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.(webm|ogg)$/;

export function estCleVoix(cle: string | null | undefined): cle is string {
  return typeof cle === 'string' && VOIX_CLE_RE.test(cle);
}

export function voixOctetsMax(env: NodeJS.ProcessEnv = process.env): number {
  const n = Number(env.WHITELIST_VOICE_MAX_BYTES?.trim());
  return Number.isInteger(n) && n >= VOIX_OCTETS_MIN
    ? n
    : VOIX_OCTETS_MAX_DEFAUT;
}

/** Type annoncé par l'en-tête Content-Type, sans paramètres (`audio/webm;codecs=opus` → `audio/webm`). */
export function typeAnnonce(
  contentType: string | undefined | null,
): VoixType | null {
  const base = (contentType ?? '').split(';')[0].trim().toLowerCase();
  return (VOIX_TYPES as readonly string[]).includes(base)
    ? (base as VoixType)
    : null;
}

/** Type réel, d'après la signature d'octets (le Content-Type du client ne suffit pas). */
export function typeReel(buf: Buffer): VoixType | null {
  // WebM / Matroska : en-tête EBML.
  if (
    buf.length >= 4 &&
    buf[0] === 0x1a &&
    buf[1] === 0x45 &&
    buf[2] === 0xdf &&
    buf[3] === 0xa3
  ) {
    return 'audio/webm';
  }
  // Ogg : « OggS ».
  if (buf.length >= 4 && buf.subarray(0, 4).toString('latin1') === 'OggS')
    return 'audio/ogg';
  return null;
}

export function genererCleVoix(type: VoixType): string {
  return `${randomUUID()}.${EXTENSION[type]}`;
}

export function typeDeCle(cle: string): VoixType {
  return TYPE_DE_EXTENSION[cle.slice(cle.lastIndexOf('.') + 1)] ?? 'audio/webm';
}

/**
 * Vrai si `dossier` n'est pas servi par le `/uploads` public : hors du dossier
 * des uploads, ou dans un sous-dossier dont un segment commence par un point
 * (main.ts et express.static ne servent pas ceux-là).
 */
export function dossierVoixPrive(dossier: string, uploadDir: string): boolean {
  const rel = relative(resolve(uploadDir), resolve(dossier));
  if (rel === '') return false;
  if (isAbsolute(rel) || rel === '..' || rel.startsWith(`..${sep}`))
    return true;
  return rel.split(/[\\/]+/).some((segment) => segment.startsWith('.'));
}

/**
 * Dossier privé des voix : `REBORN_VOICE_DIR`, sinon `<REBORN_UPLOAD_DIR>/.voix`.
 * Un `REBORN_VOICE_DIR` qui serait servi publiquement (sous les uploads, sans
 * segment à point) est refusé : retour au dossier par défaut, avec une erreur
 * au démarrage.
 */
export function resolveVoixDir(env: NodeJS.ProcessEnv = process.env): string {
  const defaut = join(resolveUploadDir(env), '.voix');
  const fromEnv = env.REBORN_VOICE_DIR?.trim();
  if (!fromEnv) return defaut;
  if (!dossierVoixPrive(fromEnv, resolveUploadDir(env))) {
    new Logger('VoixConfig').error(
      'REBORN_VOICE_DIR est servi publiquement par /uploads : ignoré, dossier par défaut utilisé.',
    );
    return defaut;
  }
  return fromEnv;
}

/**
 * Lit le corps brut de la requête (octets audio), borné à `max` octets. Le
 * corps JSON global de main.ts ne le touche pas (Content-Type audio/*).
 */
export async function lireCorpsBrut(
  req: AsyncIterable<Buffer | string> & {
    headers?: Record<string, string | string[] | undefined>;
  },
  max: number,
): Promise<Buffer> {
  const trop = () =>
    new PayloadTooLargeException(
      `Enregistrement trop lourd (${Math.floor(max / 1024)} Ko au plus). Refais-le, une minute au plus.`,
    );
  const annonce = Number(req.headers?.['content-length']);
  if (Number.isFinite(annonce) && annonce > max) throw trop();

  const morceaux: Buffer[] = [];
  let total = 0;
  for await (const morceau of req) {
    const b = typeof morceau === 'string' ? Buffer.from(morceau) : morceau;
    total += b.length;
    if (total > max) throw trop();
    morceaux.push(b);
  }
  return Buffer.concat(morceaux, total);
}
