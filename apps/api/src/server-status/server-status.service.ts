import { Injectable, Logger } from '@nestjs/common';
import {
  offlineStatus,
  pingServer,
  ServerStatusDto,
} from './slp';

export type { ServerStatusDto };

// Hôte/port par défaut si les variables d'env ne sont pas définies
// (cf. CLAUDE.md — REBORN_SERVER_HOST / REBORN_SERVER_PORT).
const DEFAULT_HOST = 'localhost';
const DEFAULT_PORT = 25565;
const PING_TIMEOUT_MS = 3000;

// Cache in-memory : la sidebar du launcher poll régulièrement et N joueurs
// connectés feraient N pings — inutile de marteler le serveur MC. 10s suffit
// pour un compteur "joueurs en ligne".
const CACHE_TTL_MS = 10_000;

interface Cached {
  data: ServerStatusDto;
  expiresAt: number;
}

@Injectable()
export class ServerStatusService {
  private readonly logger = new Logger(ServerStatusService.name);
  private cache: Cached | null = null;
  // Coalesce les pings concurrents : si plusieurs requêtes arrivent alors que
  // le cache est expiré, un seul ping part et toutes attendent son résultat.
  private inflight: Promise<ServerStatusDto> | null = null;

  async getStatus(): Promise<ServerStatusDto> {
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

  private async fetchFresh(): Promise<ServerStatusDto> {
    const host = process.env.REBORN_SERVER_HOST?.trim();
    const portRaw = process.env.REBORN_SERVER_PORT?.trim();

    // Hôte non configuré → on renvoie une réponse offline bien formée plutôt
    // que de pinger localhost à l'aveugle.
    if (!host) {
      return offlineStatus(new Date().toISOString());
    }

    const port = portRaw ? Number(portRaw) : DEFAULT_PORT;
    const safePort =
      Number.isInteger(port) && port > 0 && port < 65536
        ? port
        : DEFAULT_PORT;

    const dto = await pingServer(host || DEFAULT_HOST, safePort, PING_TIMEOUT_MS);
    if (!dto.online) {
      this.logger.warn(`MC ping ${host}:${safePort} hors ligne / injoignable`);
    }
    return dto;
  }
}
