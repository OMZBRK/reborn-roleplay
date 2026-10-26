import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import {
  AppStatus,
  DouaneReservationStatut,
  OralVia,
  Role,
} from '@prisma/client';
import type { DecisionDouaneJeuDto } from './dto/game-douane.dto';
import { GameDouaneService } from './game-douane.service';

// Routes signées du jeu, côté métier (CANDIDATURE_ET_DOUANE.md §3.7, §3.8,
// §3.9) : l'examinateur doit avoir le rôle web avec CE compte Minecraft, la
// décision passe par decideOral (via DOUANE), le drapeau douane est respecté.

const UUID_CANDIDAT = '11111111-1111-4111-8111-111111111111';
const UUID_EXAMINATEUR = '22222222-2222-4222-8222-222222222222';
const UUID_SECOND_COMPTE = '33333333-3333-4333-8333-333333333333';
const UUID_CANDIDAT_BANNI = '88888888-8888-4888-8888-888888888888';
const H = new Date('2026-10-24T19:00:00Z');

const comptes: Record<string, Record<string, unknown>> = {
  [UUID_CANDIDAT]: {
    id: 'u-candidat',
    minecraftUuid: UUID_CANDIDAT,
    minecraftUsername: 'Kaito',
    role: Role.PLAYER,
    banned: false,
    oralPassedAt: null,
    whitelistRejectedAt: null,
  },
  [UUID_EXAMINATEUR]: {
    id: 'u-examinateur',
    minecraftUuid: UUID_EXAMINATEUR,
    minecraftUsername: 'Garde',
    role: Role.WHITELIST_REVIEWER,
    banned: false,
    oralPassedAt: null,
    whitelistRejectedAt: null,
  },
  // Le même membre du staff, sur un second compte Minecraft : rôle web absent.
  [UUID_SECOND_COMPTE]: {
    id: 'u-second',
    minecraftUuid: UUID_SECOND_COMPTE,
    minecraftUsername: 'GardeAlt',
    role: Role.WHITELISTED,
    banned: false,
    oralPassedAt: null,
    whitelistRejectedAt: null,
  },
  // Candidat banni pendant son passage de douane.
  [UUID_CANDIDAT_BANNI]: {
    id: 'u-banni',
    minecraftUuid: UUID_CANDIDAT_BANNI,
    minecraftUsername: 'Exclu',
    role: Role.PLAYER,
    banned: true,
    oralPassedAt: null,
    whitelistRejectedAt: null,
  },
};

const candidature = {
  id: 'app1',
  userId: 'u-candidat',
  status: AppStatus.PENDING,
  hrpStatus: AppStatus.APPROVED,
  rpStatus: AppStatus.APPROVED,
  oralStatus: AppStatus.PENDING,
  oralDecidedAt: null,
  reviewedAt: new Date('2026-10-20T10:00:00Z'),
  writtenApprovedAt: new Date('2026-10-20T10:00:00Z'),
  writtenStaffNotes: 'Creuser la motivation du départ du village.',
  oralReason: null,
  formVersion: 2,
  playerAge: 17,
  dob: null,
  rpYears: 'UN_A_DEUX_ANS',
  narutoKnowledge: 'BONNE_CONNAISSANCE',
  firstName: 'Kaito',
  lastName: 'Arashi',
  village: 'Konohagakure',
  characterSex: 'HOMME',
  characterAge: 16,
  characterType: 'NINJA',
  history: 'Ligne 1\nLigne 2\n\nLigne 3\nLigne 4',
  objectives: 'Devenir chûnin.',
  availability: 'Soirs et week-ends.',
  motivation: null,
  experience: null,
  appearance: null,
  voiceKey: 'voix.webm',
  voiceDeletedAt: null,
  voiceDurationSec: 42,
};

const place = {
  id: 'r1',
  seanceId: 's1',
  convocationAt: H,
  presentAt: new Date(H.getTime() - 5 * 60 * 1000),
  calledAt: null,
};

function decisionDto(
  over: Partial<DecisionDouaneJeuDto> = {},
): DecisionDouaneJeuDto {
  return {
    mcUuidCandidat: UUID_CANDIDAT,
    mcUuidExaminateur: UUID_EXAMINATEUR,
    reservationId: 'r1',
    resultat: AppStatus.APPROVED,
    ...over,
  };
}

