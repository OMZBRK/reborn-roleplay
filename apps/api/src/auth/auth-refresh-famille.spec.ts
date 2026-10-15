import { HttpException, UnauthorizedException } from '@nestjs/common';
import { Role } from '@prisma/client';
import { AuthService } from './auth.service';

/**
 * Familles de rotation des refresh tokens (15/10) : un replay ne coupe que sa famille, un refresh prolonge la
 * famille, `nouvelleFamille` en ouvre une sans consommer le token (ADMIN / OWNER).
 */
describe('AuthService.refresh — familles de sessions', () => {
  const user = { id: 'u1', minecraftUuid: 'uuid', role: Role.OWNER, banned: false } as never;
  let prisma: {
    session: { findUnique: jest.Mock; updateMany: jest.Mock; update: jest.Mock; create: jest.Mock };
  };
  let service: AuthService;

  function sessionEnBase(over: Record<string, unknown>) {
    prisma.session.findUnique.mockResolvedValue({
      id: 's1',
      userId: 'u1',
      familyId: 'famA',
      revokedAt: null,
      expiresAt: new Date(Date.now() + 3600_000),
      user,
      ...over,
    });
  }

  beforeEach(() => {
    prisma = {
      session: {
        findUnique: jest.fn(),
        updateMany: jest.fn().mockResolvedValue({ count: 1 }),
        update: jest.fn().mockResolvedValue({}),
        create: jest.fn().mockResolvedValue({}),
      },
    };
    const jwt = { signAsync: jest.fn().mockResolvedValue('jwt') };
    const config = { get: jest.fn((_k: string, d: unknown) => d) };
    service = new AuthService(prisma as never, jwt as never, config as never, {} as never, {} as never);
  });

  it('replay : ne révoque que la famille du token', async () => {
    sessionEnBase({ revokedAt: new Date() });
    await expect(service.refresh('x'.repeat(30), {})).rejects.toBeInstanceOf(UnauthorizedException);
    expect(prisma.session.updateMany).toHaveBeenCalledWith({
      where: { userId: 'u1', revokedAt: null, familyId: 'famA' },
      data: { revokedAt: expect.any(Date) },
    });
  });

  it('refresh normal : révoque l\'ancienne et reste dans la même famille', async () => {
    sessionEnBase({});
    await service.refresh('x'.repeat(30), {});
    expect(prisma.session.update).toHaveBeenCalledWith({ where: { id: 's1' }, data: { revokedAt: expect.any(Date) } });
    expect(prisma.session.create.mock.calls[0][0].data.familyId).toBe('famA');
  });

  it('session d\'avant le 15/10 (sans famille) : la famille devient son id', async () => {
    sessionEnBase({ familyId: null });
    await service.refresh('x'.repeat(30), {});
    expect(prisma.session.create.mock.calls[0][0].data.familyId).toBe('s1');
  });

  it('nouvelleFamille : ne consomme pas le token et ouvre une autre famille', async () => {
    sessionEnBase({});
    await service.refresh('x'.repeat(30), {}, { nouvelleFamille: true });
    expect(prisma.session.update).not.toHaveBeenCalled();
    const fam = prisma.session.create.mock.calls[0][0].data.familyId;
    expect(fam).toBeTruthy();
    expect(fam).not.toBe('famA');
  });

  it('nouvelleFamille : refusée hors ADMIN / OWNER', async () => {
    sessionEnBase({ user: { ...(user as object), role: Role.MODERATOR } });
    await expect(service.refresh('x'.repeat(30), {}, { nouvelleFamille: true })).rejects.toBeInstanceOf(HttpException);
    expect(prisma.session.create).not.toHaveBeenCalled();
  });
});
