import { Logger, NotFoundException } from '@nestjs/common';
import {
  existsSync,
  mkdtempSync,
  rmSync,
  utimesSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import type { Readable } from 'node:stream';
import type { AuditService } from '../audit/audit.service';
import type { PrismaService } from '../prisma/prisma.service';
import { genererCleVoix } from './voix.config';
import { WhitelistVoixService } from './whitelist-voix.service';

// Fichiers de voix : réécoute par le candidat (brouillon d'abord), purge qui ne
// vide une référence que si le fichier a bien disparu, et balayage des
// fichiers orphelins. Les fichiers sont écrits pour de vrai dans un dossier
// temporaire ; Prisma est simulé.

const USER_ID = '11111111-1111-4111-8111-111111111111';

async function lire(flux: Readable): Promise<Buffer> {
  const morceaux: Buffer[] = [];
  for await (const m of flux) morceaux.push(m as Buffer);
  return Buffer.concat(morceaux);
}

describe('WhitelistVoixService', () => {
  let dossier: string;
  const saved = process.env.REBORN_VOICE_DIR;

  const deposer = (contenu = 'voix', ilYaMs = 0) => {
    const cle = genererCleVoix('audio/webm');
    const chemin = join(dossier, cle);
    writeFileSync(chemin, contenu);
    if (ilYaMs > 0) {
      const t = new Date(Date.now() - ilYaMs);
      utimesSync(chemin, t, t);
    }
    return cle;
  };

  /** Ligne de brouillon simulée : `updatedAt` = dernière activité. */
  interface LigneBrouillon {
    id: string;
    voiceKey: string | null;
    voiceMimeType?: string | null;
    voiceDurationSec?: number | null;
    voiceUploadedAt?: Date | null;
    updatedAt: Date;
  }

  function monter(o: {
    brouillon?: Record<string, unknown> | null;
    app?: Record<string, unknown> | null;
    references?: string[];
    aPurger?: Array<{ id: string; voiceKey: string }>;
    /** Brouillons en base, lus par la purge des brouillons abandonnés. */
    brouillons?: LigneBrouillon[];
    /** Clés de voix déjà reprises par une candidature (envoi en cours). */
    reprises?: string[];
  }) {
    const references = o.references ?? [];
    const brouillons = o.brouillons ?? [];
    const prisma = {
      candidatureBrouillon: {
        findUnique: jest.fn(() => Promise.resolve(o.brouillon ?? null)),
        findMany: jest.fn(
          ({
            where,
          }: {
            where: {
              voiceKey: { in?: string[] };
              updatedAt?: { lte: Date };
            };
          }) => {
            if (where.voiceKey.in) {
              return Promise.resolve(
                where.voiceKey.in
                  .filter((k) => references.includes(k))
                  .map((voiceKey) => ({ voiceKey })),
              );
            }
            const lte = where.updatedAt?.lte;
            return Promise.resolve(
              brouillons
                .filter((b) => b.voiceKey !== null && lte && b.updatedAt <= lte)
                .map(({ id, voiceKey }) => ({ id, voiceKey })),
            );
          },
        ),
        // UPDATE … WHERE id, voiceKey et updatedAt : rien si le brouillon a bougé.
        updateMany: jest.fn(
          ({
            where,
            data,
          }: {
            where: { id: string; voiceKey: string; updatedAt: { lte: Date } };
            data: Partial<LigneBrouillon>;
          }) => {
            const b = brouillons.find(
              (x) =>
                x.id === where.id &&
                x.voiceKey === where.voiceKey &&
                x.updatedAt <= where.updatedAt.lte,
            );
            if (!b) return Promise.resolve({ count: 0 });
            Object.assign(b, data, { updatedAt: new Date() });
            return Promise.resolve({ count: 1 });
          },
        ),
        update: jest.fn(() => Promise.resolve({})),
      },
      whitelistApplication: {
        findUnique: jest.fn(() => Promise.resolve(o.app ?? null)),
        findMany: jest.fn(
          ({ where }: { where: { voiceKey: { in?: string[] } } }) =>
            Promise.resolve(where.voiceKey.in ? [] : (o.aPurger ?? [])),
        ),
        count: jest.fn(({ where }: { where: { voiceKey: string } }) =>
          Promise.resolve((o.reprises ?? []).includes(where.voiceKey) ? 1 : 0),
        ),
        update: jest.fn(() => Promise.resolve({})),
      },
    };
    const audit = { log: jest.fn(() => Promise.resolve(undefined)) };
    const service = new WhitelistVoixService(
      prisma as unknown as PrismaService,
      audit as unknown as AuditService,
    );
    return { service, prisma };
  }

  beforeEach(() => {
    dossier = mkdtempSync(join(tmpdir(), 'reborn-voix-svc-'));
    process.env.REBORN_VOICE_DIR = dossier;
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => {});
    jest.spyOn(Logger.prototype, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    jest.restoreAllMocks();
    rmSync(dossier, { recursive: true, force: true });
    if (saved === undefined) delete process.env.REBORN_VOICE_DIR;
    else process.env.REBORN_VOICE_DIR = saved;
  });

  it('réécoute : la voix du brouillon d’abord, sinon celle de la candidature', async () => {
    const duBrouillon = deposer('brouillon');
    const deLaCandidature = deposer('candidature');
    const a = monter({
      brouillon: { voiceKey: duBrouillon, voiceMimeType: 'audio/webm' },
      app: { voiceKey: deLaCandidature, voiceMimeType: 'audio/webm' },
    });
    const v = await a.service.ouvrirPourCandidat(USER_ID);
    expect((await lire(v.flux)).toString()).toBe('brouillon');
    expect(v.type).toBe('audio/webm');

    const b = monter({
      brouillon: null,
      app: { voiceKey: deLaCandidature, voiceMimeType: null },
    });
    const w = await b.service.ouvrirPourCandidat(USER_ID);
    expect((await lire(w.flux)).toString()).toBe('candidature');
    expect(w.type).toBe('audio/webm');
  });

  it('réécoute « Mon dossier » (source candidature) : la voix envoyée, même avec un brouillon', async () => {
    const duBrouillon = deposer('brouillon');
    const deLaCandidature = deposer('candidature');
    const a = monter({
      brouillon: { voiceKey: duBrouillon, voiceMimeType: 'audio/webm' },
      app: { voiceKey: deLaCandidature, voiceMimeType: 'audio/ogg' },
    });
    const v = await a.service.ouvrirPourCandidat(USER_ID, 'candidature');
    expect((await lire(v.flux)).toString()).toBe('candidature');
    expect(v.type).toBe('audio/ogg');
    const b = await a.service.ouvrirPourCandidat(USER_ID, 'brouillon');
    expect((await lire(b.flux)).toString()).toBe('brouillon');
    await expect(
      monter({
        brouillon: { voiceKey: duBrouillon, voiceMimeType: 'audio/webm' },
        app: null,
      }).service.ouvrirPourCandidat(USER_ID, 'candidature'),
    ).rejects.toThrow('Aucun enregistrement vocal.');
  });

  it('réécoute : 404 « aucun enregistrement » ou « effacé le … »', async () => {
    await expect(
      monter({}).service.ouvrirPourCandidat(USER_ID),
    ).rejects.toThrow('Aucun enregistrement vocal.');
    const err = await monter({
      app: {
        voiceKey: null,
        voiceDeletedAt: new Date('2026-11-24T20:00:00Z'),
      },
    })
      .service.ouvrirPourCandidat(USER_ID)
      .catch((e: unknown) => e);
    expect(err).toBeInstanceOf(NotFoundException);
    expect((err as Error).message).toMatch(/^Enregistrement effacé le 24\/11/);
  });

  it('purge : fichier orphelin de plus d’une heure effacé ; référencé ou récent, gardé', async () => {
    const orphelin = deposer('o', 2 * 3600 * 1000);
    const reference = deposer('r', 2 * 3600 * 1000);
    const recent = deposer('n');
    const { service } = monter({ references: [reference] });

    const n = await service.purger();

    expect(n).toBe(1);
    expect(existsSync(join(dossier, orphelin))).toBe(false);
    expect(existsSync(join(dossier, reference))).toBe(true);
    expect(existsSync(join(dossier, recent))).toBe(true);
  });

  it('purge : la référence n’est vidée que si le fichier a disparu', async () => {
    const cle = deposer();
    const { service, prisma } = monter({
      aPurger: [{ id: 'a1', voiceKey: cle }],
      references: [cle],
    });
    jest.spyOn(service, 'supprimer').mockResolvedValueOnce(false);
    await service.purger();
    expect(prisma.whitelistApplication.update).not.toHaveBeenCalled();

    await service.purger();
    expect(prisma.whitelistApplication.update).toHaveBeenCalledWith({
      where: { id: 'a1' },
      data: { voiceKey: null, voiceDeletedAt: expect.any(Date) as Date },
    });
  });

  describe('voix d’un brouillon abandonné (complément du 25/10)', () => {
    const JOUR = 24 * 3600 * 1000;
    const ilYa = (ms: number) => new Date(Date.now() - ms);

    it('30 jours sans activité sur le brouillon : voix effacée, brouillon gardé sans voix', async () => {
      const cle = deposer();
      const b: LigneBrouillon = {
        id: 'b1',
        voiceKey: cle,
        voiceMimeType: 'audio/webm',
        voiceDurationSec: 42,
        voiceUploadedAt: ilYa(40 * JOUR),
        updatedAt: ilYa(30 * JOUR + 60 * 1000),
      };
      const { service, prisma } = monter({ brouillons: [b] });

      const n = await service.purger();

      expect(n).toBe(1);
      expect(existsSync(join(dossier, cle))).toBe(false);
      expect(b).toMatchObject({
        voiceKey: null,
        voiceMimeType: null,
        voiceDurationSec: null,
        voiceUploadedAt: null,
      });
      // Critère : l'activité du brouillon (updatedAt), pas la date du dépôt.
      const lecture = (
        prisma.candidatureBrouillon.findMany.mock.calls as unknown as Array<
          [{ where: Record<string, unknown> }]
        >
      ).find(([a]) => 'updatedAt' in a.where);
      expect(lecture?.[0].where).toEqual({
        voiceKey: { not: null },
        updatedAt: { lte: expect.any(Date) as Date },
      });
      const limite = (lecture?.[0].where.updatedAt as { lte: Date }).lte;
      expect(Date.now() - limite.getTime()).toBeGreaterThanOrEqual(30 * JOUR);
      expect(Date.now() - limite.getTime()).toBeLessThan(30 * JOUR + 60 * 1000);
    });

    it('voix déposée il y a 40 jours mais brouillon modifié il y a 2 jours : gardée', async () => {
      const cle = deposer();
      const b: LigneBrouillon = {
        id: 'b1',
        voiceKey: cle,
        voiceUploadedAt: ilYa(40 * JOUR),
        updatedAt: ilYa(2 * JOUR),
      };
      const { service, prisma } = monter({
        brouillons: [b],
        references: [cle],
      });

      expect(await service.purger()).toBe(0);
      expect(existsSync(join(dossier, cle))).toBe(true);
      expect(b.voiceKey).toBe(cle);
      expect(prisma.candidatureBrouillon.updateMany).not.toHaveBeenCalled();
    });

    it('joueur revenu entre la lecture et l’écriture : rien n’est vidé ni effacé', async () => {
      const cle = deposer();
      const b: LigneBrouillon = {
        id: 'b1',
        voiceKey: cle,
        updatedAt: ilYa(45 * JOUR),
      };
      const { service, prisma } = monter({
        brouillons: [b],
        references: [cle],
      });
      // Lecture faite sur l'état ancien ; le joueur enregistre son brouillon juste après.
      prisma.candidatureBrouillon.findMany.mockImplementationOnce(() => {
        b.updatedAt = new Date();
        return Promise.resolve([{ id: 'b1', voiceKey: cle }]);
      });

      expect(await service.purger()).toBe(0);
      expect(existsSync(join(dossier, cle))).toBe(true);
      expect(b.voiceKey).toBe(cle);
    });

    it('nouveau dépôt entre la lecture et l’écriture : le nouveau fichier n’est jamais touché', async () => {
      const ancienne = deposer();
      const nouvelle = deposer();
      const b: LigneBrouillon = {
        id: 'b1',
        voiceKey: ancienne,
        updatedAt: ilYa(45 * JOUR),
      };
      const { service, prisma } = monter({
        brouillons: [b],
        references: [nouvelle],
      });
      prisma.candidatureBrouillon.findMany.mockImplementationOnce(() => {
        Object.assign(b, { voiceKey: nouvelle, updatedAt: new Date() });
        return Promise.resolve([{ id: 'b1', voiceKey: ancienne }]);
      });

      await service.purger();

      expect(b.voiceKey).toBe(nouvelle);
      expect(existsSync(join(dossier, nouvelle))).toBe(true);
    });

    it('voix déjà reprise par une candidature (envoi en cours) : référence du brouillon vidée, fichier gardé', async () => {
      const cle = deposer();
      const b: LigneBrouillon = {
        id: 'b1',
        voiceKey: cle,
        updatedAt: ilYa(31 * JOUR),
      };
      const { service } = monter({
        brouillons: [b],
        reprises: [cle],
        references: [cle],
      });

      expect(await service.purger()).toBe(0);
      expect(b.voiceKey).toBeNull();
      expect(existsSync(join(dossier, cle))).toBe(true);
    });

    it('effacement du fichier en échec : référence vidée quand même, le balayage des orphelins le reprendra', async () => {
      const cle = deposer();
      const b: LigneBrouillon = {
        id: 'b1',
        voiceKey: cle,
        updatedAt: ilYa(31 * JOUR),
      };
      const { service } = monter({ brouillons: [b] });
      jest.spyOn(service, 'supprimer').mockResolvedValueOnce(false);

      expect(await service.purger()).toBe(0);
      expect(b.voiceKey).toBeNull();
    });
  });
});
