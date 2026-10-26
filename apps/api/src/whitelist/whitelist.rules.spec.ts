import { AppStatus, Role } from '@prisma/client';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { SubmitWhitelistDto } from './dto/whitelist.dto';
import {
  ageDepuisNaissance,
  ageMinimum,
  calculerSuivi,
  CHAMPS_ENVOI,
  CHAMPS_ENVOI_COMMUNS,
  CHAMPS_ENVOI_RP,
  debutLendemainParis,
  EntreePhase,
  estEnvoiV2,
  estNomDeClan,
  formatDateParis,
  heureCourteParis,
  MSG_LAUNCHER_A_JOUR,
  statutGlobal,
  verifierBrouillon,
  verifierChamp,
  verifierEnvoi,
} from './whitelist.rules';

// Candidature v2 (CANDIDATURE_ET_DOUANE.md §2.3, §2.4, §7) : seul formVersion 2
// s'envoie (complément du 25/10), bornes vérifiées par l'API, statut global et
// phases.

const txt = (n: number) => 'a'.repeat(n);

/** Envoi de l'ancien launcher (sans formVersion), tel qu'il part aujourd'hui : refusé d'un bloc par le service. */
const ENVOI_V1 = {
  dob: '2006-04-12',
  motivation: txt(200),
  experience: txt(200),
  availability: 'Le soir après 20 h, le samedi.',
  firstName: 'Kaito',
  lastName: 'Arashi',
  village: 'Konohagakure',
  support: '',
  history: txt(300),
  appearance: txt(200),
  objectives: txt(450),
};

/** Envoi complet de la candidature v2. */
const ENVOI_V2 = {
  formVersion: 2,
  playerAge: 19,
  rpYears: 'TROIS_A_CINQ_ANS',
  narutoKnowledge: 'BONNE_CONNAISSANCE',
  discoverySource: 'AMI',
  availability: 'En semaine après 20 h.',
  firstName: 'Kaito',
  lastName: 'Arashi',
  village: 'Konohagakure',
  characterType: 'NINJA',
  characterSex: 'HOMME',
  characterAge: 16,
  support: 'https://www.canva.com/design/kaito',
  history: txt(1840),
  objectives: txt(450),
  rulesAccepted: true,
  voiceConsent: true,
};

/** Valide comme le ValidationPipe global (whitelist + forbidNonWhitelisted). Renvoie les messages. */
async function erreurs(corps: Record<string, unknown>): Promise<string[]> {
  const dto = plainToInstance(SubmitWhitelistDto, corps);
  const res = await validate(dto, {
    whitelist: true,
    forbidNonWhitelisted: true,
  });
  return res.flatMap((e) => Object.values(e.constraints ?? {}));
}

