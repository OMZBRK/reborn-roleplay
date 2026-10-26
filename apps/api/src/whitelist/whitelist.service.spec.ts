import { BadRequestException, ConflictException, Logger } from '@nestjs/common';
import { AppStatus, Role, WhitelistApplication } from '@prisma/client';
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import type { AuditService } from '../audit/audit.service';
import type { PrismaService } from '../prisma/prisma.service';
import type { WebhooksService } from '../webhooks/webhooks.service';
import type { SubmitWhitelistDto } from './dto/whitelist.dto';
import { genererCleVoix } from './voix.config';
import type { WhitelistMessagesService } from './whitelist-messages.service';
import { WhitelistVoixService } from './whitelist-voix.service';
import { WhitelistService } from './whitelist.service';

// Service de la candidature côté candidat : réponses sans notes privées,
// phases, envoi v2 avec la voix du brouillon, refus de l'ancien format
// (complément du 25/10), lecture des anciens dossiers, délai après un refus,
// renvoi partiel, voix effacée au retrait. Prisma est simulé ; les fichiers de
// voix sont écrits pour de vrai dans un dossier temporaire.

const MSG_LAUNCHER = 'Mets ton launcher à jour pour envoyer ta candidature.';

const USER_ID = '11111111-1111-4111-8111-111111111111';
const APP_ID = '22222222-2222-4222-8222-222222222222';

function candidature(
  o: Partial<WhitelistApplication> = {},
): WhitelistApplication {
  const now = new Date('2026-10-24T18:12:00Z');
  return {
    id: APP_ID,
    userId: USER_ID,
    status: AppStatus.PENDING,
    hrpStatus: AppStatus.PENDING,
    rpStatus: AppStatus.PENDING,
    formVersion: 2,
    playerAge: 19,
    dob: null,
    rpYears: 'TROIS_A_CINQ_ANS',
    narutoKnowledge: 'BONNE_CONNAISSANCE',
    discoverySource: null,
    availability: 'En semaine après 20 h.',
    motivation: null,
    experience: null,
    firstName: 'Kaito',
    lastName: 'Arashi',
    village: 'Konohagakure',
    characterType: 'NINJA',
    characterSex: 'HOMME',
    characterAge: 16,
    support: null,
    history: 'h'.repeat(300),
    appearance: null,
    objectives: 'o'.repeat(450),
    voiceKey: null,
    voiceMimeType: null,
    voiceDurationSec: null,
    voiceUploadedAt: null,
    voicePurgeAt: null,
    voiceDeletedAt: null,
    rulesAcceptedAt: now,
    voiceConsentAt: now,
    submittedAt: now,
    reviewedAt: null,
    reviewedBy: null,
    reviewNotes: null,
    writtenApprovedAt: null,
    writtenStaffNotes: null,
    oralStatus: AppStatus.PENDING,
    oralDecidedAt: null,
    oralVia: null,
    oralExaminerUserId: null,
    oralReason: null,
    oralNotes: null,
    discordThreadId: null,
    discordMessageId: null,
    assignedToUserId: null,
    assignedAt: null,
    ...o,
  };
}

/** Corps envoyé par l'ancien launcher (0.3.x) : pas de formVersion, anciens champs. */
const ENVOI_V1: SubmitWhitelistDto = {
  dob: '2006-04-12',
  motivation: 'm'.repeat(200),
  experience: 'e'.repeat(200),
  availability: 'Le soir après 20 h, le samedi.',
  firstName: 'Kaito',
  lastName: 'Arashi',
  village: 'Konohagakure',
  support: null,
  history: 'h'.repeat(300),
  appearance: 'a'.repeat(200),
  objectives: 'o'.repeat(450),
};

