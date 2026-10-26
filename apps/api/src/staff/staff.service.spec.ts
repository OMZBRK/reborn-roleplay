import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Logger,
} from '@nestjs/common';
import { AppStatus, Role } from '@prisma/client';
import type { AuditService } from '../audit/audit.service';
import type { PrismaService } from '../prisma/prisma.service';
import type { WebhooksService } from '../webhooks/webhooks.service';
import type { WhitelistMessagesService } from '../whitelist/whitelist-messages.service';
import type { WhitelistVoixService } from '../whitelist/whitelist-voix.service';
import {
  fusionnerCommentaires,
  messageDecisionEcrit,
  StaffService,
  STATUT_BOT_ECRIT_ACCEPTE,
} from './staff.service';

// Décision de l'écrit (CANDIDATURE_ET_DOUANE.md §1.3, §2.9) : HRP + RP validés
// = « écrit accepté » (statut PENDING + writtenApprovedAt, réserve ta douane) ;
// la promotion WHITELISTED attend l'oral ; l'ancienne décision globale passe par
// là et ne donne plus la whitelist d'office.

const APP_ID = '22222222-2222-4222-8222-222222222222';
const JOUEUR = '11111111-1111-4111-8111-111111111111';
const STAFF = '44444444-4444-4444-8444-444444444444';

function monter(
  app: Record<string, unknown>,
  user: { role: Role; oralPassedAt: Date | null },
) {
  const tx = {
    $queryRaw: jest.fn(() => Promise.resolve([])),
    whitelistApplication: {
      findUnique: jest.fn(() =>
        Promise.resolve({
          ...app,
          user: { id: JOUEUR, ...user },
        }),
      ),
      update: jest.fn(({ data }: { data: Record<string, unknown> }) =>
        Promise.resolve({
          ...app,
          ...data,
        }),
      ),
    },
    douaneReservation: {
      updateMany: jest.fn(() => Promise.resolve({ count: 1 })),
    },
    user: {
      update: jest.fn(() => Promise.resolve({})),
      updateMany: jest.fn(() =>
        Promise.resolve({
          count: user.role === Role.PLAYER ? 1 : 0,
        }),
      ),
    },
  };
  const prisma = {
    $transaction: jest.fn((fn: (t: typeof tx) => Promise<unknown>) =>
      Promise.resolve(fn(tx)),
    ),
    user: {
      findUnique: jest.fn(() =>
        Promise.resolve({
          id: STAFF,
          discordUsername: 'Ayame',
          minecraftUsername: 'Ayame',
        }),
      ),
    },
  };
  const webhooks = { statusUpdate: jest.fn(() => Promise.resolve(undefined)) };
  const audit = { log: jest.fn(() => Promise.resolve(undefined)) };
  const messages = {
    postSystemMessage: jest.fn(() => Promise.resolve(undefined)),
  };
  const voix = { supprimer: jest.fn(() => Promise.resolve(undefined)) };
  const service = new StaffService(
    prisma as unknown as PrismaService,
    webhooks as unknown as WebhooksService,
    audit as unknown as AuditService,
    messages as unknown as WhitelistMessagesService,
    voix as unknown as WhitelistVoixService,
  );
  return { service, tx, webhooks, messages, audit };
}

const base = {
  id: APP_ID,
  userId: JOUEUR,
  status: AppStatus.PENDING,
  hrpStatus: AppStatus.APPROVED,
  rpStatus: AppStatus.PENDING,
  oralStatus: AppStatus.PENDING,
  writtenApprovedAt: null,
  writtenStaffNotes: null,
  reviewNotes: null,
  reviewedBy: null,
  voiceKey: '55555555-5555-4555-8555-555555555555.webm',
  voicePurgeAt: null,
  discordThreadId: null,
  discordMessageId: 'msg-1',
};

