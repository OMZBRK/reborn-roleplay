import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
  Optional,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  AppStatus,
  DouaneKind,
  DouaneReservationStatut,
  DouaneSeanceStatut,
  Prisma,
} from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import { PrismaService } from '../prisma/prisma.service';
import { WebhooksService } from '../webhooks/webhooks.service';
import { WhitelistMessagesService } from '../whitelist/whitelist-messages.service';
import {
  calculerSuivi,
  formatDateParis,
  placeManquee,
} from '../whitelist/whitelist.rules';
import {
  AnnulerReservationDto,
  AnnulerSeanceDto,
  CloturerSeanceDto,
  CreerSeanceDto,
  EvenementPresence,
  ListerSeancesQueryDto,
  ModifierSeanceDto,
} from './dto/douane.dto';
import {
  AVANCE_ENTREE_MIN,
  DUREE_SEANCE_BORNES_MIN,
  PHASES_RESERVABLES,
  attribuerConvocation,
  calculerPhase,
  debutDuLendemainParis,
  estActif,
  estExaminateur,
  etatFenetre,
  fenetreEntree,
  heureParis,
  jourParis,
  lireRetardMax,
  raisonPhase,
} from './douane.regles';
import {
  OPTIONS_TRANSACTION,
  traduireConflitTransaction,
  verrouillerCandidature,
  verrouillerSeance,
} from './douane.verrous';
import {
  DernierePlaceVue,
  EtatDouaneCandidatVue,
  ReservationCandidatVue,
  ReservationStaffVue,
  SeanceCandidatVue,
  SeanceStaffDetailVue,
  SeanceStaffVue,
  reservationCandidatVue,
  reservationStaffVue,
  seanceCandidatVue,
  seanceStaffVue,
} from './douane.vues';

const MIN = 60 * 1000;
/** Une séance s'ouvre au plus 6 mois à l'avance (garde-fou contre une faute de frappe dans l'année). */
const HORIZON_SEANCE_MS = 183 * 24 * 60 * MIN;
/** Les places encore BOOKED 15 min après la fin de la séance sont soldées par la tâche planifiée. */
const MARGE_FIN_SEANCE_MIN = 15;
/** Rappel envoyé dans l'heure qui précède la convocation. */
const RAPPEL_AVANT_MIN = 60;
/** Motif d'une place libérée par le serveur de jeu, faute d'examinateur (§3.10). */
const MOTIF_REPORT = 'Séance reportée : aucun examinateur présent';
/** Places comptées dans la capacité. */
const STATUTS_PLACE_PRISE: DouaneReservationStatut[] = [
  DouaneReservationStatut.BOOKED,
  DouaneReservationStatut.DONE,
];

const COMPTE_SELECT = {
  id: true,
  minecraftUsername: true,
  discordUsername: true,
} as const;

const INCLUDE_SEANCE_STAFF = {
  examinateurs: { select: { ...COMPTE_SELECT, role: true } },
  openedBy: { select: COMPTE_SELECT },
} satisfies Prisma.DouaneSeanceInclude;

const INCLUDE_RESERVATION_STAFF = {
  user: { select: COMPTE_SELECT },
  application: {
    select: {
      id: true,
      firstName: true,
      lastName: true,
      village: true,
      oralStatus: true,
    },
  },
  examinateur: { select: COMPTE_SELECT },
} satisfies Prisma.DouaneReservationInclude;

/**
 * Séances de douane avec capacité et réservations (CANDIDATURE_ET_DOUANE.md
 * §3.3, §3.6, §3.8). Remplace les créneaux d'un candidat (`OralSlotsService`),
 * qui restent en service pendant la transition.
 *
 * La décision d'oral vit à part (`DouaneOralService`) : le panel l'appelle
 * aujourd'hui (oral hors jeu), le serveur de jeu l'appellera au lot B.
 */