const ENVOI_V2: SubmitWhitelistDto = {
  formVersion: 2,
  playerAge: 19,
  rpYears: 'TROIS_A_CINQ_ANS',
  narutoKnowledge: 'BONNE_CONNAISSANCE',
  discoverySource: 'AMI',
  availability: '  En semaine après 20 h.  ',
  firstName: 'Kaito',
  lastName: 'Arashi',
  village: 'Konohagakure',
  characterType: 'NINJA',
  characterSex: 'HOMME',
  characterAge: 16,
  support: '',
  history: 'h'.repeat(1840),
  objectives: 'o'.repeat(450),
  rulesAccepted: true,
  voiceConsent: true,
};

interface Etat {
  user: {
    id: string;
    role: Role;
    oralPassedAt: Date | null;
    whitelistRejectedAt: Date | null;
    minecraftUsername: string;
    discordUserId: string | null;
  };
  app: WhitelistApplication | null;
  brouillon: Record<string, unknown> | null;
}

function monter(etat: Etat) {
  const prisma = {
    user: { findUnique: jest.fn(() => Promise.resolve(etat.user)) },
    whitelistApplication: {
      findUnique: jest.fn(
        (): Promise<(WhitelistApplication & { assignedTo: null }) | null> =>
          Promise.resolve(etat.app ? { ...etat.app, assignedTo: null } : null),
      ),
      create: jest.fn(({ data }: { data: Partial<WhitelistApplication> }) =>
        Promise.resolve(candidature({ ...data })),
      ),
      update: jest.fn(({ data }: { data: Partial<WhitelistApplication> }) =>
        Promise.resolve(candidature({ ...(etat.app ?? {}), ...data })),
      ),
      delete: jest.fn(() => Promise.resolve(etat.app)),
    },
    candidatureBrouillon: {
      findUnique: jest.fn(() => Promise.resolve(etat.brouillon)),
      deleteMany: jest.fn(() =>
        Promise.resolve({ count: etat.brouillon ? 1 : 0 }),
      ),
      upsert: jest.fn(({ create }: { create: Record<string, unknown> }) =>
        Promise.resolve({
          formVersion: 2,
          data: {},
          updatedAt: new Date(),
          ...create,
        }),
      ),
      update: jest.fn(({ data }: { data: Record<string, unknown> }) =>
        Promise.resolve({ ...(etat.brouillon ?? {}), ...data }),
      ),
    },
    douaneReservation: {
      findFirst: jest.fn(() => Promise.resolve(null)),
      updateMany: jest.fn(() => Promise.resolve({ count: 0 })),
    },
    // SELECT … FOR UPDATE : candidature (rien à lire) ou brouillon (sa clé de voix).
    $queryRaw: jest.fn(() =>
      Promise.resolve([{ voiceKey: etat.brouillon?.voiceKey ?? null }]),
    ),
    $transaction: jest.fn(),
  };
  // Transaction interactive (fonction) ou en lot (tableau), sur la même base simulée.
  prisma.$transaction.mockImplementation(
    (arg: ((tx: typeof prisma) => Promise<unknown>) | Promise<unknown>[]) =>
      typeof arg === 'function' ? arg(prisma) : Promise.all(arg),
  );
  const webhooks = {
    whitelistSubmitted: jest.fn(() => Promise.resolve(null)),
    statusUpdate: jest.fn(() => Promise.resolve(undefined)),
  };
  const messages = {
    postSystemMessage: jest.fn(() => Promise.resolve(undefined)),
  };
  const audit = { log: jest.fn(() => Promise.resolve(undefined)) };
  const voix = new WhitelistVoixService(
    prisma as unknown as PrismaService,
    audit as unknown as AuditService,
  );
  const service = new WhitelistService(
    prisma as unknown as PrismaService,
    webhooks as unknown as WebhooksService,
    messages as unknown as WhitelistMessagesService,
    voix,
  );
  return { service, prisma, webhooks, messages };
}

