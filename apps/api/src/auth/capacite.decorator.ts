import { SetMetadata } from '@nestjs/common';
import type { Capacite } from './capacites';

export const CAPACITES_KEY = 'capacites';

/**
 * Exige au moins UNE des capacités listées (auth/capacites.ts). Lue par
 * `RolesGuard`, avec `JwtAuthGuard` pour que `request.user.role` soit posé.
 *
 * Posée sur une méthode, elle remplace celle de la classe (comme `@MinRole`) :
 * la capacité de méthode doit donc être au moins aussi stricte que celle de
 * la classe, sauf choix délibéré et commenté.
 */
export const ExigeCapacite = (...capacites: Capacite[]) =>
  SetMetadata(CAPACITES_KEY, capacites);