describe('SubmitWhitelistDto — candidature v2 seulement', () => {
  const saved = process.env.WHITELIST_AGE_MIN;
  afterEach(() => {
    if (saved === undefined) delete process.env.WHITELIST_AGE_MIN;
    else process.env.WHITELIST_AGE_MIN = saved;
  });

  it('sans formVersion 2 : aucune erreur de champ, le corps va au service qui le refuse d’un bloc', async () => {
    // Même avec des champs hors règles : l'ancien launcher ne saurait pas les corriger.
    expect(await erreurs(ENVOI_V1)).toEqual([]);
    expect(
      await erreurs({ ...ENVOI_V1, dob: '2999-01-01', lastName: 'Uchiha' }),
    ).toEqual([]);
    expect(await erreurs({ ...ENVOI_V1, formVersion: 1 })).toEqual([]);
    expect(await erreurs({ ...ENVOI_V2, formVersion: 3 })).toEqual([]);
  });

  it('sans formVersion 2 : un champ non déclaré reste refusé', async () => {
    expect(
      (await erreurs({ ...ENVOI_V1, clan: 'Uchiha' })).length,
    ).toBeGreaterThan(0);
  });

  it('accepte une candidature v2 complète', async () => {
    expect(await erreurs(ENVOI_V2)).toEqual([]);
  });

  it('v2 : refuse un joueur de 14 ans, avec le message du launcher', async () => {
    const msgs = await erreurs({ ...ENVOI_V2, playerAge: 14 });
    expect(msgs).toEqual([
      'playerAge: Reborn accueille les joueurs à partir de 15 ans.',
    ]);
  });

  it('v2 : l’âge minimum se règle par WHITELIST_AGE_MIN', async () => {
    process.env.WHITELIST_AGE_MIN = '16';
    expect(ageMinimum()).toBe(16);
    expect(await erreurs({ ...ENVOI_V2, playerAge: 15 })).toEqual([
      'playerAge: Reborn accueille les joueurs à partir de 16 ans.',
    ]);
  });

  it('v2 : refuse un nom de famille qui est un nom de clan, même avec accents ou casse', async () => {
    for (const nom of ['Uchiha', 'hyûga', ' SENJU ']) {
      const msgs = await erreurs({ ...ENVOI_V2, lastName: nom });
      expect(msgs).toHaveLength(1);
      expect(msgs[0]).toMatch(/^lastName: « .+ » est un nom de clan\./);
    }
  });

  it('v2 : nom de famille libre de 3 à 20 lettres', async () => {
    expect(await erreurs({ ...ENVOI_V2, lastName: 'Al' })).toEqual([
      'lastName: Lettres, espaces, apostrophes et tirets, de 3 à 20 caractères.',
    ]);
    expect(await erreurs({ ...ENVOI_V2, lastName: 'Arashi99' })).toHaveLength(
      1,
    );
    expect(await erreurs({ ...ENVOI_V2, lastName: "D'Arashi-No" })).toEqual([]);
  });

  it('v2 : refuse une histoire de 12 001 caractères, sans la couper', async () => {
    const msgs = await erreurs({ ...ENVOI_V2, history: txt(12001) });
    expect(msgs).toHaveLength(1);
    expect(msgs[0]).toMatch(
      /^history: 1 caractère de trop \(12\s000 maximum\)\.$/u,
    );
    expect(await erreurs({ ...ENVOI_V2, history: txt(12000) })).toEqual([]);
    expect(await erreurs({ ...ENVOI_V2, history: txt(149) })).toHaveLength(1);
  });

  it('v2 : village dans la liste fermée seulement (Déserteur exclu)', async () => {
    expect(await erreurs({ ...ENVOI_V2, village: 'Konoha' })).toHaveLength(1);
    expect(await erreurs({ ...ENVOI_V2, village: 'Déserteur' })).toHaveLength(
      1,
    );
    expect(await erreurs({ ...ENVOI_V2, village: 'Amegakure' })).toEqual([]);
  });

  it('v2 : nouveaux champs obligatoires, listes fermées, bornes du jeu', async () => {
    const msgs = await erreurs({
      ...ENVOI_V2,
      rpYears: undefined,
      characterType: 'SAMOURAI',
      characterAge: 61,
      characterSex: undefined,
    });
    expect(msgs).toEqual(
      expect.arrayContaining([
        'rpYears: Choisis une réponse.',
        expect.stringMatching(/^characterType: Valeur inconnue/),
        'characterAge: Entre 10 et 60 ans, les bornes du jeu.',
        'characterSex: Choisis une réponse.',
      ]),
    );
  });

  it('v2 : la découverte est facultative', async () => {
    expect(await erreurs({ ...ENVOI_V2, discoverySource: null })).toEqual([]);
    expect(await erreurs({ ...ENVOI_V2, discoverySource: undefined })).toEqual(
      [],
    );
  });

  it('v2 : les champs retirés (expérience, « Pourquoi Reborn ? », caractère et apparence) sont refusés', async () => {
    const msgs = await erreurs({
      ...ENVOI_V2,
      motivation: txt(200),
      experience: txt(200),
      appearance: txt(200),
    });
    expect(msgs).toHaveLength(3);
    expect(
      msgs.every((m) => m.includes('Champ retiré de la candidature v2')),
    ).toBe(true);
  });

  it('v2 : règlement et consentement à la voix obligatoires', async () => {
    const msgs = await erreurs({
      ...ENVOI_V2,
      rulesAccepted: false,
      voiceConsent: undefined,
    });
    expect(msgs).toHaveLength(2);
  });

  it('v2 : le support visuel exige https://', async () => {
    expect(
      await erreurs({ ...ENVOI_V2, support: 'http://exemple.fr/x' }),
    ).toHaveLength(1);
    expect(await erreurs({ ...ENVOI_V2, support: '' })).toEqual([]);
  });

  it('v2 : la date de naissance de l’ancien formulaire est refusée aussi', async () => {
    expect(await erreurs({ ...ENVOI_V2, dob: '2006-04-12' })).toEqual([
      'dob: Champ retiré de la candidature v2 : ne pas l’envoyer.',
    ]);
  });

  it('v2 : tout champ non déclaré est refusé', async () => {
    expect(
      (await erreurs({ ...ENVOI_V2, clan: 'Uchiha' })).length,
    ).toBeGreaterThan(0);
  });
});

