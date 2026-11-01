import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { AppStatus, Prisma, Role, TicketStatus } from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import type { AuditSource } from '../audit/audit.service';
import { PrismaService } from '../prisma/prisma.service';
import { WebhooksService } from '../webhooks/webhooks.service';
import { WhitelistMessagesService } from '../whitelist/whitelist-messages.service';
import { WhitelistVoixService } from '../whitelist/whitelist-voix.service';
import { calculerPhase, estExaminateur } from '../douane/douane.regles';
import {
  OPTIONS_TRANSACTION,
  traduireConflitTransaction,
  verrouillerCandidature,
  verrouillerCompte,
} from '../douane/douane.verrous';
import {
  DELAI_NOUVELLE_CANDIDATURE_MS,
  formatDateParis,
  GARDE_VOIX_MS,
  statutGlobal,
} from '../whitelist/whitelist.rules';
import {
  PartieEcrit,
  TicketStatusDto,
  WhitelistDecisionDto,
  WhitelistPartDecisionBotDto,
  WhitelistPartDecisionDto,
} from './dto/staff.dto';
import {
  messageRepasserOral,
  REFUS_ECRIT_ORAL_A_REPASSER,
  REFUS_ORAL_SANS_CANDIDATURE,
  refusRepasserOral,
} from './oral-repasser.regles';

export interface DecisionActor {
  /** Si fourni, on resoud le nom via User.discordUsername / minecraftUsername. */
  userId?: string;
  /** Fallback affiche dans l'embed Discord si pas de userId. */
  name?: string;
  /** Origine inscrite à l'audit (panel par défaut). */
  source?: AuditSource;
}

/** Statut envoyé au bot quand l'écrit est accepté mais l'oral pas encore passé (statut global PENDING). */
export const STATUT_BOT_ECRIT_ACCEPTE = 'ECRIT_ACCEPTE';

/**
 * Message système posté dans le fil du candidat après une décision sur
 * l'écrit (visible dans le launcher). Jamais de note privée ici.
 */
export function messageDecisionEcrit(
  parties: PartieEcrit[],
  decision: AppStatus,
  hrp: AppStatus,
  rp: AppStatus,
  statut: AppStatus,
  now: Date,
): string {
  const nom = parties.length === 2 ? 'HRP et RP' : parties[0];
  if (statut === AppStatus.APPROVED) {
    return '🎉 Félicitations, ta candidature est acceptée ! Bienvenue sur Reborn.';
  }
  if (hrp === AppStatus.APPROVED && rp === AppStatus.APPROVED) {
    return '✅ Écrit accepté. Réserve ta séance de douane dans le launcher.';
  }
  if (decision === AppStatus.REJECTED) {
    const quand = formatDateParis(
      new Date(now.getTime() + DELAI_NOUVELLE_CANDIDATURE_MS),
    );
    const quoi =
      parties.length === 2 ? 'Candidature refusée' : `Partie ${nom} refusée`;
    return `❌ ${quoi}. Nouvelle candidature complète possible le ${quand}.`;
  }
  if (decision === AppStatus.NEEDS_REVISION) {
    return `✏️ Une révision de la partie ${nom} est demandée : modifie ta candidature puis renvoie-la.`;
  }
  if (parties.length === 1) {
    const autre = parties[0] === 'HRP' ? 'RP' : 'HRP';
    const autreStatut = autre === 'HRP' ? hrp : rp;
    // Aucun délai annoncé (Q11).
    return autreStatut === AppStatus.PENDING
      ? `✅ Partie ${nom} validée. La partie ${autre} est en cours de relecture.`
      : `✅ Partie ${nom} validée.`;
  }
  return 'Candidature mise à jour.';
}

/** Statuts dont le commentaire reste à lire par le candidat. */
const STATUTS_COMMENTES: readonly AppStatus[] = [
  AppStatus.NEEDS_REVISION,
  AppStatus.REJECTED,
];

/** Candidature en cours : ni acceptée ni refusée (même règle que le retour à PLAYER de RoleService). */
const STATUTS_EN_COURS: readonly AppStatus[] = [
  AppStatus.PENDING,
  AppStatus.NEEDS_REVISION,
];

const MOTIF_SECTION = /^(HRP|RP) : /;

