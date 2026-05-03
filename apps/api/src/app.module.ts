import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { AuthModule } from './auth/auth.module';
import { ManifestModule } from './manifest/manifest.module';
import { PatchnotesModule } from './patchnotes/patchnotes.module';
import { PrismaModule } from './prisma/prisma.module';
import { RulesModule } from './rules/rules.module';
import { WhitelistModule } from './whitelist/whitelist.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env', '../../.env'],
    }),
    PrismaModule,
    AuthModule,
    ManifestModule,
    PatchnotesModule,
    RulesModule,
    WhitelistModule,
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
