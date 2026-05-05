import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { TicketsController } from './tickets.controller';
import { TicketsService } from './tickets.service';

@Module({
  imports: [AuthModule],
  controllers: [TicketsController],
  providers: [TicketsService],
  // Exporté pour StaffModule (postStaffMessage déclenché par le listener
  // messageCreate du bot via /staff/tickets/:id/messages HMAC).
  exports: [TicketsService],
})
export class TicketsModule {}