/** Découpe un commentaire « HRP : … » / « RP : … » en sections ; null s'il n'a pas cette forme. */
function sectionsCommentaire(
  texte: string,
): Partial<Record<PartieEcrit, string>> | null {
  const morceaux = texte.split(/\n\n(?=(?:HRP|RP) : )/);
  const out: Partial<Record<PartieEcrit, string>> = {};
  for (const m of morceaux) {
    const r = MOTIF_SECTION.exec(m);
    if (!r) return null;
    out[r[1] as PartieEcrit] = m.slice(r[0].length);
  }
  return out;
}

/**
 * Commentaire de l'écrit (visible du joueur) après la décision d'UNE partie.
 * Le commentaire de l'autre partie, s'il est encore à lire (à revoir,
 * refusée), est gardé : « HRP : … » puis « RP : … ». Sans nouveau commentaire,
 * rien ne change. Un commentaire sans sections encore en place est celui de
 * l'autre partie s'il est encore à lire (il a été posé par sa décision).
 */
export function fusionnerCommentaires(
  existant: string | null,
  partie: PartieEcrit,
  nouveau: string | undefined,
  statutAutre: AppStatus,
): string | null {
  const texte = nouveau?.trim();
  if (!texte) return existant;
  const autre: PartieEcrit = partie === 'HRP' ? 'RP' : 'HRP';
  if (!existant || !STATUTS_COMMENTES.includes(statutAutre)) return texte;
  const sections = sectionsCommentaire(existant);
  const commentaireAutre = sections ? sections[autre] : existant;
  if (!commentaireAutre) return texte;
  const hrp = partie === 'HRP' ? texte : commentaireAutre;
  const rp = partie === 'RP' ? texte : commentaireAutre;
  return `HRP : ${hrp}\n\nRP : ${rp}`;
}

@Injectable()
export class StaffService {
  private readonly logger = new Logger(StaffService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly webhooks: WebhooksService,
    private readonly audit: AuditService,
    private readonly whitelistMessages: WhitelistMessagesService,
    private readonly voix: WhitelistVoixService,
  ) {}

  /**
   * Resoud le label affiche dans l'embed Discord du status-update :
   * - actor.userId fourni → lookup discordUsername > minecraftUsername
   * - sinon actor.name si fourni
   * - sinon "Staff"
   */
  private async resolveActorName(actor: DecisionActor): Promise<string> {
    if (actor.userId) {
      const user = await this.prisma.user.findUnique({
        where: { id: actor.userId },
        select: { discordUsername: true, minecraftUsername: true },
      });
      if (user) {
        return user.discordUsername ?? user.minecraftUsername ?? 'Staff';
      }
    }
    return actor.name ?? 'Staff';
  }

  /**
   * Ancienne décision globale (boutons du bot, `/whitelist accept`, PATCH
   * /admin/whitelist/:id). Elle passe désormais par la décision de l'écrit,
   * HRP et RP ensemble (CANDIDATURE_ET_DOUANE.md §2.9) : elle ne donne plus
   * WHITELISTED sans l'oral et ne rétrograde jamais un staff.
   */
  async decideWhitelist(
    applicationId: string,
    dto: WhitelistDecisionDto,
    actor: DecisionActor = {},
  ) {
    if (dto.status === AppStatus.PENDING) {
      throw new BadRequestException(
        "PENDING n'est pas une décision staff (c'est l'état initial).",
      );
    }
    // Bot : l'acteur se retrouve par son compte Discord lié, s'il est transmis.
    let acteur = actor;
    if (!acteur.userId && dto.actorDiscordId) {
      const staff = await this.prisma.user.findUnique({
        where: { discordUserId: dto.actorDiscordId },
        select: { id: true },
      });
      acteur = {
        ...acteur,
        userId: staff?.id,
        source: acteur.source ?? 'discord',
      };
    }
    const updated = await this.trancherEcrit(
      applicationId,
      ['HRP', 'RP'],
      dto.status,
      dto.reviewNotes,
      undefined,
      acteur,
    );
    return {
      id: updated.id,
      status: updated.status,
      reviewedAt: updated.reviewedAt,
      reviewNotes: updated.reviewNotes,
      userId: updated.userId,
    };
  }

