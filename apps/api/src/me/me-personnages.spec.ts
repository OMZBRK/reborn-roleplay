import { Logger } from '@nestjs/common';
import type { ConfigService } from '@nestjs/config';
import type { PrismaService } from '../prisma/prisma.service';
import {
  etatPersonnage,
  lireServeursPersonnages,
  vuePersonnage,
  type PersonnagePourVue,
} from './me-personnages';
import { MePersonnagesService } from './me-personnages.service';

// « Mes personnages » (CANDIDATURE_ET_DOUANE.md §4.6) : serveurs de prod
// seulement, personnages du compte seulement, dossier d'entrée seulement si la
// candidature est bien celle du compte.

const UUID_MC = '069a79f4-44e9-4726-a5be-fca90e38aaf5';

function ligne(sur: Partial<PersonnagePourVue> = {}): PersonnagePourVue {
  return {
    characterId: 'aaaaaaaa-0000-4000-8000-000000000001',
    playerId: 42,
    firstName: 'Kaito',
    lastName: 'Arashi',
    clan: null,
    village: 'Konohagakure',
    type: 'NINJA',
    rank: 'GENIN',
    sex: 'HOMME',
    age: 16,
    dead: false,
    deletedAt: null,
    playMinutes: 95,
    createdAt: new Date('2026-10-25T18:00:00Z'),
    lastSeenAt: new Date('2026-10-25T19:30:00Z'),
    firstSyncedAt: new Date('2026-10-25T18:00:05Z'),
    applicationId: null,
    application: null,
    ...sur,
  };
}

describe('lireServeursPersonnages', () => {
  it('vide : prod', () => {
    expect(lireServeursPersonnages(undefined)).toEqual({
      serveurs: ['prod'],
      avertissements: [],
    });
    expect(lireServeursPersonnages('  ').serveurs).toEqual(['prod']);
  });

  it('liste réglée : identifiants valides, sans doublon', () => {
    expect(lireServeursPersonnages('prod, prod-2,prod').serveurs).toEqual([
      'prod',
      'prod-2',
    ]);
  });

  it('identifiant invalide : ignoré et signalé ; aucun valide : prod', () => {
    const lu = lireServeursPersonnages('Prod Principal');
    expect(lu.serveurs).toEqual(['prod']);
    expect(lu.avertissements).toHaveLength(2);
  });
});

describe('vuePersonnage', () => {
  it('carte complète : N° sur cinq chiffres, dates ISO, état en vie', () => {
    expect(vuePersonnage(ligne(), 'u-kaito')).toEqual({
      characterId: 'aaaaaaaa-0000-4000-8000-000000000001',
      playerId: 42,
      playerNumber: '00042',
      firstName: 'Kaito',
      lastName: 'Arashi',
      clan: null,
      village: 'Konohagakure',
      type: 'NINJA',
      rank: 'GENIN',
      sex: 'HOMME',
      age: 16,
      state: 'ALIVE',
      playMinutes: 95,
      playMinutesSince: '2026-10-25T18:00:05.000Z',
      createdAt: '2026-10-25T18:00:00.000Z',
      lastSeenAt: '2026-10-25T19:30:00.000Z',
      fromApplication: false,
      dossier: null,
    });
  });

  it('personnage issu de la candidature du compte : dossier d’entrée', () => {
    const v = vuePersonnage(
      ligne({
        applicationId: 'app1',
        application: {
          userId: 'u-kaito',
          history: 'Né à Konoha…',
          objectives: 'Devenir chûnin.',
          oralDecidedAt: new Date('2026-10-24T19:20:00Z'),
        },
      }),
      'u-kaito',
    );
    expect(v.fromApplication).toBe(true);
    expect(v.dossier).toEqual({
      history: 'Né à Konoha…',
      objectives: 'Devenir chûnin.',
      oralDecidedAt: '2026-10-24T19:20:00.000Z',
    });
  });

  it('candidature d’un autre compte : jamais montrée', () => {
    const v = vuePersonnage(
      ligne({
        applicationId: 'app2',
        application: {
          userId: 'u-autre',
          history: 'Secret',
          objectives: 'Secret',
          oralDecidedAt: null,
        },
      }),
      'u-kaito',
    );
    expect(v.fromApplication).toBe(false);
    expect(v.dossier).toBeNull();
  });

  it('sans N° ni date de création : null', () => {
    const v = vuePersonnage(ligne({ playerId: null, createdAt: null }), 'u');
    expect(v.playerNumber).toBeNull();
    expect(v.createdAt).toBeNull();
  });
});