@Injectable()
export class DouaneService {
  private readonly logger = new Logger(DouaneService.name);
  /** Retard maximal après l'heure de convocation, en minutes (DOUANE_RETARD_MAX_MIN, défaut 30). */
  readonly retardMaxMin: number;
  /** REBORN_DOUANE_ENABLED, lu comme le jeton de jeu : la douane se passe en jeu. */
  readonly douaneEnJeu: boolean;

  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
    private readonly messages: WhitelistMessagesService,
    config: ConfigService,
    /** MP Discord au candidat (lot H) ; absent dans les tests unitaires. */
    @Optional() private readonly webhooks?: WebhooksService,
  ) {
    this.retardMaxMin = lireRetardMax(
      config.get<string>('DOUANE_RETARD_MAX_MIN'),
    );
    this.douaneEnJeu = estActif(config.get<string>('REBORN_DOUANE_ENABLED'));
  }

  // ═══════════════════════════ Candidat ═══════════════════════════

  /**
   * État de la douane pour le launcher : phase, réservation en cours avec sa
   * fenêtre d'entrée, et séances ouvertes avec leurs places. Les séances ne
   * sont listées que si le candidat peut réserver ou changer de séance.
   */
  async etatCandidat(userId: string): Promise<EtatDouaneCandidatVue> {
    const maintenant = new Date();
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: {
        role: true,
        oralPassedAt: true,
        whitelistRejectedAt: true,
        whitelistApp: {
          select: {
            id: true,
            status: true,
            hrpStatus: true,
            rpStatus: true,
            oralStatus: true,
            writtenApprovedAt: true,
            oralDecidedAt: true,
            reviewedAt: true,
          },
        },
      },
    });
    if (!user) throw new NotFoundException('Compte introuvable.');
    const app = user.whitelistApp;

    const reservation = app
      ? await this.prisma.douaneReservation.findFirst({
          where: { userId, statut: DouaneReservationStatut.BOOKED },
          orderBy: { convocationAt: 'asc' },
          include: { seance: { select: { startAt: true, endAt: true } } },
        })
      : null;

    // Même calcul que GET /whitelist/me : refus encore en cours après un retrait
    // (whitelistRejectedAt), et place dont l'entrée est fermée sans arrivée
    // comptée comme manquée sans attendre la tâche planifiée.
    const suivi = calculerSuivi({
      role: user.role,
      oralPassedAt: user.oralPassedAt,
      whitelistRejectedAt: user.whitelistRejectedAt,
      app: app
        ? {
            status: app.status,
            hrpStatus: app.hrpStatus,
            rpStatus: app.rpStatus,
            oralStatus: app.oralStatus,
            oralDecidedAt: app.oralDecidedAt,
            reviewedAt: app.reviewedAt,
          }
        : null,
      reservation,
      now: maintenant,
      retardMaxMin: this.retardMaxMin,
      placeFermeeManquee: true,
      douaneEnJeu: this.douaneEnJeu,
    });
    const phase = suivi.phase;
    const placeEnCours = phase === 'DOUANE_RESERVEE' ? reservation : null;
    const peutReserver = PHASES_RESERVABLES.includes(phase);
    const peutChanger = phase === 'DOUANE_RESERVEE';
    const auPlusTot = this.premiereSeancePermise(app, maintenant);

    const seances =
      peutReserver || peutChanger
        ? await this.seancesOuvertes(maintenant, auPlusTot)
        : [];

    let raison: string | null = null;
    if (!peutReserver && !peutChanger) {
      const reprise = suivi.canReapplyAt ? new Date(suivi.canReapplyAt) : null;
      raison =
        phase === 'REFUSE' && reprise
          ? reprise.getTime() > maintenant.getTime()
            ? `Ta candidature a été refusée : nouvelle candidature complète possible le ${formatDateParis(reprise)}.`
            : 'Ta candidature a été refusée : tu peux envoyer une nouvelle candidature complète.'
          : raisonPhase(phase) || null;
    }

    return {
      phase,
      peutReserver,
      peutChanger,
      raison,
      prochaineSeanceDes: auPlusTot?.toISOString() ?? null,
      reservation: placeEnCours
        ? reservationCandidatVue(placeEnCours, maintenant, this.retardMaxMin)
        : null,
      derniereReservation:
        app && peutReserver
          ? await this.dernierePlacePerdue(
              userId,
              app,
              reservation &&
                placeManquee(reservation, maintenant, this.retardMaxMin)
                ? reservation
                : null,
            )
          : null,
      seances,
      regles: {
        avanceEntreeMin: AVANCE_ENTREE_MIN,
        retardMaxMin: this.retardMaxMin,
        douaneEnJeu: this.douaneEnJeu,
      },
    };
  }

  /**
   * Dernière place perdue depuis la dernière décision (écrit ou oral) : la
   * place encore BOOKED dont l'entrée est fermée sans arrivée (manquée, pas
   * encore soldée), sinon la dernière MISSED ou CANCELLED créée depuis.
   */
  private async dernierePlacePerdue(
    userId: string,
    app: {
      id: string;
      reviewedAt: Date | null;
      oralDecidedAt: Date | null;
      writtenApprovedAt: Date | null;
    },
    placeFermee: { convocationAt: Date } | null,
  ): Promise<DernierePlaceVue | null> {
    const vue = (
      statut: 'MISSED' | 'CANCELLED',
      convocationAt: Date,
      motifAnnulation: string | null,
    ): DernierePlaceVue => ({
      statut,
      convocationAt: convocationAt.toISOString(),
      fermeAt: fenetreEntree(
        convocationAt,
        this.retardMaxMin,
      ).fermeAt.toISOString(),
      motifAnnulation,
    });
    if (placeFermee) return vue('MISSED', placeFermee.convocationAt, null);

    const decisions = [app.reviewedAt, app.oralDecidedAt, app.writtenApprovedAt]
      .filter((d): d is Date => d instanceof Date)
      .map((d) => d.getTime());
    const depuis =
      decisions.length > 0 ? new Date(Math.max(...decisions)) : null;
    const derniere = await this.prisma.douaneReservation.findFirst({
      where: {
        userId,
        applicationId: app.id,
        statut: {
          in: [
            DouaneReservationStatut.MISSED,
            DouaneReservationStatut.CANCELLED,
          ],
        },
        ...(depuis ? { createdAt: { gt: depuis } } : {}),
      },
      orderBy: { createdAt: 'desc' },
      select: { statut: true, convocationAt: true, motifAnnulation: true },
    });
    if (!derniere) return null;
    return vue(
      derniere.statut === DouaneReservationStatut.MISSED
        ? 'MISSED'
        : 'CANCELLED',
      derniere.convocationAt,
      derniere.motifAnnulation,
    );
  }

  /**
   * Réserve une place. Tout se joue dans une transaction, sous deux verrous
   * pris dans cet ordre :
   *  1. la candidature : une seule réservation BOOKED par compte, et pas de
   *     réservation pendant qu'une décision d'oral s'écrit ;
   *  2. la séance : le décompte des places et l'insertion sont atomiques, la
   *     capacité tient même sous des réservations simultanées.
   * L'heure de convocation est calculée à ce moment-là (vagues de 30 min).
   *
   * @param remplacer true : la place déjà réservée est rendue dans la même
   *   transaction, seulement si la nouvelle est obtenue.
   */
  async reserver(
    userId: string,
    seanceId: string,
    remplacer = false,
  ): Promise<ReservationCandidatVue> {
    const maintenant = new Date();
    const appId = await this.prisma.whitelistApplication.findUnique({
      where: { userId },
      select: { id: true },
    });
    if (!appId) {
      throw new ForbiddenException(raisonPhase('BROUILLON'));
    }

    const { creee, remplacee } = await this.prisma
      .$transaction(async (tx) => {
        await verrouillerCandidature(tx, appId.id);

        const app = await tx.whitelistApplication.findUnique({
          where: { id: appId.id },
          include: {
            user: {
              select: {
                role: true,
                oralPassedAt: true,
                banned: true,
                whitelistRejectedAt: true,
              },
            },
          },
        });
        if (!app) throw new ForbiddenException(raisonPhase('BROUILLON'));
        if (app.user.banned) {
          throw new ForbiddenException(
            'Ton compte est suspendu : pas de réservation possible.',
          );
        }

        let active = await tx.douaneReservation.findFirst({
          where: { userId, statut: DouaneReservationStatut.BOOKED },
          orderBy: { convocationAt: 'asc' },
        });
        // Fenêtre d'entrée passée sans arrivée enregistrée : la place est manquée
        // (pas de limite de séances manquées, Q12), le candidat peut réserver de nouveau.
        if (
          active &&
          !active.presentAt &&
          etatFenetre(active.convocationAt, maintenant, this.retardMaxMin) ===
            'FERMEE'
        ) {
          await tx.douaneReservation.update({
            where: { id: active.id },
            data: { statut: DouaneReservationStatut.MISSED },
          });
          active = null;
        }

        const phase = calculerPhase({
          role: app.user.role,
          oralPassedAt: app.user.oralPassedAt,
          whitelistRejectedAt: app.user.whitelistRejectedAt,
          application: app,
          reservationActive: active !== null,
        });
        if (active) {
          // Une place restée réservée sur un compte déjà accepté ou refusé ne permet pas d'en changer.
          if (phase !== 'DOUANE_RESERVEE') {
            throw new ForbiddenException(raisonPhase(phase));
          }
          if (active.seanceId === seanceId) {
            throw new ConflictException(
              'Tu as déjà une place dans cette séance.',
            );
          }
          if (!remplacer) {
            throw new ConflictException(
              'Tu as déjà une place réservée. Annule-la ou change de séance.',
            );
          }
        } else if (!PHASES_RESERVABLES.includes(phase)) {
          throw new ForbiddenException(raisonPhase(phase));
        }

        const auPlusTot = this.premiereSeancePermise(app, maintenant);

        await verrouillerSeance(tx, seanceId);
        const seance = await tx.douaneSeance.findUnique({
          where: { id: seanceId },
        });
        if (!seance || seance.kind !== DouaneKind.WHITELIST) {
          throw new NotFoundException('Séance introuvable.');
        }
        if (seance.statut !== DouaneSeanceStatut.OUVERTE) {
          throw new ConflictException(
            "Cette séance n'accepte plus de réservation.",
          );
        }
        if (seance.endAt.getTime() <= maintenant.getTime()) {
          throw new ConflictException('Cette séance est terminée.');
        }
        if (auPlusTot && seance.startAt.getTime() < auPlusTot.getTime()) {
          throw new ConflictException(
            `Après une douane « à revoir », la prochaine séance doit avoir lieu à partir du ${jourParis(auPlusTot)}.`,
          );
        }

        const prises = await tx.douaneReservation.findMany({
          where: { seanceId, statut: { in: STATUTS_PLACE_PRISE } },
          select: { convocationAt: true },
        });
        if (prises.length >= seance.capacite) {
          throw new ConflictException(
            'Cette séance est complète : choisis-en une autre.',
          );
        }
        const convocationAt = attribuerConvocation(
          seance,
          prises.map((p) => p.convocationAt),
          maintenant,
          this.retardMaxMin,
        );
        if (!convocationAt) {
          throw new ConflictException(
            'Cette séance est trop avancée pour réserver : choisis-en une autre.',
          );
        }

        if (active) {
          await tx.douaneReservation.update({
            where: { id: active.id },
            data: {
              statut: DouaneReservationStatut.CANCELLED,
              annuleeAt: maintenant,
              motifAnnulation: 'Changement de séance par le candidat',
            },
          });
        }
        const nouvelle = await tx.douaneReservation.create({
          data: { seanceId, applicationId: app.id, userId, convocationAt },
          include: { seance: { select: { startAt: true, endAt: true } } },
        });
        return {
          creee: nouvelle,
          remplacee: active ? { id: active.id } : null,
        };
      }, OPTIONS_TRANSACTION)
      .catch(traduireConflitTransaction);

    this.logger.log(
      `douane : place ${creee.id} (séance ${seanceId}) pour ${userId}` +
        (remplacee ? `, remplace ${remplacee.id}` : ''),
    );
    const f = fenetreEntree(creee.convocationAt, this.retardMaxMin);
    void this.messages.postSystemMessage(
      appId.id,
      `📅 Place réservée à la douane : ${jourParis(creee.convocationAt)}, convocation à ${heureParis(creee.convocationAt)}. ` +
        `Entrée possible de ${heureParis(f.ouvreAt)} à ${heureParis(f.fermeAt)}.`,
    );
    return reservationCandidatVue(creee, maintenant, this.retardMaxMin);
  }

  /** Le candidat annule sa propre place (« jusqu'au dernier moment »). */
  async annulerMaReservation(
    userId: string,
    reservationId: string,
  ): Promise<{ ok: true }> {
    const res = await this.prisma.douaneReservation.updateMany({
      where: {
        id: reservationId,
        userId,
        statut: DouaneReservationStatut.BOOKED,
      },
      data: {
        statut: DouaneReservationStatut.CANCELLED,
        annuleeAt: new Date(),
        motifAnnulation: 'Annulée par le candidat',
      },
    });
    if (res.count === 0) {
      throw new NotFoundException(
        'Réservation introuvable, déjà annulée ou déjà passée.',
      );
    }
    const r = await this.prisma.douaneReservation.findUnique({
      where: { id: reservationId },
      select: { applicationId: true },
    });
    if (r) {
      void this.messages.postSystemMessage(
        r.applicationId,
        'Réservation de douane annulée. Tu peux choisir une autre séance dans le launcher.',
      );
    }
    return { ok: true };
  }

  // ═══════════════════════════ Staff : séances ═══════════════════════════

  async listerSeances(query: ListerSeancesQueryDto): Promise<SeanceStaffVue[]> {
    const depuis = query.depuis
      ? new Date(query.depuis)
      : new Date(Date.now() - 24 * 60 * MIN);
    const where: Prisma.DouaneSeanceWhereInput = {
      kind: DouaneKind.WHITELIST,
      endAt: { gte: depuis },
      ...(query.jusqua ? { startAt: { lte: new Date(query.jusqua) } } : {}),
      ...(query.statut ? { statut: query.statut } : {}),
    };
    const seances = await this.prisma.douaneSeance.findMany({
      where,
      orderBy: { startAt: 'asc' },
      take: query.take ?? 100,
      include: INCLUDE_SEANCE_STAFF,
    });
    const statuts = await this.statutsParSeance(seances.map((s) => s.id));
    return seances.map((s) => seanceStaffVue(s, statuts.get(s.id) ?? []));
  }

  async detailSeance(id: string): Promise<SeanceStaffDetailVue> {
    const maintenant = new Date();
    const s = await this.prisma.douaneSeance.findUnique({
      where: { id },
      include: {
        ...INCLUDE_SEANCE_STAFF,
        reservations: {
          orderBy: [{ convocationAt: 'asc' }, { createdAt: 'asc' }],
          include: INCLUDE_RESERVATION_STAFF,
        },
      },
    });
    if (!s) throw new NotFoundException('Séance introuvable.');
    const manquees = await this.seancesManqueesParCompte(
      s.reservations.map((r) => r.userId),
    );
    return {
      ...seanceStaffVue(
        s,
        s.reservations.map((r) => r.statut),
      ),
      reservations: s.reservations.map((r) =>
        reservationStaffVue(
          r,
          manquees.get(r.userId) ?? 0,
          maintenant,
          this.retardMaxMin,
        ),
      ),
    };
  }

  /** Ouvre une séance : début, fin, capacité, examinateurs prévus (§3.3, Q5). */
  async creerSeance(
    staffUserId: string,
    dto: CreerSeanceDto,
  ): Promise<SeanceStaffDetailVue> {
    const startAt = new Date(dto.startAt);
    const endAt = new Date(dto.endAt);
    this.validerHoraires(startAt, endAt, new Date(), true);
    const examinateurIds = await this.validerExaminateurs(
      dto.examinateurIds ?? [],
    );

    const s = await this.prisma.douaneSeance.create({
      data: {
        kind: DouaneKind.WHITELIST,
        startAt,
        endAt,
        capacite: dto.capacite,
        openedByUserId: staffUserId,
        examinateurs: { connect: examinateurIds.map((id) => ({ id })) },
      },
      select: { id: true },
    });
    this.logger.log(
      `douane : séance ${s.id} ouverte par ${staffUserId} (${dto.capacite} places)`,
    );
    void this.audit.log({
      actorId: staffUserId,
      action: 'douane.seance.create',
      targetEntity: `douane-seance:${s.id}`,
      metadata: {
        startAt: startAt.toISOString(),
        endAt: endAt.toISOString(),
        capacite: dto.capacite,
        examinateurIds,
      },
      source: 'panel',
    });
    return this.detailSeance(s.id);
  }

  /**
   * Modifie une séance ouverte ou fermée. L'horaire ne bouge plus dès qu'un
   * candidat est convoqué (ses heures de convocation en dépendent) ; la
   * capacité ne descend pas sous le nombre de places prises.
   */
  async modifierSeance(
    staffUserId: string,
    id: string,
    dto: ModifierSeanceDto,
  ): Promise<SeanceStaffDetailVue> {
    const examinateurIds =
      dto.examinateurIds !== undefined
        ? await this.validerExaminateurs(dto.examinateurIds)
        : null;

    await this.prisma
      .$transaction(async (tx) => {
        await verrouillerSeance(tx, id);
        const s = await tx.douaneSeance.findUnique({ where: { id } });
        if (!s) throw new NotFoundException('Séance introuvable.');
        if (
          s.statut === DouaneSeanceStatut.TERMINEE ||
          s.statut === DouaneSeanceStatut.ANNULEE
        ) {
          throw new ConflictException(
            'Cette séance est close : elle ne se modifie plus.',
          );
        }
        const prises = await tx.douaneReservation.count({
          where: { seanceId: id, statut: { in: STATUTS_PLACE_PRISE } },
        });

        const startAt = dto.startAt ? new Date(dto.startAt) : s.startAt;
        const endAt = dto.endAt ? new Date(dto.endAt) : s.endAt;
        const horaireChange =
          startAt.getTime() !== s.startAt.getTime() ||
          endAt.getTime() !== s.endAt.getTime();
        if (horaireChange) {
          if (prises > 0) {
            throw new ConflictException(
              "Des candidats sont déjà convoqués : l'horaire ne change plus. Annule la séance (ils sont prévenus) et ouvres-en une autre.",
            );
          }
          this.validerHoraires(startAt, endAt, new Date(), false);
        }
        const capacite = dto.capacite ?? s.capacite;
        if (capacite < prises) {
          throw new ConflictException(
            `${prises} place${prises > 1 ? 's sont' : ' est'} déjà prise${prises > 1 ? 's' : ''} : la capacité ne peut pas descendre en dessous.`,
          );
        }

        await tx.douaneSeance.update({
          where: { id },
          data: {
            startAt,
            endAt,
            capacite,
            ...(dto.statut ? { statut: dto.statut } : {}),
            ...(examinateurIds
              ? {
                  examinateurs: { set: examinateurIds.map((e) => ({ id: e })) },
                }
              : {}),
          },
        });
      }, OPTIONS_TRANSACTION)
      .catch(traduireConflitTransaction);

    void this.audit.log({
      actorId: staffUserId,
      action: 'douane.seance.update',
      targetEntity: `douane-seance:${id}`,
      metadata: { ...dto },
      source: 'panel',
    });
    return this.detailSeance(id);
  }

  /**
   * Clôture avec notes privées. Les places restées BOOKED sont soldées :
   * arrivée enregistrée → annulée sans pénalité (« séance close avant ton
   * passage ») ; sinon → manquée.
   */
  async cloturerSeance(
    staffUserId: string,
    id: string,
    dto: CloturerSeanceDto,
  ): Promise<SeanceStaffDetailVue> {
    const maintenant = new Date();
    const soldees = await this.prisma
      .$transaction(async (tx): Promise<SoldePlaces> => {
        await verrouillerSeance(tx, id);
        const s = await tx.douaneSeance.findUnique({ where: { id } });
        if (!s) throw new NotFoundException('Séance introuvable.');
        if (s.statut === DouaneSeanceStatut.ANNULEE) {
          throw new ConflictException('Séance annulée : rien à clore.');
        }
        if (s.statut === DouaneSeanceStatut.TERMINEE) {
          throw new ConflictException('Séance déjà close.');
        }
        const solde = await this.solderPlacesRestantes(
          tx,
          { seanceId: id },
          maintenant,
        );
        await tx.douaneSeance.update({
          where: { id },
          data: {
            statut: DouaneSeanceStatut.TERMINEE,
            clotureeAt: maintenant,
            notes: dto.notes?.trim() || null,
          },
        });
        return solde;
      }, OPTIONS_TRANSACTION)
      .catch(traduireConflitTransaction);

    this.prevenirPlacesSoldees(soldees);
    void this.audit.log({
      actorId: staffUserId,
      action: 'douane.seance.cloture',
      targetEntity: `douane-seance:${id}`,
      metadata: {
        manquees: soldees.absentes.length,
        annuleesSansPenalite: soldees.presentes.length,
      },
      source: 'panel',
    });
    return this.detailSeance(id);
  }

  /** Annule une séance : toutes les places BOOKED sont annulées et les candidats prévenus. */
  async annulerSeance(
    staffUserId: string,
    id: string,
    dto: AnnulerSeanceDto,
  ): Promise<SeanceStaffDetailVue> {
    const maintenant = new Date();
    const motif = dto.motif.trim();
    const touchees = await this.prisma
      .$transaction(async (tx) => {
        await verrouillerSeance(tx, id);
        const s = await tx.douaneSeance.findUnique({ where: { id } });
        if (!s) throw new NotFoundException('Séance introuvable.');
        if (s.statut === DouaneSeanceStatut.TERMINEE) {
          throw new ConflictException(
            "Séance déjà close : elle ne s'annule plus.",
          );
        }
        if (s.statut === DouaneSeanceStatut.ANNULEE) {
          throw new ConflictException('Séance déjà annulée.');
        }
        const places = await tx.douaneReservation.findMany({
          where: { seanceId: id, statut: DouaneReservationStatut.BOOKED },
          select: {
            id: true,
            applicationId: true,
            userId: true,
            convocationAt: true,
          },
        });
        await tx.douaneReservation.updateMany({
          where: {
            id: { in: places.map((p) => p.id) },
            statut: DouaneReservationStatut.BOOKED,
          },
          data: {
            statut: DouaneReservationStatut.CANCELLED,
            annuleeAt: maintenant,
            motifAnnulation: `Séance annulée : ${motif}`,
          },
        });
        await tx.douaneSeance.update({
          where: { id },
          data: {
            statut: DouaneSeanceStatut.ANNULEE,
            annuleeAt: maintenant,
            motifAnnulation: motif,
          },
        });
        return places;
      }, OPTIONS_TRANSACTION)
      .catch(traduireConflitTransaction);

    for (const p of touchees) {
      void this.messages.postSystemMessage(
        p.applicationId,
        `⚠️ Ta séance de douane du ${jourParis(p.convocationAt)} (${heureParis(p.convocationAt)}) est annulée par le staff. ` +
          `Motif : ${motif}\nRéserve une autre séance dans le launcher.`,
      );
      this.mpCandidat(
        p.userId,
        'Séance de douane annulée',
        `Ta séance de douane du ${jourParis(p.convocationAt)} (${heureParis(p.convocationAt)}) est annulée par le staff. ` +
          `Motif : ${motif}\nRéserve une autre séance dans le launcher.`,
        p.applicationId,
      );
    }
    this.logger.log(
      `douane : séance ${id} annulée par ${staffUserId}, ${touchees.length} candidat(s) prévenu(s)`,
    );
    void this.audit.log({
      actorId: staffUserId,
      action: 'douane.seance.cancel',
      targetEntity: `douane-seance:${id}`,
      metadata: { motif, candidatsPrevenus: touchees.length },
      source: 'panel',
    });
    return this.detailSeance(id);
  }

  // ═══════════════════════════ Staff : réservations ═══════════════════════════

  /**
   * Présence saisie au panel (en attendant `POST /game/douane/presence`,
   * lot B) : arrivée au hall, appel au bureau, sortie.
   */
  async presence(
    id: string,
    evenement: EvenementPresence,
  ): Promise<ReservationStaffVue> {
    const maintenant = new Date();
    const r = await this.prisma.douaneReservation.findUnique({ where: { id } });
    if (!r) throw new NotFoundException('Réservation introuvable.');
    if (
      r.statut !== DouaneReservationStatut.BOOKED &&
      r.statut !== DouaneReservationStatut.DONE
    ) {
      throw new ConflictException(
        'Réservation annulée ou manquée : la présence ne se modifie plus.',
      );
    }
    const data: Prisma.DouaneReservationUpdateInput =
      evenement === 'ARRIVEE'
        ? { presentAt: r.presentAt ?? maintenant, departAt: null }
        : evenement === 'APPEL'
          ? { presentAt: r.presentAt ?? maintenant, calledAt: maintenant }
          : { departAt: maintenant };
    const maj = await this.prisma.douaneReservation.update({
      where: { id },
      data,
      include: INCLUDE_RESERVATION_STAFF,
    });
    const manquees = await this.seancesManqueesParCompte([maj.userId]);
    return reservationStaffVue(
      maj,
      manquees.get(maj.userId) ?? 0,
      maintenant,
      this.retardMaxMin,
    );
  }

  /** Marque une place réservée comme manquée (visible au panel ; pas de limite, Q12). */
  async marquerAbsent(
    staffUserId: string,
    id: string,
  ): Promise<ReservationStaffVue> {
    const r = await this.basculerPlace(id, {
      statut: DouaneReservationStatut.MISSED,
    });
    void this.messages.postSystemMessage(
      r.applicationId,
      'Séance de douane manquée. Tu peux réserver une nouvelle séance dans le launcher.',
    );
    void this.audit.log({
      actorId: staffUserId,
      action: 'douane.reservation.missed',
      targetUserId: r.userId,
      targetEntity: `douane-reservation:${id}`,
      metadata: { seanceId: r.seanceId },
      source: 'panel',
    });
    return r;
  }

  /** « Retirer de la douane » : annule la place avec un motif montré au candidat. */
  async annulerReservationStaff(
    staffUserId: string,
    id: string,
    dto: AnnulerReservationDto,
  ): Promise<ReservationStaffVue> {
    const motif = dto.motif.trim();
    const r = await this.basculerPlace(id, {
      statut: DouaneReservationStatut.CANCELLED,
      annuleeAt: new Date(),
      motifAnnulation: motif,
    });
    void this.messages.postSystemMessage(
      r.applicationId,
      `⚠️ Ta place à la douane est annulée par le staff. Motif : ${motif}\nTu peux réserver une nouvelle séance dans le launcher.`,
    );
    this.mpCandidat(
      r.userId,
      'Place de douane annulée',
      `Ta place à la douane est annulée par le staff. Motif : ${motif}\nTu peux réserver une nouvelle séance dans le launcher.`,
      r.applicationId,
    );
    void this.audit.log({
      actorId: staffUserId,
      action: 'douane.reservation.cancel',
      targetUserId: r.userId,
      targetEntity: `douane-reservation:${id}`,
      metadata: { seanceId: r.seanceId, motif },
      source: 'panel',
    });
    return r;
  }

  /**
   * Place annulée depuis le serveur de jeu (POST /game/douane/presence, §3.10) :
   * `RETRAIT` (« Retirer de la douane » : motif de l'examinateur, montré au
   * candidat) ou `REPORT` (séance reportée faute d'examinateur). Sans pénalité
   * dans les deux cas : la place n'est pas « manquée », le candidat peut
   * réserver une autre séance. L'audit est écrit par l'appelant (acteur et
   * serveur connus de lui).
   */
  async annulerPlaceDepuisJeu(
    id: string,
    evenement: 'RETRAIT' | 'REPORT',
    motif?: string,
  ): Promise<ReservationStaffVue & { applicationId: string; userId: string }> {
    const texte = motif?.trim() ?? '';
    if (evenement === 'RETRAIT' && texte.length === 0) {
      throw new BadRequestException(
        'motif : obligatoire pour un retrait (il est montré au candidat).',
      );
    }
    const r = await this.basculerPlace(id, {
      statut: DouaneReservationStatut.CANCELLED,
      annuleeAt: new Date(),
      motifAnnulation: evenement === 'RETRAIT' ? texte : MOTIF_REPORT,
    });
    void this.messages.postSystemMessage(
      r.applicationId,
      evenement === 'RETRAIT'
        ? `⚠️ Tu as été retiré de la douane par l'examinateur. Motif : ${texte}\nTu peux réserver une nouvelle séance dans le launcher.`
        : "La séance de douane est reportée : aucun examinateur n'était présent. Réserve une nouvelle séance dans le launcher : cette place ne compte pas comme manquée.",
    );
    this.mpCandidat(
      r.userId,
      evenement === 'RETRAIT'
        ? 'Retiré de la douane'
        : 'Séance de douane reportée',
      evenement === 'RETRAIT'
        ? `Tu as été retiré de la douane. Motif : ${texte}\nTu peux réserver une nouvelle séance dans le launcher.`
        : "Ta séance de douane est reportée : aucun examinateur n'était présent. Réserve une nouvelle séance dans le launcher ; cette place ne compte pas comme manquée.",
      r.applicationId,
    );
    return r;
  }

  /** Bloc « Douane » de la fiche candidature (panel, lot G) : phase, oral, historique des places. */
  async douaneDeLaCandidature(applicationId: string) {
    const maintenant = new Date();
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
      select: {
        id: true,
        userId: true,
        status: true,
        hrpStatus: true,
        rpStatus: true,
        oralStatus: true,
        writtenApprovedAt: true,
        writtenStaffNotes: true,
        oralDecidedAt: true,
        oralVia: true,
        oralReason: true,
        oralNotes: true,
        oralExaminer: { select: COMPTE_SELECT },
        user: {
          select: { role: true, oralPassedAt: true, whitelistRejectedAt: true },
        },
      },
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');
    const reservations = await this.prisma.douaneReservation.findMany({
      where: { applicationId },
      orderBy: { createdAt: 'desc' },
      include: {
        ...INCLUDE_RESERVATION_STAFF,
        seance: { select: { startAt: true, endAt: true, statut: true } },
      },
    });
    const manquees = reservations.filter(
      (r) => r.statut === DouaneReservationStatut.MISSED,
    ).length;
    const phase = calculerPhase({
      role: app.user.role,
      oralPassedAt: app.user.oralPassedAt,
      whitelistRejectedAt: app.user.whitelistRejectedAt,
      application: app,
      reservationActive: reservations.some(
        (r) => r.statut === DouaneReservationStatut.BOOKED,
      ),
    });
    return {
      applicationId: app.id,
      userId: app.userId,
      phase,
      writtenApprovedAt: app.writtenApprovedAt?.toISOString() ?? null,
      writtenStaffNotes: app.writtenStaffNotes,
      oralPassedAt: app.user.oralPassedAt?.toISOString() ?? null,
      oral: {
        status: app.oralStatus,
        via: app.oralVia,
        decidedAt: app.oralDecidedAt?.toISOString() ?? null,
        examinateur: app.oralExaminer
          ? {
              id: app.oralExaminer.id,
              minecraftUsername: app.oralExaminer.minecraftUsername,
              discordUsername: app.oralExaminer.discordUsername,
            }
          : null,
        reason: app.oralReason,
        notes: app.oralNotes,
      },
      reservations: reservations.map((r) => ({
        ...reservationStaffVue(r, manquees, maintenant, this.retardMaxMin),
        seance: {
          startAt: r.seance.startAt.toISOString(),
          endAt: r.seance.endAt.toISOString(),
          statut: r.seance.statut,
        },
      })),
    };
  }

  // ═══════════════════════════ Tâches planifiées ═══════════════════════════

  // Appelées toutes les 5 min par DouaneTachesService (@nestjs/schedule).

  /** Places encore BOOKED 15 min après la fin de leur séance. Retourne le nombre soldé. */
  async solderSeancesPassees(maintenant: Date): Promise<number> {
    const limite = new Date(maintenant.getTime() - MARGE_FIN_SEANCE_MIN * MIN);
    const solde = await this.solderPlacesRestantes(
      this.prisma,
      { seance: { endAt: { lt: limite } } },
      maintenant,
    );
    this.prevenirPlacesSoldees(solde);
    return solde.presentes.length + solde.absentes.length;
  }

  /**
   * Rappel dans l'heure qui précède la convocation, une seule fois
   * (`rappelEnvoyeAt`). Message système dans le fil (le launcher l'affiche) et
   * MP Discord du bot si le compte Discord est lié (lot H).
   */
  async envoyerRappels(maintenant: Date): Promise<number> {
    const cibles = await this.prisma.douaneReservation.findMany({
      where: {
        statut: DouaneReservationStatut.BOOKED,
        rappelEnvoyeAt: null,
        convocationAt: {
          gt: maintenant,
          lte: new Date(maintenant.getTime() + RAPPEL_AVANT_MIN * MIN),
        },
        seance: {
          statut: {
            in: [DouaneSeanceStatut.OUVERTE, DouaneSeanceStatut.FERMEE],
          },
        },
      },
      select: {
        id: true,
        applicationId: true,
        userId: true,
        convocationAt: true,
      },
    });
    let envoyes = 0;
    for (const c of cibles) {
      const pris = await this.prisma.douaneReservation.updateMany({
        where: {
          id: c.id,
          rappelEnvoyeAt: null,
          statut: DouaneReservationStatut.BOOKED,
        },
        data: { rappelEnvoyeAt: maintenant },
      });
      if (pris.count === 0) continue;
      const f = fenetreEntree(c.convocationAt, this.retardMaxMin);
      const texte =
        `ta séance de douane commence à ${heureParis(c.convocationAt)}. ` +
        `Entrée possible de ${heureParis(f.ouvreAt)} à ${heureParis(f.fermeAt)}.`;
      void this.messages.postSystemMessage(
        c.applicationId,
        `⏰ Rappel : ${texte}`,
      );
      this.mpCandidat(
        c.userId,
        'Rappel : séance de douane',
        `Rappel : ${texte}\nLe bouton « Passer la douane » du launcher s'active à l'ouverture de l'entrée.`,
        c.applicationId,
      );
      envoyes += 1;
    }
    return envoyes;
  }

  // ═══════════════════════════ Interne ═══════════════════════════

  /**
   * MP Discord au candidat (lot H : rappel 1 h avant, séance annulée, retrait,
   * report), en plus du message système du fil, qui reste la référence. Parti
   * seulement si le compte Discord est lié ; le bot ne le joint que s'il
   * partage un serveur avec lui. Un échec est journalisé, sans suite.
   */
  private mpCandidat(
    userId: string,
    sujet: string,
    contenu: string,
    applicationId: string,
  ): void {
    const webhooks = this.webhooks;
    if (!webhooks) return;
    void (async () => {
      const u = await this.prisma.user.findUnique({
        where: { id: userId },
        select: { discordUserId: true, minecraftUsername: true },
      });
      if (!u?.discordUserId) return;
      await webhooks.directMessage({
        discordUserId: u.discordUserId,
        context: { kind: 'douane', entityId: applicationId, subject: sujet },
        fromPseudo: u.minecraftUsername,
        content: contenu,
      });
    })().catch((err) =>
      this.logger.warn(
        `MP douane non transmis (${userId}) : ${(err as Error).message}`,
      ),
    );
  }

  /** Après une douane « à revoir » : minuit du lendemain de la décision (Q7), si c'est encore à venir. */
  private premiereSeancePermise(
    app: { oralStatus: AppStatus; oralDecidedAt: Date | null } | null,
    maintenant: Date,
  ): Date | null {
    if (
      !app ||
      app.oralStatus !== AppStatus.NEEDS_REVISION ||
      !app.oralDecidedAt
    )
      return null;
    const des = debutDuLendemainParis(app.oralDecidedAt);
    return des.getTime() > maintenant.getTime() ? des : null;
  }

  private async seancesOuvertes(
    maintenant: Date,
    auPlusTot: Date | null,
  ): Promise<SeanceCandidatVue[]> {
    const seances = await this.prisma.douaneSeance.findMany({
      where: {
        kind: DouaneKind.WHITELIST,
        statut: DouaneSeanceStatut.OUVERTE,
        endAt: { gt: maintenant },
        ...(auPlusTot ? { startAt: { gte: auPlusTot } } : {}),
      },
      orderBy: { startAt: 'asc' },
      take: 60,
      include: { _count: { select: { examinateurs: true } } },
    });
    const prises = await this.prisesParSeance(seances.map((s) => s.id));
    return (
      seances
        .map((s) =>
          seanceCandidatVue(
            s,
            s._count.examinateurs,
            prises.get(s.id) ?? [],
            maintenant,
            this.retardMaxMin,
          ),
        )
        // Une séance trop avancée (plus aucune vague attribuable) n'est plus proposée.
        .filter((v) => v.complet || v.convocationIndicative !== null)
    );
  }

  /** Heures de convocation des places prises (BOOKED, DONE), par séance. */
  private async prisesParSeance(ids: string[]): Promise<Map<string, Date[]>> {
    const out = new Map<string, Date[]>();
    if (ids.length === 0) return out;
    const rows = await this.prisma.douaneReservation.findMany({
      where: { seanceId: { in: ids }, statut: { in: STATUTS_PLACE_PRISE } },
      select: { seanceId: true, convocationAt: true },
    });
    for (const r of rows) {
      const l = out.get(r.seanceId) ?? [];
      l.push(r.convocationAt);
      out.set(r.seanceId, l);
    }
    return out;
  }

  private async statutsParSeance(
    ids: string[],
  ): Promise<Map<string, DouaneReservationStatut[]>> {
    const out = new Map<string, DouaneReservationStatut[]>();
    if (ids.length === 0) return out;
    const rows = await this.prisma.douaneReservation.findMany({
      where: { seanceId: { in: ids } },
      select: { seanceId: true, statut: true },
    });
    for (const r of rows) {
      const l = out.get(r.seanceId) ?? [];
      l.push(r.statut);
      out.set(r.seanceId, l);
    }
    return out;
  }

  /** Séances manquées par compte (Q12 : visibles au panel, sans limite). */
  private async seancesManqueesParCompte(
    userIds: string[],
  ): Promise<Map<string, number>> {
    const out = new Map<string, number>();
    const uniques = [...new Set(userIds)];
    if (uniques.length === 0) return out;
    const rows = await this.prisma.douaneReservation.findMany({
      where: {
        userId: { in: uniques },
        statut: DouaneReservationStatut.MISSED,
      },
      select: { userId: true },
    });
    for (const r of rows) out.set(r.userId, (out.get(r.userId) ?? 0) + 1);
    return out;
  }

  /**
   * Solde les places encore BOOKED qui correspondent au filtre : arrivée
   * enregistrée → CANCELLED sans pénalité ; sinon → MISSED.
   */
  private async solderPlacesRestantes(
    client: Prisma.TransactionClient | PrismaService,
    filtre: Prisma.DouaneReservationWhereInput,
    maintenant: Date,
  ): Promise<SoldePlaces> {
    const places = await client.douaneReservation.findMany({
      where: { ...filtre, statut: DouaneReservationStatut.BOOKED },
      select: { id: true, applicationId: true, presentAt: true },
    });
    const presentes = places.filter((p) => p.presentAt !== null);
    const absentes = places.filter((p) => p.presentAt === null);
    if (presentes.length > 0) {
      await client.douaneReservation.updateMany({
        where: {
          id: { in: presentes.map((p) => p.id) },
          statut: DouaneReservationStatut.BOOKED,
        },
        data: {
          statut: DouaneReservationStatut.CANCELLED,
          annuleeAt: maintenant,
          motifAnnulation: 'Séance terminée avant le passage',
        },
      });
    }
    if (absentes.length > 0) {
      await client.douaneReservation.updateMany({
        where: {
          id: { in: absentes.map((p) => p.id) },
          statut: DouaneReservationStatut.BOOKED,
        },
        data: { statut: DouaneReservationStatut.MISSED },
      });
    }
    return {
      presentes: presentes.map((p) => p.applicationId),
      absentes: absentes.map((p) => p.applicationId),
    };
  }

  private prevenirPlacesSoldees(solde: SoldePlaces): void {
    for (const appId of solde.presentes) {
      void this.messages.postSystemMessage(
        appId,
        "La séance de douane s'est terminée avant ton passage. Réserve une nouvelle séance dans le launcher : cette place ne compte pas comme manquée.",
      );
    }
    for (const appId of solde.absentes) {
      void this.messages.postSystemMessage(
        appId,
        'Séance de douane manquée. Tu peux réserver une nouvelle séance dans le launcher.',
      );
    }
  }

  /** Fait passer une place BOOKED à un autre statut (staff), et renvoie sa vue. */
  private async basculerPlace(
    id: string,
    data: Prisma.DouaneReservationUpdateManyMutationInput,
  ): Promise<ReservationStaffVue & { applicationId: string; userId: string }> {
    const res = await this.prisma.douaneReservation.updateMany({
      where: { id, statut: DouaneReservationStatut.BOOKED },
      data,
    });
    if (res.count === 0) {
      const existe = await this.prisma.douaneReservation.findUnique({
        where: { id },
        select: { id: true },
      });
      if (!existe) throw new NotFoundException('Réservation introuvable.');
      throw new ConflictException(
        'Seule une place encore réservée peut changer ainsi.',
      );
    }
    const r = await this.prisma.douaneReservation.findUniqueOrThrow({
      where: { id },
      include: INCLUDE_RESERVATION_STAFF,
    });
    const manquees = await this.seancesManqueesParCompte([r.userId]);
    return {
      ...reservationStaffVue(
        r,
        manquees.get(r.userId) ?? 0,
        new Date(),
        this.retardMaxMin,
      ),
      applicationId: r.applicationId,
      userId: r.userId,
    };
  }

  private validerHoraires(
    startAt: Date,
    endAt: Date,
    maintenant: Date,
    creation: boolean,
  ): void {
    if (Number.isNaN(startAt.getTime()) || Number.isNaN(endAt.getTime())) {
      throw new BadRequestException('Dates de séance invalides.');
    }
    const dureeMin = (endAt.getTime() - startAt.getTime()) / MIN;
    if (dureeMin <= 0)
      throw new BadRequestException('La fin doit être après le début.');
    if (
      dureeMin < DUREE_SEANCE_BORNES_MIN.min ||
      dureeMin > DUREE_SEANCE_BORNES_MIN.max
    ) {
      throw new BadRequestException(
        `Une séance dure de ${DUREE_SEANCE_BORNES_MIN.min} min à ${DUREE_SEANCE_BORNES_MIN.max / 60} h.`,
      );
    }
    if (endAt.getTime() <= maintenant.getTime()) {
      throw new BadRequestException(
        creation
          ? 'Cette séance serait déjà terminée.'
          : 'La nouvelle fin est déjà passée.',
      );
    }
    if (startAt.getTime() > maintenant.getTime() + HORIZON_SEANCE_MS) {
      throw new BadRequestException(
        "Une séance s'ouvre au plus 6 mois à l'avance.",
      );
    }
  }

  /** Examinateurs prévus : comptes existants, non bannis, rôle WHITELIST_REVIEWER ou plus. */
  private async validerExaminateurs(ids: string[]): Promise<string[]> {
    const uniques = [...new Set(ids)];
    if (uniques.length === 0) return [];
    const users = await this.prisma.user.findMany({
      where: { id: { in: uniques } },
      select: { id: true, role: true, banned: true, minecraftUsername: true },
    });
    if (users.length !== uniques.length) {
      throw new BadRequestException('Examinateur introuvable.');
    }
    const refuse = users.find((u) => u.banned || !estExaminateur(u.role));
    if (refuse) {
      throw new BadRequestException(
        `${refuse.minecraftUsername} ne peut pas examiner : il faut le rôle Relecteur whitelist ou plus.`,
      );
    }
    return uniques;
  }
}

interface SoldePlaces {
  /** Candidatures dont la place est annulée sans pénalité (arrivée enregistrée). */
  presentes: string[];
  /** Candidatures dont la place est manquée. */
  absentes: string[];
}