  /**
   * Reset propre d'une candidature : supprime la candidature (les
   * `WhitelistMessage` liés cascadent) pour que le joueur puisse en
   * re-soumettre une fraiche, et remet son role a PLAYER — UNIQUEMENT s'il
   * etait WHITELISTED. Un HELPER/REVIEWER/ADMIN/OWNER garde son role : jamais
   * de retrogradation du staff (c'est ce qui reverrouillait la publication en
   * re-testant le flow). Remplace le `DELETE FROM WhitelistApplication` brut.
   *
   * Candidature v2 : la voix jointe est effacée avec la candidature, et le délai
   * après un refus est levé (décision ADMIN). Par défaut `oralPassedAt` reste :
   * l'oral ne se passe qu'à la première whitelist (décision du 19/10).
   * Exception du 01/11 : avec `refaireOral`, l'oral redevient aussi à passer
   * (oralPassedAt effacé, indicateur « oral à repasser » levé), pour rejouer le
   * cycle complet, écrit puis oral (comptes de test, retour d'absence).
   */
  async resetWhitelist(
    applicationId: string,
    actor: DecisionActor = {},
    options: { refaireOral?: boolean } = {},
  ) {
    const refaireOral = options.refaireOral === true;
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
      select: { id: true, userId: true, status: true, voiceKey: true },
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');

    const target = await this.prisma.user.findUnique({
      where: { id: app.userId },
      select: { role: true, oralPassedAt: true },
    });
    // Retrograde WHITELISTED → PLAYER seulement. Jamais le staff.
    const demote = target?.role === 'WHITELISTED';
    const oral = refaireOral
      ? {
          oralPassedAt: null,
          oralRetakeRequestedAt: null,
          oralRetakeRequestedById: null,
          oralRetakeReason: null,
        }
      : {};

    await this.prisma.$transaction(async (tx) => {
      await tx.whitelistApplication.delete({ where: { id: applicationId } });
      await tx.user.update({
        where: { id: app.userId },
        data: demote
          ? { role: 'PLAYER', whitelistRejectedAt: null, ...oral }
          : { whitelistRejectedAt: null, ...oral },
      });
    });
    await this.voix.supprimer(app.voiceKey);

    this.logger.log(
      `whitelist reset ${applicationId} (user ${app.userId})` +
        (demote ? ' — role → PLAYER' : ' — role conserve') +
        (refaireOral ? ' — oral à refaire' : ''),
    );

    if (actor.userId) {
      void this.audit.log({
        actorId: actor.userId,
        action: 'whitelist.reset',
        targetUserId: app.userId,
        targetEntity: `whitelist:${applicationId}`,
        metadata: {
          previousStatus: app.status,
          roleDemoted: demote,
          refaireOral,
          oralPassedAtAvant: target?.oralPassedAt?.toISOString() ?? null,
        },
        source: 'panel',
      });
    }

    return {
      ok: true,
      userId: app.userId,
      roleDemoted: demote,
      oralReinitialise: refaireOral,
    };
  }

