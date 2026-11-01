import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { AppStatus, Role } from '@prisma/client';
import type { AuditService } from '../audit/audit.service';
import type { PrismaService } from '../prisma/prisma.service';
import type { WebhooksService } from '../webhooks/webhooks.service';
import type { WhitelistMessagesService } from '../whitelist/whitelist-messages.service';
import type { WhitelistVoixService } from '../whitelist/whitelist-voix.service';
import { OPTIONS_TRANSACTION } from '../douane/douane.verrous';
import { evaluerAcces } from '../game/game-acces.regles';
import { calculerSuivi } from '../whitelist/whitelist.rules';
import {
  messageRepasserOral,
  REFUS_ECRIT_ORAL_A_REPASSER,
  REFUS_ORAL_CANDIDATURE_REFUSEE,
  REFUS_ORAL_COMPTE_STAFF,
  REFUS_ORAL_DEJA_A_PASSER,
  REFUS_ORAL_SANS_CANDIDATURE,
  refusRepasserOral,
} from './oral-repasser.regles';
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
  user: {
    role: Role;
    oralPassedAt: Date | null;
    oralRetakeRequestedAt?: Date | null;
  },
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

  it('oral à repasser : l’écrit déjà accepté ne se retranche plus (panel et bot), un écrit en relecture si', async () => {
    const exige = new Date('2026-11-01T10:00:00Z');
    const a = monter(
      { ...base, rpStatus: AppStatus.APPROVED },
      { role: Role.PLAYER, oralPassedAt: null, oralRetakeRequestedAt: exige },
    );
    await expect(
      a.service.decideWhitelistPart(
        APP_ID,
        { part: 'HRP', status: AppStatus.REJECTED, reviewNotes: 'Non.' },
        { userId: STAFF },
      ),
    ).rejects.toThrow(REFUS_ECRIT_ORAL_A_REPASSER);
    await expect(
      a.service.decideWhitelist(
        APP_ID,
        { status: AppStatus.REJECTED },
        { userId: STAFF },
      ),
    ).rejects.toBeInstanceOf(ConflictException);
    expect(a.tx.whitelistApplication.update).not.toHaveBeenCalled();
    expect(a.tx.user.update).not.toHaveBeenCalled();

    // Oral exigé avant la fin de l'écrit : la relecture suit son cours.
    const b = monter(base, {
      role: Role.PLAYER,
      oralPassedAt: null,
      oralRetakeRequestedAt: exige,
    });
    const res = await b.service.decideWhitelistPart(
      APP_ID,
      { part: 'RP', status: AppStatus.APPROVED },
      { userId: STAFF },
    );
    expect(res.status).toBe(AppStatus.PENDING);
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

// Oral à repasser (décision du 01/11, amende celle du 19/10) : le staff remet le
// compte dans l'état « écrit accepté, oral à passer » que toutes les règles
// lisent déjà ; quand, qui et pourquoi vivent à part, sur le compte.

const ORAL_PASSE_LE = new Date('2026-09-25T20:30:00Z');
const MOTIF = 'Candidature de clan Uchiha.';

/** Dossier accepté, oral compris : celui d'un joueur whitelisté. */
const accepte = {
  ...base,
  status: AppStatus.APPROVED,
  rpStatus: AppStatus.APPROVED,
  oralStatus: AppStatus.APPROVED,
  writtenApprovedAt: new Date('2026-09-20T18:00:00Z'),
  reviewedAt: new Date('2026-09-20T18:00:00Z'),
  reviewedBy: STAFF,
  oralDecidedAt: ORAL_PASSE_LE,
  oralVia: 'DOUANE',
  oralExaminerUserId: STAFF,
  oralReason: 'Bienvenue.',
  oralNotes: 'Très à l’aise.',
  voicePurgeAt: new Date('2026-10-25T20:30:00Z'),
  assignedToUserId: STAFF,
  assignedAt: new Date('2026-09-19T10:00:00Z'),
};

interface CompteRepassage {
  role: Role;
  oralPassedAt: Date | null;
  discordUserId?: string | null;
}

function monterRepassage(
  compte: CompteRepassage | null,
  app: Record<string, unknown> | null,
  opts: {
    /** Lignes relues sous le verrou, quand elles ont changé depuis la première lecture. */
    relu?: { compte: CompteRepassage; app: Record<string, unknown> | null };
    /** Place BOOKED de la candidature. */
    place?: { id: string } | null;
  } = {},
) {
  const joueur = (c: CompteRepassage) => ({
    id: JOUEUR,
    minecraftUsername: 'Kaito',
    discordUserId: null,
    whitelistRejectedAt: null,
    ...c,
  });
  const relu = opts.relu ?? (compte ? { compte, app } : null);
  // Ni update ni updateMany sur les places : toute écriture d'une place lèverait.
  const tx = {
    $queryRaw: jest.fn(() => Promise.resolve([])),
    whitelistApplication: {
      findUnique: jest.fn(() =>
        Promise.resolve(
          relu?.app ? { ...relu.app, user: joueur(relu.compte) } : null,
        ),
      ),
      update: jest.fn(({ data }: { data: Record<string, unknown> }) =>
        Promise.resolve({ ...relu?.app, ...data }),
      ),
    },
    douaneReservation: {
      findFirst: jest.fn(() => Promise.resolve(opts.place ?? null)),
    },
    user: {
      update: jest.fn(
        (a: { where: { id: string }; data: Record<string, unknown> }) =>
          Promise.resolve(a.data),
      ),
      updateMany: jest.fn((a: { where: { id: string; role: Role } }) =>
        Promise.resolve({
          count: relu?.compte.role === a.where.role ? 1 : 0,
        }),
      ),
    },
  };
  const prisma = {
    $transaction: jest.fn((fn: (t: typeof tx) => Promise<unknown>) =>
      Promise.resolve(fn(tx)),
    ),
    user: {
      findUnique: jest.fn(({ where }: { where: { id: string } }) =>
        Promise.resolve(
          where.id !== JOUEUR
            ? {
                id: STAFF,
                discordUsername: 'Ayame',
                minecraftUsername: 'Ayame',
              }
            : compte && {
                ...joueur(compte),
                whitelistApp: app && {
                  id: APP_ID,
                  status: app.status,
                  hrpStatus: app.hrpStatus,
                  rpStatus: app.rpStatus,
                  oralStatus: app.oralStatus,
                },
              },
        ),
      ),
    },
  };
  const webhooks = {
    statusUpdate: jest.fn(() => Promise.resolve(undefined)),
    directMessage: jest.fn(() => Promise.resolve(undefined)),
  };
  const audit = { log: jest.fn(() => Promise.resolve(undefined)) };
  const messages = {
    postSystemMessage: jest.fn(() => Promise.resolve(undefined)),
  };
  const service = new StaffService(
    prisma as unknown as PrismaService,
    webhooks as unknown as WebhooksService,
    audit as unknown as AuditService,
    messages as unknown as WhitelistMessagesService,
    { supprimer: jest.fn() } as unknown as WhitelistVoixService,
  );
  return { service, prisma, tx, webhooks, audit, messages };
}

/** Verrous pris dans la transaction, dans l'ordre : [requête, identifiant]. */
function verrousPris(queryRaw: jest.Mock): Array<[string, unknown]> {
  return (
    queryRaw.mock.calls as unknown as Array<[TemplateStringsArray, unknown]>
  ).map(([sql, id]) => [sql.join('?'), id]);
}

/** Erreur levée par la promesse (le test échoue si elle se résout). */
async function erreurDe(p: Promise<unknown>): Promise<Error> {
  try {
    await p;
  } catch (e) {
    return e as Error;
  }
  throw new Error('Refus attendu, la promesse s’est résolue.');
}

describe('StaffService.exigerNouvelOral — oral à repasser (01/11)', () => {
  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => {});
  });
  afterEach(() => jest.restoreAllMocks());

  it('whitelisté, dossier accepté : PLAYER, oral à passer, écrit gardé, indicateur posé, fil, bot, MP, audit', async () => {
    const m = monterRepassage(
      {
        role: Role.WHITELISTED,
        oralPassedAt: ORAL_PASSE_LE,
        discordUserId: '123456789012345678',
      },
      accepte,
    );
    const res = await m.service.exigerNouvelOral(JOUEUR, `  ${MOTIF}  `, {
      userId: STAFF,
    });

    expect(res).toEqual({
      ok: true,
      userId: JOUEUR,
      applicationId: APP_ID,
      roleAvant: Role.WHITELISTED,
      phase: 'DOUANE_A_RESERVER',
    });
    // Une transaction, mêmes options que la douane : la candidature verrouillée
    // d'abord, puis le compte (rôle et oral figés jusqu'aux écritures).
    expect(m.prisma.$transaction).toHaveBeenCalledWith(
      expect.any(Function),
      OPTIONS_TRANSACTION,
    );
    expect(verrousPris(m.tx.$queryRaw)).toEqual([
      [
        'SELECT "id" FROM "WhitelistApplication" WHERE "id" = ? FOR UPDATE',
        APP_ID,
      ],
      ['SELECT "id" FROM "User" WHERE "id" = ? FOR UPDATE', JOUEUR],
    ]);
    expect(m.tx.user.update).toHaveBeenCalledWith({
      where: { id: JOUEUR },
      data: {
        oralPassedAt: null,
        oralRetakeRequestedAt: expect.any(Date) as Date,
        oralRetakeRequestedById: STAFF,
        oralRetakeReason: MOTIF,
      },
    });
    expect(m.tx.user.updateMany).toHaveBeenCalledWith({
      where: { id: JOUEUR, role: Role.WHITELISTED },
      data: { role: Role.PLAYER },
    });
    // Écrit gardé (writtenApprovedAt intact), oral remis à passer, plus de prise
    // en charge, voix gardée jusqu'à la prochaine décision finale. Jamais « à
    // revoir » : pas de « 2ᵉ passage » en jeu, pas d'attente du lendemain.
    expect(m.tx.whitelistApplication.update.mock.calls[0][0].data).toEqual({
      status: AppStatus.PENDING,
      hrpStatus: AppStatus.APPROVED,
      rpStatus: AppStatus.APPROVED,
      oralStatus: AppStatus.PENDING,
      oralDecidedAt: null,
      oralVia: null,
      oralExaminerUserId: null,
      oralReason: null,
      oralNotes: null,
      assignedToUserId: null,
      assignedAt: null,
      voicePurgeAt: null,
    });

    const message =
      "Le staff te demande de repasser l'oral : Candidature de clan Uchiha. " +
      "Réserve une séance de douane dans le launcher (onglet Whitelist) pour retrouver l'accès au jeu.";
    expect(m.messages.postSystemMessage).toHaveBeenCalledWith(APP_ID, message);
    expect(m.webhooks.statusUpdate).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'whitelist',
        messageId: 'msg-1',
        status: STATUT_BOT_ECRIT_ACCEPTE,
        reason: `Oral à repasser : ${MOTIF}`,
      }),
    );
    expect(m.webhooks.directMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        discordUserId: '123456789012345678',
        context: {
          kind: 'douane',
          entityId: APP_ID,
          subject: 'Oral à repasser',
        },
        content: message,
      }),
    );
    expect(m.audit.log).toHaveBeenCalledWith({
      actorId: STAFF,
      action: 'whitelist.oral.repasser',
      targetUserId: JOUEUR,
      targetEntity: `whitelist:${APP_ID}`,
      source: 'panel',
      metadata: {
        motif: MOTIF,
        roleAvant: Role.WHITELISTED,
        roleApres: Role.PLAYER,
        oralPassedAtAvant: ORAL_PASSE_LE.toISOString(),
        statutAvant: AppStatus.APPROVED,
        oralAvant: AppStatus.APPROVED,
        hrpAvant: AppStatus.APPROVED,
        rpAvant: AppStatus.APPROVED,
        ecritForce: false,
        phaseApres: 'DOUANE_A_RESERVER',
        // L'oral effacé de la candidature, notes privées comprises.
        oralDecidedAtAvant: ORAL_PASSE_LE.toISOString(),
        oralViaAvant: 'DOUANE',
        oralExaminerAvant: STAFF,
        oralReasonAvant: 'Bienvenue.',
        oralNotesAvant: 'Très à l’aise.',
      },
    });
  });

  it('l’état produit est celui que lisent déjà le launcher, le jeu et le jeton : douane à réserver', async () => {
    const m = monterRepassage(
      { role: Role.WHITELISTED, oralPassedAt: ORAL_PASSE_LE },
      accepte,
    );
    await m.service.exigerNouvelOral(JOUEUR, MOTIF, { userId: STAFF });
    const data = m.tx.whitelistApplication.update.mock.calls[0][0].data;
    const app = {
      status: data.status as AppStatus,
      hrpStatus: data.hrpStatus as AppStatus,
      rpStatus: data.rpStatus as AppStatus,
      oralStatus: data.oralStatus as AppStatus,
      oralDecidedAt: data.oralDecidedAt as Date | null,
      reviewedAt: accepte.reviewedAt,
      // Gardée telle quelle (absente des données écrites).
      writtenApprovedAt: accepte.writtenApprovedAt,
    };
    const now = new Date('2026-11-01T15:00:00Z');

    expect(
      calculerSuivi({
        role: Role.PLAYER,
        oralPassedAt: null,
        whitelistRejectedAt: null,
        app,
        reservation: null,
        now,
      }),
    ).toMatchObject({ phase: 'DOUANE_A_RESERVER', oralRequis: true });
    // Accès au jeu (même ordre que le jeton de jeu) : réserver, puis le hall dans la fenêtre d'entrée.
    const acces = (
      reservation: { id: string; seanceId: string; convocationAt: Date } | null,
    ) =>
      evaluerAcces({
        compte: {
          role: Role.PLAYER,
          banned: false,
          oralPassedAt: null,
          whitelistRejectedAt: null,
        },
        candidature: app,
        reservation,
        maintenant: now,
        douaneActive: true,
        retardMaxMin: 30,
      });
    expect(acces(null)).toMatchObject({
      portee: 'aucune',
      raison: 'DOUANE_A_RESERVER',
    });
    expect(
      acces({
        id: 'r1',
        seanceId: 's1',
        convocationAt: new Date(now.getTime() + 5 * 60 * 1000),
      }),
    ).toMatchObject({
      portee: 'douane',
      raison: 'DOUANE_FENETRE_OUVERTE',
      phase: 'DOUANE_RESERVEE',
    });
  });

  it('joueur en cours d’écrit dont l’oral est passé : seuls oralPassedAt et l’indicateur changent', async () => {
    const m = monterRepassage(
      { role: Role.PLAYER, oralPassedAt: ORAL_PASSE_LE },
      base,
    );
    const res = await m.service.exigerNouvelOral(JOUEUR, MOTIF, {
      userId: STAFF,
    });

    expect(res).toMatchObject({
      roleAvant: Role.PLAYER,
      phase: 'ECRIT_EN_ATTENTE',
    });
    expect(m.tx.user.update).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          oralPassedAt: null,
          oralRetakeReason: MOTIF,
        }) as unknown,
      }),
    );
    expect(m.tx.whitelistApplication.update).not.toHaveBeenCalled();
    // L'écrit d'abord : le message ne promet pas une réservation impossible.
    expect(m.messages.postSystemMessage).toHaveBeenCalledWith(
      APP_ID,
      "Le staff te demande de repasser l'oral : Candidature de clan Uchiha. " +
        "Une fois ton écrit accepté, réserve une séance de douane dans le launcher (onglet Whitelist) pour retrouver l'accès au jeu.",
    );
    const journal = m.audit.log.mock.calls[0] as unknown as [
      { metadata: Record<string, unknown> },
    ];
    expect(journal[0].metadata).toMatchObject({
      roleAvant: Role.PLAYER,
      roleApres: Role.PLAYER,
      ecritForce: false,
      phaseApres: 'ECRIT_EN_ATTENTE',
    });
    // Pas d'oral tranché sur cette candidature : rien à recopier.
    expect(journal[0].metadata).not.toHaveProperty('oralNotesAvant');
  });

  it('ancien dossier (v1) accepté d’un bloc : parties forcées validées, date d’écrit posée', async () => {
    const v1 = {
      ...accepte,
      formVersion: 1,
      hrpStatus: AppStatus.PENDING,
      rpStatus: AppStatus.PENDING,
      writtenApprovedAt: null,
    };
    const m = monterRepassage(
      { role: Role.WHITELISTED, oralPassedAt: ORAL_PASSE_LE },
      v1,
    );
    const res = await m.service.exigerNouvelOral(JOUEUR, MOTIF, {
      userId: STAFF,
    });

    const data = m.tx.whitelistApplication.update.mock.calls[0][0].data;
    expect(data).toMatchObject({
      status: AppStatus.PENDING,
      hrpStatus: AppStatus.APPROVED,
      rpStatus: AppStatus.APPROVED,
      oralStatus: AppStatus.PENDING,
    });
    expect(data.writtenApprovedAt).toBeInstanceOf(Date);
    expect(res.phase).toBe('DOUANE_A_RESERVER');
    expect(m.audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        metadata: expect.objectContaining({
          ecritForce: true,
          hrpAvant: AppStatus.PENDING,
          rpAvant: AppStatus.PENDING,
        }) as unknown,
      }),
    );
  });

  it('place de douane encore réservée : gardée telle quelle, phase DOUANE_RESERVEE', async () => {
    const m = monterRepassage(
      { role: Role.WHITELISTED, oralPassedAt: ORAL_PASSE_LE },
      accepte,
      { place: { id: 'r1' } },
    );
    const res = await m.service.exigerNouvelOral(JOUEUR, MOTIF, {
      userId: STAFF,
    });
    expect(res.phase).toBe('DOUANE_RESERVEE');
    expect(m.messages.postSystemMessage).toHaveBeenCalledWith(
      APP_ID,
      expect.stringContaining(
        'Ta place de douane est gardée : présente-toi à ta séance',
      ),
    );
  });

  it('refus avant toute écriture : joueur inconnu (404), son propre compte (403), motif vide (400)', async () => {
    const inconnu = monterRepassage(null, null);
    await expect(
      inconnu.service.exigerNouvelOral(JOUEUR, MOTIF, { userId: STAFF }),
    ).rejects.toBeInstanceOf(NotFoundException);

    const soi = monterRepassage(
      { role: Role.WHITELISTED, oralPassedAt: ORAL_PASSE_LE },
      accepte,
    );
    await expect(
      soi.service.exigerNouvelOral(JOUEUR, MOTIF, { userId: JOUEUR }),
    ).rejects.toBeInstanceOf(ForbiddenException);
    await expect(
      soi.service.exigerNouvelOral(JOUEUR, '  a  ', { userId: STAFF }),
    ).rejects.toBeInstanceOf(BadRequestException);

    for (const m of [inconnu, soi]) {
      expect(m.prisma.$transaction).not.toHaveBeenCalled();
      expect(m.audit.log).not.toHaveBeenCalled();
    }
  });

  it('compte staff : refusé (409), son grade ne se touche pas ici', async () => {
    for (const role of [
      Role.HELPER,
      Role.MODELISATEUR,
      Role.DEVELOPPEUR,
      Role.WHITELIST_REVIEWER,
      Role.MODERATOR,
      Role.ADMIN,
      Role.OWNER,
    ]) {
      const m = monterRepassage({ role, oralPassedAt: ORAL_PASSE_LE }, accepte);
      const e = await erreurDe(
        m.service.exigerNouvelOral(JOUEUR, MOTIF, { userId: STAFF }),
      );
      expect(e).toBeInstanceOf(ConflictException);
      expect(e.message).toBe(REFUS_ORAL_COMPTE_STAFF);
      expect(m.prisma.$transaction).not.toHaveBeenCalled();
    }
  });

  it('sans candidature, ou oral déjà à passer : refusé (409), rien n’est écrit', async () => {
    const cas: Array<
      [CompteRepassage, Record<string, unknown> | null, string]
    > = [
      [
        { role: Role.WHITELISTED, oralPassedAt: ORAL_PASSE_LE },
        null,
        REFUS_ORAL_SANS_CANDIDATURE,
      ],
      // Écrit accepté, douane à réserver : l'oral est déjà à passer.
      [
        { role: Role.PLAYER, oralPassedAt: null },
        {
          ...accepte,
          status: AppStatus.PENDING,
          oralStatus: AppStatus.PENDING,
        },
        REFUS_ORAL_DEJA_A_PASSER,
      ],
      // Douane « à revoir ».
      [
        { role: Role.PLAYER, oralPassedAt: null },
        {
          ...accepte,
          status: AppStatus.PENDING,
          oralStatus: AppStatus.NEEDS_REVISION,
        },
        REFUS_ORAL_DEJA_A_PASSER,
      ],
      // Nouvel écrit refusé après une remise à zéro sans « refaire l'oral » :
      // la douane ne se réserve pas, le joueur perdrait son accès pour rien.
      [
        { role: Role.PLAYER, oralPassedAt: ORAL_PASSE_LE },
        {
          ...base,
          status: AppStatus.REJECTED,
          hrpStatus: AppStatus.REJECTED,
        },
        REFUS_ORAL_CANDIDATURE_REFUSEE,
      ],
      // Whitelisté dont la candidature envoyée ensuite a été refusée.
      [
        { role: Role.WHITELISTED, oralPassedAt: ORAL_PASSE_LE },
        { ...base, status: AppStatus.REJECTED, rpStatus: AppStatus.REJECTED },
        REFUS_ORAL_CANDIDATURE_REFUSEE,
      ],
    ];
    for (const [compte, app, attendu] of cas) {
      const m = monterRepassage(compte, app);
      const e = await erreurDe(
        m.service.exigerNouvelOral(JOUEUR, MOTIF, { userId: STAFF }),
      );
      expect(e).toBeInstanceOf(ConflictException);
      expect(e.message).toBe(attendu);
      expect(m.prisma.$transaction).not.toHaveBeenCalled();
    }
  });

  it('relu sous les verrous : un oral déjà remis à passer, une candidature effacée ou un grade staff donné entre-temps, refusé sans écrire', async () => {
    const cas: Array<
      [{ compte: CompteRepassage; app: Record<string, unknown> | null }, string]
    > = [
      // Changement de grade (RoleService) passé avant le verrou du compte : le
      // rôle relu est celui du staff, ni l'oral ni la candidature ne bougent.
      [
        {
          compte: { role: Role.HELPER, oralPassedAt: ORAL_PASSE_LE },
          app: accepte,
        },
        REFUS_ORAL_COMPTE_STAFF,
      ],
      [
        {
          compte: { role: Role.PLAYER, oralPassedAt: null },
          app: {
            ...accepte,
            status: AppStatus.PENDING,
            oralStatus: AppStatus.PENDING,
          },
        },
        REFUS_ORAL_DEJA_A_PASSER,
      ],
      [
        {
          compte: { role: Role.WHITELISTED, oralPassedAt: ORAL_PASSE_LE },
          app: null,
        },
        REFUS_ORAL_SANS_CANDIDATURE,
      ],
    ];
    for (const [relu, attendu] of cas) {
      const m = monterRepassage(
        { role: Role.WHITELISTED, oralPassedAt: ORAL_PASSE_LE },
        accepte,
        { relu },
      );
      const e = await erreurDe(
        m.service.exigerNouvelOral(JOUEUR, MOTIF, { userId: STAFF }),
      );
      expect(e).toBeInstanceOf(ConflictException);
      expect(e.message).toBe(attendu);
      // Relu une fois la candidature puis le compte verrouillés.
      expect(verrousPris(m.tx.$queryRaw).map(([, id]) => id)).toEqual([
        APP_ID,
        JOUEUR,
      ]);
      expect(m.tx.user.update).not.toHaveBeenCalled();
      expect(m.tx.user.updateMany).not.toHaveBeenCalled();
      expect(m.tx.whitelistApplication.update).not.toHaveBeenCalled();
      expect(m.audit.log).not.toHaveBeenCalled();
    }
  });
});

