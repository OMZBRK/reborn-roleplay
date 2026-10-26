/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access, @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-return, @typescript-eslint/no-unsafe-argument -- base Prisma simulée en mémoire, typée librement */
import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { AppStatus, OralVia, Role } from '@prisma/client';
import { DouaneOralService } from './douane-oral.service';
import { DouaneService } from './douane.service';

/**
 * Séances de douane et décision d'oral (CANDIDATURE_ET_DOUANE.md §3.3, §3.9,
 * §3.11). La base est simulée en mémoire, avec un point clé : chaque accès
 * rend la main à la boucle d'événements (les transactions concurrentes
 * s'entrelacent pour de vrai), et `SELECT … FOR UPDATE` prend un verrou
 * tenu jusqu'à la fin de la transaction, comme Postgres. Le témoin « sans
 * verrou » montre que les tests de concurrence mesurent bien ce verrou.
 */

type Ligne = Record<string, any>;
const MIN = 60_000;
const MAINTENANT = new Date('2026-10-30T11:00:00.000Z'); // vendredi 12 h à Paris
const CE_SOIR = new Date('2026-10-30T20:00:00.000Z'); // 21 h à Paris
const DEMAIN_SOIR = new Date('2026-10-31T20:00:00.000Z');

/** Verrou exclusif par clé, libéré à la fin de la transaction qui l'a pris. */
class Verrous {
  private readonly files = new Map<string, Promise<void>>();

  async prendre(cle: string): Promise<() => void> {
    const precedent = this.files.get(cle) ?? Promise.resolve();
    let liberer!: () => void;
    const tenu = new Promise<void>((r) => (liberer = r));
    this.files.set(
      cle,
      precedent.then(() => tenu),
    );
    await precedent;
    return liberer;
  }
}

const laisserLaMain = () => new Promise<void>((r) => setImmediate(r));

function vaut(valeur: any, attendu: any): boolean {
  if (attendu instanceof Date)
    return valeur instanceof Date && valeur.getTime() === attendu.getTime();
  if (attendu !== null && typeof attendu === 'object') {
    if ('in' in attendu) return attendu.in.includes(valeur);
    if ('not' in attendu) return valeur !== attendu.not;
    const t = valeur instanceof Date ? valeur.getTime() : valeur;
    const v = (x: any) => (x instanceof Date ? x.getTime() : x);
    if ('gt' in attendu && !(t > v(attendu.gt))) return false;
    if ('gte' in attendu && !(t >= v(attendu.gte))) return false;
    if ('lt' in attendu && !(t < v(attendu.lt))) return false;
    if ('lte' in attendu && !(t <= v(attendu.lte))) return false;
    return true;
  }
  return valeur === attendu;
}

function correspond(ligne: Ligne, where: Ligne = {}): boolean {
  return Object.entries(where).every(([k, attendu]) => vaut(ligne[k], attendu));
}

