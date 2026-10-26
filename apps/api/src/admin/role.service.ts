import {
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { AppStatus, DouaneReservationStatut, Role } from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import { aCapacite, LIBELLES_GRADES } from '../auth/capacites';
import { PrismaService } from '../prisma/prisma.service';
import { GARDE_VOIX_MS } from '../whitelist/whitelist.rules';

/** Ordre croissant de privilège (identique à RolesGuard / AssignmentService). */
const ROLE_RANKS: Role[] = [
  Role.PLAYER,
  Role.WHITELISTED,
  Role.HELPER,
  Role.MODELISATEUR,
  Role.DEVELOPPEUR,
  Role.WHITELIST_REVIEWER,
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];

interface Actor {
  id: string;
  role: Role;
  minecraftUsername: string;
}

interface Target {
  id: string;
  role: Role;
  minecraftUsername: string;
}

export interface RoleChangeResult {
  id: string;
  minecraftUsername: string;
  role: Role;
  previousRole: Role;
  changed: boolean;
}

/**
 * Changement de rôle Reborn (PLAYER … OWNER) avec garde-fous, partagé par le
 * panel staff (JWT) et le bot Discord (`/role set`, HMAC).
 *
 * Règles (« tu ne peux jamais toucher à ton niveau ou au-dessus ») :
 *  - l'acteur doit avoir la capacité `grades.changer` (ADMIN, OWNER) ;
 *  - on ne change pas son propre rôle ;
 *  - la cible doit être STRICTEMENT en dessous de l'acteur (pas de pair/supérieur) ;
 *  - le rôle attribué doit être STRICTEMENT en dessous de l'acteur.
 *
 * Conséquence voulue : OWNER ne peut être attribué par personne via cet outil
 * (il reste réservé au SQL direct), et un ADMIN ne peut ni créer ni toucher un
 * autre ADMIN/OWNER.
 */
@Injectable()
export class RoleService {
  private readonly logger = new Logger(RoleService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  private rank(role: Role): number {
    return ROLE_RANKS.indexOf(role);
  }

  /**
   * Panel staff (JWT) : l'acteur est identifié par son userId Reborn. Le
   * motif (obligatoire au panel, ChangeRoleDto) part au journal.
   */
  async changeRoleByUserId(
    actorUserId: string,
    targetUserId: string,
    newRole: Role,
    motif?: string,
  ): Promise<RoleChangeResult> {
    const actor = await this.resolveActorById(actorUserId);
    const target = await this.resolveTargetById(targetUserId);
    return this.apply(actor, target, newRole, 'panel', motif);
  }

  /** Bot Discord (HMAC) : acteur par snowflake Discord, cible par pseudo MC. */
  async changeRoleFromDiscord(
    actorDiscordId: string,
    targetPseudo: string,
    newRole: Role,
  ): Promise<RoleChangeResult> {
    const actor = await this.resolveActorByDiscord(actorDiscordId);
    const target = await this.resolveTargetByPseudo(targetPseudo);
    return this.apply(actor, target, newRole, 'discord');
  }

  private async apply(
    actor: Actor,
    target: Target,
    newRole: Role,
    source: 'panel' | 'discord',
    motif?: string,
  ): Promise<RoleChangeResult> {
    // Capacité `grades.changer` (Administrateur, Propriétaire) : même règle
    // pour le panel et le bot, lue dans la table des capacités.
    if (!aCapacite(actor.role, 'grades.changer')) {
      throw new ForbiddenException(
        'Seuls les administrateurs et le propriétaire peuvent changer les grades.',
      );
    }
    if (actor.id === target.id) {
      throw new ForbiddenException('Tu ne peux pas changer ton propre grade.');
    }
    // Libellés français (jamais de code de grade brut dans un message).
    if (this.rank(target.role) >= this.rank(actor.role)) {
      throw new ForbiddenException(
        `Impossible : ${target.minecraftUsername} a un grade égal ou supérieur au tien (${LIBELLES_GRADES[target.role]}).`,
      );
    }
    if (this.rank(newRole) >= this.rank(actor.role)) {
      throw new ForbiddenException(
        `Tu ne peux pas attribuer un grade égal ou supérieur au tien (${LIBELLES_GRADES[actor.role]}).`,
      );
    }

    if (target.role === newRole) {
      return {
        id: target.id,
        minecraftUsername: target.minecraftUsername,
        role: target.role,
        previousRole: target.role,
        changed: false,
      };
    }

    // PLAYER → rôle supérieur : la candidature n'attend plus de décision (phase
    // ACCEPTE). Sa voix suit la règle d'une décision finale : effacée 30 jours
    // plus tard (Q2), au lieu d'être gardée sans limite. Une place de douane
    // encore réservée est rendue : elle ne compte plus dans la capacité, et ni
    // rappel ni « séance manquée » ne part vers un compte déjà accepté.
    // Retour à PLAYER : la procédure reprend là où elle en était ; l'effacement
    // programmé par la promotion est levé sur une candidature encore en cours
    // (ni acceptée ni refusée), comme le refus annulé de la décision de l'écrit.
    const now = new Date();
    const majRole = this.prisma.user.update({
      where: { id: target.id },
      data: { role: newRole },
      select: { id: true, minecraftUsername: true, role: true },
    });
    let updated: Awaited<typeof majRole>;
    if (target.role === Role.PLAYER && newRole !== Role.PLAYER) {
      const [u, voix, places] = await this.prisma.$transaction([
        majRole,
        this.prisma.whitelistApplication.updateMany({
          where: {
            userId: target.id,
            voiceKey: { not: null },
            voicePurgeAt: null,
          },
          data: { voicePurgeAt: new Date(now.getTime() + GARDE_VOIX_MS) },
        }),
        this.prisma.douaneReservation.updateMany({
          where: { userId: target.id, statut: DouaneReservationStatut.BOOKED },
          data: {
            statut: DouaneReservationStatut.CANCELLED,
            annuleeAt: now,
            motifAnnulation: 'Compte accepté par changement de rôle',
          },
        }),
      ]);
      updated = u;
      if (voix.count > 0) {
        this.logger.log(
          `voix de la candidature de ${target.minecraftUsername} programmée pour effacement (changement de rôle)`,
        );
      }
      if (places.count > 0) {
        this.logger.log(
          `${places.count} place(s) de douane de ${target.minecraftUsername} rendue(s) (changement de rôle)`,
        );
      }
    } else if (target.role !== Role.PLAYER && newRole === Role.PLAYER) {
      const [u, voix] = await this.prisma.$transaction([
        majRole,
        this.prisma.whitelistApplication.updateMany({
          where: {
            userId: target.id,
            status: { in: [AppStatus.PENDING, AppStatus.NEEDS_REVISION] },
            voiceKey: { not: null },
            voicePurgeAt: { not: null },
          },
          data: { voicePurgeAt: null },
        }),
      ]);
      updated = u;
      if (voix.count > 0) {
        this.logger.log(
          `effacement de la voix de la candidature de ${target.minecraftUsername} levé (retour à PLAYER)`,
        );
      }
    } else {
      updated = await majRole;
    }

    this.logger.log(
      `role ${target.minecraftUsername} : ${target.role} → ${newRole} ` +
        `par ${actor.minecraftUsername} (${source})`,
    );
    void this.audit.log({
      actorId: actor.id,
      action: 'role.change',
      targetUserId: target.id,
      targetEntity: `user:${target.id}`,
      metadata: {
        from: target.role,
        to: newRole,
        ...(motif?.trim() ? { motif: motif.trim() } : {}),
      },
      source,
    });

    return {
      id: updated.id,
      minecraftUsername: updated.minecraftUsername,
      role: updated.role,
      previousRole: target.role,
      changed: true,
    };
  }

  // ── resolvers ──────────────────────────────────────────
  private async resolveActorById(userId: string): Promise<Actor> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { id: true, role: true, minecraftUsername: true },
    });
    if (!user) throw new ForbiddenException('Compte staff introuvable.');
    return user;
  }

  private async resolveActorByDiscord(discordUserId: string): Promise<Actor> {
    const user = await this.prisma.user.findUnique({
      where: { discordUserId },
      select: { id: true, role: true, minecraftUsername: true },
    });
    if (!user) {
      throw new ForbiddenException(
        'Aucun compte Reborn lié à ce Discord. Connecte-toi via le launcher pour lier ton compte.',
      );
    }
    return user;
  }

  private async resolveTargetById(userId: string): Promise<Target> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { id: true, role: true, minecraftUsername: true },
    });
    if (!user) throw new NotFoundException('Joueur introuvable.');
    return user;
  }

  private async resolveTargetByPseudo(pseudo: string): Promise<Target> {
    const user = await this.prisma.user.findFirst({
      where: { minecraftUsername: { equals: pseudo, mode: 'insensitive' } },
      select: { id: true, role: true, minecraftUsername: true },
    });
    if (!user) {
      throw new NotFoundException(`Aucun joueur nommé « ${pseudo} ».`);
    }
    return user;
  }
}