describe('refusRepasserOral — même règle pour la route et le bouton du panel', () => {
  const { APPROVED: A, PENDING: P, NEEDS_REVISION: R, REJECTED: X } = AppStatus;
  /** Parties HRP et RP au même statut ; validées par défaut sur un dossier accepté. */
  const app = (
    status: AppStatus,
    oralStatus: AppStatus,
    ecrit: AppStatus = status === A ? A : P,
  ) => ({ status, hrpStatus: ecrit, rpStatus: ecrit, oralStatus });

  it('acceptés : whitelisté (même promu à la main), oral passé, dossier accepté resté PLAYER', () => {
    const ok = [
      refusRepasserOral(
        { role: Role.WHITELISTED, oralPassedAt: ORAL_PASSE_LE },
        app(A, A),
      ),
      refusRepasserOral(
        { role: Role.WHITELISTED, oralPassedAt: null },
        app(P, P),
      ),
      refusRepasserOral(
        { role: Role.PLAYER, oralPassedAt: ORAL_PASSE_LE },
        app(P, P),
      ),
      refusRepasserOral({ role: Role.PLAYER, oralPassedAt: null }, app(A, A)),
      refusRepasserOral({ role: Role.PLAYER, oralPassedAt: null }, app(P, A)),
    ];
    expect(ok).toEqual([null, null, null, null, null]);
  });

  it('refusés : staff, sans candidature, oral déjà à passer (attente, à revoir)', () => {
    expect(
      refusRepasserOral(
        { role: Role.HELPER, oralPassedAt: ORAL_PASSE_LE },
        app(A, A),
      ),
    ).toBe(REFUS_ORAL_COMPTE_STAFF);
    expect(
      refusRepasserOral(
        { role: Role.WHITELISTED, oralPassedAt: ORAL_PASSE_LE },
        null,
      ),
    ).toBe(REFUS_ORAL_SANS_CANDIDATURE);
    for (const [s, o] of [
      [P, P],
      [P, R],
      [R, P],
    ] as const) {
      expect(
        refusRepasserOral({ role: Role.PLAYER, oralPassedAt: null }, app(s, o)),
      ).toBe(REFUS_ORAL_DEJA_A_PASSER);
    }
  });

  it('refusés : toute candidature refusée (statut, HRP, RP ou oral), même avec un oral déjà réussi', () => {
    const comptes = [
      { role: Role.PLAYER, oralPassedAt: null },
      { role: Role.PLAYER, oralPassedAt: ORAL_PASSE_LE },
      { role: Role.WHITELISTED, oralPassedAt: ORAL_PASSE_LE },
    ];
    const refusees = [
      app(X, P, X),
      app(X, X, A),
      { ...app(P, P), hrpStatus: X },
      { ...app(P, P), rpStatus: X },
      app(P, X, A),
    ];
    for (const compte of comptes) {
      for (const dossier of refusees) {
        expect(refusRepasserOral(compte, dossier)).toBe(
          REFUS_ORAL_CANDIDATURE_REFUSEE,
        );
      }
    }
  });

  it('message au joueur : le motif, puis la consigne de la phase obtenue ; point final ajouté s’il manque', () => {
    expect(messageRepasserOral('Longue absence', 'DOUANE_A_RESERVER')).toBe(
      "Le staff te demande de repasser l'oral : Longue absence. " +
        "Réserve une séance de douane dans le launcher (onglet Whitelist) pour retrouver l'accès au jeu.",
    );
    expect(
      messageRepasserOral('Longue absence !', 'DOUANE_A_RESERVER'),
    ).toContain('Longue absence ! Réserve');
    // Écrit encore en relecture ou à revoir : la douane se réserve après.
    for (const phase of ['ECRIT_EN_ATTENTE', 'ECRIT_A_REVOIR'] as const) {
      expect(messageRepasserOral('Longue absence', phase)).toBe(
        "Le staff te demande de repasser l'oral : Longue absence. " +
          "Une fois ton écrit accepté, réserve une séance de douane dans le launcher (onglet Whitelist) pour retrouver l'accès au jeu.",
      );
    }
    // Place déjà prise, gardée telle quelle.
    expect(messageRepasserOral('Longue absence', 'DOUANE_RESERVEE')).toBe(
      "Le staff te demande de repasser l'oral : Longue absence. " +
        "Ta place de douane est gardée : présente-toi à ta séance pour retrouver l'accès au jeu.",
    );
  });
});

