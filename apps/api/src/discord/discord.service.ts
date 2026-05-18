import {
  BadRequestException,
  ConflictException,
  Injectable,
  InternalServerErrorException,
  Logger,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { randomBytes } from 'node:crypto';
import { Role } from '@prisma/client';
import { AuthService, AuthResult } from '../auth/auth.service';
import { TwoFactorChallengeStore } from '../auth/twofa.controller';
import { PrismaService } from '../prisma/prisma.service';

const STATE_TTL_MS = 5 * 60_000;
const DISCORD_API_BASE = 'https://discord.com/api/v10';

type PendingState =
  | { kind: 'link'; userId: string; expiresAt: number }
  | { kind: 'staff-login'; expiresAt: number };

interface DiscordTokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token: string;
  scope: string;
}

interface DiscordUserResponse {
  id: string;
  username: string;
  global_name: string | null;
  discriminator: string;
  avatar: string | null;
}

/**
 * Flow OAuth2 Discord (PLAN §10.8 / §7) :
 *
 *   1. Le launcher (authentifie via JWT) appelle GET /v1/auth/discord/start
 *      pour recevoir une URL d'autorisation Discord prefixee d'un `state`
 *      aleatoire stocke en RAM (TTL 5 min).
 *   2. Le launcher ouvre cette URL dans le navigateur systeme.
 *   3. L'utilisateur autorise -> Discord redirige vers
 *      /v1/auth/discord/callback?code=...&state=...
 *   4. Le serveur valide le state, echange le code contre un access_token,
 *      recupere l'identite Discord, met a jour le User en base.
 *   5. Le launcher polle GET /v1/auth/me et detecte que `discord` est
 *      desormais peuple.
 *
 * Pour le MVP, le state vit en memoire (Map). C'est suffisant tant qu'il y
 * a une seule instance d'API. Quand on scalera, on passera sur Redis.
 */
@Injectable()
export class DiscordService {
  private readonly logger = new Logger(DiscordService.name);
  private readonly states = new Map<string, PendingState>();

  constructor(
    private readonly prisma: PrismaService,
    private readonly auth: AuthService,
  ) {}

  /**
   * Genere un state lie a `userId` et retourne l'URL d'autorisation Discord.
   */
  startLinkFlow(userId: string): { url: string; state: string } {
    this.gcStates();

    const clientId = this.requireEnv('DISCORD_CLIENT_ID');
    const redirectUri = this.requireEnv('DISCORD_REDIRECT_URI');

    const state = randomBytes(24).toString('hex');
    this.states.set(state, {
      kind: 'link',
      userId,
      expiresAt: Date.now() + STATE_TTL_MS,
    });

    const params = new URLSearchParams({
      client_id: clientId,
      redirect_uri: redirectUri,
      response_type: 'code',
      scope: 'identify',
      state,
      prompt: 'consent',
    });

    return {
      url: `https://discord.com/oauth2/authorize?${params.toString()}`,
      state,
    };
  }

  /**
   * Login flow for the staff panel (apps/admin). No userId yet — we'll
   * resolve the Reborn user via `discordUserId` after the callback. The
   * redirect URI lands on a dedicated `/staff/callback` endpoint so we
   * don't have to multiplex callbacks on `state.kind`.
   */
  startStaffLogin(): { url: string; state: string } {
    this.gcStates();
    const clientId = this.requireEnv('DISCORD_CLIENT_ID');
    const redirectUri = this.requireEnv('DISCORD_STAFF_REDIRECT_URI');

    const state = randomBytes(24).toString('hex');
    this.states.set(state, {
      kind: 'staff-login',
      expiresAt: Date.now() + STATE_TTL_MS,
    });

    const params = new URLSearchParams({
      client_id: clientId,
      redirect_uri: redirectUri,
      response_type: 'code',
      scope: 'identify',
      state,
      prompt: 'consent',
    });

    return {
      url: `https://discord.com/oauth2/authorize?${params.toString()}`,
      state,
    };
  }

  /**
   * Echange le code OAuth contre les infos Discord et persiste la liaison.
   */
  async completeLinkFlow(code: string, state: string): Promise<void> {
    this.gcStates();
    const pending = this.states.get(state);
    if (!pending || pending.kind !== 'link') {
      throw new BadRequestException(
        'State invalide ou expire. Recommence la liaison depuis le launcher.',
      );
    }
    this.states.delete(state);

    const token = await this.exchangeCode(code, 'DISCORD_REDIRECT_URI');
    const profile = await this.fetchProfile(token.access_token);

    const existing = await this.prisma.user.findUnique({
      where: { discordUserId: profile.id },
    });
    if (existing && existing.id !== pending.userId) {
      throw new ConflictException(
        'Ce compte Discord est deja lie a un autre utilisateur Reborn.',
      );
    }

    await this.prisma.user.update({
      where: { id: pending.userId },
      data: {
        discordUserId: profile.id,
        // Le `username` est le handle global unique (ex: "omz_42").
        // Le `global_name` est le display name (ex: "OMZ"). On stocke
        // le handle car c'est lui qui identifie unique sur Discord.
        discordUsername: profile.username,
        discordLinkedAt: new Date(),
      },
    });
  }

