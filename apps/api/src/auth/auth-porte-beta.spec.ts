import { HttpException, Logger } from '@nestjs/common';
import { Role } from '@prisma/client';
import {
  AuthService,
  estAdmisMalgreLaPorte,
  lireListeBeta,
} from './auth.service';

/**
 * Porte de la beta fermée (LAUNCHER_BETA_GATE) et comptes de test admis
 * malgré elle (LAUNCHER_BETA_ALLOW) : UUID Minecraft seulement, et la porte
 * s'applique aussi au rafraîchissement des sessions.
 */
const UUID = '069a79f4-44e9-4726-a5be-fca90e38aaf5';

describe('LAUNCHER_BETA_ALLOW', () => {
  it('UUID avec ou sans tirets, sans casse ; pseudos et entrées invalides ignorés', () => {
    const env = {
      LAUNCHER_BETA_ALLOW: ` ${UUID.toUpperCase()} , Notch, 1234, `,
    } as NodeJS.ProcessEnv;
    expect(lireListeBeta(env)).toEqual({
      uuids: new Set([UUID.replace(/-/g, '')]),
      ignorees: 2,
    });
    expect(estAdmisMalgreLaPorte({ minecraftUuid: UUID }, env)).toBe(true);
    expect(
      estAdmisMalgreLaPorte({ minecraftUuid: UUID.replace(/-/g, '') }, env),
    ).toBe(true);
    // Un pseudo listé n'admet personne (il change et se reprend).
    expect(
      estAdmisMalgreLaPorte(
        { minecraftUuid: '00000000-0000-4000-8000-000000000000' },
        { LAUNCHER_BETA_ALLOW: 'Notch' },
      ),
    ).toBe(false);
    expect(estAdmisMalgreLaPorte({ minecraftUuid: UUID }, {})).toBe(false);
  });
});

describe('AuthService.refresh — porte de la beta', () => {
  const saved = {
    gate: process.env.LAUNCHER_BETA_GATE,
    allow: process.env.LAUNCHER_BETA_ALLOW,
  };
  let prisma: {
    session: {
      findUnique: jest.Mock;
      update: jest.Mock;
      create: jest.Mock;
      updateMany: jest.Mock;
    };
  };
  let service: AuthService;

  function sessionDe(role: Role) {
    prisma.session.findUnique.mockResolvedValue({
      id: 's1',
      userId: 'u1',
      familyId: 'famA',
      revokedAt: null,
      expiresAt: new Date(Date.now() + 3600_000),
      user: { id: 'u1', minecraftUuid: UUID, role, banned: false },
    });
  }

  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
    prisma = {
      session: {
        findUnique: jest.fn(),
        update: jest.fn().mockResolvedValue({}),
        create: jest.fn().mockResolvedValue({}),
        updateMany: jest.fn().mockResolvedValue({ count: 1 }),
      },
    };
    const jwt = { signAsync: jest.fn().mockResolvedValue('jwt') };
    const config = { get: jest.fn((_k: string, d: unknown) => d) };
    process.env.LAUNCHER_BETA_GATE = 'helper';
    delete process.env.LAUNCHER_BETA_ALLOW;
    service = new AuthService(
      prisma as never,
      jwt as never,
      config as never,
      {} as never,
      {} as never,
    );
  });

  afterEach(() => {
    jest.restoreAllMocks();
    if (saved.gate === undefined) delete process.env.LAUNCHER_BETA_GATE;
    else process.env.LAUNCHER_BETA_GATE = saved.gate;
    if (saved.allow === undefined) delete process.env.LAUNCHER_BETA_ALLOW;
    else process.env.LAUNCHER_BETA_ALLOW = saved.allow;
  });

  it('compte sous la porte et plus sur la liste : refus 403, aucune session neuve', async () => {
    sessionDe(Role.PLAYER);
    await expect(service.refresh('x'.repeat(30), {})).rejects.toBeInstanceOf(
      HttpException,
    );
    expect(prisma.session.create).not.toHaveBeenCalled();
  });

  it('compte de test listé par son UUID : rafraîchi', async () => {
    process.env.LAUNCHER_BETA_ALLOW = UUID;
    sessionDe(Role.PLAYER);
    await service.refresh('x'.repeat(30), {});
    expect(prisma.session.create).toHaveBeenCalled();
  });

  it('staff au-dessus de la porte : rafraîchi', async () => {
    sessionDe(Role.HELPER);
    await service.refresh('x'.repeat(30), {});
    expect(prisma.session.create).toHaveBeenCalled();
  });

  it('au démarrage, alerte pour les entrées ignorées', () => {
    const warn = jest
      .spyOn(Logger.prototype, 'warn')
      .mockImplementation(() => undefined);
    process.env.LAUNCHER_BETA_ALLOW = 'Notch';
    new AuthService(
      prisma as never,
      { signAsync: jest.fn() } as never,
      { get: jest.fn() } as never,
      {} as never,
      {} as never,
    );
    expect(warn).toHaveBeenCalledWith(
      expect.stringContaining('1 entrée(s) ignorée(s)'),
    );
  });
});
