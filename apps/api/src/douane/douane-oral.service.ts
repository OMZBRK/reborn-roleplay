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
  OralVia,
  Prisma,
  Role,
} from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import type { AuditSource } from '../audit/audit.service';
import { PrismaService } from '../prisma/prisma.service';
import { WebhooksService } from '../webhooks/webhooks.service';
import { WhitelistMessagesService } from '../whitelist/whitelist-messages.service';
import { RESULTATS_ORAL, type ResultatOral } from './dto/douane.dto';
import {
  DELAI_RECANDIDATURE_MS,
  GARDE_VOIX_MS,
  PHASES_ORAL,
  calculerPhase,
  debutDuLendemainParis,
  estActif,
  estExaminateur,
  etatFenetre,
  heureParis,
  jourParis,
  lireRetardMax,
  type PhaseCandidature,
} from './douane.regles';
import {
  OPTIONS_TRANSACTION,
  traduireConflitTransaction,
  verrouillerCandidature,
} from './douane.verrous';
import type { OralDecisionVue } from './douane.vues';

export interface OptionsDecisionOral {
  /** DOUANE : décision envoyée par le serveur de jeu (lot B). HORS_JEU (défaut) : carte « Oral » du panel. */
  via?: OralVia;
  /** Source inscrite à l'audit. */
  source?: AuditSource;
  /**
   * Oral hors jeu : pourquoi (« crash », « micro du jeu »…). Obligatoire quand
   * la douane est ouverte en jeu (secours, Q9) ; pendant la transition, tous les
   * oraux sont hors jeu et un motif par défaut est inscrit. Privé, à l'audit.
   */
  motifHorsJeu?: string;
  /** Lot B : la place que le serveur croit en cours. Refus si ce n'est plus celle-là. */
  reservationId?: string;
}

/**
 * Décision de l'oral (CANDIDATURE_ET_DOUANE.md §3.9, §3.11, Q7, Q9).
 *
 * Un seul point d'entrée, `decideOral`, pour les deux voies :
 *  - aujourd'hui, la carte « Oral » du panel (oral hors jeu : transition,
 *    puis secours décidé par l'examinateur) ;
 *  - au lot B, la route signée du serveur de jeu (`via: DOUANE`), qui
 *    retrouvera l'examinateur par son compte Minecraft puis appellera ce
 *    service.
 *
 * Effets :
 *  - APPROVED : statut global APPROVED, `User.oralPassedAt` (plus jamais
 *    d'oral), rôle WHITELISTED si le compte est PLAYER (jamais un staff) ;
 *  - NEEDS_REVISION : l'écrit est gardé, nouvelle séance dès le lendemain ;
 *  - REJECTED : statut global REJECTED, `User.whitelistRejectedAt` (nouvelle
 *    candidature complète 1 jour plus tard).
 * La place en cours est soldée, la voix est programmée pour effacement
 * 30 jours après une décision finale, le candidat est prévenu dans son fil
 * et la décision est inscrite à l'audit.
 */
