import { SetMetadata } from '@nestjs/common';
import { Role } from '@prisma/client';

export const MIN_ROLE_KEY = 'minRole';

/**
 * Annotates a route with the minimum role required to access it.
 * Consumed by `RolesGuard`. Use together with `JwtAuthGuard` so that
 * `request.user.role` is populated.
 */
export const MinRole = (role: Role) => SetMetadata(MIN_ROLE_KEY, role);
