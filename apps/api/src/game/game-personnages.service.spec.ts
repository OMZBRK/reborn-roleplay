import { Logger } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import type { PrismaService } from '../prisma/prisma.service';
import type { SyncPersonnagesJeuDto } from './dto/game-personnages.dto';
import { GamePersonnagesService } from './game-personnages.service';
import type { GameService } from './game.service';

// Synchronisation des personnages (CANDIDATURE_ET_DOUANE.md §4.5) : photo
// complète, idempotente, ordonnée par snapshotAt, un résultat par photo ; la
// candidature n'est rattachée que si elle est du même compte.

const UUID_MC = '069a79f4-44e9-4726-a5be-fca90e38aaf5';
const UUID_MC_AUTRE = '11111111-1111-4111-8111-111111111111';
const UUID_MC_SANS_COMPTE = '99999999-9999-4999-8999-999999999999';
const PERSO_A = 'aaaaaaaa-0000-4000-8000-000000000001';
const PERSO_B = 'bbbbbbbb-0000-4000-8000-000000000002';
const APP_KAITO = 'cccccccc-0000-4000-8000-000000000003';
const APP_AUTRE = 'dddddddd-0000-4000-8000-000000000004';
const MAINTENANT = new Date('2026-10-25T20:00:00Z');

type Ligne = Record<string, unknown> & {
  id: string;
  serverId: string;
  characterId: string;
  playerId: number | null;
  snapshotAt: Date;
};

function erreurUnique(cible: string[]) {
  return new Prisma.PrismaClientKnownRequestError('Unique constraint failed', {
    code: 'P2002',
    clientVersion: 'test',
    meta: { target: cible },
  });
}

/** Table GameCharacter en mémoire, avec ses deux clés uniques. */
function fausseBase() {
  const lignes: Ligne[] = [];
  let suivant = 1;
  const doublonNumero = (l: Partial<Ligne>, sauf?: string) =>
    l.playerId !== null &&
    l.playerId !== undefined &&
    lignes.some(
      (x) =>
        x.id !== sauf && x.serverId === l.serverId && x.playerId === l.playerId,
    );
  const gameCharacter = {
    findUnique: jest.fn(
      ({
        where,
      }: {
        where: {
          serverId_characterId: { serverId: string; characterId: string };
        };
      }) => {
        const k = where.serverId_characterId;
        const l = lignes.find(
          (x) => x.serverId === k.serverId && x.characterId === k.characterId,
        );
        return Promise.resolve(
          l
            ? { id: l.id, snapshotAt: l.snapshotAt, deletedAt: l.deletedAt }
            : null,
        );
      },
    ),
    updateMany: jest.fn(
      ({
        where,
        data,
      }: {
        where: { id: string; snapshotAt: { lte: Date } };
        data: Partial<Ligne>;
      }) => {
        const l = lignes.find(
          (x) => x.id === where.id && x.snapshotAt <= where.snapshotAt.lte,
        );
        if (!l) return Promise.resolve({ count: 0 });
        if (doublonNumero({ ...data, serverId: l.serverId }, l.id)) {
          return Promise.reject(erreurUnique(['serverId', 'playerId']));
        }
        Object.assign(l, data);
        return Promise.resolve({ count: 1 });
      },
    ),
    create: jest.fn(({ data }: { data: Ligne }) => {
      if (
        lignes.some(
          (x) =>
            x.serverId === data.serverId && x.characterId === data.characterId,
        )
      ) {
        return Promise.reject(erreurUnique(['serverId', 'characterId']));
      }
      if (doublonNumero(data)) {
        return Promise.reject(erreurUnique(['serverId', 'playerId']));
      }
      const l = { ...data, id: `gc${suivant++}`, firstSyncedAt: MAINTENANT };
      lignes.push(l);
      return Promise.resolve(l);
    }),
  };
  const prisma = {
    gameCharacter,
    whitelistApplication: {
      findUnique: jest.fn(({ where }: { where: { id: string } }) =>
        Promise.resolve(
          where.id === APP_KAITO
            ? { userId: 'u-kaito' }
            : where.id === APP_AUTRE
              ? { userId: 'u-autre' }
              : null,
        ),
      ),
    },
  };
  const game = {
    findUserByMcUuid: jest.fn((mc: string) =>
      Promise.resolve(
        mc === UUID_MC
          ? { id: 'u-kaito' }
          : mc === UUID_MC_AUTRE
            ? { id: 'u-autre' }
            : null,
      ),
    ),
  };
  const service = new GamePersonnagesService(
    prisma as unknown as PrismaService,
    game as unknown as GameService,
  );
  return { service, lignes, prisma, game };
}

function photo(sur: Record<string, unknown> = {}) {
  return {
    characterId: PERSO_A,
    ownerMcUuid: UUID_MC,
    playerId: 42,
    firstName: 'Kaito',
    lastName: 'Arashi',
    village: 'Konohagakure',
    rank: 'GENIN',
    sex: 'HOMME',
    age: 16,
    dead: false,
    playMinutes: 10,
    createdAt: '2026-10-25T18:00:00Z',
    lastSeenAt: '2026-10-25T19:00:00Z',
    snapshotAt: '2026-10-25T19:00:00Z',
    ...sur,
  };
}

