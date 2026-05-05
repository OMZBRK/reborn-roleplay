import { Module } from '@nestjs/common';
import { StaffController } from './staff.controller';
import { StaffService } from './staff.service';
import { WhitelistModule } from '../whitelist/whitelist.module';
import { TicketsModule } from '../tickets/tickets.module';

@Module({
  imports: [WhitelistModule, TicketsModule],
  controllers: [StaffController],
  providers: [StaffService],
})
export class StaffModule {}
