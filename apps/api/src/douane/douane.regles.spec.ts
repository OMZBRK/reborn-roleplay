import { AppStatus, Role } from '@prisma/client';
import {
  attribuerConvocation,
  calculerPhase,
  capaciteConseillee,
  debutDuLendemainParis,
  estExaminateur,
  etatFenetre,
  fenetreEntree,
  heureParis,
  lireRetardMax,
  vaguesConvocation,
  type EntreePhase,
} from './douane.regles';

const MIN = 60_000;
const iso = (d: Date | null) => d?.toISOString() ?? null;

describe("douane — fenêtre d'entrée (règle unique H − 15 / H + 30)", () => {
  const H = new Date('2026-10-31T20:00:00.000Z');

  it('ouvre 15 min avant et ferme 30 min après la convocation', () => {
    const f = fenetreEntree(H);
    expect(f.ouvreAt.toISOString()).toBe('2026-10-31T19:45:00.000Z');
    expect(f.fermeAt.toISOString()).toBe('2026-10-31T20:30:00.000Z');
  });

  it('suit la vérification prévue : grisé à −20, actif à −10 et à +25, grisé à +35 (§6.4)', () => {
    const a = (decalageMin: number) =>
      etatFenetre(H, new Date(H.getTime() + decalageMin * MIN));
    expect(a(-20)).toBe('A_VENIR');
    expect(a(-10)).toBe('OUVERTE');
    expect(a(-15)).toBe('OUVERTE');
    expect(a(25)).toBe('OUVERTE');
    expect(a(30)).toBe('OUVERTE');
    expect(a(35)).toBe('FERMEE');
  });

  it('le retard maximal se règle, borné, et retombe à 30 min si la valeur est absurde', () => {
    expect(lireRetardMax(undefined)).toBe(30);
    expect(lireRetardMax('45')).toBe(45);
    expect(lireRetardMax('abc')).toBe(30);
    expect(lireRetardMax('1')).toBe(30);
    expect(lireRetardMax('999')).toBe(30);
    expect(fenetreEntree(H, 45).fermeAt.toISOString()).toBe(
      '2026-10-31T20:45:00.000Z',
    );
  });
});

describe('douane — vagues de convocation', () => {
  const debut = new Date('2026-10-31T20:00:00.000Z');
  const fin = (min: number) => new Date(debut.getTime() + min * MIN);

  it("séance d'1 h au plus : tout le monde au début", () => {
    expect(vaguesConvocation(debut, fin(60)).map(iso)).toEqual([
      '2026-10-31T20:00:00.000Z',
    ]);
    expect(vaguesConvocation(debut, fin(45))).toHaveLength(1);
  });

  it('séance de 2 h : 21 h, 21 h 30, 22 h (heure de Paris), comme la maquette L9', () => {
    expect(vaguesConvocation(debut, fin(120)).map(iso)).toEqual([
      '2026-10-31T20:00:00.000Z',
      '2026-10-31T20:30:00.000Z',
      '2026-10-31T21:00:00.000Z',
    ]);
    expect(vaguesConvocation(debut, fin(90))).toHaveLength(2);
  });

  it('remplit chaque vague à sa part (capacité / vagues), puis passe à la suivante', () => {
    const seance = { startAt: debut, endAt: fin(120), capacite: 6 };
    const avant = new Date(debut.getTime() - 24 * 60 * MIN);
    const prises: Date[] = [];
    const attribuees: (string | null)[] = [];
    for (let i = 0; i < 6; i++) {
      const h = attribuerConvocation(seance, prises, avant);
      attribuees.push(iso(h));
      if (h) prises.push(h);
    }
    expect(attribuees).toEqual([
      '2026-10-31T20:00:00.000Z',
      '2026-10-31T20:00:00.000Z',
      '2026-10-31T20:30:00.000Z',
      '2026-10-31T20:30:00.000Z',
      '2026-10-31T21:00:00.000Z',
      '2026-10-31T21:00:00.000Z',
    ]);
  });

  it('une place rendue dans une vague pleine est reprise par la réservation suivante', () => {
    const seance = { startAt: debut, endAt: fin(120), capacite: 6 };
    const avant = new Date(debut.getTime() - 60 * MIN);
    // Vague de 20 h : une place rendue ; les deux autres vagues pleines.
    const prises = [fin(0), fin(30), fin(30), fin(60), fin(60)];
    expect(iso(attribuerConvocation(seance, prises, avant))).toBe(
      '2026-10-31T20:00:00.000Z',
    );
  });

  it('saute une vague dont la fenêtre se ferme dans moins de 15 min', () => {
    const seance = { startAt: debut, endAt: fin(120), capacite: 6 };
    // 20 h 20 : la vague de 20 h ferme à 20 h 30 (10 min), on convoque à 20 h 30.
    expect(iso(attribuerConvocation(seance, [], fin(20)))).toBe(
      '2026-10-31T20:30:00.000Z',
    );
    // 20 h 10 : il reste 20 min de fenêtre sur la vague de 20 h, elle est encore attribuée.
    expect(iso(attribuerConvocation(seance, [], fin(10)))).toBe(
      '2026-10-31T20:00:00.000Z',
    );
  });

  it('plus aucune vague attribuable : null (séance trop avancée)', () => {
    const seance = { startAt: debut, endAt: fin(60), capacite: 8 };
    expect(attribuerConvocation(seance, [], fin(20))).toBeNull();
  });

  it('toutes les vagues restantes ont leur part : débordement sur la moins remplie', () => {
    const seance = { startAt: debut, endAt: fin(120), capacite: 6 };
    // 20 h 20 : seules 20 h 30 et 21 h restent ; 20 h 30 a 3 places, 21 h en a 2.
    const prises = [fin(30), fin(30), fin(30), fin(60), fin(60)];
    expect(iso(attribuerConvocation(seance, prises, fin(20)))).toBe(
      '2026-10-31T21:00:00.000Z',
    );
  });

  it('capacité conseillée : examinateurs × heures × 4', () => {
    expect(capaciteConseillee(debut, fin(120), 2)).toBe(16);
    expect(capaciteConseillee(debut, fin(120), 0)).toBeNull();
  });
});

