import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { PrismaModule } from '../prisma/prisma.module';
import { TicketsModule } from '../tickets/tickets.module';
import { PlayController } from './play.controller';
import { PlayReportController } from './play-report.controller';
import { PlayService } from './play.service';

@Module({
  imports: [AuthModule, PrismaModule, TicketsModule],
  controllers: [PlayController, PlayReportController],
  providers: [PlayService],
  exports: [PlayService],
})
export class PlayModule {}
