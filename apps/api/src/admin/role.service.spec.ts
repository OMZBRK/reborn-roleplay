import { Logger } from '@nestjs/common';
import { Role } from '@prisma/client';
import type { AuditService } from '../audit/audit.service';
import type { PrismaService } from '../prisma/prisma.service';
import { RoleService } from './role.service';

// Passage de PLAYER à un rôle supérieur au panel : la candidature n'attend plus
// de décision, sa voix suit la règle d'une décision finale (effacée 30 jours
// plus tard, Q2) au lieu d'être gardée sans limite.

function monter(cibleRole: Role, { oralARepasser = false } = {}) {
  const users: Record<
    string,
    { id: string; role: Role; minecraftUsername: string }
  > = {
    admin: { id: 'admin', role: Role.ADMIN, minecraftUsername: 'Admin' },
    cible: { id: 'cible', role: cibleRole, minecraftUsername: 'Kazuki' },
  };
  const prisma = {
    user: {
      findUnique: jest.fn(({ where }: { where: { id: string } }) =>
        Promise.resolve(users[where.id] ?? null),
      ),
      update: jest.fn(({ data }: { data: { role: Role } }) =>
        Promise.resolve({ ...users.cible, role: data.role }),
      ),
      // Levée conditionnelle de l'oral à repasser : une ligne si une exigence est en cours.
      updateMany: jest.fn(() =>
        Promise.resolve({ count: oralARepasser ? 1 : 0 }),
      ),
    },
    whitelistApplication: {
      updateMany: jest.fn(() => Promise.resolve({ count: 1 })),
    },
    douaneReservation: {
      updateMany: jest.fn(() => Promise.resolve({ count: 1 })),
    },
    $transaction: jest.fn((ops: Promise<unknown>[]) => Promise.all(ops)),
  };
  const audit = { log: jest.fn(() => Promise.resolve(undefined)) };
  return {
    service: new RoleService(
      prisma as unknown as PrismaService,
      audit as unknown as AuditService,
    ),
    prisma,
    audit,
  };
}

describe('RoleService — voix de la candidature', () => {
  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
  });
  afterEach(() => jest.restoreAllMocks());

  it('PLAYER → WHITELISTED : effacement de la voix programmé à 30 jours', async () => {
    const { service, prisma } = monter(Role.PLAYER);
    const res = await service.changeRoleByUserId(
      'admin',
      'cible',
      Role.WHITELISTED,
    );
    expect(res).toMatchObject({ role: Role.WHITELISTED, changed: true });
    expect(prisma.whitelistApplication.updateMany).toHaveBeenCalledWith({
      where: { userId: 'cible', voiceKey: { not: null }, voicePurgeAt: null },
      data: { voicePurgeAt: expect.any(Date) as Date },
    });
    const quand = (
      prisma.whitelistApplication.updateMany.mock.calls[0] as unknown as [
        { data: { voicePurgeAt: Date } },
      ]
    )[0].data.voicePurgeAt;
    const jours = (quand.getTime() - Date.now()) / (24 * 3600 * 1000);
    expect(jours).toBeGreaterThan(29.9);
    expect(jours).toBeLessThan(30.1);
  });

  it('PLAYER → WHITELISTED : la place de douane encore réservée est rendue', async () => {
    const { service, prisma } = monter(Role.PLAYER);
    await service.changeRoleByUserId('admin', 'cible', Role.WHITELISTED);
    expect(prisma.douaneReservation.updateMany).toHaveBeenCalledWith({
      where: { userId: 'cible', statut: 'BOOKED' },
      data: {
        statut: 'CANCELLED',
        annuleeAt: expect.any(Date) as Date,
        motifAnnulation: 'Compte accepté par changement de rôle',
      },
    });
  });

  it('retour à PLAYER : l’effacement programmé est levé sur une candidature en cours', async () => {
    const { service, prisma } = monter(Role.WHITELISTED);
    await service.changeRoleByUserId('admin', 'cible', Role.PLAYER);
    expect(prisma.whitelistApplication.updateMany).toHaveBeenCalledWith({
      where: {
        userId: 'cible',
        status: { in: ['PENDING', 'NEEDS_REVISION'] },
        voiceKey: { not: null },
        voicePurgeAt: { not: null },
      },
      data: { voicePurgeAt: null },
    });
    expect(prisma.douaneReservation.updateMany).not.toHaveBeenCalled();
  });

  it('entre deux rôles au-dessus de PLAYER : la candidature n’est pas touchée', async () => {
    const { service, prisma } = monter(Role.WHITELISTED);
    await service.changeRoleByUserId('admin', 'cible', Role.HELPER);
    expect(prisma.whitelistApplication.updateMany).not.toHaveBeenCalled();
    expect(prisma.douaneReservation.updateMany).not.toHaveBeenCalled();
  });
});