describe('douane — phase calculée (§1.2)', () => {
  const app = (
    over: Partial<NonNullable<EntreePhase['application']>> = {},
  ) => ({
    status: AppStatus.PENDING,
    hrpStatus: AppStatus.PENDING,
    rpStatus: AppStatus.PENDING,
    oralStatus: AppStatus.PENDING,
    writtenApprovedAt: null,
    ...over,
  });
  const phase = (over: Partial<EntreePhase>) =>
    calculerPhase({
      role: Role.PLAYER,
      oralPassedAt: null,
      application: app(),
      reservationActive: false,
      ...over,
    });
  const ecritOk = {
    hrpStatus: AppStatus.APPROVED,
    rpStatus: AppStatus.APPROVED,
    writtenApprovedAt: new Date(),
  };

  it('sans candidature : BROUILLON', () => {
    expect(phase({ application: null })).toBe('BROUILLON');
  });

  it('écrit en relecture : ECRIT_EN_ATTENTE ; une partie à revoir : ECRIT_A_REVOIR', () => {
    expect(phase({})).toBe('ECRIT_EN_ATTENTE');
    expect(phase({ application: app({ hrpStatus: AppStatus.APPROVED }) })).toBe(
      'ECRIT_EN_ATTENTE',
    );
    expect(
      phase({ application: app({ rpStatus: AppStatus.NEEDS_REVISION }) }),
    ).toBe('ECRIT_A_REVOIR');
  });

  it('écrit accepté : DOUANE_A_RESERVER, puis DOUANE_RESERVEE avec une place', () => {
    expect(phase({ application: app(ecritOk) })).toBe('DOUANE_A_RESERVER');
    expect(phase({ application: app(ecritOk), reservationActive: true })).toBe(
      'DOUANE_RESERVEE',
    );
    // Sans la date (dossier d'avant la v2), HRP et RP validés suffisent.
    expect(
      phase({
        application: app({
          hrpStatus: AppStatus.APPROVED,
          rpStatus: AppStatus.APPROVED,
        }),
      }),
    ).toBe('DOUANE_A_RESERVER');
  });

  it('oral à revoir : DOUANE_A_REVOIR ; oral refusé : REFUSE', () => {
    expect(
      phase({
        application: app({ ...ecritOk, oralStatus: AppStatus.NEEDS_REVISION }),
      }),
    ).toBe('DOUANE_A_REVOIR');
    expect(
      phase({
        application: app({
          ...ecritOk,
          oralStatus: AppStatus.REJECTED,
          status: AppStatus.REJECTED,
        }),
      }),
    ).toBe('REFUSE');
    expect(phase({ application: app({ hrpStatus: AppStatus.REJECTED }) })).toBe(
      'REFUSE',
    );
  });

  it("oral déjà réussi (oralPassedAt) : ACCEPTE dès l'écrit validé, sans nouvel oral", () => {
    expect(phase({ application: app(ecritOk), oralPassedAt: new Date() })).toBe(
      'ACCEPTE',
    );
    // … mais l'écrit reste à valider.
    expect(phase({ oralPassedAt: new Date() })).toBe('ECRIT_EN_ATTENTE');
  });

  it('rôle WHITELISTED ou staff : ACCEPTE, quelle que soit la candidature', () => {
    expect(phase({ role: Role.WHITELISTED, application: null })).toBe(
      'ACCEPTE',
    );
    expect(phase({ role: Role.HELPER })).toBe('ACCEPTE');
    expect(
      phase({
        application: app({ ...ecritOk, oralStatus: AppStatus.APPROVED }),
      }),
    ).toBe('ACCEPTE');
  });

  it('même phase que GET /whitelist/me : dossier migré (ancien oral validé, RP à relire) = ECRIT_EN_ATTENTE', () => {
    expect(
      phase({
        oralPassedAt: new Date('2026-10-25T00:00:00.000Z'),
        application: app({
          hrpStatus: AppStatus.APPROVED,
          oralStatus: AppStatus.APPROVED,
        }),
      }),
    ).toBe('ECRIT_EN_ATTENTE');
  });
});

