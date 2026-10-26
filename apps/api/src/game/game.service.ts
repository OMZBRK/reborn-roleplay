import { Injectable, Logger } from '@nestjs/common';
import type {
  AppStatus,
  CharacterSex,
  CharacterType,
  Role,
} from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { calculerSuivi, SANS_CLAN } from '../whitelist/whitelist.rules';
import { ROLES_STAFF } from './game-acces.regles';

/**
 * Vue candidature exposee a ShinobiCore pour le wizard de creation de perso.
 *
 * Depuis le 24/10 (CANDIDATURE_ET_DOUANE.md Q4, Q13 et complément) : aucun clan
 * dans la candidature, tous se rejoignent en jeu, en session de clan. Le
 * premier personnage arrive « sans clan » (`clan` = « Autre », le clan
 * personnalise du mod), avec son nom de famille libre pre-rempli et
 * modifiable (`familyName`), comme le prenom. Seuls le village, le clan
 * « Autre » et l'âge (`age`) restent verrouillés ; le sexe reste libre (Q13),
 * simplement suggéré (`sex`). Le type Ninja / Civil (`type`) et l'identifiant
 * de la candidature (`applicationId`) marquent le premier personnage.
 *
 * `found` = candidature ACCEPTEE (phase ACCEPTE) : une candidature en cours ne
 * pre-remplit rien.
 */
export interface CandidatureView {
  found: boolean;
  /** Prenom RP valide (whitelist firstName), pre-rempli et modifiable. */
  name?: string;
  /** Toujours « Autre » (SANS_CLAN) : le clan se rejoint en jeu, jamais par la candidature. */
  clan?: string;
  /**
   * Nom de famille libre de la candidature v2, pre-rempli et modifiable. null
   * pour l'ancien formulaire (v1), ou le champ portait souvent un clan.
   */
  familyName?: string | null;
  /** Village RP valide (whitelist village). */
  village?: string;
  /**
   * Âge du personnage (whitelist characterAge), pré-rempli et verrouillé à la
   * création (Q13). null si la candidature n'en porte pas (ancien formulaire).
   */
  age?: number | null;
  /**
   * Identifiant de la candidature : ShinobiCore l'écrit dans la marque
   * `application` du premier personnage, que la synchronisation remonte en
   * `GameCharacter.applicationId` (« Mes personnages », dossier d'entrée, §4.4).
   */
  applicationId?: string;
  /** Type du personnage (`NINJA` / `CIVIL`), null pour l'ancien formulaire (= Ninja en jeu). */
  type?: CharacterType | null;
  /** Sexe déclaré (`HOMME` / `FEMME`), simple suggestion : le sexe reste libre en jeu (Q13). */
  sex?: CharacterSex | null;
  /** true si l'utilisateur est staff (choix libre, rien n'est verrouille). */
  staff?: boolean;
  /** Prevalidation HRP acceptee (via test oral). */
  hrpApproved?: boolean;
  /** Validation RP acceptee. */
  rpApproved?: boolean;
}

/** Compte lu pour la vue candidature. */
export interface CompteCandidature {
  role: Role;
  oralPassedAt: Date | null;
  whitelistRejectedAt: Date | null;
}

/** Candidature lue pour la vue (champs de WhitelistApplication). */
export interface CandidaturePourVue {
  id: string;
  status: AppStatus;
  hrpStatus: AppStatus;
  rpStatus: AppStatus;
  oralStatus: AppStatus;
  oralDecidedAt: Date | null;
  reviewedAt: Date | null;
  firstName: string;
  lastName: string;
  village: string;
  characterAge: number | null;
  characterType: CharacterType | null;
  characterSex: CharacterSex | null;
  formVersion: number;
}

/**
 * Vue candidature d'un compte, sans requête : partagée par
 * GET /game/candidature et le bloc `candidature` de GET /game/acces.
 */
export function construireVueCandidature(
  compte: CompteCandidature,
  app: CandidaturePourVue | null,
  maintenant: Date = new Date(),
): CandidatureView {
  const staff = ROLES_STAFF.has(compte.role);
  const { phase } = calculerSuivi({
    role: compte.role,
    oralPassedAt: compte.oralPassedAt,
    whitelistRejectedAt: compte.whitelistRejectedAt,
    app,
    reservation: null,
    now: maintenant,
  });
  if (!app || phase !== 'ACCEPTE') {
    // Pas de candidature acceptee, mais on connait le grade : staff = libre quand meme.
    return { found: false, staff };
  }

  return {
    found: true,
    name: app.firstName,
    clan: SANS_CLAN,
    familyName: app.formVersion === 2 ? app.lastName : null,
    village: app.village,
    age: app.characterAge,
    applicationId: app.id,
    type: app.characterType ?? null,
    sex: app.characterSex ?? null,
    staff,
    hrpApproved: app.hrpStatus === 'APPROVED',
    rpApproved: app.rpStatus === 'APPROVED',
  };
}

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
    return construireVueCandidature(user, app);
  }

  /**
   * Bukkit renvoie l'UUID avec tirets ; on tente l'exact puis une forme
   * normalisee (avec/sans tirets) pour absorber les differences de format.
   * Public : les routes signees du jeu (GameDouaneService) l'utilisent aussi.
   */
  async findUserByMcUuid(mcUuid: string) {
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