@Injectable()
export class DouaneOralService {
  private readonly logger = new Logger(DouaneOralService.name);
  private readonly retardMaxMin: number;
  /** REBORN_DOUANE_ENABLED : hors de la transition, un oral hors jeu est un secours motivé (Q9). */
  private readonly douaneEnJeu: boolean;

  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
    private readonly webhooks: WebhooksService,
    private readonly messages: WhitelistMessagesService,
    config: ConfigService,
  ) {
    this.retardMaxMin = lireRetardMax(
      config.get<string>('DOUANE_RETARD_MAX_MIN'),
    );
    this.douaneEnJeu = estActif(config.get<string>('REBORN_DOUANE_ENABLED'));
  }

  async decideOral(
    applicationId: string,
    examinateurUserId: string,
    resultat: AppStatus,
    motif: string | null | undefined,
    notesPrivees: string | null | undefined,
    options: OptionsDecisionOral = {},
  ): Promise<OralDecisionVue> {
    const via = options.via ?? OralVia.HORS_JEU;
    const source: AuditSource = options.source ?? 'panel';
    if (!(RESULTATS_ORAL as readonly AppStatus[]).includes(resultat)) {
      throw new BadRequestException(
        'Résultat : APPROVED, NEEDS_REVISION ou REJECTED.',
      );
    }
    const res = resultat as ResultatOral;
    const motifVisible = motif?.trim() || null;
    const notes = notesPrivees?.trim() || null;
    let motifHorsJeu = options.motifHorsJeu?.trim() || null;
    if (res !== AppStatus.APPROVED && !motifVisible) {
      throw new BadRequestException(
        'Le motif est obligatoire pour « À revoir » et « Refusé » : il est montré au candidat.',
      );
    }
    if (via === OralVia.HORS_JEU && !motifHorsJeu) {
      // Secours (Q9) : seulement une fois la douane ouverte en jeu. Pendant la
      // transition, tous les oraux sont hors jeu : motif par défaut.
      if (this.douaneEnJeu) {
        throw new BadRequestException(
          "Indique pourquoi l'oral s'est passé hors jeu.",
        );
      }
      motifHorsJeu = MOTIF_HORS_JEU_TRANSITION;
    }

    const examinateur = await this.prisma.user.findUnique({
      where: { id: examinateurUserId },
      select: {
        id: true,
        role: true,
        banned: true,
        minecraftUsername: true,
        discordUsername: true,
      },
    });
    if (
      !examinateur ||
      examinateur.banned ||
      !estExaminateur(examinateur.role)
    ) {
      throw new ForbiddenException(
        "Seul un examinateur (rôle Relecteur whitelist ou plus) peut trancher l'oral.",
      );
    }

    const maintenant = new Date();
    const issue = await this.prisma
      .$transaction(async (tx) => {
        await verrouillerCandidature(tx, applicationId);
        const app = await tx.whitelistApplication.findUnique({
          where: { id: applicationId },
          include: {
            user: { select: { id: true, role: true, oralPassedAt: true } },
          },
        });
        if (!app) throw new NotFoundException('Candidature introuvable.');
        if (app.userId === examinateur.id) {
          throw new ForbiddenException(
            'Tu ne peux pas trancher ta propre candidature.',
          );
        }

        const reservation = await tx.douaneReservation.findFirst({
          where: { applicationId, statut: DouaneReservationStatut.BOOKED },
          orderBy: { convocationAt: 'asc' },
        });
        const phaseAvant = calculerPhase({
          role: app.user.role,
          oralPassedAt: app.user.oralPassedAt,
          application: app,
          reservationActive: reservation !== null,
        });
        if (via === OralVia.DOUANE) {
          if (phaseAvant !== 'DOUANE_RESERVEE' || !reservation) {
            throw new ConflictException(
              'Aucune place de douane en cours pour ce candidat : rien à trancher.',
            );
          }
          if (
            options.reservationId &&
            options.reservationId !== reservation.id
          ) {
            throw new ConflictException(
              "La place indiquée n'est plus celle en cours.",
            );
          }
        } else if (!PHASES_ORAL.includes(phaseAvant)) {
          throw new ConflictException(conflitOral(phaseAvant));
        }

        const finale = res !== AppStatus.NEEDS_REVISION;
        const lignesNotes = [
          via === OralVia.HORS_JEU && motifHorsJeu
            ? `Oral hors jeu : ${motifHorsJeu}`
            : null,
          notes,
        ].filter((l): l is string => l !== null);

        const appMaj = await tx.whitelistApplication.update({
          where: { id: applicationId },
          data: {
            oralStatus: res,
            oralDecidedAt: maintenant,
            oralVia: via,
            oralExaminerUserId: examinateur.id,
            oralReason: motifVisible,
            oralNotes: lignesNotes.length > 0 ? lignesNotes.join('\n\n') : null,
            // APPROVED seulement après l'oral (§1.3) ; « à revoir » garde l'écrit, donc le statut en cours.
            status:
              res === AppStatus.APPROVED
                ? AppStatus.APPROVED
                : res === AppStatus.REJECTED
                  ? AppStatus.REJECTED
                  : app.status,
            ...(finale && app.voiceKey
              ? { voicePurgeAt: new Date(maintenant.getTime() + GARDE_VOIX_MS) }
              : {}),
          },
        });

        // La place en cours : tranchée (DONE) si l'oral a eu lieu à son heure ;
        // rendue (CANCELLED) si l'oral hors jeu passe avant que sa fenêtre s'ouvre,
        // pour ne pas garder une place prise dans la capacité de la séance.
        let placeMaj: {
          id: string;
          seanceId: string;
          statut: DouaneReservationStatut;
          resultat: AppStatus | null;
        } | null = null;
        if (reservation) {
          const avantFenetre =
            via === OralVia.HORS_JEU &&
            etatFenetre(
              reservation.convocationAt,
              maintenant,
              this.retardMaxMin,
            ) === 'A_VENIR';
          const data: Prisma.DouaneReservationUncheckedUpdateInput =
            avantFenetre
              ? {
                  statut: DouaneReservationStatut.CANCELLED,
                  annuleeAt: maintenant,
                  motifAnnulation: 'Oral passé hors jeu',
                }
              : {
                  statut: DouaneReservationStatut.DONE,
                  resultat: res,
                  decidedAt: maintenant,
                  examinateurUserId: examinateur.id,
                };
          placeMaj = await tx.douaneReservation.update({
            where: { id: reservation.id },
            data,
            select: { id: true, seanceId: true, statut: true, resultat: true },
          });
        }

        let roleApres: Role = app.user.role;
        let oralPassedAt = app.user.oralPassedAt;
        if (res === AppStatus.APPROVED) {
          oralPassedAt = maintenant;
          await tx.user.update({
            where: { id: app.userId },
            data: { oralPassedAt: maintenant },
          });
          // Promotion seulement depuis PLAYER : jamais de changement de rôle pour un staff.
          const promu = await tx.user.updateMany({
            where: { id: app.userId, role: Role.PLAYER },
            data: { role: Role.WHITELISTED },
          });
          if (promu.count > 0) roleApres = Role.WHITELISTED;
        } else if (res === AppStatus.REJECTED) {
          await tx.user.update({
            where: { id: app.userId },
            data: { whitelistRejectedAt: maintenant },
          });
        }

        const phaseApres = calculerPhase({
          role: roleApres,
          oralPassedAt,
          application: appMaj,
          reservationActive: false,
        });
        return {
          app,
          appMaj,
          place: placeMaj,
          phaseAvant,
          phaseApres,
          promu: roleApres !== app.user.role,
        };
      }, OPTIONS_TRANSACTION)
      .catch(traduireConflitTransaction);

    const { app, appMaj, place, phaseAvant, phaseApres } = issue;
    const prochaineSeanceDes =
      res === AppStatus.NEEDS_REVISION
        ? debutDuLendemainParis(maintenant)
        : null;
    const canReapplyAt =
      res === AppStatus.REJECTED
        ? new Date(maintenant.getTime() + DELAI_RECANDIDATURE_MS)
        : null;

    this.logger.log(
      `oral ${applicationId} → ${res} (${via}) par ${examinateur.id}` +
        (issue.promu ? ` — ${app.userId} → WHITELISTED` : ''),
    );

    void this.messages.postSystemMessage(
      applicationId,
      messageCandidat(res, motifVisible, prochaineSeanceDes, canReapplyAt),
    );

    if (app.discordThreadId || app.discordMessageId) {
      void this.webhooks
        .statusUpdate({
          kind: 'whitelist',
          threadId: app.discordThreadId,
          messageId: app.discordMessageId,
          status: res,
          actorName:
            examinateur.discordUsername ?? examinateur.minecraftUsername,
          // Seulement le motif visible du joueur : jamais les notes privées.
          reason: motifVisible ? `Oral : ${motifVisible}` : undefined,
        })
        .catch((err) =>
          this.logger.warn(
            `statusUpdate oral ${applicationId} échec : ${(err as Error).message}`,
          ),
        );
    }

    // Attendu (et non lancé en tâche de fond) : la décision doit être tracée. AuditService.log ne lève jamais.
    await this.audit.log({
      actorId: examinateur.id,
      action: `whitelist.oral.${res.toLowerCase()}`,
      targetUserId: app.userId,
      targetEntity: `whitelist:${applicationId}`,
      metadata: {
        via,
        resultat: res,
        motif: motifVisible,
        motifHorsJeu,
        notesPrivees: notes !== null,
        phaseAvant,
        reservationId: place?.id ?? null,
        seanceId: place?.seanceId ?? null,
        promotion: issue.promu,
      },
      source,
    });

    return {
      applicationId,
      userId: app.userId,
      resultat: res,
      status: appMaj.status,
      oralStatus: appMaj.oralStatus,
      oralVia: appMaj.oralVia,
      oralDecidedAt: appMaj.oralDecidedAt?.toISOString() ?? null,
      phase: phaseApres,
      prochaineSeanceDes: prochaineSeanceDes?.toISOString() ?? null,
      canReapplyAt: canReapplyAt?.toISOString() ?? null,
      reservation: place,
    };
  }
}