describe('StaffService — décision de l’écrit', () => {
  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => {});
  });
  afterEach(() => jest.restoreAllMocks());

  it('HRP + RP validés : écrit accepté, statut PENDING, pas de promotion, « réserve ta douane »', async () => {
    const { service, tx, messages, webhooks } = monter(base, {
      role: Role.PLAYER,
      oralPassedAt: null,
    });
    const res = await service.decideWhitelistPart(
      APP_ID,
      {
        part: 'RP',
        status: AppStatus.APPROVED,
        staffNotes: 'Creuser le passé du père.',
      },
      { userId: STAFF },
    );

    const data = tx.whitelistApplication.update.mock.calls[0][0].data;
    expect(data).toMatchObject({
      rpStatus: AppStatus.APPROVED,
      status: AppStatus.PENDING,
      writtenStaffNotes: 'Creuser le passé du père.',
      reviewedBy: STAFF,
    });
    expect(data.writtenApprovedAt).toBeInstanceOf(Date);
    expect(data).not.toHaveProperty('voicePurgeAt');
    expect(tx.user.updateMany).not.toHaveBeenCalled();
    expect(res.status).toBe(AppStatus.PENDING);
    expect(messages.postSystemMessage).toHaveBeenCalledWith(
      APP_ID,
      '✅ Écrit accepté. Réserve ta séance de douane dans le launcher.',
    );
    expect(webhooks.statusUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ status: STATUT_BOT_ECRIT_ACCEPTE }),
    );
  });

  it('oral déjà passé sur ce compte : l’écrit validé donne la whitelist (PLAYER seulement)', async () => {
    const { service, tx } = monter(base, {
      role: Role.PLAYER,
      oralPassedAt: new Date('2026-09-01'),
    });
    const res = await service.decideWhitelistPart(
      APP_ID,
      { part: 'RP', status: AppStatus.APPROVED },
      { userId: STAFF },
    );

    expect(res.status).toBe(AppStatus.APPROVED);
    expect(tx.user.updateMany).toHaveBeenCalledWith({
      where: { id: JOUEUR, role: Role.PLAYER },
      data: { role: Role.WHITELISTED },
    });
    // Décision finale : la voix sera effacée dans 30 jours.
    expect(
      tx.whitelistApplication.update.mock.calls[0][0].data.voicePurgeAt,
    ).toBeInstanceOf(Date);
  });

  it('ancienne décision globale « Accepter » : écrit accepté seulement, jamais WHITELISTED d’office', async () => {
    const { service, tx } = monter(
      { ...base, hrpStatus: AppStatus.PENDING },
      { role: Role.PLAYER, oralPassedAt: null },
    );
    const res = await service.decideWhitelist(
      APP_ID,
      { status: AppStatus.APPROVED },
      { userId: STAFF },
    );

    expect(tx.whitelistApplication.update.mock.calls[0][0].data).toMatchObject({
      hrpStatus: AppStatus.APPROVED,
      rpStatus: AppStatus.APPROVED,
      status: AppStatus.PENDING,
    });
    expect(tx.user.updateMany).not.toHaveBeenCalled();
    expect(res.status).toBe(AppStatus.PENDING);
  });

  it('refus : délai d’un jour posé sur le compte, place de douane rendue, voix programmée', async () => {
    const { service, tx, messages } = monter(base, {
      role: Role.PLAYER,
      oralPassedAt: null,
    });
    await service.decideWhitelistPart(
      APP_ID,
      {
        part: 'RP',
        status: AppStatus.REJECTED,
        reviewNotes: 'Histoire copiée.',
      },
      { userId: STAFF },
    );

    expect(tx.whitelistApplication.update.mock.calls[0][0].data).toMatchObject({
      status: AppStatus.REJECTED,
      writtenApprovedAt: null,
      reviewNotes: 'Histoire copiée.',
    });
    expect(tx.user.update).toHaveBeenCalledWith(
      expect.objectContaining({
        data: { whitelistRejectedAt: expect.any(Date) as Date },
      }),
    );
    expect(tx.douaneReservation.updateMany).toHaveBeenCalled();
    expect((messages.postSystemMessage.mock.calls[0] as unknown[])[1]).toMatch(
      /^❌ Partie RP refusée\. Nouvelle candidature complète possible le /,
    );
  });

  it('refuse de trancher sa propre candidature, ou une candidature déjà acceptée', async () => {
    const a = monter(base, { role: Role.HELPER, oralPassedAt: null });
    await expect(
      a.service.decideWhitelistPart(
        APP_ID,
        { part: 'HRP', status: AppStatus.APPROVED },
        { userId: JOUEUR },
      ),
    ).rejects.toBeInstanceOf(ForbiddenException);

    const b = monter(
      { ...base, status: AppStatus.APPROVED },
      { role: Role.WHITELISTED, oralPassedAt: new Date() },
    );
    await expect(
      b.service.decideWhitelistPart(
        APP_ID,
        { part: 'HRP', status: AppStatus.REJECTED, reviewNotes: 'Non.' },
        { userId: STAFF },
      ),
    ).rejects.toBeInstanceOf(ConflictException);
  });

  it('« À revoir » et « Refusé » au panel : commentaire obligatoire (il est montré au candidat)', async () => {
    const { service, tx } = monter(base, {
      role: Role.PLAYER,
      oralPassedAt: null,
    });
    for (const status of [AppStatus.NEEDS_REVISION, AppStatus.REJECTED]) {
      await expect(
        service.decideWhitelistPart(
          APP_ID,
          { part: 'RP', status, reviewNotes: '   ' },
          { userId: STAFF },
        ),
      ).rejects.toBeInstanceOf(BadRequestException);
    }
    expect(tx.whitelistApplication.update).not.toHaveBeenCalled();
  });

  it('une partie tranchée : le commentaire encore à lire de l’autre partie est gardé', async () => {
    const { service, tx } = monter(
      {
        ...base,
        hrpStatus: AppStatus.NEEDS_REVISION,
        status: AppStatus.NEEDS_REVISION,
        reviewNotes: 'Précise tes disponibilités.',
      },
      { role: Role.PLAYER, oralPassedAt: null },
    );
    await service.decideWhitelistPart(
      APP_ID,
      {
        part: 'RP',
        status: AppStatus.NEEDS_REVISION,
        reviewNotes: 'Développe ton histoire.',
      },
      { userId: STAFF },
    );
    expect(tx.whitelistApplication.update.mock.calls[0][0].data).toMatchObject({
      reviewNotes:
        'HRP : Précise tes disponibilités.\n\nRP : Développe ton histoire.',
    });
  });

  it('retour sur un écrit refusé : effacement de la voix et délai annulés', async () => {
    const { service, tx } = monter(
      {
        ...base,
        rpStatus: AppStatus.REJECTED,
        status: AppStatus.REJECTED,
        voicePurgeAt: new Date('2026-11-24T10:00:00Z'),
      },
      { role: Role.PLAYER, oralPassedAt: null },
    );
    await service.decideWhitelistPart(
      APP_ID,
      {
        part: 'RP',
        status: AppStatus.NEEDS_REVISION,
        reviewNotes: 'Finalement, reprends la fin de ton histoire.',
      },
      { userId: STAFF },
    );
    expect(tx.whitelistApplication.update.mock.calls[0][0].data).toMatchObject({
      status: AppStatus.NEEDS_REVISION,
      voicePurgeAt: null,
    });
    expect(tx.user.update).toHaveBeenCalledWith({
      where: { id: JOUEUR },
      data: { whitelistRejectedAt: null },
    });
  });

  it('la note privée ne part jamais dans le fil ni vers le bot', async () => {
    const { service, messages, webhooks } = monter(base, {
      role: Role.PLAYER,
      oralPassedAt: null,
    });
    await service.decideWhitelistPart(
      APP_ID,
      {
        part: 'RP',
        status: AppStatus.NEEDS_REVISION,
        reviewNotes: 'Précise tes objectifs.',
        staffNotes: 'NOTE-PRIVEE',
      },
      { userId: STAFF },
    );
    expect(JSON.stringify(messages.postSystemMessage.mock.calls)).not.toContain(
      'NOTE-PRIVEE',
    );
    expect(JSON.stringify(webhooks.statusUpdate.mock.calls)).not.toContain(
      'NOTE-PRIVEE',
    );
  });
});

