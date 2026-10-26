import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  AppStatus,
  DouaneReservationStatut,
  DouaneSeanceStatut,
  OralVia,
  type User,
} from '@prisma/client';
import { AuditService, type AuditSource } from '../audit/audit.service';
import type { EvenementPresence } from '../douane/dto/douane.dto';
import { DouaneOralService } from '../douane/douane-oral.service';
import {
  calculerPhase,
  estActif,
  estExaminateur,
  lireRetardMax,
} from '../douane/douane.regles';
import { DouaneService } from '../douane/douane.service';
import { PrismaService } from '../prisma/prisma.service';
import type {
  DecisionDouaneJeuDto,
  EvenementPresenceJeu,
  PresenceDouaneJeuDto,
} from './dto/game-douane.dto';
import { evaluerAcces, fenetreIso } from './game-acces.regles';
import {
  dossierDouaneVue,
  type AccesJeuVue,
  type DecisionDouaneJeuVue,
  type DossierDouaneVue,
  type PresenceDouaneJeuVue,
} from './game-douane.vues';
import { GameService, construireVueCandidature } from './game.service';

/**
 * Source des entrées d'audit écrites pour le serveur de jeu (§3.9 : `source:
 * "game"`). `AuditSource` (audit.service.ts) ne la liste pas encore ; la
 * colonne est un texte libre et le panel affiche toute valeur.
 */
const SOURCE_AUDIT_JEU = 'game' as AuditSource;

/** Une sortie (`depart`) peut viser la dernière place tranchée jusqu'à 2 h après la décision. */
const DEPART_APRES_DECISION_MS = 2 * 3600 * 1000;

const MSG_DOUANE_COUPEE =
  'La douane en jeu est coupée (REBORN_DOUANE_ENABLED) : oral hors jeu au panel.';
const MSG_EXAMINATEUR =
  'Examinateur non reconnu : il faut le rôle Relecteur whitelist (ou plus) sur le panel, avec ce compte Minecraft lié.';
const MSG_PAS_DE_PASSAGE = 'Aucun passage de douane en cours pour ce joueur.';

/** Présence au sens du panel (heures d'arrivée, d'appel, de sortie). */
const EVENEMENTS: Partial<Record<EvenementPresenceJeu, EvenementPresence>> = {
  arrivee: 'ARRIVEE',
  appel: 'APPEL',
  depart: 'DEPART',
};
/** Sorties sans décision (§3.10) : la place passe CANCELLED avec son motif. */
const ANNULATIONS: Partial<Record<EvenementPresenceJeu, 'RETRAIT' | 'REPORT'>> =
  {
    retrait: 'RETRAIT',
    report: 'REPORT',
  };

/**
 * Routes signées du serveur de jeu (CANDIDATURE_ET_DOUANE.md §3.3, §3.6, §3.7,
 * §3.9) : aiguillage à la connexion, fiche résumée pour l'examinateur, décision
 * de l'oral, présence au hall.
 *
 * La décision passe par `DouaneOralService.decideOral` (`via: DOUANE`) : rien
 * n'est dupliqué. Ce service n'ajoute que ce que le jeu doit prouver :
 * l'examinateur a le rôle web WHITELIST_REVIEWER ou plus avec CE compte
 * Minecraft lié, et la place tranchée est celle en cours.
 */
@Injectable()
export class GameDouaneService {
  private readonly logger = new Logger(GameDouaneService.name);
  private readonly douaneActive: boolean;
  private readonly retardMaxMin: number;
  private readonly adminBaseUrl: string | null;

  constructor(
    config: ConfigService,
    private readonly prisma: PrismaService,
    private readonly game: GameService,
    private readonly oral: DouaneOralService,
    private readonly douane: DouaneService,
    private readonly audit: AuditService,
  ) {
    this.douaneActive = estActif(config.get<string>('REBORN_DOUANE_ENABLED'));
    this.retardMaxMin = lireRetardMax(
      config.get<string>('DOUANE_RETARD_MAX_MIN'),
    );
    const base = config.get<string>('ADMIN_BASE_URL')?.trim();
    this.adminBaseUrl = base ? base.replace(/\/+$/, '') : null;
  }

