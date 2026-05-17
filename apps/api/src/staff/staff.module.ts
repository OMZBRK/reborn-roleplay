import { Module } from '@nestjs/common';
import { StaffController } from './staff.controller';
import { StaffService } from './staff.service';
import { AssignmentModule } from '../admin/assignment.module';
import { WhitelistModule } from '../whitelist/whitelist.module';
import { TicketsModule } from '../tickets/tickets.module';

@Module({
  imports: [AssignmentModule, WhitelistModule, TicketsModule],
  controllers: [StaffController],
  providers: [StaffService],
  exports: [StaffService],
})
export class StaffModule {}