  /**
   * Staff login : exchange the code, find the Reborn user matching the
   * Discord identity. Si twoFactorEnabled, on cree un challenge 2FA
   * temporaire (TTL 5min) et le frontend devra POST /auth/2fa/verify
   * avec le code TOTP pour recevoir les tokens. Sinon emission directe.
   */
  async completeStaffLogin(
    code: string,
    state: string,
    meta: { userAgent?: string; ip?: string },
  ): Promise<
    | { kind: 'tokens'; tokens: AuthResult }
    | { kind: 'challenge'; challenge: string }
  > {
    this.gcStates();
    const pending = this.states.get(state);
    if (!pending || pending.kind !== 'staff-login') {
      throw new BadRequestException(
        'State invalide ou expire. Recommence la connexion staff.',
      );
    }
    this.states.delete(state);

    const token = await this.exchangeCode(code, 'DISCORD_STAFF_REDIRECT_URI');
    const profile = await this.fetchProfile(token.access_token);

    const user = await this.prisma.user.findUnique({
      where: { discordUserId: profile.id },
      select: { id: true, role: true, banned: true, twoFactorEnabled: true },
    });
    if (!user) {
      throw new UnauthorizedException(
        'Aucun compte Reborn lie a ce Discord. Connecte-toi d\'abord via le launcher pour lier ton compte.',
      );
    }
    // Le check role est fait DANS issueTokensForUserId pour le flow
    // direct ; pour le flow 2FA on doit l'avancer ici car on emet pas
    // de tokens tant que le code n'est pas verifie.
    if (user.banned) {
      throw new UnauthorizedException('Compte banni.');
    }
    const ranks: Role[] = [
      Role.PLAYER,
      Role.WHITELISTED,
      Role.HELPER,
      Role.WHITELIST_REVIEWER,
      Role.MODERATOR,
      Role.ADMIN,
      Role.OWNER,
    ];
    if (ranks.indexOf(user.role) < ranks.indexOf(Role.HELPER)) {
      throw new UnauthorizedException(
        `Role insuffisant (${user.role}) pour acceder au panel staff.`,
      );
    }

    if (user.twoFactorEnabled) {
      const challenge = TwoFactorChallengeStore.create(user.id);
      return { kind: 'challenge', challenge };
    }

    const tokens = await this.auth.issueTokensForUserId(user.id, meta, {
      requireMinRole: Role.HELPER,
    });
    return { kind: 'tokens', tokens };
  }

  async unlink(userId: string): Promise<void> {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user) throw new NotFoundException('Utilisateur introuvable.');
    if (!user.discordUserId) {
      throw new BadRequestException('Aucun compte Discord lie.');
    }
    await this.prisma.user.update({
      where: { id: userId },
      data: {
        discordUserId: null,
        discordUsername: null,
        discordLinkedAt: null,
      },
    });
  }

  private async exchangeCode(
    code: string,
    redirectEnvKey: 'DISCORD_REDIRECT_URI' | 'DISCORD_STAFF_REDIRECT_URI',
  ): Promise<DiscordTokenResponse> {
    const clientId = this.requireEnv('DISCORD_CLIENT_ID');
    const clientSecret = this.requireEnv('DISCORD_CLIENT_SECRET');
    const redirectUri = this.requireEnv(redirectEnvKey);

    const body = new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      grant_type: 'authorization_code',
      code,
      redirect_uri: redirectUri,
    });

    const res = await fetch(`${DISCORD_API_BASE}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    });
    if (!res.ok) {
      const text = await res.text().catch(() => '');
      this.logger.warn(`Discord oauth2/token ${res.status} : ${text}`);
      throw new InternalServerErrorException(
        'Echec de l\'echange OAuth2 Discord.',
      );
    }
    return (await res.json()) as DiscordTokenResponse;
  }

  private async fetchProfile(accessToken: string): Promise<DiscordUserResponse> {
    const res = await fetch(`${DISCORD_API_BASE}/users/@me`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!res.ok) {
      const text = await res.text().catch(() => '');
      this.logger.warn(`Discord users/@me ${res.status} : ${text}`);
      throw new InternalServerErrorException(
        'Impossible de recuperer le profil Discord.',
      );
    }
    return (await res.json()) as DiscordUserResponse;
  }

  private requireEnv(key: string): string {
    const value = process.env[key];
    if (!value) {
      this.logger.error(`Variable d'env manquante : ${key}`);
      throw new InternalServerErrorException(
        `Configuration Discord incomplete (${key}).`,
      );
    }
    return value;
  }

  private gcStates() {
    const now = Date.now();
    for (const [state, payload] of this.states) {
      if (payload.expiresAt < now) this.states.delete(state);
    }
  }
}
