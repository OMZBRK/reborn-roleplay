import {
  BadRequestException,
  ConflictException,
  Injectable,
  Logger,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import * as OTPAuth from 'otpauth';
import * as QRCode from 'qrcode';
import { PrismaService } from '../prisma/prisma.service';

const ISSUER = 'Reborn Roleplay Staff';
const ALGORITHM = 'SHA1';
const DIGITS = 6;
const PERIOD = 30;
// +/- 1 step tolerance (30s avant ou apres) — couvre les drifts d'horloge
// raisonnables sans elargir trop la fenetre.
const WINDOW = 1;

/**
 * Cache RAM des secrets en attente de confirmation (enroll → confirm).
 * On ne persiste le secret en DB qu'apres confirmation reussie d'un
 * code TOTP, comme ca un enroll abandonne ne laisse pas un secret
 * orphelin pre-active.
 *
 * TTL 10 min — au-dela il faut recommencer.
 */
const PENDING_SECRETS = new Map<
  string,
  { secret: string; expiresAt: number }
>();
const PENDING_TTL_MS = 10 * 60_000;

function gcPending() {
  const now = Date.now();
  for (const [k, v] of PENDING_SECRETS) {
    if (v.expiresAt < now) PENDING_SECRETS.delete(k);
  }
}

@Injectable()
export class TwoFactorService {
  private readonly logger = new Logger(TwoFactorService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Genere un nouveau secret TOTP pour le user. Pas encore active —
   * il faut appeler `confirm` avec un code valide pour finaliser.
   */
  async enroll(userId: string): Promise<{
    secret: string;
    otpauthUri: string;
    qrDataUrl: string;
  }> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { id: true, minecraftUsername: true, twoFactorEnabled: true },
    });
    if (!user) throw new NotFoundException('User introuvable.');
    if (user.twoFactorEnabled) {
      throw new ConflictException(
        '2FA deja active. Desactive d\'abord avant un nouveau enroll.',
      );
    }
    gcPending();
    const secret = new OTPAuth.Secret({ size: 20 }); // 160 bits
    const totp = new OTPAuth.TOTP({
      issuer: ISSUER,
      label: user.minecraftUsername,
      algorithm: ALGORITHM,
      digits: DIGITS,
      period: PERIOD,
      secret,
    });
    const base32 = secret.base32;
    PENDING_SECRETS.set(userId, {
      secret: base32,
      expiresAt: Date.now() + PENDING_TTL_MS,
    });

    const otpauthUri = totp.toString();
    const qrDataUrl = await QRCode.toDataURL(otpauthUri, {
      errorCorrectionLevel: 'M',
      margin: 1,
      width: 256,
    });
    return { secret: base32, otpauthUri, qrDataUrl };
  }

  /**
   * Verifie un code 6-chiffres contre le secret en attente puis
   * persiste {twoFactorSecret, twoFactorEnabled=true} sur le User.
   * Le secret est stocke en clair dans la DB — pas chiffre dans le MVP.
   * (TODO: chiffrer avec une cle KMS-managed en prod.)
   */
  async confirm(userId: string, code: string): Promise<void> {
    gcPending();
    const pending = PENDING_SECRETS.get(userId);
    if (!pending) {
      throw new BadRequestException(
        'Aucun enroll en attente — appelle /enroll d\'abord.',
      );
    }
    if (!this.verifyCode(pending.secret, code)) {
      throw new UnauthorizedException('Code invalide.');
    }
    await this.prisma.user.update({
      where: { id: userId },
      data: {
        twoFactorSecret: pending.secret,
        twoFactorEnabled: true,
      },
    });
    PENDING_SECRETS.delete(userId);
    this.logger.log(`2FA active pour user ${userId}`);
  }

  /**
   * Desactive le 2FA apres verification d'un code (anti-takeover :
   * un attaquant qui a hijack la session ne peut pas desactiver le
   * 2FA sans le code TOTP).
   */
  async disable(userId: string, code: string): Promise<void> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { twoFactorSecret: true, twoFactorEnabled: true },
    });
    if (!user || !user.twoFactorEnabled || !user.twoFactorSecret) {
      throw new BadRequestException('2FA non actif.');
    }
    if (!this.verifyCode(user.twoFactorSecret, code)) {
      throw new UnauthorizedException('Code invalide.');
    }
    await this.prisma.user.update({
      where: { id: userId },
      data: { twoFactorSecret: null, twoFactorEnabled: false },
    });
    this.logger.log(`2FA desactive pour user ${userId}`);
  }

  /**
   * Verifie un code TOTP pour un user donne. Utilise par le flow login :
   * apres Discord OAuth, si twoFactorEnabled, on demande le code avant
   * d'emettre les tokens.
   */
  async verifyForLogin(userId: string, code: string): Promise<boolean> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { twoFactorSecret: true, twoFactorEnabled: true },
    });
    if (!user?.twoFactorEnabled || !user.twoFactorSecret) return false;
    return this.verifyCode(user.twoFactorSecret, code);
  }

  /** Status pour la page Settings. */
  async status(userId: string): Promise<{ enabled: boolean }> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { twoFactorEnabled: true },
    });
    return { enabled: user?.twoFactorEnabled ?? false };
  }

  private verifyCode(base32Secret: string, rawCode: string): boolean {
    const code = rawCode.replace(/\s+/g, '');
    if (!/^\d{6}$/.test(code)) return false;
    const totp = new OTPAuth.TOTP({
      issuer: ISSUER,
      algorithm: ALGORITHM,
      digits: DIGITS,
      period: PERIOD,
      secret: OTPAuth.Secret.fromBase32(base32Secret),
    });
    // delta=null si invalide, sinon nombre de steps de drift.
    const delta = totp.validate({ token: code, window: WINDOW });
    return delta !== null;
  }
}