describe('etatPersonnage', () => {
  it('supprimé passe avant mort RP', () => {
    expect(etatPersonnage({ dead: false, deletedAt: null })).toBe('ALIVE');
    expect(etatPersonnage({ dead: true, deletedAt: null })).toBe('DEAD');
    expect(etatPersonnage({ dead: true, deletedAt: new Date() })).toBe(
      'DELETED',
    );
  });
});

describe('MePersonnagesService.mesPersonnages', () => {
  beforeAll(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
  });
  afterAll(() => jest.restoreAllMocks());

  function monter(
    env: Record<string, string | undefined>,
    lignes: PersonnagePourVue[],
    minecraftUuid: string | null = UUID_MC.replace(/-/g, ''),
  ) {
    const prisma = {
      user: {
        findUnique: jest.fn(() =>
          Promise.resolve(minecraftUuid === null ? null : { minecraftUuid }),
        ),
      },
      gameCharacter: {
        // L'argument est relu par les tests (filtre sur les serveurs).
        findMany: jest.fn((args: { where: Record<string, unknown> }) => {
          void args;
          return Promise.resolve(lignes);
        }),
      },
    };
    const config = { get: (k: string) => env[k] };
    const service = new MePersonnagesService(
      config as unknown as ConfigService,
      prisma as unknown as PrismaService,
    );
    return { service, prisma };
  }

  it('filtre sur la prod par défaut et sur les personnages du compte (ou de son compte Minecraft non rattaché)', async () => {
    const { service, prisma } = monter({}, [ligne()]);
    const r = await service.mesPersonnages('u-kaito');
    expect(r.characters).toHaveLength(1);
    expect(prisma.gameCharacter.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          serverId: { in: ['prod'] },
          OR: [
            { userId: 'u-kaito' },
            // Forme compacte en base de comptes : comparée en forme canonique.
            { userId: null, ownerMcUuid: UUID_MC },
          ],
        },
      }),
    );
  });

  it('jamais REBORN_SERVER_ID : un serveur par défaut réglé sur le dev ne montre pas le dev', async () => {
    const { service, prisma } = monter({ REBORN_SERVER_ID: 'dev' }, []);
    await service.mesPersonnages('u-kaito');
    expect(prisma.gameCharacter.findMany.mock.calls[0][0].where).toMatchObject({
      serverId: { in: ['prod'] },
    });
  });

  it('REBORN_CHARACTERS_SERVER_IDS : serveurs lus', async () => {
    const { service, prisma } = monter(
      { REBORN_CHARACTERS_SERVER_IDS: 'prod,prod-2' },
      [],
    );
    await service.mesPersonnages('u-kaito');
    expect(prisma.gameCharacter.findMany.mock.calls[0][0].where).toMatchObject({
      serverId: { in: ['prod', 'prod-2'] },
    });
  });

  it('ordre : création en jeu, la première synchronisation à défaut', async () => {
    const ancien = ligne({
      characterId: 'ancien',
      playerId: 3,
      createdAt: null,
      firstSyncedAt: new Date('2026-10-25T10:00:00Z'),
    });
    const recent = ligne({ characterId: 'recent', playerId: 50 });
    const { service } = monter({}, [recent, ancien]);
    const r = await service.mesPersonnages('u-kaito');
    expect(r.characters.map((c) => c.characterId)).toEqual([
      'ancien',
      'recent',
    ]);
  });

  it('panel (personnagesPourStaff) : tous les serveurs, chaque carte marquée de son serveur', async () => {
    const dev = { ...ligne({ characterId: 'dev-1' }), serverId: 'dev' };
    const { service, prisma } = monter(
      { REBORN_CHARACTERS_SERVER_IDS: 'dev' },
      [dev as PersonnagePourVue],
    );
    const r = await service.personnagesPourStaff('u-kaito');
    const where = prisma.gameCharacter.findMany.mock.calls[0][0].where;
    expect(where).not.toHaveProperty('serverId');
    expect(where).toMatchObject({ OR: [{ userId: 'u-kaito' }, { userId: null, ownerMcUuid: UUID_MC }] });
    expect(r.serveursLauncher).toEqual(['dev']);
    expect(r.characters.map((c) => [c.characterId, c.serverId])).toEqual([['dev-1', 'dev']]);
  });

  it('compte inconnu : liste vide, aucune lecture des personnages', async () => {
    const { service, prisma } = monter({}, [ligne()], null);
    expect(await service.mesPersonnages('u-x')).toEqual({ characters: [] });
    expect(prisma.gameCharacter.findMany).not.toHaveBeenCalled();
  });
});
