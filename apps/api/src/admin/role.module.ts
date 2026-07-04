import { Module } from '@nestjs/common';
import { RoleService } from './role.service';

/**
 * Fournit {@link RoleService} (changement de rôle avec garde-fous), partagé
 * par le panel staff (AdminController, JWT) et le bot Discord (StaffController,
 * HMAC). Module feuille : ne dépend que de Prisma + Audit (tous deux @Global).
 */
@Module({
  providers: [RoleService],
  exports: [RoleService],
})
export class RoleModule {}
