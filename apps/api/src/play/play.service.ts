import { ForbiddenException, Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHmac, timingSafeEqual } from 'node:crypto';
import { PrismaService } from '../prisma/prisma.service';

/**
 * Play-token compact format (NOT a full JWT, intentionally simple to
 * verify cote plugin Java sans dep externe) :
 *
 *   <base64url(JSON payload)>.<base64url(HMAC-SHA256(secret, payload))>
 *
 * Payload :
 *   { sub: userId, mcUuid: dashed, mcUsername, iat: unix, exp: unix }
 *
 * Le secret est partage entre l'API (qui emet) et le plugin Guardian
 * (qui verifie). Le launcher n'a pas ce secret — il ne fait que recevoir
 * le token via l'endpoint authentifie ci-dessous, donc un user patche
 * cote client ne peut pas inventer de play-token.
 */
export interface PlayTokenPayload {
  sub: string;
  mcUuid: string;
  mcUsername: string;
  iat: number;
  exp: number;
}

const TOKEN_TTL_SECONDS = 5 * 60;

@Injectable()
export class PlayService {
  private readonly logger = new Logger(PlayService.name);
  private readonly secret: Buffer;

  constructor(config: ConfigService, private readonly prisma: PrismaService) {
    const raw = config.get<string>('REBORN_PLAY_TOKEN_SECRET');
    if (!raw || raw.length < 32) {
      throw new Error(
        'REBORN_PLAY_TOKEN_SECRET manquant ou trop court (32+ chars requis). ' +
          'Cf .env.example.',
      );
    }
    this.secret = Buffer.from(raw, 'utf8');
  }

  /**
   * Emet un play-token pour `userId`, apres avoir verifie en base que le
   * user existe, n'est pas banni, et que son role autorise l'entree sur
   * le serveur. C'est l'unique source de verite : meme si le launcher
   * gate cote UI, c'est ici qu'on bloque pour de vrai.
   */
  async issuePlayToken(userId: string): Promise<{ playToken: string; expiresAt: string }> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: {
        id: true,
        minecraftUuid: true,
        minecraftUsername: true,
        role: true,
        banned: true,
      },
    });

    if (!user) throw new ForbiddenException('user inconnu');
    if (user.banned) throw new ForbiddenException('compte banni');
    if (user.role === 'PLAYER') {
      throw new ForbiddenException('whitelist requise pour rejoindre le serveur');
    }

    const now = Math.floor(Date.now() / 1000);
    const payload: PlayTokenPayload = {
      sub: user.id,
      mcUuid: user.minecraftUuid,
      mcUsername: user.minecraftUsername,
      iat: now,
      exp: now + TOKEN_TTL_SECONDS,
    };

    const payloadB64 = base64url(Buffer.from(JSON.stringify(payload), 'utf8'));
    const signature = createHmac('sha256', this.secret).update(payloadB64).digest();
    const sigB64 = base64url(signature);

    this.logger.log(`play-token emis pour ${user.minecraftUsername} (exp dans ${TOKEN_TTL_SECONDS}s)`);
    return {
      playToken: `${payloadB64}.${sigB64}`,
      expiresAt: new Date(payload.exp * 1000).toISOString(),
    };
  }

  /**
   * Verifie un play-token (utilise par les tests + potentiellement un futur
   * endpoint d'introspection cote plugin). La verif cote plugin Guardian
   * est faite en Java avec la meme logique.
   */
  verifyPlayToken(token: string): PlayTokenPayload | null {
    const parts = token.split('.');
    if (parts.length !== 2) return null;
    const [payloadB64, sigB64] = parts;

    const expectedSig = createHmac('sha256', this.secret).update(payloadB64).digest();
    let providedSig: Buffer;
    try {
      providedSig = base64urlDecode(sigB64);
    } catch {
      return null;
    }
    if (providedSig.length !== expectedSig.length) return null;
    if (!timingSafeEqual(providedSig, expectedSig)) return null;

    let payload: PlayTokenPayload;
    try {
      payload = JSON.parse(base64urlDecode(payloadB64).toString('utf8')) as PlayTokenPayload;
    } catch {
      return null;
    }

    if (typeof payload.exp !== 'number' || payload.exp * 1000 < Date.now()) return null;
    return payload;
  }

  /**
   * Vérifie la signature HMAC d'un play-token SANS exiger la fraîcheur (exp).
   * Utilisé par le report in-game : le menu ÉCHAP peut être ouvert bien après
   * les 5min de validité, mais la signature prouve toujours que l'API a émis
   * ce token pour ce compte — donc l'identité (`sub`) reste digne de confiance.
   */
  verifyPlayTokenSignature(token: string): PlayTokenPayload | null {
    const parts = token.split('.');
    if (parts.length !== 2) return null;
    const [payloadB64, sigB64] = parts;

    const expectedSig = createHmac('sha256', this.secret).update(payloadB64).digest();
    let providedSig: Buffer;
    try {
      providedSig = base64urlDecode(sigB64);
    } catch {
      return null;
    }
    if (providedSig.length !== expectedSig.length) return null;
    if (!timingSafeEqual(providedSig, expectedSig)) return null;

    try {
      return JSON.parse(base64urlDecode(payloadB64).toString('utf8')) as PlayTokenPayload;
    } catch {
      return null;
    }
  }
}

function base64url(buf: Buffer): string {
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64urlDecode(str: string): Buffer {
  const pad = str.length % 4 === 0 ? '' : '='.repeat(4 - (str.length % 4));
  return Buffer.from(str.replace(/-/g, '+').replace(/_/g, '/') + pad, 'base64');
}
