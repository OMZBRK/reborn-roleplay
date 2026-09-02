import { Module } from '@nestjs/common';
import { ModrinthService } from './modrinth.service';

/**
 * Détection planifiée des updates de mods Modrinth (cron → notif bot Discord).
 * Pas de surface HTTP : PrismaService (global) + WebhooksService (global) sont
 * injectés directement. Gardé par MODRINTH_SYNC_ENABLED=true.
 */
@Module({
  providers: [ModrinthService],
})
export class ModrinthModule {}
