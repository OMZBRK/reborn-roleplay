import { Injectable, Logger } from '@nestjs/common';
import { Role } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';

/**
 * Vue candidature exposee a ShinobiCore pour le wizard de creation de perso.
 *
 * Le launcher capture, dans la whitelist L5 : `firstName` (prenom du perso),
 * `lastName` (le CLAN — le champ est un menu deroulant de clans cote launcher),
 * et `village`. ShinobiCore lit ces valeurs VALIDEES pour verrouiller le clan
 * et le village du wizard (sauf staff = libre).
 */
export interface CandidatureView {
  found: boolean;
  /** Prenom RP valide (whitelist firstName). */
  name?: string;
  /** Clan RP valide (whitelist lastName = menu clan cote launcher). */
  clan?: string;
  /** Village RP valide (whitelist village). */
  village?: string;
  /** true si l'utilisateur est staff (choix libre, rien n'est verrouille). */
  staff?: boolean;
  /** Prevalidation HRP acceptee (via test oral). */
  hrpApproved?: boolean;
  /** Validation RP acceptee. */
  rpApproved?: boolean;
}

const STAFF_ROLES = new Set<Role>([
  Role.HELPER,
  Role.MODERATOR,
  Role.WHITELIST_REVIEWER,
  Role.ADMIN,
  Role.OWNER,
]);

@Injectable()
export class GameService {
  private readonly logger = new Logger(GameService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Renvoie la candidature validee liee a un compte Minecraft (par UUID).
   * `found=false` si aucun compte / aucune candidature (degradation propre :
   * ShinobiCore ne verrouille alors rien).
   */
  async candidatureByMcUuid(mcUuid: string): Promise<CandidatureView> {
    const user = await this.findUserByMcUuid(mcUuid);
    if (!user) {
      return { found: false };
    }

    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId: user.id },
    });
    const staff = STAFF_ROLES.has(user.role);

    if (!app) {
      // Pas de candidature mais on connait le grade : staff = libre quand meme.
      return { found: false, staff };
    }

    return {
      found: true,
      name: app.firstName,
      clan: app.lastName,
      village: app.village,
      staff,
      hrpApproved: app.hrpStatus === 'APPROVED',
      rpApproved: app.rpStatus === 'APPROVED',
    };
  }

  /**
   * Bukkit renvoie l'UUID avec tirets ; on tente l'exact puis une forme
   * normalisee (avec/sans tirets) pour absorber les differences de format.
   */
  private async findUserByMcUuid(mcUuid: string) {
    const exact = await this.prisma.user.findUnique({
      where: { minecraftUuid: mcUuid },
    });
    if (exact) return exact;

    const alt = mcUuid.includes('-')
      ? mcUuid.replace(/-/g, '')
      : this.dashify(mcUuid);
    if (alt && alt !== mcUuid) {
      return this.prisma.user.findUnique({ where: { minecraftUuid: alt } });
    }
    return null;
  }

  /** 32 hex → forme 8-4-4-4-12 ; renvoie l'entree telle quelle si longueur != 32. */
  private dashify(raw: string): string {
    if (raw.length !== 32) return raw;
    return `${raw.slice(0, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}-${raw.slice(20)}`;
  }
}
