import {
  BadRequestException,
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Post,
  UseGuards,
} from '@nestjs/common';
import { IsString, MaxLength, MinLength } from 'class-validator';
import { CurrentUser } from './current-user.decorator';
import type { RequestUser } from './current-user.decorator';
import { JwtAuthGuard } from './jwt-auth.guard';
import { TwoFactorService } from './twofa.service';
import { AuthService } from './auth.service';
import { Role } from '@prisma/client';

class ConfirmDto {
  @IsString()
  @MinLength(6)
  @MaxLength(10)
  code!: string;
}

class VerifyChallengeDto {
  @IsString()
  @MinLength(16)
  @MaxLength(128)
  challenge!: string;

  @IsString()
  @MinLength(6)
  @MaxLength(10)
  code!: string;
}

/**
 * Endpoints 2FA TOTP staff.
 *
 *   POST /auth/2fa/enroll     (JWT) → genere secret + QR
 *   POST /auth/2fa/confirm    (JWT) → confirme avec code → active
 *   POST /auth/2fa/disable    (JWT) → desactive (requiert code)
 *   GET  /auth/2fa/status     (JWT) → {enabled}
 *
 *   POST /auth/2fa/verify     (PUBLIC) → echange challenge + code →
 *                                        emet les vrais tokens.
 *
 * Le flow login Discord staff (cf DiscordController.staffCallback) :
 *   - si user.twoFactorEnabled : redirect admin avec
 *     #challenge=<id> au lieu de #access=...&refresh=...
 *   - le panel detecte challenge, propose la page TOTP, POSTe vers
 *     /auth/2fa/verify avec le code → recoit les tokens.
 */
@Controller('auth/2fa')
export class TwoFactorController {
  constructor(
    private readonly twofa: TwoFactorService,
    private readonly auth: AuthService,
  ) {}

  @Get('status')
  @UseGuards(JwtAuthGuard)
  status(@CurrentUser() user: RequestUser) {
    return this.twofa.status(user.sub);
  }

  @Post('enroll')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  enroll(@CurrentUser() user: RequestUser) {
    return this.twofa.enroll(user.sub);
  }

  @Post('confirm')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.NO_CONTENT)
  async confirm(@CurrentUser() user: RequestUser, @Body() dto: ConfirmDto) {
    await this.twofa.confirm(user.sub, dto.code);
  }

  @Post('disable')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.NO_CONTENT)
  async disable(@CurrentUser() user: RequestUser, @Body() dto: ConfirmDto) {
    await this.twofa.disable(user.sub, dto.code);
  }

  /**
   * Endpoint public — echange un challenge + un code TOTP contre une
   * paire {access, refresh}. Le challenge a une TTL 5 min.
   */
  @Post('verify')
  @HttpCode(HttpStatus.OK)
  async verifyChallenge(@Body() dto: VerifyChallengeDto) {
    const userId = TwoFactorChallengeStore.consume(dto.challenge);
    if (!userId) {
      throw new BadRequestException('Challenge invalide ou expire.');
    }
    const ok = await this.twofa.verifyForLogin(userId, dto.code);
    if (!ok) {
      // On re-stocke le challenge pour permettre une 2e tentative —
      // sinon une erreur de frappe forcerait a refaire le login Discord
      // complet. TTL = celui d'origine.
      TwoFactorChallengeStore.create(userId, dto.challenge);
      throw new BadRequestException('Code invalide.');
    }
    // Le challenge est consume, on emet les tokens (requireMinRole=HELPER
    // refait le check role au cas ou le role aurait change pendant le
    // delai du challenge).
    return this.auth.issueTokensForUserId(userId, {}, {
      requireMinRole: Role.HELPER,
    });
  }
}

/**
 * Store en RAM des challenges 2FA en attente. Cle = challenge id
 * (random hex), valeur = {userId, expiresAt}. TTL 5 min.
 */
export class TwoFactorChallengeStore {
  private static readonly TTL_MS = 5 * 60_000;
  private static store = new Map<
    string,
    { userId: string; expiresAt: number }
  >();

  static create(userId: string, challengeId?: string): string {
    this.gc();
    const id =
      challengeId ??
      require('node:crypto').randomBytes(24).toString('hex');
    this.store.set(id, { userId, expiresAt: Date.now() + this.TTL_MS });
    return id;
  }

  /** Retourne le userId associe et supprime le challenge. */
  static consume(challengeId: string): string | null {
    this.gc();
    const found = this.store.get(challengeId);
    if (!found) return null;
    this.store.delete(challengeId);
    return found.userId;
  }

  private static gc() {
    const now = Date.now();
    for (const [k, v] of this.store) {
      if (v.expiresAt < now) this.store.delete(k);
    }
  }
}
