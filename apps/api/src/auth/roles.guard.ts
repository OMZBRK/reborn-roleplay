import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Role } from '@prisma/client';
import { CAPACITES_KEY } from './capacite.decorator';
import { aCapacite, LIBELLES_GRADES, messageRefus } from './capacites';
import type { Capacite } from './capacites';
import type { RequestUser } from './current-user.decorator';
import { MIN_ROLE_KEY } from './roles.decorator';

// Ordered low → high. Index is the privilege level.
const ROLE_HIERARCHY: Role[] = [
  Role.PLAYER,
  Role.WHITELISTED,
  Role.HELPER,
  // Grades techniques (contributeurs) — au-dessus de HELPER, sous la modération.
  // Leur accès réel aux fichiers passe par la carte de scopes de FilesService,
  // pas par ce rang linéaire.
  Role.MODELISATEUR,
  Role.DEVELOPPEUR,
  Role.WHITELIST_REVIEWER,
  Role.MODERATOR,
  Role.ADMIN,
  Role.OWNER,
];

function roleRank(role: Role): number {
  const idx = ROLE_HIERARCHY.indexOf(role);
  return idx === -1 ? -1 : idx;
}

/**
 * Garde des routes staff. Deux règles, appliquées toutes deux si présentes :
 *  - `@MinRole` : rang linéaire (routes pas encore passées aux capacités) ;
 *  - `@ExigeCapacite` : au moins une des capacités listées (PANEL_V2 §3.3).
 * Refus en français, sans code de grade brut.
 */
@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const targets = [context.getHandler(), context.getClass()];
    const minRole = this.reflector.getAllAndOverride<Role | undefined>(
      MIN_ROLE_KEY,
      targets,
    );
    const capacites = this.reflector.getAllAndOverride<Capacite[] | undefined>(
      CAPACITES_KEY,
      targets,
    );
    if (!minRole && !capacites?.length) return true;

    const req = context.switchToHttp().getRequest<{ user?: RequestUser }>();
    const user = req.user;
    if (!user) {
      throw new ForbiddenException('Authentification requise.');
    }

    if (minRole && roleRank(user.role) < roleRank(minRole)) {
      throw new ForbiddenException(
        `Réservé au grade ${LIBELLES_GRADES[minRole]} et au-dessus.`,
      );
    }
    if (capacites?.length && !capacites.some((c) => aCapacite(user.role, c))) {
      throw new ForbiddenException(messageRefus(capacites));
    }
    return true;
  }
}
