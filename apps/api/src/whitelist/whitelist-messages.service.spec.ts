import { ForbiddenException } from '@nestjs/common';
import type { PrismaService } from '../prisma/prisma.service';
import type { WebhooksService } from '../webhooks/webhooks.service';
import { WhitelistMessagesService } from './whitelist-messages.service';

// Fil du candidat : fermé avec la décision finale (acceptée ou refusée), comme
// côté staff (maquette L8 « refusé » : une question ? ouvre un ticket).

function monter(status: string) {
  const prisma = {
    whitelistApplication: {
      findUnique: jest.fn(() => Promise.resolve({ id: 'app1', status })),
    },
    whitelistMessage: { create: jest.fn() },
    user: { findUnique: jest.fn() },
  };
  return {
    service: new WhitelistMessagesService(
      prisma as unknown as PrismaService,
      {} as WebhooksService,
    ),
    prisma,
  };
}

describe('WhitelistMessagesService.postMine — fil fermé avec la décision', () => {
  it('refusée : 403, le candidat passe par un ticket', async () => {
    const { service, prisma } = monter('REJECTED');
    const err = await service
      .postMine('u1', { content: 'Pourquoi ?' })
      .catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ForbiddenException);
    expect((err as Error).message).toBe(
      'Ta candidature est refusée : le fil est fermé. Une question ? Ouvre un ticket.',
    );
    expect(prisma.whitelistMessage.create).not.toHaveBeenCalled();
  });

  it('acceptée : 403, message accentué', async () => {
    await expect(
      monter('APPROVED').service.postMine('u1', { content: 'Merci' }),
    ).rejects.toThrow(
      'Ta candidature est acceptée. Pour toute question, ouvre un ticket.',
    );
  });
});
