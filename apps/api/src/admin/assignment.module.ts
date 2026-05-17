import { Module } from '@nestjs/common';
import { AssignmentService } from './assignment.service';

/**
 * Module dedie au service de claim/release des cas (whitelist + tickets).
 * Extrait d'AdminModule pour eviter le cycle :
 *   - AdminModule importe StaffModule, TicketsModule, WhitelistModule
 *   - mais ceux-ci veulent aussi AssignmentService (Staff = HMAC bot,
 *     Whitelist/Tickets = reclaim user)
 *
 * AssignmentService ne depend que de PrismaService donc on peut le
 * placer dans son propre module global-ish que tout le monde importe
 * sans cycle.
 */
@Module({
  providers: [AssignmentService],
  exports: [AssignmentService],
})
export class AssignmentModule {}