function creerBase({ verrous = true }: { verrous?: boolean } = {}) {
  const db = {
    users: new Map<string, Ligne>(),
    apps: new Map<string, Ligne>(),
    seances: new Map<string, Ligne>(),
    reservations: [] as Ligne[],
  };
  const v = new Verrous();
  let seq = 0;

  const appDe = (where: Ligne) =>
    where.id
      ? db.apps.get(where.id)
      : [...db.apps.values()].find((a) => a.userId === where.userId);
  const avecSeance = (r: Ligne) => {
    const s = db.seances.get(r.seanceId)!;
    return {
      ...r,
      seance: { startAt: s.startAt, endAt: s.endAt, statut: s.statut },
    };
  };

  function client(tx: { liberations: (() => void)[] } | null) {
    return {
      $queryRaw: async (
        morceaux: TemplateStringsArray,
        ...valeurs: unknown[]
      ) => {
        await laisserLaMain();
        if (!tx || !verrous) return [];
        const sql = morceaux.join('?');
        const table = sql.includes('"DouaneSeance"')
          ? 'seance'
          : sql.includes('"WhitelistApplication"')
            ? 'app'
            : null;
        if (table)
          tx.liberations.push(
            await v.prendre(`${table}:${String(valeurs[0])}`),
          );
        return [];
      },
      user: {
        findUnique: async ({ where, select }: Ligne) => {
          await laisserLaMain();
          const u = db.users.get(where.id);
          if (!u) return null;
          const out: Ligne = { ...u };
          if (select?.whitelistApp) {
            const a = [...db.apps.values()].find((x) => x.userId === u.id);
            out.whitelistApp = a ? { ...a } : null;
          }
          return out;
        },
        findMany: async ({ where }: Ligne) => {
          await laisserLaMain();
          return [...db.users.values()]
            .filter((u) => correspond(u, where))
            .map((u) => ({ ...u }));
        },
        update: async ({ where, data }: Ligne) => {
          await laisserLaMain();
          const u = db.users.get(where.id)!;
          Object.assign(u, data);
          return { ...u };
        },
        updateMany: async ({ where, data }: Ligne) => {
          await laisserLaMain();
          const u = db.users.get(where.id);
          if (!u || !correspond(u, where)) return { count: 0 };
          Object.assign(u, data);
          return { count: 1 };
        },
      },
      whitelistApplication: {
        findUnique: async ({ where, include }: Ligne) => {
          await laisserLaMain();
          const a = appDe(where);
          if (!a) return null;
          const out: Ligne = { ...a };
          if (include?.user) out.user = { ...db.users.get(a.userId) };
          return out;
        },
        update: async ({ where, data }: Ligne) => {
          await laisserLaMain();
          const a = db.apps.get(where.id)!;
          Object.assign(a, data);
          return { ...a };
        },
      },
      douaneSeance: {
        findUnique: async ({ where }: Ligne) => {
          await laisserLaMain();
          const s = db.seances.get(where.id);
          return s ? { ...s } : null;
        },
        findMany: async ({ where }: Ligne) => {
          await laisserLaMain();
          return [...db.seances.values()]
            .filter((s) => correspond(s, where))
            .sort((a, b) => a.startAt.getTime() - b.startAt.getTime())
            .map((s) => ({
              ...s,
              _count: { examinateurs: s.examinateurs?.length ?? 0 },
            }));
        },
      },
      douaneReservation: {
        findFirst: async ({ where }: Ligne) => {
          await laisserLaMain();
          const r = db.reservations
            .filter((x) => correspond(x, where))
            .sort(
              (a, b) => a.convocationAt.getTime() - b.convocationAt.getTime(),
            )[0];
          return r ? avecSeance(r) : null;
        },
        findMany: async ({ where }: Ligne) => {
          await laisserLaMain();
          return db.reservations
            .filter((x) => correspond(x, where))
            .map((x) => ({ ...x }));
        },
        findUnique: async ({ where }: Ligne) => {
          await laisserLaMain();
          const r = db.reservations.find((x) => x.id === where.id);
          return r ? { ...r } : null;
        },
        create: async ({ data }: Ligne) => {
          await laisserLaMain();
          const r: Ligne = {
            id: `r${++seq}`,
            statut: 'BOOKED',
            presentAt: null,
            calledAt: null,
            departAt: null,
            resultat: null,
            decidedAt: null,
            examinateurUserId: null,
            rappelEnvoyeAt: null,
            annuleeAt: null,
            motifAnnulation: null,
            createdAt: new Date(),
            updatedAt: new Date(),
            ...data,
          };
          db.reservations.push(r);
          return avecSeance(r);
        },
        update: async ({ where, data }: Ligne) => {
          await laisserLaMain();
          const r = db.reservations.find((x) => x.id === where.id)!;
          Object.assign(r, data);
          return { ...r };
        },
        updateMany: async ({ where, data }: Ligne) => {
          await laisserLaMain();
          const rs = db.reservations.filter((x) => correspond(x, where));
          rs.forEach((x) => Object.assign(x, data));
          return { count: rs.length };
        },
      },
    };
  }

  const prisma = {
    ...client(null),
    $transaction: async (fn: (tx: unknown) => Promise<unknown>) => {
      const tx = { liberations: [] as (() => void)[] };
      try {
        return await fn(client(tx));
      } finally {
        tx.liberations.forEach((l) => l());
      }
    },
  };
  return { db, prisma };
}

type Base = ReturnType<typeof creerBase>;

function ajouterCompte(base: Base, id: string, role: Role = Role.PLAYER) {
  base.db.users.set(id, {
    id,
    role,
    banned: false,
    oralPassedAt: null,
    whitelistRejectedAt: null,
    minecraftUsername: `mc_${id}`,
    discordUsername: null,
  });
}

/** Candidat dont l'écrit est accepté (phase DOUANE_A_RESERVER), sauf réglage contraire. */
function ajouterCandidat(base: Base, id: string, app: Ligne = {}) {
  ajouterCompte(base, id);
  base.db.apps.set(`app-${id}`, {
    id: `app-${id}`,
    userId: id,
    status: AppStatus.PENDING,
    hrpStatus: AppStatus.APPROVED,
    rpStatus: AppStatus.APPROVED,
    oralStatus: AppStatus.PENDING,
    writtenApprovedAt: new Date('2026-10-28T18:00:00.000Z'),
    oralDecidedAt: null,
    oralVia: null,
    oralExaminerUserId: null,
    oralReason: null,
    oralNotes: null,
    voiceKey: 'voix-test.webm',
    voicePurgeAt: null,
    discordThreadId: null,
    discordMessageId: null,
    ...app,
  });
}

function ajouterSeance(
  base: Base,
  id: string,
  startAt: Date,
  dureeMin: number,
  capacite: number,
  over: Ligne = {},
) {
  base.db.seances.set(id, {
    id,
    kind: 'WHITELIST',
    startAt,
    endAt: new Date(startAt.getTime() + dureeMin * MIN),
    capacite,
    statut: 'OUVERTE',
    examinateurs: [{ id: 'exam' }],
    ...over,
  });
}

