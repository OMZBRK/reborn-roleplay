import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AdminModule } from './admin/admin.module';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { AuditModule } from './audit/audit.module';
import { AuthModule } from './auth/auth.module';
import { DiscordAuthModule } from './discord/discord.module';
import { LoreModule } from './lore/lore.module';
import { ManifestModule } from './manifest/manifest.module';
import { PatchnotesModule } from './patchnotes/patchnotes.module';
import { PlayModule } from './play/play.module';
import { PrismaModule } from './prisma/prisma.module';
import { ReleasesModule } from './releases/releases.module';
import { RulesModule } from './rules/rules.module';
import { SecurityModule } from './security/security.module';
import { ServerStatusModule } from './server-status/server-status.module';
import { StaffModule } from './staff/staff.module';
import { TicketsModule } from './tickets/tickets.module';
import { WebhooksModule } from './webhooks/webhooks.module';
import { WhitelistModule } from './whitelist/whitelist.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env', '../../.env'],
    }),
    PrismaModule,
    AdminModule,
    AuditModule,
    AuthModule,
    DiscordAuthModule,
    LoreModule,
    ManifestModule,
    PatchnotesModule,
    PlayModule,
    ReleasesModule,
    RulesModule,
    SecurityModule,
    ServerStatusModule,
    StaffModule,
    TicketsModule,
    WebhooksModule,
    WhitelistModule,
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
