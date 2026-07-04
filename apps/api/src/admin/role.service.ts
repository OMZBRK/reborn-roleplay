import {
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { Role } from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import { PrismaService } from '../prisma/prisma.service';

/** Ordre croissant de privilège (identique à RolesGuard / AssignmentService). */
const ROLE_RANKS: Role[] = [
  Role.PLAYER,
  Role.WHITELISTED,
  Role.HELPER,
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
 *  - l'acteur doit être au moins ADMIN ;
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

  /** Panel staff (JWT) : l'acteur est identifié par son userId Reborn. */
  async changeRoleByUserId(
    actorUserId: string,
    targetUserId: string,
    newRole: Role,
  ): Promise<RoleChangeResult> {
    const actor = await this.resolveActorById(actorUserId);
    const target = await this.resolveTargetById(targetUserId);
    return this.apply(actor, target, newRole, 'panel');
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
  ): Promise<RoleChangeResult> {
    if (this.rank(actor.role) < this.rank(Role.ADMIN)) {
      throw new ForbiddenException(
        'Seuls les ADMIN et OWNER peuvent changer les rôles.',
      );
    }
    if (actor.id === target.id) {
      throw new ForbiddenException('Tu ne peux pas changer ton propre rôle.');
    }
    if (this.rank(target.role) >= this.rank(actor.role)) {
      throw new ForbiddenException(
        `Impossible : ${target.minecraftUsername} a un rôle égal ou supérieur au tien (${target.role}).`,
      );
    }
    if (this.rank(newRole) >= this.rank(actor.role)) {
      throw new ForbiddenException(
        `Tu ne peux pas attribuer un rôle égal ou supérieur au tien (${actor.role}). OWNER se pose en base.`,
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

    const updated = await this.prisma.user.update({
      where: { id: target.id },
      data: { role: newRole },
      select: { id: true, minecraftUsername: true, role: true },
    });

    this.logger.log(
      `role ${target.minecraftUsername} : ${target.role} → ${newRole} ` +
        `par ${actor.minecraftUsername} (${source})`,
    );
    void this.audit.log({
      actorId: actor.id,
      action: 'role.change',
      targetUserId: target.id,
      targetEntity: `user:${target.id}`,
      metadata: { from: target.role, to: newRole },
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
