import {
  ExecutionContext,
  HttpException,
  Logger,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Prisma } from '@prisma/client';
import { createHmac } from 'node:crypto';
import {
  GARDE_NONCE_MS,
  LimiteurDebit,
  SEAUX_MAX,
  lireSecretsJeu,
  type RequeteJeu,
} from './game-signature';
import { GameSignatureGuard } from './game-signature.guard';
import { GameSignatureService, type LimiteJeu } from './game-signature.service';

// Routes signées du serveur de jeu (CANDIDATURE_ET_DOUANE.md §3.9, §3.12) :
// signature sur méthode, chemin, serveur, horodatage, nonce et corps brut ;
// horodatage à ± 60 s ; nonce à usage unique ; limite de débit par serveur.

const SECRET_DEV = 'secret-du-serveur-dev-32-caracteres-min!';
const SECRET_BUILD = 'secret-du-serveur-build-32-caracteres-mi';
const UUID = '069a79f4-44e9-4726-a5be-fca90e38aaf5';
const LIMITE: LimiteJeu = { nom: 'test', parMinute: 1000 };

/**
 * Signature calculée comme le plugin Java la calculera, à partir du contrat
 * écrit (concaténation de chaînes), pas avec les fonctions de l'API.
 */
function signerCommeLePlugin(
  secret: string,
  methode: string,
  chemin: string,
  serveur: string,
  ts: string,
  nonce: string,
  corps: string,
): string {
  const chaine = `REBORN-GAME-V1\n${methode}\n${chemin}\n${serveur}\n${ts}\n${nonce}\n${corps}`;
  return createHmac('sha256', secret).update(chaine, 'utf8').digest('hex');
}

let numeroNonce = 0;
function nonceNeuf(): string {
  numeroNonce += 1;
  return `nonce-de-test-${String(numeroNonce).padStart(6, '0')}`;
}

function requete(
  o: {
    secret?: string;
    methode?: string;
    chemin?: string;
    serveur?: string;
    ts?: number;
    nonce?: string;
    corps?: string;
    /** Ce que reçoit l'API, s'il diffère de ce qui a été signé. */
    recu?: Partial<{ methode: string; chemin: string; corps: string }>;
  } = {},
): RequeteJeu {
  const methode = o.methode ?? 'GET';
  const chemin = o.chemin ?? `/v1/game/acces/${UUID}`;
  const serveur = o.serveur ?? 'dev';
  const ts = String(o.ts ?? Math.floor(Date.now() / 1000));
  const nonce = o.nonce ?? nonceNeuf();
  const corps = o.corps ?? '';
  return {
    methode: o.recu?.methode ?? methode,
    chemin: o.recu?.chemin ?? chemin,
    enTetes: {
      'x-reborn-server': serveur,
      'x-reborn-timestamp': ts,
      'x-reborn-nonce': nonce,
      'x-reborn-signature': signerCommeLePlugin(
        o.secret ?? SECRET_DEV,
        methode,
        chemin,
        serveur,
        ts,
        nonce,
        corps,
      ),
    },
    corps: Buffer.from(o.recu?.corps ?? corps, 'utf8'),
  };
}

