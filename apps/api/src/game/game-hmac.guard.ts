import {
  CanActivate,
  ExecutionContext,
  GoneException,
  Injectable,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHmac, timingSafeEqual } from 'node:crypto';
import type { Request } from 'express';

/**
 * Garde HISTORIQUE de `GET /v1/game/candidature/:mcUuid`, la seule route qui
 * l'utilise. Vérifie que le header `X-Reborn-Signature` vaut
 * `hex(HMAC-SHA256(REBORN_WEBHOOK_SECRET, mcUuid))`, où le mcUuid est le
 * paramètre de route.
 *
 * Faiblesses connues (CANDIDATURE_ET_DOUANE.md §3.12) : la signature ne porte
 * que sur le mcUuid, elle est donc statique et rejouable sans limite de temps ;
 * et elle oblige le serveur de jeu à détenir `REBORN_WEBHOOK_SECRET`, qui signe
 * aussi les routes du bot (/v1/staff/*). Les nouvelles routes du jeu passent
 * par `GameSignatureGuard`, et `GET /v1/game/acces/:mcUuid` renvoie le même bloc
 * `candidature`.
 *
 * Retrait sans nouveau code, dans cet ordre :
 *  1. ShinobiCore lit `candidature` dans `GET /v1/game/acces/:mcUuid` (lot D) ;
 *  2. `REBORN_GAME_CANDIDATURE_HISTORIQUE=0` côté API : la route répond 410 ;
 *  3. retirer `reborn.webhook-secret` de la config du jeu, puis faire tourner
 *     `REBORN_WEBHOOK_SECRET` (API et bot).
 * Variable vide ou absente : route ouverte (le ShinobiCore actuel en dépend),
 * signalée au démarrage.
 */
@Injectable()
export class GameHmacGuard implements CanActivate {
  private readonly logger = new Logger(GameHmacGuard.name);
  private readonly routeOuverte: boolean;

  constructor(config: ConfigService) {
    this.routeOuverte = routeHistoriqueOuverte(
      config.get<string>('REBORN_GAME_CANDIDATURE_HISTORIQUE'),
    );
    if (this.routeOuverte) {
      this.logger.warn(
        'Route historique GET /v1/game/candidature OUVERTE (signée par REBORN_WEBHOOK_SECRET) : à couper (REBORN_GAME_CANDIDATURE_HISTORIQUE=0) dès que ShinobiCore lit GET /v1/game/acces.',
      );
    } else {
      this.logger.log(
        'Route historique GET /v1/game/candidature coupée (410).',
      );
    }
  }

  canActivate(context: ExecutionContext): boolean {
    if (!this.routeOuverte) {
      throw new GoneException(
        'Route retirée : le bloc candidature est dans GET /v1/game/acces/:mcUuid, signé avec le secret du jeu.',
      );
    }

    const req = context.switchToHttp().getRequest<Request>();

    const secret = process.env.REBORN_WEBHOOK_SECRET;
    if (!secret) {
      this.logger.warn(
        'GameHmacGuard : REBORN_WEBHOOK_SECRET non configure, refuse 401.',
      );
      throw new UnauthorizedException('webhook secret manquant cote serveur');
    }

    const provided = req.headers['x-reborn-signature'];
    if (!provided || typeof provided !== 'string') {
      throw new UnauthorizedException('header x-reborn-signature manquant');
    }

    const mcUuid = (req.params?.mcUuid ?? '').toString();
    if (!mcUuid) {
      throw new UnauthorizedException('mcUuid manquant');
    }

    const expected = createHmac('sha256', secret).update(mcUuid).digest('hex');

    if (expected.length !== provided.length) {
      throw new UnauthorizedException('signature invalide');
    }
    try {
      const ok = timingSafeEqual(
        Buffer.from(expected, 'hex'),
        Buffer.from(provided, 'hex'),
      );
      if (!ok) {
        throw new UnauthorizedException('signature invalide');
      }
    } catch {
      throw new UnauthorizedException('signature invalide');
    }
    return true;
  }
}

/**
 * `REBORN_GAME_CANDIDATURE_HISTORIQUE` : `0`, `false`, `non`, `no` ou `off`
 * coupe la route ; toute autre valeur, ou rien, la laisse ouverte.
 */
export function routeHistoriqueOuverte(
  raw: string | undefined | null,
): boolean {
  const v = raw?.trim().toLowerCase();
  return !(
    v === '0' ||
    v === 'false' ||
    v === 'non' ||
    v === 'no' ||
    v === 'off'
  );
}
