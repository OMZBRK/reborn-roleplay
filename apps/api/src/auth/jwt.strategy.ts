import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { PrismaService } from '../prisma/prisma.service';
import type { JwtPayload } from './auth.service';

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy, 'jwt') {
  constructor(config: ConfigService, private readonly prisma: PrismaService) {
    const secret = config.get<string>('JWT_SECRET');
    if (!secret) {
      throw new Error('JWT_SECRET manquant dans .env');
    }
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      secretOrKey: secret,
      issuer: config.get('JWT_ISSUER', 'reborn-rp'),
      audience: config.get('JWT_AUDIENCE', 'reborn-launcher'),
      ignoreExpiration: false,
    });
  }

  async validate(payload: JwtPayload) {
    // On verifie que l'utilisateur existe toujours et n'est pas banni.
    const user = await this.prisma.user.findUnique({
      where: { id: payload.sub },
      select: { id: true, banned: true, role: true, minecraftUuid: true },
    });
    if (!user || user.banned) throw new UnauthorizedException();
    return { sub: user.id, uuid: user.minecraftUuid, role: user.role };
  }
}