describe('StaffService.resetWhitelist — « Refaire aussi l’oral » (01/11)', () => {
  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
  });
  afterEach(() => jest.restoreAllMocks());

  function monterReset(compte: { role: Role; oralPassedAt: Date | null }) {
    const tx = {
      whitelistApplication: { delete: jest.fn(() => Promise.resolve({})) },
      user: {
        update: jest.fn(
          (a: { where: { id: string }; data: Record<string, unknown> }) =>
            Promise.resolve(a.data),
        ),
      },
    };
    const prisma = {
      whitelistApplication: {
        findUnique: jest.fn(() =>
          Promise.resolve({
            id: APP_ID,
            userId: JOUEUR,
            status: AppStatus.APPROVED,
            voiceKey: 'voix.webm',
          }),
        ),
      },
      user: { findUnique: jest.fn(() => Promise.resolve(compte)) },
      $transaction: jest.fn((fn: (t: typeof tx) => Promise<unknown>) =>
        Promise.resolve(fn(tx)),
      ),
    };
    const audit = { log: jest.fn(() => Promise.resolve(undefined)) };
    const voix = { supprimer: jest.fn(() => Promise.resolve(undefined)) };
    const service = new StaffService(
      prisma as unknown as PrismaService,
      {} as WebhooksService,
      audit as unknown as AuditService,
      {} as WhitelistMessagesService,
      voix as unknown as WhitelistVoixService,
    );
    return { service, tx, audit, voix };
  }

  it('avec la case : oral redevenu à passer, indicateur levé, inscrit au journal', async () => {
    const m = monterReset({
      role: Role.WHITELISTED,
      oralPassedAt: ORAL_PASSE_LE,
    });
    const res = await m.service.resetWhitelist(
      APP_ID,
      { userId: STAFF },
      { refaireOral: true },
    );

    expect(res).toEqual({
      ok: true,
      userId: JOUEUR,
      roleDemoted: true,
      oralReinitialise: true,
    });
    expect(m.tx.whitelistApplication.delete).toHaveBeenCalled();
    expect(m.tx.user.update).toHaveBeenCalledWith({
      where: { id: JOUEUR },
      data: {
        role: 'PLAYER',
        whitelistRejectedAt: null,
        oralPassedAt: null,
        oralRetakeRequestedAt: null,
        oralRetakeRequestedById: null,
        oralRetakeReason: null,
      },
    });
    expect(m.voix.supprimer).toHaveBeenCalledWith('voix.webm');
    expect(m.audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        action: 'whitelist.reset',
        metadata: {
          previousStatus: AppStatus.APPROVED,
          roleDemoted: true,
          refaireOral: true,
          oralPassedAtAvant: ORAL_PASSE_LE.toISOString(),
        },
      }),
    );
  });

  it('sans la case, ou décochée : comme avant, l’oral déjà passé reste acquis', async () => {
    for (const options of [undefined, { refaireOral: false }]) {
      const m = monterReset({
        role: Role.WHITELISTED,
        oralPassedAt: ORAL_PASSE_LE,
      });
      const res = await m.service.resetWhitelist(
        APP_ID,
        { userId: STAFF },
        options,
      );
      expect(res).toEqual({
        ok: true,
        userId: JOUEUR,
        roleDemoted: true,
        oralReinitialise: false,
      });
      expect(m.tx.user.update).toHaveBeenCalledWith({
        where: { id: JOUEUR },
        data: { role: 'PLAYER', whitelistRejectedAt: null },
      });
      expect(m.audit.log).toHaveBeenCalledWith(
        expect.objectContaining({
          metadata: expect.objectContaining({ refaireOral: false }) as unknown,
        }),
      );
    }
  });

  it('compte staff : grade gardé, même avec la case', async () => {
    const m = monterReset({ role: Role.ADMIN, oralPassedAt: ORAL_PASSE_LE });
    const res = await m.service.resetWhitelist(
      APP_ID,
      { userId: STAFF },
      { refaireOral: true },
    );
    expect(res).toMatchObject({ roleDemoted: false, oralReinitialise: true });
    expect(m.tx.user.update.mock.calls[0][0].data).not.toHaveProperty('role');
  });
});
