import {
  type ExecutionContext,
  GoneException,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { createHmac } from 'node:crypto';
import { GameHmacGuard, routeHistoriqueOuverte } from './game-hmac.guard';

// Route historique GET /v1/game/candidature/:mcUuid : ouverte tant que
// ShinobiCore ne lit pas GET /v1/game/acces, coupée (410) ensuite par
// REBORN_GAME_CANDIDATURE_HISTORIQUE=0, sans nouveau code.

const SECRET_BOT = 'secret-du-bot-pour-les-tests-32-caracteres';
const UUID = '069a79f4-44e9-4726-a5be-fca90e38aaf5';

function garde(valeur?: string): GameHmacGuard {
  const config = {
    get: (k: string) =>
      k === 'REBORN_GAME_CANDIDATURE_HISTORIQUE' ? valeur : undefined,
  };
  return new GameHmacGuard(config as never);
}

function contexte(signature?: string): ExecutionContext {
  const req = {
    params: { mcUuid: UUID },
    headers: signature ? { 'x-reborn-signature': signature } : {},
  };
  return {
    switchToHttp: () => ({ getRequest: () => req }),
  } as unknown as ExecutionContext;
}

describe('GameHmacGuard (route historique)', () => {
  const avant = process.env.REBORN_WEBHOOK_SECRET;
  let avertir: jest.SpyInstance;

  beforeEach(() => {
    process.env.REBORN_WEBHOOK_SECRET = SECRET_BOT;
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);
    avertir = jest
      .spyOn(Logger.prototype, 'warn')
      .mockImplementation(() => undefined);
  });

  afterEach(() => {
    // process.env convertit undefined en « undefined » : on efface plutôt.
    if (avant === undefined) delete process.env.REBORN_WEBHOOK_SECRET;
    else process.env.REBORN_WEBHOOK_SECRET = avant;
    jest.restoreAllMocks();
  });

  const signee = () =>
    createHmac('sha256', SECRET_BOT).update(UUID).digest('hex');

  it('variable absente : route ouverte (ShinobiCore actuel), signalée au démarrage', () => {
    const g = garde();
    expect(g.canActivate(contexte(signee()))).toBe(true);
    expect(avertir).toHaveBeenCalledWith(
      expect.stringContaining('REBORN_GAME_CANDIDATURE_HISTORIQUE=0'),
    );
  });

  it('route ouverte : signature fausse ou absente refusée (401)', () => {
    const g = garde();
    expect(() => g.canActivate(contexte('ab'.repeat(32)))).toThrow(
      UnauthorizedException,
    );
    expect(() => g.canActivate(contexte())).toThrow(UnauthorizedException);
  });

  it('REBORN_GAME_CANDIDATURE_HISTORIQUE=0 : 410, même avec une signature valide', () => {
    const g = garde('0');
    expect(() => g.canActivate(contexte(signee()))).toThrow(GoneException);
    expect(() => g.canActivate(contexte())).toThrow(GoneException);
  });

  it.each([
    ['0', false],
    ['false', false],
    ['Non', false],
    [' off ', false],
    ['no', false],
    [undefined, true],
    ['', true],
    ['1', true],
    ['oui', true],
  ])('routeHistoriqueOuverte(%p) = %p', (valeur, ouverte) => {
    expect(routeHistoriqueOuverte(valeur)).toBe(ouverte);
  });
});
