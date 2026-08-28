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
 * Garde du pont de commandes serveur (`/v1/files/commands/*`) — appelé par le
 * plugin ShinobiCore, PAS par le panel (donc pas de JWT).
 *
 * Vérifie `X-Reborn-Signature = hex(HMAC-SHA256(REBORN_WEBHOOK_SECRET, base))` :
 * - GET  (drain) : base = la chaîne fixe `"pending"` (lecture seule des reloads
 *   en attente — non sensible ; le secret prouve l'identité du pont).
 * - POST (ack)   : base = le rawBody exact (comme {@code HmacSignatureGuard}) →
 *   empêche de forger un résultat sans le secret.
 *
 * Le secret est le même `REBORN_WEBHOOK_SECRET` partagé API ↔ bot ↔ pont.
 */
@Injectable()
export class CommandBridgeGuard implements CanActivate {
  private readonly logger = new Logger(CommandBridgeGuard.name);

  canActivate(context: ExecutionContext): boolean {
    const req = context.switchToHttp().getRequest<
      Request & { rawBody?: Buffer }
    >();

    const secret = process.env.REBORN_WEBHOOK_SECRET;
    if (!secret) {
      this.logger.warn('CommandBridgeGuard : REBORN_WEBHOOK_SECRET manquant, refuse 401.');
      throw new UnauthorizedException('webhook secret manquant côté serveur');
    }

    const provided = req.headers['x-reborn-signature'];
    if (!provided || typeof provided !== 'string') {
      throw new UnauthorizedException('header x-reborn-signature manquant');
    }

    const base =
      req.method === 'GET'
        ? Buffer.from('pending')
        : (req.rawBody ?? Buffer.from(''));
    const expected = createHmac('sha256', secret).update(base).digest('hex');

    if (expected.length !== provided.length) {
      throw new UnauthorizedException('signature invalide');
    }
    try {
      const ok = timingSafeEqual(
        Buffer.from(expected, 'hex'),
        Buffer.from(provided, 'hex'),
      );
      if (!ok) throw new UnauthorizedException('signature invalide');
    } catch {
      throw new UnauthorizedException('signature invalide');
    }
    return true;
  }
}