  /**
   * Exiger un nouvel oral (décision du 01/11, qui amende celle du 19/10) :
   * candidature de clan, longue absence, essai du parcours avec des comptes de
   * test. Motif obligatoire, montré au joueur et inscrit à l'audit.
   *
   * Aucune règle ne change : le compte est remis dans l'état que les règles
   * lisent déjà comme « écrit accepté, oral à passer » (rôle PLAYER,
   * oralPassedAt effacé, oral PENDING, candidature PENDING). La suite est le
   * parcours habituel, launcher et jeu inchangés : réservation, jeton douane,
   * décision, puis decideOral APPROVED rend WHITELISTED et repose oralPassedAt.
   * Quand, qui et pourquoi vivent sur le compte (User.oralRetake*).
   *
   * Refus avant toute écriture : le staff (refusRepasserOral), un compte sans
   * candidature, une candidature refusée, un oral déjà à passer. Un compte
   * banni passe : l'exigence prend effet à la levée du bannissement. Jamais
   * NEEDS_REVISION (le jeu afficherait « 2ᵉ passage » avec un ancien motif, et
   * la réservation attendrait le lendemain) ; une place BOOKED reste telle
   * quelle. L'oral effacé (date, voie, examinateur, motif, notes privées) est
   * recopié au journal. « Lever » l'exigence = accepter l'oral hors jeu
   * (PATCH /admin/whitelist/:id/oral).
   */
  async exigerNouvelOral(
    userId: string,
    motif: string,
    actor: DecisionActor & { userId: string },
  ) {
    const raison = motif.trim();
    if (raison.length < 3) {
      throw new BadRequestException('Motif : 3 caractères au moins.');
    }
    const cible = await this.prisma.user.findUnique({
      where: { id: userId },
      select: {
        id: true,
        role: true,
        oralPassedAt: true,
        discordUserId: true,
        minecraftUsername: true,
        whitelistApp: {
          select: {
            id: true,
            status: true,
            hrpStatus: true,
            rpStatus: true,
            oralStatus: true,
          },
        },
      },
    });
    if (!cible) throw new NotFoundException('Joueur introuvable.');
    if (actor.userId === userId) {
      throw new ForbiddenException(
        'Tu ne peux pas exiger un nouvel oral de ton propre compte.',
      );
    }
    const refus = refusRepasserOral(cible, cible.whitelistApp);
    if (refus !== null || !cible.whitelistApp) {
      throw new ConflictException(refus ?? REFUS_ORAL_SANS_CANDIDATURE);
    }
    const applicationId = cible.whitelistApp.id;

    const maintenant = new Date();
    const issue = await this.prisma
      .$transaction(async (tx) => {
        // Même verrou et même ordre que la douane (decideOral) : la candidature
        // d'abord, puis le compte. Un changement de grade (RoleService) ne peut
        // plus passer entre la relecture du rôle et les écritures.
        await verrouillerCandidature(tx, applicationId);
        await verrouillerCompte(tx, userId);
        const app = await tx.whitelistApplication.findUnique({
          where: { id: applicationId },
          include: {
            user: {
              select: {
                id: true,
                role: true,
                oralPassedAt: true,
                whitelistRejectedAt: true,
              },
            },
          },
        });
        // Relu sous les verrous : une décision, une remise à zéro ou un changement
        // de grade a pu passer entre-temps.
        const refusFrais = refusRepasserOral(app?.user ?? cible, app);
        if (refusFrais !== null || !app) {
          throw new ConflictException(
            refusFrais ?? REFUS_ORAL_SANS_CANDIDATURE,
          );
        }
        const place = await tx.douaneReservation.findFirst({
          where: { applicationId, statut: 'BOOKED' },
          select: { id: true },
        });

        await tx.user.update({
          where: { id: userId },
          data: {
            oralPassedAt: null,
            oralRetakeRequestedAt: maintenant,
            oralRetakeRequestedById: actor.userId,
            oralRetakeReason: raison,
          },
        });
        // Rétrogradation seulement depuis WHITELISTED : jamais un autre rôle. Le
        // compte est verrouillé : le rôle relu est bien celui qui change ici.
        const r = await tx.user.updateMany({
          where: { id: userId, role: Role.WHITELISTED },
          data: { role: Role.PLAYER },
        });
        const roleApres = r.count > 0 ? Role.PLAYER : app.user.role;

        // Oral accepté sur la candidature : il redevient à passer, l'écrit reste
        // accepté. Un ancien dossier accepté d'un bloc (v1) n'a pas toujours ses
        // parties tranchées : elles comptent alors comme validées.
        const accepte = app.status === AppStatus.APPROVED;
        const ecritForce =
          accepte &&
          (app.hrpStatus !== AppStatus.APPROVED ||
            app.rpStatus !== AppStatus.APPROVED);
        const effaceOral = accepte || app.oralStatus === AppStatus.APPROVED;
        const data: Prisma.WhitelistApplicationUncheckedUpdateInput = effaceOral
          ? {
              oralStatus: AppStatus.PENDING,
              oralDecidedAt: null,
              oralVia: null,
              oralExaminerUserId: null,
              oralReason: null,
              oralNotes: null,
              // Plus de prise en charge : le dossier ne revient pas dans les prises d'un ancien relecteur.
              assignedToUserId: null,
              assignedAt: null,
            }
          : {};
        if (accepte) {
          data.status = AppStatus.PENDING;
          data.hrpStatus = AppStatus.APPROVED;
          data.rpStatus = AppStatus.APPROVED;
        }
        const enCours = STATUTS_EN_COURS.includes(
          accepte ? AppStatus.PENDING : app.status,
        );
        const ecritValide =
          accepte ||
          (app.hrpStatus === AppStatus.APPROVED &&
            app.rpStatus === AppStatus.APPROVED);
        // Le jeton douane et l'accès au jeu lisent writtenApprovedAt pour « écrit accepté ».
        if (enCours && ecritValide && !app.writtenApprovedAt) {
          data.writtenApprovedAt = maintenant;
        }
        // Procédure reprise, comme un retour à PLAYER (RoleService) : l'effacement
        // programmé de la voix est levé sur une candidature en cours.
        if (enCours && app.voiceKey && app.voicePurgeAt) {
          data.voicePurgeAt = null;
        }
        const appApres =
          Object.keys(data).length > 0
            ? await tx.whitelistApplication.update({
                where: { id: applicationId },
                data,
              })
            : app;

        const phaseApres = calculerPhase({
          role: roleApres,
          oralPassedAt: null,
          whitelistRejectedAt: app.user.whitelistRejectedAt,
          application: appApres,
          reservationActive: place !== null,
        });
        return { app, appApres, roleApres, ecritForce, effaceOral, phaseApres };
      }, OPTIONS_TRANSACTION)
      .catch(traduireConflitTransaction);

    const { app, appApres, roleApres, ecritForce, effaceOral, phaseApres } =
      issue;
    this.logger.log(
      `oral à repasser ${applicationId} (user ${userId}) par ${actor.userId}` +
        (roleApres !== app.user.role
          ? ` — ${app.user.role} → ${roleApres}`
          : '') +
        ` — phase ${phaseApres}`,
    );

    const message = messageRepasserOral(raison, phaseApres);
    void this.whitelistMessages.postSystemMessage(applicationId, message);

    if (app.discordThreadId || app.discordMessageId) {
      const actorName = await this.resolveActorName(actor);
      const ecritAccepte =
        appApres.hrpStatus === AppStatus.APPROVED &&
        appApres.rpStatus === AppStatus.APPROVED;
      void this.webhooks
        .statusUpdate({
          kind: 'whitelist',
          threadId: app.discordThreadId,
          messageId: app.discordMessageId,
          status:
            ecritAccepte && appApres.status === AppStatus.PENDING
              ? STATUT_BOT_ECRIT_ACCEPTE
              : appApres.status,
          actorName,
          reason: `Oral à repasser : ${raison}`,
        })
        .catch((err) =>
          this.logger.warn(
            `statusUpdate oral à repasser ${applicationId} échec : ${(err as Error).message}`,
          ),
        );
    }

    // MP au joueur (kind `douane`, comme les MP de la douane) : le fil reste la référence.
    if (cible.discordUserId) {
      void this.webhooks
        .directMessage({
          discordUserId: cible.discordUserId,
          context: {
            kind: 'douane',
            entityId: applicationId,
            subject: 'Oral à repasser',
          },
          fromPseudo: cible.minecraftUsername,
          content: message,
        })
        .catch((err) =>
          this.logger.warn(
            `MP oral à repasser non transmis (${userId}) : ${(err as Error).message}`,
          ),
        );
    }

    // Attendu (et non lancé en tâche de fond) : l'exigence doit être tracée. AuditService.log ne lève jamais.
    await this.audit.log({
      actorId: actor.userId,
      action: 'whitelist.oral.repasser',
      targetUserId: userId,
      targetEntity: `whitelist:${applicationId}`,
      metadata: {
        motif: raison,
        roleAvant: app.user.role,
        roleApres,
        oralPassedAtAvant: app.user.oralPassedAt?.toISOString() ?? null,
        statutAvant: app.status,
        oralAvant: app.oralStatus,
        hrpAvant: app.hrpStatus,
        rpAvant: app.rpStatus,
        ecritForce,
        phaseApres,
        // L'oral effacé de la candidature ne vit plus qu'ici, notes privées
        // comprises (le journal est réservé à la direction, audit.voir).
        ...(effaceOral
          ? {
              oralDecidedAtAvant: app.oralDecidedAt?.toISOString() ?? null,
              oralViaAvant: app.oralVia,
              oralExaminerAvant: app.oralExaminerUserId,
              oralReasonAvant: app.oralReason,
              oralNotesAvant: app.oralNotes,
            }
          : {}),
      },
      source: actor.source ?? 'panel',
    });

    return {
      ok: true as const,
      userId,
      applicationId,
      roleAvant: app.user.role,
      phase: phaseApres,
    };
  }

