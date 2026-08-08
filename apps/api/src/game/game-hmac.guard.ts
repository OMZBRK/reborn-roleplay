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
 * `HMAC-SHA256(<mcUuid>, REBORN_WEBHOOK_SECRET)` en hex, ou le mcUuid est
 * le parametre de route de la requete.
 *
 * Contrairement au {@link HmacSignatureGuard} des endpoints /v1/staff/*
 * (qui signe le rawBody), les endpoints /v1/game/* sont des GET sans corps :
 * on signe donc le mcUuid demande. La signature reste liee a la ressource
 * consultee (pas de signature statique rejouable pour n'importe quel uuid)
 * et prouve que l'appelant (le plugin ShinobiCore) detient le secret partage.
 *
 * ShinobiCore doit configurer le meme `REBORN_WEBHOOK_SECRET` que l'API et
 * envoyer `X-Reborn-Signature = hex(HMAC-SHA256(secret, mcUuid))`.
 */
@Injectable()
export class GameHmacGuard implements CanActivate {
  private readonly logger = new Logger(GameHmacGuard.name);

  canActivate(context: ExecutionContext): boolean {
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
