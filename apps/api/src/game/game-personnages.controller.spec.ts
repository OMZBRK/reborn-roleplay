import { type INestApplication, Logger } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { json } from 'express';
import { createHmac, randomUUID } from 'node:crypto';
import request from 'supertest';
import type { App } from 'supertest/types';
import { RebornValidationPipe } from '../common/pipes/validation.pipe';
import { GamePersonnagesController } from './game-personnages.controller';
import { GamePersonnagesService } from './game-personnages.service';
import { GameSignatureGuard } from './game-signature.guard';
import { GameSignatureService } from './game-signature.service';

// Contrat bout à bout, par HTTP, comme ShinobiCore l'appellera : préfixe /v1,
// corps brut capturé comme dans main.ts, pipe de validation global, même garde
// signé que la douane.

const SECRET = 'secret-du-serveur-prod-32-caracteres-min';
const CHEMIN = '/v1/game/characters/sync';

/** En-têtes calculés à partir du contrat écrit (game-signature.ts), comme côté Java. */
function entetes(corps: string, serveur = 'prod', secret = SECRET) {
  const ts = String(Math.floor(Date.now() / 1000));
  const nonce = randomUUID();
  const chaine = `REBORN-GAME-V1\nPOST\n${CHEMIN}\n${serveur}\n${ts}\n${nonce}\n${corps}`;
  return {
    'X-Reborn-Server': serveur,
    'X-Reborn-Timestamp': ts,
    'X-Reborn-Nonce': nonce,
    'X-Reborn-Signature': createHmac('sha256', secret)
      .update(chaine, 'utf8')
      .digest('hex'),
    'Content-Type': 'application/json; charset=utf-8',
  };
}

const PHOTO = {
  characterId: 'aaaaaaaa-0000-4000-8000-000000000001',
  ownerMcUuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5',
  playerId: 42,
  firstName: 'Kaïto',
  lastName: 'Arashi',
  village: 'Konohagakure',
  rank: 'GENIN',
  sex: 'HOMME',
  age: 16,
  dead: false,
  playMinutes: 10,
  snapshotAt: '2026-10-25T19:00:00Z',
};

describe('GamePersonnagesController (HTTP)', () => {
  let app: INestApplication<App>;
  const metier = {
    synchroniser: jest.fn((dto: { characters: unknown[] }, serveur: string) =>
      Promise.resolve({
        ok: true,
        serveur,
        resultats: dto.characters.map((_, index) => ({
          index,
          characterId: null,
          statut: 'cree',
        })),
      }),
    ),
  };

  beforeAll(async () => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
    const prisma = {
      gameRequestNonce: { create: jest.fn().mockResolvedValue({}) },
    };
    const config = {
      get: (k: string) =>
        k === 'REBORN_GAME_SECRETS' ? `prod:${SECRET}` : undefined,
    };
    const moduleRef = await Test.createTestingModule({
      controllers: [GamePersonnagesController],
      providers: [
        GameSignatureGuard,
        {
          provide: GameSignatureService,
          useValue: new GameSignatureService(config as never, prisma as never),
        },
        { provide: GamePersonnagesService, useValue: metier },
      ],
    }).compile();
    app = moduleRef.createNestApplication<INestApplication<App>>({
      bodyParser: false,
    });
    app.use(
      json({
        verify: (req, _res, buf) => {
          (req as { rawBody?: Buffer }).rawBody = Buffer.from(buf);
        },
      }),
    );
    app.setGlobalPrefix('v1');
    app.useGlobalPipes(new RebornValidationPipe());
    await app.init();
  });

  afterAll(async () => {
    await app.close();
    jest.restoreAllMocks();
  });

  beforeEach(() => metier.synchroniser.mockClear());

  it('envoi signé (accents compris) : 200, photos intactes, serveur de l’en-tête', async () => {
    const corps = JSON.stringify({ characters: [PHOTO] });
    const res = await request(app.getHttpServer())
      .post(CHEMIN)
      .set(entetes(corps))
      .send(corps)
      .expect(200);
    expect(res.body).toEqual({
      ok: true,
      serveur: 'prod',
      resultats: [{ index: 0, characterId: null, statut: 'cree' }],
    });
    // Le pipe global ne retire rien des photos : le service les contrôle une à une.
    expect(metier.synchroniser).toHaveBeenCalledWith(
      { characters: [PHOTO] },
      'prod',
    );
  });

  it('sans signature : 401, le métier n’est pas appelé', async () => {
    const corps = JSON.stringify({ characters: [PHOTO] });
    await request(app.getHttpServer())
      .post(CHEMIN)
      .set('Content-Type', 'application/json')
      .send(corps)
      .expect(401);
    expect(metier.synchroniser).not.toHaveBeenCalled();
  });

  it('serveur inconnu de l’API (dev sans secret) : 401', async () => {
    const corps = JSON.stringify({ characters: [PHOTO] });
    await request(app.getHttpServer())
      .post(CHEMIN)
      .set(entetes(corps, 'dev', SECRET))
      .send(corps)
      .expect(401);
    expect(metier.synchroniser).not.toHaveBeenCalled();
  });

  it('serveur déclaré dans le corps : 400 (le serveur vient de l’en-tête seulement)', async () => {
    const corps = JSON.stringify({ serverId: 'dev', characters: [PHOTO] });
    await request(app.getHttpServer())
      .post(CHEMIN)
      .set(entetes(corps))
      .send(corps)
      .expect(400);
    expect(metier.synchroniser).not.toHaveBeenCalled();
  });

  it('enveloppe invalide (liste vide, pas une liste, entrée non objet) : 400', async () => {
    for (const enveloppe of [
      { characters: [] },
      { characters: PHOTO },
      { characters: ['texte'] },
      {},
    ]) {
      const corps = JSON.stringify(enveloppe);
      await request(app.getHttpServer())
        .post(CHEMIN)
        .set(entetes(corps))
        .send(corps)
        .expect(400);
    }
    expect(metier.synchroniser).not.toHaveBeenCalled();
  });

  it('plus de 200 photos : 400', async () => {
    const corps = JSON.stringify({
      characters: Array.from({ length: 201 }, () => PHOTO),
    });
    await request(app.getHttpServer())
      .post(CHEMIN)
      .set(entetes(corps))
      .send(corps)
      .expect(400);
  });
});