  /**
   * Décision sur UNE partie de l'écrit (HRP ou RP), au panel.
   * Les deux validées = « écrit accepté » : statut global PENDING avec
   * writtenApprovedAt, message « réserve ta douane » ; la promotion WHITELISTED
   * attend l'oral (User.oralPassedAt), sauf oral déjà passé sur ce compte.
   */
  async decideWhitelistPart(
    applicationId: string,
    dto: WhitelistPartDecisionDto,
    actor: DecisionActor = {},
  ) {
    if (dto.status === AppStatus.PENDING) {
      throw new BadRequestException("PENDING n'est pas une décision staff.");
    }
    // Le candidat lit ce commentaire en tête du suivi (maquette L8), comme le motif de l'oral.
    if (STATUTS_COMMENTES.includes(dto.status) && !dto.reviewNotes?.trim()) {
      throw new BadRequestException(
        'Le commentaire est obligatoire pour « À revoir » et « Refusé » : il est montré au candidat.',
      );
    }
    return this.trancherEcrit(
      applicationId,
      [dto.part],
      dto.status,
      dto.reviewNotes,
      dto.staffNotes,
      actor,
    );
  }

  /**
   * Décision sur UNE partie de l'écrit depuis le bot (boutons du MP, commande
   * `/whitelist hrp|rp`). Le staff Discord doit avoir un compte Reborn lié, non
   * banni, de rôle WHITELIST_REVIEWER ou plus (même règle que l'examinateur de
   * la douane) : la permission Discord de la commande ne suffit pas. La
   * décision est inscrite à son nom (audit `source: discord`). La note privée
   * de l'écrit ne repart pas vers le bot.
   */
  async decideWhitelistPartFromDiscord(
    applicationId: string,
    dto: WhitelistPartDecisionBotDto,
  ) {
    const staff = await this.prisma.user.findUnique({
      where: { discordUserId: dto.actorDiscordId },
      select: { id: true, role: true, banned: true },
    });
    if (!staff) {
      throw new ForbiddenException(
        "Ton compte Discord n'est lié à aucun compte Reborn : lie-le depuis le launcher pour décider.",
      );
    }
    if (staff.banned || !estExaminateur(staff.role)) {
      throw new ForbiddenException(
        'Rôle insuffisant : il faut le rôle Relecteur whitelist (ou plus) sur le panel.',
      );
    }
    const vue = await this.decideWhitelistPart(
      applicationId,
      { part: dto.part, status: dto.status, reviewNotes: dto.reviewNotes },
      { userId: staff.id, source: 'discord' },
    );
    return {
      id: vue.id,
      status: vue.status,
      hrpStatus: vue.hrpStatus,
      rpStatus: vue.rpStatus,
      oralStatus: vue.oralStatus,
      writtenApprovedAt: vue.writtenApprovedAt,
      reviewedAt: vue.reviewedAt,
      reviewNotes: vue.reviewNotes,
      userId: vue.userId,
    };
  }

