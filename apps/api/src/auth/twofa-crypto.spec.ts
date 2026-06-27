import { randomBytes } from 'node:crypto';
import {
  decryptSecret,
  encryptSecret,
  isEncrypted,
  loadEncryptionKey,
} from './twofa-crypto';

describe('twofa-crypto', () => {
  const key = randomBytes(32);
  // Secret TOTP base32 typique (160 bits).
  const secret = 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';

  describe('encrypt/decrypt round-trip', () => {
    it('chiffre puis dechiffre vers la valeur d origine', () => {
      const enc = encryptSecret(secret, key);
      expect(enc).not.toBe(secret);
      expect(isEncrypted(enc)).toBe(true);
      expect(decryptSecret(enc, key)).toBe(secret);
    });

    it('produit un format auto-descriptif v1:<iv>:<tag>:<ct>', () => {
      const enc = encryptSecret(secret, key);
      const parts = enc.split(':');
      expect(parts).toHaveLength(4);
      expect(parts[0]).toBe('v1');
    });

    it('utilise un IV aleatoire (deux chiffrements differents)', () => {
      expect(encryptSecret(secret, key)).not.toBe(encryptSecret(secret, key));
    });
  });

  describe('compat ascendante (legacy plaintext)', () => {
    it('retourne tel quel un secret en clair sans prefixe v1:', () => {
      expect(isEncrypted(secret)).toBe(false);
      expect(decryptSecret(secret, key)).toBe(secret);
    });
  });

  describe('detection de falsification (GCM)', () => {
    it('leve si le ciphertext est altere', () => {
      const enc = encryptSecret(secret, key);
      const parts = enc.split(':');
      // Flippe un bit du dernier byte du ciphertext.
      const ct = Buffer.from(parts[3], 'base64');
      ct[ct.length - 1] ^= 0x01;
      const tampered = [parts[0], parts[1], parts[2], ct.toString('base64')].join(
        ':',
      );
      expect(() => decryptSecret(tampered, key)).toThrow();
    });

    it('leve si l auth tag est altere', () => {
      const enc = encryptSecret(secret, key);
      const parts = enc.split(':');
      const tag = Buffer.from(parts[2], 'base64');
      tag[0] ^= 0xff;
      const tampered = [parts[0], parts[1], tag.toString('base64'), parts[3]].join(
        ':',
      );
      expect(() => decryptSecret(tampered, key)).toThrow();
    });

    it('leve avec une mauvaise cle', () => {
      const enc = encryptSecret(secret, key);
      expect(() => decryptSecret(enc, randomBytes(32))).toThrow();
    });
  });

  describe('loadEncryptionKey', () => {
    const ORIGINAL = process.env.REBORN_2FA_ENC_KEY;
    const ORIGINAL_ENV = process.env.NODE_ENV;
    afterEach(() => {
      if (ORIGINAL === undefined) delete process.env.REBORN_2FA_ENC_KEY;
      else process.env.REBORN_2FA_ENC_KEY = ORIGINAL;
      process.env.NODE_ENV = ORIGINAL_ENV;
    });

    it('charge une cle hex (64 chars)', () => {
      process.env.REBORN_2FA_ENC_KEY = randomBytes(32).toString('hex');
      expect(loadEncryptionKey()).toHaveLength(32);
    });

    it('charge une cle base64', () => {
      process.env.REBORN_2FA_ENC_KEY = randomBytes(32).toString('base64');
      expect(loadEncryptionKey()).toHaveLength(32);
    });

    it('leve sur une cle de mauvaise longueur', () => {
      process.env.REBORN_2FA_ENC_KEY = 'tropcourt';
      expect(() => loadEncryptionKey()).toThrow();
    });

    it('utilise le fallback dev quand absente hors production', () => {
      delete process.env.REBORN_2FA_ENC_KEY;
      process.env.NODE_ENV = 'development';
      expect(loadEncryptionKey()).toHaveLength(32);
    });

    it('leve quand absente en production', () => {
      delete process.env.REBORN_2FA_ENC_KEY;
      process.env.NODE_ENV = 'production';
      expect(() => loadEncryptionKey()).toThrow(/REBORN_2FA_ENC_KEY/);
    });
  });
});
