import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { StaffModule } from '../staff/staff.module';
import { TicketsModule } from '../tickets/tickets.module';
import { WhitelistModule } from '../whitelist/whitelist.module';
import { AdminController } from './admin.controller';
import { AdminService } from './admin.service';

@Module({
  imports: [AuthModule, StaffModule, TicketsModule, WhitelistModule],
  controllers: [AdminController],
  providers: [AdminService],
})
export class AdminModule {}