describe('RoleService — oral à repasser (01/11)', () => {
  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
  });
  afterEach(() => jest.restoreAllMocks());

  /** Levée de l'oral à repasser : écriture conditionnelle, jugée en base. */
  const levee = {
    where: { id: 'cible', oralRetakeRequestedAt: { not: null } },
    data: {
      oralPassedAt: expect.any(Date) as Date,
      oralRetakeRequestedAt: null,
      oralRetakeRequestedById: null,
      oralRetakeReason: null,
    },
  };

  it('PLAYER → WHITELISTED : la promotion manuelle lève l’oral exigé comme « Lever » (oral réussi, indicateur effacé), dans la même transaction', async () => {
    const { service, prisma, audit } = monter(Role.PLAYER, {
      oralARepasser: true,
    });
    await service.changeRoleByUserId(
      'admin',
      'cible',
      Role.WHITELISTED,
      'Accepté hors douane',
    );
    expect(prisma.user.updateMany).toHaveBeenCalledWith(levee);
    expect(prisma.user.update).toHaveBeenCalledWith({
      where: { id: 'cible' },
      data: { role: Role.WHITELISTED },
      select: { id: true, minecraftUsername: true, role: true },
    });
    // En tête de la transaction (avant le rôle), avec la voix et la place rendue.
    const ops = prisma.$transaction.mock.calls[0][0];
    expect(ops).toHaveLength(4);
    expect(ops[0]).toBe(prisma.user.updateMany.mock.results[0].value);
    expect(ops[1]).toBe(prisma.user.update.mock.results[0].value);
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        metadata: {
          from: Role.PLAYER,
          to: Role.WHITELISTED,
          motif: 'Accepté hors douane',
          oralARepasserLeve: true,
        },
      }),
    );
  });

  it('entre deux rôles au-dessus de PLAYER : une exigence posée entre-temps est levée aussi ; seul le rôle change sinon', async () => {
    const { service, prisma } = monter(Role.WHITELISTED);
    await service.changeRoleByUserId('admin', 'cible', Role.HELPER);
    expect(prisma.user.updateMany).toHaveBeenCalledWith(levee);
    expect(prisma.user.update).toHaveBeenCalledWith(
      expect.objectContaining({ data: { role: Role.HELPER } }),
    );
  });

  it('retour à PLAYER : l’oral ne se touche pas', async () => {
    const { service, prisma } = monter(Role.WHITELISTED);
    await service.changeRoleByUserId('admin', 'cible', Role.PLAYER);
    expect(prisma.user.updateMany).not.toHaveBeenCalled();
    expect(prisma.user.update).toHaveBeenCalledWith(
      expect.objectContaining({ data: { role: Role.PLAYER } }),
    );
  });
});

describe('RoleService — motif au journal', () => {
  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
  });
  afterEach(() => jest.restoreAllMocks());

  it('le motif du panel est inscrit avec le changement', async () => {
    const { service, audit } = monter(Role.WHITELISTED);
    await service.changeRoleByUserId(
      'admin',
      'cible',
      Role.HELPER,
      '  Rejoint l’équipe d’accueil  ',
    );
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        action: 'role.change',
        metadata: {
          from: Role.WHITELISTED,
          to: Role.HELPER,
          motif: 'Rejoint l’équipe d’accueil',
        },
      }),
    );
  });

  it('sans motif (bot Discord) : seulement les deux grades', async () => {
    const { service, audit } = monter(Role.WHITELISTED);
    await service.changeRoleByUserId('admin', 'cible', Role.HELPER);
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        metadata: { from: Role.WHITELISTED, to: Role.HELPER },
      }),
    );
  });

  it('refus en français, sans code de grade brut', async () => {
    const { service } = monter(Role.WHITELISTED);
    await expect(
      service.changeRoleByUserId('admin', 'cible', Role.OWNER, 'Essai'),
    ).rejects.toThrow('(Administrateur)');
  });
});
