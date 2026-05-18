import { Global, Module } from '@nestjs/common';
import { AuditService } from './audit.service';

/**
 * @Global : AuditService est injecte un peu partout (StaffService,
 * AssignmentService, ReleasesService, DiscordService…). Evite de
 * forwarder l'import a la main dans chaque feature module.
 */
@Global()
@Module({
  providers: [AuditService],
  exports: [AuditService],
})
export class AuditModule {}