/** Motif inscrit quand l'oral se passe hors jeu parce que la douane en jeu n'est pas encore ouverte (§3.11). */
export const MOTIF_HORS_JEU_TRANSITION =
  'Transition : douane en jeu pas encore ouverte';

/** Refus de la carte « Oral » selon la phase (texte pour le staff). */
function conflitOral(phase: PhaseCandidature): string {
  switch (phase) {
    case 'ACCEPTE':
      return "Ce candidat est déjà accepté : il n'a pas d'oral à passer.";
    case 'REFUSE':
      return 'Cette candidature est refusée : une nouvelle candidature complète est nécessaire avant un oral.';
    default:
      return "L'écrit n'est pas accepté : l'oral se tranche après la validation des parties HRP et RP.";
  }
}

/** Message système posté dans le fil du candidat (visible dans le launcher). */
function messageCandidat(
  resultat: ResultatOral,
  motif: string | null,
  prochaineSeanceDes: Date | null,
  canReapplyAt: Date | null,
): string {
  if (resultat === AppStatus.APPROVED) {
    return (
      '🎉 Douane réussie : bienvenue sur Reborn ! Ton accès au serveur est ouvert.' +
      (motif ? `\nMot de l'examinateur : ${motif}` : '')
    );
  }
  if (resultat === AppStatus.NEEDS_REVISION) {
    const quand = prochaineSeanceDes
      ? ` à partir du ${jourParis(prochaineSeanceDes)}`
      : '';
    return (
      `✏️ Douane à revoir. Motif de l'examinateur : ${motif ?? ''}\n` +
      `Ton écrit est gardé : réserve une nouvelle séance dans le launcher${quand}.`
    );
  }
  const quand = canReapplyAt
    ? ` à partir du ${jourParis(canReapplyAt)} à ${heureParis(canReapplyAt)}`
    : '';
  return (
    `❌ Douane non validée. Motif de l'examinateur : ${motif ?? ''}\n` +
    `Une nouvelle candidature complète sera possible${quand}.`
  );
}
