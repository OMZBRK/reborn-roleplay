import { HttpException, HttpStatus, Injectable, Logger } from '@nestjs/common';
import axios, { AxiosInstance } from 'axios';

const MC_PROFILE_URL = 'https://api.minecraftservices.com/minecraft/profile';

export interface MinecraftProfile {
  id: string;
  name: string;
}

/**
 * Valide cote serveur le mc_access_token transmis par le launcher.
 *
 * Reference : PLAN_CONCEPTION_LAUNCHER.md §7.6.
 *
 * On ne fait JAMAIS confiance a un payload "user" envoye par le client —
 * on appelle directement Mojang pour recuperer l'UUID + pseudo authoritatifs.
 */
@Injectable()
export class MojangService {
  private readonly logger = new Logger(MojangService.name);
  private readonly http: AxiosInstance = axios.create({
    timeout: 10_000,
    headers: { 'User-Agent': 'reborn-api/0.0.1' },
  });

  async fetchProfile(mcAccessToken: string): Promise<MinecraftProfile> {
    try {
      const { data, status } = await this.http.get<MinecraftProfile>(MC_PROFILE_URL, {
        headers: { Authorization: `Bearer ${mcAccessToken}` },
        validateStatus: () => true,
      });

      if (status === 401 || status === 403) {
        throw new HttpException(
          'Microsoft access token invalide ou expire.',
          HttpStatus.UNAUTHORIZED,
        );
      }

      if (status === 404) {
        throw new HttpException(
          'Aucune licence Minecraft Java sur ce compte Microsoft.',
          HttpStatus.FORBIDDEN,
        );
      }

      if (status >= 400) {
        this.logger.warn(`Mojang a repondu ${status} : ${JSON.stringify(data)}`);
        throw new HttpException(
          'Impossible de valider le compte Minecraft pour le moment.',
          HttpStatus.BAD_GATEWAY,
        );
      }

      if (!data?.id || !data?.name) {
        throw new HttpException(
          'Reponse Mojang invalide.',
          HttpStatus.BAD_GATEWAY,
        );
      }

      return data;
    } catch (err) {
      if (err instanceof HttpException) throw err;
      this.logger.error('Erreur reseau Mojang', err as Error);
      throw new HttpException(
        'Service Mojang injoignable.',
        HttpStatus.BAD_GATEWAY,
      );
    }
  }
}
