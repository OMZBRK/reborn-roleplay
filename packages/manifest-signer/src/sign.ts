import {
  generateKeyPairSync,
  sign,
  verify,
  createPrivateKey,
  createPublicKey,
  type KeyObject,
} from "node:crypto";
import { canonicalize, type SignedManifest, type UnsignedManifest } from "./manifest.js";

/**
 * Genere un keypair Ed25519. Retourne le materiel sous deux formes :
 *  - PEM (PKCS#8 prive, SPKI public) — pour stocker en `secrets/*.pem`
 *  - hex 32 bytes — la forme dont le launcher Rust et les outils CLI ont
 *    besoin (ed25519 cle publique = 32 octets, signature = 64 octets)
 */
export function generateKeyPair(): {
  privatePem: string;
  publicPem: string;
  publicKeyHex: string;
} {
  const { privateKey, publicKey } = generateKeyPairSync("ed25519");
  const privatePem = privateKey
    .export({ type: "pkcs8", format: "pem" })
    .toString();
  const publicPem = publicKey.export({ type: "spki", format: "pem" }).toString();

  // Le format SPKI ajoute un en-tete ASN.1 de 12 octets devant les 32 octets de cle pure.
  const spkiDer = publicKey.export({ type: "spki", format: "der" });
  const rawPubKey = spkiDer.subarray(spkiDer.length - 32);
  const publicKeyHex = rawPubKey.toString("hex");

  return { privatePem, publicPem, publicKeyHex };
}

export function loadPrivateKeyFromPem(pem: string): KeyObject {
  return createPrivateKey({ key: pem, format: "pem" });
}

export function loadPublicKeyFromPem(pem: string): KeyObject {
  return createPublicKey({ key: pem, format: "pem" });
}

/**
 * Signe un manifest non signe et retourne le manifest signe (avec
 * `signature` = "ed25519:" + 64 octets hex).
 */
export function signManifest(
  manifest: UnsignedManifest,
  privateKey: KeyObject,
): SignedManifest {
  const message = canonicalize(manifest);
  const sig = sign(null, message, privateKey);
  return {
    ...manifest,
    signature: `ed25519:${sig.toString("hex")}`,
  };
}

/**
 * Verifie qu'un manifest signe est valide. Retourne `true` si OK,
 * `false` sinon — pas d'exception, on laisse l'appelant decider.
 */
export function verifyManifest(
  manifest: SignedManifest,
  publicKey: KeyObject,
): boolean {
  if (!manifest.signature?.startsWith("ed25519:")) return false;
  const sigHex = manifest.signature.slice("ed25519:".length);
  if (sigHex.length !== 128) return false;

  const sig = Buffer.from(sigHex, "hex");
  const { signature: _drop, ...unsigned } = manifest;
  const message = canonicalize(unsigned);
  try {
    return verify(null, message, publicKey, sig);
  } catch {
    return false;
  }
}