describe('StaffService — décision par partie depuis le bot', () => {
  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => {});
  });
  afterEach(() => jest.restoreAllMocks());

  const decision = {
    part: 'RP' as const,
    status: AppStatus.NEEDS_REVISION,
    reviewNotes: 'Précise tes objectifs.',
    actorDiscordId: '123456789012345678',
  };

  it('compte Discord lié de rôle Relecteur : décision à son nom, sans note privée', async () => {
    const m = monter(
      { ...base, writtenStaffNotes: 'NOTE-PRIVEE' },
      { role: Role.PLAYER, oralPassedAt: null },
    );
    const prisma = (
      m.service as unknown as { prisma: { user: { findUnique: jest.Mock } } }
    ).prisma;
    prisma.user.findUnique.mockImplementation(() =>
      Promise.resolve({
        id: STAFF,
        role: Role.WHITELIST_REVIEWER,
        banned: false,
        discordUsername: 'Ayame',
        minecraftUsername: 'Ayame',
      }),
    );
    const res = await m.service.decideWhitelistPartFromDiscord(
      APP_ID,
      decision,
    );
    expect(prisma.user.findUnique).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { discordUserId: decision.actorDiscordId },
      }),
    );
    expect(
      m.tx.whitelistApplication.update.mock.calls[0][0].data,
    ).toMatchObject({
      rpStatus: AppStatus.NEEDS_REVISION,
      reviewedBy: STAFF,
    });
    expect(m.audit.log).toHaveBeenCalledWith(
      expect.objectContaining({ actorId: STAFF, source: 'discord' }),
    );
    expect(res).not.toHaveProperty('writtenStaffNotes');
    expect(res.rpStatus).toBe(AppStatus.NEEDS_REVISION);
  });

  it('compte non lié, banni ou de rôle insuffisant : refusé, rien n’est tranché', async () => {
    for (const compte of [
      null,
      { id: STAFF, role: Role.HELPER, banned: false },
      { id: STAFF, role: Role.ADMIN, banned: true },
    ]) {
      const m = monter(base, { role: Role.PLAYER, oralPassedAt: null });
      const prisma = (
        m.service as unknown as { prisma: { user: { findUnique: jest.Mock } } }
      ).prisma;
      prisma.user.findUnique.mockImplementation(() => Promise.resolve(compte));
      await expect(
        m.service.decideWhitelistPartFromDiscord(APP_ID, decision),
      ).rejects.toBeInstanceOf(ForbiddenException);
      expect(m.tx.whitelistApplication.update).not.toHaveBeenCalled();
    }
  });
});

