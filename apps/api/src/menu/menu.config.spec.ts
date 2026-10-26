import {
  DISCORD_RETRY_MAX_MS,
  DISCORD_RETRY_MS,
  DISCORD_RETRY_UNKNOWN_INVITE_MS,
  discordInviteApiUrl,
  discordInviteCode,
  discordInviteUrl,
  discordRetryDelayMs,
  parseDiscordInvite,
} from './menu.config';

const env = (v?: string) => ({ DISCORD_INVITE_CODE: v }) as NodeJS.ProcessEnv;

describe('menu.config (compteur Discord)', () => {
  it('lit le code d’invitation, ou un lien complet collé à sa place', () => {
    expect(discordInviteCode(env('Nw7jpjJ6dg'))).toBe('Nw7jpjJ6dg');
    expect(discordInviteCode(env('  Nw7jpjJ6dg  '))).toBe('Nw7jpjJ6dg');
    expect(discordInviteCode(env('https://discord.gg/Nw7jpjJ6dg'))).toBe(
      'Nw7jpjJ6dg',
    );
    expect(discordInviteCode(env('discord.com/invite/Nw7jpjJ6dg/'))).toBe(
      'Nw7jpjJ6dg',
    );
  });

  it('refuse une variable absente ou mal formée', () => {
    expect(discordInviteCode(env(undefined))).toBeNull();
    expect(discordInviteCode(env('   '))).toBeNull();
    expect(discordInviteCode(env('../guilds/1'))).toBeNull();
    expect(discordInviteCode(env('abc?x=1'))).toBeNull();
    expect(
      discordInviteCode(env('https://exemple.test/Nw7jpjJ6dg')),
    ).toBeNull();
  });

  it('construit le lien public et l’endpoint Discord', () => {
    expect(discordInviteUrl('Nw7jpjJ6dg')).toBe(
      'https://discord.gg/Nw7jpjJ6dg',
    );
    expect(discordInviteApiUrl('Nw7jpjJ6dg')).toBe(
      'https://discord.com/api/v10/invites/Nw7jpjJ6dg?with_counts=true',
    );
  });

  it('extrait membres, présence et nom ; refuse une réponse sans compteurs', () => {
    expect(
      parseDiscordInvite({
        code: 'Nw7jpjJ6dg',
        guild: { id: '1', name: ' Reborn Roleplay ' },
        approximate_member_count: 1238,
        approximate_presence_count: 214,
      }),
    ).toEqual({ members: 1238, online: 214, name: 'Reborn Roleplay' });
    expect(
      parseDiscordInvite({
        approximate_member_count: 3,
        approximate_presence_count: 0,
      }),
    ).toEqual({ members: 3, online: 0, name: null });
    expect(parseDiscordInvite({ code: 'x', guild: { name: 'R' } })).toBeNull();
    expect(
      parseDiscordInvite({
        approximate_member_count: -1,
        approximate_presence_count: 2,
      }),
    ).toBeNull();
    expect(
      parseDiscordInvite({
        approximate_member_count: '12',
        approximate_presence_count: 2,
      }),
    ).toBeNull();
    expect(parseDiscordInvite(null)).toBeNull();
    expect(parseDiscordInvite('oups')).toBeNull();
  });

  it('espace les appels après un échec selon la cause', () => {
    expect(discordRetryDelayMs(null)).toBe(DISCORD_RETRY_MS);
    expect(discordRetryDelayMs(503)).toBe(DISCORD_RETRY_MS);
    expect(discordRetryDelayMs(404)).toBe(DISCORD_RETRY_UNKNOWN_INVITE_MS);
    expect(discordRetryDelayMs(429, '120')).toBe(120_000);
    expect(discordRetryDelayMs(429, '0.5')).toBe(DISCORD_RETRY_MS);
    expect(discordRetryDelayMs(429, '99999')).toBe(DISCORD_RETRY_MAX_MS);
    expect(discordRetryDelayMs(429, null)).toBe(DISCORD_RETRY_MS);
    expect(discordRetryDelayMs(429, 'demain')).toBe(DISCORD_RETRY_MS);
  });
});