const envoi = (
  ...characters: Record<string, unknown>[]
): SyncPersonnagesJeuDto => ({ characters });

describe('GamePersonnagesService.synchroniser', () => {
  beforeAll(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
  });
  afterAll(() => jest.restoreAllMocks());

  it('création : ligne écrite pour le serveur de l’en-tête, compte rattaché', async () => {
    const { service, lignes } = fausseBase();
    const r = await service.synchroniser(envoi(photo()), 'dev', MAINTENANT);
    expect(r).toEqual({
      ok: true,
      serveur: 'dev',
      resultats: [{ index: 0, characterId: PERSO_A, statut: 'cree' }],
    });
    expect(lignes).toHaveLength(1);
    expect(lignes[0]).toMatchObject({
      serverId: 'dev',
      characterId: PERSO_A,
      ownerMcUuid: UUID_MC,
      userId: 'u-kaito',
      playerId: 42,
      clan: null,
      type: 'NINJA',
      snapshotAt: new Date('2026-10-25T19:00:00Z'),
    });
  });

  it('même photo renvoyée (file de reprise) : maj, rien ne change', async () => {
    const { service, lignes } = fausseBase();
    await service.synchroniser(envoi(photo()), 'prod', MAINTENANT);
    const avant = { ...lignes[0] };
    const r = await service.synchroniser(envoi(photo()), 'prod', MAINTENANT);
    expect(r.resultats[0].statut).toBe('maj');
    expect(lignes).toHaveLength(1);
    expect(lignes[0]).toEqual(avant);
  });

  it('photo plus récente : mise à jour (mort RP, temps de jeu)', async () => {
    const { service, lignes } = fausseBase();
    await service.synchroniser(envoi(photo()), 'prod', MAINTENANT);
    const r = await service.synchroniser(
      envoi(
        photo({
          dead: true,
          playMinutes: 70,
          clan: 'Uchiha',
          snapshotAt: '2026-10-25T19:30:00Z',
        }),
      ),
      'prod',
      MAINTENANT,
    );
    expect(r.resultats[0].statut).toBe('maj');
    expect(lignes[0]).toMatchObject({
      dead: true,
      playMinutes: 70,
      clan: 'Uchiha',
    });
  });

  it('photo plus ancienne que celle en base : perime, rien n’est écrit', async () => {
    const { service, lignes, prisma } = fausseBase();
    await service.synchroniser(
      envoi(photo({ dead: true, snapshotAt: '2026-10-25T19:30:00Z' })),
      'prod',
      MAINTENANT,
    );
    const r = await service.synchroniser(
      envoi(photo({ dead: false, snapshotAt: '2026-10-25T19:00:00Z' })),
      'prod',
      MAINTENANT,
    );
    expect(r.resultats[0].statut).toBe('perime');
    expect(lignes[0].dead).toBe(true);
    expect(prisma.gameCharacter.updateMany).not.toHaveBeenCalled();
  });

  it('photo plus récente écrite entre la lecture et l’écriture : perime', async () => {
    const { service, prisma } = fausseBase();
    await service.synchroniser(envoi(photo()), 'prod', MAINTENANT);
    prisma.gameCharacter.updateMany.mockResolvedValueOnce({ count: 0 });
    const r = await service.synchroniser(
      envoi(photo({ snapshotAt: '2026-10-25T19:10:00Z' })),
      'prod',
      MAINTENANT,
    );
    expect(r.resultats[0].statut).toBe('perime');
  });

  it('même personnage sur deux serveurs : deux lignes distinctes (dev ≠ prod)', async () => {
    const { service, lignes } = fausseBase();
    await service.synchroniser(envoi(photo()), 'dev', MAINTENANT);
    await service.synchroniser(envoi(photo()), 'prod', MAINTENANT);
    expect(lignes.map((l) => l.serverId)).toEqual(['dev', 'prod']);
  });

  it('N° déjà porté par un autre personnage du serveur : conflit, le reste de l’envoi passe', async () => {
    const { service, lignes } = fausseBase();
    const r = await service.synchroniser(
      envoi(
        photo(),
        photo({ characterId: PERSO_B, firstName: 'Ren' }),
        photo({ characterId: PERSO_B, firstName: 'Ren', playerId: 43 }),
      ),
      'prod',
      MAINTENANT,
    );
    expect(r.resultats.map((x) => x.statut)).toEqual([
      'cree',
      'conflit',
      'cree',
    ]);
    expect(r.resultats[1].message).toBe(
      'playerId : le N° 00042 est déjà porté par un autre personnage de ce serveur.',
    );
    expect(lignes.map((l) => l.playerId)).toEqual([42, 43]);
  });

  it('création concurrente du même personnage : relue puis mise à jour', async () => {
    const { service, prisma, lignes } = fausseBase();
    // Première lecture : rien ; entre-temps, un autre envoi a créé la ligne.
    prisma.gameCharacter.findUnique.mockImplementationOnce(() => {
      lignes.push({
        id: 'gc-concurrent',
        serverId: 'prod',
        characterId: PERSO_A,
        playerId: 42,
        snapshotAt: new Date('2026-10-25T18:59:00Z'),
        deletedAt: null,
      });
      return Promise.resolve(null);
    });
    const r = await service.synchroniser(envoi(photo()), 'prod', MAINTENANT);
    expect(r.resultats[0].statut).toBe('maj');
    expect(lignes).toHaveLength(1);
    expect(lignes[0].firstName).toBe('Kaito');
  });

  it('photo invalide : refusée seule, les autres sont écrites', async () => {
    const { service, lignes } = fausseBase();
    const r = await service.synchroniser(
      envoi(
        photo({ sex: 'Homme' }),
        photo({ characterId: PERSO_B, playerId: 43 }),
      ),
      'prod',
      MAINTENANT,
    );
    expect(r.resultats[0]).toEqual({
      index: 0,
      characterId: PERSO_A,
      statut: 'refuse',
      message: 'sex : HOMME ou FEMME.',
    });
    expect(r.resultats[1].statut).toBe('cree');
    expect(lignes).toHaveLength(1);
  });

  it('photo datée de plus de 5 min dans le futur : refusée', async () => {
    const { service, lignes } = fausseBase();
    const r = await service.synchroniser(
      envoi(photo({ snapshotAt: '2026-10-25T20:06:00Z' })),
      'prod',
      MAINTENANT,
    );
    expect(r.resultats[0].statut).toBe('refuse');
    expect(r.resultats[0].message).toContain('snapshotAt');
    expect(lignes).toHaveLength(0);
  });

  it('candidature du même compte : rattachée', async () => {
    const { service, lignes } = fausseBase();
    const r = await service.synchroniser(
      envoi(photo({ applicationId: APP_KAITO.toUpperCase() })),
      'prod',
      MAINTENANT,
    );
    expect(r.resultats[0]).toEqual({
      index: 0,
      characterId: PERSO_A,
      statut: 'cree',
    });
    expect(lignes[0].applicationId).toBe(APP_KAITO);
  });

  it('candidature d’un autre compte ou introuvable : ignorée, avertissement', async () => {
    const { service, lignes } = fausseBase();
    const r = await service.synchroniser(
      envoi(
        photo({ applicationId: APP_AUTRE }),
        photo({
          characterId: PERSO_B,
          playerId: 43,
          applicationId: 'eeeeeeee-0000-4000-8000-000000000005',
        }),
      ),
      'prod',
      MAINTENANT,
    );
    for (const res of r.resultats) {
      expect(res.statut).toBe('cree');
      expect(res.avertissements).toEqual([
        "applicationId ignoré : candidature introuvable ou d'un autre compte.",
      ]);
    }
    expect(lignes.map((l) => l.applicationId)).toEqual([null, null]);
  });

  it('compte Minecraft sans compte Reborn : écrit sans compte, avertissement ; candidature jamais rattachée', async () => {
    const { service, lignes } = fausseBase();
    const r = await service.synchroniser(
      envoi(
        photo({ ownerMcUuid: UUID_MC_SANS_COMPTE, applicationId: APP_KAITO }),
      ),
      'prod',
      MAINTENANT,
    );
    expect(r.resultats[0].statut).toBe('cree');
    expect(r.resultats[0].avertissements).toHaveLength(2);
    expect(lignes[0]).toMatchObject({ userId: null, applicationId: null });
  });

  it('UUID du propriétaire en forme compacte : normalisé, une seule recherche par envoi', async () => {
    const { service, lignes, game } = fausseBase();
    await service.synchroniser(
      envoi(
        photo({ ownerMcUuid: UUID_MC.replace(/-/g, '').toUpperCase() }),
        photo({ characterId: PERSO_B, playerId: 43 }),
      ),
      'prod',
      MAINTENANT,
    );
    expect(lignes.map((l) => l.ownerMcUuid)).toEqual([UUID_MC, UUID_MC]);
    expect(game.findUserByMcUuid).toHaveBeenCalledTimes(1);
  });

  it('suppression : date posée une fois, gardée aux envois suivants', async () => {
    const { service, lignes } = fausseBase();
    await service.synchroniser(
      envoi(photo({ deleted: true, snapshotAt: '2026-10-25T19:00:00Z' })),
      'prod',
      MAINTENANT,
    );
    await service.synchroniser(
      envoi(photo({ deleted: true, snapshotAt: '2026-10-25T19:45:00Z' })),
      'prod',
      MAINTENANT,
    );
    expect(lignes[0].deletedAt).toEqual(new Date('2026-10-25T19:00:00Z'));
  });

  it('erreur de base inattendue : remonte (le plugin renverra tout l’envoi)', async () => {
    const { service, prisma } = fausseBase();
    prisma.gameCharacter.findUnique.mockRejectedValueOnce(
      new Error('base indisponible'),
    );
    await expect(
      service.synchroniser(envoi(photo()), 'prod', MAINTENANT),
    ).rejects.toThrow('base indisponible');
  });
});