describe('plus de voie « ancien format » (complément du 25/10)', () => {
  it('message exact du refus', () => {
    expect(MSG_LAUNCHER_A_JOUR).toBe(
      'Mets ton launcher à jour pour envoyer ta candidature.',
    );
  });

  it('seul formVersion 2 (le nombre) passe', () => {
    expect(estEnvoiV2(ENVOI_V2)).toBe(true);
    expect(estEnvoiV2(ENVOI_V1)).toBe(false);
    for (const formVersion of [undefined, null, 1, 3, '2'])
      expect(estEnvoiV2({ formVersion })).toBe(false);
    expect(estEnvoiV2(null)).toBe(false);
  });

  it('verifierChamp : formVersion autre que 2 = message du launcher à mettre à jour', () => {
    expect(verifierChamp('formVersion', 2)).toBeNull();
    for (const v of [undefined, 1, 3])
      expect(verifierChamp('formVersion', v)).toBe(MSG_LAUNCHER_A_JOUR);
  });

  it('verifierEnvoi d’un corps de l’ancien launcher : formVersion refusé, anciens champs refusés', () => {
    const msgs = verifierEnvoi(ENVOI_V1, CHAMPS_ENVOI);
    expect(msgs).toEqual(
      expect.arrayContaining([
        `formVersion: ${MSG_LAUNCHER_A_JOUR}`,
        'dob: Champ retiré de la candidature v2 : ne pas l’envoyer.',
        'motivation: Champ retiré de la candidature v2 : ne pas l’envoyer.',
      ]),
    );
  });
});

describe('renvoi par partie (verifierEnvoi)', () => {
  it('ne contrôle que les champs demandés : partie RP seule, sans champs HRP', () => {
    const {
      playerAge: _a,
      rpYears: _r,
      narutoKnowledge: _n,
      ...sansHrp
    } = ENVOI_V2;
    void _a;
    void _r;
    void _n;
    expect(
      verifierEnvoi(sansHrp, [...CHAMPS_ENVOI_COMMUNS, ...CHAMPS_ENVOI_RP]),
    ).toEqual([]);
  });

  it('messages au format « champ: message »', () => {
    expect(
      verifierEnvoi({ ...ENVOI_V2, appearance: txt(200) }, CHAMPS_ENVOI_RP),
    ).toEqual([
      'appearance: Champ retiré de la candidature v2 : ne pas l’envoyer.',
    ]);
  });
});

describe('noms de clan', () => {
  it('reconnaît les clans du mod et le clan réservé « Autre »', () => {
    expect(estNomDeClan('Onoki')).toBe(true);
    expect(estNomDeClan('Autre')).toBe(true);
    expect(estNomDeClan('Arashi')).toBe(false);
  });
});

