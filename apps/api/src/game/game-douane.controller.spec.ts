import { type INestApplication, Logger } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { json } from 'express';
import { createHmac, randomUUID } from 'node:crypto';
import request from 'supertest';
import type { App } from 'supertest/types';
import { RebornValidationPipe } from '../common/pipes/validation.pipe';
import { GameDouaneController } from './game-douane.controller';
import { GameDouaneService } from './game-douane.service';
import { GameSignatureGuard } from './game-signature.guard';
import { GameSignatureService } from './game-signature.service';

// Contrat bout à bout, par HTTP, comme le plugin l'appellera : préfixe /v1,
// corps brut capturé comme dans main.ts, pipe de validation global.

const SECRET = 'secret-du-serveur-dev-32-caracteres-min!';
const UUID = '069a79f4-44e9-4726-a5be-fca90e38aaf5';

/** En-têtes calculés à partir du contrat écrit, comme côté Java. */
function entetes(methode: string, chemin: string, corps = '') {
  const ts = String(Math.floor(Date.now() / 1000));
  const nonce = randomUUID();
  const chaine = `REBORN-GAME-V1\n${methode}\n${chemin}\ndev\n${ts}\n${nonce}\n${corps}`;
  return {
    'X-Reborn-Server': 'dev',
    'X-Reborn-Timestamp': ts,
    'X-Reborn-Nonce': nonce,
    'X-Reborn-Signature': createHmac('sha256', SECRET)
      .update(chaine, 'utf8')
      .digest('hex'),
  };
}

describe('GameDouaneController (HTTP)', () => {
  let app: INestApplication<App>;
  const metier = {
    acces: jest.fn().mockResolvedValue({ portee: 'play' }),
    dossier: jest.fn().mockResolvedValue({ applicationId: 'app1' }),
    decision: jest.fn().mockResolvedValue({ ok: true }),
    presence: jest.fn().mockResolvedValue({ ok: true }),
  };

  beforeAll(async () => {
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
    jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => undefined);
    const prisma = {
      gameRequestNonce: { create: jest.fn().mockResolvedValue({}) },
    };
    const config = {
      get: (k: string) =>
        k === 'REBORN_GAME_SECRETS' ? `dev:${SECRET}` : undefined,
    };
    const moduleRef = await Test.createTestingModule({
      controllers: [GameDouaneController],
      providers: [
        GameSignatureGuard,
        {
          provide: GameSignatureService,
          useValue: new GameSignatureService(config as never, prisma as never),
        },
        { provide: GameDouaneService, useValue: metier },
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

  it('GET /v1/game/acces/:mcUuid signé : 200, serveur transmis', async () => {
    const chemin = `/v1/game/acces/${UUID}`;
    await request(app.getHttpServer())
      .get(chemin)
      .set(entetes('GET', chemin))
      .expect(200, { portee: 'play' });
    expect(metier.acces).toHaveBeenCalledWith(UUID, 'dev');
  });

  it('GET dossier avec ?examinateur= : la requête est signée et lue', async () => {
    const chemin = `/v1/game/douane/dossier/${UUID}?examinateur=${UUID}`;
    await request(app.getHttpServer())
      .get(chemin)
      .set(entetes('GET', chemin))
      .expect(200);
    expect(metier.dossier).toHaveBeenCalledWith(UUID, UUID, 'dev');
  });

  it('POST décision : corps brut signé (accents compris), 200', async () => {
    const corps = JSON.stringify({
      mcUuidCandidat: UUID,
      mcUuidExaminateur: '22222222-2222-4222-8222-222222222222',
      reservationId: '33333333-3333-4333-8333-333333333333',
      resultat: 'NEEDS_REVISION',
      motif: 'Réponses hésitantes sur le règlement.',
    });
    await request(app.getHttpServer())
      .post('/v1/game/douane/decision')
      .set(entetes('POST', '/v1/game/douane/decision', corps))
      .set('Content-Type', 'application/json; charset=utf-8')
      .send(corps)
      .expect(200, { ok: true });
  });

  it('POST présence « retrait » avec motif (corps de ShinobiCore) : accepté, transmis au métier', async () => {
    // Même forme que DouaneClient.presence côté plugin (champs et ordre).
    const corps = JSON.stringify({
      mcUuid: UUID,
      evenement: 'retrait',
      reservationId: '33333333-3333-4333-8333-333333333333',
      mcUuidExaminateur: '22222222-2222-4222-8222-222222222222',
      motif: 'Trouble le hall malgré deux rappels.',
    });
    await request(app.getHttpServer())
      .post('/v1/game/douane/presence')
      .set(entetes('POST', '/v1/game/douane/presence', corps))
      .set('Content-Type', 'application/json; charset=utf-8')
      .send(corps)
      .expect(200);
    expect(metier.presence).toHaveBeenLastCalledWith(
      expect.objectContaining({
        evenement: 'retrait',
        motif: 'Trouble le hall malgré deux rappels.',
      }),
      'dev',
    );
    const report = JSON.stringify({ mcUuid: UUID, evenement: 'report' });
    await request(app.getHttpServer())
      .post('/v1/game/douane/presence')
      .set(entetes('POST', '/v1/game/douane/presence', report))
      .set('Content-Type', 'application/json; charset=utf-8')
      .send(report)
      .expect(200);
  });

  it('sans signature : 401, le métier n’est pas appelé', async () => {
    metier.acces.mockClear();
    await request(app.getHttpServer())
      .get(`/v1/game/acces/${UUID}`)
      .expect(401);
    expect(metier.acces).not.toHaveBeenCalled();
  });

  it('champ inconnu dans la décision : 400 (après la signature)', async () => {
    const corps = JSON.stringify({
      mcUuidCandidat: UUID,
      mcUuidExaminateur: UUID,
      reservationId: '33333333-3333-4333-8333-333333333333',
      resultat: 'APPROVED',
      ts: 1,
    });
    await request(app.getHttpServer())
      .post('/v1/game/douane/decision')
      .set(entetes('POST', '/v1/game/douane/decision', corps))
      .set('Content-Type', 'application/json')
      .send(corps)
      .expect(400);
  });

  it('UUID de route invalide : 400', async () => {
    const chemin = '/v1/game/acces/pas-un-uuid';
    await request(app.getHttpServer())
      .get(chemin)
      .set(entetes('GET', chemin))
      .expect(400);
  });
});