  /**
   * Cœur de la décision sur l'écrit. Une transaction, avec la ligne de la
   * candidature verrouillée (même ordre que la douane : candidature d'abord),
   * pour ne pas croiser une réservation ou une décision d'oral.
   */
  private async trancherEcrit(
    applicationId: string,
    parties: PartieEcrit[],
    decision: AppStatus,
    reviewNotes: string | undefined,
    staffNotes: string | undefined,
    actor: DecisionActor,
  ) {
    const now = new Date();
    const issue = await this.prisma.$transaction(async (tx) => {
      await tx.$queryRaw`SELECT "id" FROM "WhitelistApplication" WHERE "id" = ${applicationId} FOR UPDATE`;
      const app = await tx.whitelistApplication.findUnique({
        where: { id: applicationId },
        include: {
          user: {
            select: {
              id: true,
              role: true,
              oralPassedAt: true,
              oralRetakeRequestedAt: true,
            },
          },
        },
      });
      if (!app) throw new NotFoundException('Candidature introuvable.');
      if (actor.userId && actor.userId === app.userId) {
        throw new ForbiddenException(
          'Tu ne peux pas trancher ta propre candidature.',
        );
      }
      if (app.status === AppStatus.APPROVED) {
        throw new ConflictException(
          'Candidature déjà acceptée (oral compris). Pour la rouvrir, passe par la remise à zéro (ADMIN).',
        );
      }
      // Oral à repasser : l'écrit déjà accepté ne se retranche plus (panel et bot).
      if (
        app.user.oralRetakeRequestedAt &&
        app.hrpStatus === AppStatus.APPROVED &&
        app.rpStatus === AppStatus.APPROVED
      ) {
        throw new ConflictException(REFUS_ECRIT_ORAL_A_REPASSER);
      }

      const hrp = parties.includes('HRP') ? decision : app.hrpStatus;
      const rp = parties.includes('RP') ? decision : app.rpStatus;
      const ecritValide =
        hrp === AppStatus.APPROVED && rp === AppStatus.APPROVED;
      const oralDejaPasse =
        app.user.oralPassedAt !== null || app.oralStatus === AppStatus.APPROVED;
      const statut = statutGlobal(hrp, rp, app.oralStatus, oralDejaPasse);
      const finale =
        statut === AppStatus.APPROVED || statut === AppStatus.REJECTED;

      // Une partie seule : le commentaire encore à lire de l'autre partie est gardé.
      const commentaire =
        parties.length === 1
          ? fusionnerCommentaires(
              app.reviewNotes,
              parties[0],
              reviewNotes,
              parties[0] === 'HRP' ? rp : hrp,
            )
          : (reviewNotes ?? app.reviewNotes);
      // Retour sur un écrit refusé (le staff le repasse en « à revoir » ou en
      // « validé ») : le refus est annulé, avec ses effets.
      const refusAnnule =
        app.status === AppStatus.REJECTED && statut !== AppStatus.REJECTED;
      const data: Prisma.WhitelistApplicationUncheckedUpdateInput = {
        hrpStatus: hrp,
        rpStatus: rp,
        status: statut,
        reviewedAt: now,
        reviewedBy: actor.userId ?? app.reviewedBy,
        reviewNotes: commentaire,
        writtenApprovedAt: ecritValide ? (app.writtenApprovedAt ?? now) : null,
      };
      if (staffNotes !== undefined)
        data.writtenStaffNotes = staffNotes.trim() || null;
      // Décision finale : la voix sera effacée 30 jours plus tard (Q2). Refus
      // annulé : l'effacement programmé par ce refus aussi, la procédure reprend.
      if (finale && app.voiceKey && !app.voicePurgeAt) {
        data.voicePurgeAt = new Date(now.getTime() + GARDE_VOIX_MS);
      } else if (!finale && refusAnnule) {
        data.voicePurgeAt = null;
      }
      const updated = await tx.whitelistApplication.update({
        where: { id: applicationId },
        data,
      });

      // Écrit plus valide (à revoir, refusé) : une place de douane réservée est rendue.
      let placesRendues = 0;
      if (!ecritValide) {
        const r = await tx.douaneReservation.updateMany({
          where: { applicationId, statut: 'BOOKED' },
          data: {
            statut: 'CANCELLED',
            annuleeAt: now,
            motifAnnulation:
              decision === AppStatus.REJECTED
                ? 'Écrit refusé'
                : 'Écrit à revoir',
          },
        });
        placesRendues = r.count;
      }
      if (statut === AppStatus.REJECTED) {
        await tx.user.update({
          where: { id: app.userId },
          data: { whitelistRejectedAt: now },
        });
      } else if (refusAnnule) {
        // Le refus venait de cette candidature : plus de délai à faire courir.
        await tx.user.update({
          where: { id: app.userId },
          data: { whitelistRejectedAt: null },
        });
      }
      let promu = false;
      if (statut === AppStatus.APPROVED) {
        // Oral déjà passé (première whitelist seulement, décision du 19/10).
        if (!app.user.oralPassedAt) {
          await tx.user.update({
            where: { id: app.userId },
            data: { oralPassedAt: now },
          });
        }
        // Promotion seulement depuis PLAYER : jamais de changement de rôle pour un staff.
        const r = await tx.user.updateMany({
          where: { id: app.userId, role: Role.PLAYER },
          data: { role: Role.WHITELISTED },
        });
        promu = r.count > 0;
      }
      return {
        app,
        updated,
        hrp,
        rp,
        ecritValide,
        statut,
        promu,
        placesRendues,
      };
    });

    const { app, updated, hrp, rp, ecritValide, statut, promu, placesRendues } =
      issue;
    this.logger.log(
      `staff decision ${applicationId} ${parties.join('+')} → ${decision} (global ${statut}` +
        `${ecritValide && statut !== AppStatus.APPROVED ? ', écrit accepté' : ''})` +
        (promu ? ` — ${app.userId} → WHITELISTED` : '') +
        (placesRendues > 0
          ? ` — ${placesRendues} place(s) de douane rendue(s)`
          : ''),
    );

    void this.whitelistMessages.postSystemMessage(
      applicationId,
      messageDecisionEcrit(parties, decision, hrp, rp, statut, now),
    );

    if (app.discordThreadId || app.discordMessageId) {
      const actorName = await this.resolveActorName(actor);
      void this.webhooks
        .statusUpdate({
          kind: 'whitelist',
          threadId: app.discordThreadId,
          messageId: app.discordMessageId,
          status:
            ecritValide && statut === AppStatus.PENDING
              ? STATUT_BOT_ECRIT_ACCEPTE
              : statut,
          actorName,
          // Seulement le commentaire visible du joueur, jamais la note privée.
          reason: reviewNotes ?? undefined,
        })
        .catch((err) =>
          this.logger.warn(
            `statusUpdate whitelist ${applicationId} échec : ${(err as Error).message}`,
          ),
        );
    }

    if (actor.userId) {
      void this.audit.log({
        actorId: actor.userId,
        action:
          parties.length === 2
            ? `whitelist.${decision.toLowerCase()}`
            : `whitelist.${parties[0].toLowerCase()}.${decision.toLowerCase()}`,
        targetUserId: app.userId,
        targetEntity: `whitelist:${applicationId}`,
        metadata: {
          parties,
          decision,
          previousStatus: app.status,
          globalStatus: statut,
          writtenApproved: ecritValide,
          promotion: promu,
          reviewNotes: reviewNotes ?? null,
          staffNotes: staffNotes !== undefined,
        },
        source: actor.source ?? 'panel',
      });
    }

    return {
      id: updated.id,
      status: updated.status,
      hrpStatus: updated.hrpStatus,
      rpStatus: updated.rpStatus,
      oralStatus: updated.oralStatus,
      writtenApprovedAt: updated.writtenApprovedAt?.toISOString() ?? null,
      reviewedAt: updated.reviewedAt?.toISOString() ?? null,
      reviewNotes: updated.reviewNotes,
      writtenStaffNotes: updated.writtenStaffNotes,
      userId: updated.userId,
    };
  }