describe('GameSignatureService', () => {
  let prisma: {
    gameRequestNonce: { create: jest.Mock; deleteMany: jest.Mock };
  };
  const vus = new Set<string>();
  let avertir: jest.SpyInstance;

  function service(
    env: Record<string, string | undefined> = {
      REBORN_GAME_SECRETS: `dev:${SECRET_DEV},build:${SECRET_BUILD}`,
    },
  ): GameSignatureService {
    const config = { get: jest.fn((k: string) => env[k]) };
    return new GameSignatureService(config as never, prisma as never);
  }

  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
    avertir = jest
      .spyOn(Logger.prototype, 'warn')
      .mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'error').mockImplementation(() => undefined);
    vus.clear();
    prisma = {
      gameRequestNonce: {
        // Simule la clé primaire : un nonce déjà vu lève P2002.
        create: jest.fn(({ data }: { data: { nonce: string } }) => {
          if (vus.has(data.nonce)) {
            return Promise.reject(
              new Prisma.PrismaClientKnownRequestError('Unique constraint', {
                code: 'P2002',
                clientVersion: 'test',
              }),
            );
          }
          vus.add(data.nonce);
          return Promise.resolve({});
        }),
        deleteMany: jest.fn().mockResolvedValue({ count: 3 }),
      },
    };
  });

  afterEach(() => jest.restoreAllMocks());

  // ── Signature ─────────────────────────────────────────────────────────

  it('GET signé selon le contrat : accepté, serveur renvoyé, nonce inscrit', async () => {
    const res = await service().verifier(requete(), LIMITE);
    expect(res).toEqual({ ok: true, serveur: 'dev' });
    expect(prisma.gameRequestNonce.create).toHaveBeenCalledWith({
      data: { nonce: expect.any(String) as string, serverId: 'dev' },
    });
  });

  it('POST avec corps JSON accentué : accepté', async () => {
    const corps = JSON.stringify({
      motif: 'Réponses très hésitantes à l’oral',
    });
    const res = await service().verifier(
      requete({ methode: 'POST', chemin: '/v1/game/douane/decision', corps }),
      LIMITE,
    );
    expect(res.ok).toBe(true);
  });

  it('chemin avec requête : la requête « ?… » est signée', async () => {
    const chemin = `/v1/game/douane/dossier/${UUID}?examinateur=${UUID}`;
    expect((await service().verifier(requete({ chemin }), LIMITE)).ok).toBe(
      true,
    );
    const autre = await service().verifier(
      requete({
        chemin,
        recu: { chemin: `/v1/game/douane/dossier/${UUID}?examinateur=autre` },
      }),
      LIMITE,
    );
    expect(autre).toMatchObject({ ok: false, statut: 401 });
  });

  it.each([
    ['secret faux', { secret: 'un-tout-autre-secret-de-32-caracteres!!' }],
    [
      'corps modifié',
      {
        methode: 'POST',
        corps: '{"resultat":"REJECTED"}',
        recu: { corps: '{"resultat":"APPROVED"}' },
      },
    ],
    [
      'autre joueur (chemin)',
      { recu: { chemin: '/v1/game/acces/0000000000000000000000000000abcd' } },
    ],
    ['autre méthode', { recu: { methode: 'POST' } }],
  ])('refusé (401) : %s', async (_cas, o) => {
    const res = await service().verifier(requete(o), LIMITE);
    expect(res).toEqual({
      ok: false,
      statut: 401,
      message: 'Signature invalide.',
    });
    expect(prisma.gameRequestNonce.create).not.toHaveBeenCalled();
  });

  it('secret par serveur : le secret du build ne signe pas pour le dev', async () => {
    const res = await service().verifier(
      requete({ serveur: 'dev', secret: SECRET_BUILD }),
      LIMITE,
    );
    expect(res).toMatchObject({ ok: false, statut: 401 });
    const build = await service().verifier(
      requete({ serveur: 'build', secret: SECRET_BUILD }),
      LIMITE,
    );
    expect(build).toEqual({ ok: true, serveur: 'build' });
  });

  it('serveur inconnu de REBORN_GAME_SECRETS : 401', async () => {
    const res = await service().verifier(requete({ serveur: 'prod' }), LIMITE);
    expect(res).toMatchObject({ ok: false, statut: 401 });
  });

  it('changement de secret : deux secrets pour le même serveur, les deux passent', async () => {
    const nouveau = 'nouveau-secret-du-dev-32-caracteres-mini';
    const s = service({
      REBORN_GAME_SECRETS: `dev:${SECRET_DEV},dev:${nouveau}`,
    });
    expect((await s.verifier(requete(), LIMITE)).ok).toBe(true);
    expect((await s.verifier(requete({ secret: nouveau }), LIMITE)).ok).toBe(
      true,
    );
  });

  it('un seul secret (REBORN_GAME_SECRET) sans liste de serveurs : tout serveur, avec ce secret, mode signalé', async () => {
    const s = service({ REBORN_GAME_SECRET: SECRET_DEV });
    expect(await s.verifier(requete({ serveur: 'prod' }), LIMITE)).toEqual({
      ok: true,
      serveur: 'prod',
    });
    expect(avertir).toHaveBeenCalledWith(
      expect.stringContaining('un seul secret'),
    );
  });

  it('un seul secret avec REBORN_SERVER_IDS / REBORN_SERVER_ID : seuls ces serveurs passent', async () => {
    const s = service({
      REBORN_GAME_SECRET: SECRET_DEV,
      REBORN_SERVER_IDS: 'dev, build',
      REBORN_SERVER_ID: 'prod',
    });
    for (const serveur of ['dev', 'build', 'prod']) {
      expect(await s.verifier(requete({ serveur }), LIMITE)).toEqual({
        ok: true,
        serveur,
      });
    }
    const inconnu = await s.verifier(requete({ serveur: 'autre' }), LIMITE);
    expect(inconnu).toMatchObject({ ok: false, statut: 401 });
    expect(!inconnu.ok && inconnu.message).toContain('inconnu');
    expect(prisma.gameRequestNonce.create).toHaveBeenCalledTimes(3);
  });

  it('en-tête manquant ou mal formé : 401', async () => {
    const r = requete();
    delete r.enTetes['x-reborn-nonce'];
    expect(await service().verifier(r, LIMITE)).toMatchObject({
      ok: false,
      statut: 401,
    });
    const court = requete({ nonce: 'court' });
    expect(await service().verifier(court, LIMITE)).toMatchObject({
      ok: false,
      statut: 401,
    });
  });

  // ── Horodatage ────────────────────────────────────────────────────────

  it.each([
    ['vieux de 90 s', -90, false],
    ['en avance de 90 s', 90, false],
    ['vieux de 50 s', -50, true],
    ['en avance de 50 s', 50, true],
  ])('horodatage %s', async (_cas, decalage, accepte) => {
    const ts = Math.floor(Date.now() / 1000) + decalage;
    const res = await service().verifier(requete({ ts }), LIMITE);
    expect(res.ok).toBe(accepte);
    if (!res.ok) expect(res.statut).toBe(401);
  });

  // ── Nonce ─────────────────────────────────────────────────────────────

  it('nonce rejoué : la même requête, renvoyée telle quelle, est refusée', async () => {
    const s = service();
    const r = requete();
    expect((await s.verifier(r, LIMITE)).ok).toBe(true);
    expect(await s.verifier(r, LIMITE)).toEqual({
      ok: false,
      statut: 401,
      message: 'Nonce déjà utilisé : requête rejouée.',
    });
  });

  it('base indisponible pour le nonce : 503, jamais accepté', async () => {
    prisma.gameRequestNonce.create.mockRejectedValueOnce(new Error('down'));
    expect(await service().verifier(requete(), LIMITE)).toMatchObject({
      ok: false,
      statut: 503,
    });
  });

  it('purge : efface les nonces plus vieux que 3 min (3 × la tolérance)', async () => {
    const maintenant = new Date('2026-10-25T20:00:00Z');
    expect(await service().purgerNonces(maintenant)).toBe(3);
    expect(prisma.gameRequestNonce.deleteMany).toHaveBeenCalledWith({
      where: {
        createdAt: { lt: new Date(maintenant.getTime() - GARDE_NONCE_MS) },
      },
    });
    expect(GARDE_NONCE_MS).toBe(3 * 60 * 1000);
  });

  // ── Débit et configuration ───────────────────────────────────────────

  it('limite de débit : la requête de trop reçoit 429 et ne consomme pas de nonce', async () => {
    const s = service();
    const limite = { nom: 'decision', parMinute: 3 };
    for (let i = 0; i < 3; i++) {
      expect((await s.verifier(requete(), limite)).ok).toBe(true);
    }
    const trop = await s.verifier(requete(), limite);
    expect(trop).toMatchObject({ ok: false, statut: 429 });
    expect(!trop.ok && trop.reessayerDansS).toBeGreaterThanOrEqual(1);
    expect(prisma.gameRequestNonce.create).toHaveBeenCalledTimes(3);
    // Autre serveur : son propre seau.
    expect(
      (
        await s.verifier(
          requete({ serveur: 'build', secret: SECRET_BUILD }),
          limite,
        )
      ).ok,
    ).toBe(true);
  });

  it('aucun secret : routes coupées (503)', async () => {
    const s = service({});
    expect(s.actif).toBe(false);
    expect(await s.verifier(requete(), LIMITE)).toMatchObject({
      ok: false,
      statut: 503,
    });
  });

  it('secret égal à REBORN_WEBHOOK_SECRET : ignoré, routes coupées', () => {
    const s = service({
      REBORN_GAME_SECRET: SECRET_DEV,
      REBORN_WEBHOOK_SECRET: SECRET_DEV,
    });
    expect(s.actif).toBe(false);
  });
});