describe('WhitelistService (candidat)', () => {
  let dossier: string;
  const saved = process.env.REBORN_VOICE_DIR;
  let etat: Etat;

  const deposer = (type: 'audio/webm' | 'audio/ogg' = 'audio/webm') => {
    const cle = genererCleVoix(type);
    writeFileSync(join(dossier, cle), Buffer.alloc(2048, 1));
    return cle;
  };

  beforeEach(() => {
    dossier = mkdtempSync(join(tmpdir(), 'reborn-voix-'));
    process.env.REBORN_VOICE_DIR = dossier;
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => {});
    etat = {
      user: {
        id: USER_ID,
        role: Role.PLAYER,
        oralPassedAt: null,
        whitelistRejectedAt: null,
        minecraftUsername: 'Kazuki',
        discordUserId: null,
      },
      app: null,
      brouillon: null,
    };
  });

  afterEach(() => {
    jest.restoreAllMocks();
    rmSync(dossier, { recursive: true, force: true });
    if (saved === undefined) delete process.env.REBORN_VOICE_DIR;
    else process.env.REBORN_VOICE_DIR = saved;
  });

  it('GET /me : jamais les notes privées du staff, ni l’examinateur, ni le nom du fichier de voix', async () => {
    etat.app = candidature({
      hrpStatus: AppStatus.APPROVED,
      rpStatus: AppStatus.APPROVED,
      writtenApprovedAt: new Date(),
      writtenStaffNotes: 'NOTE-PRIVEE-ECRIT',
      oralStatus: AppStatus.NEEDS_REVISION,
      oralDecidedAt: new Date('2026-10-25T19:30:00Z'),
      oralReason: 'Revois la présentation de ton personnage.',
      oralNotes: 'NOTE-PRIVEE-ORAL',
      oralExaminerUserId: '33333333-3333-4333-8333-333333333333',
      voiceKey: deposer(),
      voiceMimeType: 'audio/webm',
      voiceDurationSec: 42,
    });
    const { service } = monter(etat);
    const res = await service.getMine(USER_ID);
    const json = JSON.stringify(res);

    expect(json).not.toContain('NOTE-PRIVEE');
    expect(json).not.toContain('33333333-3333');
    expect(json).not.toContain(etat.app.voiceKey as string);
    expect(res.application).not.toHaveProperty('writtenStaffNotes');
    expect(res.application).not.toHaveProperty('oralNotes');
    expect(res.application).not.toHaveProperty('voiceKey');
    // Ce qui est visible du joueur, lui, y est.
    expect(res.application?.oralReason).toBe(
      'Revois la présentation de ton personnage.',
    );
    expect(res.application?.voix).toMatchObject({
      mimeType: 'audio/webm',
      durationSec: 42,
    });
    expect(res.suivi.phase).toBe('DOUANE_A_REVOIR');
    expect(res.application?.phase).toBe('DOUANE_A_REVOIR');
    // Compatibilité ancien launcher : les champs v1 restent des chaînes.
    expect(res.application).toMatchObject({
      dob: '',
      motivation: '',
      experience: '',
      appearance: '',
    });
  });

  it('GET /me sans candidature : phase BROUILLON', async () => {
    const { service } = monter(etat);
    expect(await service.getMine(USER_ID)).toMatchObject({
      application: null,
      suivi: { phase: 'BROUILLON' },
    });
  });

  it('retrait : la voix de la candidature et celle du brouillon sont effacées du disque', async () => {
    const voixApp = deposer();
    const voixBrouillon = deposer('audio/ogg');
    etat.app = candidature({ voiceKey: voixApp });
    etat.brouillon = { userId: USER_ID, voiceKey: voixBrouillon };
    const { service, prisma } = monter(etat);

    await service.withdraw(USER_ID);

    expect(existsSync(join(dossier, voixApp))).toBe(false);
    expect(existsSync(join(dossier, voixBrouillon))).toBe(false);
    expect(prisma.whitelistApplication.delete).toHaveBeenCalledWith({
      where: { userId: USER_ID },
    });
    expect(prisma.candidatureBrouillon.deleteMany).toHaveBeenCalled();
  });

  it('envoi v2 sans voix dans le brouillon : 400 rattaché au champ « voix »', async () => {
    const { service, prisma } = monter(etat);
    const err = await service
      .submit(USER_ID, ENVOI_V2)
      .catch((e: unknown) => e);
    expect(err).toBeInstanceOf(BadRequestException);
    expect((err as BadRequestException).message).toMatch(/^voix: /);
    expect(prisma.whitelistApplication.create).not.toHaveBeenCalled();
  });

  it('envoi v2 : la voix passe du brouillon à la candidature, consentement daté, champs v1 vides', async () => {
    const cle = deposer();
    etat.brouillon = {
      userId: USER_ID,
      voiceKey: cle,
      voiceMimeType: 'audio/webm',
      voiceDurationSec: 42,
      voiceUploadedAt: new Date(),
    };
    const { service, prisma, messages } = monter(etat);

    const res = await service.submit(USER_ID, ENVOI_V2);

    const data = (
      prisma.whitelistApplication.create.mock.calls[0] as unknown as [
        { data: Record<string, unknown> },
      ]
    )[0].data;
    expect(data).toMatchObject({
      userId: USER_ID,
      formVersion: 2,
      playerAge: 19,
      dob: null,
      motivation: null,
      experience: null,
      appearance: null,
      availability: 'En semaine après 20 h.',
      voiceKey: cle,
      voiceDurationSec: 42,
      status: AppStatus.PENDING,
    });
    expect(data.voiceConsentAt).toBeInstanceOf(Date);
    expect(data.rulesAcceptedAt).toBeInstanceOf(Date);
    // Le brouillon est effacé, pas le fichier repris par la candidature.
    expect(prisma.candidatureBrouillon.deleteMany).toHaveBeenCalled();
    expect(existsSync(join(dossier, cle))).toBe(true);
    expect(res.phase).toBe('ECRIT_EN_ATTENTE');
    expect(messages.postSystemMessage).toHaveBeenCalled();
  });

  it('refus il y a moins d’un jour : nouvelle candidature refusée en 409 avec la date', async () => {
    etat.user.whitelistRejectedAt = new Date(Date.now() - 3600 * 1000);
    etat.app = candidature({
      status: AppStatus.REJECTED,
      rpStatus: AppStatus.REJECTED,
    });
    const { service } = monter(etat);
    const err = await service
      .submit(USER_ID, ENVOI_V2)
      .catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ConflictException);
    expect((err as ConflictException).getResponse()).toMatchObject({
      statusCode: 409,
      code: 'DELAI_REFUS',
      message: expect.stringMatching(
        /^Nouvelle candidature possible le \d\d\/\d\d à /,
      ) as string,
    });
  });

  it('refus passé le délai : nouvelle candidature complète sur la même ligne, HRP et oral remis à zéro', async () => {
    const ancienne = deposer();
    const nouvelle = deposer();
    etat.user.whitelistRejectedAt = new Date(Date.now() - 2 * 24 * 3600 * 1000);
    etat.app = candidature({
      status: AppStatus.REJECTED,
      hrpStatus: AppStatus.APPROVED,
      rpStatus: AppStatus.APPROVED,
      oralStatus: AppStatus.REJECTED,
      writtenStaffNotes: 'ancienne note',
      voiceKey: ancienne,
    });
    etat.brouillon = {
      userId: USER_ID,
      voiceKey: nouvelle,
      voiceMimeType: 'audio/webm',
      voiceDurationSec: 30,
    };
    const { service, prisma } = monter(etat);

    await service.submit(USER_ID, ENVOI_V2);

    const data = (
      prisma.whitelistApplication.update.mock.calls[0] as unknown as [
        { data: Record<string, unknown> },
      ]
    )[0].data;
    expect(data).toMatchObject({
      hrpStatus: AppStatus.PENDING,
      rpStatus: AppStatus.PENDING,
      oralStatus: AppStatus.PENDING,
      status: AppStatus.PENDING,
      writtenApprovedAt: null,
      writtenStaffNotes: null,
      voiceKey: nouvelle,
    });
    expect(existsSync(join(dossier, ancienne))).toBe(false);
    expect(existsSync(join(dossier, nouvelle))).toBe(true);
  });

  it('candidature en cours : POST refusé en 409', async () => {
    etat.app = candidature();
    const { service } = monter(etat);
    await expect(service.submit(USER_ID, ENVOI_V2)).rejects.toBeInstanceOf(
      ConflictException,
    );
  });

  it('« RP à revoir » : seul le RP est repris, l’HRP validé reste verrouillé', async () => {
    etat.app = candidature({
      hrpStatus: AppStatus.APPROVED,
      rpStatus: AppStatus.NEEDS_REVISION,
      status: AppStatus.NEEDS_REVISION,
      voiceKey: deposer(),
    });
    const { service, prisma } = monter(etat);

    const res = await service.resubmit(USER_ID, {
      ...ENVOI_V2,
      playerAge: 30,
      firstName: 'Kaiko',
    });

    const data = (
      prisma.whitelistApplication.update.mock.calls[0] as unknown as [
        { data: Record<string, unknown> },
      ]
    )[0].data;
    expect(data).toMatchObject({
      firstName: 'Kaiko',
      rpStatus: AppStatus.PENDING,
      status: AppStatus.PENDING,
    });
    expect(data).not.toHaveProperty('playerAge');
    expect(data).not.toHaveProperty('hrpStatus');
    expect(res.phase).toBe('ECRIT_EN_ATTENTE');
  });

  it('« oral à revoir » : pas de renvoi de l’écrit, il faut réserver une nouvelle séance', async () => {
    etat.app = candidature({
      hrpStatus: AppStatus.APPROVED,
      rpStatus: AppStatus.APPROVED,
      oralStatus: AppStatus.NEEDS_REVISION,
      oralDecidedAt: new Date(),
    });
    const { service } = monter(etat);
    await expect(service.resubmit(USER_ID, ENVOI_V2)).rejects.toBeInstanceOf(
      ConflictException,
    );
  });

  describe('plus de voie « ancien format » (complément du 25/10)', () => {
    /** Erreur levée, avec le corps de réponse (statut et message exacts). */
    const refus = async (p: Promise<unknown>) => {
      const err = await p.catch((e: unknown) => e);
      expect(err).toBeInstanceOf(BadRequestException);
      return (err as BadRequestException).getResponse();
    };

    it('POST sans formVersion (ancien launcher) : 400 « Mets ton launcher à jour… », rien d’écrit, brouillon gardé', async () => {
      etat.brouillon = {
        userId: USER_ID,
        voiceKey: deposer(),
        voiceMimeType: 'audio/webm',
        voiceDurationSec: 30,
      };
      const { service, prisma, webhooks } = monter(etat);

      expect(await refus(service.submit(USER_ID, ENVOI_V1))).toEqual({
        statusCode: 400,
        message: MSG_LAUNCHER,
        error: 'Bad Request',
      });
      expect(prisma.whitelistApplication.create).not.toHaveBeenCalled();
      expect(prisma.candidatureBrouillon.deleteMany).not.toHaveBeenCalled();
      expect(webhooks.whitelistSubmitted).not.toHaveBeenCalled();
      expect(existsSync(join(dossier, etat.brouillon.voiceKey as string))).toBe(
        true,
      );
    });

    it('formVersion 1, inconnu ou en texte : même refus ; formVersion 2 : accepté', async () => {
      const { service, prisma } = monter(etat);
      for (const formVersion of [1, 3, '2'])
        await expect(
          service.submit(USER_ID, {
            ...ENVOI_V2,
            formVersion,
          } as unknown as SubmitWhitelistDto),
        ).rejects.toThrow(MSG_LAUNCHER);
      expect(prisma.whitelistApplication.create).not.toHaveBeenCalled();

      etat.brouillon = {
        userId: USER_ID,
        voiceKey: deposer(),
        voiceMimeType: 'audio/webm',
        voiceDurationSec: 30,
      };
      await expect(service.submit(USER_ID, ENVOI_V2)).resolves.toMatchObject({
        formVersion: 2,
      });
    });

    it('le refus passe avant tout autre contrôle (candidature déjà en cours, délai après refus)', async () => {
      etat.app = candidature();
      const { service } = monter(etat);
      await expect(service.submit(USER_ID, ENVOI_V1)).rejects.toThrow(
        MSG_LAUNCHER,
      );
      etat.user.whitelistRejectedAt = new Date(Date.now() - 3600 * 1000);
      etat.app = candidature({
        status: AppStatus.REJECTED,
        rpStatus: AppStatus.REJECTED,
      });
      await expect(service.submit(USER_ID, ENVOI_V1)).rejects.toThrow(
        MSG_LAUNCHER,
      );
    });

    it('PATCH sans formVersion : même 400, que le dossier soit v2 ou de l’ancien format', async () => {
      for (const formVersion of [2, 1]) {
        etat.app = candidature({
          formVersion,
          hrpStatus: AppStatus.APPROVED,
          rpStatus: AppStatus.NEEDS_REVISION,
          status: AppStatus.NEEDS_REVISION,
          voiceKey: deposer(),
        });
        const { service, prisma } = monter(etat);
        expect(await refus(service.resubmit(USER_ID, ENVOI_V1))).toEqual({
          statusCode: 400,
          message: MSG_LAUNCHER,
          error: 'Bad Request',
        });
        expect(prisma.whitelistApplication.update).not.toHaveBeenCalled();
      }
    });

    it('PATCH sans formVersion après un refus : même 400, pas de nouvelle candidature', async () => {
      etat.user.whitelistRejectedAt = new Date(
        Date.now() - 2 * 24 * 3600 * 1000,
      );
      etat.app = candidature({
        status: AppStatus.REJECTED,
        rpStatus: AppStatus.REJECTED,
      });
      const { service, prisma } = monter(etat);
      await expect(service.resubmit(USER_ID, ENVOI_V1)).rejects.toThrow(
        MSG_LAUNCHER,
      );
      expect(prisma.whitelistApplication.update).not.toHaveBeenCalled();
    });

    it('lecture d’un ancien dossier (formVersion 1) : GET /me le rend tel quel', async () => {
      etat.app = candidature({
        formVersion: 1,
        playerAge: null,
        rpYears: null,
        narutoKnowledge: null,
        characterType: null,
        characterSex: null,
        characterAge: null,
        dob: new Date('2006-04-12'),
        motivation: 'm'.repeat(200),
        experience: 'e'.repeat(200),
        appearance: 'a'.repeat(200),
        voiceConsentAt: null,
      });
      const { service } = monter(etat);

      const res = await service.getMine(USER_ID);

      expect(res.application).toMatchObject({
        formVersion: 1,
        dob: '2006-04-12',
        motivation: 'm'.repeat(200),
        experience: 'e'.repeat(200),
        appearance: 'a'.repeat(200),
        playerAge: null,
        voix: null,
        phase: 'ECRIT_EN_ATTENTE',
      });
      expect(res.suivi.phase).toBe('ECRIT_EN_ATTENTE');
    });
  });

  it('dossier v1 (HRP validé, RP à revoir) renvoyé en v2 sans champs HRP : accepté, passe en v2, HRP v1 gardé', async () => {
    etat.app = candidature({
      formVersion: 1,
      playerAge: null,
      rpYears: null,
      narutoKnowledge: null,
      dob: new Date('2006-04-12'),
      motivation: 'm'.repeat(200),
      experience: 'e'.repeat(200),
      appearance: 'a'.repeat(200),
      characterType: null,
      characterSex: null,
      characterAge: null,
      lastName: 'Uchiha',
      hrpStatus: AppStatus.APPROVED,
      rpStatus: AppStatus.NEEDS_REVISION,
      status: AppStatus.NEEDS_REVISION,
    });
    const cle = deposer();
    etat.brouillon = {
      userId: USER_ID,
      voiceKey: cle,
      voiceMimeType: 'audio/webm',
      voiceDurationSec: 40,
    };
    const { service, prisma } = monter(etat);
    const {
      playerAge: _a,
      rpYears: _r,
      narutoKnowledge: _n,
      ...rpSeul
    } = ENVOI_V2;
    void _a;
    void _r;
    void _n;

    const res = await service.resubmit(USER_ID, rpSeul);

    const data = (
      prisma.whitelistApplication.update.mock.calls[0] as unknown as [
        { data: Record<string, unknown> },
      ]
    )[0].data;
    expect(data).toMatchObject({
      formVersion: 2,
      lastName: 'Arashi',
      appearance: null,
      characterType: 'NINJA',
      voiceKey: cle,
      rpStatus: AppStatus.PENDING,
    });
    for (const c of ['dob', 'motivation', 'experience', 'playerAge'])
      expect(data).not.toHaveProperty(c);
    expect(data).not.toHaveProperty('hrpStatus');
    expect(res.phase).toBe('ECRIT_EN_ATTENTE');
  });

  it('renvoi partiel : les champs d’une partie verrouillée sont ignorés, ceux de la partie rouverte contrôlés', async () => {
    etat.app = candidature({
      hrpStatus: AppStatus.APPROVED,
      rpStatus: AppStatus.NEEDS_REVISION,
      status: AppStatus.NEEDS_REVISION,
      voiceKey: deposer(),
    });
    const { service } = monter(etat);
    // playerAge hors bornes mais partie HRP verrouillée : ignoré.
    await expect(
      service.resubmit(USER_ID, { ...ENVOI_V2, playerAge: 3 }),
    ).resolves.toBeDefined();
    const err = await service
      .resubmit(USER_ID, { ...ENVOI_V2, lastName: 'Uchiha' })
      .catch((e: unknown) => e);
    expect(err).toBeInstanceOf(BadRequestException);
    expect((err as BadRequestException).getResponse()).toMatchObject({
      message: [
        expect.stringMatching(/^lastName: « Uchiha » est un nom de clan/),
      ],
    });
  });

  it('renvoi croisé avec une décision du staff (relue sous verrou) : 409, rien d’écrasé', async () => {
    etat.app = candidature({
      hrpStatus: AppStatus.APPROVED,
      rpStatus: AppStatus.NEEDS_REVISION,
      status: AppStatus.NEEDS_REVISION,
      voiceKey: deposer(),
    });
    const { service, prisma } = monter(etat);
    // 1re lecture : à revoir ; relecture sous verrou : le staff vient de refuser le RP.
    prisma.whitelistApplication.findUnique
      .mockResolvedValueOnce({ ...etat.app, assignedTo: null })
      .mockResolvedValueOnce({
        ...etat.app,
        rpStatus: AppStatus.REJECTED,
        status: AppStatus.REJECTED,
        assignedTo: null,
      });
    await expect(service.resubmit(USER_ID, ENVOI_V2)).rejects.toThrow(
      'Ta candidature vient d’être relue : recharge-la.',
    );
    expect(prisma.whitelistApplication.update).not.toHaveBeenCalled();
    expect(prisma.$queryRaw).toHaveBeenCalled();
  });

  it('dossier de l’ancien format, « RP à revoir » : le renvoi v2 exige la voix', async () => {
    etat.app = candidature({
      formVersion: 1,
      playerAge: null,
      dob: new Date('2006-04-12'),
      motivation: 'm'.repeat(200),
      experience: 'e'.repeat(200),
      appearance: 'a'.repeat(200),
      hrpStatus: AppStatus.APPROVED,
      rpStatus: AppStatus.NEEDS_REVISION,
      status: AppStatus.NEEDS_REVISION,
    });
    const { service, prisma } = monter(etat);
    const err = await service
      .resubmit(USER_ID, ENVOI_V2)
      .catch((e: unknown) => e);
    expect(err).toBeInstanceOf(BadRequestException);
    expect((err as BadRequestException).message).toMatch(/^voix: /);
    expect(prisma.whitelistApplication.update).not.toHaveBeenCalled();
  });

  it('dossier de l’ancien format, « HRP à revoir » seul : renvoi v2, l’HRP passe au format v2, le dossier reste v1', async () => {
    etat.app = candidature({
      formVersion: 1,
      playerAge: null,
      rpYears: null,
      narutoKnowledge: null,
      dob: new Date('2006-04-12'),
      motivation: 'm'.repeat(200),
      experience: 'e'.repeat(200),
      appearance: 'a'.repeat(200),
      hrpStatus: AppStatus.NEEDS_REVISION,
      rpStatus: AppStatus.APPROVED,
      status: AppStatus.NEEDS_REVISION,
    });
    const { service, prisma } = monter(etat);

    await service.resubmit(USER_ID, ENVOI_V2);

    const data = (
      prisma.whitelistApplication.update.mock.calls[0] as unknown as [
        { data: Record<string, unknown> },
      ]
    )[0].data;
    expect(data).toMatchObject({
      formVersion: 1,
      playerAge: 19,
      dob: null,
      motivation: null,
      experience: null,
      hrpStatus: AppStatus.PENDING,
    });
    // Partie RP validée : verrouillée, rien d'écrit (apparence v1 gardée).
    for (const c of ['appearance', 'firstName', 'rpStatus', 'voiceKey'])
      expect(data).not.toHaveProperty(c);
  });

  it('dépôt de la voix refusé pendant la relecture de l’écrit', async () => {
    etat.app = candidature();
    const { service } = monter(etat);
    const webm = Buffer.concat([
      Buffer.from([0x1a, 0x45, 0xdf, 0xa3]),
      Buffer.alloc(4096),
    ]);
    await expect(
      service.deposerVoix(USER_ID, 'audio/webm', '42', webm),
    ).rejects.toBeInstanceOf(ConflictException);
  });

  it('dépôt de la voix au brouillon : un nouveau dépôt remplace et efface l’ancien fichier', async () => {
    const ancienne = deposer();
    etat.brouillon = { userId: USER_ID, voiceKey: ancienne };
    const { service, prisma } = monter(etat);
    const webm = Buffer.concat([
      Buffer.from([0x1a, 0x45, 0xdf, 0xa3]),
      Buffer.alloc(4096),
    ]);

    const res = await service.deposerVoix(
      USER_ID,
      'audio/webm;codecs=opus',
      '42',
      webm,
    );

    expect(res.voix).toMatchObject({
      mimeType: 'audio/webm',
      durationSec: 42,
      octets: webm.length,
    });
    expect(prisma.candidatureBrouillon.upsert).toHaveBeenCalled();
    // Ancienne clé lue sous le verrou du brouillon, puis son fichier effacé.
    expect(prisma.$queryRaw).toHaveBeenCalled();
    expect(prisma.candidatureBrouillon.update).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ voiceDurationSec: 42 }) as object,
      }),
    );
    expect(existsSync(join(dossier, ancienne))).toBe(false);
  });

  it('dépôt de la voix : durée hors bornes ou octets d’un autre format refusés', async () => {
    const { service } = monter(etat);
    const webm = Buffer.concat([
      Buffer.from([0x1a, 0x45, 0xdf, 0xa3]),
      Buffer.alloc(4096),
    ]);
    await expect(
      service.deposerVoix(USER_ID, 'audio/webm', '12', webm),
    ).rejects.toThrow('15 secondes au moins.');
    await expect(
      service.deposerVoix(USER_ID, 'audio/ogg', '42', webm),
    ).rejects.toBeInstanceOf(BadRequestException);
  });
});
