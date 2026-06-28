import { Module } from '@nestjs/common';
import { ServerStatusController } from './server-status.controller';
import { ServerStatusService } from './server-status.service';

// Pas d'import d'AuthModule : la route est publique (aucun JwtAuthGuard).
@Module({
  controllers: [ServerStatusController],
  providers: [ServerStatusService],
})
export class ServerStatusModule {}
