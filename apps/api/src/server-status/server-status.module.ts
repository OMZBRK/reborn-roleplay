import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { ServerStatusController } from './server-status.controller';
import { ServerStatusService } from './server-status.service';

@Module({
  imports: [AuthModule],
  controllers: [ServerStatusController],
  providers: [ServerStatusService],
})
export class ServerStatusModule {}