function placesBooked(base: Base, filtre: Ligne) {
  return base.db.reservations.filter(
    (r) => r.statut === 'BOOKED' && correspond(r, filtre),
  );
}

function services(base: Base, env: Record<string, string> = {}) {
  const messages = {
    postSystemMessage: jest.fn().mockResolvedValue(undefined),
  };
  const audit = { log: jest.fn().mockResolvedValue(undefined) };
  const webhooks = { statusUpdate: jest.fn().mockResolvedValue(undefined) };
  const config = { get: jest.fn((k: string) => env[k]) };
  const douane = new DouaneService(
    base.prisma as never,
    audit as never,
    messages as never,
    config as never,
  );
  const oral = new DouaneOralService(
    base.prisma as never,
    audit as never,
    webhooks as never,
    messages as never,
    config as never,
  );
  return { douane, oral, messages, audit, webhooks };
}

beforeAll(() => {
  Logger.overrideLogger(false);
});

beforeEach(() => {
  // Seule l'horloge est figée : setImmediate reste réel pour l'entrelacement.
  jest.useFakeTimers({
    now: MAINTENANT,
    doNotFake: [
      'nextTick',
      'setImmediate',
      'clearImmediate',
      'setTimeout',
      'clearTimeout',
      'setInterval',
      'clearInterval',
      'queueMicrotask',
    ],
  });
});

afterEach(() => {
  jest.useRealTimers();
});

// ═══════════════════════════ Réservations ═══════════════════════════

