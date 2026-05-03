import {
  CanActivate,
  ExecutionContext,
  Injectable,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { createHmac, timingSafeEqual } from 'node:crypto';
import type { Request } from 'express';

/**
 * Verifie que le header `X-Reborn-Signature` matche
 * `HMAC-SHA256(rawBody, REBORN_WEBHOOK_SECRET)` en hex.
 *
 * Utilise pour les endpoints /v1/staff/* qui sont appeles par le bot
 * Discord (slash commands staff). Le bot est de l'infrastructure de
 * confiance ; on lui delegue des actions admin via cette signature
 * symetrique au lieu d'introduire un JWT de service. Quand on aura
 * de vrais staff humains avec des roles distincts, on basculera sur
 * un guard JWT classique.
 *
 * Le rawBody est capture par le hook `verify` de body-parser dans
 * main.ts.
 */
@Injectable()
export class HmacSignatureGuard implements CanActivate {
  private readonly logger = new Logger(HmacSignatureGuard.name);

  canActivate(context: ExecutionContext): boolean {
    const req = context
      .switchToHttp()
      .getRequest<Request & { rawBody?: Buffer }>();

    const secret = process.env.REBORN_WEBHOOK_SECRET;
    if (!secret) {
      this.logger.warn(
        'HmacSignatureGuard : REBORN_WEBHOOK_SECRET non configure, refuse 401.',
      );
      throw new UnauthorizedException('webhook secret manquant cote serveur');
    }

    const provided = req.headers['x-reborn-signature'];
    if (!provided || typeof provided !== 'string') {
      throw new UnauthorizedException('header x-reborn-signature manquant');
    }

    const body = req.rawBody ?? Buffer.alloc(0);
    const expected = createHmac('sha256', secret).update(body).digest('hex');

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
