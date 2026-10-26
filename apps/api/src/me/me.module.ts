import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { MePersonnagesService } from './me-personnages.service';
import { MeController } from './me.controller';
import { MeService } from './me.service';

@Module({
  imports: [AuthModule],
  controllers: [MeController],
  providers: [MeService, MePersonnagesService],
})
export class MeModule {}