describe('DouaneService.reserver — capacité et réservation unique', () => {
  it('30 réservations simultanées sur 5 places : exactement 5 passent', async () => {
    const base = creerBase();
    ajouterSeance(base, 'S1', CE_SOIR, 120, 5);
    const ids = Array.from({ length: 30 }, (_, i) => `c${i}`);
    ids.forEach((id) => ajouterCandidat(base, id));
    const { douane } = services(base);

    const issues = await Promise.allSettled(
      ids.map((id) => douane.reserver(id, 'S1')),
    );

    const ok = issues.filter((i) => i.status === 'fulfilled');
    const refus = issues.filter(
      (i): i is PromiseRejectedResult => i.status === 'rejected',
    );
    expect(ok).toHaveLength(5);
    expect(refus).toHaveLength(25);
    for (const r of refus) {
      expect(r.reason).toBeInstanceOf(ConflictException);
      expect((r.reason as Error).message).toContain('complète');
    }
    expect(placesBooked(base, { seanceId: 'S1' })).toHaveLength(5);
  });

  it('témoin : sans les verrous, la même rafale déborde (le test ci-dessus mesure bien le verrou)', async () => {
    const base = creerBase({ verrous: false });
    ajouterSeance(base, 'S1', CE_SOIR, 120, 5);
    const ids = Array.from({ length: 30 }, (_, i) => `c${i}`);
    ids.forEach((id) => ajouterCandidat(base, id));
    const { douane } = services(base);

    await Promise.allSettled(ids.map((id) => douane.reserver(id, 'S1')));

    expect(placesBooked(base, { seanceId: 'S1' }).length).toBeGreaterThan(5);
  });

  it("un compte qui réserve 4 séances en même temps n'obtient qu'une place", async () => {
    const base = creerBase();
    ['S1', 'S2', 'S3', 'S4'].forEach((s, i) =>
      ajouterSeance(
        base,
        s,
        new Date(CE_SOIR.getTime() + i * 24 * 60 * MIN),
        60,
        8,
      ),
    );
    ajouterCandidat(base, 'c0');
    const { douane } = services(base);

    const issues = await Promise.allSettled(
      ['S1', 'S2', 'S3', 'S4'].map((s) => douane.reserver('c0', s)),
    );

    expect(issues.filter((i) => i.status === 'fulfilled')).toHaveLength(1);
    const refus = issues.filter(
      (i): i is PromiseRejectedResult => i.status === 'rejected',
    );
    expect(refus).toHaveLength(3);
    for (const r of refus) {
      expect(r.reason).toBeInstanceOf(ConflictException);
      expect((r.reason as Error).message).toContain('déjà une place');
    }
    expect(placesBooked(base, { userId: 'c0' })).toHaveLength(1);
  });

  it('changer de séance : refus sans `remplacer`, échange atomique avec', async () => {
    const base = creerBase();
    ajouterSeance(base, 'S1', CE_SOIR, 60, 8);
    ajouterSeance(base, 'S2', DEMAIN_SOIR, 60, 8);
    ajouterCandidat(base, 'c0');
    const { douane } = services(base);

    const premiere = await douane.reserver('c0', 'S1');
    await expect(douane.reserver('c0', 'S2')).rejects.toBeInstanceOf(
      ConflictException,
    );

    const seconde = await douane.reserver('c0', 'S2', true);
    expect(seconde.seanceId).toBe('S2');
    const ancienne = base.db.reservations.find((r) => r.id === premiere.id)!;
    expect(ancienne.statut).toBe('CANCELLED');
    expect(ancienne.motifAnnulation).toBe(
      'Changement de séance par le candidat',
    );
    expect(placesBooked(base, { userId: 'c0' })).toHaveLength(1);
  });

  it("changer pour une séance complète : l'ancienne place est gardée", async () => {
    const base = creerBase();
    ajouterSeance(base, 'S1', CE_SOIR, 60, 8);
    ajouterSeance(base, 'S2', DEMAIN_SOIR, 60, 1);
    ajouterCandidat(base, 'c0');
    ajouterCandidat(base, 'c1');
    const { douane } = services(base);

    const premiere = await douane.reserver('c0', 'S1');
    await douane.reserver('c1', 'S2');
    await expect(douane.reserver('c0', 'S2', true)).rejects.toBeInstanceOf(
      ConflictException,
    );
    expect(base.db.reservations.find((r) => r.id === premiere.id)!.statut).toBe(
      'BOOKED',
    );
  });

  it("renvoie l'heure de convocation et la fenêtre H − 15 / H + 30", async () => {
    const base = creerBase();
    ajouterSeance(base, 'S1', CE_SOIR, 120, 6);
    ['c0', 'c1', 'c2'].forEach((id) => ajouterCandidat(base, id));
    const { douane, messages } = services(base);

    const r0 = await douane.reserver('c0', 'S1');
    await douane.reserver('c1', 'S1');
    const r2 = await douane.reserver('c2', 'S1');

    expect(r0.convocationAt).toBe('2026-10-30T20:00:00.000Z');
    expect(r0.fenetre).toEqual({
      ouvreAt: '2026-10-30T19:45:00.000Z',
      fermeAt: '2026-10-30T20:30:00.000Z',
      etat: 'A_VENIR',
    });
    // 6 places, 3 vagues : deux candidats par vague, le 3ᵉ passe à 21 h 30 (Paris).
    expect(r2.convocationAt).toBe('2026-10-30T20:30:00.000Z');
    expect(messages.postSystemMessage).toHaveBeenCalledWith(
      'app-c0',
      expect.stringContaining('convocation à 21 h.'),
    );
  });

  it("refusée tant que l'écrit n'est pas accepté", async () => {
    const base = creerBase();
    ajouterSeance(base, 'S1', CE_SOIR, 60, 8);
    ajouterCandidat(base, 'c0', {
      rpStatus: AppStatus.PENDING,
      writtenApprovedAt: null,
    });
    ajouterCandidat(base, 'c1', {
      rpStatus: AppStatus.NEEDS_REVISION,
      writtenApprovedAt: null,
    });
    const { douane } = services(base);

    await expect(douane.reserver('c0', 'S1')).rejects.toThrow(
      ForbiddenException,
    );
    await expect(douane.reserver('c0', 'S1')).rejects.toThrow(/relecture/);
    await expect(douane.reserver('c1', 'S1')).rejects.toThrow(/à revoir/);
    expect(base.db.reservations).toHaveLength(0);
  });

  it('refusée sans candidature, pour un compte déjà accepté, et dans une séance fermée', async () => {
    const base = creerBase();
    ajouterSeance(base, 'S1', CE_SOIR, 60, 8);
    ajouterSeance(base, 'S2', DEMAIN_SOIR, 60, 8, { statut: 'FERMEE' });
    ajouterCompte(base, 'sans');
    ajouterCandidat(base, 'deja', {
      oralStatus: AppStatus.APPROVED,
      status: AppStatus.APPROVED,
    });
    ajouterCandidat(base, 'c0');
    const { douane } = services(base);

    await expect(douane.reserver('sans', 'S1')).rejects.toBeInstanceOf(
      ForbiddenException,
    );
    await expect(douane.reserver('deja', 'S1')).rejects.toThrow(/déjà accepté/);
    await expect(douane.reserver('c0', 'S2')).rejects.toThrow(/n'accepte plus/);
  });

  it('une place dont la fenêtre est passée sans arrivée devient manquée, et le candidat peut réserver', async () => {
    const base = creerBase();
    ajouterSeance(
      base,
      'S0',
      new Date(MAINTENANT.getTime() - 120 * MIN),
      180,
      8,
    );
    ajouterSeance(base, 'S1', CE_SOIR, 60, 8);
    ajouterCandidat(base, 'c0');
    base.db.reservations.push({
      id: 'vieille',
      seanceId: 'S0',
      applicationId: 'app-c0',
      userId: 'c0',
      convocationAt: new Date(MAINTENANT.getTime() - 120 * MIN),
      statut: 'BOOKED',
      presentAt: null,
    });
    const { douane } = services(base);

    const r = await douane.reserver('c0', 'S1');
    expect(r.seanceId).toBe('S1');
    expect(base.db.reservations.find((x) => x.id === 'vieille')!.statut).toBe(
      'MISSED',
    );
  });

  it("annuler sa place : oui ; celle d'un autre : introuvable", async () => {
    const base = creerBase();
    ajouterSeance(base, 'S1', CE_SOIR, 60, 8);
    ajouterCandidat(base, 'c0');
    ajouterCandidat(base, 'c1');
    const { douane } = services(base);

    const r = await douane.reserver('c0', 'S1');
    await expect(
      douane.annulerMaReservation('c1', r.id),
    ).rejects.toBeInstanceOf(NotFoundException);
    await expect(douane.annulerMaReservation('c0', r.id)).resolves.toEqual({
      ok: true,
    });
    expect(base.db.reservations[0].statut).toBe('CANCELLED');
    await expect(
      douane.annulerMaReservation('c0', r.id),
    ).rejects.toBeInstanceOf(NotFoundException);
  });
});

describe('DouaneService.etatCandidat — séances visibles selon la phase', () => {
  it('écrit en relecture : aucune séance, une raison', async () => {
    const base = creerBase();
    ajouterSeance(base, 'S1', CE_SOIR, 60, 8);
    ajouterCandidat(base, 'c0', {
      rpStatus: AppStatus.PENDING,
      writtenApprovedAt: null,
    });
    const { douane } = services(base);

    const etat = await douane.etatCandidat('c0');
    expect(etat.phase).toBe('ECRIT_EN_ATTENTE');
    expect(etat.peutReserver).toBe(false);
    expect(etat.seances).toEqual([]);
    expect(etat.raison).toContain('relecture');
  });

  it('écrit accepté : séances ouvertes avec places restantes, puis la place réservée', async () => {
    const base = creerBase();
    ajouterSeance(base, 'S1', CE_SOIR, 120, 16);
    ajouterSeance(base, 'S2', DEMAIN_SOIR, 60, 8, { statut: 'ANNULEE' });
    ajouterCandidat(base, 'c0');
    ajouterCandidat(base, 'c1');
    const { douane } = services(base);
    await douane.reserver('c1', 'S1');

    const avant = await douane.etatCandidat('c0');
    expect(avant.phase).toBe('DOUANE_A_RESERVER');
    expect(avant.peutReserver).toBe(true);
    expect(avant.seances).toHaveLength(1);
    expect(avant.seances[0]).toMatchObject({
      id: 'S1',
      placesTotal: 16,
      placesRestantes: 15,
      complet: false,
      nbExaminateurs: 1,
      convocationIndicative: '2026-10-30T20:00:00.000Z',
    });
    expect(avant.regles).toEqual({
      avanceEntreeMin: 15,
      retardMaxMin: 30,
      douaneEnJeu: false,
    });
    expect(avant.derniereReservation).toBeNull();

    await douane.reserver('c0', 'S1');
    const apres = await douane.etatCandidat('c0');
    expect(apres.phase).toBe('DOUANE_RESERVEE');
    expect(apres.peutChanger).toBe(true);
    expect(apres.reservation?.fenetre.ouvreAt).toBe('2026-10-30T19:45:00.000Z');
  });

  it('douane ouverte en jeu (REBORN_DOUANE_ENABLED) : regles.douaneEnJeu = true', async () => {
    const base = creerBase();
    ajouterCandidat(base, 'c0');
    const { douane } = services(base, { REBORN_DOUANE_ENABLED: 'true' });
    expect((await douane.etatCandidat('c0')).regles.douaneEnJeu).toBe(true);
  });

  it('entrée fermée sans arrivée : séance manquée tout de suite, place à réserver de nouveau', async () => {
    const base = creerBase();
    // Séance longue commencée à 10 h (Paris 11 h) : place convoquée à 10 h, entrée
    // fermée à 10 h 30 ; la tâche planifiée ne la solderait qu'après la fin.
    const debut = new Date(MAINTENANT.getTime() - 60 * MIN);
    ajouterSeance(base, 'S1', debut, 240, 8);
    ajouterSeance(base, 'S2', DEMAIN_SOIR, 60, 8);
    ajouterCandidat(base, 'c0');
    base.db.reservations.push({
      id: 'place-c0',
      seanceId: 'S1',
      applicationId: 'app-c0',
      userId: 'c0',
      convocationAt: debut,
      statut: 'BOOKED',
      presentAt: null,
      createdAt: new Date(MAINTENANT.getTime() - 24 * 60 * MIN),
    });
    const { douane } = services(base);

    const etat = await douane.etatCandidat('c0');
    expect(etat.phase).toBe('DOUANE_A_RESERVER');
    expect(etat.peutReserver).toBe(true);
    expect(etat.reservation).toBeNull();
    expect(etat.derniereReservation).toEqual({
      statut: 'MISSED',
      convocationAt: debut.toISOString(),
      fermeAt: new Date(debut.getTime() + 30 * MIN).toISOString(),
      motifAnnulation: null,
    });
  });

  it('place annulée par le staff depuis la dernière décision : motif repris', async () => {
    const base = creerBase();
    ajouterSeance(base, 'S2', DEMAIN_SOIR, 60, 8);
    ajouterCandidat(base, 'c0');
    base.db.reservations.push({
      id: 'place-c0',
      seanceId: 'S2',
      applicationId: 'app-c0',
      userId: 'c0',
      convocationAt: CE_SOIR,
      statut: 'CANCELLED',
      motifAnnulation: 'Examinateur absent',
      presentAt: null,
      createdAt: new Date('2026-10-29T10:00:00.000Z'),
    });
    const { douane } = services(base);
    expect((await douane.etatCandidat('c0')).derniereReservation).toMatchObject(
      { statut: 'CANCELLED', motifAnnulation: 'Examinateur absent' },
    );
  });

  it('retrait après un refus : REFUSE avec la date, pas BROUILLON', async () => {
    const base = creerBase();
    ajouterCompte(base, 'c0');
    base.db.users.get('c0')!.whitelistRejectedAt = new Date(
      MAINTENANT.getTime() - 60 * MIN,
    );
    const { douane } = services(base);
    const etat = await douane.etatCandidat('c0');
    expect(etat.phase).toBe('REFUSE');
    expect(etat.raison).toBe(
      'Ta candidature a été refusée : nouvelle candidature complète possible le 31/10 à 11 h.',
    );
  });
});

// ═══════════════════════════ Décision d'oral ═══════════════════════════

describe('DouaneOralService.decideOral', () => {
  function avecPlace(
    base: Base,
    userId: string,
    convocationAt: Date,
    seanceId = 'S1',
  ) {
    base.db.reservations.push({
      id: `place-${userId}`,
      seanceId,
      applicationId: `app-${userId}`,
      userId,
      convocationAt,
      statut: 'BOOKED',
      presentAt: null,
      resultat: null,
      decidedAt: null,
      examinateurUserId: null,
    });
  }

  function baseOral() {
    const base = creerBase();
    ajouterCompte(base, 'exam', Role.WHITELIST_REVIEWER);
    ajouterSeance(
      base,
      'S1',
      new Date(MAINTENANT.getTime() - 10 * MIN),
      120,
      8,
    );
    ajouterSeance(base, 'S2', CE_SOIR, 60, 8);
    ajouterSeance(base, 'S3', DEMAIN_SOIR, 60, 8);
    ajouterCandidat(base, 'c0');
    return base;
  }

  it('ACCEPTÉ hors jeu : oral passé, rôle WHITELISTED, place tranchée, voix programmée, audit', async () => {
    const base = baseOral();
    avecPlace(base, 'c0', new Date(MAINTENANT.getTime() - 5 * MIN));
    const { oral, audit, messages } = services(base);

    const vue = await oral.decideOral(
      'app-c0',
      'exam',
      AppStatus.APPROVED,
      null,
      "Très à l'aise.",
      {
        via: OralVia.HORS_JEU,
        motifHorsJeu: 'crash du jeu',
      },
    );

    expect(vue).toMatchObject({
      phase: 'ACCEPTE',
      status: 'APPROVED',
      oralStatus: 'APPROVED',
      oralVia: 'HORS_JEU',
    });
    const app = base.db.apps.get('app-c0')!;
    expect(app.status).toBe(AppStatus.APPROVED);
    expect(app.oralExaminerUserId).toBe('exam');
    expect(app.oralNotes).toBe(
      "Oral hors jeu : crash du jeu\n\nTrès à l'aise.",
    );
    expect(app.voicePurgeAt).toEqual(
      new Date(MAINTENANT.getTime() + 30 * 24 * 60 * MIN),
    );
    const user = base.db.users.get('c0')!;
    expect(user.oralPassedAt).toEqual(MAINTENANT);
    expect(user.role).toBe(Role.WHITELISTED);
    const place = base.db.reservations[0];
    expect(place).toMatchObject({
      statut: 'DONE',
      resultat: 'APPROVED',
      examinateurUserId: 'exam',
    });
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        actorId: 'exam',
        action: 'whitelist.oral.approved',
        targetUserId: 'c0',
        source: 'panel',
        metadata: expect.objectContaining({
          via: 'HORS_JEU',
          motifHorsJeu: 'crash du jeu',
          notesPrivees: true,
        }),
      }),
    );
    expect(messages.postSystemMessage).toHaveBeenCalledWith(
      'app-c0',
      expect.stringContaining('bienvenue'),
    );
  });

  it("hors jeu avant l'ouverture de la fenêtre : la place est rendue à la séance", async () => {
    const base = baseOral();
    avecPlace(base, 'c0', CE_SOIR, 'S2');
    const { oral } = services(base);

    await oral.decideOral('app-c0', 'exam', AppStatus.APPROVED, null, null, {
      motifHorsJeu: 'micro du jeu',
    });

    expect(base.db.reservations[0]).toMatchObject({
      statut: 'CANCELLED',
      motifAnnulation: 'Oral passé hors jeu',
    });
  });

  it('À REVOIR : motif obligatoire, écrit gardé, nouvelle séance dès le lendemain seulement', async () => {
    const base = baseOral();
    avecPlace(base, 'c0', new Date(MAINTENANT.getTime() - 5 * MIN));
    const { oral, douane } = services(base);

    await expect(
      oral.decideOral('app-c0', 'exam', AppStatus.NEEDS_REVISION, '  ', null, {
        motifHorsJeu: 'crash',
      }),
    ).rejects.toBeInstanceOf(BadRequestException);

    const vue = await oral.decideOral(
      'app-c0',
      'exam',
      AppStatus.NEEDS_REVISION,
      'Lore du clan à revoir.',
      null,
      {
        motifHorsJeu: 'crash',
      },
    );
    expect(vue.phase).toBe('DOUANE_A_REVOIR');
    expect(vue.status).toBe(AppStatus.PENDING);
    expect(vue.prochaineSeanceDes).toBe('2026-10-30T23:00:00.000Z');
    const app = base.db.apps.get('app-c0')!;
    expect(app.oralReason).toBe('Lore du clan à revoir.');
    expect(app.voicePurgeAt).toBeNull();
    expect(base.db.users.get('c0')).toMatchObject({
      oralPassedAt: null,
      whitelistRejectedAt: null,
      role: Role.PLAYER,
    });
    expect(base.db.reservations[0]).toMatchObject({
      statut: 'DONE',
      resultat: 'NEEDS_REVISION',
    });

    // Le soir même : non. Le lendemain : oui.
    await expect(douane.reserver('c0', 'S2')).rejects.toThrow(
      /à partir du samedi 31 octobre/,
    );
    await expect(douane.reserver('c0', 'S3')).resolves.toMatchObject({
      seanceId: 'S3',
    });
  });

  it('REFUSÉ : statut REJECTED, délai de 1 jour, voix programmée, plus de réservation', async () => {
    const base = baseOral();
    avecPlace(base, 'c0', new Date(MAINTENANT.getTime() - 5 * MIN));
    const { oral, douane } = services(base);

    const vue = await oral.decideOral(
      'app-c0',
      'exam',
      AppStatus.REJECTED,
      'Règlement non maîtrisé.',
      'Hésitant.',
      {
        motifHorsJeu: 'crash',
      },
    );

    expect(vue.phase).toBe('REFUSE');
    expect(vue.canReapplyAt).toBe('2026-10-31T11:00:00.000Z');
    expect(base.db.apps.get('app-c0')!.status).toBe(AppStatus.REJECTED);
    expect(base.db.apps.get('app-c0')!.voicePurgeAt).toEqual(
      new Date(MAINTENANT.getTime() + 30 * 24 * 60 * MIN),
    );
    expect(base.db.users.get('c0')).toMatchObject({
      whitelistRejectedAt: MAINTENANT,
      role: Role.PLAYER,
      oralPassedAt: null,
    });
    await expect(douane.reserver('c0', 'S3')).rejects.toThrow(/refusée/);
  });

  it('oral en jeu (lot B) : exige une place en cours', async () => {
    const base = baseOral();
    const { oral } = services(base);

    await expect(
      oral.decideOral('app-c0', 'exam', AppStatus.APPROVED, null, null, {
        via: OralVia.DOUANE,
      }),
    ).rejects.toThrow(/Aucune place/);

    avecPlace(base, 'c0', new Date(MAINTENANT.getTime() - 5 * MIN));
    const vue = await oral.decideOral(
      'app-c0',
      'exam',
      AppStatus.APPROVED,
      null,
      null,
      {
        via: OralVia.DOUANE,
        source: 'system',
      },
    );
    expect(vue.oralVia).toBe('DOUANE');
    expect(base.db.reservations[0].statut).toBe('DONE');
  });

  it('refus : examinateur sans le rôle, sa propre candidature, motif du hors jeu absent', async () => {
    const base = baseOral();
    ajouterCompte(base, 'helper', Role.HELPER);
    ajouterCandidat(base, 'relecteur');
    base.db.users.get('relecteur')!.role = Role.WHITELIST_REVIEWER;
    const { oral } = services(base);

    await expect(
      oral.decideOral('app-c0', 'helper', AppStatus.APPROVED, null, null, {
        motifHorsJeu: 'crash',
      }),
    ).rejects.toBeInstanceOf(ForbiddenException);
    await expect(
      oral.decideOral(
        'app-relecteur',
        'relecteur',
        AppStatus.APPROVED,
        null,
        null,
        { motifHorsJeu: 'crash' },
      ),
    ).rejects.toThrow(/propre candidature/);
    await expect(
      oral.decideOral('app-c0', 'exam', AppStatus.PENDING, null, null, {
        motifHorsJeu: 'crash',
      }),
    ).rejects.toBeInstanceOf(BadRequestException);
    // Douane ouverte en jeu : un oral hors jeu est un secours, motif obligatoire (Q9).
    const enJeu = services(base, { REBORN_DOUANE_ENABLED: 'true' });
    await expect(
      enJeu.oral.decideOral('app-c0', 'exam', AppStatus.APPROVED, null, null),
    ).rejects.toThrow(/hors jeu/);
  });

  it('transition (douane pas encore en jeu) : motif du hors jeu facultatif, motif par défaut inscrit', async () => {
    const base = baseOral();
    const { oral, audit } = services(base);
    await oral.decideOral('app-c0', 'exam', AppStatus.APPROVED, null, null);
    expect(base.db.apps.get('app-c0')!.oralNotes).toBe(
      'Oral hors jeu : Transition : douane en jeu pas encore ouverte',
    );
    expect(audit.log).toHaveBeenCalledWith(
      expect.objectContaining({
        metadata: expect.objectContaining({
          motifHorsJeu: 'Transition : douane en jeu pas encore ouverte',
        }),
      }),
    );
  });

  it('refus : écrit pas accepté, ou oral déjà tranché', async () => {
    const base = baseOral();
    ajouterCandidat(base, 'c1', {
      rpStatus: AppStatus.PENDING,
      writtenApprovedAt: null,
    });
    const { oral } = services(base);

    await expect(
      oral.decideOral('app-c1', 'exam', AppStatus.APPROVED, null, null, {
        motifHorsJeu: 'crash',
      }),
    ).rejects.toThrow(/écrit n'est pas accepté/);

    await oral.decideOral('app-c0', 'exam', AppStatus.APPROVED, null, null, {
      motifHorsJeu: 'crash',
    });
    await expect(
      oral.decideOral('app-c0', 'exam', AppStatus.REJECTED, 'Non.', null, {
        motifHorsJeu: 'crash',
      }),
    ).rejects.toBeInstanceOf(ConflictException);
  });
});