describe('statut global (§1.3)', () => {
  const { APPROVED: A, PENDING: P, NEEDS_REVISION: R, REJECTED: X } = AppStatus;

  it('écrit accepté, oral à passer : PENDING (plus APPROVED à l’écrit)', () => {
    expect(statutGlobal(A, A, P, false)).toBe(P);
  });
  it('écrit accepté et oral déjà passé sur ce compte : APPROVED', () => {
    expect(statutGlobal(A, A, P, true)).toBe(A);
    expect(statutGlobal(A, A, A, false)).toBe(A);
  });
  it('une partie refusée ou l’oral refusé : REJECTED ; une partie à revoir : NEEDS_REVISION', () => {
    expect(statutGlobal(A, X, P, false)).toBe(X);
    expect(statutGlobal(A, A, X, false)).toBe(X);
    expect(statutGlobal(R, A, P, false)).toBe(R);
  });
  it('oral à revoir : l’écrit est gardé, statut PENDING', () => {
    expect(statutGlobal(A, A, R, false)).toBe(P);
  });
});

describe('phases (§1.2)', () => {
  const NOW = new Date('2026-10-25T12:00:00Z');
  const app = (
    o: Partial<NonNullable<EntreePhase['app']>> = {},
  ): NonNullable<EntreePhase['app']> => ({
    status: AppStatus.PENDING,
    hrpStatus: AppStatus.PENDING,
    rpStatus: AppStatus.PENDING,
    oralStatus: AppStatus.PENDING,
    oralDecidedAt: null,
    reviewedAt: null,
    ...o,
  });
  const suivi = (o: Partial<EntreePhase> = {}) =>
    calculerSuivi({
      role: Role.PLAYER,
      oralPassedAt: null,
      whitelistRejectedAt: null,
      app: app(),
      reservation: null,
      now: NOW,
      ...o,
    });
  const ecrit = { hrpStatus: AppStatus.APPROVED, rpStatus: AppStatus.APPROVED };

  it('BROUILLON sans candidature ; ACCEPTE pour un compte whitelisté ou staff', () => {
    expect(suivi({ app: null }).phase).toBe('BROUILLON');
    expect(suivi({ role: Role.WHITELISTED, app: null }).phase).toBe('ACCEPTE');
    expect(suivi({ role: Role.HELPER }).phase).toBe('ACCEPTE');
  });

  it('ECRIT_EN_ATTENTE tant que l’écrit n’est pas tranché', () => {
    expect(suivi().phase).toBe('ECRIT_EN_ATTENTE');
    expect(suivi({ app: app({ hrpStatus: AppStatus.APPROVED }) }).phase).toBe(
      'ECRIT_EN_ATTENTE',
    );
  });

  it('ECRIT_A_REVOIR rouvre seulement les parties « à revoir »', () => {
    const s = suivi({
      app: app({
        hrpStatus: AppStatus.APPROVED,
        rpStatus: AppStatus.NEEDS_REVISION,
      }),
    });
    expect(s.phase).toBe('ECRIT_A_REVOIR');
    expect(s.partiesRouvertes).toEqual(['RP']);
    // HRP encore en relecture : il reste fermé.
    expect(
      suivi({ app: app({ rpStatus: AppStatus.NEEDS_REVISION }) })
        .partiesRouvertes,
    ).toEqual(['RP']);
    // Statut global « à revoir » ancien, sans partie marquée : tout ce qui n'est pas validé.
    expect(
      suivi({ app: app({ status: AppStatus.NEEDS_REVISION }) })
        .partiesRouvertes,
    ).toEqual(['HRP', 'RP']);
  });

  it('douaneEnJeu suit REBORN_DOUANE_ENABLED (false par défaut)', () => {
    expect(suivi().douaneEnJeu).toBe(false);
    expect(suivi({ douaneEnJeu: true }).douaneEnJeu).toBe(true);
    expect(suivi({ douaneEnJeu: false }).douaneEnJeu).toBe(false);
  });

  it('vue du candidat : place sans arrivée dont l’entrée est fermée = manquée, séance à réserver', () => {
    const h = new Date('2026-10-25T11:00:00Z'); // NOW = H + 1 h
    const place = { id: 'r1', seanceId: 's1', convocationAt: h };
    expect(
      suivi({
        app: app(ecrit),
        reservation: { ...place, presentAt: null },
        placeFermeeManquee: true,
      }).phase,
    ).toBe('DOUANE_A_RESERVER');
    // Arrivé au hall : la place tient jusqu'à la fin de la séance.
    expect(
      suivi({
        app: app(ecrit),
        reservation: { ...place, presentAt: new Date('2026-10-25T10:50:00Z') },
        placeFermeeManquee: true,
      }).phase,
    ).toBe('DOUANE_RESERVEE');
    // Panel et décision d'oral : une place BOOKED compte tant qu'elle l'est.
    expect(
      suivi({ app: app(ecrit), reservation: { ...place, presentAt: null } })
        .phase,
    ).toBe('DOUANE_RESERVEE');
  });

  it('écrit accepté : DOUANE_A_RESERVER, puis DOUANE_RESERVEE avec la fenêtre H − 15 / H + 30', () => {
    expect(suivi({ app: app(ecrit) }).phase).toBe('DOUANE_A_RESERVER');
    const h = new Date('2026-10-26T19:00:00Z');
    const s = suivi({
      app: app(ecrit),
      reservation: { id: 'r1', seanceId: 's1', convocationAt: h },
    });
    expect(s.phase).toBe('DOUANE_RESERVEE');
    expect(s.reservation).toEqual({
      id: 'r1',
      seanceId: 's1',
      convocationAt: '2026-10-26T19:00:00.000Z',
      entreeOuvreAt: '2026-10-26T18:45:00.000Z',
      entreeFermeAt: '2026-10-26T19:30:00.000Z',
    });
  });

  it('fenêtre d’entrée : le retard maximal réglé (DOUANE_RETARD_MAX_MIN) est repris tel quel', () => {
    const h = new Date('2026-10-26T19:00:00Z');
    const s = suivi({
      app: app(ecrit),
      reservation: { id: 'r1', seanceId: 's1', convocationAt: h },
      retardMaxMin: 45,
    });
    expect(s.reservation?.entreeOuvreAt).toBe('2026-10-26T18:45:00.000Z');
    expect(s.reservation?.entreeFermeAt).toBe('2026-10-26T19:45:00.000Z');
  });

  it('oral à revoir : DOUANE_A_REVOIR, nouvelle séance dès le lendemain (minuit, Paris)', () => {
    const s = suivi({
      app: app({
        ...ecrit,
        oralStatus: AppStatus.NEEDS_REVISION,
        oralDecidedAt: new Date('2026-10-25T19:30:00Z'),
      }),
    });
    expect(s.phase).toBe('DOUANE_A_REVOIR');
    expect(s.reservableDes).toBe('2026-10-25T23:00:00.000Z');
  });

  it('REFUSE (écrit ou oral) avec la date de nouvelle candidature, 1 jour après', () => {
    const refus = new Date('2026-10-25T10:00:00Z');
    const s = suivi({
      whitelistRejectedAt: refus,
      app: app({ rpStatus: AppStatus.REJECTED, status: AppStatus.REJECTED }),
    });
    expect(s).toMatchObject({
      phase: 'REFUSE',
      refusPar: 'ECRIT',
      canReapplyAt: '2026-10-26T10:00:00.000Z',
    });
    const o = suivi({
      whitelistRejectedAt: refus,
      app: app({ ...ecrit, oralStatus: AppStatus.REJECTED }),
    });
    expect(o).toMatchObject({ phase: 'REFUSE', refusPar: 'ORAL' });
  });

  it('retrait après un refus : le délai court toujours, puis BROUILLON', () => {
    expect(
      suivi({
        app: null,
        whitelistRejectedAt: new Date('2026-10-25T10:00:00Z'),
      }).phase,
    ).toBe('REFUSE');
    expect(
      suivi({
        app: null,
        whitelistRejectedAt: new Date('2026-10-24T10:00:00Z'),
      }).phase,
    ).toBe('BROUILLON');
  });

  it('oral déjà passé sur ce compte : l’écrit validé mène directement à ACCEPTE', () => {
    const s = suivi({
      oralPassedAt: new Date('2026-09-01T00:00:00Z'),
      app: app(ecrit),
    });
    expect(s.phase).toBe('ACCEPTE');
    expect(suivi({ oralPassedAt: new Date() }).oralRequis).toBe(false);
  });

  it('migration : ancien « HRP validé à l’oral » encore en attente du RP = écrit en attente, pas accepté', () => {
    const s = suivi({
      oralPassedAt: new Date('2026-10-25T00:00:00Z'),
      app: app({
        hrpStatus: AppStatus.APPROVED,
        oralStatus: AppStatus.APPROVED,
      }),
    });
    expect(s.phase).toBe('ECRIT_EN_ATTENTE');
  });
});