describe('lireSecretsJeu', () => {
  it('ignore un secret trop court et une entrée sans serveur', () => {
    const s = lireSecretsJeu({
      REBORN_GAME_SECRETS: `dev:court,${SECRET_BUILD},build:${SECRET_BUILD}`,
    });
    expect(s.mode).toBe('par-serveur');
    expect([...s.parServeur.keys()]).toEqual(['build']);
    expect(s.avertissements).toHaveLength(2);
  });

  it('REBORN_GAME_SECRETS rempli : REBORN_GAME_SECRET ignoré', () => {
    const s = lireSecretsJeu({
      REBORN_GAME_SECRET: SECRET_DEV,
      REBORN_GAME_SECRETS: `build:${SECRET_BUILD}`,
    });
    expect(s.mode).toBe('par-serveur');
    expect(s.unique).toBeNull();
  });

  it('un secret qui contient « : » reste entier', () => {
    const s = lireSecretsJeu({
      REBORN_GAME_SECRETS: `dev:${SECRET_DEV}:suite`,
    });
    expect(s.parServeur.get('dev')).toEqual([`${SECRET_DEV}:suite`]);
  });

  it('même secret pour deux serveurs : gardé, mais signalé', () => {
    const s = lireSecretsJeu({
      REBORN_GAME_SECRETS: `dev:${SECRET_DEV},prod:${SECRET_DEV}`,
    });
    expect([...s.parServeur.keys()]).toEqual(['dev', 'prod']);
    expect(s.avertissements).toHaveLength(1);
    expect(s.avertissements[0]).toContain('prod a le même secret que dev');
    expect(s.avertissements[0]).not.toContain(SECRET_DEV);
  });

  it('même secret répété pour un seul serveur : rien à signaler', () => {
    const s = lireSecretsJeu({
      REBORN_GAME_SECRETS: `dev:${SECRET_DEV},dev:${SECRET_DEV}`,
    });
    expect(s.avertissements).toHaveLength(0);
  });

  it('mode unique : serveurs admis lus dans REBORN_SERVER_IDS et REBORN_SERVER_ID', () => {
    const s = lireSecretsJeu({
      REBORN_GAME_SECRET: SECRET_DEV,
      REBORN_SERVER_IDS: 'dev,Build!,',
      REBORN_SERVER_ID: 'prod',
    });
    expect(s.mode).toBe('unique');
    expect([...s.serveursUnique].sort()).toEqual(['dev', 'prod']);
  });
});