describe('douane — rôles et heure de Paris', () => {
  it('examinateur : WHITELIST_REVIEWER ou plus, pas HELPER', () => {
    expect(estExaminateur(Role.WHITELIST_REVIEWER)).toBe(true);
    expect(estExaminateur(Role.MODERATOR)).toBe(true);
    expect(estExaminateur(Role.OWNER)).toBe(true);
    expect(estExaminateur(Role.HELPER)).toBe(false);
    expect(estExaminateur(Role.DEVELOPPEUR)).toBe(false);
    expect(estExaminateur(Role.PLAYER)).toBe(false);
  });

  it("lendemain à Paris, l'hiver (UTC+1) : 21 h le 25/10 → minuit le 26/10", () => {
    expect(
      debutDuLendemainParis(new Date('2026-10-25T20:00:00.000Z')).toISOString(),
    ).toBe('2026-10-25T23:00:00.000Z');
  });

  it("lendemain à Paris, l'été (UTC+2), y compris après minuit à Paris mais pas en UTC", () => {
    // 23 h 30 le 10/07 à Paris → minuit le 11/07.
    expect(
      debutDuLendemainParis(new Date('2026-07-10T21:30:00.000Z')).toISOString(),
    ).toBe('2026-07-10T22:00:00.000Z');
    // 0 h 30 le 11/07 à Paris (encore le 10 en UTC) → minuit le 12/07.
    expect(
      debutDuLendemainParis(new Date('2026-07-10T22:30:00.000Z')).toISOString(),
    ).toBe('2026-07-11T22:00:00.000Z');
  });

  it("lendemain à Paris, la nuit du passage à l'heure d'hiver (25/10/2026)", () => {
    // 1 h du matin le 25/10 à Paris (heure d'été) → minuit le 26/10 (heure d'hiver).
    expect(
      debutDuLendemainParis(new Date('2026-10-24T23:00:00.000Z')).toISOString(),
    ).toBe('2026-10-25T23:00:00.000Z');
  });

  it('heure affichée à Paris, comme les maquettes L9 et L10 : « 21 h », « 21 h 30 », « 9 h 05 »', () => {
    expect(heureParis(new Date('2026-10-31T20:00:00.000Z'))).toBe('21 h');
    expect(heureParis(new Date('2026-10-31T20:30:00.000Z'))).toBe('21 h 30');
    expect(heureParis(new Date('2026-10-31T08:05:00.000Z'))).toBe('9 h 05');
  });

  it('phase : un refus encore en cours après un retrait reste REFUSE', () => {
    expect(
      calculerPhase({
        role: Role.PLAYER,
        oralPassedAt: null,
        whitelistRejectedAt: new Date(Date.now() - 3600 * 1000),
        application: null,
        reservationActive: false,
      }),
    ).toBe('REFUSE');
  });
});