describe('dates à l’heure de Paris', () => {
  it('lendemain : minuit de Paris, changement d’heure compris', () => {
    // 24/10 21 h (heure d'été) → 25/10 0 h, encore en heure d'été (UTC+2).
    expect(
      debutLendemainParis(new Date('2026-10-24T19:00:00Z')).toISOString(),
    ).toBe('2026-10-24T22:00:00.000Z');
    // 25/10 21 h (heure d'hiver) → 26/10 0 h (UTC+1).
    expect(
      debutLendemainParis(new Date('2026-10-25T20:00:00Z')).toISOString(),
    ).toBe('2026-10-25T23:00:00.000Z');
  });

  it('heure courte : « 21 h », « 21 h 30 », « 9 h 05 », sans zéro devant', () => {
    expect(heureCourteParis(new Date('2026-10-31T20:00:00Z'))).toBe('21 h');
    expect(heureCourteParis(new Date('2026-10-31T20:30:00Z'))).toBe('21 h 30');
    expect(heureCourteParis(new Date('2026-10-31T08:05:00Z'))).toBe('9 h 05');
  });

  it('âge révolu depuis la date de naissance (anciens dossiers), au jour de Paris', () => {
    const now = new Date('2026-10-25T12:00:00Z');
    expect(ageDepuisNaissance('2011-10-25', now)).toBe(15);
    expect(ageDepuisNaissance('2011-10-26', now)).toBe(14);
    expect(ageDepuisNaissance('pas une date', now)).toBeNull();
  });

  it('format « 26/10 à 21 h » / « à 21 h 05 »', () => {
    expect(formatDateParis(new Date('2026-10-26T20:00:00Z'))).toBe(
      '26/10 à 21 h',
    );
    expect(formatDateParis(new Date('2026-10-26T20:05:00Z'))).toBe(
      '26/10 à 21 h 05',
    );
  });
});

describe('brouillon serveur (§2.5)', () => {
  it('contrôle souple : aucun minimum, un texte trop long reste enregistrable jusqu’au double', () => {
    expect(verifierBrouillon({ history: 'court', playerAge: 14 })).toBeNull();
    expect(verifierBrouillon({ history: txt(13000) })).toBeNull();
    expect(verifierBrouillon({ history: txt(24001) })).toMatchObject({
      code: 400,
    });
  });

  it('refuse ce qui n’est pas un objet, et au-delà de 256 Ko', () => {
    expect(verifierBrouillon([1, 2])).toMatchObject({ code: 400 });
    const lourd: Record<string, string> = {};
    for (let i = 0; i < 20; i++) lourd[`champ${i}`] = txt(14000);
    expect(verifierBrouillon(lourd)).toMatchObject({ code: 413 });
  });
});
