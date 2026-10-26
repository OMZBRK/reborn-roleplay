import { ForbiddenException, Logger } from '@nestjs/common';
import { AppStatus, Role } from '@prisma/client';
import type { PrismaService } from '../prisma/prisma.service';
import { OralSlotsService } from './oral-slots.service';

// Anciens créneaux d'oral, encore servis à l'ancien launcher pendant la
// transition : réservables seulement une fois l'écrit accepté (§3.3), comme
// une place de douane.

function monter(app: Record<string, unknown> | null) {
  const prisma = {
    user: {
      findUnique: jest.fn(() =>
        Promise.resolve({
          role: Role.PLAYER,
          oralPassedAt: null,
          whitelistRejectedAt: null,
        }),
      ),
    },
    whitelistApplication: { findUnique: jest.fn(() => Promise.resolve(app)) },
    douaneReservation: { findFirst: jest.fn(() => Promise.resolve(null)) },
    whitelistOralSlot: {
      findFirst: jest.fn(() => Promise.resolve(null)),
      updateMany: jest.fn(() => Promise.resolve({ count: 1 })),
      findUnique: jest.fn(() => Promise.resolve({ id: 'slot1' })),
    },
  };
  return {
    service: new OralSlotsService(prisma as unknown as PrismaService),
    prisma,
  };
}

const app = (o: Record<string, unknown> = {}) => ({
  id: 'app1',
  status: AppStatus.PENDING,
  hrpStatus: AppStatus.APPROVED,
  rpStatus: AppStatus.APPROVED,
  oralStatus: AppStatus.PENDING,
  oralDecidedAt: null,
  reviewedAt: null,
  ...o,
});

describe('OralSlotsService.book', () => {
  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
  });
  afterEach(() => jest.restoreAllMocks());

  it('écrit en relecture : 403 avec la raison, aucun créneau pris', async () => {
    const { service, prisma } = monter(app({ rpStatus: AppStatus.PENDING }));
    const err = await service.book('u1', 'slot1').catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ForbiddenException);
    expect((err as Error).message).toContain('relecture');
    expect(prisma.whitelistOralSlot.updateMany).not.toHaveBeenCalled();
  });

  it('écrit accepté : le créneau est pris', async () => {
    const { service, prisma } = monter(app());
    await service.book('u1', 'slot1');
    expect(prisma.whitelistOralSlot.updateMany).toHaveBeenCalled();
  });
});
