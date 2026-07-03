import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

export interface MenuDiscord {
  members: number;
  online: number;
}

export interface MenuPatchNote {
  id: string;
  version: string;
  title: string;
  publishedAt: string;
  /** Lien web vers la patch note (ouvert au clic depuis le menu ÉCHAP). */
  url: string;
}

export interface MenuStream {
  name: string;
  url: string;
  live: boolean;
  title: string | null;
}

export interface MenuPanelDto {
  discord: MenuDiscord | null;
  patchNotes: MenuPatchNote[];
  streams: MenuStream[];
}

// Le menu ESC in-game (mod Fabric) poll ce panneau à l'ouverture. On cache
// 60s : Discord rate-limit l'endpoint guild, les patch notes bougent peu, et
// N joueurs ouvrant ESC ne doivent pas marteler l'API.
const CACHE_TTL_MS = 60_000;
const HTTP_TIMEOUT_MS = 4000;

interface Cached {
  data: MenuPanelDto;
  expiresAt: number;
}

@Injectable()
export class MenuService {
  private readonly logger = new Logger(MenuService.name);
  private cache: Cached | null = null;
  private inflight: Promise<MenuPanelDto> | null = null;
  // Token d'app Twitch (client_credentials) mis en cache jusqu'à ~1min avant
  // expiration ; évite un aller-retour OAuth à chaque refresh du panneau.
  private twitchToken: { value: string; expiresAt: number } | null = null;

  async getPanel(): Promise<MenuPanelDto> {
    const now = Date.now();
    if (this.cache && this.cache.expiresAt > now) {
      return this.cache.data;
    }
    if (this.inflight) {
      return this.inflight;
    }
    this.inflight = this.fetchFresh()
      .then((data) => {
        this.cache = { data, expiresAt: Date.now() + CACHE_TTL_MS };
        return data;
      })
      .finally(() => {
        this.inflight = null;
      });
    return this.inflight;
  }

  constructor(private readonly prisma: PrismaService) {}

  private async fetchFresh(): Promise<MenuPanelDto> {
    const [discord, patchNotes, streams] = await Promise.all([
      this.fetchDiscord(),
      this.fetchPatchNotes(),
      this.fetchStreams(),
    ]);
    return { discord, patchNotes, streams };
  }

  // ── Discord : compteur membres + présence via le bot token ──
  private async fetchDiscord(): Promise<MenuDiscord | null> {
    const guildId = process.env.DISCORD_GUILD_ID?.trim();
    const botToken = process.env.DISCORD_BOT_TOKEN?.trim();
    if (!guildId || !botToken) return null;
    try {
      const res = await this.fetchWithTimeout(
        `https://discord.com/api/v10/guilds/${guildId}?with_counts=true`,
        { headers: { Authorization: `Bot ${botToken}` } },
      );
      if (!res.ok) {
        this.logger.warn(`Discord guild counts HTTP ${res.status}`);
        return null;
      }
      const json = (await res.json()) as {
        approximate_member_count?: number;
        approximate_presence_count?: number;
      };
      return {
        members: json.approximate_member_count ?? 0,
        online: json.approximate_presence_count ?? 0,
      };
    } catch (err) {
      this.logger.warn(`Discord guild counts échec: ${String(err)}`);
      return null;
    }
  }

  // ── Patch notes : les 5 plus récentes (épinglées d'abord) ──
  private async fetchPatchNotes(): Promise<MenuPatchNote[]> {
    try {
      const web = (process.env.REBORN_WEB_URL?.trim() || 'https://reborn-rp.fr').replace(
        /\/$/,
        '',
      );
      const rows = await this.prisma.patchNote.findMany({
        orderBy: [{ pinned: 'desc' }, { publishedAt: 'desc' }],
        take: 5,
        select: { id: true, version: true, title: true, publishedAt: true },
      });
      return rows.map((r) => ({
        id: r.id,
        version: r.version,
        title: r.title,
        publishedAt: r.publishedAt.toISOString(),
        url: `${web}/patchnotes/${r.id}`,
      }));
    } catch (err) {
      this.logger.warn(`Patch notes menu échec: ${String(err)}`);
      return [];
    }
  }

  // ── Streams : liste configurée via REBORN_STREAMERS, statut live via Twitch
  // si TWITCH_CLIENT_ID/SECRET présents (sinon live=false). ──
  private async fetchStreams(): Promise<MenuStream[]> {
    const raw = process.env.REBORN_STREAMERS?.trim();
    if (!raw) return [];
    // Format : "login|Nom Affiché,login2" — le nom affiché est optionnel.
    const entries = raw
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
      .map((s) => {
        const [login, display] = s.split('|').map((p) => p.trim());
        return { login: login.toLowerCase(), name: display || login };
      });
    if (entries.length === 0) return [];

    const liveLogins = await this.fetchTwitchLive(entries.map((e) => e.login));
    return entries.map((e) => ({
      name: e.name,
      url: `https://twitch.tv/${e.login}`,
      live: liveLogins?.has(e.login) ?? false,
      title: liveLogins?.get(e.login) ?? null,
    }));
  }

  private async fetchTwitchLive(
    logins: string[],
  ): Promise<Map<string, string | null> | null> {
    const clientId = process.env.TWITCH_CLIENT_ID?.trim();
    const clientSecret = process.env.TWITCH_CLIENT_SECRET?.trim();
    if (!clientId || !clientSecret || logins.length === 0) return null;
    try {
      const token = await this.getTwitchToken(clientId, clientSecret);
      if (!token) return null;
      const qs = logins.map((l) => `user_login=${encodeURIComponent(l)}`).join('&');
      const res = await this.fetchWithTimeout(
        `https://api.twitch.tv/helix/streams?${qs}`,
        { headers: { 'Client-Id': clientId, Authorization: `Bearer ${token}` } },
      );
      if (!res.ok) {
        this.logger.warn(`Twitch streams HTTP ${res.status}`);
        return null;
      }
      const json = (await res.json()) as {
        data?: Array<{ user_login: string; title: string }>;
      };
      const map = new Map<string, string | null>();
      for (const s of json.data ?? []) {
        map.set(s.user_login.toLowerCase(), s.title ?? null);
      }
      return map;
    } catch (err) {
      this.logger.warn(`Twitch live échec: ${String(err)}`);
      return null;
    }
  }

  private async getTwitchToken(
    clientId: string,
    clientSecret: string,
  ): Promise<string | null> {
    const now = Date.now();
    if (this.twitchToken && this.twitchToken.expiresAt > now) {
      return this.twitchToken.value;
    }
    const body = new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      grant_type: 'client_credentials',
    });
    const res = await this.fetchWithTimeout('https://id.twitch.tv/oauth2/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
    if (!res.ok) {
      this.logger.warn(`Twitch token HTTP ${res.status}`);
      return null;
    }
    const json = (await res.json()) as {
      access_token?: string;
      expires_in?: number;
    };
    if (!json.access_token) return null;
    this.twitchToken = {
      value: json.access_token,
      expiresAt: now + Math.max(60_000, (json.expires_in ?? 3600) * 1000 - 60_000),
    };
    return json.access_token;
  }

  private async fetchWithTimeout(
    url: string,
    init: RequestInit,
  ): Promise<Response> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), HTTP_TIMEOUT_MS);
    try {
      return await fetch(url, { ...init, signal: controller.signal });
    } finally {
      clearTimeout(timer);
    }
  }
}
