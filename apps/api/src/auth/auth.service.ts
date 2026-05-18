import {
  HttpException,
  HttpStatus,
  Injectable,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { randomUUID, randomBytes, createHash } from 'crypto';
import { Role, User } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { LoginAnomalyService } from '../security/login-anomaly.service';
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
  discord: {
    userId: string;
    username: string;
    linkedAt: string;
  } | null;
}

const REFRESH_TOKEN_TTL_DAYS = 30;

/**
 * Calcule l'UUID offline-mode tel que Paper le fait sur un serveur en
 * `online-mode=false` : MD5("OfflinePlayer:<pseudo>") avec les bits de
 * version forces a 3 (name-based MD5) et de variant a IETF (RFC 4122).
 *
 * Le resultat est un UUID v3 valide, en format dashed, donc parsable par
 * `UndashedUuid.fromStringLenient` cote MC 1.21+.
 */
export function offlineModeUuid(username: string): string {
  const md5 = createHash('md5').update(`OfflinePlayer:${username}`).digest();
  // RFC 4122 §4.3 — UUIDv3 : version 0011 dans le 7e octet, variant 10x dans le 9e.
  md5[6] = (md5[6] & 0x0f) | 0x30;
  md5[8] = (md5[8] & 0x3f) | 0x80;
  const hex = md5.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

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
    private readonly loginAnomaly: LoginAnomalyService,
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

    const tokens = await this.issueTokens(user, meta);
    void this.loginAnomaly.check(user.id, meta.ip, meta.userAgent);
    return tokens;
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
   * Issue a token pair for an existing user (no MS/MC validation).
   * Used by the staff Discord-login flow once we've matched a Discord
   * identity to a Reborn user. Refuses banned users and, when
   * `requireMinRole` is provided, refuses anyone below that role.
   */
  async issueTokensForUserId(
    userId: string,
    meta: { userAgent?: string; ip?: string },
    opts?: { requireMinRole?: Role },
  ): Promise<AuthResult> {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user || user.banned) {
      throw new UnauthorizedException();
    }
    if (opts?.requireMinRole) {
      const ranks: Role[] = [
        Role.PLAYER,
        Role.WHITELISTED,
        Role.HELPER,
        Role.WHITELIST_REVIEWER,
        Role.MODERATOR,
        Role.ADMIN,
        Role.OWNER,
      ];
      if (ranks.indexOf(user.role) < ranks.indexOf(opts.requireMinRole)) {
        throw new HttpException(
          `Rôle insuffisant : ${user.role} < ${opts.requireMinRole}.`,
          HttpStatus.FORBIDDEN,
        );
      }
    }
    await this.prisma.user.update({
      where: { id: user.id },
      data: { lastLoginAt: new Date(), lastKnownIp: meta.ip ?? user.lastKnownIp },
    });
    const tokens = await this.issueTokens(user, meta);
    // Detection anomalie login : nouveau pays vs lastKnownCountry.
    // Best-effort, fire-and-forget (best ne pas await pour pas
    // ralentir le login).
    void this.loginAnomaly.check(user.id, meta.ip, meta.userAgent);
    return tokens;
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

    // UUID au format **offline-mode Mojang** : MD5("OfflinePlayer:<pseudo>")
    // avec version=3 (UUID v3, name-based). C'est ce qu'un serveur Paper en
    // mode offline calcule lui-meme pour ce pseudo, donc le serveur dev
    // reconnaitra le joueur. Surtout, c'est du hex strict — UndashedUuid
    // de MC 1.21+ refuse tout char non-hex et plante un NumberFormatException
    // sur l'ancien format "dev00000-...".
    const fakeUuid = offlineModeUuid(username);
    const msAccountId = `dev:${username}`;

    // On lookup par msAccountId (stable au pseudo) et non par UUID, pour
    // pouvoir migrer en silence les anciens dev users qui avaient l'UUID
    // casse "dev00000-...-OMZ000000000".
    let user = await this.prisma.user.findUnique({ where: { msAccountId } });
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
        data: {
          minecraftUuid: fakeUuid, // migration silencieuse si UUID legacy.
          minecraftUsername: username,
          lastLoginAt: new Date(),
          lastKnownIp: meta.ip ?? user.lastKnownIp,
        },
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
      discord:
        user.discordUserId && user.discordUsername && user.discordLinkedAt
          ? {
              userId: user.discordUserId,
              username: user.discordUsername,
              linkedAt: user.discordLinkedAt.toISOString(),
            }
          : null,
    };
  }
}
