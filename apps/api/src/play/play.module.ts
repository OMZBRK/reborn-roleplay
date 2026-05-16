import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { PrismaModule } from '../prisma/prisma.module';
import { PlayController } from './play.controller';
import { PlayService } from './play.service';

@Module({
  imports: [AuthModule, PrismaModule],
  controllers: [PlayController],
  providers: [PlayService],
  exports: [PlayService],
})
export class PlayModule {}
