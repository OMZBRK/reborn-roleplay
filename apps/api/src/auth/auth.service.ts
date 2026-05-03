import {
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { randomUUID, randomBytes } from 'crypto';
import { Role, User } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { MojangService } from './mojang.service';

export interface JwtPayload {
  sub: string;            // user.id
  uuid: string;           // minecraftUuid (stable identifier)
  role: Role;
  ban?: boolean;
}

export interface AuthResult {
  accessToken: string;
  refreshToken: string;
  user: PublicUser;
}

export interface PublicUser {
  id: string;
  minecraftUuid: string;
  minecraftUsername: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: Role;
}

const REFRESH_TOKEN_TTL_DAYS = 30;

/**
 * Convertit un UUID Mojang (32 hex sans tirets) en form 8-4-4-4-12.
 * Le schema Prisma stocke la forme dashed.
 */
function dashUuid(id: string): string {
  if (id.length === 32) {
    return `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`;
  }
  return id;
}

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
    private readonly mojang: MojangService,
  ) {}

  /**
   * Etape principale : valide le token MC contre Mojang, cree/maj le User,
   * emet un JWT Reborn + un refresh token persiste en Session.
   */
  async loginWithMinecraft(mcAccessToken: string, meta: { userAgent?: string; ip?: string }): Promise<AuthResult> {
    const profile = await this.mojang.fetchProfile(mcAccessToken);
    const uuid = dashUuid(profile.id);

    let user = await this.prisma.user.findUnique({ where: { minecraftUuid: uuid } });

    if (!user) {
      user = await this.prisma.user.create({
        data: {
          msAccountId: uuid,        // place-holder : on pourrait stocker le sub MS plus tard
          minecraftUuid: uuid,
          minecraftUsername: profile.name,
          lastLoginAt: new Date(),
          lastKnownIp: meta.ip ?? null,
        },
      });
      this.logger.log(`Nouvel utilisateur cree : ${profile.name} (${uuid})`);
    } else {
      user = await this.prisma.user.update({
        where: { id: user.id },
        data: {
          minecraftUsername: profile.name,
          lastLoginAt: new Date(),
          lastKnownIp: meta.ip ?? user.lastKnownIp,
        },
      });
    }

    if (user.banned) {
      throw new HttpException(
        `Compte banni : ${user.banReason ?? 'sans raison fournie'}`,
        HttpStatus.FORBIDDEN,
      );
    }

    return this.issueTokens(user, meta);
  }

  /**
   * Roule le refresh token : invalide l'ancien et en emet un nouveau.
   * Detecte les replays (refresh deja revoque) -> revoque toutes les
   * sessions du user, oblige a re-login.
   */
  async refresh(rawRefreshToken: string, meta: { userAgent?: string; ip?: string }): Promise<AuthResult> {
    const session = await this.prisma.session.findUnique({
      where: { refreshToken: rawRefreshToken },
      include: { user: true },
    });

    if (!session) throw new UnauthorizedException('Refresh token inconnu.');

    if (session.revokedAt) {
      // Replay detecte : on revoque toutes les sessions du user.
      await this.prisma.session.updateMany({
        where: { userId: session.userId, revokedAt: null },
        data: { revokedAt: new Date() },
      });
      throw new UnauthorizedException('Refresh token replay : sessions revoquees.');
    }

    if (session.expiresAt.getTime() < Date.now()) {
      throw new UnauthorizedException('Refresh token expire.');
    }

    if (session.user.banned) {
      throw new HttpException('Compte banni.', HttpStatus.FORBIDDEN);
    }

    // Marque l'ancienne session revoquee + emet un nouveau couple.
    await this.prisma.session.update({
      where: { id: session.id },
      data: { revokedAt: new Date() },
    });

    return this.issueTokens(session.user, meta);
  }

  async logout(userId: string, refreshToken?: string): Promise<void> {
    if (refreshToken) {
      await this.prisma.session.updateMany({
        where: { userId, refreshToken, revokedAt: null },
        data: { revokedAt: new Date() },
      });
    } else {
      // Pas de refresh fourni → on ne revoque que les sessions actives.
      await this.prisma.session.updateMany({
        where: { userId, revokedAt: null },
        data: { revokedAt: new Date() },
      });
    }
  }

  async getCurrentUser(userId: string): Promise<PublicUser> {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user) throw new UnauthorizedException();
    return this.toPublic(user);
  }

  /**
   * Dev-only : cree (ou reutilise) un User factice et emet un JWT,
   * sans passer par Microsoft. Utilise tant que le Client ID Azure
   * attend l'approbation Microsoft (cf docs/adr/0001-...).
   *
   * **Doit etre desactive en production** — l'endpoint controleur verifie
   * NODE_ENV avant d'appeler ce service.
   */
  async devLogin(
    username: string,
    meta: { userAgent?: string; ip?: string },
  ): Promise<AuthResult> {
    if (process.env.NODE_ENV === 'production') {
      throw new HttpException('dev-login disabled in production', HttpStatus.FORBIDDEN);
    }

    // UUID stable derive du pseudo, format Mojang en clair pour le dev.
    const fakeUuid = `dev00000-0000-4000-8000-${username.padEnd(12, '0').slice(0, 12)}`;
    const msAccountId = `dev:${username}`;

    let user = await this.prisma.user.findUnique({ where: { minecraftUuid: fakeUuid } });
    if (!user) {
      user = await this.prisma.user.create({
        data: {
          msAccountId,
          minecraftUuid: fakeUuid,
          minecraftUsername: username,
          lastLoginAt: new Date(),
          lastKnownIp: meta.ip ?? null,
        },
      });
    } else {
      user = await this.prisma.user.update({
        where: { id: user.id },
        data: { lastLoginAt: new Date(), lastKnownIp: meta.ip ?? user.lastKnownIp },
      });
    }

    return this.issueTokens(user, meta);
  }

  // ──────────────────────────────────────────────────────

  private async issueTokens(
    user: User,
    meta: { userAgent?: string; ip?: string },
  ): Promise<AuthResult> {
    const payload: JwtPayload = {
      sub: user.id,
      uuid: user.minecraftUuid,
      role: user.role,
    };

    const accessToken = await this.jwt.signAsync(payload, {
      expiresIn: this.config.get('JWT_ACCESS_TTL', '15m'),
    });

    const refreshToken = randomBytes(48).toString('base64url');
    const expiresAt = new Date(Date.now() + REFRESH_TOKEN_TTL_DAYS * 24 * 3600 * 1000);

    await this.prisma.session.create({
      data: {
        id: randomUUID(),
        userId: user.id,
        refreshToken,
        userAgent: meta.userAgent,
        ipAddress: meta.ip,
        expiresAt,
      },
    });

    return {
      accessToken,
      refreshToken,
      user: this.toPublic(user),
    };
  }

  private toPublic(user: User): PublicUser {
    return {
      id: user.id,
      minecraftUuid: user.minecraftUuid,
      minecraftUsername: user.minecraftUsername,
      displayName: user.displayName,
      avatarUrl: user.avatarUrl,
      role: user.role,
    };
  }
}
