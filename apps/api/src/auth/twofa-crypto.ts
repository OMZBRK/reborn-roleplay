import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';

/**
 * Chiffrement au repos des secrets TOTP (2FA).
 *
 * On utilise AES-256-GCM (chiffrement authentifie) via le module `crypto`
 * natif de Node — pas de dependance externe. Le format stocke est
 * auto-descriptif :
 *
 *     v1:<base64(iv)>:<base64(authTag)>:<base64(ciphertext)>
 *
 * Le prefixe `v1:` permet (a) de versionner le schema si on change d'algo
 * plus tard, et (b) de distinguer une valeur chiffree d'une ancienne valeur
 * en clair (cf. compat ascendante plus bas).
 *
 * GCM fournit l'integrite : toute alteration de l'IV, du tag ou du
 * ciphertext fait echouer `decipher.final()` (exception), donc un attaquant
 * qui aurait un acces ecriture a la DB ne peut pas trafiquer le secret sans
 * etre detecte.
 */

const PREFIX = 'v1';
const ALGORITHM = 'aes-256-gcm';
const IV_BYTES = 12; // nonce standard recommande pour GCM
const KEY_BYTES = 32; // AES-256

/**
 * Cle de dev hardcodee, utilisee UNIQUEMENT quand `REBORN_2FA_ENC_KEY` est
 * absente ET que NODE_ENV !== 'production'. Meme logique de garde que les
 * autres bypass de dev du repo (dev-login, secret play-token de fallback).
 * En prod, l'absence de cle leve une erreur explicite au boot.
 */
const DEV_FALLBACK_KEY_HEX =
  '0000000000000000000000000000000000000000000000000000000000000000';

/**
 * Parse une cle 32 bytes fournie en hex (64 chars) ou en base64.
 * Leve si le format/longueur ne correspond pas.
 */
function parseKey(raw: string): Buffer {
  const trimmed = raw.trim();
  if (/^[0-9a-fA-F]{64}$/.test(trimmed)) {
    return Buffer.from(trimmed, 'hex');
  }
  // Tente base64 (standard ou url-safe).
  const b64 = Buffer.from(trimmed, 'base64');
  if (b64.length === KEY_BYTES) {
    return b64;
  }
  throw new Error(
    `REBORN_2FA_ENC_KEY invalide : attendu 32 bytes en hex (64 caracteres) ` +
      `ou en base64. Genere une cle avec \`openssl rand -hex 32\`.`,
  );
}

/**
 * Charge et valide la cle de chiffrement depuis l'environnement.
 * A appeler a l'init du module (constructeur du service) pour fail-fast.
 */
export function loadEncryptionKey(): Buffer {
  const raw = process.env.REBORN_2FA_ENC_KEY?.trim();
  if (!raw) {
    if (process.env.NODE_ENV === 'production') {
      throw new Error(
        'REBORN_2FA_ENC_KEY manquante : requise en production pour chiffrer ' +
          'les secrets 2FA au repos. Genere-la avec `openssl rand -hex 32`.',
      );
    }
    // Dev/test : fallback explicite, jamais utilise en prod (garde ci-dessus).
    return Buffer.from(DEV_FALLBACK_KEY_HEX, 'hex');
  }
  const key = parseKey(raw);
  if (key.length !== KEY_BYTES) {
    throw new Error(
      `REBORN_2FA_ENC_KEY doit faire exactement ${KEY_BYTES} bytes ` +
        `(actuel : ${key.length}). Genere-la avec \`openssl rand -hex 32\`.`,
    );
  }
  return key;
}

/** True si la valeur stockee est au format chiffre `v1:...`. */
export function isEncrypted(stored: string): boolean {
  return stored.startsWith(`${PREFIX}:`);
}

/**
 * Chiffre un secret (TOTP base32) en `v1:<iv>:<tag>:<ciphertext>` (base64).
 */
export function encryptSecret(plaintext: string, key: Buffer): string {
  const iv = randomBytes(IV_BYTES);
  const cipher = createCipheriv(ALGORITHM, key, iv);
  const ciphertext = Buffer.concat([
    cipher.update(plaintext, 'utf8'),
    cipher.final(),
  ]);
  const authTag = cipher.getAuthTag();
  return [
    PREFIX,
    iv.toString('base64'),
    authTag.toString('base64'),
    ciphertext.toString('base64'),
  ].join(':');
}

/**
 * Dechiffre une valeur stockee.
 *
 * Compat ascendante : les lignes enregistrees avant l'introduction du
 * chiffrement sont du base32 en clair (pas de prefixe `v1:`). On les detecte
 * via {@link isEncrypted} et on les retourne telles quelles. Les nouveaux
 * secrets et la re-encryption opportuniste (cf. service) les migreront au
 * fil des verifications reussies.
 *
 * Si la valeur est chiffree et qu'elle a ete alteree (IV/tag/ciphertext),
 * `decipher.final()` leve — on laisse remonter l'exception (detection de
 * falsification GCM).
 */
export function decryptSecret(stored: string, key: Buffer): string {
  if (!isEncrypted(stored)) {
    // Legacy : valeur en clair pre-chiffrement, on la retourne directement.
    return stored;
  }
  const parts = stored.split(':');
  if (parts.length !== 4) {
    throw new Error('Secret 2FA chiffre malforme (nombre de segments).');
  }
  const [, ivB64, tagB64, ctB64] = parts;
  const iv = Buffer.from(ivB64, 'base64');
  const authTag = Buffer.from(tagB64, 'base64');
  const ciphertext = Buffer.from(ctB64, 'base64');

  const decipher = createDecipheriv(ALGORITHM, key, iv);
  decipher.setAuthTag(authTag);
  return Buffer.concat([
    decipher.update(ciphertext),
    decipher.final(), // leve si le tag ne valide pas (falsification / mauvaise cle)
  ]).toString('utf8');
}
