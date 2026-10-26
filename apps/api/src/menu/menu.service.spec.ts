import { Logger } from '@nestjs/common';
import type { PrismaService } from '../prisma/prisma.service';
import { MenuService } from './menu.service';

// Compteur Discord du panneau /v1/menu/panel : invitation publique, cache de
// 5 min, repli sur la dernière valeur connue, « indisponible » sinon.
// fetch et l'horloge sont simulés ; aucun appel réseau réel.

const T0 = 1_800_000_000_000;
const MIN = 60_000;

const prisma = {
  patchNote: { findMany: jest.fn().mockResolvedValue([]) },
} as unknown as PrismaService;

const invite = (members: number, online: number) =>
  new Response(
    JSON.stringify({
      code: 'Nw7jpjJ6dg',
      guild: { id: '1', name: 'Reborn Roleplay' },
      approximate_member_count: members,
      approximate_presence_count: online,
    }),
    { status: 200, headers: { 'content-type': 'application/json' } },
  );

const httpError = (status: number, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify({ message: 'Unknown Invite', code: 10006 }), {
    status,
    headers,
  });

/**
 * Écarte le cache du panneau (60 s) sans toucher à l'état du compteur
 * Discord : ce cache couvre exactement la minute d'attente après un échec,
 * getPanel() seul ne repasserait donc jamais par le compteur pendant cette
 * minute.
 */
const expirePanelCache = (service: MenuService) => {
  (service as unknown as { cache: unknown }).cache = null;
};

describe('MenuService (compteur Discord)', () => {
  const saved = {
    code: process.env.DISCORD_INVITE_CODE,
    streamers: process.env.REBORN_STREAMERS,
  };
  let now = T0;
  let fetchMock: jest.SpyInstance;
  let warn: jest.SpyInstance;

  beforeEach(() => {
    now = T0;
    jest.spyOn(Date, 'now').mockImplementation(() => now);
    // Par défaut, tout appel non prévu échoue : jamais de vrai réseau.
    fetchMock = jest
      .spyOn(globalThis, 'fetch')
      .mockRejectedValue(new Error('appel réseau non prévu par le test'));
    warn = jest.spyOn(Logger.prototype, 'warn').mockImplementation(() => {});
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => {});
    process.env.DISCORD_INVITE_CODE = 'Nw7jpjJ6dg';
    delete process.env.REBORN_STREAMERS;
  });

  afterEach(() => {
    jest.restoreAllMocks();
    process.env.DISCORD_INVITE_CODE = saved.code;
    if (saved.code === undefined) delete process.env.DISCORD_INVITE_CODE;
    process.env.REBORN_STREAMERS = saved.streamers;
    if (saved.streamers === undefined) delete process.env.REBORN_STREAMERS;
  });

  it('lit l’invitation publique sans jeton et renvoie compteurs et lien', async () => {
    fetchMock.mockResolvedValueOnce(invite(1238, 214));
    const panel = await new MenuService(prisma).getPanel();

    expect(panel.discord).toEqual({
      members: 1238,
      online: 214,
      inviteUrl: 'https://discord.gg/Nw7jpjJ6dg',
      name: 'Reborn Roleplay',
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(
      'https://discord.com/api/v10/invites/Nw7jpjJ6dg?with_counts=true',
    );
    expect(init.headers).not.toHaveProperty('Authorization');
  });

  it('sans DISCORD_INVITE_CODE : compteur indisponible, aucun appel, un seul avertissement', async () => {
    delete process.env.DISCORD_INVITE_CODE;
    const service = new MenuService(prisma);

    expect((await service.getPanel()).discord).toBeNull();
    now += 2 * MIN; // au-delà du cache du panneau (60 s)
    expect((await service.getPanel()).discord).toBeNull();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(warn).toHaveBeenCalledTimes(1);
  });

  it('garde le compteur 5 min avant de rappeler Discord', async () => {
    fetchMock
      .mockResolvedValueOnce(invite(100, 10))
      .mockResolvedValueOnce(invite(101, 12));
    const service = new MenuService(prisma);

    expect((await service.getPanel()).discord?.members).toBe(100);
    now += 2 * MIN;
    expect((await service.getPanel()).discord?.members).toBe(100);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    now += 4 * MIN; // 6 min après le premier appel
    expect((await service.getPanel()).discord?.members).toBe(101);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('Discord muet : 1 min entre deux essais, dernière valeur connue pendant 30 min, puis indisponible', async () => {
    fetchMock
      .mockResolvedValueOnce(invite(100, 10))
      .mockResolvedValueOnce(httpError(503))
      .mockRejectedValueOnce(new TypeError('fetch failed'))
      .mockRejectedValueOnce(new TypeError('fetch failed'));
    const service = new MenuService(prisma);

    await service.getPanel();
    now += 6 * MIN; // compteur périmé : nouvel appel, Discord répond 503
    expect((await service.getPanel()).discord?.members).toBe(100);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    // Pas de nouvel appel pendant la minute qui suit l'échec, même avec le
    // cache du panneau écarté (c'est discordRetryAt qui retient l'appel).
    now += 0.5 * MIN;
    expirePanelCache(service);
    expect((await service.getPanel()).discord?.members).toBe(100);
    expect(fetchMock).toHaveBeenCalledTimes(2);

    // La minute écoulée : nouvel essai (échec réseau), dernière valeur servie.
    now += 0.6 * MIN;
    expirePanelCache(service);
    expect((await service.getPanel()).discord?.members).toBe(100);
    expect(fetchMock).toHaveBeenCalledTimes(3);

    now = T0 + 31 * MIN;
    expect((await service.getPanel()).discord).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('invitation expirée (404) : indisponible, nouvel essai dans 10 min seulement', async () => {
    fetchMock
      .mockResolvedValueOnce(httpError(404))
      .mockResolvedValueOnce(invite(5, 1));
    const service = new MenuService(prisma);

    expect((await service.getPanel()).discord).toBeNull();
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('expirée'));

    now += 5 * MIN;
    expect((await service.getPanel()).discord).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    now += 6 * MIN;
    expect((await service.getPanel()).discord?.members).toBe(5);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('respecte le Retry-After de Discord (429)', async () => {
    fetchMock
      .mockResolvedValueOnce(httpError(429, { 'retry-after': '300' }))
      .mockResolvedValueOnce(invite(7, 2));
    const service = new MenuService(prisma);

    expect((await service.getPanel()).discord).toBeNull();
    now += 2 * MIN;
    expect((await service.getPanel()).discord).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    now += 4 * MIN;
    expect((await service.getPanel()).discord?.online).toBe(2);
  });
});