describe('GameDouaneService', () => {
  let prisma: {
    whitelistApplication: { findUnique: jest.Mock };
    douaneReservation: { findFirst: jest.Mock };
  };
  let oral: { decideOral: jest.Mock };
  let douane: { presence: jest.Mock; annulerPlaceDepuisJeu: jest.Mock };
  let audit: { log: jest.Mock };

  function service(
    env: Record<string, string | undefined> = {
      REBORN_DOUANE_ENABLED: 'true',
      ADMIN_BASE_URL: 'https://panel.exemple.test/',
    },
  ): GameDouaneService {
    const config = { get: jest.fn((k: string) => env[k]) };
    const game = {
      findUserByMcUuid: jest.fn((u: string) =>
        Promise.resolve(comptes[u] ?? null),
      ),
    };
    return new GameDouaneService(
      config as never,
      prisma as never,
      game as never,
      oral as never,
      douane as never,
      audit as never,
    );
  }

  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'debug').mockImplementation(() => undefined);
    prisma = {
      whitelistApplication: {
        findUnique: jest.fn().mockResolvedValue(candidature),
      },
      douaneReservation: { findFirst: jest.fn().mockResolvedValue(place) },
    };
    oral = {
      decideOral: jest.fn().mockResolvedValue({
        applicationId: 'app1',
        userId: 'u-candidat',
        resultat: AppStatus.APPROVED,
        status: AppStatus.APPROVED,
        oralStatus: AppStatus.APPROVED,
        oralVia: OralVia.DOUANE,
        oralDecidedAt: H.toISOString(),
        phase: 'ACCEPTE',
        prochaineSeanceDes: null,
        canReapplyAt: null,
        reservation: {
          id: 'r1',
          seanceId: 's1',
          statut: DouaneReservationStatut.DONE,
          resultat: AppStatus.APPROVED,
        },
      }),
    };
    douane = {
      presence: jest.fn().mockResolvedValue({
        id: 'r1',
        presentAt: H.toISOString(),
        calledAt: null,
        departAt: null,
      }),
      annulerPlaceDepuisJeu: jest.fn().mockResolvedValue({
        id: 'r1',
        presentAt: H.toISOString(),
        calledAt: null,
        departAt: H.toISOString(),
      }),
    };
    audit = { log: jest.fn().mockResolvedValue(undefined) };
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  // ── Décision ──────────────────────────────────────────────────────────

  it('décision : passe par decideOral (via DOUANE, source game, place pinglée), audit avec le serveur', async () => {
    const res = await service().decision(
      decisionDto({ motif: 'Très bon oral', notesPrivees: 'RAS' }),
      'dev',
    );
    expect(oral.decideOral).toHaveBeenCalledWith(
      'app1',
      'u-examinateur',
      AppStatus.APPROVED,
      'Très bon oral',
      'RAS',
      { via: OralVia.DOUANE, source: 'game', reservationId: 'r1' },
    );
    expect(res).toEqual({
      ok: true,
      dejaTraitee: false,
      resultat: AppStatus.APPROVED,
      phase: 'ACCEPTE',
      applicationId: 'app1',
      reservationId: 'r1',
      prochaineSeanceDes: null,
      canReapplyAt: null,
    });
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'u-examinateur',
        action: 'douane.jeu.decision',
        source: 'game',
        metadata: expect.objectContaining({
          serveur: 'dev',
          issue: 'enregistree',
        }) as object,
      }),
    );
  });

  it('second compte Minecraft d’un staff (sans rôle web) : 403, rien n’est tranché', async () => {
    await expect(
      service().decision(
        decisionDto({ mcUuidExaminateur: UUID_SECOND_COMPTE }),
        'dev',
      ),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(oral.decideOral).not.toHaveBeenCalled();
    // Le refus d'un compte existant est tracé.
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'u-second',
        metadata: expect.objectContaining({
          issue: 'examinateur_refuse',
        }) as object,
      }),
    );
  });

  it('examinateur inconnu de l’API : 403', async () => {
    await expect(
      service().decision(
        decisionDto({
          mcUuidExaminateur: '44444444-4444-4444-8444-444444444444',
        }),
        'dev',
      ),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(oral.decideOral).not.toHaveBeenCalled();
  });

  it('examinateur banni : 403', async () => {
    const banni = { ...comptes[UUID_EXAMINATEUR], banned: true };
    comptes['55555555-5555-4555-8555-555555555555'] = banni;
    await expect(
      service().decision(
        decisionDto({
          mcUuidExaminateur: '55555555-5555-4555-8555-555555555555',
        }),
        'dev',
      ),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('trancher sa propre candidature : 403', async () => {
    await expect(
      service().decision(
        decisionDto({ mcUuidCandidat: UUID_EXAMINATEUR }),
        'dev',
      ),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(oral.decideOral).not.toHaveBeenCalled();
  });

  it('candidat banni pendant son passage : 403, jamais promu, refus tracé', async () => {
    await expect(
      service().decision(
        decisionDto({ mcUuidCandidat: UUID_CANDIDAT_BANNI }),
        'dev',
      ),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(oral.decideOral).not.toHaveBeenCalled();
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'u-examinateur',
        action: 'douane.jeu.decision',
        targetUserId: 'u-banni',
        source: 'game',
        metadata: expect.objectContaining({
          serveur: 'dev',
          reservationId: 'r1',
          issue: 'candidat_banni',
        }) as object,
      }),
    );
  });

  it('candidat sans compte Reborn : 404, refus tracé', async () => {
    const inconnu = '99999999-9999-4999-8999-999999999999';
    await expect(
      service().decision(decisionDto({ mcUuidCandidat: inconnu }), 'dev'),
    ).rejects.toBeInstanceOf(NotFoundException);
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'u-examinateur',
        action: 'douane.jeu.decision',
        metadata: expect.objectContaining({
          serveur: 'dev',
          issue: 'candidat_inconnu',
          mcUuidCandidat: inconnu,
        }) as object,
      }),
    );
  });

  it('candidat sans candidature : 404, refus tracé', async () => {
    prisma.whitelistApplication.findUnique.mockResolvedValueOnce(null);
    await expect(
      service().decision(decisionDto(), 'dev'),
    ).rejects.toBeInstanceOf(NotFoundException);
    expect(oral.decideOral).not.toHaveBeenCalled();
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'u-examinateur',
        targetUserId: 'u-candidat',
        metadata: expect.objectContaining({
          issue: 'sans_candidature',
        }) as object,
      }),
    );
  });

  it('douane coupée (REBORN_DOUANE_ENABLED) : 403 sur dossier, décision et présence', async () => {
    const s = service({});
    await expect(s.decision(decisionDto(), 'dev')).rejects.toBeInstanceOf(
      ForbiddenException,
    );
    await expect(
      s.dossier(UUID_CANDIDAT, UUID_EXAMINATEUR, 'dev'),
    ).rejects.toBeInstanceOf(ForbiddenException);
    await expect(
      s.presence({ mcUuid: UUID_CANDIDAT, evenement: 'arrivee' }, 'dev'),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(oral.decideOral).not.toHaveBeenCalled();
  });

  it('nouvelle tentative d’une décision déjà enregistrée : 200, dejaTraitee', async () => {
    oral.decideOral.mockRejectedValueOnce(
      new ConflictException('Aucune place de douane en cours'),
    );
    prisma.douaneReservation.findFirst.mockResolvedValueOnce({ id: 'r1' });
    prisma.whitelistApplication.findUnique
      .mockResolvedValueOnce({ id: 'app1' })
      .mockResolvedValueOnce({
        ...candidature,
        status: AppStatus.APPROVED,
        oralStatus: AppStatus.APPROVED,
        user: {
          role: Role.WHITELISTED,
          oralPassedAt: H,
          whitelistRejectedAt: null,
        },
      });
    const res = await service().decision(decisionDto(), 'dev');
    expect(res).toMatchObject({
      ok: true,
      dejaTraitee: true,
      phase: 'ACCEPTE',
      reservationId: 'r1',
    });
    expect(prisma.douaneReservation.findFirst).toHaveBeenCalledWith({
      where: {
        id: 'r1',
        applicationId: 'app1',
        statut: DouaneReservationStatut.DONE,
        resultat: AppStatus.APPROVED,
        examinateurUserId: 'u-examinateur',
      },
      select: { id: true },
    });
  });

  it('conflit sans décision identique (autre place en cours) : 409 renvoyé tel quel', async () => {
    oral.decideOral.mockRejectedValueOnce(
      new ConflictException("La place indiquée n'est plus celle en cours."),
    );
    prisma.douaneReservation.findFirst.mockResolvedValueOnce(null);
    await expect(
      service().decision(decisionDto(), 'dev'),
    ).rejects.toBeInstanceOf(ConflictException);
  });

  // ── Dossier ───────────────────────────────────────────────────────────

  it('dossier : fiche résumée, note privée de l’écrit, lien panel, pas de voix ; lecture tracée', async () => {
    const d = await service().dossier(UUID_CANDIDAT, UUID_EXAMINATEUR, 'dev');
    expect(d).toMatchObject({
      applicationId: 'app1',
      candidat: {
        pseudo: 'Kaito',
        ageDeclare: 17,
        anneesRp: '1 à 2 ans',
        connaissanceNaruto: 'Bonne connaissance',
      },
      personnage: {
        prenom: 'Kaito',
        nomFamille: 'Arashi',
        sexe: 'Homme',
        age: 16,
        village: 'Konohagakure',
        type: 'Ninja',
      },
      noteEcrit: {
        texte: 'Creuser la motivation du départ du village.',
        tronquee: false,
      },
      voix: { presente: true, dureeSec: 42 },
      passage: { reservationId: 'r1', deuxiemePassage: false },
      lienPanel: 'https://panel.exemple.test/whitelist/app1',
    });
    const histoire = d.extraits.find((e) => e.champ === 'history');
    expect(histoire).toEqual({
      champ: 'history',
      titre: 'Histoire',
      texte: 'Ligne 1\nLigne 2\nLigne 3',
      tronque: true,
      caracteres: candidature.history.length,
    });
    expect(JSON.stringify(d)).not.toContain('voix.webm');
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'u-examinateur',
        action: 'douane.dossier.read',
        targetUserId: 'u-candidat',
        source: 'game',
      }),
    );
  });

  it('dossier hors passage (écrit en attente) : 409', async () => {
    prisma.whitelistApplication.findUnique.mockResolvedValueOnce({
      ...candidature,
      rpStatus: AppStatus.PENDING,
    });
    await expect(
      service().dossier(UUID_CANDIDAT, UUID_EXAMINATEUR, 'dev'),
    ).rejects.toBeInstanceOf(ConflictException);
  });

  it('dossier d’un candidat banni : 403, rien n’est lu', async () => {
    await expect(
      service().dossier(UUID_CANDIDAT_BANNI, UUID_EXAMINATEUR, 'dev'),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(prisma.whitelistApplication.findUnique).not.toHaveBeenCalled();
    expect(audit.log).not.toHaveBeenCalled();
  });

  it('dossier demandé par un compte sans rôle web : 403', async () => {
    await expect(
      service().dossier(UUID_CANDIDAT, UUID_SECOND_COMPTE, 'dev'),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(audit.log).not.toHaveBeenCalled();
  });

  // ── Accès ─────────────────────────────────────────────────────────────

  it('accès : candidat dans sa fenêtre → douane, avec la place pour la file', async () => {
    jest.useFakeTimers({ now: new Date(H.getTime() - 5 * 60 * 1000) });
    const a = await service().acces(UUID_CANDIDAT, 'dev');
    expect(a).toMatchObject({
      portee: 'douane',
      raison: 'DOUANE_FENETRE_OUVERTE',
      pseudo: 'Kaito',
      douaneActive: true,
      candidature: { found: false, staff: false },
      douane: {
        reservationId: 'r1',
        convocationAt: H.toISOString(),
        entreeOuvreAt: '2026-10-24T18:45:00.000Z',
        entreeFermeAt: '2026-10-24T19:30:00.000Z',
        deuxiemePassage: false,
        village: 'Konohagakure',
      },
    });
    // L'âge (verrouillé à la création, Q13), le type et le sexe sont lus pour le bloc candidature.
    expect(prisma.whitelistApplication.findUnique).toHaveBeenCalledWith(
      expect.objectContaining({
        select: expect.objectContaining({
          id: true,
          characterAge: true,
          characterType: true,
          characterSex: true,
        }) as object,
      }),
    );
  });

  it('accès : UUID inconnu → aucune, sans erreur', async () => {
    const a = await service().acces(
      '66666666-6666-4666-8666-666666666666',
      'dev',
    );
    expect(a).toMatchObject({
      portee: 'aucune',
      raison: 'COMPTE_INCONNU',
      pseudo: null,
      douane: null,
    });
  });

  // ── Présence ──────────────────────────────────────────────────────────

  it('présence : « appel » écrit par DouaneService, acteur = examinateur reconnu', async () => {
    const res = await service().presence(
      {
        mcUuid: UUID_CANDIDAT,
        evenement: 'appel',
        mcUuidExaminateur: UUID_EXAMINATEUR,
      },
      'dev',
    );
    expect(douane.presence).toHaveBeenCalledWith('r1', 'APPEL');
    expect(res).toMatchObject({ ok: true, reservationId: 'r1' });
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'u-examinateur',
        action: 'douane.presence.appel',
      }),
    );
  });

  it('présence « retrait » : examinateur reconnu et motif exigés, place annulée avec le motif', async () => {
    const res = await service().presence(
      {
        mcUuid: UUID_CANDIDAT,
        evenement: 'retrait',
        mcUuidExaminateur: UUID_EXAMINATEUR,
        reservationId: 'r1',
        motif: '  Trouble le hall.  ',
      },
      'dev',
    );
    expect(douane.annulerPlaceDepuisJeu).toHaveBeenCalledWith(
      'r1',
      'RETRAIT',
      'Trouble le hall.',
    );
    expect(douane.presence).not.toHaveBeenCalled();
    expect(res).toMatchObject({ ok: true, reservationId: 'r1' });
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'u-examinateur',
        action: 'douane.presence.retrait',
        metadata: { serveur: 'dev', motif: 'Trouble le hall.' },
      }),
    );

    // Sans motif, sans examinateur, ou examinateur sans rôle web : rien n'est annulé.
    douane.annulerPlaceDepuisJeu.mockClear();
    await expect(
      service().presence(
        {
          mcUuid: UUID_CANDIDAT,
          evenement: 'retrait',
          mcUuidExaminateur: UUID_EXAMINATEUR,
        },
        'dev',
      ),
    ).rejects.toBeInstanceOf(BadRequestException);
    await expect(
      service().presence(
        { mcUuid: UUID_CANDIDAT, evenement: 'retrait', motif: 'Trouble.' },
        'dev',
      ),
    ).rejects.toBeInstanceOf(BadRequestException);
    await expect(
      service().presence(
        {
          mcUuid: UUID_CANDIDAT,
          evenement: 'retrait',
          mcUuidExaminateur: UUID_SECOND_COMPTE,
          motif: 'Trouble.',
        },
        'dev',
      ),
    ).rejects.toBeInstanceOf(ForbiddenException);
    expect(douane.annulerPlaceDepuisJeu).not.toHaveBeenCalled();
  });

  it('présence « report » : place libérée sans motif d’examinateur, acteur = candidat', async () => {
    await service().presence(
      { mcUuid: UUID_CANDIDAT, evenement: 'report', motif: 'ignoré' },
      'dev',
    );
    expect(douane.annulerPlaceDepuisJeu).toHaveBeenCalledWith(
      'r1',
      'REPORT',
      undefined,
    );
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'u-candidat',
        action: 'douane.presence.report',
      }),
    );
  });

  it('présence sans place en cours : 409 ; joueur inconnu : 404', async () => {
    prisma.douaneReservation.findFirst.mockResolvedValue(null);
    await expect(
      service().presence(
        { mcUuid: UUID_CANDIDAT, evenement: 'arrivee' },
        'dev',
      ),
    ).rejects.toBeInstanceOf(ConflictException);
    await expect(
      service().presence(
        {
          mcUuid: '77777777-7777-4777-8777-777777777777',
          evenement: 'arrivee',
        },
        'dev',
      ),
    ).rejects.toBeInstanceOf(NotFoundException);
  });
});
