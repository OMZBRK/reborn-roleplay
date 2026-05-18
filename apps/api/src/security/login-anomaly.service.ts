import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { WebhooksService } from '../webhooks/webhooks.service';

/**
 * Detection d'anomalies de login : compare le pays courant (lookup IP)
 * avec le `lastKnownCountry` du user. Si different ET non-null :
 *   - cree LoginAnomaly {acknowledged=false}
 *   - notifie le bot via webhook security-alert (poste embed dans
 *     #staff-security)
 *
 * MVP : on compare juste contre le dernier pays connu (un seul). En
 * v1.1 on remontera l'historique des N derniers logins via Session.
 *
 * GeoIP : ip-api.com gratuit, 45 req/min sans cle. Cache RAM TTL 24h
 * pour limiter les hits (l'IP est plus stable que ca).
 */

interface GeoCacheEntry {
  countryCode: string | null;
  country: string | null;
  expiresAt: number;
}

const GEO_CACHE = new Map<string, GeoCacheEntry>();
const GEO_TTL_MS = 24 * 60 * 60 * 1000;

@Injectable()
export class LoginAnomalyService {
  private readonly logger = new Logger(LoginAnomalyService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly webhooks: WebhooksService,
  ) {}

  /**
   * Appele apres chaque login (Microsoft launcher OU Discord staff)
   * une fois les tokens emis. Best-effort : un fail geo / webhook ne
   * bloque PAS le login.
   */
  async check(userId: string, ip?: string, userAgent?: string): Promise<void> {
    if (!ip || ip === '::1' || ip === '127.0.0.1' || ip.startsWith('192.168.')) {
      // IP locale / loopback : pas de geo possible. On no-op.
      return;
    }
    try {
      const user = await this.prisma.user.findUnique({
        where: { id: userId },
        select: {
          id: true,
          minecraftUsername: true,
          lastKnownCountry: true,
        },
      });
      if (!user) return;

      const geo = await this.lookupCountry(ip);
      if (!geo.countryCode) return;

      // Premier login → pas d'anomalie, on log juste le pays.
      if (!user.lastKnownCountry) {
        await this.prisma.user.update({
          where: { id: userId },
          data: { lastKnownCountry: geo.countryCode, lastKnownIp: ip },
        });
        return;
      }

      if (user.lastKnownCountry === geo.countryCode) {
        // Meme pays : juste maj de l'IP (peut avoir change).
        if (ip !== user.lastKnownCountry) {
          await this.prisma.user.update({
            where: { id: userId },
            data: { lastKnownIp: ip },
          });
        }
        return;
      }

      // Anomalie : nouveau pays detecte.
      const reason = `Login depuis ${geo.country ?? geo.countryCode} (anciennement ${user.lastKnownCountry}).`;
      await this.prisma.loginAnomaly.create({
        data: {
          userId,
          ipAddress: ip,
          country: geo.countryCode,
          reason,
          notifiedVia: [],
        },
      });
      this.logger.warn(`anomaly login userId=${userId} : ${reason}`);

      // Notif Discord — best-effort.
      try {
        await this.webhooks.securityAlert({
          userPseudo: user.minecraftUsername,
          userId: user.id,
          kind: 'login.new-country',
          reason,
          ip,
          country: geo.country ?? geo.countryCode,
          userAgent,
        });
        await this.prisma.loginAnomaly.updateMany({
          where: { userId, country: geo.countryCode, acknowledged: false },
          data: { notifiedVia: ['discord'] },
        });
      } catch (err) {
        this.logger.warn(
          `securityAlert webhook echec : ${(err as Error).message}`,
        );
      }

      // On maj quand meme le pays connu — sinon l'anomalie est
      // re-flag a chaque login depuis le nouveau pays. Le staff peut
      // ack l'anomalie ensuite.
      await this.prisma.user.update({
        where: { id: userId },
        data: { lastKnownCountry: geo.countryCode, lastKnownIp: ip },
      });
    } catch (err) {
      this.logger.warn(`LoginAnomaly check crash : ${(err as Error).message}`);
    }
  }

  async list(opts: { unacknowledgedOnly?: boolean; take?: number } = {}) {
    return this.prisma.loginAnomaly.findMany({
      where: opts.unacknowledgedOnly ? { acknowledged: false } : undefined,
      orderBy: { createdAt: 'desc' },
      take: opts.take ?? 100,
    });
  }

  async acknowledge(id: string): Promise<void> {
    await this.prisma.loginAnomaly.update({
      where: { id },
      data: { acknowledged: true },
    });
  }

  private async lookupCountry(
    ip: string,
  ): Promise<{ countryCode: string | null; country: string | null }> {
    const now = Date.now();
    const cached = GEO_CACHE.get(ip);
    if (cached && cached.expiresAt > now) {
      return { countryCode: cached.countryCode, country: cached.country };
    }
    try {
      const res = await fetch(
        `http://ip-api.com/json/${encodeURIComponent(ip)}?fields=status,country,countryCode`,
        { signal: AbortSignal.timeout(3000) },
      );
      if (!res.ok) throw new Error(`ip-api ${res.status}`);
      const data = (await res.json()) as {
        status?: string;
        country?: string;
        countryCode?: string;
      };
      const result =
        data.status === 'success'
          ? {
              countryCode: data.countryCode ?? null,
              country: data.country ?? null,
            }
          : { countryCode: null, country: null };
      GEO_CACHE.set(ip, { ...result, expiresAt: now + GEO_TTL_MS });
      return result;
    } catch (err) {
      this.logger.warn(`geo lookup echec ip=${ip} : ${(err as Error).message}`);
      GEO_CACHE.set(ip, {
        countryCode: null,
        country: null,
        expiresAt: now + 5 * 60_000, // cache court sur fail pour pas re-spammer
      });
      return { countryCode: null, country: null };
    }
  }
}
