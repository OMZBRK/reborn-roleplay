import { Logger, PayloadTooLargeException } from '@nestjs/common';
import { join } from 'node:path';
import { Readable } from 'node:stream';
import {
  dossierVoixPrive,
  estCleVoix,
  genererCleVoix,
  lireCorpsBrut,
  resolveVoixDir,
  typeAnnonce,
  typeReel,
  voixOctetsMax,
  VOIX_OCTETS_MAX_DEFAUT,
} from './voix.config';

// Voix de la candidature : fichier privé, types acceptés, signature d'octets,
// taille bornée, corps brut lu sans dépasser la borne.

const EBML = Buffer.from([0x1a, 0x45, 0xdf, 0xa3, 0x01, 0x02]);
const OGG = Buffer.from('OggS\u0000\u0002', 'latin1');

describe('voix — configuration', () => {
  it('type annoncé : audio/webm et audio/ogg, paramètres ignorés', () => {
    expect(typeAnnonce('audio/webm;codecs=opus')).toBe('audio/webm');
    expect(typeAnnonce('AUDIO/OGG')).toBe('audio/ogg');
    expect(typeAnnonce('audio/mpeg')).toBeNull();
    expect(typeAnnonce(undefined)).toBeNull();
  });

  it('type réel lu dans les octets, pas dans l’en-tête', () => {
    expect(typeReel(EBML)).toBe('audio/webm');
    expect(typeReel(OGG)).toBe('audio/ogg');
    expect(typeReel(Buffer.from('<html>'))).toBeNull();
  });

  it('clé de fichier : uuid v4 + extension, jamais une donnée du client', () => {
    const cle = genererCleVoix('audio/ogg');
    expect(estCleVoix(cle)).toBe(true);
    expect(cle.endsWith('.ogg')).toBe(true);
    expect(estCleVoix('../../etc/passwd')).toBe(false);
    expect(estCleVoix(`../${cle}`)).toBe(false);
  });

  it('dossier privé : <uploads>/.voix par défaut, REBORN_VOICE_DIR sinon', () => {
    expect(resolveVoixDir({ REBORN_UPLOAD_DIR: join('x', 'uploads') })).toBe(
      join('x', 'uploads', '.voix'),
    );
    expect(resolveVoixDir({ REBORN_VOICE_DIR: join('y', 'voix') })).toBe(
      join('y', 'voix'),
    );
  });

  it('dossier servi publiquement : REBORN_VOICE_DIR refusé, retour au dossier par défaut', () => {
    const uploads = join('x', 'uploads');
    expect(dossierVoixPrive(join(uploads, '.voix'), uploads)).toBe(true);
    expect(dossierVoixPrive(join(uploads, 'a', '.b', 'voix'), uploads)).toBe(
      true,
    );
    expect(dossierVoixPrive(join('y', 'voix'), uploads)).toBe(true);
    expect(dossierVoixPrive(join(uploads, 'voix'), uploads)).toBe(false);
    expect(dossierVoixPrive(uploads, uploads)).toBe(false);

    const erreur = jest
      .spyOn(Logger.prototype, 'error')
      .mockImplementation(() => undefined);
    expect(
      resolveVoixDir({
        REBORN_UPLOAD_DIR: uploads,
        REBORN_VOICE_DIR: join(uploads, 'voix'),
      }),
    ).toBe(join(uploads, '.voix'));
    expect(erreur).toHaveBeenCalled();
    erreur.mockRestore();
  });

  it('taille maximale réglable, défaut 1,5 Mo', () => {
    expect(voixOctetsMax({})).toBe(VOIX_OCTETS_MAX_DEFAUT);
    expect(voixOctetsMax({ WHITELIST_VOICE_MAX_BYTES: '2000000' })).toBe(
      2000000,
    );
    expect(voixOctetsMax({ WHITELIST_VOICE_MAX_BYTES: 'abc' })).toBe(
      VOIX_OCTETS_MAX_DEFAUT,
    );
  });
});

describe('voix — lecture du corps brut', () => {
  const requete = (morceaux: Buffer[], headers: Record<string, string> = {}) =>
    Object.assign(Readable.from(morceaux), { headers });

  it('assemble les morceaux', async () => {
    const buf = await lireCorpsBrut(requete([EBML, Buffer.alloc(10)]), 1000);
    expect(buf.length).toBe(EBML.length + 10);
  });

  it('413 si Content-Length annonce trop', async () => {
    await expect(
      lireCorpsBrut(requete([EBML], { 'content-length': '5000' }), 1000),
    ).rejects.toBeInstanceOf(PayloadTooLargeException);
  });

  it('413 si le flux dépasse la borne sans l’annoncer', async () => {
    await expect(
      lireCorpsBrut(requete([Buffer.alloc(600), Buffer.alloc(600)]), 1000),
    ).rejects.toBeInstanceOf(PayloadTooLargeException);
  });
});
