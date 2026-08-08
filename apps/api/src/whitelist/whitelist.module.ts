import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { AssignmentModule } from '../admin/assignment.module';
import { OralSlotsModule } from '../oral-slots/oral-slots.module';
import { WhitelistController } from './whitelist.controller';
import { WhitelistService } from './whitelist.service';
import { WhitelistMessagesService } from './whitelist-messages.service';

@Module({
  imports: [AuthModule, AssignmentModule, OralSlotsModule],
  controllers: [WhitelistController],
  providers: [WhitelistService, WhitelistMessagesService],
  // Exporté pour que StaffModule puisse appeler postStaffMessage quand le
  // bot relaie un message staff via /staff/whitelist/:id/messages.
  exports: [WhitelistMessagesService],
})
export class WhitelistModule {}
