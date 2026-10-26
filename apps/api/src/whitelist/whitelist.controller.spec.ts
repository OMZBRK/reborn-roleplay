import { ExecutionContext, INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { json } from 'express';
import { Readable } from 'node:stream';
import request from 'supertest';
import type { App } from 'supertest/types';
import { AssignmentService } from '../admin/assignment.service';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { RebornValidationPipe } from '../common/pipes/validation.pipe';
import { OralSlotsService } from '../oral-slots/oral-slots.service';
import { WhitelistController } from './whitelist.controller';
import { WhitelistMessagesService } from './whitelist-messages.service';
import { WhitelistVoixService } from './whitelist-voix.service';
import { WhitelistService } from './whitelist.service';

// Bout en bout HTTP du contrôleur, monté comme main.ts (corps JSON global,
// ValidationPipe whitelist + forbidNonWhitelisted) : messages de validation
// rattachés au champ, corps brut de la voix, plafond 413, alias de routes.
// Le service est simulé.

const USER_ID = '11111111-1111-4111-8111-111111111111';

describe('WhitelistController (HTTP)', () => {
  let app: INestApplication<App>;
  const service = {
    getMine: jest.fn(() =>
      Promise.resolve({
        application: null,
        suivi: { phase: 'BROUILLON' },
      }),
    ),
    options: jest.fn(() => ({ formVersion: 2 })),
    submit: jest.fn(() => Promise.resolve({ id: 'a1' })),
    resubmit: jest.fn(() => Promise.resolve({ id: 'a1' })),
    withdraw: jest.fn(() => Promise.resolve(undefined)),
    getBrouillon: jest.fn(() => Promise.resolve({ brouillon: null })),
    putBrouillon: jest.fn(() => Promise.resolve({ brouillon: {} })),
    deleteBrouillon: jest.fn(() => Promise.resolve(undefined)),
    deposerVoix: jest.fn((_u: string, _t: string, _d: string, corps: Buffer) =>
      Promise.resolve({
        voix: { octets: corps.length },
      }),
    ),
  };
  const audioCandidat = Buffer.concat([
    Buffer.from('OggS', 'latin1'),
    Buffer.alloc(1500, 3),
  ]);
  const voix = {
    ouvrirPourCandidat: jest.fn(() =>
      Promise.resolve({
        flux: Readable.from([audioCandidat]),
        type: 'audio/ogg',
        taille: audioCandidat.length,
      }),
    ),
  };
  const saved = process.env.WHITELIST_VOICE_MAX_BYTES;

  beforeAll(async () => {
    process.env.WHITELIST_VOICE_MAX_BYTES = '4096';
    const mod = await Test.createTestingModule({
      controllers: [WhitelistController],
      providers: [
        { provide: WhitelistService, useValue: service },
        { provide: WhitelistMessagesService, useValue: {} },
        { provide: AssignmentService, useValue: {} },
        { provide: OralSlotsService, useValue: {} },
        { provide: WhitelistVoixService, useValue: voix },
      ],
    })
      .overrideGuard(JwtAuthGuard)
      .useValue({
        canActivate: (ctx: ExecutionContext) => {
          ctx.switchToHttp().getRequest<{ user: unknown }>().user = {
            sub: USER_ID,
          };
          return true;
        },
      })
      .compile();
    app = mod.createNestApplication({ bodyParser: false });
    app.use(json({ limit: '1mb' }));
    app.setGlobalPrefix('v1');
    app.useGlobalPipes(new RebornValidationPipe());
    await app.init();
  });

  afterAll(async () => {
    await app.close();
    if (saved === undefined) delete process.env.WHITELIST_VOICE_MAX_BYTES;
    else process.env.WHITELIST_VOICE_MAX_BYTES = saved;
  });

  beforeEach(() => jest.clearAllMocks());

  it('POST /v1/whitelist v2 invalide : 400, un message par champ, préfixé par son nom', async () => {
    const res = await request(app.getHttpServer())
      .post('/v1/whitelist')
      .send({ formVersion: 2, playerAge: 14 })
      .expect(400);
    const messages = (res.body as { message: string[] }).message;
    expect(messages).toContain(
      'playerAge: Reborn accueille les joueurs à partir de 15 ans.',
    );
    expect(messages.every((m) => /^[a-zA-Z]+: /.test(m))).toBe(true);
    expect(service.submit).not.toHaveBeenCalled();
  });

  it('POST /v1/whitelist de l’ancien launcher : accepté', async () => {
    await request(app.getHttpServer())
      .post('/v1/whitelist')
      .send({
        dob: '2006-04-12',
        motivation: 'm'.repeat(200),
        experience: 'e'.repeat(200),
        availability: 'Le soir après 20 h, le samedi.',
        firstName: 'Kaito',
        lastName: 'Arashi',
        village: 'Konohagakure',
        support: '',
        history: 'h'.repeat(300),
        appearance: 'a'.repeat(200),
        objectives: 'o'.repeat(450),
      })
      .expect(201);
    expect(service.submit).toHaveBeenCalledTimes(1);
  });

  it('champ inconnu : message français rattaché au champ, sans détail interne', async () => {
    const res = await request(app.getHttpServer())
      .post('/v1/whitelist')
      .send({ formVersion: 2, nomFamille: 'Arashi' })
      .expect(400);
    const messages = (res.body as { message: string[] }).message;
    expect(messages).toContain(
      'nomFamille: champ inconnu de la candidature (mets à jour le launcher).',
    );
    expect(messages.join(' ')).not.toContain('should not exist');
  });

  it('PATCH /v1/whitelist/me : le contrôle par partie est laissé au service', async () => {
    await request(app.getHttpServer())
      .patch('/v1/whitelist/me')
      .send({ formVersion: 2, firstName: 'Kaito', rulesAccepted: true })
      .expect(200);
    expect(service.resubmit).toHaveBeenCalledWith(USER_ID, {
      formVersion: 2,
      firstName: 'Kaito',
      rulesAccepted: true,
    });
    await request(app.getHttpServer())
      .patch('/v1/whitelist/me')
      .send({ formVersion: 2, micTeste: true })
      .expect(400);
  });

  it('GET /v1/whitelist/me/voix (et /me/voice) : sa propre voix, privée, jamais en cache', async () => {
    const res = await request(app.getHttpServer())
      .get('/v1/whitelist/me/voix')
      .buffer(true)
      .parse((r, fin) => {
        const morceaux: Buffer[] = [];
        r.on('data', (c: Buffer) => morceaux.push(c));
        r.on('end', () => fin(null, Buffer.concat(morceaux)));
      })
      .expect(200);
    expect(res.headers['content-type']).toBe('audio/ogg');
    expect(res.headers['cache-control']).toBe('private, no-store');
    expect(res.headers['x-content-type-options']).toBe('nosniff');
    expect(Buffer.compare(res.body as Buffer, audioCandidat)).toBe(0);
    expect(voix.ouvrirPourCandidat).toHaveBeenCalledWith(USER_ID, undefined);
    await request(app.getHttpServer())
      .get('/v1/whitelist/me/voice')
      .expect(200);
  });

  it('GET /v1/whitelist/me/voix?source= : candidature (voix envoyée) ou brouillon ; autre valeur refusée', async () => {
    await request(app.getHttpServer())
      .get('/v1/whitelist/me/voix?source=candidature')
      .expect(200);
    expect(voix.ouvrirPourCandidat).toHaveBeenLastCalledWith(
      USER_ID,
      'candidature',
    );
    await request(app.getHttpServer())
      .get('/v1/whitelist/me/voix?source=brouillon')
      .expect(200);
    expect(voix.ouvrirPourCandidat).toHaveBeenLastCalledWith(
      USER_ID,
      'brouillon',
    );
    const appels = voix.ouvrirPourCandidat.mock.calls.length;
    await request(app.getHttpServer())
      .get('/v1/whitelist/me/voix?source=../autre')
      .expect(400);
    expect(voix.ouvrirPourCandidat.mock.calls.length).toBe(appels);
  });

  it('POST /v1/whitelist/me/voix : corps brut transmis tel quel, avec type et durée', async () => {
    const audio = Buffer.concat([
      Buffer.from([0x1a, 0x45, 0xdf, 0xa3]),
      Buffer.alloc(2000, 7),
    ]);
    const res = await request(app.getHttpServer())
      .post('/v1/whitelist/me/voix?dureeSec=42')
      .set('Content-Type', 'audio/webm;codecs=opus')
      .send(audio)
      .expect(201);
    expect(res.body).toEqual({ voix: { octets: audio.length } });
    const [userId, type, duree, corps] = service.deposerVoix.mock.calls[0];
    expect([userId, type, duree]).toEqual([
      USER_ID,
      'audio/webm;codecs=opus',
      '42',
    ]);
    expect(Buffer.compare(corps, audio)).toBe(0);
  });

  it('POST /v1/whitelist/me/voice (alias) au-delà du plafond : 413', async () => {
    await request(app.getHttpServer())
      .post('/v1/whitelist/me/voice?dureeSec=42')
      .set('Content-Type', 'audio/ogg')
      .send(Buffer.alloc(5000))
      .expect(413);
    expect(service.deposerVoix).not.toHaveBeenCalled();
  });

  it('brouillon : GET /me/brouillon et l’alias /me/draft ; PUT exige un objet data', async () => {
    await request(app.getHttpServer())
      .get('/v1/whitelist/me/brouillon')
      .expect(200, { brouillon: null });
    await request(app.getHttpServer())
      .get('/v1/whitelist/me/draft')
      .expect(200);
    await request(app.getHttpServer())
      .put('/v1/whitelist/me/brouillon')
      .send({ data: 'x' })
      .expect(400);
    await request(app.getHttpServer())
      .put('/v1/whitelist/me/brouillon')
      .send({
        formVersion: 2,
        data: { history: 'Il était une fois', reglement: false },
      })
      .expect(200);
    expect(service.putBrouillon).toHaveBeenCalledWith(USER_ID, {
      formVersion: 2,
      data: { history: 'Il était une fois', reglement: false },
    });
  });

  it('GET /v1/whitelist/options', async () => {
    await request(app.getHttpServer())
      .get('/v1/whitelist/options')
      .expect(200, { formVersion: 2 });
  });
});
