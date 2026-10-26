import {
  BadRequestException,
  ForbiddenException,
  Logger,
} from '@nestjs/common';
import { createHmac } from 'node:crypto';
import { fenetreEntree } from '../douane/douane.regles';
import { formatConvocation } from './douane-fenetre';
import {
  DOUANE_KEY_LABEL,
  PlayService,
  type PlayTokenPayload,
} from './play.service';

const SECRET = 'test-secret-with-32-or-more-chars-ok!';

/** Clé des jetons douane : HMAC-SHA256(secret, "reborn-douane-v1"), comme Guardian 0.2.0. */
const DOUANE_KEY = createHmac('sha256', SECRET)
  .update(DOUANE_KEY_LABEL)
  .digest();

function sign(key: string | Buffer, payload: object): string {
  const payloadB64 = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const sig = createHmac('sha256', key).update(payloadB64).digest('base64url');
  return `${payloadB64}.${sig}`;
}

/**
 * Jeton de jeu (25/10) : claims `scope` et `aud`, portée douane derrière REBORN_DOUANE_ENABLED,
 * fenêtre d'entrée H − 15 min à H + 30 min (CANDIDATURE_ET_DOUANE.md §3.3).
 */
describe('PlayService.issuePlayToken — portée et serveur visé', () => {
  // Samedi 24/10/2026, 21 h à Paris (UTC+2).
  const H = new Date('2026-10-24T19:00:00Z');

  let prisma: {
    user: { findUnique: jest.Mock };
    whitelistApplication: { findUnique: jest.Mock };
    douaneReservation: { findFirst: jest.Mock };
  };

  function service(env: Record<string, string | undefined> = {}): PlayService {
    const all: Record<string, string | undefined> = {
      REBORN_PLAY_TOKEN_SECRET: SECRET,
      ...env,
    };
    const config = { get: jest.fn((k: string) => all[k]) };
    return new PlayService(config as never, prisma as never);
  }

  /** Décode le jeton après avoir vérifié sa signature avec la clé de sa portée. */
  function decode(token: string): PlayTokenPayload {
    const [payloadB64, sigB64] = token.split('.');
    const payload = JSON.parse(
      Buffer.from(payloadB64, 'base64url').toString('utf8'),
    ) as PlayTokenPayload;
    const key = payload.scope === 'douane' ? DOUANE_KEY : SECRET;
    const expected = createHmac('sha256', key)
      .update(payloadB64)
      .digest('base64url');
    expect(sigB64).toBe(expected);
    return payload;
  }

  function compte(over: Record<string, unknown> = {}) {
    prisma.user.findUnique.mockResolvedValue({
      id: 'u1',
      minecraftUuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5',
      minecraftUsername: 'Notch',
      role: 'PLAYER',
      banned: false,
      oralPassedAt: null,
      ...over,
    });
  }

  function ecritAccepte(over: Record<string, unknown> = {}) {
    prisma.whitelistApplication.findUnique.mockResolvedValue({
      id: 'app1',
      status: 'PENDING',
      writtenApprovedAt: new Date('2026-10-20T10:00:00Z'),
      oralStatus: 'PENDING',
      ...over,
    });
  }

  const DOUANE_ON = { REBORN_DOUANE_ENABLED: 'true', REBORN_SERVER_ID: 'prod' };

  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'error').mockImplementation(() => undefined);
    prisma = {
      user: { findUnique: jest.fn() },
      whitelistApplication: { findUnique: jest.fn() },
      douaneReservation: {
        findFirst: jest.fn().mockResolvedValue({ convocationAt: H }),
      },
    };
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  // ── Joueurs whitelistés et staff ──────────────────────────────────────

  it('whitelisté : portée play et aud du serveur par défaut', async () => {
    compte({ role: 'WHITELISTED' });
    const res = await service({ REBORN_SERVER_ID: 'prod' }).issuePlayToken(
      'u1',
    );
    const payload = decode(res.playToken);
    expect(payload).toMatchObject({
      sub: 'u1',
      mcUsername: 'Notch',
      scope: 'play',
      aud: 'prod',
    });
    expect(payload.exp - payload.iat).toBe(300);
    expect(res).toMatchObject({ scope: 'play', aud: 'prod' });
  });

  it('sans REBORN_SERVER_ID : aucun aud (ancien comportement)', async () => {
    compte({ role: 'WHITELISTED' });
    const res = await service().issuePlayToken('u1');
    expect('aud' in decode(res.playToken)).toBe(false);
    expect(res.aud).toBeNull();
  });

  it('staff avec la douane active : toujours play, aucune lecture de réservation', async () => {
    compte({ role: 'HELPER' });
    const res = await service(DOUANE_ON).issuePlayToken('u1');
    expect(decode(res.playToken).scope).toBe('play');
    expect(prisma.douaneReservation.findFirst).not.toHaveBeenCalled();
  });

  it('serveur demandé connu : aud = ce serveur', async () => {
    compte({ role: 'WHITELISTED' });
    const res = await service({
      REBORN_SERVER_ID: 'prod',
      REBORN_SERVER_IDS: 'prod, dev',
    }).issuePlayToken('u1', 'dev');
    expect(decode(res.playToken).aud).toBe('dev');
  });

  it('serveur demandé inconnu : 400', async () => {
    compte({ role: 'WHITELISTED' });
    await expect(
      service({
        REBORN_SERVER_ID: 'prod',
        REBORN_SERVER_IDS: 'dev',
      }).issuePlayToken('u1', 'build'),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it("serveur demandé mais aucun serveur connu de l'API : pas de aud", async () => {
    compte({ role: 'WHITELISTED' });
    const res = await service().issuePlayToken('u1', 'dev');
    expect(res.aud).toBeNull();
  });

  it("identifiant de serveur invalide dans l'environnement : ignoré", async () => {
    compte({ role: 'WHITELISTED' });
    const res = await service({
      REBORN_SERVER_ID: 'Prod Serveur',
    }).issuePlayToken('u1');
    expect(res.aud).toBeNull();
  });

  it('compte banni : 403', async () => {
    compte({ role: 'WHITELISTED', banned: true });
    await expect(service().issuePlayToken('u1')).rejects.toThrow('banni');
  });

  // ── Candidats (PLAYER) ───────────────────────────────────────────────

  it('candidat, douane coupée (défaut) : 403 candidature requise', async () => {
    compte();
    ecritAccepte();
    await expect(
      service({ REBORN_SERVER_ID: 'prod' }).issuePlayToken('u1'),
    ).rejects.toThrow(
      'Ta candidature doit être acceptée avant de rejoindre le serveur.',
    );
  });

  it('candidat dans sa fenêtre : portée douane et aud', async () => {
    jest.useFakeTimers({
      now: new Date(H.getTime() - 10 * 60_000),
      doNotFake: ['nextTick', 'setImmediate'],
    });
    compte();
    ecritAccepte();
    const res = await service(DOUANE_ON).issuePlayToken('u1');
    expect(decode(res.playToken)).toMatchObject({
      scope: 'douane',
      aud: 'prod',
    });
    const calls = prisma.douaneReservation.findFirst.mock.calls as [
      [{ where: Record<string, unknown> }],
    ];
    const [[args]] = calls;
    expect(args.where).toMatchObject({
      userId: 'u1',
      applicationId: 'app1',
      statut: 'BOOKED',
    });
  });

  it('candidat encore dans la fenêtre à H + 30 min (borne comprise)', async () => {
    jest.useFakeTimers({
      now: new Date(H.getTime() + 30 * 60_000),
      doNotFake: ['nextTick', 'setImmediate'],
    });
    compte();
    ecritAccepte();
    const res = await service(DOUANE_ON).issuePlayToken('u1');
    expect(res.scope).toBe('douane');
  });

  it("candidat trop tôt : 403 avec l'heure de la séance", async () => {
    jest.useFakeTimers({
      now: new Date(H.getTime() - 20 * 60_000),
      doNotFake: ['nextTick', 'setImmediate'],
    });
    compte();
    ecritAccepte();
    await expect(service(DOUANE_ON).issuePlayToken('u1')).rejects.toThrow(
      "Ta séance de douane est prévue samedi 24 octobre à 21 h. L'entrée ouvre 15 min avant.",
    );
  });

  it('candidat trop tard : 403 séance passée', async () => {
    jest.useFakeTimers({
      now: new Date(H.getTime() + 35 * 60_000),
      doNotFake: ['nextTick', 'setImmediate'],
    });
    compte();
    ecritAccepte();
    await expect(service(DOUANE_ON).issuePlayToken('u1')).rejects.toThrow(
      'réserve une nouvelle séance',
    );
  });

  it('retard maximal réglable par DOUANE_RETARD_MAX_MIN (même variable que le launcher)', async () => {
    jest.useFakeTimers({
      now: new Date(H.getTime() + 35 * 60_000),
      doNotFake: ['nextTick', 'setImmediate'],
    });
    compte();
    ecritAccepte();
    const res = await service({
      ...DOUANE_ON,
      DOUANE_RETARD_MAX_MIN: '45',
    }).issuePlayToken('u1');
    expect(res.scope).toBe('douane');
  });

  it('fenêtre du jeton = fenetreEntree de douane.regles, bornes comprises', async () => {
    const f = fenetreEntree(H, 45);
    const env = { ...DOUANE_ON, DOUANE_RETARD_MAX_MIN: '45' };
    compte();
    ecritAccepte();

    jest.useFakeTimers({
      now: f.ouvreAt,
      doNotFake: ['nextTick', 'setImmediate'],
    });
    expect((await service(env).issuePlayToken('u1')).scope).toBe('douane');

    jest.setSystemTime(f.fermeAt);
    expect((await service(env).issuePlayToken('u1')).scope).toBe('douane');

    jest.setSystemTime(new Date(f.fermeAt.getTime() + 1000));
    await expect(service(env).issuePlayToken('u1')).rejects.toThrow(
      'réserve une nouvelle séance',
    );
  });

  it('retard hors des bornes du launcher (5 à 120) : 30 min, comme le launcher', async () => {
    jest.useFakeTimers({
      now: new Date(H.getTime() + 35 * 60_000),
      doNotFake: ['nextTick', 'setImmediate'],
    });
    compte();
    ecritAccepte();
    await expect(
      service({ ...DOUANE_ON, DOUANE_RETARD_MAX_MIN: '200' }).issuePlayToken(
        'u1',
      ),
    ).rejects.toThrow('réserve une nouvelle séance');
  });

  it("l'ancien nom REBORN_DOUANE_RETARD_MAX_MIN n'est plus lu", async () => {
    jest.useFakeTimers({
      now: new Date(H.getTime() + 35 * 60_000),
      doNotFake: ['nextTick', 'setImmediate'],
    });
    compte();
    ecritAccepte();
    await expect(
      service({
        ...DOUANE_ON,
        REBORN_DOUANE_RETARD_MAX_MIN: '45',
      }).issuePlayToken('u1'),
    ).rejects.toThrow('réserve une nouvelle séance');
  });

  it('écrit accepté sans réservation : 403 réserve ta séance', async () => {
    compte();
    ecritAccepte();
    prisma.douaneReservation.findFirst.mockResolvedValue(null);
    await expect(service(DOUANE_ON).issuePlayToken('u1')).rejects.toThrow(
      'réserve ta séance de douane',
    );
  });

  it('écrit pas encore accepté : 403 candidature requise', async () => {
    compte();
    ecritAccepte({ writtenApprovedAt: null });
    await expect(service(DOUANE_ON).issuePlayToken('u1')).rejects.toThrow(
      MSG(),
    );
    expect(prisma.douaneReservation.findFirst).not.toHaveBeenCalled();
  });

  it('oral refusé ou candidature refusée : 403', async () => {
    compte();
    ecritAccepte({ oralStatus: 'REJECTED' });
    await expect(
      service(DOUANE_ON).issuePlayToken('u1'),
    ).rejects.toBeInstanceOf(ForbiddenException);
    ecritAccepte({ status: 'REJECTED' });
    await expect(
      service(DOUANE_ON).issuePlayToken('u1'),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('aucune candidature : 403', async () => {
    compte();
    prisma.whitelistApplication.findUnique.mockResolvedValue(null);
    await expect(service(DOUANE_ON).issuePlayToken('u1')).rejects.toThrow(
      MSG(),
    );
  });

  it('oral déjà réussi, écrit pas encore accepté (dossier migré) : candidature requise, pas de douane', async () => {
    compte({ oralPassedAt: new Date('2026-10-01T10:00:00Z') });
    prisma.whitelistApplication.findUnique.mockResolvedValue({
      status: 'PENDING',
      hrpStatus: 'APPROVED',
      rpStatus: 'PENDING',
      oralStatus: 'APPROVED',
      oralDecidedAt: null,
      reviewedAt: null,
    });
    await expect(service(DOUANE_ON).issuePlayToken('u1')).rejects.toThrow(
      MSG(),
    );
    expect(prisma.douaneReservation.findFirst).not.toHaveBeenCalled();
  });

  it('oral déjà réussi et écrit accepté, rôle encore PLAYER : incohérence, ticket (douane coupée ou non)', async () => {
    const warn = jest
      .spyOn(Logger.prototype, 'warn')
      .mockImplementation(() => undefined);
    compte({ oralPassedAt: new Date('2026-10-01T10:00:00Z') });
    prisma.whitelistApplication.findUnique.mockResolvedValue({
      status: 'PENDING',
      hrpStatus: 'APPROVED',
      rpStatus: 'APPROVED',
      oralStatus: 'PENDING',
      oralDecidedAt: null,
      reviewedAt: null,
    });
    for (const env of [DOUANE_ON, {}]) {
      await expect(service(env).issuePlayToken('u1')).rejects.toThrow(
        "Ton accès est validé mais ton compte n'est pas à jour : ouvre un ticket.",
      );
    }
    expect(warn).toHaveBeenCalled();
  });

  it('douane active sans identifiant de serveur : jeton douane refusé', async () => {
    jest.useFakeTimers({ now: H, doNotFake: ['nextTick', 'setImmediate'] });
    compte();
    ecritAccepte();
    await expect(
      service({ REBORN_DOUANE_ENABLED: 'true' }).issuePlayToken('u1'),
    ).rejects.toThrow('pas encore ouverte');
  });

  // ── Clé des jetons douane ────────────────────────────────────────────

  it("jeton douane : signé par la clé dérivée, illisible pour un Guardian d'avant le 25/10", async () => {
    jest.useFakeTimers({ now: H, doNotFake: ['nextTick', 'setImmediate'] });
    compte();
    ecritAccepte();
    const res = await service(DOUANE_ON).issuePlayToken('u1');
    const [payloadB64, sigB64] = res.playToken.split('.');
    // Guardian 0.1.0 vérifie avec le secret brut : signature invalide.
    expect(sigB64).not.toBe(
      createHmac('sha256', SECRET).update(payloadB64).digest('base64url'),
    );
    expect(decode(res.playToken).scope).toBe('douane');
    expect(service().verifyPlayToken(res.playToken)).toMatchObject({
      scope: 'douane',
    });
  });

  it('vecteur partagé avec PlayTokenVerifierTest (Guardian)', () => {
    const payloadB64 = Buffer.from(
      '{"sub":"user-1","mcUuid":"069a79f4-44e9-4726-a5be-fca90e38aaf5","mcUsername":"Notch",' +
        '"iat":1800000000,"exp":1800000300,"scope":"douane","aud":"dev"}',
    ).toString('base64url');
    expect(
      createHmac('sha256', DOUANE_KEY).update(payloadB64).digest('base64url'),
    ).toBe('QIT09w5-HKMdiOMPB7ZR5WFvWqCAsNp8m6CQocI668U');
  });

  it('la clé qui signe fixe la portée', () => {
    const svc = service();
    const now = Math.floor(Date.now() / 1000);
    const base = {
      sub: 'u1',
      mcUuid: 'x',
      mcUsername: 'Notch',
      iat: now,
      exp: now + 60,
    };
    // Payload douane signé avec le secret brut : refusé.
    expect(
      svc.verifyPlayToken(sign(SECRET, { ...base, scope: 'douane' })),
    ).toBeNull();
    expect(
      svc.verifyPlayTokenSignature(sign(SECRET, { ...base, scope: 'douane' })),
    ).toBeNull();
    // Payload play (ou sans portée) signé avec la clé douane : refusé.
    expect(
      svc.verifyPlayToken(sign(DOUANE_KEY, { ...base, scope: 'play' })),
    ).toBeNull();
    expect(svc.verifyPlayToken(sign(DOUANE_KEY, base))).toBeNull();
    // Chaque portée avec sa clé : accepté.
    expect(
      svc.verifyPlayToken(sign(DOUANE_KEY, { ...base, scope: 'douane' })),
    ).toMatchObject({ scope: 'douane' });
    expect(
      svc.verifyPlayToken(sign(SECRET, { ...base, scope: 'play' })),
    ).toMatchObject({ scope: 'play' });
  });

  it('payload signé mais pas un objet : refusé sans exception', () => {
    const svc = service();
    const payloadB64 = Buffer.from('null').toString('base64url');
    const sig = createHmac('sha256', SECRET)
      .update(payloadB64)
      .digest('base64url');
    expect(svc.verifyPlayToken(`${payloadB64}.${sig}`)).toBeNull();
  });

  // ── Compatibilité ────────────────────────────────────────────────────

  it('verifyPlayToken accepte encore un jeton sans scope ni aud', () => {
    const svc = service();
    const now = Math.floor(Date.now() / 1000);
    const payloadB64 = Buffer.from(
      JSON.stringify({
        sub: 'u1',
        mcUuid: 'x',
        mcUsername: 'Notch',
        iat: now,
        exp: now + 60,
      }),
    ).toString('base64url');
    const sig = createHmac('sha256', SECRET)
      .update(payloadB64)
      .digest('base64url');
    expect(svc.verifyPlayToken(`${payloadB64}.${sig}`)).toMatchObject({
      sub: 'u1',
    });
  });

  function MSG() {
    return 'Ta candidature doit être acceptée avant de rejoindre le serveur.';
  }
});

describe('douane-fenetre', () => {
  const H = new Date('2026-10-24T19:00:00Z');

  it("heure de Paris, changement d'heure compris", () => {
    expect(formatConvocation(H)).toBe('samedi 24 octobre à 21 h');
    // 31/10 : heure d'hiver (UTC+1).
    expect(formatConvocation(new Date('2026-10-31T20:30:00Z'))).toBe(
      'samedi 31 octobre à 21 h 30',
    );
  });
});