  async setTicketStatus(
    ticketId: string,
    dto: TicketStatusDto,
    actor: DecisionActor = {},
  ) {
    if (dto.status === TicketStatus.OPEN) {
      throw new BadRequestException(
        'OPEN nest pas une transition staff (cest letat initial).',
      );
    }
    const ticket = await this.prisma.ticket.findUnique({
      where: { id: ticketId },
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');

    const updated = await this.prisma.ticket.update({
      where: { id: ticketId },
      data: { status: dto.status },
    });

    this.logger.log(`staff ticket ${ticketId} → ${dto.status}`);

    if (ticket.discordThreadId || ticket.discordMessageId) {
      const actorName = await this.resolveActorName(actor);
      void this.webhooks
        .statusUpdate({
          kind: 'ticket',
          threadId: ticket.discordThreadId,
          messageId: ticket.discordMessageId,
          status: dto.status,
          actorName,
        })
        .catch((err) =>
          this.logger.warn(
            `statusUpdate ticket ${ticketId} echec : ${(err as Error).message}`,
          ),
        );
    }

    if (actor.userId) {
      void this.audit.log({
        actorId: actor.userId,
        action: `ticket.${dto.status.toLowerCase()}`,
        targetUserId: ticket.userId,
        targetEntity: `ticket:${ticketId}`,
        metadata: {
          previousStatus: ticket.status,
          newStatus: dto.status,
          subject: ticket.subject,
        },
        source: 'panel',
      });
    }

    return {
      id: updated.id,
      status: updated.status,
      userId: updated.userId,
      subject: updated.subject,
    };
  }
}
