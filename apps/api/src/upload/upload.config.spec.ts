import {
  buildPublicUrl,
  DEFAULT_MAX_UPLOAD_BYTES,
  extensionForMime,
  generateStoredFilename,
  isAllowedImageMime,
  maxUploadBytes,
  resolvePublicApiUrl,
  resolveUploadDir,
} from './upload.config';

describe('upload.config', () => {
  describe('isAllowedImageMime', () => {
    it('accepte png/jpeg/webp/gif', () => {
      for (const mime of [
        'image/png',
        'image/jpeg',
        'image/webp',
        'image/gif',
      ]) {
        expect(isAllowedImageMime(mime)).toBe(true);
      }
    });

    it('rejette tout autre type', () => {
      for (const mime of [
        'image/svg+xml',
        'image/bmp',
        'application/pdf',
        'text/html',
        'application/octet-stream',
        '',
      ]) {
        expect(isAllowedImageMime(mime)).toBe(false);
      }
    });
  });

  describe('extensionForMime', () => {
    it('mappe chaque MIME accepté vers son extension whitelistée', () => {
      expect(extensionForMime('image/png')).toBe('png');
      expect(extensionForMime('image/jpeg')).toBe('jpg');
      expect(extensionForMime('image/webp')).toBe('webp');
      expect(extensionForMime('image/gif')).toBe('gif');
    });

    it('throw pour un MIME non supporté', () => {
      expect(() => extensionForMime('application/pdf')).toThrow();
      expect(() => extensionForMime('image/svg+xml')).toThrow();
    });
  });

  describe('generateStoredFilename', () => {
    it('produit <uuidv4>.<ext> avec la bonne extension', () => {
      const name = generateStoredFilename('image/png');
      expect(name).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.png$/,
      );
      expect(generateStoredFilename('image/jpeg').endsWith('.jpg')).toBe(true);
    });

    it('ne contient aucun séparateur de chemin', () => {
      const name = generateStoredFilename('image/webp');
      expect(name.includes('/')).toBe(false);
      expect(name.includes('\\')).toBe(false);
      expect(name.includes('..')).toBe(false);
    });

    it('génère un nom unique à chaque appel', () => {
      expect(generateStoredFilename('image/gif')).not.toBe(
        generateStoredFilename('image/gif'),
      );
    });

    it('throw pour un MIME non whitelisté (pas de fichier sans extension)', () => {
      expect(() => generateStoredFilename('application/zip')).toThrow();
    });
  });

  describe('resolveUploadDir', () => {
    it('respecte REBORN_UPLOAD_DIR si défini', () => {
      expect(resolveUploadDir({ REBORN_UPLOAD_DIR: '/data/up' })).toBe(
        '/data/up',
      );
    });

    it('tombe sur <cwd>/uploads sinon', () => {
      expect(resolveUploadDir({})).toContain('uploads');
    });
  });

  describe('resolvePublicApiUrl', () => {
    it('défaut http://localhost:3000', () => {
      expect(resolvePublicApiUrl({})).toBe('http://localhost:3000');
    });

    it('retire le(s) slash(es) final(aux)', () => {
      expect(
        resolvePublicApiUrl({ REBORN_PUBLIC_API_URL: 'https://api.x.fr//' }),
      ).toBe('https://api.x.fr');
    });
  });

  describe('buildPublicUrl', () => {
    it('construit base + /uploads/<filename>', () => {
      expect(
        buildPublicUrl('abc.png', {
          REBORN_PUBLIC_API_URL: 'https://api.reborn.fr',
        }),
      ).toBe('https://api.reborn.fr/uploads/abc.png');
    });
  });

  describe('maxUploadBytes', () => {
    it('défaut ~8 MiB', () => {
      expect(maxUploadBytes({})).toBe(DEFAULT_MAX_UPLOAD_BYTES);
    });

    it('respecte un override numérique valide', () => {
      expect(maxUploadBytes({ REBORN_UPLOAD_MAX_BYTES: '1048576' })).toBe(
        1048576,
      );
    });

    it('ignore une valeur invalide et garde le défaut', () => {
      expect(maxUploadBytes({ REBORN_UPLOAD_MAX_BYTES: 'nope' })).toBe(
        DEFAULT_MAX_UPLOAD_BYTES,
      );
      expect(maxUploadBytes({ REBORN_UPLOAD_MAX_BYTES: '-5' })).toBe(
        DEFAULT_MAX_UPLOAD_BYTES,
      );
    });
  });
});
