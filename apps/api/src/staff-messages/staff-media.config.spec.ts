import {
  generateStaffMediaId,
  isStaffMediaId,
  resolveStaffMediaDir,
  signedStaffMediaUrl,
  sniffImageMime,
  staffMediaMime,
  verifyStaffMediaSignature,
} from './staff-media.config';

const env = {
  JWT_SECRET: 'secret-de-test',
  REBORN_PUBLIC_API_URL: 'https://api.test',
} as NodeJS.ProcessEnv;

describe('staff-media.config', () => {
  it('génère des identifiants valides et rejette les autres', () => {
    const id = generateStaffMediaId('image/png');
    expect(isStaffMediaId(id)).toBe(true);
    expect(staffMediaMime(id)).toBe('image/png');
    expect(isStaffMediaId('../etc/passwd')).toBe(false);
    expect(isStaffMediaId('abc.png')).toBe(false);
    expect(() => generateStaffMediaId('image/svg+xml')).toThrow();
  });

  it('reconnaît les octets des images acceptées', () => {
    expect(
      sniffImageMime(
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0]),
      ),
    ).toBe('image/png');
    expect(sniffImageMime(Buffer.from([0xff, 0xd8, 0xff, 0xe0]))).toBe(
      'image/jpeg',
    );
    expect(sniffImageMime(Buffer.from('GIF89a......', 'latin1'))).toBe(
      'image/gif',
    );
    expect(sniffImageMime(Buffer.from('RIFF\0\0\0\0WEBPVP8 ', 'latin1'))).toBe(
      'image/webp',
    );
    expect(
      sniffImageMime(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"/>')),
    ).toBeNull();
  });

  it('signe et vérifie une URL, refuse expirée ou altérée', () => {
    const id = generateStaffMediaId('image/jpeg');
    const now = 1_800_000_000;
    const url = new URL(signedStaffMediaUrl(id, 60, now, env));
    expect(url.origin).toBe('https://api.test');
    expect(url.pathname).toBe(`/v1/admin/discord/media/${id}`);
    const exp = Number(url.searchParams.get('exp'));
    const sig = url.searchParams.get('sig')!;
    expect(verifyStaffMediaSignature(id, exp, sig, now, env)).toBe(true);
    expect(verifyStaffMediaSignature(id, exp, sig, now + 61, env)).toBe(false);
    expect(verifyStaffMediaSignature(id, exp + 1, sig, now, env)).toBe(false);
    const other = generateStaffMediaId('image/jpeg');
    expect(verifyStaffMediaSignature(other, exp, sig, now, env)).toBe(false);
    expect(verifyStaffMediaSignature(id, exp, 'zz', now, env)).toBe(false);
  });

  it('range les médias dans un dossier à point sous les uploads par défaut', () => {
    expect(
      resolveStaffMediaDir({
        REBORN_UPLOAD_DIR: '/data/up',
      }).replace(/\\/g, '/'),
    ).toBe('/data/up/.staff-media');
    expect(
      resolveStaffMediaDir({
        REBORN_STAFF_MEDIA_DIR: '/x',
      }),
    ).toBe('/x');
  });
});