describe('LimiteurDebit', () => {
  it('recharge au fil du temps', () => {
    let t = 0;
    const l = new LimiteurDebit(() => t);
    expect(l.prendre('k', 2)).toBe(0);
    expect(l.prendre('k', 2)).toBe(0);
    expect(l.prendre('k', 2)).toBe(30);
    t = 30_000;
    expect(l.prendre('k', 2)).toBe(0);
  });

  it('table bornée : les seaux inactifs depuis une minute sont effacés, les actifs gardés', () => {
    let t = 0;
    const l = new LimiteurDebit(() => t);
    for (let i = 0; i < SEAUX_MAX - 1; i++) l.prendre(`vieux-${i}`, 60);
    t = 30_000;
    // Seau actif, vidé : il doit survivre à la purge.
    expect(l.prendre('actif', 1)).toBe(0);
    expect(l.taille).toBe(SEAUX_MAX);
    t = 61_000;
    expect(l.prendre('neuf', 60)).toBe(0);
    expect(l.taille).toBe(2);
    expect(l.prendre('actif', 1)).toBeGreaterThan(0);
  });
});

describe('GameSignatureGuard', () => {
  function contexte(entetes: Record<string, string> = {}) {
    const req = {
      method: 'GET',
      originalUrl: `/v1/game/acces/${UUID}`,
      headers: entetes,
    } as Record<string, unknown>;
    const res = { setHeader: jest.fn() };
    const ctx = {
      switchToHttp: () => ({ getRequest: () => req, getResponse: () => res }),
      getHandler: () => () => undefined,
    } as unknown as ExecutionContext;
    return { ctx, req, res };
  }

  it('accepté : le serveur authentifié est posé sur la requête', async () => {
    const verifier = jest.fn().mockResolvedValue({ ok: true, serveur: 'dev' });
    const garde = new GameSignatureGuard(
      { verifier } as never,
      new Reflector(),
    );
    const { ctx, req } = contexte();
    await expect(garde.canActivate(ctx)).resolves.toBe(true);
    expect(req.serveurJeu).toBe('dev');
    expect(verifier).toHaveBeenCalledWith(
      expect.objectContaining({
        methode: 'GET',
        chemin: `/v1/game/acces/${UUID}`,
        corps: Buffer.alloc(0),
      }),
      { nom: 'defaut', parMinute: 120 },
    );
  });

  it('401, 429 (avec Retry-After) et 503', async () => {
    const cas: Array<[object, new (...a: never[]) => HttpException]> = [
      [{ ok: false, statut: 401, message: 'x' }, UnauthorizedException],
      [{ ok: false, statut: 503, message: 'x' }, ServiceUnavailableException],
    ];
    for (const [reponse, classe] of cas) {
      const garde = new GameSignatureGuard(
        { verifier: jest.fn().mockResolvedValue(reponse) } as never,
        new Reflector(),
      );
      await expect(garde.canActivate(contexte().ctx)).rejects.toBeInstanceOf(
        classe,
      );
    }
    const garde = new GameSignatureGuard(
      {
        verifier: jest.fn().mockResolvedValue({
          ok: false,
          statut: 429,
          message: 'x',
          reessayerDansS: 7,
        }),
      } as never,
      new Reflector(),
    );
    const { ctx, res } = contexte();
    await expect(garde.canActivate(ctx)).rejects.toMatchObject({
      status: 429,
    });
    expect(res.setHeader).toHaveBeenCalledWith('Retry-After', '7');
  });
});
