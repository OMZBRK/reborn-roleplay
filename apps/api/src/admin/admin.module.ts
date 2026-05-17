import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { StaffModule } from '../staff/staff.module';
import { TicketsModule } from '../tickets/tickets.module';
import { WhitelistModule } from '../whitelist/whitelist.module';
import { AdminController } from './admin.controller';
import { AdminService } from './admin.service';
import { AssignmentModule } from './assignment.module';

@Module({
  imports: [
    AuthModule,
    AssignmentModule,
    StaffModule,
    TicketsModule,
    WhitelistModule,
  ],
  controllers: [AdminController],
  providers: [AdminService],
})
export class AdminModule {}