describe('DouaneService.annulerPlaceDepuisJeu — retrait et report (§3.10), MP au candidat', () => {
  function monterAnnulation(discordUserId: string | null) {
    const place = {
      id: 'r1',
      seanceId: 'S1',
      applicationId: 'app-c0',
      userId: 'c0',
      statut: 'CANCELLED',
      convocationAt: CE_SOIR,
      presentAt: CE_SOIR,
      calledAt: null,
      departAt: CE_SOIR,
      resultat: null,
      decidedAt: null,
      rappelEnvoyeAt: null,
      annuleeAt: MAINTENANT,
      motifAnnulation: null,
      createdAt: MAINTENANT,
      user: { id: 'c0', minecraftUsername: 'Kaito', discordUsername: null },
      application: {
        id: 'app-c0',
        firstName: 'Kaito',
        lastName: 'Arashi',
        village: 'Konohagakure',
        oralStatus: AppStatus.PENDING,
      },
      examinateur: null,
    };
    const prisma = {
      douaneReservation: {
        updateMany: jest.fn().mockResolvedValue({ count: 1 }),
        findUniqueOrThrow: jest.fn().mockResolvedValue(place),
        findMany: jest.fn().mockResolvedValue([]),
      },
      user: {
        findUnique: jest
          .fn()
          .mockResolvedValue({ discordUserId, minecraftUsername: 'Kaito' }),
      },
    };
    const messages = {
      postSystemMessage: jest.fn().mockResolvedValue(undefined),
    };
    const webhooks = { directMessage: jest.fn().mockResolvedValue(undefined) };
    const douane = new DouaneService(
      prisma as never,
      { log: jest.fn() } as never,
      messages as never,
      { get: jest.fn(() => undefined) } as never,
      webhooks as never,
    );
    return { douane, prisma, messages, webhooks };
  }

  it('retrait : place CANCELLED avec le motif de l’examinateur, fil et MP « douane »', async () => {
    const m = monterAnnulation('123456789012345678');
    await m.douane.annulerPlaceDepuisJeu('r1', 'RETRAIT', ' Trouble le hall. ');
    expect(m.prisma.douaneReservation.updateMany).toHaveBeenCalledWith({
      where: { id: 'r1', statut: 'BOOKED' },
      data: expect.objectContaining({
        statut: 'CANCELLED',
        motifAnnulation: 'Trouble le hall.',
      }),
    });
    expect(m.messages.postSystemMessage).toHaveBeenCalledWith(
      'app-c0',
      expect.stringContaining('Motif : Trouble le hall.'),
    );
    await jest.advanceTimersByTimeAsync(0);
    expect(m.webhooks.directMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        discordUserId: '123456789012345678',
        context: expect.objectContaining({
          kind: 'douane',
          entityId: 'app-c0',
        }),
      }),
    );
  });

  it('retrait sans motif : refusé ; report : motif fixe, pas de MP sans Discord lié', async () => {
    const m = monterAnnulation(null);
    await expect(
      m.douane.annulerPlaceDepuisJeu('r1', 'RETRAIT', '  '),
    ).rejects.toBeInstanceOf(BadRequestException);
    expect(m.prisma.douaneReservation.updateMany).not.toHaveBeenCalled();
    await m.douane.annulerPlaceDepuisJeu('r1', 'REPORT');
    expect(m.prisma.douaneReservation.updateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          motifAnnulation: 'Séance reportée : aucun examinateur présent',
        }),
      }),
    );
    await jest.advanceTimersByTimeAsync(0);
    expect(m.webhooks.directMessage).not.toHaveBeenCalled();
  });
});