  // ═══════════════════════════ Aiguillage ═══════════════════════════

  /**
   * GET /v1/game/acces/:mcUuid — portée du joueur à sa connexion. Même règle
   * que le jeton de jeu (game-acces.regles.ts). Répond toujours 200 : un compte
   * inconnu est une portée `aucune`.
   */
  async acces(mcUuid: string, serveur: string): Promise<AccesJeuVue> {
    const maintenant = new Date();
    const compte = await this.game.findUserByMcUuid(mcUuid);
    if (!compte) {
      const a = evaluerAcces({
        compte: null,
        candidature: null,
        reservation: null,
        maintenant,
        douaneActive: this.douaneActive,
        retardMaxMin: this.retardMaxMin,
      });
      this.logger.debug(`accès ${mcUuid} (${serveur}) → aucune, ${a.raison}`);
      return {
        ...a,
        pseudo: null,
        douaneActive: this.douaneActive,
        candidature: { found: false },
        douane: null,
      };
    }

    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId: compte.id },
      select: {
        id: true,
        status: true,
        hrpStatus: true,
        rpStatus: true,
        oralStatus: true,
        oralDecidedAt: true,
        reviewedAt: true,
        writtenApprovedAt: true,
        firstName: true,
        lastName: true,
        village: true,
        characterAge: true,
        characterType: true,
        characterSex: true,
        formVersion: true,
      },
    });
    // Même requête que le jeton de jeu (play.service.ts › assertDouaneAccess).
    const place = app
      ? await this.prisma.douaneReservation.findFirst({
          where: {
            userId: compte.id,
            applicationId: app.id,
            statut: DouaneReservationStatut.BOOKED,
            seance: {
              statut: {
                in: [DouaneSeanceStatut.OUVERTE, DouaneSeanceStatut.FERMEE],
              },
            },
          },
          orderBy: { convocationAt: 'asc' },
          select: {
            id: true,
            seanceId: true,
            convocationAt: true,
            presentAt: true,
          },
        })
      : null;

    const a = evaluerAcces({
      compte,
      candidature: app,
      reservation: place,
      maintenant,
      douaneActive: this.douaneActive,
      retardMaxMin: this.retardMaxMin,
    });
    this.logger.debug(
      `accès ${compte.minecraftUsername} (${serveur}) → ${a.portee}, ${a.raison}`,
    );
    return {
      ...a,
      pseudo: compte.minecraftUsername,
      douaneActive: this.douaneActive,
      candidature: construireVueCandidature(compte, app, maintenant),
      douane:
        a.portee === 'douane' && app && place
          ? {
              reservationId: place.id,
              seanceId: place.seanceId,
              convocationAt: place.convocationAt.toISOString(),
              ...fenetreIso(place.convocationAt, this.retardMaxMin),
              arriveeAt: place.presentAt?.toISOString() ?? null,
              deuxiemePassage: app.oralStatus === AppStatus.NEEDS_REVISION,
              village: app.village,
            }
          : null,
    };
  }

  // ═══════════════════════════ Dossier ═══════════════════════════

  /**
   * GET /v1/game/douane/dossier/:mcUuid?examinateur=<uuid> — fiche RÉSUMÉE du
   * candidat en cours de douane (§3.7), jamais la voix. Seulement pour un
   * examinateur reconnu, et seulement pendant le passage (phase
   * DOUANE_RESERVEE) ; chaque lecture est inscrite à l'audit.
   */
  async dossier(
    mcUuidCandidat: string,
    mcUuidExaminateur: string,
    serveur: string,
  ): Promise<DossierDouaneVue> {
    this.exigerDouaneActive();
    const examinateur = await this.exigerExaminateur(mcUuidExaminateur);
    const candidat = await this.game.findUserByMcUuid(mcUuidCandidat);
    if (!candidat) {
      throw new NotFoundException('Aucun compte Reborn pour ce joueur.');
    }
    if (candidat.banned) {
      throw new ForbiddenException('Compte banni : pas de passage de douane.');
    }
    if (candidat.id === examinateur.id) {
      throw new ForbiddenException(
        'Tu ne peux pas ouvrir ton propre dossier de douane.',
      );
    }
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId: candidat.id },
    });
    if (!app) {
      throw new NotFoundException('Aucune candidature pour ce joueur.');
    }
    // Même place que la décision (decideOral) : la plus proche encore réservée.
    const place = await this.prisma.douaneReservation.findFirst({
      where: { applicationId: app.id, statut: DouaneReservationStatut.BOOKED },
      orderBy: { convocationAt: 'asc' },
      select: {
        id: true,
        seanceId: true,
        convocationAt: true,
        presentAt: true,
        calledAt: true,
      },
    });
    const phase = calculerPhase({
      role: candidat.role,
      oralPassedAt: candidat.oralPassedAt,
      whitelistRejectedAt: candidat.whitelistRejectedAt,
      application: app,
      reservationActive: place !== null,
    });
    if (phase !== 'DOUANE_RESERVEE' || !place) {
      throw new ConflictException(MSG_PAS_DE_PASSAGE);
    }

    await this.audit.log({
      actorId: examinateur.id,
      action: 'douane.dossier.read',
      targetUserId: candidat.id,
      targetEntity: `whitelist:${app.id}`,
      metadata: { serveur, reservationId: place.id },
      source: SOURCE_AUDIT_JEU,
    });
    return dossierDouaneVue(app, candidat, place, {
      maintenant: new Date(),
      retardMaxMin: this.retardMaxMin,
      adminBaseUrl: this.adminBaseUrl,
    });
  }

  // ═══════════════════════════ Décision ═══════════════════════════

  /**
   * POST /v1/game/douane/decision — décision de l'oral passée en jeu (§3.9).
   * Une nouvelle tentative d'une décision déjà enregistrée (même place, même
   * examinateur, même résultat) répond 200 avec `dejaTraitee: true`.
   */
  async decision(
    dto: DecisionDouaneJeuDto,
    serveur: string,
  ): Promise<DecisionDouaneJeuVue> {
    this.exigerDouaneActive();
    const examinateur = await this.exigerExaminateur(
      dto.mcUuidExaminateur,
      serveur,
    );
    // §3.9 : chaque appel à l'audit, refus compris, dès que l'examinateur est reconnu.
    const trace = (
      issue: string,
      cible: { userId?: string; applicationId?: string } = {},
      extra: Record<string, unknown> = {},
    ) =>
      this.audit.log({
        actorId: examinateur.id,
        action: 'douane.jeu.decision',
        targetUserId: cible.userId,
        targetEntity: cible.applicationId
          ? `whitelist:${cible.applicationId}`
          : undefined,
        metadata: {
          serveur,
          reservationId: dto.reservationId,
          resultat: dto.resultat,
          issue,
          ...extra,
        },
        source: SOURCE_AUDIT_JEU,
      });

    const candidat = await this.game.findUserByMcUuid(dto.mcUuidCandidat);
    if (!candidat) {
      await trace(
        'candidat_inconnu',
        {},
        { mcUuidCandidat: dto.mcUuidCandidat },
      );
      throw new NotFoundException('Aucun compte Reborn pour ce joueur.');
    }
    // decideOral ne regarde pas le bannissement du candidat : sans ce contrôle,
    // un compte banni pendant son passage serait promu WHITELISTED.
    if (candidat.banned) {
      await trace('candidat_banni', { userId: candidat.id });
      throw new ForbiddenException('Compte banni : rien à trancher.');
    }
    if (candidat.id === examinateur.id) {
      await trace('propre_candidature', { userId: candidat.id });
      throw new ForbiddenException(
        'Tu ne peux pas trancher ta propre candidature.',
      );
    }
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId: candidat.id },
      select: { id: true },
    });
    if (!app) {
      await trace('sans_candidature', { userId: candidat.id });
      throw new NotFoundException('Aucune candidature pour ce joueur.');
    }
    const cible = { userId: candidat.id, applicationId: app.id };

    try {
      const vue = await this.oral.decideOral(
        app.id,
        examinateur.id,
        dto.resultat,
        dto.motif,
        dto.notesPrivees,
        {
          via: OralVia.DOUANE,
          source: SOURCE_AUDIT_JEU,
          reservationId: dto.reservationId,
        },
      );
      await trace('enregistree', cible);
      this.logger.log(
        `douane (${serveur}) : ${candidat.minecraftUsername} → ${vue.resultat} par ${examinateur.minecraftUsername}`,
      );
      return {
        ok: true,
        dejaTraitee: false,
        resultat: vue.resultat,
        phase: vue.phase,
        applicationId: vue.applicationId,
        reservationId: vue.reservation?.id ?? dto.reservationId,
        prochaineSeanceDes: vue.prochaineSeanceDes,
        canReapplyAt: vue.canReapplyAt,
      };
    } catch (err) {
      if (err instanceof ConflictException) {
        const deja = await this.decisionDejaEnregistree(
          app.id,
          examinateur.id,
          dto,
        );
        if (deja) {
          await trace('deja_traitee', cible);
          return deja;
        }
      }
      await trace('refusee', cible, { erreur: (err as Error).message });
      throw err;
    }
  }

  /** La même décision (place, examinateur, résultat) est-elle déjà enregistrée ? */
  private async decisionDejaEnregistree(
    applicationId: string,
    examinateurUserId: string,
    dto: DecisionDouaneJeuDto,
  ): Promise<DecisionDouaneJeuVue | null> {
    const place = await this.prisma.douaneReservation.findFirst({
      where: {
        id: dto.reservationId,
        applicationId,
        statut: DouaneReservationStatut.DONE,
        resultat: dto.resultat,
        examinateurUserId,
      },
      select: { id: true },
    });
    if (!place) return null;
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
      include: {
        user: {
          select: { role: true, oralPassedAt: true, whitelistRejectedAt: true },
        },
      },
    });
    if (!app) return null;
    const phase = calculerPhase({
      role: app.user.role,
      oralPassedAt: app.user.oralPassedAt,
      whitelistRejectedAt: app.user.whitelistRejectedAt,
      application: app,
      reservationActive: false,
    });
    return {
      ok: true,
      dejaTraitee: true,
      resultat: dto.resultat,
      phase,
      applicationId,
      reservationId: place.id,
      prochaineSeanceDes: null,
      canReapplyAt: null,
    };
  }

  // ═══════════════════════════ Présence ═══════════════════════════

  /**
   * POST /v1/game/douane/presence — arrivée au hall, appel au bureau, sortie
   * (§3.6, lot facultatif). Écrit `presentAt`, `calledAt`, `departAt` par
   * `DouaneService.presence`, la même règle que la saisie au panel.
   *
   * `retrait` (« Retirer de la douane ») et `report` (séance reportée faute
   * d'examinateur) annulent la place réservée avec son motif, sans pénalité
   * (§3.10) ; ShinobiCore les envoie après `depart`. Le retrait exige un
   * examinateur reconnu (rôle web revérifié) et un motif, montré au candidat.
   */
  async presence(
    dto: PresenceDouaneJeuDto,
    serveur: string,
  ): Promise<PresenceDouaneJeuVue> {
    this.exigerDouaneActive();
    const annulation = ANNULATIONS[dto.evenement];
    // Retrait : examinateur et motif contrôlés avant tout effet.
    let examinateur: User | null = null;
    if (annulation === 'RETRAIT') {
      if (!dto.mcUuidExaminateur) {
        throw new BadRequestException(
          'mcUuidExaminateur : obligatoire pour un retrait.',
        );
      }
      if (!dto.motif?.trim()) {
        throw new BadRequestException(
          'motif : obligatoire pour un retrait (il est montré au candidat).',
        );
      }
      examinateur = await this.exigerExaminateur(dto.mcUuidExaminateur);
    }
    const candidat = await this.game.findUserByMcUuid(dto.mcUuid);
    if (!candidat) {
      throw new NotFoundException('Aucun compte Reborn pour ce joueur.');
    }

    let place: { id: string } | null;
    if (dto.reservationId) {
      place = await this.prisma.douaneReservation.findFirst({
        where: { id: dto.reservationId, userId: candidat.id },
        select: { id: true },
      });
    } else {
      place = await this.prisma.douaneReservation.findFirst({
        where: { userId: candidat.id, statut: DouaneReservationStatut.BOOKED },
        orderBy: { convocationAt: 'asc' },
        select: { id: true },
      });
      if (!place && dto.evenement === 'depart') {
        place = await this.prisma.douaneReservation.findFirst({
          where: {
            userId: candidat.id,
            statut: DouaneReservationStatut.DONE,
            decidedAt: { gte: new Date(Date.now() - DEPART_APRES_DECISION_MS) },
          },
          orderBy: { decidedAt: 'desc' },
          select: { id: true },
        });
      }
    }
    if (!place) throw new ConflictException(MSG_PAS_DE_PASSAGE);

    if (annulation) {
      const motif = annulation === 'RETRAIT' ? dto.motif?.trim() : undefined;
      const annulee = await this.douane.annulerPlaceDepuisJeu(
        place.id,
        annulation,
        motif,
      );
      void this.audit.log({
        actorId: (examinateur ?? candidat).id,
        action: `douane.presence.${dto.evenement}`,
        targetUserId: candidat.id,
        targetEntity: `douane-reservation:${place.id}`,
        metadata: { serveur, ...(motif ? { motif } : {}) },
        source: SOURCE_AUDIT_JEU,
      });
      this.logger.log(
        `douane (${serveur}) : ${candidat.minecraftUsername} → ${dto.evenement}` +
          (examinateur ? ` par ${examinateur.minecraftUsername}` : ''),
      );
      return {
        ok: true,
        reservationId: annulee.id,
        presentAt: annulee.presentAt,
        calledAt: annulee.calledAt,
        departAt: annulee.departAt,
      };
    }

    const evenement = EVENEMENTS[dto.evenement];
    if (!evenement) {
      throw new BadRequestException(
        'evenement : arrivee, appel, depart, retrait ou report.',
      );
    }
    const vue = await this.douane.presence(place.id, evenement);

    // L'appel est l'acte de l'examinateur ; l'arrivée et la sortie, celles du candidat.
    let acteur: Pick<User, 'id'> = candidat;
    if (dto.evenement === 'appel' && dto.mcUuidExaminateur) {
      const ex = await this.game.findUserByMcUuid(dto.mcUuidExaminateur);
      if (ex && !ex.banned && estExaminateur(ex.role)) acteur = ex;
    }
    void this.audit.log({
      actorId: acteur.id,
      action: `douane.presence.${dto.evenement}`,
      targetUserId: candidat.id,
      targetEntity: `douane-reservation:${place.id}`,
      metadata: { serveur },
      source: SOURCE_AUDIT_JEU,
    });

    return {
      ok: true,
      reservationId: vue.id,
      presentAt: vue.presentAt,
      calledAt: vue.calledAt,
      departAt: vue.departAt,
    };
  }

  // ═══════════════════════════ Contrôles communs ═══════════════════════════

  private exigerDouaneActive(): void {
    if (!this.douaneActive) throw new ForbiddenException(MSG_DOUANE_COUPEE);
  }

  /**
   * Examinateur reconnu : un compte Reborn dont le `minecraftUuid` est celui
   * qui joue, non banni, rôle web WHITELIST_REVIEWER ou plus (§3.8). Un staff
   * sur un second compte Minecraft est donc refusé. Le refus d'un compte
   * existant est inscrit à l'audit.
   */
  private async exigerExaminateur(
    mcUuid: string,
    serveur?: string,
  ): Promise<User> {
    const compte = await this.game.findUserByMcUuid(mcUuid);
    if (!compte || compte.banned || !estExaminateur(compte.role)) {
      this.logger.warn(
        `examinateur refusé : ${compte ? compte.minecraftUsername : mcUuid} (${compte ? compte.role : 'aucun compte'})`,
      );
      if (compte && serveur) {
        void this.audit.log({
          actorId: compte.id,
          action: 'douane.jeu.decision',
          metadata: { serveur, issue: 'examinateur_refuse', role: compte.role },
          source: SOURCE_AUDIT_JEU,
        });
      }
      throw new ForbiddenException(MSG_EXAMINATEUR);
    }
    return compte;
  }
}