describe('messageDecisionEcrit', () => {
  const now = new Date('2026-10-24T15:05:00Z');
  const { APPROVED: A, PENDING: P, NEEDS_REVISION: R } = AppStatus;

  it('une partie validée, l’autre en attente', () => {
    expect(messageDecisionEcrit(['HRP'], A, A, P, P, now)).toBe(
      '✅ Partie HRP validée. La partie RP est en cours de relecture.',
    );
  });
  it('révision demandée sur les deux parties', () => {
    expect(messageDecisionEcrit(['HRP', 'RP'], R, R, R, R, now)).toBe(
      '✏️ Une révision de la partie HRP et RP est demandée : modifie ta candidature puis renvoie-la.',
    );
  });
});

describe('fusionnerCommentaires', () => {
  const { APPROVED: A, PENDING: P, NEEDS_REVISION: R } = AppStatus;

  it('sans nouveau commentaire : rien ne change', () => {
    expect(fusionnerCommentaires('Ancien.', 'RP', undefined, R)).toBe(
      'Ancien.',
    );
  });
  it('l’autre partie n’a plus rien à lire : le nouveau commentaire seul', () => {
    expect(fusionnerCommentaires('Ancien.', 'RP', 'Nouveau.', A)).toBe(
      'Nouveau.',
    );
    expect(fusionnerCommentaires('Ancien.', 'RP', 'Nouveau.', P)).toBe(
      'Nouveau.',
    );
  });
  it('sections déjà en place : seule la partie tranchée est remplacée', () => {
    expect(
      fusionnerCommentaires('HRP : Un.\n\nRP : Deux.', 'RP', 'Trois.', R),
    ).toBe('HRP : Un.\n\nRP : Trois.');
  });
});
