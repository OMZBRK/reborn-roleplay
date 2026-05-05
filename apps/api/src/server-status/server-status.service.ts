import { Injectable, Logger } from '@nestjs/common';
import { status } from 'minecraft-server-util';

export interface ServerStatusDto {
  online: boolean;
  players: number;
  capacity: number;
  ping: number | null; // millisecondes, null si timeout/unreachable
  version: string | null;
  motd: string | null;
  ip: string;
  // Timestamp ISO de la dernière mesure réelle. Le sidebar peut afficher
  // "il y a 12s" pour situer la fraîcheur (le service met en cache 10s).
  measuredAt: string;
}

// Cache simple in-memory pour éviter de pinger le serveur Minecraft à
// chaque request. La sidebar du launcher poll toutes les 30s mais N
// joueurs simultanés ferait N pings/30s — inutile, et la liste des
// joueurs ne change que toutes les minutes en pratique.
const CACHE_TTL_MS = 10_000;

interface Cached {
  data: ServerStatusDto;
  expiresAt: number;
}

@Injectable()
export class ServerStatusService {
  private readonly logger = new Logger(ServerStatusService.name);
  private cache: Cached | null = null;
  // Évite les pings concurrents : si N requêtes arrivent en parallèle alors
  // que le cache est expiré, on ne lance qu'un seul ping et tous attendent.
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
    const host = process.env.REBORN_SERVER_HOST?.trim() || 'localhost';
    const port = Number(process.env.REBORN_SERVER_PORT ?? 25565);
    const ip = `${host}:${port}`;

    try {
      // status() utilise le protocole Server List Ping (MC 1.7+).
      // timeout 3s pour ne pas bloquer la sidebar côté launcher si le
      // serveur est down / saturé.
      const res = await status(host, port, { timeout: 3000 });
      // res.players.sample peut contenir un échantillon, on s'en fiche.
      // res.motd peut être un objet (MOTD multi-style) ou plain text ; on
      // garde toString() pour rester simple.
      return {
        online: true,
        players: res.players.online,
        capacity: res.players.max,
        ping: res.roundTripLatency,
        version: res.version.name,
        // Le motd peut contenir des codes couleur §a/§b ; on les strip
        // pour avoir un texte lisible côté sidebar (qui n'a pas le
        // rendu MC).
        motd: stripMcColorCodes(res.motd.clean ?? res.motd.raw ?? ''),
        ip,
        measuredAt: new Date().toISOString(),
      };
    } catch (err) {
      // Serveur down / unreachable / timeout. On retourne offline, pas
      // d'exception — le sidebar gère l'état "hors ligne" et c'est
      // l'info qu'on veut afficher.
      this.logger.warn(
        `MC ping ${ip} échoué : ${(err as Error).message}`,
      );
      return {
        online: false,
        players: 0,
        capacity: 0,
        ping: null,
        version: null,
        motd: null,
        ip,
        measuredAt: new Date().toISOString(),
      };
    }
  }
}

function stripMcColorCodes(s: string): string {
  // Codes Minecraft : section sign (§) suivi d'un caractère hex ou lettre
  // de style. On les retire mais on garde le reste.
  return s.replace(/§[0-9a-fk-or]/gi, '').trim();
}
